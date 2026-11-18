# V2-CONS · Encargo 161 · Correcciones de frontend

Fecha: 18/11/2026. Implementador: Codex. Rama: `tarea/V2-CONS-consolidacion`.
Base: `97bf5b1` (integración de la demo sobre `b7f663b`). Estado: **Hecha en local;
pendiente de revisión del coordinador**. Sin integración ni despliegue.

## Cambios y criterios

- `whatsapp-profesional.ts`: `fallar` marca el error con `marcarPresentado` para las
  tres operaciones (`cargar`, `generar` y `desvincular`). El 404 esperado oculta el
  panel sin aviso; el resto conserva el mensaje junto a la acción. Se reutiliza
  V2-DA-50 y V2-DA-109, sin modificar el interceptor ni los contratos.
- `whatsapp-profesional.spec.ts`: interceptor real y espía de `MatSnackBar.open`.
  Regresión de GET/POST/DELETE × 404/500, esperando la microtarea del interceptor.
  Comprueba panel oculto y ningún aviso ante 404, y alerta accesible sin detalle
  técnico ni snackbar duplicado ante 500. Conserva los cuatro casos satisfactorios.
- `shell.html`: «BarberTurno · Reservas y turnos para barberías · Proyecto académico
  con datos ficticios». `git grep -n "Una sede" -- frontend`: sin coincidencias
  después del cambio; no había pruebas que exigieran el texto anterior.
- `generar-iconos.mjs` y `public/favicon.ico`: se rasteriza el SVG existente con
  Chromium, y se escribe el contenedor ICO usando `Buffer`, sin dependencias
  adicionales. Tres imágenes PNG de 16, 32 y 48 px. Cierre del navegador en
  `finally`. Los cuatro PNG existentes se regeneraron sin cambios de bytes.
- `index.html`: enlace explícito al ICO con sus tamaños; conserva el SVG existente.
- `manifest.webmanifest`: descripción «BarberTurno · Reservas y turnos para
  barberías»; `name` y `short_name` ya eran «BarberTurno». E2E correspondiente
  adaptada en `e2e/pwa/movil.spec.ts`.
- Solo formato, exigido por Prettier: `e2e/demo/aceptacion.spec.ts`,
  `e2e/demo/ayudantes.ts`, `playwright.demo.config.ts` y una expresión preexistente
  en `e2e/pwa/movil.spec.ts`. Sin cambios de aserciones ni de ejecución en la demo.

## Entorno y comandos

Windows, PowerShell, Node **24.21.0**, npm **11.19.0**, Angular core **22.2.1**,
CLI **22.2.0**, Vitest **5.0.3**, Prettier **3.9.9**, TypeScript **6.0.3**,
Playwright **1.63.0** (versiones del lockfile existente).
`frontend/node_modules` es la unión preexistente del worktree `-v2-twilio`.
No se ejecutó `npm ci` ni `npm install`.

Desde `frontend/`, antes de cada verificación:

```powershell
& 'C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe\fnm.exe' env --use-on-cd --version-file-strategy recursive --shell powershell | Out-String | Invoke-Expression
fnm use 24.21.0
```

Se fija la versión explícita: la inicialización del perfil seleccionaba 24.22.2.
El primer intento aislado de `fnm env` falló al crear su enlace de sesión:

```text
error: Can't create the symlink for multishells [...]
Acceso denegado. (os error 5)
error: We can't find the necessary environment variables to replace the Node version.
```

Se repitió con escalación, aceptada por la revisión automática. Sin cambios de ACL,
propietario ni configuración. El intento de consulta CIM aislado también fue
rechazado por permisos; se consultaron solo identificadores/nombres de procesos
con escalación, sin sus líneas de comandos.

| Orden desde `frontend/` | Resultado |
|---|---|
| `npm run lint` | Correcto: `All files pass linting.`; también `tsc -p tsconfig.e2e.json`, salida 0 |
| `npx --no-install prettier --check .` | Correcto tras el formato descrito abajo; salida 0 |
| `npm test -- --watch=false` | Primer intento: worker finalizó con SIGTERM, salida 1; repetición completa con `maxWorkers: 4`: **652/652, 56 archivos**, salida 0 |
| `npm run build` | Correcto, sin avisos: inicial **509,09 kB**, transferencia estimada 128,72 kB; salida 0 |
| `node tools/generar-iconos.mjs` | Correcto; 4 PNG sin cambios y favicon de 2079 B |
| `git diff --check` | Correcto |

`--no-install` garantiza que `npx` usa el Prettier ya instalado. Salida del primer
control completo (código 1), corregida exclusivamente mediante Prettier:

```text
Checking formatting...
[warn] e2e/demo/aceptacion.spec.ts
[warn] e2e/demo/ayudantes.ts
[warn] playwright.demo.config.ts
[warn] Code style issues found in 3 files. Run Prettier with --write to fix.
```

Corrección y repetición:

```powershell
npx --no-install prettier --write e2e/demo/aceptacion.spec.ts e2e/demo/ayudantes.ts playwright.demo.config.ts
npx --no-install prettier --check .
```

```text
Checking formatting...
All matched files use Prettier code style!
```

El primer intento de Vitest no completó la suite:

```text
Error: Worker exited unexpectedly with signal SIGTERM during stopping state while running test file D:/Proyectos/BarberTurno-v2-cons/frontend/src/app/core/api/reservas-avisos-api.spec.ts
    at ChildProcess.emitUnexpectedExit (file:///D:/Proyectos/BarberTurno-v2-twilio/frontend/node_modules/vitest/dist/chunks/index.DpLw24bj.js:11005:33)
    at ChildProcess.emit (node:events:526:24)
    at Process.ChildProcess._handle.onexit (node:internal/child_process:295:12)
Emitted 'error' event at:
    at ChildProcess.emitUnexpectedExit (file:///D:/Proyectos/BarberTurno-v2-twilio/frontend/node_modules/vitest/dist/chunks/index.DpLw24bj.js:11007:22)
    at ChildProcess.emit (node:events:526:24)
    at Process.ChildProcess._handle.onexit (node:internal/child_process:295:12)
Node.js v24.21.0
```

Se aplica la repetición autorizada con cuatro workers, sin cambiar tiempos máximos,
aserciones ni pruebas. Archivo temporal ignorado
`frontend/tmp/v2-cons-161/vitest.config.mjs`:

```javascript
export default { test: { maxWorkers: 4 } };
```

```powershell
npm test -- --watch=false --runner-config=tmp/v2-cons-161/vitest.config.mjs
```

```text
Test Files  56 passed (56)
     Tests  652 passed (652)
  Start at  01:17:44
  Duration  12.91s (environment 69%, tests 21%, import 6%, transform 3%, setup 2%)
```

Build:

```text
Initial total | 509.09 kB | 128.72 kB
Application bundle generation complete. [9.940 seconds]
Output location: D:\Proyectos\BarberTurno-v2-cons\frontend\dist\frontend
```

Se verificó que el `favicon.ico` del build es idéntico al de `public/` por SHA-256,
que `/favicon.ico` aparece en la tabla de hashes de `ngsw.json` y que el manifest
empaquetado conserva los tres textos de marca esperados.

Playwright E2E no se ejecuta por instrucción del encargo; queda a cargo del
coordinador. La E2E modificada sí forma parte de la comprobación TypeScript de lint.

## Verificación del favicon

Generación con el Chromium existente, sin descargas:

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = 'D:\Proyectos\BarberTurno-v2-twilio\frontend\tmp\navegadores'
node tools/generar-iconos.mjs
```

Comprobación independiente con `System.Drawing.Icon`: cabecera ICO (reservado 0,
tipo 1, tres imágenes), directorio contiguo sin bytes sobrantes, límites de cada
imagen, firma PNG y decodificación de cada tamaño solicitado. Se exportaron las
imágenes a `frontend/tmp/v2-cons-161/favicon-{16,32,48}.png` para inspección visual.
Las tijeras verdes y el fondo claro coinciden con `public/iconos/icono.svg`.

```text
ICO válido: 16x16, PNG 307 B, decodificado con System.Drawing.Icon
ICO válido: 32x32, PNG 659 B, decodificado con System.Drawing.Icon
ICO válido: 48x48, PNG 1059 B, decodificado con System.Drawing.Icon
Tamaño total: 2079 B
```

## Coherencia de la marca y hallazgos

- `<title>`, `name` y `short_name`: «BarberTurno», ya correctos. La descripción
  del manifest ahora incluye también la marca. No existe
  `apple-mobile-web-app-title` ni un texto propio de invitación a instalar la PWA:
  la instalación toma el nombre del manifest.
- Fuera del frontend, `android/twa-manifest.json` mantiene `name` =
  «BarberTurno (demo)» y `launcherName` = «BarberTurno», con los iconos verdes.
  Es una distinción de demo preexistente; se informa sin modificar Android.
- Observación adicional para el arquitecto: `features/auth/privacidad.ts` todavía
  describe una sola barbería ficticia de Huamanga. No se cambia el aviso de
  privacidad, ajeno a las cuatro correcciones autorizadas.

## Cierre y límites

| Commit | Asunto |
|---|---|
| `02fb81bf8788e81148fdb7de4cf730a4a5f1edd6` | `fix(agenda): silenciar el vínculo profesional no disponible [V2-CONS]` |
| `0daa44380a08088832b466421c49ad41c281b97f` | `fix(frontend): unificar marca e iconos de BarberTurno [V2-CONS]` |
| `b8244610d00528e1e43888a08c7bfbaae8f13598` | `style(e2e): corregir formato pendiente de la demo [V2-CONS]` |

Commit documental identificado por asunto:
`docs: registrar correcciones y verificación del encargo 161 [V2-CONS]`;
su hash propio se entrega en el informe final. **Merge: ninguno**, por instrucción
expresa. `git diff --check` antes de cada commit.

El primer `git add` aislado fue rechazado al crear el índice del worktree:

```text
fatal: Unable to create 'D:/Proyectos/BarberTurno/.git/worktrees/BarberTurno-v2-cons/index.lock': Permission denied
```

Los commits se hicieron con escalación aprobada, sin cambiar permisos ni ACL.
No quedan workers ni navegadores propios. Comparación final por PID y padre:
solo los Node preexistentes (`3680`, `29668`), los Java preexistentes (`17372`,
`2264`) y el Chrome previo (`15844` y sus hijos). Se conservaron intactos.

No hay cambios de arranque o de comandos de pruebas; README sin cambios.
Sin código Java nuevo o modificado: Javadoc no aplica. No se crean endpoints.
Sin cambios en backend, migraciones, Android, documentos de diseño/requisitos,
APF2, configuración ni secretos. Sin consultas a `.local/coordinacion/`, sin
lectura de archivos `.local/`, sin PostgreSQL, servidores, publicación, merge,
push, remotos, etiquetas, ramas nuevas ni worktrees nuevos.
