# PowerShell 7. Ejecutar después de backend/mvnw.cmd verify, sin otra suite PostgreSQL.
# Crea recursos por API ADMIN y limpia exclusivamente sus IDs en finally.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$origen = 'http://localhost:8080'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$servidor = $null
$servicioId = 0L
$barberoId = 0L
$clienteId = 0L
$reservaId = 0L
$marcador = [Guid]::NewGuid().ToString('N')
$variables = @('JAVA_HOME', 'BT_ADMIN_CORREO', 'BT_ADMIN_PASSWORD', 'BT_DB_PASSWORD', 'BT_DB_USER', 'BT_DB_URL', 'PGPASSWORD')
$previas = @{}
foreach ($nombre in $variables) { $previas[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$admin = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$cliente = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$salida = Join-Path ([IO.Path]::GetTempPath()) ('barberturno-t26-' + $marcador)
[IO.Directory]::CreateDirectory($salida) | Out-Null

function Peticion($sesion, $metodo, $ruta, $esperado, $cuerpo = $null, $csrf = 'valido') {
    $opciones = @{ Uri = $origen + $ruta; Method = $metodo; WebSession = $sesion
        SkipHttpErrorCheck = $true; TimeoutSec = 20 }
    if ($null -ne $cuerpo) {
        $opciones.ContentType = 'application/json'
        $opciones.Body = $cuerpo | ConvertTo-Json -Depth 5 -Compress
    }
    if ($metodo -ne 'GET' -and $csrf -ne 'ausente') {
        $token = $sesion.Cookies.GetCookies([Uri]$origen)['XSRF-TOKEN'].Value
        if (-not $token) { throw 'Falta XSRF-TOKEN; valor omitido.' }
        $opciones.Headers = @{ 'X-XSRF-TOKEN' = $(if ($csrf -eq 'incorrecto') { 'incorrecto' } else { [Uri]::UnescapeDataString($token) }) }
    }
    $respuesta = Invoke-WebRequest @opciones
    Write-Host "$metodo $ruta -> $($respuesta.StatusCode)"
    if ($respuesta.StatusCode -ne $esperado) {
        $codigo = ''
        try { $codigo = ($respuesta.Content | ConvertFrom-Json).codigo } catch { }
        throw "HTTP inesperado: $metodo $ruta; esperado=$esperado; obtenido=$($respuesta.StatusCode); codigo=$codigo."
    }
    if ($respuesta.Content) { return ($respuesta.Content | ConvertFrom-Json) }
}

try {
    if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) { throw '8080 ocupado; no se detienen procesos ajenos.' }
    foreach ($linea in [IO.File]::ReadLines((Join-Path $raiz '.local/barberturno.env'))) {
        if ($linea -match '^\s*(BT_[A-Z_]+)\s*=(.*)$' -and $variables -contains $Matches[1]) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
        }
    }
    if (-not $env:BT_ADMIN_CORREO -or -not $env:BT_ADMIN_PASSWORD -or -not $env:BT_DB_PASSWORD) { throw 'Faltan credenciales locales para el ensayo; valores omitidos.' }
    if ($env:BT_DB_URL -and $env:BT_DB_URL -ne 'jdbc:postgresql://localhost:5433/barberturno') { throw 'El ensayo requiere la base local barberturno en 5433.' }
    if ($env:BT_DB_USER -and $env:BT_DB_USER -ne 'barberturno') { throw 'El ensayo requiere el rol local barberturno.' }
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
    $jar = Join-Path $raiz 'backend/target/barberturno-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Falta el JAR de verify.' }
    $opcionesProceso = @{
        FilePath = "$env:JAVA_HOME\bin\java.exe"
        ArgumentList = @('-jar', $jar, '--spring.profiles.active=dev', '--barberturno.seguridad.cookie-secure=false')
        WorkingDirectory = $raiz; WindowStyle = 'Hidden'; PassThru = $true
        RedirectStandardOutput = Join-Path $salida 'backend.log'
        RedirectStandardError = Join-Path $salida 'backend-error.log'
    }
    $servidor = Start-Process @opcionesProceso
    $listo = $false
    for ($n = 0; $n -lt 100; $n++) {
        if ($servidor.HasExited) { throw 'El backend terminó antes de estar listo; logs privados temporales.' }
        try {
            $r = Invoke-WebRequest ($origen + '/api/servicios') -TimeoutSec 2
            if ($r.StatusCode -eq 200) { $listo = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $listo) { throw 'El catálogo no quedó disponible.' }
    $null = Peticion $admin GET '/api/auth/sesion' 401
    $null = Peticion $admin POST '/api/auth/login' 200 @{ correo = $env:BT_ADMIN_CORREO; password = $env:BT_ADMIN_PASSWORD }
    $null = Peticion $admin GET '/api/auth/sesion' 200
    $servicio = Peticion $admin POST '/api/servicios' 201 @{ nombre = 'Corte T26 ' + $marcador; descripcion = 'Servicio ficticio'; duracionMin = 30; precio = 25 }
    $servicioId = [long]$servicio.id
    $barbero = Peticion $admin POST '/api/barberos' 201 @{
        nombre = 'Profesional T26 ' + $marcador; correo = 'barbero-t26-' + $marcador + '@ejemplo.test'
        telefono = '999000026'; especialidad = 'Corte ficticio'; passwordTemporal = 'Ficticia26-' + [Guid]::NewGuid().ToString('N')
    }
    $barberoId = [long]$barbero.id
    $semana = @(1..7 | ForEach-Object { @{ diaSemana = $_; horaInicio = '09:00'; horaFin = '18:00' } })
    $null = Peticion $admin PUT "/api/barberos/$barberoId/jornadas" 200 $semana
    $null = Peticion $cliente GET '/api/auth/sesion' 401
    $identidad = Peticion $cliente POST '/api/auth/registro' 201 @{
        nombre = 'Cliente ficticio T26'; correo = 'cliente-t26-' + $marcador + '@ejemplo.test'
        telefono = '999000027'; password = 'Ficticia26-' + [Guid]::NewGuid().ToString('N'); aceptaPrivacidad = $true
    }
    $clienteId = [long]$identidad.id
    $null = Peticion $cliente GET '/api/auth/sesion' 200
    $zona = [TimeZoneInfo]::FindSystemTimeZoneById('SA Pacific Standard Time')
    $fecha = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $zona).Date.AddDays(1).ToString('yyyy-MM-dd')
    $rutaDisponibilidad = "/api/disponibilidad?servicioId=$servicioId&barberoId=$barberoId&fecha=$fecha"
    $disponibilidad = Peticion $cliente GET $rutaDisponibilidad 200
    if ($disponibilidad.franjas.Count -lt 4) { throw 'La jornada ficticia no produjo suficientes franjas.' }
    $crear = @{ servicioId = $servicioId; barberoId = $barberoId; inicio = $disponibilidad.franjas[0].inicio }
    $null = Peticion $cliente POST '/api/reservas' 403 $crear 'ausente'
    $null = Peticion $cliente POST '/api/reservas' 403 $crear 'incorrecto'
    $reserva = Peticion $cliente POST '/api/reservas' 201 $crear
    $reservaId = [long]$reserva.id
    $detalle = Peticion $cliente GET "/api/reservas/$reservaId" 200
    if (-not $detalle.permisos.reprogramar) { throw 'La reserva ficticia no permite reprogramar.' }
    $exclusion = Peticion $cliente GET "$rutaDisponibilidad&excluirReservaId=$reservaId" 200
    $cambiar = @{ inicio = $exclusion.franjas[3].inicio; barberoId = $barberoId; version = $detalle.version }
    $null = Peticion $cliente POST "/api/reservas/$reservaId/reprogramacion" 403 $cambiar 'ausente'
    $null = Peticion $cliente POST "/api/reservas/$reservaId/reprogramacion" 403 $cambiar 'incorrecto'
    $nueva = Peticion $cliente POST "/api/reservas/$reservaId/reprogramacion" 200 $cambiar
    if ($nueva.version -ne ($detalle.version + 1) -or $nueva.precioRef -ne 25 -or $nueva.duracionMin -ne 30) { throw 'No se conservaron las referencias o la versión.' }
    Write-Host "Recorrido base aprobado: cliente=$clienteId; servicio=$servicioId; barbero=$barberoId; reserva=$reservaId; version=$($nueva.version)."

    # Diagnóstico del bloqueo, sin reproducir reglas de disponibilidad en el frontend.
    $null = Peticion $admin PUT "/api/servicios/$servicioId" 200 @{ nombre = $servicio.nombre; descripcion = 'Servicio ficticio actualizado'; duracionMin = 60; precio = 99 }
    $actualizada = Peticion $cliente GET "$rutaDisponibilidad&excluirReservaId=$reservaId" 200
    Write-Host "Hallazgo RN-09/RN-13: reserva.duracionMin=$($nueva.duracionMin); disponibilidad.duracionMin=$($actualizada.duracionMin)."
    if ($actualizada.duracionMin -ne $nueva.duracionMin) { Write-Host 'BLOQUEO REPRODUCIDO: las franjas de reprogramación no usan la duración de referencia.' }
    $null = Peticion $admin PATCH "/api/servicios/$servicioId/estado" 200 @{ activo = $false }
    $rechazo = Peticion $cliente GET "$rutaDisponibilidad&excluirReservaId=$reservaId" 422
    Write-Host "Hallazgo RN-06: servicio desactivado -> 422 $($rechazo.codigo), pese a conservar el servicio en reprogramación."
} finally {
    try {
        if ($servicioId -or $barberoId -or $clienteId -or $reservaId) {
            $env:PGPASSWORD = $env:BT_DB_PASSWORD
            $sql = @"
\set ON_ERROR_STOP on
SELECT current_database() = 'barberturno' AND current_setting('port') = '5433' AS destino
\gset
\if :destino
BEGIN;
CREATE TEMP TABLE t26_usuario_barbero ON COMMIT DROP AS SELECT usuario_id AS id FROM barbero WHERE id = $barberoId;
DELETE FROM notificacion WHERE reserva_id = $reservaId;
DELETE FROM auditoria WHERE reserva_id = $reservaId;
DELETE FROM reserva WHERE id = $reservaId AND cliente_id = $clienteId AND servicio_id = $servicioId AND barbero_id = $barberoId;
DELETE FROM jornada WHERE barbero_id = $barberoId;
DELETE FROM barbero WHERE id = $barberoId;
DELETE FROM usuario WHERE id = $clienteId AND rol = 'CLIENTE';
DELETE FROM usuario WHERE id IN (SELECT id FROM t26_usuario_barbero) AND rol = 'BARBERO';
DELETE FROM servicio WHERE id = $servicioId;
COMMIT;
SELECT (SELECT count(*) FROM reserva WHERE id = $reservaId) +
       (SELECT count(*) FROM usuario WHERE id = $clienteId) +
       (SELECT count(*) FROM barbero WHERE id = $barberoId) +
       (SELECT count(*) FROM servicio WHERE id = $servicioId);
\else
\quit 3
\endif
"@
            $resultado = $sql | & $psql -X -w -h localhost -p 5433 -U barberturno -d barberturno -At
            if ($LASTEXITCODE -ne 0 -or $resultado[-1] -ne '0') { throw 'No se confirmó la limpieza por IDs de T-26.' }
            Write-Host 'Limpieza transaccional por IDs confirmada: restantes=0.'
        }
    } finally {
        if ($servidor -and -not $servidor.HasExited) { Stop-Process -Id $servidor.Id; $servidor.WaitForExit(10000) | Out-Null }
        foreach ($nombre in $variables) { [Environment]::SetEnvironmentVariable($nombre, $previas[$nombre], 'Process') }
        Write-Host 'Backend propio detenido; entorno de credenciales restaurado.'
    }
}
