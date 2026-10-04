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
$correoAdmin = 'admin-t28-' + $marcador + '@ejemplo.test'
$correoBarbero = 'barbero-t28-' + $marcador + '@ejemplo.test'
$correoCliente = 'cliente-t28-' + $marcador + '@ejemplo.test'
$nombreServicio = 'Corte T28 ' + $marcador
$variables = @('JAVA_HOME', 'BT_ADMIN_CORREO', 'BT_ADMIN_PASSWORD', 'BT_ADMIN_NOMBRE', 'BT_DB_PASSWORD', 'BT_DB_USER', 'BT_DB_URL', 'PGPASSWORD', 'BT_T28_CLIENTE_CORREO', 'BT_T28_CLIENTE_PASSWORD', 'BT_T28_RESERVA_ID', 'BT_T28_FECHA', 'BT_T28_BARBERO_CORREO', 'BT_T28_BARBERO_PASSWORD', 'BT_T28_FASE')
$previas = @{}
foreach ($nombre in $variables) { $previas[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$admin = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$cliente = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$personal = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
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
    $env:BT_ADMIN_PASSWORD = 'FicticiaT28-' + [Guid]::NewGuid().ToString('N')
    $env:BT_ADMIN_NOMBRE = 'Administrador ficticio T28'
    $env:BT_T28_CLIENTE_CORREO = $correoCliente
    $env:BT_T28_CLIENTE_PASSWORD = 'FicticiaT28-' + [Guid]::NewGuid().ToString('N')
    $jar = Join-Path $raiz 'backend/target/barberturno-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Falta el JAR de verify.' }
    $servidores.Add((Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-jar', $jar, '--spring.profiles.active=dev') -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $salida 't28-backend.log') -RedirectStandardError (Join-Path $salida 't28-backend-error.log')))
    $servidores.Add((Start-Process -FilePath (Get-Command node).Source -ArgumentList @('node_modules/@angular/cli/bin/ng.js', 'serve', '--host', 'localhost') -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $salida 't28-frontend.log') -RedirectStandardError (Join-Path $salida 't28-frontend-error.log')))
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
    $env:BT_T28_BARBERO_CORREO = $correoBarbero
    $passwordTemporal = 'Ficticia28-' + [Guid]::NewGuid().ToString('N')
    $env:BT_T28_BARBERO_PASSWORD = 'NuevaFicticia28-' + [Guid]::NewGuid().ToString('N')
    $barbero = Peticion $admin POST '/api/barberos' 201 @{
        nombre = 'Profesional ficticio T28'; correo = $correoBarbero
        telefono = '999000026'; especialidad = 'Corte ficticio'; passwordTemporal = $passwordTemporal
    }
    $barberoId = [long]$barbero.id
    $usuarioBarberoId = [long](@(Sql "SELECT usuario_id FROM barbero WHERE id = $barberoId;")[0])
    $semana = @(1..7 | ForEach-Object { @{ diaSemana = $_; horaInicio = '09:00'; horaFin = '18:00' } })
    $null = Peticion $admin PUT "/api/barberos/$barberoId/jornadas" 200 $semana
    $null = Peticion $cliente GET '/api/auth/sesion' 401
    $identidad = Peticion $cliente POST '/api/auth/registro' 201 @{
        nombre = 'Cliente ficticio T28'; correo = $correoCliente
        telefono = '999000027'; password = $env:BT_T28_CLIENTE_PASSWORD; aceptaPrivacidad = $true
    }
    $clienteId = [long]$identidad.id
    $null = Peticion $cliente GET '/api/auth/sesion' 200
    $env:BT_T28_FECHA = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'SA Pacific Standard Time').AddDays(1).ToString('yyyy-MM-dd')
    $rutaDisponibilidad = "/api/disponibilidad?servicioId=$servicioId&barberoId=$barberoId&fecha=$env:BT_T28_FECHA"
    $disponibilidad = Peticion $cliente GET $rutaDisponibilidad 200
    if ($disponibilidad.franjas.Count -lt 4) { throw 'La jornada ficticia no produjo suficientes franjas.' }
    $crear = @{ servicioId = $servicioId; barberoId = $barberoId; inicio = $disponibilidad.franjas[0].inicio }
    $null = Peticion $cliente POST '/api/reservas' 403 $crear 'ausente'
    $null = Peticion $cliente POST '/api/reservas' 403 $crear 'incorrecto'
    $reserva = Peticion $cliente POST '/api/reservas' 201 $crear
    $reservaId = [long]$reserva.id
    $null = Peticion $personal GET '/api/auth/sesion' 401
    $identidadBarbero = Peticion $personal POST '/api/auth/login' 200 @{ correo = $correoBarbero; password = $passwordTemporal }
    if (-not $identidadBarbero.debeCambiarPassword) { throw 'No se exigió cambiar la contraseña temporal.' }
    $null = Peticion $personal GET '/api/auth/sesion' 200
    $null = Peticion $personal PUT '/api/auth/password' 204 @{ passwordActual = $passwordTemporal; passwordNueva = $env:BT_T28_BARBERO_PASSWORD }
    $null = Peticion $personal POST '/api/auth/login' 200 @{ correo = $correoBarbero; password = $env:BT_T28_BARBERO_PASSWORD }
    $null = Peticion $personal GET '/api/auth/sesion' 200
    $env:BT_T28_RESERVA_ID = [string]$reservaId
    $env:BT_T28_FASE = 'futura'
    & node (Join-Path $PSScriptRoot 'verificar-agenda-visual.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Falló QA visual de agenda futura.' }

    # Solo la reserva ficticia propia: trae la cita a un minuto antes de ahora.
    # La API la creó en una franja válida de mañana; no se modifica Clock ni se promueven roles.
    $null = Sql "UPDATE reserva SET inicio = date_trunc('minute', current_timestamp) - interval '1 minute', fin = date_trunc('minute', current_timestamp) + interval '29 minutes', version = version + 1 WHERE id = $reservaId AND cliente_id = $clienteId AND barbero_id = $barberoId AND servicio_id = $servicioId;"
    $hoy = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'SA Pacific Standard Time').ToString('yyyy-MM-dd')
    $agenda = Peticion $personal GET "/api/reservas?desde=$hoy&hasta=$hoy&tamano=100&pagina=0" 200
    if ($agenda.totalElementos -ne 1 -or $agenda.contenido[0].id -ne $reservaId) { throw 'El barbero no ve su agenda del día.' }
    $actual = $agenda.contenido[0]
    if ($actual.permisos.transiciones -notcontains 'EN_ATENCION') { throw 'La cita no quedó dentro de la ventana real.' }
    $otra = Peticion $personal GET "/api/reservas?desde=$hoy&hasta=$hoy&barberoId=$($barberoId + 10000)" 403
    foreach ($csrf in @('ausente','incorrecto')) {
        $null = Peticion $personal POST "/api/reservas/$reservaId/transiciones" 403 @{ estado = 'EN_ATENCION'; version = $actual.version } $csrf
    }
    $null = Peticion $cliente POST "/api/reservas/$reservaId/transiciones" 403 @{ estado = 'EN_ATENCION'; version = $actual.version }
    $atencion = Peticion $personal POST "/api/reservas/$reservaId/transiciones" 200 @{ estado = 'EN_ATENCION'; version = $actual.version }
    if ($atencion.estado -ne 'EN_ATENCION' -or $atencion.version -ne ($actual.version + 1)) { throw 'No se inició con versión.' }
    $conflicto = Peticion $personal POST "/api/reservas/$reservaId/transiciones" 409 @{ estado = 'EN_ATENCION'; version = $actual.version }
    if ($conflicto.codigo -ne 'VERSION_DESACTUALIZADA') { throw 'No se detectó la versión desactualizada.' }
    $final = Peticion $personal POST "/api/reservas/$reservaId/transiciones" 200 @{ estado = 'COMPLETADA'; version = $atencion.version }
    if ($final.estado -ne 'COMPLETADA' -or $final.version -ne ($atencion.version + 1)) { throw 'No se completó con versión.' }
    $auditoria = @(Peticion $admin GET "/api/reservas/$reservaId/auditoria" 200)
    if (($auditoria.accion -join ',') -ne 'CREAR,INICIAR,COMPLETAR') { throw 'Auditoría inesperada.' }
    if ($auditoria[1].actorNombre -ne 'Profesional ficticio T28' -or $auditoria[2].estadoNuevo -ne 'COMPLETADA') { throw 'Actor o estados incorrectos.' }
    $env:BT_T28_FASE = 'completada'
    $env:BT_T28_FECHA = $hoy
    & node (Join-Path $PSScriptRoot 'verificar-agenda-visual.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Falló QA visual de auditoría.' }
    $null = Peticion $personal POST '/api/auth/logout' 204
    $null = Peticion $cliente POST '/api/auth/logout' 204
    $null = Peticion $admin POST '/api/auth/logout' 204
    Write-Host "Recorrido T-28 aprobado: ADMIN=$adminId; BARBERO=$usuarioBarberoId; CLIENTE=$clienteId; servicio=$servicioId; barbero=$barberoId; reserva=$reservaId; version=$($final.version); CREAR,INICIAR,COMPLETAR."
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
            if ($resultado[-1] -ne '0') { throw 'No se confirmó la limpieza por IDs de T-28.' }
            Write-Host "IDs propios limpiados: ADMIN=$adminId; cliente=$clienteId; servicio=$servicioId; barbero=$barberoId; usuario BARBERO=$usuarioBarberoId; reservas=$listaIds."
            Write-Host ('Limpieza transaccional por IDs: ' + ($resultado -join ', ') + '; restantes=0.')
        }
    } finally {
        foreach ($nombre in $variables) { [Environment]::SetEnvironmentVariable($nombre, $previas[$nombre], 'Process') }
        Write-Host 'Servidores propios detenidos; entorno de credenciales restaurado.'
    }
}
