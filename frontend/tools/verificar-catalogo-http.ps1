# PowerShell 7; Node 24.21.0 activo. Datos ficticios, sin imprimir credenciales ni cookies.
# La eliminación física se limita a los IDs creados por este recorrido, autorizado en T-17.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$frontend = Join-Path $raiz 'frontend'
$backend = Join-Path $raiz 'backend'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$origen = 'http://localhost:4200'
$sesion = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$procesoBackend = $null
$procesoFrontend = $null
$servicioId = $null
$barberoId = $null
$usuarioBarberoId = $null
$adminId = $null
$sufijo = [Guid]::NewGuid().ToString('N')
$correoAdmin = "t17-admin-$sufijo@ejemplo.test"
$correoBarbero = "t17-barbero-$sufijo@ejemplo.test"
$variablesAnteriores = @{}
foreach ($clave in @('JAVA_HOME', 'BT_ADMIN_CORREO', 'BT_ADMIN_PASSWORD', 'BT_ADMIN_NOMBRE', 'PGPASSWORD')) {
    $variablesAnteriores[$clave] = [Environment]::GetEnvironmentVariable($clave, 'Process')
}
function Sql($consulta) {
    $salida = @($consulta | & $psql -X -w -h localhost -p 5433 -U barberturno -d barberturno -At)
    if ($LASTEXITCODE -ne 0) { throw 'La consulta PostgreSQL falló; no se imprimen credenciales.' }
    return $salida
}
function Peticion($metodo, $ruta, $esperado, $cuerpo = $null, $csrf = 'valido') {
    $opciones = @{
        Uri = $origen + $ruta; Method = $metodo; WebSession = $sesion
        SkipHttpErrorCheck = $true; TimeoutSec = 15
    }
    if ($null -ne $cuerpo) {
        $opciones.ContentType = 'application/json'
        $opciones.Body = $cuerpo | ConvertTo-Json -Compress
    }
    if ($csrf -eq 'valido') {
        $token = $sesion.Cookies.GetCookies([Uri]$origen)['XSRF-TOKEN'].Value
        if (-not $token) { throw 'Falta la cookie XSRF-TOKEN; valor omitido.' }
        $opciones.Headers = @{ 'X-XSRF-TOKEN' = [Uri]::UnescapeDataString($token) }
    } elseif ($csrf -eq 'incorrecto') {
        $opciones.Headers = @{ 'X-XSRF-TOKEN' = 'incorrecto' }
    }
    $respuesta = Invoke-WebRequest @opciones
    Write-Host "$metodo $ruta (CSRF $csrf) -> $($respuesta.StatusCode)"
    if ($respuesta.StatusCode -ne $esperado) { throw "Estado inesperado: esperado $esperado, recibido $($respuesta.StatusCode)." }
    if ($respuesta.Content) { return $respuesta.Content | ConvertFrom-Json }
}
try {
    if ((node --version) -ne 'v24.21.0') { throw 'Active Node 24.21.0 con fnm.' }
    if (@(Get-NetTCPConnection -LocalPort 8080,4200 -State Listen -ErrorAction SilentlyContinue).Count) {
        throw '8080/4200 deben estar libres; se preservan los procesos ajenos.'
    }
    foreach ($linea in [IO.File]::ReadLines((Join-Path $raiz '.local/barberturno.env'))) {
        if ($linea -match '^BT_DB_PASSWORD=(.*)$') { $env:PGPASSWORD = $Matches[1] }
    }
    if (-not $env:PGPASSWORD) { throw 'No hay credencial local para la limpieza.' }
    $destino = Sql "SELECT current_database() = 'barberturno' AND current_setting('port') = '5433';"
    if ($destino[0] -ne 't') { throw 'Destino PostgreSQL inesperado.' }
    $usuarios = Sql 'SELECT count(*) FROM usuario;'
    if ($usuarios[0] -ne '0') { throw 'Este recorrido requiere la base dev sin usuarios para reconocer el ADMIN que crea.' }
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
    $env:BT_ADMIN_CORREO = $correoAdmin
    $env:BT_ADMIN_PASSWORD = 'TemporalT17-' + [Guid]::NewGuid().ToString('N')
    $env:BT_ADMIN_NOMBRE = 'Administrador ficticio T17'
    $null = New-Item -ItemType Directory -Force (Join-Path $frontend 'tmp')
    $procesoBackend = Start-Process -FilePath (Get-Process -Id $PID).Path -WorkingDirectory $backend -ArgumentList @(
        '-NoProfile', '-Command', '.\mvnw.cmd spring-boot:run ''-Dspring-boot.run.profiles=dev'''
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $frontend 'tmp/t17-backend.log') -RedirectStandardError (Join-Path $frontend 'tmp/t17-backend-error.log')
    $procesoFrontend = Start-Process -FilePath (Get-Command node).Source -WorkingDirectory $frontend -ArgumentList @(
        'node_modules/@angular/cli/bin/ng.js', 'serve', '--host', 'localhost'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $frontend 'tmp/t17-frontend.log') -RedirectStandardError (Join-Path $frontend 'tmp/t17-frontend-error.log')
    $listos = $false
    for ($intento = 0; $intento -lt 90; $intento++) {
        if ($procesoBackend.HasExited -or $procesoFrontend.HasExited) { throw 'Un servidor terminó antes de estar disponible.' }
        try {
            $health = Invoke-WebRequest 'http://localhost:8080/actuator/health' -TimeoutSec 2
            $web = Invoke-WebRequest $origen -TimeoutSec 2
            if ($health.StatusCode -eq 200 -and $web.StatusCode -eq 200) { $listos = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $listos) { throw 'Los servidores no quedaron disponibles.' }
    $adminId = [long](@(Sql "SELECT id FROM usuario WHERE correo = '$correoAdmin' AND rol = 'ADMIN';")[0])
    if (-not $adminId) { throw 'No se creó el administrador ficticio.' }
    $null = Peticion GET '/api/auth/sesion' 401 $null 'sin'
    $admin = Peticion POST '/api/auth/login' 200 @{ correo = $env:BT_ADMIN_CORREO; password = $env:BT_ADMIN_PASSWORD }
    if ($admin.id -ne $adminId -or $admin.rol -ne 'ADMIN') { throw 'Sesión administrativa inesperada.' }
    $null = Peticion GET '/api/auth/sesion' 200
    $servicio = @{ nombre = "Servicio ficticio T17 $sufijo"; descripcion = 'Recorrido HTTP'; duracionMin = 30; precio = 25.50 }
    foreach ($csrf in @('sin', 'incorrecto')) { $null = Peticion POST '/api/servicios' 403 $servicio $csrf }
    $creado = Peticion POST '/api/servicios' 201 $servicio
    $servicioId = [long]$creado.id
    $barbero = @{ nombre = 'Barbero ficticio T17'; correo = $correoBarbero; telefono = '999000017'; especialidad = 'Corte y barba'; passwordTemporal = 'TemporalT17-' + [Guid]::NewGuid().ToString('N') }
    foreach ($csrf in @('sin', 'incorrecto')) { $null = Peticion POST '/api/barberos' 403 $barbero $csrf }
    $creado = Peticion POST '/api/barberos' 201 $barbero
    $barberoId = [long]$creado.id
    $usuarioBarberoId = [long](@(Sql "SELECT usuario_id FROM barbero WHERE id = $barberoId;")[0])
    $listaServicios = @(Peticion GET '/api/servicios?incluirInactivos=true' 200)
    $listaBarberos = @(Peticion GET '/api/barberos?incluirInactivos=true' 200)
    if ($servicioId -notin $listaServicios.id -or $barberoId -notin $listaBarberos.id) { throw 'Los listados no incluyen las altas.' }
    $edicionBarbero = @{ nombre = 'Barbero ficticio T17 editado'; telefono = '999000018'; especialidad = 'Barba' }
    foreach ($caso in @(
        @{ ruta = "/api/servicios/$servicioId"; cuerpo = $servicio },
        @{ ruta = "/api/barberos/$barberoId"; cuerpo = $edicionBarbero }
    )) {
        foreach ($csrf in @('sin', 'incorrecto')) { $null = Peticion PUT $caso.ruta 403 $caso.cuerpo $csrf }
        $null = Peticion PUT $caso.ruta 200 $caso.cuerpo
    }
    foreach ($caso in @(
        @{ ruta = "/api/servicios/$servicioId/estado" },
        @{ ruta = "/api/barberos/$barberoId/estado" }
    )) {
        foreach ($csrf in @('sin', 'incorrecto')) { $null = Peticion PATCH $caso.ruta 403 @{ activo = $false } $csrf }
    }
    $estado = Peticion PATCH "/api/barberos/$barberoId/estado" 200 @{ activo = $false }
    if ($estado.barbero.activo -or $estado.reservasFuturasVigentes -ne 0) { throw 'La desactivación devolvió datos inesperados.' }
    $activos = @(Peticion GET '/api/barberos' 200)
    if ($barberoId -in $activos.id) { throw 'El barbero inactivo sigue en el listado público.' }
    $estado = Peticion PATCH "/api/barberos/$barberoId/estado" 200 @{ activo = $true }
    if (-not $estado.barbero.activo) { throw 'No se reactivó el barbero.' }
    $null = Peticion PATCH "/api/servicios/$servicioId/estado" 200 @{ activo = $false }
    $null = Peticion PATCH "/api/servicios/$servicioId/estado" 200 @{ activo = $true }
    Write-Host "Recorrido HTTP completado: ADMIN id=$adminId, servicio id=$servicioId, barbero id=$barberoId, usuario BARBERO id=$usuarioBarberoId."
    # Comprobación de layout y diálogos con Edge local sin dependencias añadidas.
    & node (Join-Path $PSScriptRoot 'verificar-catalogo-visual.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Falló la comprobación visual; consulte la salida sin secretos.' }
    $null = Peticion POST '/api/auth/logout' 204
} finally {
    foreach ($proceso in @($procesoFrontend, $procesoBackend)) {
        if ($null -ne $proceso -and -not $proceso.HasExited) { taskkill.exe /PID $proceso.Id /T /F | Out-Null }
    }
    try {
        # Recupera IDs solo de los correos ficticios exclusivos de esta ejecución ante un fallo parcial.
        if ($procesoBackend -and -not $adminId) {
            $ids = @(Sql "SELECT id FROM usuario WHERE correo = '$correoAdmin' AND rol = 'ADMIN';")
            if ($ids.Count) { $adminId = [long]$ids[0] }
        }
        if ($barberoId -and -not $usuarioBarberoId) {
            $usuarioBarberoId = [long](@(Sql "SELECT usuario_id FROM barbero WHERE id = $barberoId;")[0])
        }
        $borrados = [Collections.Generic.List[string]]::new()
        $comprobaciones = [Collections.Generic.List[string]]::new()
        if ($barberoId) {
            $borrados.Add("DELETE FROM barbero WHERE id = $barberoId AND usuario_id = $usuarioBarberoId;")
            $comprobaciones.Add("(SELECT count(*) FROM barbero WHERE id = $barberoId)")
        }
        if ($usuarioBarberoId) {
            $borrados.Add("DELETE FROM usuario WHERE id = $usuarioBarberoId AND correo = '$correoBarbero' AND rol = 'BARBERO';")
            $comprobaciones.Add("(SELECT count(*) FROM usuario WHERE id = $usuarioBarberoId)")
        }
        if ($servicioId) {
            $borrados.Add("DELETE FROM servicio WHERE id = $servicioId;")
            $comprobaciones.Add("(SELECT count(*) FROM servicio WHERE id = $servicioId)")
        }
        if ($adminId) {
            $borrados.Add("DELETE FROM usuario WHERE id = $adminId AND correo = '$correoAdmin' AND rol = 'ADMIN';")
            $comprobaciones.Add("(SELECT count(*) FROM usuario WHERE id = $adminId)")
        }
        if ($borrados.Count) {
            $sql = "\set ON_ERROR_STOP on" + [Environment]::NewLine + "BEGIN;" + [Environment]::NewLine +
                ($borrados -join [Environment]::NewLine) + [Environment]::NewLine +
                'SELECT ' + ($comprobaciones -join ' + ') + ' = 0 AS limpia' + [Environment]::NewLine +
                '\gset' + [Environment]::NewLine + '\if :limpia' + [Environment]::NewLine + 'COMMIT;' + [Environment]::NewLine +
                '\else' + [Environment]::NewLine + 'ROLLBACK;' + [Environment]::NewLine + '\quit 3' + [Environment]::NewLine + '\endif'
            $salida = @(Sql $sql)
            if (@($salida | Where-Object { $_ -eq 'DELETE 1' }).Count -ne $borrados.Count -or $salida[-1] -ne 'COMMIT') {
                throw 'La limpieza no confirmó todos los registros propios.'
            }
            Write-Host "Limpieza por IDs: $($borrados.Count) DELETE 1; cero filas propias restantes; COMMIT."
        }
    } finally {
        foreach ($clave in $variablesAnteriores.Keys) { [Environment]::SetEnvironmentVariable($clave, $variablesAnteriores[$clave], 'Process') }
        Write-Host 'Árboles de procesos propios detenidos; variables de proceso restauradas.'
    }
}
