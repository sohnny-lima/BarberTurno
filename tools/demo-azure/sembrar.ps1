#requires -Version 7.2
<# .SYNOPSIS
Carga el escenario ficticio con el mismo jar, desde un directorio aislado, y termina su proceso.
#>
[CmdletBinding()]
param(
    [string]$Jar = (Join-Path $PSScriptRoot '../../backend/target/barberturno-0.0.1-SNAPSHOT.jar'),
    [string]$Secretos = 'D:\Proyectos\BarberTurno\.local\demo-azure',
    [string]$Base = 'barberturno_v2_demo', [string]$RolMigraciones = 'bt_v2_demo',
    [string]$RolAplicacion = 'bt_v2_demo_app',
    [string]$Java = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\java.exe',
    [ValidateRange(30, 1800)][int]$TiempoEsperaSegundos = 300, [switch]$Simular
)
. "$PSScriptRoot/comun.ps1"
Assert-DestinoDemo $Base $RolMigraciones $RolAplicacion
if ($Simular) {
    Write-Output 'SIMULAR: ejecutar el jar con dev,demo, puerto local aleatorio y TLS; sin importar .local ni heredar credenciales.'
    Write-Output 'SIMULAR: esperar Started y health UP con readinessState; detener el hijo; revisar secretos antes de guardar el registro y el marcador.'
    return
}
$Jar = (Resolve-Path -LiteralPath $Jar).Path
$Secretos = Initialize-DirectorioSecretos $Secretos
$marcador = Join-Path $Secretos 'siembra-completa.json'
if ([IO.File]::Exists($marcador)) { Write-Output 'Siembra ya completada; no se vuelve a ejecutar el jar.'; return }
$candadoRuta = Join-Path $Secretos 'siembra-en-curso.lock'
$candado = [IO.File]::Open($candadoRuta, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
$temporal = $null; $proceso = $null; $iniciado = $false
try {
    if ([IO.File]::Exists($marcador)) { Write-Output 'Siembra ya completada.'; return }
    $claves = @{
        BT_DB_PASSWORD = Get-SecretoDemo $Secretos 'aplicacion.secreto'
        BT_FLYWAY_PASSWORD = Get-SecretoDemo $Secretos 'migraciones.secreto'
        BT_DEMO_PASSWORD = Get-SecretoDemo $Secretos 'demo-cuentas.secreto' -Crear
        BT_ADMIN_PASSWORD = Get-SecretoDemo $Secretos 'admin-plataforma.secreto' -Crear
        BT_JWT_SECRET = Get-SecretoDemo $Secretos 'jwt.secreto' -Crear -Jwt
    }
    $entorno = @{
        BT_DB_URL = Get-JdbcDemo $Base; BT_DB_USER = $RolAplicacion; BT_FLYWAY_USER = $RolMigraciones
        BT_ADMIN_CORREO = 'plataforma-demo@ejemplo.test'; BT_ADMIN_NOMBRE = 'Administración demo'
        BARBERTURNO_IA_HABILITADA = 'false'; BARBERTURNO_WHATSAPP_PROCESADOR_HABILITADO = 'false'
        BARBERTURNO_WHATSAPP_SALIDA_HABILITADO = 'false'
    }
    foreach ($nombre in $claves.Keys) { $entorno[$nombre] = $claves[$nombre] }
    # Dos niveles nuevos: ni ./ ni ../ contienen .local. Tampoco se heredan SPRING_CONFIG_*.
    $temporal = Join-Path ([IO.Path]::GetTempPath()) ('bt-demo-' + [guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($temporal)
    $trabajo = Join-Path $temporal 'trabajo'
    [void][IO.Directory]::CreateDirectory($trabajo)
    $argumentos = @('-Xmx320m', '-XX:MaxMetaspaceSize=192m', '-XX:+UseSerialGC', '-Xss512k', '-Dfile.encoding=UTF-8',
        '-jar', $Jar, '--spring.profiles.active=dev,demo', '--server.address=127.0.0.1', '--server.port=0',
        '--management.endpoint.health.probes.enabled=true', '--logging.level.org.springframework.boot.availability=DEBUG')
    $proceso = New-ProcesoDemo $Java $argumentos $entorno $trabajo
    $registro = [Text.StringBuilder]::new()
    $completo = $false; $puerto = 0; $started = $false; $listo = $false; $detenido = $false
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    $cliente = [Net.Http.HttpClient]::new()
    $cliente.Timeout = [TimeSpan]::FromSeconds(2)
    try {
        [void]$proceso.Start(); $iniciado = $true; $proceso.StandardInput.Close()
        # Ambas tuberías se consumen simultáneamente, sin mostrar líneas sin sanear.
        $lecturas = @($proceso.StandardOutput.ReadLineAsync(), $proceso.StandardError.ReadLineAsync())
        while ($reloj.Elapsed.TotalSeconds -lt $TiempoEsperaSegundos) {
            for ($i = 0; $i -lt 2; $i++) {
                while ($null -ne $lecturas[$i] -and $lecturas[$i].IsCompleted) {
                    $linea = $lecturas[$i].GetAwaiter().GetResult()
                    if ($null -eq $linea) { $lecturas[$i] = $null; break }
                    [void]$registro.AppendLine($linea)
                    if ($registro.Length -gt 8MB) { throw 'El registro superó 8 MB; se descarta.' }
                    if ($linea -match 'Tomcat started on port (\d+)') { $puerto = [int]$Matches[1] }
                    if ($linea -match '\bStarted .+ in ') { $started = $true }
                    # Spring Boot registra Started ANTES de los runners; ACCEPTING_TRAFFIC va después.
                    if ($linea -match 'ReadinessState.*ACCEPTING_TRAFFIC') { $listo = $true }
                    $lecturas[$i] = if ($i -eq 0) { $proceso.StandardOutput.ReadLineAsync() } else { $proceso.StandardError.ReadLineAsync() }
                }
            }
            if ($proceso.HasExited) { break }
            if ($started -and $listo -and $puerto -gt 0) {
                try {
                    $respuesta = $cliente.GetStringAsync("http://127.0.0.1:$puerto/actuator/health").GetAwaiter().GetResult() | ConvertFrom-Json
                    if ($respuesta.status -eq 'UP') { $completo = $true; break }
                } catch { <# Reintentar mientras se completa la disponibilidad. #> }
            }
            Start-Sleep -Milliseconds 200
        }
    } finally {
        $cliente.Dispose()
        if ($iniciado) {
            if (-not $proceso.HasExited) { $proceso.Kill($true); $detenido = $true }
            $proceso.WaitForExit()
            # Recoger también las líneas posteriores a la señal o a un fallo.
            foreach ($i in 0..1) {
                if ($null -ne $lecturas[$i]) {
                    $linea = $lecturas[$i].GetAwaiter().GetResult()
                    if ($null -ne $linea) { [void]$registro.AppendLine($linea) }
                }
            }
            [void]$registro.Append($proceso.StandardOutput.ReadToEnd())
            [void]$registro.Append($proceso.StandardError.ReadToEnd())
        }
    }
    $texto = $registro.ToString()
    # Incluye el secreto admin PostgreSQL aunque nunca se entrega a Java.
    $valores = @($claves.Values) + @(Get-SecretoDemo $Secretos 'pg-admin.secreto')
    foreach ($valor in $valores) {
        if ($texto.Contains($valor, [StringComparison]::Ordinal)) { throw 'El registro contiene un secreto: no se guardó ni se marcó la siembra.' }
    }
    $nombreRegistro = 'siembra-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fffffff') + '.log'
    [IO.File]::WriteAllText((Join-Path $Secretos $nombreRegistro), $texto, [Text.UTF8Encoding]::new($false))
    if (-not $completo -or -not $detenido -or $texto -match '(?im)\bERROR\b|\b[A-Za-z0-9_.]*Exception\b|APPLICATION FAILED TO START') {
        throw 'La siembra no terminó correctamente. Revise el registro privado; no se creó el marcador.'
    }
    $evidencia = @{ jarSha256 = (Get-FileHash -LiteralPath $Jar -Algorithm SHA256).Hash
        fechaUtc = [DateTime]::UtcNow.ToString('o'); base = $Base; registro = $nombreRegistro }
    [IO.File]::WriteAllText($marcador, ($evidencia | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    Write-Output 'Datos ficticios cargados; disponibilidad confirmada, hijo detenido y registro privado revisado.'
} finally {
    if ($null -ne $proceso) {
        if ($iniciado -and -not $proceso.HasExited) { $proceso.Kill($true); $proceso.WaitForExit() }
        $proceso.Dispose()
    }
    if ($temporal) {
        # Solo los dos directorios vacíos creados por este proceso; sin borrado recursivo.
        [IO.Directory]::Delete((Join-Path $temporal 'trabajo'))
        [IO.Directory]::Delete($temporal)
    }
    $candado.Dispose()
    # Conservar el archivo vacío evita carreras entre quienes esperan el candado.
}
