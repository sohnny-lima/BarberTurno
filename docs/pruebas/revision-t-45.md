# Revisión técnica de T-45 — Recuperación ante una cookie de sesión inválida o revocada (DA-22)

- **Fecha:** 04/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 050 (Codex, código 0, 30 min). Clasificación: CRÍTICA (autenticación).
- **Origen:** hallazgo del recorrido de T-31: con una cookie revocada, incluso el login respondía 401 y el usuario quedaba bloqueado hasta que caducaba.
- **Commits:** `2fe0aa0`, `e03ab23`, `cef254a`, merge `44b2839`, registro `01d176b`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Resolver | `CookieBearerTokenResolver` devuelve `null` en `POST` `/api/auth/login`, `/registro` y `/logout` (ruta sin `contextPath`). |
| V-02 | Borrado de la cookie | Nuevo `CookieSesion` centraliza emisión y borrado (`BT_SESION`, `HttpOnly`, `SameSite=Strict`, `Path=/`, `Secure` según perfil); el *entry point* añade el borrado solo ante `OAuth2AuthenticationException` (todas las causas de token no válido, incluida la revocación por `token_version`), sin cambiar el 401 ni emitir borrado en accesos anónimos. |
| V-03 | Sin debilitar la autenticación | Las rutas protegidas siguen rechazando el token revocado; CSRF sigue exigido en login y registro (`DEFAULT_CSRF_MATCHER`); el login emite siempre un JWT nuevo (sin fijación de sesión). |
| V-04 | Frontend | Se retira el reintento de login de T-31; los 401 reales se propagan. |
| V-05 | Verificación (Codex) | `clean verify` **1476/1476** local y sin `.local/`; 0 avisos de Javadoc; `AuthIT` con 3 causas reales de revocación (cambio de contraseña, restablecimiento, desactivación), 4 formas de token inválido y 8 casos de CSRF; frontend 317/317 en Lima y Madrid. |
| V-06 | Revisión independiente (Gemini, g013) | `VEREDICTO: APROBADO`. Repositorio sin cambios. |
