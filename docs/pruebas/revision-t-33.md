# Revisión técnica de T-33 — Empaquetado y endurecimiento de producción

- **Fecha:** 04/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 037 (Codex, código 0, 39 min). Clasificación: CRÍTICA (rutas públicas, cabeceras y `prod`).
- **Commits:** hasta `f920385`, cierre `e53e091`, merge `532920b`, registro `e2a9b67`.
- **Resultado:** **Aprobada con una observación** (→ T-46).

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Empaquetado | Perfil Maven `con-frontend` (`npm ci` + `npm run build` y copia de `dist/frontend/browser` a `target/classes/static`); sin el perfil, el build normal no incluye estáticos (comprobado en el worktree). `inlineCritical: false` en `angular.json` para que el `index.html` no tenga scripts en línea. |
| V-02 | Rutas de la SPA | `SpaForwardFilter`: público solo `GET`/`HEAD` fuera de `/api`, `/actuator`, `/v3/api-docs` y `/swagger-ui`; reenvío a `index.html` solo sin extensión y con `Accept` HTML; prueba de que una muestra de rutas `/api/**` sigue cerrada. `PasswordTemporalFilter` deja cargar la SPA sin abrir la API restringida (regresión añadida). |
| V-03 | Cabeceras | HSTS (en `prod` o petición segura), `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin` y la CSP del encargo (`'unsafe-inline'` solo en estilos; el *nonce* con `ngCspNonce` queda como mejora futura documentada). |
| V-04 | `prod` | Health sin detalles, errores sin trazas, logs ECS; revisión de logs sin correos completos, contraseñas, tokens ni cookies. `docs/despliegue.md` con requisitos, variables, endurecimiento de PostgreSQL (`REVOKE … FROM PUBLIC`, rol mínimo), proxy, monitor y servicio. |
| V-05 | Verificación (Codex) | `clean verify` **1525/1525** local y sin `.local/`; 0 avisos de Javadoc; frontend 317/317 y build 489,80 kB. Jar real en `prod` (base y rol efímeros, variables ficticias en memoria): health, `/`, `/reservar` y estáticos con las cabeceras; Edge renderiza Angular y Material sin violaciones de CSP. |
| V-06 | Revisión independiente (Gemini, g014) | `VEREDICTO: APROBADO`. Repositorio sin cambios. |

## Observación
| # | Observación | Acción |
|---|---|---|
| O-1 | El `@ExceptionHandler(Exception.class)` genérico de `ManejadorErrores` (T-08) captura también las excepciones estándar de Spring MVC: `Accept` no aceptable (406), `Content-Type` no soportado (415) o método no permitido (405) acaban en **500** y con un log de error. Ejemplo alcanzable: `POST /api/reservas` con `Content-Type: text/plain`. | **T-46.** |
