# Requiere PowerShell 7. Ejecutar desde cualquier directorio del repositorio.
[CmdletBinding()]
param(
    [ValidateRange(1, 50)][int]$Usuarios = 50,
    [ValidateRange(1, 300)][int]$Segundos = 300,
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$Fecha = '',
    [string]$Psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$salida = Join-Path $PSScriptRoot ('target/ejecuciones/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
[IO.Directory]::CreateDirectory($salida) | Out-Null
$variables = @('JAVA_HOME','Path','PGPASSFILE','BT_DB_URL','BT_DB_USER','BT_DB_PASSWORD','BT_JWT_SECRET',
    'BT_ADMIN_CORREO','BT_ADMIN_PASSWORD','BT_ADMIN_NOMBRE','BT_COOKIE_SECURE','BT_PERF_PASSWORD','BT_PERF_FECHA')
$previas = @{}
foreach ($nombre in $variables) { $previas[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$servidor = $null
$creada = $false
Push-Location $PSScriptRoot
try {
    if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
        throw 'El puerto 8080 debe estar libre; no se detienen procesos ajenos.'
    }
    # Carga privada exclusivamente al entorno del proceso; nunca escribe o muestra los valores.
    $local = Join-Path $raiz '.local/barberturno.env'
    if (Test-Path -LiteralPath $local) {
        foreach ($linea in [IO.File]::ReadAllLines($local)) {
            if ($linea -match '^\s*(BT_[A-Z_]+)\s*=(.*)$' -and $variables -contains $Matches[1] -and
                    -not [Environment]::GetEnvironmentVariable($Matches[1], 'Process')) {
                [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
            }
        }
    }
    if (-not $env:BT_DB_USER) { $env:BT_DB_USER = 'barberturno' }
    if ($env:BT_DB_USER -ne 'barberturno') { throw 'El ensayo local requiere el rol barberturno.' }
    if (-not $env:BT_DB_PASSWORD) { throw 'Falta BT_DB_PASSWORD en el entorno.' }
    # Contraseña efímera de cuentas ficticias; solo se hereda por los procesos hijos.
    if (-not $env:BT_PERF_PASSWORD) { $env:BT_PERF_PASSWORD = 'Carga35' + [Guid]::NewGuid().ToString('N') }
    $env:BT_DB_URL = 'jdbc:postgresql://localhost:5433/barberturno_perf'
    $env:BT_COOKIE_SECURE = 'false'
    # Impide importar un ADMIN privado en la base de carga.
    $env:BT_ADMIN_CORREO = ''
    $env:BT_ADMIN_PASSWORD = ''
    $env:BT_ADMIN_NOMBRE = ''
    $env:BT_PERF_FECHA = $Fecha
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    $env:PGPASSFILE = Join-Path $raiz '.local/pgpass.conf'
    $psqlArgs = @('-X','-w','-h','localhost','-p','5433','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1')
    $existe = & $Psql @psqlArgs -Atc "SELECT count(*) FROM pg_database WHERE datname='barberturno_perf'"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar la base dedicada.' }
    if ($existe.Trim() -ne '0') { throw 'barberturno_perf ya existe; el script no toca bases preexistentes.' }
    & $Psql @psqlArgs -c 'CREATE DATABASE barberturno_perf OWNER barberturno' *> (Join-Path $salida 'base.log')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base dedicada; consulte la alternativa manual documentada.' }
    $creada = $true
    $jar = Join-Path $raiz 'backend/target/barberturno-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Ejecute backend/mvnw.cmd verify antes de la carga.' }
    $servidor = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-jar', $jar,
        '--spring.profiles.active=dev','--barberturno.admin.correo=', '--barberturno.admin.password=',
        '--barberturno.admin.nombre=') -WorkingDirectory $raiz -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $salida 'backend.log') -RedirectStandardError (Join-Path $salida 'backend-error.log')
    $lista = $false
    for ($n = 0; $n -lt 120; $n++) {
        if ($servidor.HasExited) { throw 'El backend terminó antes de estar listo; consulte su log local sin publicar secretos.' }
        try { $lista = (Invoke-RestMethod 'http://localhost:8080/actuator/health' -TimeoutSec 2).status -eq 'UP' } catch { }
        if ($lista) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $lista) { throw 'El backend no alcanzó health UP.' }
    & (Join-Path $raiz 'backend/mvnw.cmd') -B -ntp -f pom.xml test-compile exec:java '-Dexec.mainClass=pe.barberturno.perf.DatosCarga' *> (Join-Path $salida 'generador.log')
    if ($LASTEXITCODE -ne 0) { throw 'Falló el generador; el ensayo limpia su base dedicada.' }
    $commit = (& git -C $raiz rev-parse HEAD).Trim()
    $cpu = Get-CimInstance Win32_Processor
    $equipo = Get-CimInstance Win32_ComputerSystem
    $sistema = Get-CimInstance Win32_OperatingSystem
    $versionPg = & $Psql @psqlArgs -Atc 'SHOW server_version'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo leer la versión de PostgreSQL.' }
    $entorno = [ordered]@{
        fecha = (Get-Date).ToString('o'); commit = $commit
        usuarios = $Usuarios; segundos = $Segundos
        cpu = @($cpu.Name); nucleos = ($cpu.NumberOfCores | Measure-Object -Sum).Sum
        procesadoresLogicos = ($cpu.NumberOfLogicalProcessors | Measure-Object -Sum).Sum
        ramGiB = [Math]::Round($equipo.TotalPhysicalMemory / 1GB, 2)
        sistema = $sistema.Caption; versionSistema = $sistema.Version
        java = 'Temurin 21.0.8+9'; postgres = $versionPg.Trim()
        gatling = '3.16.0'; plugin = '4.21.12'
    }
    [IO.File]::WriteAllText((Join-Path $salida 'entorno.json'),
        ($entorno | ConvertTo-Json) + "`n", [Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target/datos.properties') -Destination $salida
    [IO.File]::WriteAllText((Join-Path $salida 'commit.txt'), "$commit`n", [Text.UTF8Encoding]::new($false))
    Remove-Item -LiteralPath (Join-Path $PSScriptRoot 'target/resultado-negocio.properties') -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $PSScriptRoot 'target/rechazos-inesperados.tsv') -ErrorAction SilentlyContinue
    Write-Output "Backend listo; base exclusiva sembrada. Gatling: $Usuarios usuarios, $Segundos segundos."
    & (Join-Path $raiz 'backend/mvnw.cmd') -B -ntp -f pom.xml gatling:test "-Dperf.usuarios=$Usuarios" "-Dperf.segundos=$Segundos" *> (Join-Path $salida 'gatling.log')
    $resultadoGatling = $LASTEXITCODE
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'target/resultado-negocio.properties')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target/resultado-negocio.properties') -Destination $salida
    }
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'target/rechazos-inesperados.tsv')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target/rechazos-inesperados.tsv') -Destination $salida
    }
    if ($resultadoGatling -ne 0) { throw 'Gatling falló; conserve el informe y analice el fallo sin alterar aserciones.' }
    Write-Output 'Gatling: BUILD SUCCESS. Informes locales en perf/target/gatling/.'
} finally {
    try {
        # Solo el PID creado por este script; Gatling termina antes de este bloque.
        if ($servidor -and -not $servidor.HasExited) {
            Stop-Process -Id $servidor.Id -ErrorAction Stop
            $servidor.WaitForExit(10000) | Out-Null
        }
        if ($creada) {
            & $Psql @psqlArgs -c 'DROP DATABASE barberturno_perf' *> (Join-Path $salida 'limpieza.log')
            if ($LASTEXITCODE -ne 0) { throw 'Falló DROP DATABASE barberturno_perf; requiere retirar exclusivamente la base creada por este ensayo.' }
            Write-Output 'Limpieza: backend detenido y barberturno_perf eliminada.'
        }
    } finally {
        foreach ($nombre in $variables) { [Environment]::SetEnvironmentVariable($nombre, $previas[$nombre], 'Process') }
        Pop-Location
    }
}
