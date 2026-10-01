# Revisión técnica de T-12 — Shell, autenticación y perfil en el frontend (P01)

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 016 (`--approve-for-me`, código 0, 30 min).
- **Commits:** `37d11c1`, `bebbf5a`, `98bc791`, `54de155`, merge `810ea59`, registro `ea3629e`.
- **Resultado:** **Aprobada.** Cierra la fase 2 (identidad).

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `frontend/**` (`core/api`, `core/auth`, `core/modelos`, `core/tiempo`, `layout/shell`, `features/auth`, `features/perfil` y la página provisional), un script de comprobación HTTP, README, la medición y la evidencia. **Backend sin cambios.** |
| V-02 | Seguridad del cliente | En el código de producción no se usan `localStorage`, `sessionStorage` ni `document.cookie`; no hay URLs absolutas (todo `/api/...` relativo, por el proxy o el mismo origen); la sesión depende solo de la cookie `HttpOnly` y de `GET /api/auth/sesion`. |
| V-03 | Interceptor | Probado: 401 protegida → limpia la sesión y va a `/ingresar`; 401 de la sesión inicial y del login sin redirigir; 403 `CAMBIO_PASSWORD_REQUERIDO` → `/cambiar-password`; 400 con `errores[]` por campo. |
| V-04 | **Cadena con Node 24.21.0** | `npm ci` (0 vulnerabilidades, sin avisos), lint, `format:check`, **58/58 pruebas**, build (inicial 410,91 kB; 104 kB transferidos). **Pruebas también en verde con `TZ=Europe/Madrid`** (RNF-13). |
| V-05 | Extremo a extremo HTTP (Codex) | Por `:4200`: sesión 401 + XSRF, registro 201, perfil 200/200, logout 204 y perfil 401. Escrituras con sesión sin CSRF o con CSRF incorrecto → 403 (regla de T-11). Solo se eliminó el usuario ficticio creado. |
| V-06 | **Medición RA-02 (cierre de la fase de identidad)** | **61,37 % de Java sin pruebas** y **71,03 % con pruebas**, por encima del umbral de alerta del 55 %. |

## Observaciones
| ID | Observación | Acción |
|---|---|---|
| O-1 | `frontend/.gitattributes` fuerza LF en `*.ps1`, frente a la regla raíz (CRLF). Viene de una ambigüedad de la cabecera de los encargos ("LF en todo"). | Inocuo (PowerShell 7 admite LF); se mantiene. Se aclara la cabecera: se respeta el `.gitattributes` raíz y no se añaden reglas que lo contradigan. |
| O-2 | La página provisional de `/reservar` está protegida por rol, como pedía el encargo, aunque la arquitectura §4.2 la define pública para explorar. | Transitorio: T-26 la hace pública. |
