# Revisión técnica de T-11 — Perfil y cambio de contraseña backend

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 015 (`--approve-for-me`, código 0, 28 min).
- **Commits:** `5f054a0`, `dc37326`, `e4a29d6`, merge `85e5b72`, registro `840d001`.
- **Resultado:** **Aprobada.** Además corrige un defecto de seguridad de T-10 (CSRF).

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `users` (perfil), `auth` (contraseña), `common` (errores de campo y `SecurityConfig`) y pruebas. Sin cambios en el frontend, la CI, APF2 ni los documentos de diseño. |
| V-02 | **Defecto de seguridad corregido** | El resource server excluía CSRF cuando encontraba el JWT (leído de la cookie): las escrituras autenticadas se aceptaban sin CSRF. Corrección en `SecurityConfig`: `withObjectPostProcessor` → `CsrfFilter.setRequireCsrfProtectionMatcher(DEFAULT_CSRF_MATCHER)`. Es **general**: protege todo método no seguro, también los endpoints futuros. |
| V-03 | Regresión | `PerfilIT#escrituras_csrfAusenteOIncorrecto_rechazanAunqueHaySesion` (parametrizada: `/api/perfil` y `/api/auth/password`): con sesión válida, sin CSRF o con CSRF incorrecto → 403 `PROHIBIDO` y el usuario no cambia. Se suman las pruebas sin sesión de `AuthIT` y `SecurityConfigIT`. |
| V-04 | Funcionalidad | Perfil: GET y PUT (nombre y teléfono; teléfono obligatorio para CLIENTE); un `correo` en el JSON se ignora y el correo no cambia. Contraseña: actual incorrecta, débil o igual a la actual → 400 por campo; si es correcta → 204, `token_version++`, `debe_cambiar_password=false` y cookie nueva; CP-17 para los tres roles. Las escrituras usan el bloqueo de usuario para no pisar datos de acceso concurrentes. |
| V-05 | **`mvnw.cmd verify` (JDK 21)** | **618 pruebas**, 0 fallos/errores/omitidas (`PerfilIT` 46, `AuthIT` 36), `jacoco:check` cumplido. Codex: 618/618 también en un worktree sin `.local/`. |

## Consecuencias para el proceso
- Adenda en [revision-t-10.md](revision-t-10.md), que reconoce el defecto no detectado.
- Nueva regla en la cabecera común de los encargos: **cada endpoint de escritura nuevo se prueba con sesión y sin CSRF (y con CSRF incorrecto) → 403**.
