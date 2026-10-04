# PowerShell 7, Node 24.21.0; limpieza únicamente de los registros ficticios de esta ejecución.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$frontend = Join-Path $raiz 'frontend'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$origen = 'http://localhost:4200'
$sesion = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$servidores = [Collections.Generic.List[object]]::new()
$adminId = $null
$barberoId = $null
$usuarioId = $null
$sufijo = [Guid]::NewGuid().ToString('N')
$correoAdmin = "t18-admin-$sufijo@ejemplo.test"
$correoBarbero = "t18-barbero-$sufijo@ejemplo.test"
$anteriores = @{}
foreach ($clave in @('JAVA_HOME','BT_ADMIN_CORREO','BT_ADMIN_PASSWORD','BT_ADMIN_NOMBRE','PGPASSWORD','BT_T18_BARBERO_ID')) {
    $anteriores[$clave] = [Environment]::GetEnvironmentVariable($clave, 'Process')
}
function Sql($consulta) {
    $salida = @($consulta | & $psql -X -w -v ON_ERROR_STOP=1 -h localhost -p 5433 -U barberturno -d barberturno -At)
    if ($LASTEXITCODE -ne 0) { throw 'Falló PostgreSQL; credenciales omitidas.' }
    return $salida
}
function Peticion($metodo, $ruta, $esperado, $cuerpo = $null, $csrf = 'valido') {
    $opciones = @{ Uri = $origen + $ruta; Method = $metodo; WebSession = $sesion; SkipHttpErrorCheck = $true; TimeoutSec = 15 }
    if ($null -ne $cuerpo) {
        $opciones.ContentType = 'application/json'
        $opciones.Body = ConvertTo-Json -InputObject $cuerpo -Depth 6 -Compress
    }
    if ($csrf -eq 'valido') {
        $token = $sesion.Cookies.GetCookies([Uri]$origen)['XSRF-TOKEN'].Value
        if (-not $token) { throw 'Falta XSRF-TOKEN; valor omitido.' }
        $opciones.Headers = @{ 'X-XSRF-TOKEN' = [Uri]::UnescapeDataString($token) }
    } elseif ($csrf -eq 'incorrecto') { $opciones.Headers = @{ 'X-XSRF-TOKEN' = 'incorrecto' } }
    $respuesta = Invoke-WebRequest @opciones
    Write-Host "$metodo $ruta (CSRF $csrf) -> $($respuesta.StatusCode)"
    if ($respuesta.StatusCode -ne $esperado) { throw "Esperado $esperado; recibido $($respuesta.StatusCode)." }
    if ($respuesta.Content) {
        $json = if ($respuesta.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($respuesta.Content) } else { $respuesta.Content }
        return ConvertFrom-Json -InputObject $json -NoEnumerate
    }
}
try {
    if ((node --version) -ne 'v24.21.0') { throw 'Active Node 24.21.0 con fnm.' }
    if (@(Get-NetTCPConnection -LocalPort 8080,4200 -State Listen -ErrorAction SilentlyContinue).Count) { throw 'Puertos ocupados; se preservan procesos ajenos.' }
    foreach ($linea in [IO.File]::ReadLines((Join-Path $raiz '.local/barberturno.env'))) {
        if ($linea -match '^BT_DB_PASSWORD=(.*)$') { $env:PGPASSWORD = $Matches[1] }
    }
    if (-not $env:PGPASSWORD) { throw 'Falta credencial local para limpiar.' }
    if (@(Sql "SELECT current_database() = 'barberturno' AND current_setting('port') = '5433';")[0] -ne 't') { throw 'Destino inesperado.' }
    if (@(Sql 'SELECT count(*) FROM usuario;')[0] -ne '0') { throw 'Se requiere dev sin usuarios para reconocer el ADMIN inicial propio.' }
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
    $env:BT_ADMIN_CORREO = $correoAdmin
    $env:BT_ADMIN_PASSWORD = 'TemporalT18-' + [Guid]::NewGuid().ToString('N')
    $env:BT_ADMIN_NOMBRE = 'Administrador ficticio T18'
    $null = New-Item -ItemType Directory -Force (Join-Path $frontend 'tmp')
    $servidores.Add((Start-Process -FilePath (Get-Process -Id $PID).Path -WorkingDirectory (Join-Path $raiz 'backend') -ArgumentList @(
        '-NoProfile','-Command', '.\mvnw.cmd spring-boot:run ''-Dspring-boot.run.profiles=dev'''
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $frontend 'tmp/t18-backend.log') -RedirectStandardError (Join-Path $frontend 'tmp/t18-backend-error.log')))
    $servidores.Add((Start-Process -FilePath (Get-Command node).Source -WorkingDirectory $frontend -ArgumentList @(
        'node_modules/@angular/cli/bin/ng.js','serve','--host','localhost'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $frontend 'tmp/t18-frontend.log') -RedirectStandardError (Join-Path $frontend 'tmp/t18-frontend-error.log')))
    $listos = $false
    for ($intento = 0; $intento -lt 90; $intento++) {
        if (@($servidores | Where-Object HasExited).Count) { throw 'Un servidor terminó antes de estar listo.' }
        try {
            $health = Invoke-WebRequest 'http://localhost:8080/actuator/health' -TimeoutSec 2
            $web = Invoke-WebRequest $origen -TimeoutSec 2
            if ($health.StatusCode -eq 200 -and $web.StatusCode -eq 200) { $listos = $true; break }
        } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $listos) { throw 'Los servidores no estuvieron disponibles.' }
    $adminId = [long](@(Sql "SELECT id FROM usuario WHERE correo = '$correoAdmin' AND rol = 'ADMIN';")[0])
    if (-not $adminId) { throw 'No se creó el ADMIN ficticio.' }
    $null = Peticion GET '/api/auth/sesion' 401 $null 'sin'
    $null = Peticion POST '/api/auth/login' 200 @{ correo = $env:BT_ADMIN_CORREO; password = $env:BT_ADMIN_PASSWORD }
    $null = Peticion GET '/api/auth/sesion' 200
    $barbero = Peticion POST '/api/barberos' 201 @{
        nombre = 'Barbero ficticio T18'; correo = $correoBarbero; especialidad = 'Cortes'
        passwordTemporal = 'TemporalT18-' + [Guid]::NewGuid().ToString('N')
    }
    $barberoId = [long]$barbero.id
    $usuarioId = [long](@(Sql "SELECT usuario_id FROM barbero WHERE id = $barberoId;")[0])
    $semana = @(@{ diaSemana = 1; horaInicio = '09:00'; horaFin = '13:00' }, @{ diaSemana = 1; horaInicio = '14:00'; horaFin = '18:00' })
    foreach ($csrf in @('sin','incorrecto')) { $null = Peticion PUT "/api/barberos/$barberoId/jornadas" 403 $semana $csrf }
    $guardada = Peticion PUT "/api/barberos/$barberoId/jornadas" 200 $semana
    if ($guardada.Count -ne 2 -or $guardada[0].horaInicio -ne '09:00') { throw 'Semana inesperada.' }
    $leida = Peticion GET "/api/barberos/$barberoId/jornadas" 200
    if ($leida.Count -ne 2) { throw 'No se conservó la semana.' }
    $invalida = @(@{ diaSemana = 1; horaInicio = '13:00'; horaFin = '09:00' })
    $errorJornada = Peticion PUT "/api/barberos/$barberoId/jornadas" 400 $invalida
    if ($errorJornada.codigo -ne 'JORNADA_INVALIDA' -or $errorJornada.errores[0].campo -ne '[0].horaFin') { throw 'Error indexado inesperado.' }
    $fecha = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'SA Pacific Standard Time').AddDays(1).ToString('yyyy-MM-dd')
    $datos = @{ inicio = $fecha + 'T16:00:00-05:00'; fin = $fecha + 'T17:00:00-05:00'; motivo = 'Trámite ficticio T18' }
    foreach ($csrf in @('sin','incorrecto')) { $null = Peticion POST "/api/barberos/$barberoId/bloqueos" 403 $datos $csrf }
    $bloqueo = Peticion POST "/api/barberos/$barberoId/bloqueos" 201 $datos
    $lista = Peticion GET "/api/barberos/$barberoId/bloqueos?desde=$fecha&hasta=$fecha" 200
    if ($bloqueo.id -notin $lista.id) { throw 'El bloqueo no apareció.' }
    foreach ($csrf in @('sin','incorrecto')) { $null = Peticion DELETE "/api/bloqueos/$($bloqueo.id)" 403 $null $csrf }
    $null = Peticion DELETE "/api/bloqueos/$($bloqueo.id)" 204
    $lista = Peticion GET "/api/barberos/$barberoId/bloqueos?desde=$fecha&hasta=$fecha" 200
    if ($lista.Count -ne 0) { throw 'El bloqueo sigue presente.' }
    $lote = @{ barberoIds = @($barberoId); inicio = $datos.inicio; fin = $datos.fin; motivo = 'Lote ficticio T18' }
    foreach ($csrf in @('sin','incorrecto')) { $null = Peticion POST '/api/bloqueos/lote' 403 $lote $csrf }
    $creados = Peticion POST '/api/bloqueos/lote' 201 $lote
    if ($creados.Count -ne 1 -or $creados[0].barberoId -ne $barberoId) { throw 'Lote inesperado.' }
    $null = Peticion DELETE "/api/bloqueos/$($creados[0].id)" 204
    Write-Host '409 de jornada: sin API pública de reservas en T-18; cubierto por JornadaIT (48 pruebas).'
    $env:BT_T18_BARBERO_ID = [string]$barberoId
    & node (Join-Path $PSScriptRoot 'verificar-horarios-visual.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Falló QA visual de horarios.' }
    $null = Peticion POST '/api/auth/logout' 204
    Write-Host "HTTP completado: ADMIN $adminId; barbero $barberoId; usuario BARBERO $usuarioId."
} finally {
    foreach ($proceso in $servidores) { if (-not $proceso.HasExited) { taskkill.exe /PID $proceso.Id /T /F | Out-Null } }
    try {
        if ($servidores.Count -and -not $adminId) {
            $ids = @(Sql "SELECT id FROM usuario WHERE correo = '$correoAdmin' AND rol = 'ADMIN';")
            if ($ids.Count) { $adminId = [long]$ids[0] }
        }
        if (-not $usuarioId) {
            $ids = @(Sql "SELECT id FROM usuario WHERE correo = '$correoBarbero' AND rol = 'BARBERO';")
            if ($ids.Count) { $usuarioId = [long]$ids[0] }
        }
        if ($usuarioId -and -not $barberoId) {
            $ids = @(Sql "SELECT id FROM barbero WHERE usuario_id = $usuarioId;")
            if ($ids.Count) { $barberoId = [long]$ids[0] }
        }
        $sentencias = [Collections.Generic.List[string]]::new()
        if ($barberoId) {
            $sentencias.Add("DELETE FROM bloqueo WHERE barbero_id = $barberoId;")
            $sentencias.Add("DELETE FROM jornada WHERE barbero_id = $barberoId;")
            $sentencias.Add("DELETE FROM barbero WHERE id = $barberoId AND usuario_id = $usuarioId;")
        }
        if ($usuarioId) { $sentencias.Add("DELETE FROM usuario WHERE id = $usuarioId AND correo = '$correoBarbero';") }
        if ($adminId) { $sentencias.Add("DELETE FROM usuario WHERE id = $adminId AND correo = '$correoAdmin' AND rol = 'ADMIN';") }
        if ($sentencias.Count) {
            $salida = @(Sql ('BEGIN;' + [Environment]::NewLine + ($sentencias -join [Environment]::NewLine) + [Environment]::NewLine + 'COMMIT;'))
            if ($salida[-1] -ne 'COMMIT') { throw 'No se confirmó la limpieza.' }
            $restantes = Sql "SELECT count(*) FROM usuario WHERE id IN ($([long]$adminId), $([long]$usuarioId));"
            if ($restantes[0] -ne '0') { throw 'Quedan registros propios.' }
            Write-Host ('Limpieza por IDs: ' + ($salida -join ', ') + '; cero usuarios propios restantes.')
        }
    } finally {
        foreach ($clave in $anteriores.Keys) { [Environment]::SetEnvironmentVariable($clave, $anteriores[$clave], 'Process') }
        Write-Host 'Servidores propios detenidos y entorno restaurado.'
    }
}
