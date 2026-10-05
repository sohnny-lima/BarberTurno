# Ensayo T-36: usa solo demo como origen y retira exclusivamente su base nueva.
# No ejecutar junto a verify, servidores u otra suite contra PostgreSQL.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$bash = 'C:\Program Files\Git\bin\bash.exe'
$destino = Join-Path (Split-Path $raiz -Parent) ('BarberTurno-respaldo-ensayo-' + [guid]::NewGuid().ToString('N'))
$origen = 'barberturno_demo_t32'
$baseNueva = 'barberturno_restore'
$creada = $false
$variables = @('PGHOST', 'PGPORT', 'PGUSER', 'PGPASSWORD', 'PGPASSFILE', 'PG_BIN', 'PGRESTORE_USER', 'PGRESTORE_PASSWORD', 'PGRESTORE_PASSFILE')
$anteriores = @{}
foreach ($nombre in $variables) { $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
function Consultar-Administrador([string]$sql) {
    $env:PGUSER = 'postgres'
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $resultado = & "$pgBin\psql.exe" -X -w -At -d postgres -v ON_ERROR_STOP=1 -c $sql
    if ($LASTEXITCODE -ne 0) { throw 'Falló la consulta administrativa.' }
    return $resultado
}
try {
    Set-Location $raiz
    $env:PGHOST = 'localhost'
    $env:PGPORT = '5433'
    $env:PGPASSFILE = Join-Path $raiz '.local\pgpass.conf'
    $env:PG_BIN = '/c/Program Files/PostgreSQL/18/bin'
    Remove-Item Env:PGRESTORE_PASSFILE -ErrorAction SilentlyContinue
    $version = Consultar-Administrador "SELECT current_setting('server_version');"
    if ($version -ne '18.6') { throw 'El ensayo requiere el PostgreSQL 18.6 autorizado.' }
    if ((Consultar-Administrador "SELECT count(*) FROM pg_database WHERE datname = '$baseNueva';") -ne '0') {
        throw 'barberturno_restore ya existe; no se toca.'
    }
    if ((Consultar-Administrador "SELECT count(*) FROM pg_database WHERE datname = '$origen' AND pg_get_userbyid(datdba) = 'barberturno';") -ne '1') {
        throw 'No existe la demo con el dueño esperado.'
    }
    # Cargar exclusivamente la contraseña de aplicación en el entorno del proceso.
    foreach ($linea in [IO.File]::ReadLines((Join-Path $raiz '.local\barberturno.env'))) {
        if ($linea -match '^BT_DB_PASSWORD=(.*)$') { $env:PGRESTORE_PASSWORD = $Matches[1] }
    }
    if (-not $env:PGRESTORE_PASSWORD) { throw 'Falta la contraseña de aplicación en el entorno local.' }
    $env:PGRESTORE_USER = 'barberturno'
    $env:PGUSER = 'barberturno'
    $env:PGPASSWORD = $env:PGRESTORE_PASSWORD
    New-Item -ItemType Directory -Path $destino | Out-Null
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    & $bash ./tools/respaldo.sh $origen ($destino.Replace('\', '/'))
    if ($LASTEXITCODE -ne 0) { throw 'Falló el respaldo real.' }
    $segundosRespaldo = $reloj.Elapsed.TotalSeconds
    $dumps = @(Get-ChildItem -LiteralPath $destino -Filter '*.dump')
    if ($dumps.Count -ne 1) { throw 'Se esperaba exactamente un dump.' }
    $dump = $dumps[0]
    $env:PGUSER = 'postgres'
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    & $bash ./tools/restaurar.sh ($dump.FullName.Replace('\', '/')) $baseNueva barberturno $origen
    if ($LASTEXITCODE -ne 0) { throw 'Falló la restauración real (revise el destino; no se elimina una base de procedencia incierta).' }
    $creada = $true
    $segundosOrigen = $reloj.Elapsed.TotalSeconds
    # Rechazo real de destino preexistente, sin modificarlo.
    & $bash ./tools/restaurar.sh ($dump.FullName.Replace('\', '/')) $baseNueva barberturno
    if ($LASTEXITCODE -eq 0) { throw 'Se aceptó indebidamente una base existente.' }
    Write-Output 'OK: destino existente rechazado (salida 3).'
    # Comparación adicional del mismo destino con el resumen publicado.
    $env:PGUSER = 'barberturno'
    $env:PGPASSWORD = $env:PGRESTORE_PASSWORD
    $consulta = @'
SELECT format('SELECT %L || chr(9) || %L || chr(9) || count(*)::text FROM %I.%I;',
encode(convert_to(n.nspname, 'UTF8'), 'hex'), encode(convert_to(c.relname, 'UTF8'), 'hex'), n.nspname, c.relname)
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind IN ('r', 'p') AND n.nspname <> 'information_schema' AND n.nspname !~ '^pg_'
ORDER BY n.nspname COLLATE "C", c.relname COLLATE "C"
\gexec
'@
    $actual = @($consulta | & "$pgBin\psql.exe" -X -w -qAt -d $baseNueva -v ON_ERROR_STOP=1)
    if ($LASTEXITCODE -ne 0) { throw 'Falló el conteo restaurado.' }
    $esperado = @(Get-Content -LiteralPath ($dump.FullName + '.resumen.tsv') | Select-Object -Skip 1)
    if (@(Compare-Object $esperado $actual).Count -ne 0) { throw 'Diferencia con el resumen.' }
    foreach ($fila in $actual) {
        $campos = $fila.Split("`t")
        $tabla = [Text.Encoding]::UTF8.GetString([Convert]::FromHexString($campos[1]))
        Write-Output ("Tabla {0}: {1} filas" -f $tabla, $campos[2])
    }
    # Repetir desde el dump sin origen para ensayar la recuperación con resumen.
    Consultar-Administrador 'DROP DATABASE barberturno_restore;' | Out-Null
    $creada = $false
    & $bash ./tools/restaurar.sh ($dump.FullName.Replace('\', '/')) $baseNueva barberturno
    if ($LASTEXITCODE -ne 0) { throw 'Falló la restauración real usando el resumen.' }
    $creada = $true
    if ((Consultar-Administrador "SELECT count(*) FROM pg_database d CROSS JOIN LATERAL aclexplode(d.datacl) a WHERE d.datname = 'barberturno_restore' AND a.grantee = 0 AND a.privilege_type IN ('CONNECT', 'TEMPORARY');") -ne '0') {
        throw 'La base restaurada permite acceso a PUBLIC.'
    }
    Write-Output 'OK: recuperación sin origen usando resumen; PUBLIC sin CONNECT/TEMPORARY.'
    $reloj.Stop()
    Write-Output ("PostgreSQL {0}; dump={1} bytes; respaldo={2:F3}s; respaldo+restauración+origen={3:F3}s; ensayo={4:F3}s; tablas={5}" -f $version, $dump.Length, $segundosRespaldo, $segundosOrigen, $reloj.Elapsed.TotalSeconds, $actual.Count)
    if ($reloj.Elapsed.TotalHours -gt 4) { throw 'Se excedió el RTO de cuatro horas.' }
} finally {
    try {
        if ($creada) {
            Consultar-Administrador 'DROP DATABASE barberturno_restore;' | Out-Null
            $creada = $false
            if ((Consultar-Administrador "SELECT count(*) FROM pg_database WHERE datname = 'barberturno_restore';") -ne '0') {
                throw 'No se retiró la base del ensayo.'
            }
            Write-Output 'Limpieza: barberturno_restore eliminada; origen intacto.'
        }
        # Validar la ruta absoluta antes de retirar solo los artefactos propios.
        $resuelto = [IO.Path]::GetFullPath($destino)
        $padreEsperado = [IO.Path]::GetFullPath((Split-Path $raiz -Parent))
        if ((Split-Path $resuelto -Parent) -ne $padreEsperado -or (Split-Path $resuelto -Leaf) -notmatch '^BarberTurno-respaldo-ensayo-[a-f0-9]{32}$') {
            throw 'Ruta de limpieza inesperada.'
        }
        if (Test-Path -LiteralPath $resuelto) {
            # No recorrer directorios ni borrar archivos que no generó este ensayo.
            foreach ($archivo in (Get-ChildItem -LiteralPath $resuelto -File)) {
                if ($archivo.Name -match '^barberturno_demo_t32-\d{8}-\d{6}\.dump(\.resumen\.tsv)?$') {
                    Remove-Item -LiteralPath $archivo.FullName
                }
            }
            Remove-Item -LiteralPath $resuelto
            Write-Output 'Limpieza: dump, resumen y directorio del ensayo retirados.'
        }
    } finally {
        foreach ($nombre in $variables) { [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre], 'Process') }
    }
}
