# Genera y firma la APK de PRUEBA del TWA de BarberTurno a partir de android/twa-manifest.json (V2-18, V2-DA-95).
# Uso (PowerShell 7):
#   pwsh -File android/generar.ps1 [-Salida D:\Android\barberturno-twa] [-Publico <carpeta public del frontend>]
# Requisitos (ver docs/v2/pruebas/v2-18-android.md):
#   - Bubblewrap CLI 1.27.0 en D:\Android\bubblewrap (npm local, sin instalación global) y
#     %USERPROFILE%\.bubblewrap\config.json con jdkPath = JDK 17 y androidSdkPath = D:\Android\bubblewrap\sdk.
#   - Node 24.21.0 con fnm.
# Qué hace:
#   1. Sirve <Publico> en http://127.0.0.1:8790 solo durante la generación (iconos y manifest que Bubblewrap descarga).
#   2. Copia twa-manifest.json a <Salida> y regenera el proyecto Gradle con `bubblewrap update` (fuera de Git).
#   3. Crea, si falta, la clave de PRUEBA en D:\Proyectos\BarberTurno\.local\android\ (fuera de Git) con una
#      contraseña aleatoria guardada allí mismo; nunca la muestra.
#   4. Compila y firma con `bubblewrap build --skipPwaValidation` e imprime la huella SHA-256 del certificado
#      (dato público, necesario para BT_TWA_HUELLAS).
# No publica nada, no usa cuentas ni dominios: el host del manifiesto es «localhost» (valor de prueba).
param([string]$Salida = 'D:\Android\barberturno-twa',
      [string]$Publico = (Join-Path $PSScriptRoot '..\frontend\public'),
      [int]$Puerto = 8790)
$ErrorActionPreference = 'Stop'
$claves = 'D:\Proyectos\BarberTurno\.local\android'
$almacen = Join-Path $claves 'barberturno-prueba.keystore'
$secreto = Join-Path $claves 'barberturno-prueba.secreto'
$alias = 'barberturno-prueba'
$jdk17 = 'C:\Program Files\Java\jdk-17'
$keytool = Join-Path $jdk17 'bin\keytool.exe'
$bubblewrap = 'D:\Android\bubblewrap'
$sdkBubblewrap = Join-Path $bubblewrap 'sdk'

$fnm = 'C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe\fnm.exe'
& $fnm env --shell powershell | Out-String | Invoke-Expression
& $fnm use 24.21.0 *> $null

if (-not (Test-Path (Join-Path $Publico 'iconos\icono-512.png'))) { throw "Faltan los iconos PNG en $Publico\iconos (ejecute frontend/tools/generar-iconos.mjs)." }
New-Item -ItemType Directory -Force $Salida, $claves | Out-Null

# Bubblewrap 1.27.0 instala build-tools 36.1.0 con unas comillas que cmd rompe en Windows, y el proyecto que
# genera compila contra android-36: ambos paquetes oficiales se instalan antes con sdkmanager.
foreach ($paquete in @(@('build-tools;36.1.0', 'build-tools\36.1.0\apksigner.bat'), @('platforms;android-36', 'platforms\android-36\android.jar'))) {
  if (-not (Test-Path (Join-Path $sdkBubblewrap $paquete[1]))) {
    $env:JAVA_HOME = $jdk17
    cmd /c ('"' + (Join-Path $sdkBubblewrap 'bin\sdkmanager.bat') + '" --sdk_root=' + $sdkBubblewrap + ' --install "' + $paquete[0] + '"')
    if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar $($paquete[0])." }
  }
}

# 1. Servidor estático temporal, solo en 127.0.0.1.
$servidorJs = @'
const http = require('http'), fs = require('fs'), path = require('path');
const raiz = path.resolve(process.argv[2]), puerto = Number(process.argv[3]);
const tipos = { '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
http.createServer((pet, res) => {
  const ruta = path.resolve(raiz, '.' + decodeURIComponent(new URL(pet.url, 'http://x').pathname));
  if (!ruta.startsWith(raiz) || !fs.existsSync(ruta) || fs.statSync(ruta).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': tipos[path.extname(ruta)] || 'application/octet-stream' });
  fs.createReadStream(ruta).pipe(res);
}).listen(puerto, '127.0.0.1');
'@
$archivoServidor = Join-Path $Salida 'servidor-iconos.cjs'
[IO.File]::WriteAllText($archivoServidor, $servidorJs)
$servidor = Start-Process node -ArgumentList $archivoServidor, (Resolve-Path $Publico).Path, $Puerto -PassThru -WindowStyle Hidden
try {
  Start-Sleep -Seconds 2
  # 2. Proyecto Gradle regenerado desde el manifiesto versionado.
  Copy-Item (Join-Path $PSScriptRoot 'twa-manifest.json') (Join-Path $Salida 'twa-manifest.json') -Force
  Push-Location $Salida
  try {
    & npx --prefix $bubblewrap bubblewrap update --skipVersionUpgrade --manifest="$Salida\twa-manifest.json" --directory="$Salida"
    if ($LASTEXITCODE -ne 0) { throw "bubblewrap update falló ($LASTEXITCODE)" }
    # AGP 8.9.1 (fijado por la plantilla) no reconoce los paquetes que instala cmdline-tools 23 en el SDK de
    # Bubblewrap: Gradle compila contra el SDK estándar de D: (android-36 y build-tools 35 de Android Studio).
    # Bubblewrap sigue usando su SDK para zipalign y apksigner.
    [IO.File]::WriteAllText((Join-Path $Salida 'local.properties'), 'sdk.dir=D\:\\Android\\Sdk' + "`n")

    # 3. Clave de prueba (fuera de Git; la contraseña no se muestra).
    if (-not (Test-Path $secreto)) {
      $bytes = [byte[]]::new(24); [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
      [IO.File]::WriteAllText($secreto, [Convert]::ToHexString($bytes))
    }
    $clave = [IO.File]::ReadAllText($secreto).Trim()
    $env:BT_CLAVE = $clave
    if (-not (Test-Path $almacen)) {
      & $keytool -genkeypair -keystore $almacen -alias $alias -keyalg RSA -keysize 2048 -validity 3650 `
        -dname 'CN=BarberTurno prueba local, O=BarberTurno (prueba), C=PE' -storepass:env BT_CLAVE -keypass:env BT_CLAVE `
        -noprompt 2>&1 | Out-Null
      if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la clave de prueba.' }
    }

    # 4. Compilación y firma. Bubblewrap llama a `gradlew.bat` sin ruta: cmd debe buscar en la carpeta actual
    #    (solo en este proceso; algunos entornos fijan NoDefaultCurrentDirectoryInExePath).
    Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
    $env:BUBBLEWRAP_KEYSTORE_PASSWORD = $clave; $env:BUBBLEWRAP_KEY_PASSWORD = $clave
    & npx --prefix $bubblewrap bubblewrap build --skipPwaValidation --manifest="$Salida\twa-manifest.json"
    if ($LASTEXITCODE -ne 0) { throw "bubblewrap build falló ($LASTEXITCODE)" }
  } finally {
    Remove-Item Env:BUBBLEWRAP_KEYSTORE_PASSWORD, Env:BUBBLEWRAP_KEY_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
  }
  $huella = (& $keytool -list -v -keystore $almacen -alias $alias -storepass:env BT_CLAVE | Select-String 'SHA256:').Line.Split('SHA256:')[1].Trim()
  "APK: $Salida\app-release-signed.apk"
  "Huella SHA-256 (BT_TWA_HUELLAS): $huella"
} finally {
  Stop-Process -Id $servidor.Id -ErrorAction SilentlyContinue
  Remove-Item Env:BT_CLAVE -ErrorAction SilentlyContinue
}
