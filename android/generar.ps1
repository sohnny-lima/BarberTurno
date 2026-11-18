# Genera y firma la APK (y el AAB) del TWA de BarberTurno a partir de android/twa-manifest.json (V2-18, V2-DA-95; V2-CONS).
# Uso (PowerShell 7):
#   pwsh -File android/generar.ps1 [-Clave distribucion|prueba] [-Salida D:\Android\barberturno-twa-dist] [-Publico <carpeta public del frontend>]
# Requisitos (ver docs/v2/pruebas/v2-18-android.md):
#   - Bubblewrap CLI 1.27.0 en D:\Android\bubblewrap (npm local, sin instalación global) y
#     %USERPROFILE%\.bubblewrap\config.json con jdkPath = JDK 17 y androidSdkPath = D:\Android\bubblewrap\sdk.
#   - Node 24.21.0 con fnm.
# Claves (siempre fuera de Git, en D:\Proyectos\BarberTurno\.local\android\):
#   - distribucion (predeterminada): distribucion\barberturno-distribucion.keystore, alias barberturno, PKCS12 RSA 4096,
#     validez 10 000 días (Play exige que venza después de 2033). Es la clave de subida/firma de la app real:
#     si se pierde no se puede actualizar la app instalada; guarde una copia fuera de este equipo (docs/v2/21).
#   - prueba: barberturno-prueba.keystore (V2-18), solo para pruebas locales; adapta la copia del manifiesto al
#     paquete pe.barberturno.prueba.twa, de modo que nunca firma el paquete real con la clave de prueba.
#   La carpeta de claves se restringe al usuario actual antes de crear nada y se rechaza cualquier clave o contraseña
#   con permisos ajenos. La contraseña es aleatoria, se guarda junto a la clave (.secreto) y nunca se muestra:
#   keytool, apksigner y jarsigner la leen de la variable de entorno BT_CLAVE.
# Qué hace:
#   1. Sirve <Publico> en http://127.0.0.1:8790 solo durante la generación (iconos y manifest que Bubblewrap descarga).
#   2. Copia twa-manifest.json a <Salida> y regenera el proyecto Gradle con `bubblewrap update` (fuera de Git).
#   3. Crea la clave si falta.
#   4. Compila sin firmar, alinea con zipalign, firma la APK con apksigner y el AAB con jarsigner, y verifica ambas firmas.
#      Imprime la huella SHA-256 del certificado (dato público, necesario para BT_TWA_HUELLAS).
# No publica nada ni usa cuentas. El puerto del servidor temporal es fijo (8790) porque así figura en twa-manifest.json.
param([ValidateSet('distribucion', 'prueba')][string]$Clave = 'distribucion',
      [string]$Salida = '',
      [string]$Publico = (Join-Path $PSScriptRoot '..\frontend\public'))
$Puerto = 8790
$ErrorActionPreference = 'Stop'
$raizClaves = 'D:\Proyectos\BarberTurno\.local\android'
if ($Clave -eq 'distribucion') {
  $claves = Join-Path $raizClaves 'distribucion'
  $almacen = Join-Path $claves 'barberturno-distribucion.keystore'
  $secreto = Join-Path $claves 'barberturno-distribucion.secreto'
  $alias = 'barberturno'
  $nombre = 'CN=BarberTurno, O=BarberTurno, L=Huamanga, ST=Ayacucho, C=PE'
  $bits = 4096; $dias = 10000
  if (-not $Salida) { $Salida = 'D:\Android\barberturno-twa-dist' }
} else {
  $claves = $raizClaves
  $almacen = Join-Path $claves 'barberturno-prueba.keystore'
  $secreto = Join-Path $claves 'barberturno-prueba.secreto'
  $alias = 'barberturno-prueba'
  $nombre = 'CN=BarberTurno prueba local, O=BarberTurno (prueba), C=PE'
  $bits = 2048; $dias = 3650
  if (-not $Salida) { $Salida = 'D:\Android\barberturno-twa' }
}
# El manifiesto versionado es el de distribución; la modalidad de prueba lo adapta en la copia de <Salida>
# (paquete, nombre y clave), de modo que nunca se mezclan la clave de prueba y el paquete real.
$manifiesto = Get-Content (Join-Path $PSScriptRoot 'twa-manifest.json') -Raw | ConvertFrom-Json
if ($Clave -eq 'prueba') {
  $manifiesto.packageId = 'pe.barberturno.prueba.twa'
  $manifiesto.name = 'BarberTurno (prueba)'
  $manifiesto.signingKey.path = $almacen
  $manifiesto.signingKey.alias = $alias
} elseif ($manifiesto.signingKey.path -ne $almacen -or $manifiesto.signingKey.alias -ne $alias -or $manifiesto.packageId -ne 'pe.barberturno.app') {
  throw "twa-manifest.json no coincide con la distribución (paquete $($manifiesto.packageId), clave $($manifiesto.signingKey.path) / $($manifiesto.signingKey.alias))."
}
$jdk17 = 'C:\Program Files\Java\jdk-17'
$keytool = Join-Path $jdk17 'bin\keytool.exe'
$jarsigner = Join-Path $jdk17 'bin\jarsigner.exe'
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

# La carpeta de claves se restringe ANTES de crear secretos (los archivos nuevos heredan su ACL) y cada archivo,
# nuevo o reutilizado, debe tener solo entradas del usuario actual; si no, se rechaza (no se reutiliza ni se firma).
$yo = [Security.Principal.WindowsIdentity]::GetCurrent().User
function ExigirSoloUsuario([string]$ruta) {
  $ajenas = (Get-Acl -LiteralPath $ruta).Access | Where-Object {
    try { $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]) -ne $yo } catch { $true }
  }
  if ($ajenas) { throw "El acceso a $ruta no es exclusivo del usuario actual: revise sus permisos antes de continuar." }
}
function ProtegerCarpeta([string]$ruta) {
  & icacls $ruta /inheritance:r /grant:r "*$($yo.Value):(OI)(CI)F" *> $null
  if ($LASTEXITCODE -ne 0) { throw "No se pudo restringir el acceso a $ruta." }
  ExigirSoloUsuario $ruta
}
ProtegerCarpeta $claves

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
  # 2. Proyecto Gradle regenerado desde el manifiesto versionado (adaptado si la modalidad es de prueba).
  [IO.File]::WriteAllText((Join-Path $Salida 'twa-manifest.json'), ($manifiesto | ConvertTo-Json -Depth 10))
  Push-Location $Salida
  try {
    & npx --prefix $bubblewrap bubblewrap update --skipVersionUpgrade --manifest="$Salida\twa-manifest.json" --directory="$Salida"
    if ($LASTEXITCODE -ne 0) { throw "bubblewrap update falló ($LASTEXITCODE)" }
    # AGP 8.9.1 (fijado por la plantilla) no reconoce los paquetes que instala cmdline-tools 23 en el SDK de
    # Bubblewrap: Gradle compila contra el SDK estándar de D: (android-36 y build-tools 35 de Android Studio).
    # Bubblewrap sigue usando su SDK para zipalign y apksigner.
    [IO.File]::WriteAllText((Join-Path $Salida 'local.properties'), 'sdk.dir=D\:\\Android\\Sdk' + "`n")

    # 3. Clave (fuera de Git; la contraseña no se muestra).
    if (-not (Test-Path $secreto)) {
      if (Test-Path $almacen) { throw "Existe $almacen sin su contraseña: no se regenera ni se sobrescribe." }
      $bytes = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
      [IO.File]::WriteAllText($secreto, [Convert]::ToHexString($bytes))
    }
    ExigirSoloUsuario $secreto
    $env:BT_CLAVE = [IO.File]::ReadAllText($secreto).Trim()
    if (-not (Test-Path $almacen)) {
      & $keytool -genkeypair -storetype PKCS12 -keystore $almacen -alias $alias -keyalg RSA -keysize $bits -validity $dias `
        -dname $nombre -storepass:env BT_CLAVE -keypass:env BT_CLAVE -noprompt 2>&1 | Out-Null
      if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la clave.' }
    }
    ExigirSoloUsuario $almacen

    # 4. Compilación y firma. Bubblewrap llama a `gradlew.bat` sin ruta: cmd debe buscar en la carpeta actual
    #    (solo en este proceso; algunos entornos fijan NoDefaultCurrentDirectoryInExePath).
    Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
    & npx --prefix $bubblewrap bubblewrap build --skipPwaValidation --skipSigning --manifest="$Salida\twa-manifest.json"
    if ($LASTEXITCODE -ne 0) { throw "bubblewrap build falló ($LASTEXITCODE)" }
    $herramientas = Join-Path $sdkBubblewrap 'build-tools\36.1.0'
    $sinFirma = Join-Path $Salida 'app\build\outputs\apk\release\app-release-unsigned.apk'
    $alineada = Join-Path $Salida 'app-release-alineada.apk'
    $firmada = Join-Path $Salida 'app-release-signed.apk'
    $paquete = Join-Path $Salida 'app\build\outputs\bundle\release\app-release.aab'
    $paqueteFirmado = Join-Path $Salida 'app-release-bundle.aab'
    Remove-Item $alineada, $firmada, $paqueteFirmado -ErrorAction SilentlyContinue
    & (Join-Path $herramientas 'zipalign.exe') -p -f 4 $sinFirma $alineada
    if ($LASTEXITCODE -ne 0) { throw "zipalign falló ($LASTEXITCODE)" }
    $env:JAVA_HOME = $jdk17
    # apksigner y jarsigner leen la contraseña de BT_CLAVE: no aparece en la línea de órdenes ni en los errores.
    & (Join-Path $herramientas 'apksigner.bat') sign --ks $almacen --ks-key-alias $alias `
      --ks-pass env:BT_CLAVE --key-pass env:BT_CLAVE --out $firmada $alineada
    if ($LASTEXITCODE -ne 0) { throw "apksigner falló ($LASTEXITCODE)" }
    & (Join-Path $herramientas 'apksigner.bat') verify $firmada
    if ($LASTEXITCODE -ne 0) { throw "La firma de la APK no se verifica ($LASTEXITCODE)" }
    if (Test-Path $paquete) {
      & $jarsigner -keystore $almacen -storepass:env BT_CLAVE -keypass:env BT_CLAVE -sigalg SHA256withRSA -digestalg SHA-256 `
        -signedjar $paqueteFirmado $paquete $alias *> $null
      if ($LASTEXITCODE -ne 0) { throw "jarsigner falló con el AAB ($LASTEXITCODE)" }
      # -strict convierte las advertencias graves (entradas sin firmar, cadena no válida, firmante distinto
      # del alias del almacén) en errores; además se exige el mensaje final de verificación.
      $verificacion = & $jarsigner -verify -strict -keystore $almacen -storepass:env BT_CLAVE $paqueteFirmado $alias 2>&1
      if ($LASTEXITCODE -ne 0 -or -not ($verificacion -contains 'jar verified.')) { throw "La firma del AAB no se verifica ($LASTEXITCODE)" }
    }
  } finally {
    Pop-Location
  }
  $huella = (& $keytool -list -v -keystore $almacen -alias $alias -storepass:env BT_CLAVE | Select-String 'SHA256:').Line.Split('SHA256:')[1].Trim()
  "APK: $firmada"
  if (Test-Path $paqueteFirmado) { "AAB: $paqueteFirmado" }
  "Huella SHA-256 (BT_TWA_HUELLAS): $huella"
} finally {
  Stop-Process -Id $servidor.Id -ErrorAction SilentlyContinue
  Remove-Item Env:BT_CLAVE -ErrorAction SilentlyContinue
}
