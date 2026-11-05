# Informe de Implementación V2-18 · M: Adaptación móvil, formularios, accesibilidad, manifest e iconos de la PWA

- **Fecha:** 2026-11-05
- **Implementador:** Gemini (Antigravity CLI) — Bloque M (Adaptación móvil y presentación)
- **Worktree:** `D:\Proyectos\BarberTurno-v2-pwa-movil`
- **Rama:** `tarea/V2-PWA-movil`
- **Coordinador:** Claude Code
- **Estado general:** Completado para revisión y ejecución de pruebas por el coordinador

---

## 1. Qué se hizo, por archivo

### Ejecución (Claude) y Correcciones Aplicadas (Gemini)
Tras la ejecución y revisión del coordinador Claude Code (05/11/2026):
1. **Iconos:** `node tools/generar-iconos.mjs` generó los 4 PNGs (192, 512, maskable 512 y apple-touch 180) correctamente.
2. **Presupuesto y build:** El build inicial reportó 500,73 kB (aviso superado por 0,73 kB). Para recuperar presupuesto:
   - Se eliminó el bloque duplicado de `:host-context(.teclado-abierto)` en [`frontend/src/app/layout/shell.scss`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/layout/shell.scss) (ahorro de ~270 bytes en SCSS y en la cadena JS del componente `shell`).
   - Se simplificó la regla base de `.cabecera` en `shell.scss` (restringiendo `env(safe-area-inset-top)` únicamente al interior de `@media (max-width: 767.98px)`).
   - En [`frontend/src/styles.scss`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/styles.scss), se redujo el selector a `body.teclado-abierto`, se retiraron los `!important` prescindibles y se colapsó el relleno de `mat-dialog-container` en una sola declaración abreviada (ahorro estimado global de ~550 bytes).
3. **Lint (Playwright):** Se corrigió el error `TS2339` en [`frontend/e2e/pwa/movil.spec.ts`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/e2e/pwa/movil.spec.ts) cambiando `async ({ page, testInfo })` por `async ({ page }, testInfo) => ...`, ya que `testInfo` es el segundo argumento del ejecutor de pruebas de Playwright.
4. **Formularios de acceso y cuenta propia:**
   - En [`frontend/src/app/features/auth/ingresar.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/auth/ingresar.html) y [`frontend/src/app/features/auth/registro.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/auth/registro.html), el campo de correo volvió a `autocomplete="username"` (resolviendo el fallo de la prueba unitaria `ingresar.spec.ts`), conservando `inputmode="email"` y `enterkeyhint="next"`.
   - Se actualizó `movil.spec.ts` para exigir `autocomplete="username"` en `/ingresar`.
5. **Formularios de terceros:**
   - En [`frontend/src/app/features/admin/barbero-dialogo.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/admin/barbero-dialogo.html), se configuró `autocomplete="off"` en nombre, correo, teléfono y contraseña temporal.
   - En [`frontend/src/app/features/gestion/invitaciones.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/gestion/invitaciones.html), se configuró `autocomplete="off"` en el campo de correo de invitación.
   - En [`frontend/src/app/features/gestion/configuracion.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/gestion/configuracion.html), se configuró `autocomplete="off"` en el nombre del negocio.
6. **Descripción multinegocio v2:**
   - En [`frontend/src/index.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/index.html) y [`frontend/public/manifest.webmanifest`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/public/manifest.webmanifest), la descripción se actualizó a «Reservas y turnos para barberías» (sin referencia exclusiva a Huamanga).
   - Se incorporó la validación exacta de esta descripción en `movil.spec.ts`.
7. **Reglas de diálogo en móvil:** Se aseguró que `.cdk-overlay-pane:has(mat-dialog-container)` y `mat-dialog-container` en `styles.scss` apliquen única y estrictamente dentro del bloque `@media (max-width: 767.98px)`.

---

### Configuración PWA, Manifest e Iconos
- [`frontend/src/index.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/index.html):
  - Añadido `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover, interactive-widget=resizes-content">` permitiendo zum de usuario (sin `maximum-scale` ni `user-scalable=no`).
  - Añadido enlace `<link rel="manifest" href="manifest.webmanifest">`.
  - Configurado `<meta name="theme-color" content="#145c43">` y `<meta name="description" content="Sistema web de reservas y turnos para barberías en Huamanga">`.
  - Añadidos `<link rel="apple-touch-icon" sizes="180x180" href="iconos/apple-touch-icon.png">` y favicon vectorial `<link rel="icon" type="image/svg+xml" href="iconos/icono.svg">`.
- [`frontend/public/manifest.webmanifest`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/public/manifest.webmanifest):
  - Creado según V2-DA-94 con `id: "/"`, `start_url: "/"`, `scope: "/"`, `name: "BarberTurno"`, `short_name: "BarberTurno"`, `description`, `display: "standalone"`, `orientation: "any"`, `lang: "es-PE"`, `dir: "ltr"`.
  - Colores de la fase verde: `theme_color: "#145c43"` y `background_color: "#f6f8f7"`.
  - Iconos registrados: `iconos/icono-192.png` (192, any), `iconos/icono-512.png` (512, any), `iconos/icono-maskable-512.png` (512, maskable), todos `image/png`. Sin capturas ni accesos directos.
- [`frontend/public/iconos/icono.svg`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/public/iconos/icono.svg):
  - Icono vectorial SVG de alta definición (512x512) con motivo de tijeras estilizadas de barbería en verde de marca `#145c43` sobre fondo claro `#f6f8f7` con esquinas suaves.
- [`frontend/public/iconos/icono-maskable.svg`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/public/iconos/icono-maskable.svg):
  - Icono adaptable (maskable) con fondo a sangre completo (sin esquinas redondeadas) y motivo centrado y acotado estrictamente dentro de la zona segura del 80% (círculo de radio 204.8 px en el centro 256, 256).
- [`frontend/tools/generar-iconos.mjs`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/tools/generar-iconos.mjs):
  - Script Node ESM sin dependencias adicionales que aprovecha `@playwright/test` (`chromium`) para rasterizar con precisión los SVG a PNG:
    - `icono-192.png` (192x192, fondo opaco).
    - `icono-512.png` (512x512, fondo opaco).
    - `icono-maskable-512.png` (512x512, fondo a sangre opaco).
    - `apple-touch-icon.png` (180x180, fondo opaco).
  - Respeta `process.env['PLAYWRIGHT_BROWSERS_PATH']` (o `tmp/navegadores`).

### Estilos, Safe Areas y Teclado Virtual
- [`frontend/src/app/layout/shell.scss`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/layout/shell.scss):
  - Márgenes de seguridad `env(safe-area-inset-top|bottom|left|right)` aplicados en `.cabecera`, `.barra-inferior`, `.riel`, `main` y `footer` móvil.
  - Objetivos táctiles mínimos: botones de cabecera con `min-width: 44px; min-height: 44px;`, enlaces de barra inferior con `min-height: 48px; min-width: 64px;`.
  - Regla que oculta `.barra-inferior` (`display: none !important`) y anula `--espacio-navegacion-inferior: 0px !important; padding-bottom: 0 !important;` ante la presencia de la clase `.teclado-abierto`.
- [`frontend/src/styles.scss`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/styles.scss):
  - Márgenes seguros en diálogos móviles (`.cdk-overlay-pane:has(mat-dialog-container)` y `mat-dialog-container`).
  - Regla global para `body.teclado-abierto, .teclado-abierto` que oculta la barra inferior y reinicia el scroll-padding del contenido.
- [`frontend/src/app/shared/_dialogo.scss`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/shared/_dialogo.scss):
  - Ajuste de `max-height` y `max-width` deduciendo `env(safe-area-inset-*)`.

### Optimización Móvil de Formularios
- [`frontend/src/app/features/auth/ingresar.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/auth/ingresar.html): correo con `autocomplete="email" inputmode="email" enterkeyhint="next"`; contraseña con `autocomplete="current-password" enterkeyhint="done"`.
- [`frontend/src/app/features/auth/registro.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/auth/registro.html): nombre (`autocomplete="name" enterkeyhint="next"`), correo (`autocomplete="email" inputmode="email" enterkeyhint="next"`), teléfono (`autocomplete="tel" inputmode="tel" enterkeyhint="next"`), contraseñas (`autocomplete="new-password"` con `enterkeyhint="next"` y `"done"`).
- [`frontend/src/app/features/auth/cambiar-password.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/auth/cambiar-password.html): contraseña actual (`enterkeyhint="next"`) y nueva (`enterkeyhint="done"`).
- [`frontend/src/app/features/perfil/perfil.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/perfil/perfil.html): nombre, teléfono (`inputmode="tel"`), correo de solo lectura.
- [`frontend/src/app/features/reservar/reservar.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/reservar/reservar.html): fecha y motivo de reprogramación con `enterkeyhint="done"`.
- [`frontend/src/app/features/agenda/agenda.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/agenda/agenda.html) y [`reprogramar-dialogo.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/agenda/reprogramar-dialogo.html): fecha y motivo con `enterkeyhint`.
- [`frontend/src/app/features/mis-citas/mis-citas.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/mis-citas/mis-citas.html) y [`cancelar-dialogo.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/mis-citas/cancelar-dialogo.html): fechas con `enterkeyhint="next"`/`"done"`, motivo con `enterkeyhint="done"`.
- [`frontend/src/app/features/bandeja-whatsapp/bandeja.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/bandeja-whatsapp/bandeja.html): textarea de respuesta con `enterkeyhint="send"`.
- [`frontend/src/app/features/admin/barbero-dialogo.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/admin/barbero-dialogo.html): usuarioId (`inputmode="numeric"`), nombre, correo, teléfono (`inputmode="tel"`), especialidad, contraseña temporal (`enterkeyhint="done"`).
- [`frontend/src/app/features/admin/servicio-dialogo.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/admin/servicio-dialogo.html): duración (`inputmode="numeric"`), precio (`inputmode="decimal"`).
- [`frontend/src/app/features/admin/dia-semana.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/admin/dia-semana.html) y [`horarios.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/admin/horarios.html): intervalos y bloqueos con `enterkeyhint`.
- [`frontend/src/app/features/admin/reportes.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/admin/reportes.html): rango de fechas con `enterkeyhint`.
- [`frontend/src/app/features/admin/usuarios.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/admin/usuarios.html): búsqueda con `type="search" autocomplete="off" enterkeyhint="search"`.
- [`frontend/src/app/features/gestion/configuracion.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/gestion/configuracion.html): parámetros numéricos con `inputmode="numeric"`.
- [`frontend/src/app/features/gestion/invitaciones.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/gestion/invitaciones.html) y [`aceptacion.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/features/gestion/aceptacion.html): correo y campos de registro.
- [`frontend/src/app/shared/selector-cliente.html`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/src/app/shared/selector-cliente.html): campo de búsqueda con `mat-label`, `type="search"`, `autocomplete="off"`, `enterkeyhint="search"`.

### Pruebas E2E PWA Móvil
- [`frontend/e2e/pwa/movil.spec.ts`](file:///D:/Proyectos/BarberTurno-v2-pwa-movil/frontend/e2e/pwa/movil.spec.ts):
  - Pruebas Playwright integradas con Chromium.
  - Cobertura de CP-7, CP-6 visual y CP-8.

---

## 2. Correspondencia criterio → prueba

| Criterio | Descripción | Prueba en `frontend/e2e/pwa/movil.spec.ts` |
|---|---|---|
| **CP-6 (visual)** | La barra inferior no estorba ni reserva espacio con teclado abierto | `CP-6 (visual) · con clase teclado-abierto la barra inferior se oculta y no reserva espacio` comprueba que con `.teclado-abierto` en `body` la barra inferior queda oculta y `paddingBottom` de `.con-barra-inferior` es `0`. |
| **CP-7** | Manifest válido y completo | `GET /manifest.webmanifest responde 200 con campos completos según V2-DA-94` comprueba status 200, Content-Type manifest/json, campos obligatorios y lista de 3 iconos. |
| **CP-7** | Iconos servidos con dimensiones reales exactas | `cada icono PNG responde 200 y sus dimensiones IHDR son exactas` verifica HTTP 200, Content-Type `image/png`, y decodifica el chunk IHDR para validar exactamente 192x192, 512x512, 512x512 y 180x180 px. |
| **CP-7** | Metadatos y enlaces en HTML | `HTML principal enlaza el manifest y contiene viewport-fit=cover y theme-color` comprueba `link[rel="manifest"]`, `meta[name="theme-color"]`, `meta[name="viewport"]` y `link[rel="apple-touch-icon"]`. |
| **CP-7** | Instalabilidad Chromium | `instalabilidad Chromium vía CDP sin errores` consulta `Page.getInstallabilityErrors` vía CDP. |
| **CP-8** | Reflujo sin scroll horizontal a 320, 360, 412 y horizontal 800×360 px | Pruebas de reflujo en `/ingresar`, `/n/barberturno/reservar`, `/n/barberturno/mis-citas`, `/n/barberturno/agenda` y `/n/barberturno/whatsapp` validando `document.documentElement.scrollWidth <= window.innerWidth + 1`. |
| **CP-8** | Escalado de texto al 200% sin pérdida de contenido | Pruebas de zoom al 200% (`style.fontSize = '200%'`) en las 5 pantallas clave comprobando ausencia de desbordamiento horizontal. |
| **CP-8** | Objetivos táctiles interactivos ≥ 44×44 px | Pruebas de objetivos táctiles en las 5 pantallas comprobando boundingBox ≥ 44×44 px en botones principales y enlaces de navegación inferior. |
| **CP-8** | Formularios optimizados para teclado móvil | Pruebas en `/ingresar` comprobando `autocomplete="username"`, `inputmode="email"`, `enterkeyhint="next"` y `autocomplete="current-password"`, `enterkeyhint="done"`. |
| **CP-8** | Auditoría axe en móvil a 360 px | Análisis con AxeBuilder a 360×800 px en las 5 pantallas clave comprobando 0 infracciones de impacto `serious` o `critical`. |

---

## 3. Supuestos sobre el contrato
- Codex añade en `<body>` o en el host del shell la clase `.teclado-abierto` cuando el teclado virtual esté visible por JS. En M proveemos las reglas CSS para `.teclado-abierto .barra-inferior` y `.con-barra-inferior`.
- Claude ejecutará `node tools/generar-iconos.mjs` para rasterizar los SVG a los archivos PNG físicos en `frontend/public/iconos/`.
- La configuración de Playwright PWA creada por Codex (`playwright.pwa.config.ts`) incluye `testMatch: ['**/pwa/**/*.spec.ts']` y baseURL `http://localhost:8081`.

---

## 4. Peticiones a Codex / Claude
- **Para Claude:** Ejecutar `node tools/generar-iconos.mjs` dentro de `frontend/` para generar los PNG en `public/iconos/`.
- **Para Codex:** Asegurar que cuando su detector JS de teclado virtual detecte la apertura del teclado, añada la clase `teclado-abierto` a `document.body` (o al contenedor host del shell). Con ello, las reglas CSS implementadas ocultarán la barra y anularán el padding inferior automáticamente sin necesidad de manipulación de estilos en línea.

---

## 5. Lo que no se pudo cubrir
- No se ejecutaron órdenes de consola (`npm run lint`, `node tools/generar-iconos.mjs`, `npx playwright test`) en cumplimiento estricto de las directrices no interactivas del encargo. La ejecución, generación de PNGs y verificación E2E quedan delegadas al coordinador Claude.

---

## 6. Traspaso: archivos completos y siguiente paso concreto
- **Archivos implementados (completos):**
  1. `frontend/src/index.html`
  2. `frontend/public/manifest.webmanifest`
  3. `frontend/public/iconos/icono.svg`
  4. `frontend/public/iconos/icono-maskable.svg`
  5. `frontend/tools/generar-iconos.mjs`
  6. `frontend/src/app/layout/shell.scss`
  7. `frontend/src/styles.scss`
  8. `frontend/src/app/shared/_dialogo.scss`
  9. `frontend/src/app/features/auth/ingresar.html`
  10. `frontend/src/app/features/auth/registro.html`
  11. `frontend/src/app/features/auth/cambiar-password.html`
  12. `frontend/src/app/features/perfil/perfil.html`
  13. `frontend/src/app/features/reservar/reservar.html`
  14. `frontend/src/app/features/agenda/agenda.html`
  15. `frontend/src/app/features/agenda/reprogramar-dialogo.html`
  16. `frontend/src/app/features/mis-citas/mis-citas.html`
  17. `frontend/src/app/features/mis-citas/cancelar-dialogo.html`
  18. `frontend/src/app/features/bandeja-whatsapp/bandeja.html`
  19. `frontend/src/app/features/admin/barbero-dialogo.html`
  20. `frontend/src/app/features/admin/servicio-dialogo.html`
  21. `frontend/src/app/features/admin/dia-semana.html`
  22. `frontend/src/app/features/admin/horarios.html`
  23. `frontend/src/app/features/admin/reportes.html`
  24. `frontend/src/app/features/admin/usuarios.html`
  25. `frontend/src/app/features/gestion/configuracion.html`
  26. `frontend/src/app/features/gestion/invitaciones.html`
  27. `frontend/src/app/features/gestion/aceptacion.html`
  28. `frontend/src/app/shared/selector-cliente.html`
  29. `frontend/e2e/pwa/movil.spec.ts`
  30. `docs/v2/pruebas/v2-18-movil.md`
- **Siguiente paso concreto para Claude:**
  1. Ejecutar `npm run lint` para verificar la corrección del tipo TS2339 en `movil.spec.ts`.
  2. Ejecutar `npm test` para verificar que `ingresar.spec.ts` y demás unitarias pasen limpiamente con `autocomplete="username"`.
  3. Ejecutar `npm run build` para comprobar que el bundle inicial vuelve a colocarse dentro o en el umbral del presupuesto de 500 kB con los ahorros de estilos aplicados.
  4. Ejecutar `prettier --write` sobre los archivos modificados.
  5. Ejecutar las pruebas E2E PWA móviles (`npx playwright test --config=playwright.pwa.config.ts e2e/pwa/movil.spec.ts`).
