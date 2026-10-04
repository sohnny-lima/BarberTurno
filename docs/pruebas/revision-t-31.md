# Revisión técnica de T-31 — Reserva asistida y gestión de usuarios (RF-18, RF-19, CP-17, CP-19)

- **Fecha:** 04/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargos:** 035 (Codex, cortado por el límite de uso a las 14:41 tras el commit de backend; frontend conservado en un *stash*) y 035b (continuación, código 0, 29 min). Clasificación: CRÍTICA.
- **Commits:** `4e24320` (backend), `a90882d`, `171bd96`, `810b19a`, merge `d074c11`, registro `bb47132`.
- **Resultado:** **Aprobada.** Genera DA-23 (clave de bloqueo del último ADMIN); el hallazgo de la cookie revocada se trata en DA-22/T-45.

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Reserva asistida | ADMIN con `clienteId` obligatorio (400 si falta); CLIENTE con `clienteId` → 403. ① sobre el **cliente** destino, después rol CLIENTE (404) y activo (422); mismo protocolo de T-20 sin el límite RN-20; `creada_por` y actor de auditoría = ADMIN; `CONFIRMADA` aunque haya confirmación manual (RN-21, con prueba de regresión). |
| V-02 | Restablecer contraseña | `SecureRandom`, 12 caracteres (al menos una letra y un dígito, mezcla Fisher-Yates), `debe_cambiar_password`, revocación por `token_version`, respuesta `no-store`; la sesión anterior → 401 y el login con la temporal obliga al cambio (CP-17). |
| V-03 | Estado de usuarios | `pg_advisory_xact_lock(313131)` antes de releer al actor (sigue siendo ADMIN activo) y de bloquear ①; no se puede desactivar a sí mismo; nunca 0 ADMIN activos; 10 carreras de desactivación cruzada → siempre una 200 y ≥ 1 ADMIN activo. Sin ciclos con 141414 → **DA-23**. |
| V-04 | Búsqueda | `LIKE` con escape de `!`, `%` y `_`; `UsuarioAdminDto` sin hash, `token_version` ni datos de bloqueo; paginación ≤ 100. |
| V-05 | Frontend | `/admin/usuarios`, selector de cliente (autocompletado) en `/reservar` para ADMIN, `PasswordTemporalDialogo` reutilizado (cierre solo con "Cerrar"); texto del modo asistido corregido ("se gestiona desde Agenda"). Recuperación local del login: un único reintento ante `401 NO_AUTENTICADO` (inocua; la causa raíz va en T-45). |
| V-06 | Verificación (Codex) | Backend `clean verify` **1452/1452** local y sin `.local/`, 0 avisos de Javadoc, reservas 100 %. Frontend 318/318 en Lima y Madrid, build 489,98 kB. HTTP + Edge 1440/360 px (stepper vertical incluido), bases a cero, sin procesos. |
| V-07 | Revisión independiente (Gemini, g012) | `VEREDICTO: APROBADO`. Repositorio sin cambios. |
