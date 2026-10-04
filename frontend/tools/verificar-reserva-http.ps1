# PowerShell 7; Node 24.21.0. Ejecutar tras verify, sin otra suite PostgreSQL.
# ADMIN inicial de T-10: valores ficticios solo en el proceso, nunca promoción SQL.
# La limpieza física está autorizada exclusivamente para los IDs de este ensayo.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$frontend = Join-Path $raiz 'frontend'
$origen = 'http://localhost:4200'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$servidores = [Collections.Generic.List[object]]::new()
$adminId = 0L
$servicioId = 0L
$barberoId = 0L
$usuarioBarberoId = 0L
$clienteId = 0L
$reservaId = 0L
$marcador = [Guid]::NewGuid().ToString('N')
$correoAdmin = 'admin-t26-' + $marcador + '@ejemplo.test'
$correoBarbero = 'barbero-t26-' + $marcador + '@ejemplo.test'
$correoCliente = 'cliente-t26-' + $marcador + '@ejemplo.test'
$nombreServicio = 'Corte T26 ' + $marcador
$variables = @('JAVA_HOME', 'BT_ADMIN_CORREO', 'BT_ADMIN_PASSWORD', 'BT_ADMIN_NOMBRE', 'BT_DB_PASSWORD', 'BT_DB_USER', 'BT_DB_URL', 'PGPASSWORD', 'BT_T26_CLIENTE_CORREO', 'BT_T26_CLIENTE_PASSWORD', 'BT_T26_SERVICIO_ID', 'BT_T26_RESERVA_ID', 'BT_T26_FECHA', 'BT_T26_FASE')
$previas = @{}
foreach ($nombre in $variables) { $previas[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$admin = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$cliente = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$salida = Join-Path $frontend 'tmp'
[IO.Directory]::CreateDirectory($salida) | Out-Null

function Sql($consulta) {
    $resultado = @($consulta | & $psql -X -w -v ON_ERROR_STOP=1 -h localhost -p 5433 -U barberturno -d barberturno -At)
    if ($LASTEXITCODE -ne 0) { throw 'La consulta PostgreSQL falló; credenciales omitidas.' }
    return $resultado
}
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
    Write-Host "$metodo $ruta (CSRF $csrf) -> $($respuesta.StatusCode)"
    $contenido = $respuesta.Content
    if ($contenido -is [byte[]]) { $contenido = [Text.Encoding]::UTF8.GetString($contenido) }
    if ($respuesta.StatusCode -ne $esperado) {
        $codigo = ''
        try { $codigo = ($contenido | ConvertFrom-Json).codigo } catch { }
        throw "HTTP inesperado: $metodo $ruta; esperado=$esperado; obtenido=$($respuesta.StatusCode); codigo=$codigo."
    }
    if ($contenido) { return ($contenido | ConvertFrom-Json) }
}
function Visual($fase) {
    $env:BT_T26_FASE = $fase
    & node (Join-Path $PSScriptRoot 'verificar-reserva-visual.mjs')
    if ($LASTEXITCODE -ne 0) { throw "Falló QA visual de reserva ($fase)." }
}
function ComprobarReferencia($datos) {
    if ($datos.duracionMin -ne 30 -or -not $datos.franjas.Count) { throw 'Disponibilidad sin referencia de 30 minutos.' }
    foreach ($franja in $datos.franjas) {
        if (([DateTimeOffset]::Parse($franja.fin) - [DateTimeOffset]::Parse($franja.inicio)).TotalMinutes -ne 30) {
            throw 'La duración del intervalo del servidor no coincide con la referencia.'
        }
    }
}
try {
    if ((node --version) -ne 'v24.21.0') { throw 'Active Node 24.21.0 con fnm.' }
    if (@(Get-NetTCPConnection -LocalPort 8080,4200 -State Listen -ErrorAction SilentlyContinue).Count) { throw '8080/4200 ocupados; se preservan procesos ajenos.' }
    foreach ($linea in [IO.File]::ReadLines((Join-Path $raiz '.local/barberturno.env'))) {
        if ($linea -match '^\s*(BT_DB_[A-Z_]+)\s*=(.*)$' -and $variables -contains $Matches[1]) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
        }
    }
    if (-not $env:BT_DB_PASSWORD) { throw 'Falta la credencial local de BD; valor omitido.' }
    if ($env:BT_DB_URL -and $env:BT_DB_URL -ne 'jdbc:postgresql://localhost:5433/barberturno') { throw 'Se requiere la base local barberturno en 5433.' }
    if ($env:BT_DB_USER -and $env:BT_DB_USER -ne 'barberturno') { throw 'Se requiere el rol local barberturno.' }
    $env:PGPASSWORD = $env:BT_DB_PASSWORD
    if (@(Sql "SELECT current_database() = 'barberturno' AND current_setting('port') = '5433';")[0] -ne 't') { throw 'Destino de BD inesperado.' }
    if (@(Sql 'SELECT count(*) FROM usuario;')[0] -ne '0') { throw 'Se requiere dev sin usuarios para reconocer el ADMIN inicial propio; no se alteran cuentas existentes.' }
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
    $env:BT_ADMIN_CORREO = $correoAdmin
    $env:BT_ADMIN_PASSWORD = 'FicticiaT26-' + [Guid]::NewGuid().ToString('N')
    $env:BT_ADMIN_NOMBRE = 'Administrador ficticio T26'
    $env:BT_T26_CLIENTE_CORREO = $correoCliente
    $env:BT_T26_CLIENTE_PASSWORD = 'FicticiaT26-' + [Guid]::NewGuid().ToString('N')
    $jar = Join-Path $raiz 'backend/target/barberturno-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Falta el JAR de verify.' }
    $servidores.Add((Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-jar', $jar, '--spring.profiles.active=dev') -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $salida 't26-backend.log') -RedirectStandardError (Join-Path $salida 't26-backend-error.log')))
    $servidores.Add((Start-Process -FilePath (Get-Command node).Source -ArgumentList @('node_modules/@angular/cli/bin/ng.js', 'serve', '--host', 'localhost') -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $salida 't26-frontend.log') -RedirectStandardError (Join-Path $salida 't26-frontend-error.log')))
    $listo = $false
    for ($n = 0; $n -lt 100; $n++) {
        if (@($servidores | Where-Object HasExited).Count) { throw 'Un servidor terminó antes de estar listo; logs temporales.' }
        try {
            $r = Invoke-WebRequest ($origen + '/api/servicios') -TimeoutSec 2
            if ($r.StatusCode -eq 200) {
                $adminId = [long](@(Sql "SELECT coalesce(max(id),0) FROM usuario WHERE correo = '$correoAdmin' AND rol = 'ADMIN';")[0])
                if ($adminId) { $listo = $true; break }
            }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $listo) { throw 'El catálogo no quedó disponible.' }
    $adminId = [long](@(Sql "SELECT id FROM usuario WHERE correo = '$correoAdmin' AND rol = 'ADMIN';")[0])
    if (-not $adminId) { throw 'El administrador inicial no fue creado.' }
    $null = Peticion $admin GET '/api/auth/sesion' 401
    $identidadAdmin = Peticion $admin POST '/api/auth/login' 200 @{ correo = $env:BT_ADMIN_CORREO; password = $env:BT_ADMIN_PASSWORD }
    if ($identidadAdmin.id -ne $adminId -or $identidadAdmin.rol -ne 'ADMIN') { throw 'Identidad ADMIN inesperada.' }
    $null = Peticion $admin GET '/api/auth/sesion' 200
    $servicio = Peticion $admin POST '/api/servicios' 201 @{ nombre = $nombreServicio; descripcion = 'Servicio ficticio'; duracionMin = 30; precio = 25 }
    $servicioId = [long]$servicio.id
    $barbero = Peticion $admin POST '/api/barberos' 201 @{
        nombre = 'Profesional ficticio T26'; correo = $correoBarbero
        telefono = '999000026'; especialidad = 'Corte ficticio'; passwordTemporal = 'Ficticia26-' + [Guid]::NewGuid().ToString('N')
    }
    $barberoId = [long]$barbero.id
    $usuarioBarberoId = [long](@(Sql "SELECT usuario_id FROM barbero WHERE id = $barberoId;")[0])
    $semana = @(1..7 | ForEach-Object { @{ diaSemana = $_; horaInicio = '09:00'; horaFin = '18:00' } })
    $null = Peticion $admin PUT "/api/barberos/$barberoId/jornadas" 200 $semana
    $null = Peticion $cliente GET '/api/auth/sesion' 401
    $identidad = Peticion $cliente POST '/api/auth/registro' 201 @{
        nombre = 'Cliente ficticio T26'; correo = $correoCliente
        telefono = '999000027'; password = $env:BT_T26_CLIENTE_PASSWORD; aceptaPrivacidad = $true
    }
    $clienteId = [long]$identidad.id
    $null = Peticion $cliente GET '/api/auth/sesion' 200
    $env:BT_T26_FECHA = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'SA Pacific Standard Time').AddDays(1).ToString('yyyy-MM-dd')
    $rutaDisponibilidad = "/api/disponibilidad?servicioId=$servicioId&barberoId=$barberoId&fecha=$env:BT_T26_FECHA"
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
    ComprobarReferencia $exclusion
    $cambiar = @{ inicio = $exclusion.franjas[3].inicio; barberoId = $barberoId; version = $detalle.version }
    $null = Peticion $cliente POST "/api/reservas/$reservaId/reprogramacion" 403 $cambiar 'ausente'
    $null = Peticion $cliente POST "/api/reservas/$reservaId/reprogramacion" 403 $cambiar 'incorrecto'
    $nueva = Peticion $cliente POST "/api/reservas/$reservaId/reprogramacion" 200 $cambiar
    if ($nueva.version -ne ($detalle.version + 1) -or $nueva.precioRef -ne 25 -or $nueva.duracionMin -ne 30) { throw 'No se conservaron referencias o versión.' }
    Write-Host "Recorrido base aprobado: ADMIN=$adminId; cliente=$clienteId; servicio=$servicioId; barbero=$barberoId; usuario BARBERO=$usuarioBarberoId; reserva=$reservaId; version=$($nueva.version)."
    $env:BT_T26_SERVICIO_ID = [string]$servicioId
    $env:BT_T26_RESERVA_ID = [string]$reservaId
    Visual 'explorar'

    # T-44/DA-21: la respuesta del servidor conserva las referencias incluso con catálogo inactivo.
    $null = Peticion $admin PUT "/api/servicios/$servicioId" 200 @{ nombre = $servicio.nombre; descripcion = 'Servicio ficticio actualizado'; duracionMin = 60; precio = 99 }
    $actualizada = Peticion $cliente GET "$rutaDisponibilidad&excluirReservaId=$reservaId" 200
    ComprobarReferencia $actualizada
    $catalogo = Peticion $cliente GET $rutaDisponibilidad 200
    if ($catalogo.duracionMin -ne 60) { throw 'El catálogo no refleja la edición ADMIN.' }
    $null = Peticion $admin PATCH "/api/servicios/$servicioId/estado" 200 @{ activo = $false }
    $inactiva = Peticion $cliente GET "$rutaDisponibilidad&excluirReservaId=$reservaId" 200
    ComprobarReferencia $inactiva
    $rechazo = Peticion $cliente GET $rutaDisponibilidad 422
    if ($rechazo.codigo -ne 'RECURSO_INACTIVO') { throw 'Se permitió crear con servicio inactivo.' }
    Visual 'reprogramar'
    $final = Peticion $cliente GET "/api/reservas/$reservaId" 200
    if ($final.version -ne ($nueva.version + 1) -or $final.precioRef -ne 25 -or $final.duracionMin -ne 30) { throw 'La reprogramación visual no conserva referencias/version.' }
    $null = Peticion $cliente POST '/api/auth/logout' 204
    $null = Peticion $admin POST '/api/auth/logout' 204
    Write-Host 'DA-21 aprobada: catálogo 60 min/S/ 99; reprogramación 30 min/S/ 25, incluso inactivo.'
} finally {
    foreach ($proceso in $servidores) {
        if (-not $proceso.HasExited) { taskkill.exe /PID $proceso.Id /T /F | Out-Null }
    }
    try {
        # Recupera también altas propias si la respuesta HTTP o la preparación visual fallaron.
        if ($servidores.Count) {
            $adminId = [long](@(Sql "SELECT coalesce(max(id),0) FROM usuario WHERE correo = '$correoAdmin' AND rol = 'ADMIN';")[0])
            $clienteId = [long](@(Sql "SELECT coalesce(max(id),0) FROM usuario WHERE correo = '$correoCliente' AND rol = 'CLIENTE';")[0])
            $usuarioBarberoId = [long](@(Sql "SELECT coalesce(max(id),0) FROM usuario WHERE correo = '$correoBarbero' AND rol = 'BARBERO';")[0])
            $barberoId = [long](@(Sql "SELECT coalesce(max(id),0) FROM barbero WHERE usuario_id = $usuarioBarberoId;")[0])
            $servicioId = [long](@(Sql "SELECT coalesce(max(id),0) FROM servicio WHERE nombre = '$nombreServicio';")[0])
            $idsReserva = @(Sql "SELECT id FROM reserva WHERE cliente_id = $clienteId AND servicio_id = $servicioId AND barbero_id = $barberoId;")
            $listaIds = (@('0') + $idsReserva) -join ','
            $sql = @"
BEGIN;
DELETE FROM notificacion WHERE reserva_id IN ($listaIds);
DELETE FROM auditoria_reserva WHERE reserva_id IN ($listaIds);
DELETE FROM reserva WHERE id IN ($listaIds) AND cliente_id = $clienteId AND servicio_id = $servicioId AND barbero_id = $barberoId;
DELETE FROM jornada WHERE barbero_id = $barberoId;
DELETE FROM barbero WHERE id = $barberoId AND usuario_id = $usuarioBarberoId;
DELETE FROM usuario WHERE id = $clienteId AND correo = '$correoCliente' AND rol = 'CLIENTE';
DELETE FROM usuario WHERE id = $usuarioBarberoId AND correo = '$correoBarbero' AND rol = 'BARBERO';
DELETE FROM servicio WHERE id = $servicioId AND nombre = '$nombreServicio';
DELETE FROM usuario WHERE id = $adminId AND correo = '$correoAdmin' AND rol = 'ADMIN';
COMMIT;
SELECT (SELECT count(*) FROM reserva WHERE id IN ($listaIds)) +
       (SELECT count(*) FROM usuario WHERE id IN ($adminId,$clienteId,$usuarioBarberoId)) +
       (SELECT count(*) FROM barbero WHERE id = $barberoId) +
       (SELECT count(*) FROM servicio WHERE id = $servicioId);
"@
            $resultado = @(Sql $sql)
            if ($resultado[-1] -ne '0') { throw 'No se confirmó la limpieza por IDs de T-26.' }
            Write-Host "IDs propios limpiados: ADMIN=$adminId; cliente=$clienteId; servicio=$servicioId; barbero=$barberoId; usuario BARBERO=$usuarioBarberoId; reservas=$listaIds."
            Write-Host ('Limpieza transaccional por IDs: ' + ($resultado -join ', ') + '; restantes=0.')
        }
    } finally {
        foreach ($nombre in $variables) { [Environment]::SetEnvironmentVariable($nombre, $previas[$nombre], 'Process') }
        Write-Host 'Servidores propios detenidos; entorno de credenciales restaurado.'
    }
}
