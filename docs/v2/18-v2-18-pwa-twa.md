# V2-18 · PWA y preparación de Android mediante TWA

> **Estado: en desarrollo local** · autorizado por el responsable el 05/11/2026 (ruta «PWA primero y Android mediante TWA después»).
> Rama `tarea/V2-PWA-TWA` (worktree `D:\Proyectos\BarberTurno-v2-pwa`) creada desde `tarea/V2-MVP-revision` = **`95984cb`** (solo evidencia sobre el código probado `761eb33`).
> La autorización **no** aprueba la revisión manual del MVP ni su integración en `v2`. Sin push, despliegue, dominio, túneles, cuentas ni Google Play. Notificaciones push: fuera de esta etapa.

## 1. Alcance
| Entra | No entra |
|---|---|
| Manifest, iconos e instalación de la aplicación Angular existente | Dominio definitivo, HTTPS público, alojamiento, publicación en Google Play |
| *Service worker* con caché **solo** de recursos públicos de la aplicación | Caché de `/api`, disponibilidad, citas, conversaciones o cualquier respuesta personalizada |
| Estado sin conexión y recuperación al volver | Escrituras encoladas o reintentadas en segundo plano (Background Sync) |
| Actualizaciones controladas por el usuario | Recarga automática que pierda formularios o interrumpa operaciones |
| Botón Atrás coherente con diálogos, menú y navegación | Notificaciones push |
| Teclado, bordes de pantalla, orientación, tamaños, formularios, escalado de texto y accesibilidad móviles | Cambios de reglas de negocio, permisos, autenticación o aislamiento |
| Proyecto TWA reproducible (Bubblewrap), Digital Asset Links servido desde configuración y APK de depuración local | Firma de publicación, claves en Git, huellas inventadas en producción |

Se reutilizan Angular 22, la identidad visual de la fase verde y el backend. La autenticación por cookies, CSRF, las restricciones de origen (CSP `'self'`) y el aislamiento entre barberías **no cambian**.

## 2. Decisiones (registradas en `07-decisiones.md`)
- **V2-DA-87 · `@angular/service-worker` oficial.** Versión **22.2.1** (la misma que `@angular/core` del *lockfile*; npm, 05/11/2026). Se registra solo en producción (`!isDevMode()`), con `registerWhenStable:30000`. Sin *service worker* propio escrito a mano.
- **V2-DA-88 · Qué guarda la caché.** Solo `assetGroups` de la aplicación: `index.html`, *bundles* JS/CSS con hash, fuentes, `manifest.webmanifest`, iconos y fotos públicas de `public/fotos` (perezosas). **Ningún grupo de datos** (precisión del 05/11, hallazgo de Codex 144: el generador oficial escribe siempre `"dataGroups": []` en `ngsw.json`; lo exigible es que la lista esté vacía y que `ngsw-config.json` no declare ninguno). `navigationUrls` excluye `/api/**`, `/actuator/**`, `/.well-known/**`, `/v3/**`, `/swagger-ui/**` y toda ruta con extensión. Una prueba lee el `ngsw.json` generado y falla si aparece cualquier patrón de API o un `dataGroups`. Otra prueba recorre la aplicación con sesión y comprueba que ninguna entrada de `CacheStorage` sea de `/api` ni contenga datos de la cuenta.
- **V2-DA-89 · Sin conexión.** Aviso fijo y anunciado («Sin conexión. Lo que ve puede no estar actualizado; no se guardará ningún cambio hasta que vuelva la conexión»). Una petición de escritura que falla por red (`status 0`) muestra un error claro y **no se reintenta ni se encola**; el usuario repite la acción. Al volver la conexión, aviso breve y las vistas pueden volver a cargar (la vista actual ofrece *Reintentar*). El aviso nunca afirma que algo se guardó.
- **V2-DA-90 · Actualizaciones controladas.** `SwUpdate`: con `VERSION_READY` aparece un aviso no modal «Hay una versión nueva» con *Actualizar*; nunca se recarga sola. Si hay un formulario modificado o un diálogo abierto, *Actualizar* pide confirmación. Comprobación al volver al primer plano (como mucho cada 30 min). `UNRECOVERABLE_STATE` → aviso de recarga obligatoria. La versión anterior instalada sigue funcionando hasta que el usuario acepta.
- **V2-DA-91 · Botón Atrás.** Abrir un diálogo, el cajón de navegación móvil o un menú superpuesto añade una entrada de historial; Atrás cierra la superposición sin cambiar de ruta. Cerrarla por otro medio retira esa entrada sin navegar. Sin bucles ni entradas duplicadas; tras cerrar, Atrás vuelve a la ruta anterior. Con una sola entrada (arranque profundo), Atrás sale de la aplicación (comportamiento estándar de TWA). Implementación central (`core/pwa`, `layout/shell.ts`), sin tocar cada diálogo.
- **V2-DA-92 · Pantalla móvil.** `viewport-fit=cover` y `interactive-widget=resizes-content`; márgenes con `env(safe-area-inset-*)`; la barra inferior se oculta mientras el teclado está abierto y el campo enfocado queda visible (también dentro de diálogos). Orientación libre (`"orientation": "any"`, WCAG 1.3.4). Reflujo sin desplazamiento horizontal a 320 px y en horizontal (800×360); texto al 200 % sin pérdida de contenido; objetivos táctiles ≥ 44×44 px en acciones principales.
- **V2-DA-93 · Sin estado residual entre cuentas o barberías.** Al cerrar sesión, al iniciar con otra cuenta y al recibir un 401 se limpian todos los estados en memoria de raíz (sesión, barbería, avisos, reserva en curso) y el almacenamiento propio (`barberturno.*` de `sessionStorage` y `localStorage`). Tras cerrar sesión, Atrás no muestra páginas privadas con datos anteriores. El cambio de barbería mantiene el reinicio de V2-07 (`NegocioActual.seleccionar`). Como la caché solo guarda recursos públicos idénticos para todos, no hay que vaciarla al cambiar de cuenta (se comprueba, no se supone).
- **V2-DA-94 · Manifest.** `id` y `start_url` `/`, `scope` `/`, `display` `standalone`, `lang` `es-PE`, nombre «BarberTurno», colores de la fase verde (`#145C43`), iconos PNG 192 y 512 (`any`) y 512 `maskable`; fuente SVG versionada y PNG generados por guion reproducible. Sin capturas ni accesos directos en esta etapa.
- **V2-DA-95 · TWA.** Bubblewrap (CLI oficial de Chrome Labs) con `twa-manifest.json` versionado en `android/`; el proyecto Gradle generado y las claves quedan fuera de Git. Valores de prueba **marcados**: paquete `pe.barberturno.prueba.twa`, nombre «BarberTurno (prueba)», origen local de prueba (ver §6). `/.well-known/assetlinks.json` lo sirve el backend **desde configuración** (`BT_TWA_PAQUETE`, `BT_TWA_HUELLAS`) y responde 404 si no está configurado: ningún binario lleva una huella inventada. Clave de depuración en `D:\Proyectos\BarberTurno\.local\android\` (fuera de Git, nunca se muestra).
- **V2-DA-96 · Presupuesto.** Si el *bundle* inicial supera 500 kB solo por `@angular/service-worker`, se sube el aviso del presupuesto inicial a **520 kB** (error 1 MB sin cambios) y se anota el tamaño real; cualquier otro crecimiento se corrige, no se absorbe.
  - **Revisión del 05/11 (Claude, con las mediciones de Codex 144 y de Gemini g110):** base `29f3ed9` 499,58 kB; núcleo sin registrar el *service worker* 500,58 kB (+1,0 kB: avisos de red y actualización y limpieza entre cuentas, que deben cargar al arrancar); con el *service worker* ≈ 507 kB (+6–7 kB); bloque M +0,97 kB (bordes seguros y teclado en estilos globales). Todo el crecimiento corresponde a funciones de la PWA que tienen que estar en el arranque, así que la condición «solo por el *service worker*» se amplía a «solo por funciones PWA de arranque» y se aplica el aviso de **520 kB** (error 1 MB igual). El tamaño integrado se anota en la evidencia. Los presupuestos por componente (4 kB / 8 kB) **no** cambian: lo que los supere se corrige.

## 3. Reparto (un implementador por archivo)
Worktrees creados desde el commit de contrato de esta rama. `frontend/node_modules` de los worktrees de implementación es una **unión** con el de `D:\Proyectos\BarberTurno-v2-pwa\frontend` (antes de `git worktree remove`: `cmd /c rmdir <unión>`). Nadie ejecuta `npm install`: `@angular/service-worker` ya está en `package.json` y en el *lockfile*.

| Bloque | Implementador | Rama / worktree | Archivos (exclusivos) | Revisa |
|---|---|---|---|---|
| **N · Núcleo PWA** | **Codex** | `tarea/V2-PWA-nucleo` · `D:\Proyectos\BarberTurno-v2-pwa-nucleo` | `frontend/ngsw-config.json` (nuevo), `frontend/angular.json`, `frontend/src/app/app.config.ts` (+ spec), `frontend/src/app/core/pwa/**` (nuevo), `frontend/src/app/core/auth/**`, `frontend/src/app/core/negocio/**`, `frontend/src/app/core/notificaciones/**`, `frontend/src/app/layout/shell.ts`, `shell.html`, `shell.spec.ts`, `frontend/src/app/features/reservar/reserva.store.ts` (solo limpieza de V2-DA-93), `frontend/playwright.pwa.config.ts` (nuevo), `frontend/e2e/pwa/**` (nuevo, salvo `pwa/movil.spec.ts`) | Gemini |
| **M · Adaptación móvil** | **Gemini** | `tarea/V2-PWA-movil` · `D:\Proyectos\BarberTurno-v2-pwa-movil` | `frontend/src/index.html`, `frontend/public/manifest.webmanifest` (nuevo), `frontend/public/iconos/**` (nuevo), `frontend/tools/generar-iconos.mjs` (nuevo), `frontend/src/styles.scss`, `frontend/src/tailwind.css`, `frontend/src/app/layout/shell.scss`, `frontend/src/app/features/**/*.html` y `*.scss`, `frontend/src/app/shared/**/*.html` y `*.scss`, `frontend/e2e/pwa/movil.spec.ts` (nuevo) | Codex |
| **T · TWA y asociación** | **Claude** | `tarea/V2-PWA-android` · `D:\Proyectos\BarberTurno-v2-pwa-android` (`…-twa` chocaba en Windows con `tarea/V2-PWA-TWA`) | `android/**` (nuevo), `backend/**` (solo `assetlinks` y, si hace falta, tipos MIME de `webmanifest`), `docs/v2/18-…`, `07-decisiones.md`, `10-backlog.md`, `docs/v2/pruebas/v2-18*.md` | Codex |

Reglas: un archivo fuera de la lista se pide al coordinador (traspaso registrado en §7). Gemini no ejecuta órdenes: Claude ejecuta sus pruebas y registra autor y ejecutor por separado. Nadie aprueba sus propios cambios.

## 4. Criterios de aceptación
| ID | Criterio | Prueba |
|---|---|---|
| CP-1 | La caché del *service worker* no contiene `/api` ni datos personales, con sesión de cliente, profesional y encargado | `ngsw.json` generado + E2E de `CacheStorage` tras recorrer con sesión (N) |
| CP-2 | Sin conexión: la aplicación arranca, muestra el aviso, y una escritura falla con error claro sin quedar encolada (no hay petición al volver la conexión) | E2E con `context.setOffline` y registro de peticiones (N) |
| CP-3 | Con una versión anterior instalada, la nueva se ofrece y solo se activa al aceptar; un formulario modificado no se pierde sin confirmación | E2E con dos *builds* servidos sucesivamente (N) |
| CP-4 | Cerrar sesión, entrar con otra cuenta en el mismo navegador y cambiar de barbería no muestra datos anteriores (ni con Atrás) | E2E con dos cuentas y dos barberías + unitarias de limpieza (N) |
| CP-5 | Atrás cierra diálogo, cajón y menú sin cambiar de ruta, sin bucles; después navega hacia atrás normalmente | E2E + unitarias del servicio de historial (N) |
| CP-6 | Con el teclado abierto el campo enfocado es visible y la barra inferior no lo tapa; bordes seguros respetados | unitarias (N) + E2E móvil con `visualViewport` simulado (M) |
| CP-7 | Instalable: manifest válido, iconos servidos con su tamaño y tipo, sin errores de instalabilidad de Chromium | E2E (M) |
| CP-8 | Reflujo a 320/360/412 px y en horizontal sin desplazamiento horizontal; texto al 200 %; objetivos táctiles; formularios con `autocomplete`, `inputmode` y `enterkeyhint` adecuados; axe sin violaciones serias o críticas en móvil | E2E (M) |
| CP-9 | `assetlinks.json` sale de la configuración, con el formato oficial, 404 sin configuración y sin autenticación | IT del backend (T) |
| CP-10 | APK de depuración generada desde `twa-manifest.json`, instalada en el emulador; arranque, rutas, exportación Excel y enlace de invitación probados en el emulador | Registro manual reproducible (T) |

Las pruebas web no sustituyen al emulador ni al teléfono: la evidencia separa **web**, **emulador** y **teléfono real**. axe no aprueba TalkBack.

## 5. Pruebas y recursos compartidos
- Frontend de cada bloque: `npm run lint`, `npm run format:check`, `npm test -- --watch=false`, `npm run build` con fnm 24.21.0.
- E2E de PWA: `playwright.pwa.config.ts`, `testMatch: ['**/pwa/**/*.spec.ts']`, **Chromium** a 360×800 y 1440×900 (el TWA usa Chrome), *build* de producción servido por el backend con el *frontend* empaquetado o por el servidor de pruebas de `e2e/pwa/`. Lo que use `barberturno_v2_test` va **en serie con el cerrojo** `D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock` y **nunca** mientras esté abierta la revisión manual del responsable (backend en 8081 que no lanzó el coordinador).
- Backend: sin cambios salvo el bloque T; se reutiliza el `verify` 3791/0/0/0 de `761eb33` para lo demás y se ejecutan las IT del bloque T.

## 6. TWA: qué se puede comprobar en local
- **Sin dominio público** no hay verificación real de Digital Asset Links. En el emulador se prueba con el backend local expuesto por `adb reverse` y la bandera de Chrome `--disable-digital-asset-link-verification-for-url` (guía de pruebas de Chromium), anotando que eso **no** valida la asociación.
- Queda bloqueado hasta que el responsable decida dominio, HTTPS y alojamiento: verificación real del `assetlinks.json`, modo sin barra de direcciones verificado, huella de la clave de publicación y Google Play.

## 7. Registro de traspasos y revisiones
| Fecha | Qué | De → a | Motivo |
|---|---|---|---|
| 05/11 18:33 | Cierre del bloque N (verificación final y ajustes) desde `f6e32e9` + evidencia `23679a3` | Codex → Claude | Codex agotó la cuota de 5 h a mitad de la pasada final (protocolo de `CLAUDE.md`: lo pendiente era pequeño). Codex sigue como revisor (encargo 145). |
| 05/11 | Prettier y ejecución de iconos/pruebas del bloque M | Gemini (autor) → Claude (ejecutor) | Gemini no puede ejecutar órdenes en modo desatendido. |
