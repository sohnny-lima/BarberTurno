# V2-18 · Evidencia del bloque T (TWA y asociación) y del traslado de Android a D:

Autor: Claude (coordinador). Revisión independiente: Codex (encargo 145, solo lectura). Fecha: 05/11/2026.
Separación: **web** (pruebas con navegador o MockMvc) · **emulador** (AVD `Medium_Phone`, API 36) · **teléfono real** (no disponible en esta etapa).

## 1. Traslado del SDK y los emuladores de C: a D:
Autorizado por el responsable el 05/11/2026. Sin Android Studio, emulador, `adb` ni Gradle en ejecución al empezar (solo el `java` de la extensión de VS Code, ajeno).

| Qué | Origen (C:) | Destino (D:) | Tamaño | Archivos |
|---|---|---|---|---|
| SDK (build-tools 35/36/36.1-rc1, platform-tools 36.0.0, platforms android-36, emulator 36.1.9, system-images android-36 Google Play x86_64, sources) | `C:\Users\limas\AppData\Local\Android\Sdk` | `D:\Android\Sdk` | 4,0 GB | 28 497 |
| AVD `Medium_Phone` (API 36, 2 GB RAM, datos 6 GB) | `C:\Users\limas\.android\avd` | `D:\Android\avd` | 8,9 GB | 34 |

Procedimiento: `robocopy /E /COPY:DAT /DCOPY:DAT /XJ` → inventario completo de rutas y tamaños sin diferencias → SHA-256 de todos los archivos de más de 20 MB en origen y destino → cambios de configuración → prueba desde D: → retirada del origen.

Cambios de configuración (respaldo de lo anterior en `D:\Android\traslado-respaldo\`):
- Android Studio 2025.1.3 y 2025.1.4: `options\android.sdk.path.xml` y `options\jdk.table.xml` → `D:\Android\Sdk`.
- Variables de usuario: `ANDROID_HOME=D:\Android\Sdk`, `ANDROID_AVD_HOME=D:\Android\avd`; `D:\Android\Sdk\platform-tools` añadido al `Path` de usuario. Antes no había ninguna variable Android.
- `cmdline-tools;latest` 23.0 instalado en `D:\Android\Sdk\cmdline-tools\latest` desde `dl.google.com` (SHA-1 comprobado contra `repository2-3.xml`).
- No se tocó `.android` salvo `avd`: `adbkey`, `debug.keystore`, `analytics.settings` y demás siguen en su sitio. Ningún otro proyecto de `D:\Proyectos` tiene `local.properties` con `sdk.dir`.

### 1.1 Resultados
- **Copia:** robocopy SDK 28 497/28 497 archivos y AVD 34/34, 0 errores. Inventario de rutas y tamaños origen ↔ destino: **0 diferencias**. SHA-256 de los archivos de más de 20 MB (**12,05 GB**): **0 diferencias**.
- **Prueba desde D: (emulador):** `emulator -list-avds` → `Medium_Phone_API_36.0`; arranque sin ventana con `D:\Android\Sdk\emulator\emulator.exe` → `sys.boot_completed=1`, Android 16, `sdk_gphone64_x86_64`, Chrome 141.0.7390.111; `hardware-qemu.ini` regenerado con `kernel.path`, `dataPartition`, `android.sdk.root` y `android.avd.home` en D:; procesos `emulator.exe` y `qemu-system-x86_64-headless.exe` desde `D:\Android\Sdk`. `adb` 36.0.0 desde `D:\Android\Sdk\platform-tools`.
- **Retirada del origen** (solo tras lo anterior): borradas `C:\Users\limas\AppData\Local\Android\Sdk` y `C:\Users\limas\.android\avd`, sustituidas por **uniones** (junctions) a `D:\Android\Sdk` y `D:\Android\avd`, para que cualquier referencia de otros proyectos o herramientas siga funcionando sin modificar sus archivos. Comprobado: `adb devices` y `emulator -list-avds` por la ruta antigua.
- **Espacio liberado en C:** 12,18 GB → 25,08 GB libres: **12,90 GB**.
- **Recuperación** (si hiciera falta volver atrás): `cmd /c rmdir` de las dos uniones, `robocopy D:\Android\Sdk C:\Users\limas\AppData\Local\Android\Sdk /E` y `robocopy D:\Android\avd C:\Users\limas\.android\avd /E`, restaurar desde `D:\Android\traslado-respaldo\` los cuatro XML de Android Studio y el `.ini` del AVD, y borrar las variables de usuario `ANDROID_HOME`/`ANDROID_AVD_HOME` y la entrada del `Path`.
- Sin bloqueos: ningún proceso de otro proyecto usaba el SDK o el AVD.

## 2. Asociación con el sitio (web)
- `GET /.well-known/assetlinks.json` (`AsociacionAndroidController`) con `BT_TWA_PAQUETE` y `BT_TWA_HUELLAS`; 404 sin configuración; arranque fallido con valores mal formados.
- `manifest.webmanifest` servido como `application/manifest+json` (`TiposRecursosPwa`): sin ello Spring lo servía como `application/octet-stream` (fallo detectado por `AsociacionAndroidIT`).
- Ejecución (commit `e14d173`, ejecutor Claude, `barberturno_v2_test` con el cerrojo): `AsociacionAndroidControllerTest` 6/6, `AsociacionAndroidIT` 4/4, `SpaForwardIT` 41/41 (regresión) → **51/0/0/0**; `-DskipTests verify` sin avisos de Javadoc.

## 3. Proyecto TWA reproducible
- `android/twa-manifest.json`: valores de **prueba** (`pe.barberturno.prueba.twa`, «BarberTurno (prueba)», host `localhost`). El proyecto Gradle y la APK se generan fuera de Git en `D:\Android\barberturno-twa` con `android/generar.ps1`.
- Bubblewrap CLI **1.27.0** (npm, 09/10/2026) instalado localmente en `D:\Android\bubblewrap` (sin instalación global); JDK 17.0.6 existente (`C:\Program Files\Java\jdk-17`, sin cambiar el Java global); SDK propio de Bubblewrap en `D:\Android\bubblewrap\sdk` (su disposición exige `bin/` en la raíz: no se altera el SDK de Android Studio). `bubblewrap doctor`: «Your jdkpath and androidSdkPath are valid».
- Clave de prueba en `D:\Proyectos\BarberTurno\.local\android\` (fuera de Git; contraseña aleatoria que no se muestra).

### 3.1 Generación de la APK de prueba (05/11/2026, ejecutor Claude)
`pwsh -File android/generar.ps1 -Publico D:\Proyectos\BarberTurno-v2-pwa-movil\frontend\public` (iconos del bloque M aún sin integrar) → `D:\Android\barberturno-twa\app-release-signed.apk` (1,1 MB), firmada con la clave de prueba. Huella SHA-256 del certificado de prueba (dato público): `E8:16:33:F1:8F:99:21:9A:67:FA:D6:3E:47:D1:40:4D:33:72:D2:77:26:73:5F:B8:85:6C:89:E1:DA:12:BE:CF`.
Incidencias resueltas por orden (todas reflejadas en el guion):
1. Bubblewrap instala `build-tools;36.1.0` con comillas que `cmd` rompe en Windows → instalación previa con `sdkmanager`.
2. `gradlew.bat` no se encontraba: el entorno fija `NoDefaultCurrentDirectoryInExePath=1` → se retira solo en el proceso del guion.
3. AGP 8.9.1 (plantilla de Bubblewrap) no reconoce `platforms;android-36` instalada por `cmdline-tools` 23 → Gradle compila contra el SDK estándar (`local.properties` → `D:\Android\Sdk`); Bubblewrap usa su SDK solo para `zipalign`/`apksigner`.
4. `androidbrowserhelper` 2.7.4 exige `minSdkVersion` 24 → corregido en `twa-manifest.json` (Android 7.0 o posterior).

**Emulador:** `adb install -r` correcto; paquete `pe.barberturno.prueba.twa`, `minSdk=24`, `targetSdk=36`, actividad de entrada `.LauncherActivity`.

### 3.2 Firma sin contraseñas en la línea de órdenes (hallazgos T1/T2 de Codex 145, `3541587`)
`bubblewrap build --skipSigning` + `zipalign -p 4` + `apksigner sign --ks-pass env:BT_CLAVE --key-pass env:BT_CLAVE` + `apksigner verify`. Regenerada desde la integración (iconos de M ya integrados): APK firmada y verificada, misma huella; la contraseña de prueba **no** aparece en el registro (comprobación por contenido, sin mostrarla). El parámetro `-Puerto` se retira (8790 fijo, como en `twa-manifest.json`).

## 4. Emulador (CP-10) · 05/11/2026 20:5x–21:0x · ejecutor Claude
Entorno: AVD `Medium_Phone_API_36.0` desde `D:\Android` (Android 16, Chrome 141.0.7390.111), arrancado con `-no-window -gpu guest` (con `swiftshader_indirect` sin ventana las capturas salían en blanco). Backend: jar de la integración (`50342dc`, frontend idéntico al final) con `dev,demo` sobre `barberturno_v2_test` (cerrojo común; base vaciada al terminar, rc=0), `BT_TWA_PAQUETE=pe.barberturno.prueba.twa` y la huella de prueba; `adb reverse tcp:8081 tcp:8081`. Chrome con `set-debug-app` y `/data/local/tmp/chrome-command-line` (`--disable-fre … --disable-digital-asset-link-verification-for-url="http://localhost:8081"`, guía de pruebas de Chromium). El lanzador del sistema daba avisos de «no responde» por el renderizado por software; se cerraban sin cambiar configuración.

| Comprobación | Resultado | Tipo |
|---|---|---|
| `GET /.well-known/assetlinks.json` del jar real | 200 `application/json`, sentencia oficial con paquete y huella de prueba; `manifest.webmanifest` → `application/manifest+json`; `ngsw-worker.js` → 200 | web (Tomcat real) |
| Instalación de la APK de prueba | `adb install -r` correcto (`minSdk 24`, `targetSdk 36`) | emulador |
| Arranque de la app TWA | Abre Chrome como **pestaña personalizada con barra de dirección** sobre `https://localhost/` → «localhost refused to connect»; el lanzador ignora la URL `http://` pasada por *intent*; `digital_asset_links_handler: endpoint responded with code -1` ([captura](v2-18-android/01-twa-sin-dominio.png)) | emulador · **TWA no validada** |
| Acceso, navegación y rutas (`/ingresar` → `/negocios` → `/n/barberturno/reservar?papel=cliente`) | correcto en Chrome del emulador | emulador (Chrome, no TWA) |
| Botón Atrás físico con el diálogo de avisos abierto | cierra el diálogo, la ruta no cambia | emulador (Chrome) |
| Wi-Fi y datos apagados | aviso «Sin conexión…» visible; desaparece al recuperar la red ([captura](v2-18-android/02-sin-conexion.png)) | emulador (Chrome) |
| Salir y Atrás | vuelve a «Iniciar sesión» (`/ingresar?returnUrl=%2Fnegocios`), sin datos de la cuenta | emulador (Chrome) |
| Exportación Excel (encargado, Reportes) | Chrome pide confirmar y guarda `reporte-barberturno-2026-11-01_2026-11-30.xlsx` (3 743 B, ZIP con `xl/worksheets/sheet1.xml`) en `Download/` ([captura](v2-18-android/03-excel-descargado.png)) | emulador (Chrome) |
| Enlace de invitación | «Copiar» → «Enlace copiado.»; tras salir, el *intent* `VIEW` del enlace `http://localhost:8081/invitacion/…` lo atiende Chrome y muestra «BarberTurno le invita como Barbero» ([captura](v2-18-android/04-enlace-invitacion.png)); un enlace `https://localhost/…` abre el **selector** (Chrome o la app), porque sin dominio verificado Android no asocia los enlaces a la app | emulador |

**Queda bloqueado (depende del responsable: dominio, HTTPS y alojamiento):** verificación real de Digital Asset Links, apertura de la app **sin barra de dirección** (modo TWA verificado), apertura directa de enlaces `https://<dominio>/…` en la app (App Links verificados), huella de la clave de publicación y Google Play. Sin dominio, el modo TWA no puede comprobarse en el emulador de forma honesta: la bandera de Chrome solo omite la verificación y además la plantilla de Bubblewrap fuerza `https://` + host. **No se ha probado en un teléfono real ni con TalkBack.**
