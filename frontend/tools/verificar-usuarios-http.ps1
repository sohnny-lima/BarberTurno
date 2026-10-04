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
$marcador = [Guid]::NewGuid().ToString('N')
$correoAdmin = 'admin-t31-' + $marcador + '@ejemplo.test'
$correoBarbero = 'barbero-t31-' + $marcador + '@ejemplo.test'
$correoCliente = 'cliente-t31-' + $marcador + '@ejemplo.test'
$nombreServicio = 'Corte T31 ' + $marcador
$variables = @('JAVA_HOME', 'BT_ADMIN_CORREO', 'BT_ADMIN_PASSWORD', 'BT_ADMIN_NOMBRE', 'BT_DB_PASSWORD', 'BT_DB_USER', 'BT_DB_URL', 'PGPASSWORD', 'BT_T31_CLIENTE_CORREO', 'BT_T31_CLIENTE_PASSWORD', 'BT_T31_FECHA', 'BT_T31_BARBERO_ID', 'BT_T31_SERVICIO_ID')
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
    $env:BT_ADMIN_PASSWORD = 'FicticiaT31-' + [Guid]::NewGuid().ToString('N')
    $env:BT_ADMIN_NOMBRE = 'Administrador ficticio T31'
    $env:BT_T31_CLIENTE_CORREO = $correoCliente
    $env:BT_T31_CLIENTE_PASSWORD = 'FicticiaT31-' + [Guid]::NewGuid().ToString('N')
    $jar = Join-Path $raiz 'backend/target/barberturno-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Falta el JAR de verify.' }
    $servidores.Add((Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-jar', $jar, '--spring.profiles.active=dev') -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $salida 't31-backend.log') -RedirectStandardError (Join-Path $salida 't31-backend-error.log')))
    $servidores.Add((Start-Process -FilePath (Get-Command node).Source -ArgumentList @('node_modules/@angular/cli/bin/ng.js', 'serve', '--host', 'localhost') -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $salida 't31-frontend.log') -RedirectStandardError (Join-Path $salida 't31-frontend-error.log')))
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
        nombre = 'Profesional ficticio T31'; correo = $correoBarbero
        telefono = '999000030'; especialidad = 'Corte ficticio'
        passwordTemporal = 'FicticiaT31-' + [Guid]::NewGuid().ToString('N')
    }
    $barberoId = [long]$barbero.id
    $usuarioBarberoId = [long](@(Sql "SELECT usuario_id FROM barbero WHERE id = $barberoId;")[0])
    $semana = @(1..7 | ForEach-Object { @{ diaSemana = $_; horaInicio = '09:00'; horaFin = '18:00' } })
    $null = Peticion $admin PUT "/api/barberos/$barberoId/jornadas" 200 $semana
    $null = Peticion $cliente GET '/api/auth/sesion' 401
    $identidad = Peticion $cliente POST '/api/auth/registro' 201 @{
        nombre = 'Cliente ficticio T31'; correo = $correoCliente
        telefono = '999000031'; password = $env:BT_T31_CLIENTE_PASSWORD; aceptaPrivacidad = $true
    }
    $clienteId = [long]$identidad.id
    $null = Peticion $cliente GET '/api/auth/sesion' 200
    $fecha = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'SA Pacific Standard Time').AddDays(1).ToString('yyyy-MM-dd')
    $env:BT_T31_FECHA = $fecha
    $disponibilidad = Peticion $cliente GET "/api/disponibilidad?servicioId=$servicioId&barberoId=$barberoId&fecha=$fecha" 200
    if ($disponibilidad.franjas.Count -lt 9) { throw 'No hay suficientes franjas ficticias.' }
    $reservas = @()
    foreach ($indice in @(0,4,8,12)) {
        $reservas += Peticion $admin POST '/api/reservas' 201 @{
            servicioId = $servicioId; barberoId = $barberoId; inicio = $disponibilidad.franjas[$indice].inicio; clienteId = $clienteId
        }
    }
    if ($reservas.Count -ne 4 -or @($reservas | Where-Object estado -ne 'CONFIRMADA').Count) { throw 'RN-20/21 asistida incumplidas.' }
    $null = Peticion $admin POST '/api/reservas' 409 @{servicioId=$servicioId;barberoId=$barberoId;inicio=$reservas[0].inicio;clienteId=$clienteId}
    $usuarios = Peticion $admin GET "/api/usuarios?rol=CLIENTE&q=CLIENTE&pagina=0&tamano=1" 200
    if ($usuarios.totalElementos -ne 1 -or $usuarios.contenido[0].id -ne $clienteId) {throw 'Búsqueda incorrecta.'}
    $propiedades = @($usuarios.contenido[0].PSObject.Properties.Name)
    if (@($propiedades | Where-Object {$_ -in @('passwordHash','tokenVersion','intentosFallidos','bloqueadoHasta')}).Count) {throw 'La búsqueda expone datos internos.'}
    foreach ($csrf in @('ausente','incorrecto')) {
        $null = Peticion $admin POST "/api/usuarios/$clienteId/restablecer-password" 403 $null $csrf
        $null = Peticion $admin PATCH "/api/usuarios/$clienteId/estado" 403 @{activo=$false} $csrf
        $null = Peticion $admin POST '/api/reservas' 403 @{servicioId=$servicioId;barberoId=$barberoId;inicio=$disponibilidad.franjas[16].inicio;clienteId=$clienteId} $csrf
    }
    $null = Peticion $cliente GET '/api/usuarios' 403
    $null = Peticion $cliente POST "/api/usuarios/$clienteId/restablecer-password" 403
    $null = Peticion $cliente PATCH "/api/usuarios/$clienteId/estado" 403 @{activo=$false}
    $null = Peticion $cliente POST '/api/reservas' 403 @{servicioId=$servicioId;barberoId=$barberoId;inicio=$disponibilidad.franjas[16].inicio;clienteId=$clienteId}
    $null = Peticion $admin PATCH "/api/usuarios/$adminId/estado" 409 @{activo=$false}
    $temporal = Peticion $admin POST "/api/usuarios/$clienteId/restablecer-password" 200
    if ($temporal.passwordTemporal.Length -ne 12 -or $temporal.passwordTemporal -notmatch '[A-Za-z]' -or $temporal.passwordTemporal -notmatch '[0-9]') {throw 'Temporal inválida, valor omitido.'}
    $null = Peticion $cliente GET '/api/auth/sesion' 401
    $null = Peticion $cliente POST '/api/auth/logout' 204
    $sesionTemporal = Peticion $cliente POST '/api/auth/login' 200 @{correo=$correoCliente;password=$temporal.passwordTemporal}
    if (-not $sesionTemporal.debeCambiarPassword) {throw 'No exige cambio.'}
    $null = Peticion $cliente GET '/api/notificaciones' 403
    $env:BT_T31_CLIENTE_PASSWORD = 'FicticiaT31-' + [Guid]::NewGuid().ToString('N')
    $null = Peticion $cliente PUT '/api/auth/password' 204 @{passwordActual=$temporal.passwordTemporal;passwordNueva=$env:BT_T31_CLIENTE_PASSWORD}
    $temporal = $null
    $null = Peticion $cliente GET '/api/auth/sesion' 200
    $null = Peticion $admin PATCH "/api/usuarios/$clienteId/estado" 200 @{activo=$false}
    $null = Peticion $cliente GET '/api/auth/sesion' 401
    $null = Peticion $admin PATCH "/api/usuarios/$clienteId/estado" 200 @{activo=$true}
    $null = Peticion $cliente GET '/api/auth/sesion' 401
    $ids = ($reservas.id -join ',')
    if (@(Sql "SELECT count(*) FROM reserva WHERE id IN ($ids) AND creada_por=$adminId AND cliente_id=$clienteId AND estado='CONFIRMADA';")[0] -ne '4') {throw 'Creador asistido incorrecto.'}
    if (@(Sql "SELECT count(*) FROM auditoria_reserva WHERE reserva_id IN ($ids) AND actor_id=$adminId AND accion='CREAR';")[0] -ne '4') {throw 'Auditoría asistida incorrecta.'}
    if (@(Sql "SELECT count(*) FROM notificacion WHERE reserva_id IN ($ids) AND usuario_id IN ($clienteId,$usuarioBarberoId);")[0] -ne '8') {throw 'Avisos incompletos.'}
    $env:BT_T31_SERVICIO_ID = [string]$servicioId
    $env:BT_T31_BARBERO_ID = [string]$barberoId
    & node (Join-Path $PSScriptRoot 'verificar-usuarios-visual.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Falló QA visual de usuarios y reserva asistida.' }
    $null = Peticion $cliente POST '/api/auth/logout' 204
    $null = Peticion $admin POST '/api/auth/logout' 204
    Write-Host 'Recorrido T-31 aprobado: cuatro reservas asistidas, actor ADMIN, ocho avisos, CP-17 y CSRF; UI usuarios y modo asistido.'
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
            if ($resultado[-1] -ne '0') { throw 'No se confirmó la limpieza por IDs de T-31.' }
            Write-Host "IDs propios limpiados: ADMIN=$adminId; cliente=$clienteId; servicio=$servicioId; barbero=$barberoId; usuario BARBERO=$usuarioBarberoId; reservas=$listaIds."
            Write-Host ('Limpieza transaccional por IDs: ' + ($resultado -join ', ') + '; restantes=0.')
        }
    } finally {
        foreach ($nombre in $variables) { [Environment]::SetEnvironmentVariable($nombre, $previas[$nombre], 'Process') }
        Write-Host 'Servidores propios detenidos; entorno de credenciales restaurado.'
    }
}
