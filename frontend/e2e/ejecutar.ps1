$ErrorActionPreference = 'Stop'
# Variables limitadas a este proceso; no se cambia Java ni Node del sistema.
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
$frontend = Split-Path -Parent $PSScriptRoot
$raiz = Split-Path -Parent $frontend
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $frontend 'tmp/navegadores'
New-Item -ItemType Directory -Force (Join-Path $frontend 'tmp/descargas') | Out-Null
$env:TEMP = Join-Path $frontend 'tmp/descargas'
$env:TMP = $env:TEMP
Push-Location $frontend
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
Push-Location (Join-Path $raiz 'backend')
try {
    & .\mvnw.cmd -Pcon-frontend clean package -DskipTests '-Dexec.skip=true'
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
Push-Location $frontend
try {
    & node node_modules/@playwright/test/cli.js test @args
    exit $LASTEXITCODE
} finally { Pop-Location }
