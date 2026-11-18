<#
.SYNOPSIS
  Prepara el entorno local de BarberTurno v2 (V2-01): roles, bases y .local/barberturno.env del worktree.

.DESCRIPTION
  - Crea (si faltan) los roles barberturno_v2 (migraciones) y barberturno_v2_app (aplicación) y las bases
    barberturno_v2, barberturno_v2_test y barberturno_v2_migracion en PostgreSQL 18 (puerto 5433).
  - Las contraseñas de los roles se generan al azar (o, con -Manual, se teclean de forma oculta). En el servidor
    solo se guarda su verificador SCRAM-SHA-256, calculado en este equipo: las contraseñas no viajan ni se muestran.
  - La única contraseña que se teclea es la de postgres, en el aviso oculto de psql.
  - Escribe .local/barberturno.env del worktree (no versionado) y comprueba la conexión de cada rol a cada base.
  - No toca roles, bases ni secretos de v1, ni credenciales de producción. Volver a ejecutarlo rota las contraseñas.

.EXAMPLE
  cd D:\Proyectos\BarberTurno-v2
  powershell -ExecutionPolicy Bypass -File tools\preparar-entorno-v2.ps1
#>
[CmdletBinding()]
param(
    [switch]$Manual,
    [switch]$Reemplazar,
    [string]$Psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $PSScriptRoot
$sql = Join-Path $PSScriptRoot 'db-local-v2.sql'
$envArchivo = Join-Path $raiz '.local\barberturno.env'
$bases = @('barberturno_v2', 'barberturno_v2_test', 'barberturno_v2_migracion')

function Fallar([string]$mensaje) { Write-Host "ERROR: $mensaje" -ForegroundColor Red; exit 1 }

# 1. Comprobaciones previas (sin secretos).
if (-not (Test-Path (Join-Path $raiz 'docs\v2\00-README.md'))) { Fallar 'Ejecútelo desde el worktree de v2 (D:\Proyectos\BarberTurno-v2).' }
$rama = (& git -C $raiz rev-parse --abbrev-ref HEAD).Trim()
if ($rama -notmatch '^(v2|tarea/V2-)') { Fallar "La rama actual es '$rama'; se esperaba v2 o tarea/V2-..." }
if (-not (Test-Path $Psql)) { Fallar "No se encuentra psql 18 en '$Psql'." }
$versionPsql = (& $Psql --version)
if ($versionPsql -notmatch ' 18\.') { Fallar "Se requiere el cliente psql 18 (encontrado: $versionPsql)." }
if ((Test-Path $envArchivo) -and -not $Reemplazar) {
    Fallar "Ya existe $envArchivo. Para rotar las contraseñas y reescribirlo, vuelva a ejecutar con -Reemplazar."
}

# 2. Contraseñas de los roles nuevos (solo en memoria).
function New-ClaveAleatoria {
    $alfabeto = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $b = New-Object byte[] 1
    $sb = New-Object Text.StringBuilder
    while ($sb.Length -lt 32) {
        $rng.GetBytes($b)
        if ($b[0] -lt (256 - (256 % $alfabeto.Length))) { [void]$sb.Append($alfabeto[$b[0] % $alfabeto.Length]) }
    }
    $sb.ToString()
}
function Read-ClaveOculta([string]$rol) {
    while ($true) {
        $a = Read-Host -AsSecureString "Contraseña para $rol (16-72 caracteres: letras, dígitos, . _ ~ -)"
        $b = Read-Host -AsSecureString "Repítala"
        $pa = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($a))
        $pb = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($b))
        if ($pa -cne $pb) { Write-Host 'No coinciden; inténtelo de nuevo.'; continue }
        # Caracteres seguros para el formato .properties del archivo de entorno.
        if ($pa -notmatch '^[A-Za-z0-9._~-]{16,72}$') { Write-Host 'Formato no admitido; inténtelo de nuevo.'; continue }
        return $pa
    }
}
if ($Manual) {
    $clavePropietario = Read-ClaveOculta 'barberturno_v2'
    $claveApp = Read-ClaveOculta 'barberturno_v2_app'
    if ($clavePropietario -ceq $claveApp) { Fallar 'Las dos contraseñas deben ser distintas.' }
} else {
    $clavePropietario = New-ClaveAleatoria
    $claveApp = New-ClaveAleatoria
}

# 3. Verificadores SCRAM-SHA-256 (RFC 5802/7677; mismo formato que guarda PostgreSQL).
function Get-VerificadorScram([string]$clave) {
    $iteraciones = 4096
    $sal = New-Object byte[] 16
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($sal)
    $kdf = New-Object Security.Cryptography.Rfc2898DeriveBytes(
        [Text.Encoding]::UTF8.GetBytes($clave), $sal, $iteraciones, [Security.Cryptography.HashAlgorithmName]::SHA256)
    $salada = $kdf.GetBytes(32)
    $hmac = New-Object Security.Cryptography.HMACSHA256 -ArgumentList (, $salada)
    $claveCliente = $hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes('Client Key'))
    $claveServidor = $hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes('Server Key'))
    $claveAlmacenada = [Security.Cryptography.SHA256]::Create().ComputeHash($claveCliente)
    'SCRAM-SHA-256$' + $iteraciones + ':' + [Convert]::ToBase64String($sal) + '$' +
        [Convert]::ToBase64String($claveAlmacenada) + ':' + [Convert]::ToBase64String($claveServidor)
}

# 4. Crear roles y bases como postgres (psql pide su contraseña sin mostrarla).
Write-Host ''
Write-Host 'Se conectará como postgres a localhost:5433. psql pedirá su contraseña (no se mostrará).'
$env:BT_V2_SCRAM_PROPIETARIO = Get-VerificadorScram $clavePropietario
$env:BT_V2_SCRAM_APP = Get-VerificadorScram $claveApp
try {
    & $Psql -X -h localhost -p 5433 -U postgres -d postgres -f $sql
    $codigo = $LASTEXITCODE
} finally {
    Remove-Item Env:BT_V2_SCRAM_PROPIETARIO, Env:BT_V2_SCRAM_APP -ErrorAction SilentlyContinue
}
if ($codigo -ne 0) { Fallar "psql terminó con el código $codigo; no se escribió $envArchivo." }

# 5. Archivo de entorno del worktree (UTF-8 sin BOM; formato .properties, DA-17).
$fecha = Get-Date -Format 'dd/MM/yyyy HH:mm'
$contenido = @(
    "# BarberTurno v2: secretos locales generados por tools/preparar-entorno-v2.ps1 el $fecha.",
    '# No versionar, no copiar a otros entornos y no reutilizar en producción.',
    'BT_DB_USER=barberturno_v2_app',
    "BT_DB_PASSWORD=$claveApp",
    'BT_FLYWAY_USER=barberturno_v2',
    "BT_FLYWAY_PASSWORD=$clavePropietario",
    ''
) -join "`n"
New-Item -ItemType Directory -Force (Split-Path $envArchivo) | Out-Null
[IO.File]::WriteAllText($envArchivo, $contenido, (New-Object Text.UTF8Encoding $false))

# 6. Comprobar cada rol en cada base (sin mostrar secretos).
function Probar([string]$rol, [string]$clave, [string]$base, [string]$consulta) {
    $env:PGPASSWORD = $clave
    try { $r = (& $Psql -X -w -q -t -A -h localhost -p 5433 -U $rol -d $base -c $consulta) }
    finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    if ($LASTEXITCODE -ne 0) { Fallar "No se pudo conectar como $rol a $base." }
    ($r | Out-String).Trim()
}
$fallos = 0
foreach ($base in $bases) {
    $app = Probar 'barberturno_v2_app' $claveApp $base `
        "select current_user || '|' || (select (rolsuper or rolbypassrls or rolcreatedb or rolcreaterole)::text from pg_roles where rolname = current_user)"
    $prop = Probar 'barberturno_v2' $clavePropietario $base `
        "select current_user || '|' || pg_get_userbyid(datdba) from pg_database where datname = current_database()"
    $ok = ($app -eq 'barberturno_v2_app|false') -and ($prop -eq 'barberturno_v2|barberturno_v2')
    if (-not $ok) { $fallos++ }
    $estado = if ($ok) { 'OK' } else { 'REVISAR' }
    Write-Host ("{0,-26} aplicación: {1,-26} propietario: {2,-30} {3}" -f $base, $app, $prop, $estado)
}
$clavePropietario = $null; $claveApp = $null
if ($fallos -gt 0) { Fallar 'Alguna comprobación no coincide con lo esperado.' }

Write-Host ''
Write-Host "Listo. Se escribió $envArchivo (claves: BT_DB_USER, BT_DB_PASSWORD, BT_FLYWAY_USER, BT_FLYWAY_PASSWORD)."
Write-Host 'No comparta ni muestre ese archivo. Avise al coordinador para continuar con V2-01.'
