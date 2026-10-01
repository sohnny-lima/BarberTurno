# Ejecutar desde cualquier directorio con PowerShell 7 y Node 24.21.0 activado.
# Inicia solo servidores propios; borra exclusivamente el usuario ficticio creado.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$frontend = Join-Path $raiz 'frontend'
$backend = Join-Path $raiz 'backend'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$procesoBackend = $null
$procesoFrontend = $null
$usuarioId = $null
$correo = 't12-' + [Guid]::NewGuid().ToString('N') + '@ejemplo.test'
$sesion = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$origen = 'http://localhost:4200'
$codigoLimpieza = 0

function Peticion($metodo, $ruta, $esperado, $cuerpo = $null, $csrf = $false) {
    $opciones = @{
        Uri = $origen + $ruta; Method = $metodo; WebSession = $sesion
        SkipHttpErrorCheck = $true; TimeoutSec = 15
    }
    if ($null -ne $cuerpo) {
        $opciones.ContentType = 'application/json'
        $opciones.Body = $cuerpo | ConvertTo-Json -Compress
    }
    if ($csrf) {
        $token = $sesion.Cookies.GetCookies([Uri]$origen)['XSRF-TOKEN'].Value
        if (-not $token) { throw 'Falta la cookie XSRF-TOKEN.' }
        $opciones.Headers = @{ 'X-XSRF-TOKEN' = [Uri]::UnescapeDataString($token) }
    }
    $respuesta = Invoke-WebRequest @opciones
    Write-Output "$metodo $ruta -> $($respuesta.StatusCode)" | Out-Host
    if ($respuesta.StatusCode -ne $esperado) {
        throw "Estado inesperado en $metodo $ruta; esperado $esperado."
    }
    return $respuesta
}

try {
    if ((node --version) -ne 'v24.21.0') { throw 'Active Node 24.21.0 con fnm antes de ejecutar.' }
    $escuchas = @(Get-NetTCPConnection -LocalPort 8080,4200 -State Listen -ErrorAction SilentlyContinue)
    if ($escuchas.Count) { throw 'Los puertos 8080 y 4200 deben estar libres; no se detienen procesos ajenos.' }
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
    $procesoBackend = Start-Process -FilePath (Get-Process -Id $PID).Path -WorkingDirectory $backend -ArgumentList @(
        '-NoProfile', '-Command', '.\mvnw.cmd spring-boot:run ''-Dspring-boot.run.profiles=dev'''
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $frontend 't12-backend.log') -RedirectStandardError (Join-Path $frontend 't12-backend-error.log')
    $procesoFrontend = Start-Process -FilePath (Get-Command node).Source -WorkingDirectory $frontend -ArgumentList @(
        'node_modules/@angular/cli/bin/ng.js', 'serve', '--host', 'localhost'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $frontend 't12-frontend.log') -RedirectStandardError (Join-Path $frontend 't12-frontend-error.log')
    $listos = $false
    for ($intento = 0; $intento -lt 90; $intento++) {
        if ($procesoBackend.HasExited -or $procesoFrontend.HasExited) { throw 'Un servidor terminó antes de estar disponible; consulte los logs locales.' }
        try {
            $health = Invoke-WebRequest 'http://localhost:8080/actuator/health' -TimeoutSec 2
            $web = Invoke-WebRequest $origen -TimeoutSec 2
            if ($health.StatusCode -eq 200 -and $web.StatusCode -eq 200) { $listos = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $listos) { throw 'Los servidores no quedaron disponibles; consulte los logs locales del frontend.' }

    $null = Peticion GET '/api/auth/sesion' 401
    if (-not $sesion.Cookies.GetCookies([Uri]$origen)['XSRF-TOKEN']) { throw 'El GET inicial no emitió XSRF-TOKEN.' }
    Write-Output 'Cookie XSRF presente; valor omitido.'
    $respuesta = Peticion POST '/api/auth/registro' 201 @{
        nombre = 'Cliente ficticio T12'; correo = $correo; telefono = '999000012'
        password = 'FicticiaT12-' + [Guid]::NewGuid().ToString('N'); aceptaPrivacidad = $true
    } $true
    $usuario = $respuesta.Content | ConvertFrom-Json
    $usuarioId = [long]$usuario.id
    if ($usuario.correo -ne $correo -or $usuario.rol -ne 'CLIENTE') { throw 'La identidad creada no coincide.' }
    Write-Output "Usuario ficticio creado: id=$usuarioId; correo omitido."
    $perfil = (Peticion GET '/api/perfil' 200).Content | ConvertFrom-Json
    if ($perfil.id -ne $usuarioId) { throw 'El perfil no pertenece al usuario creado.' }
    $datos = @{ nombre = 'Cliente ficticio T12 actualizado'; telefono = '999000013' }
    $null = Peticion PUT '/api/perfil' 403 $datos
    $respuestaCsrf = Invoke-WebRequest ($origen + '/api/perfil') -Method PUT -WebSession $sesion -SkipHttpErrorCheck -ContentType 'application/json' -Body ($datos | ConvertTo-Json -Compress) -Headers @{ 'X-XSRF-TOKEN' = 'incorrecto' }
    Write-Output "PUT /api/perfil con CSRF incorrecto -> $($respuestaCsrf.StatusCode)"
    if ($respuestaCsrf.StatusCode -ne 403) { throw 'CSRF incorrecto no fue rechazado.' }
    $actualizado = (Peticion PUT '/api/perfil' 200 $datos $true).Content | ConvertFrom-Json
    if ($actualizado.nombre -ne $datos.nombre -or $actualizado.telefono -ne $datos.telefono) { throw 'El perfil no conservó los cambios.' }
    $null = Peticion POST '/api/auth/logout' 204 $null $true
    $null = Peticion GET '/api/perfil' 401
    Write-Output 'Recorrido HTTP de identidad completado.'
} finally {
    if ($null -ne $usuarioId) {
        $passwordAnterior = $env:PGPASSWORD
        try {
            # Se carga solo la clave requerida en el entorno del proceso, sin imprimirla.
            foreach ($linea in [IO.File]::ReadLines((Join-Path $raiz '.local/barberturno.env'))) {
                if ($linea -match '^BT_DB_PASSWORD=(.*)$') { $env:PGPASSWORD = $Matches[1] }
            }
            if (-not $env:PGPASSWORD) { throw 'No se pudo cargar la credencial de limpieza.' }
            $sql = @"
\set ON_ERROR_STOP on
SELECT current_database() = 'barberturno' AND current_setting('port') = '5433' AS destino
\gset
\if :destino
DELETE FROM usuario WHERE id = $usuarioId AND correo = '$correo' AND rol = 'CLIENTE'
  AND NOT EXISTS (SELECT 1 FROM reserva WHERE cliente_id = $usuarioId)
  AND NOT EXISTS (SELECT 1 FROM barbero WHERE usuario_id = $usuarioId)
RETURNING id;
SELECT count(*) AS restantes FROM usuario WHERE id = $usuarioId AND correo = '$correo';
\else
\quit 3
\endif
"@
            $resultado = $sql | & $psql -X -w -h localhost -p 5433 -U barberturno -d barberturno -At
            if ($LASTEXITCODE -ne 0 -or $resultado -notcontains 'DELETE 1' -or $resultado[-1] -ne '0') { throw 'La limpieza no confirmó DELETE 1 y cero filas restantes.' }
            Write-Output "Limpieza limitada al usuario creado id=${usuarioId}: DELETE 1; restantes=0."
        } catch {
            $codigoLimpieza = 1
            Write-Output "No se pudo confirmar la limpieza del usuario ficticio id=$usuarioId."
            throw
        } finally {
            $env:PGPASSWORD = $passwordAnterior
            foreach ($proceso in @($procesoFrontend, $procesoBackend)) {
                if ($null -ne $proceso -and -not $proceso.HasExited) { taskkill.exe /PID $proceso.Id /T /F | Out-Null }
            }
        }
    } else {
        foreach ($proceso in @($procesoFrontend, $procesoBackend)) {
            if ($null -ne $proceso -and -not $proceso.HasExited) { taskkill.exe /PID $proceso.Id /T /F | Out-Null }
        }
    }
    Write-Output 'Árboles de procesos propios detenidos.'
}
if ($codigoLimpieza) { exit $codigoLimpieza }
