# Revisión técnica de T-10 — Autenticación backend

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 014 (`--approve-for-me`, código 0, 25 min).
- **Commits:** `d62defa`, `5bc796e`, `d5c3161`, merge `54760de`, registro `b389221`.
- **Resultado:** **Aprobada.** Incluye la corrección C-1 de T-08 y la 401/403 con `ProblemDetail`.

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `auth/**`, `common/security/**`, `ManejadorErrores`, repositorios, YAML, pom, README, evidencia y `tareas.md`. Sin cambios en el frontend, la CI, APF2 ni los documentos de diseño. |
| V-02 | **C-1 de T-08** | Driver `postgresql` con alcance `compile`; `ManejadorErrores` ya no usa reflexión. |
| V-03 | `SecurityConfig` | STATELESS; `csrf.spa()` + `CookieCsrfTokenRepository` (`XSRF-TOKEN` legible, `SameSite=Strict`, `Secure` según el perfil); *entry point* 401 y `AccessDeniedHandler` 403 con `ProblemDetail`; resource server con `CookieBearerTokenResolver` y `UsuarioJwtConverter`; `PasswordTemporalFilter`; rutas públicas solo para health, Swagger (si está habilitado) y `/api/auth/*`; `PUT /api/auth/password` autenticado; **`denyAll` en el resto**. |
| V-04 | JWT | HS256 (`NimbusJwtEncoder`/`Decoder`), 8 h con el `Clock`. Secreto Base64 de ≥ 32 bytes; en dev/test, si falta, se genera un efímero con `SecureRandom` y un WARN sin el valor. |
| V-05 | Cookie y *resolver* | `BT_SESION` `HttpOnly`, `SameSite=Strict`, `Path=/` y `Secure` configurable; logout con `Max-Age=0`. El *resolver* solo lee la cookie (ignora `Authorization` y parámetros) y rechaza dos cookies `BT_SESION` ambiguas. |
| V-06 | Revocación | El conversor valida `sub` y `tv` y consulta en la base que el usuario esté activo y que la versión coincida; si no → 401. |
| V-07 | Login | Bloquea la fila del usuario (serializa los intentos); BCrypt ficticio si la cuenta no existe; mensajes genéricos; `@Transactional(noRollbackFor = NegocioException)` para que `intentos_fallidos` y `bloqueado_hasta` persistan aunque responda 401; 5 fallos → 15 min; un bloqueo vencido reinicia la serie. Logs solo con ids. |
| V-08 | Logs | `org.hibernate.orm.jdbc.error: OFF`, aceptado: PostgreSQL incluye valores personales (p. ej., el correo) en los errores de clave duplicada, y `ManejadorErrores` ya registra la traza saneada (RNF-12, RNF-14). En el log de `verify` no aparece ningún token (`BT_SESION=ey…`, `Bearer ey…`). |
| V-09 | **`mvnw.cmd verify` (JDK 21)** | **571 pruebas**, 0 fallos/errores/omitidas: `AuthIT` 36, `AuthCookieSecureIT` 1, `JwtConfigTest` 9, `SecurityConfigIT` 22 y `UsuarioActualSecurityTest` 3. `jacoco:check` cumplido. Codex: también 571/571 en un worktree sin `.local/`. |
| V-10 | Pruebas de T-02 | Ahora usan el intercambio real de cookie y cabecera CSRF en lugar de la utilidad de pruebas, que alteraba el repositorio de tokens (más estrictas, no más débiles). |

## Hallazgo resuelto
La matriz §7.2 limitaba el login y el registro a usuarios sin sesión y el logout a usuarios con sesión. Se actualiza en este commit: login y registro abiertos a cualquiera, y logout público e idempotente, como se implementó.

## Adenda (01/10/2026, revisión de T-11)
**Defecto de seguridad no detectado en esta revisión:** el resource server de Spring Security exime de CSRF a las peticiones en las que encuentra un token *bearer*. Como `CookieBearerTokenResolver` lee el JWT de la cookie, **toda escritura autenticada** se aceptaba sin la cabecera `X-XSRF-TOKEN`. Las pruebas de T-10 solo comprobaban CSRF en peticiones sin sesión (login y registro). Lo detectó Codex en T-11 y lo corrigió: el `CsrfFilter` vuelve a usar `DEFAULT_CSRF_MATCHER`. Las pruebas de regresión cubren la petición con sesión y sin CSRF o con CSRF incorrecto (ver [revision-t-11.md](revision-t-11.md)). **Lección incorporada al proceso:** cada endpoint de escritura nuevo debe probarse con sesión y sin CSRF (regla añadida a la cabecera común de los encargos).
