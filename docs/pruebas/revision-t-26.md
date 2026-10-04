# Revisión técnica de T-26 — Reserva guiada en el frontend (P02)

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargos:** 030 (Codex; bloqueado por un hallazgo de diseño y por las credenciales del recorrido) y 030b (continuación tras T-44, código 0, 39 min). Clasificación: NORMAL (pantalla sobre API revisada). Revisión de Gemini no requerida: la parte crítica (DA-21) se revisó en T-44.
- **Commits:** `ca5ac35`, `b5126f4`, `f76a138`, `27904c5`, `22ef2ca`, `331acdf`, `f3fcd6e`, `52a61ea`, `509c268`, merge `c11c796`, registro `d726e93`.
- **Resultado:** **Aprobada con una observación menor.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `frontend/**` (APIs y modelos, `features/reservar/{reserva.store,reservar}`, verificadores HTTP y visual), `t-26.md`, `tareas.md` y `README.md`. Sin Java. |
| V-02 | Bloqueos resueltos | (1) Disponibilidad en reprogramación → DA-21/T-44; el store usa `duracionMin` de la respuesta. (2) Credenciales: ADMIN ficticio con el inicializador de T-10 (`BT_ADMIN_*` solo en el proceso), **sin promoción de rol por SQL** (la revisión automática de Codex la había rechazado con acierto); limpieza por id. |
| V-03 | Reglas en el cliente | El asistente no decide reglas: franjas, permisos y errores vienen del servidor; los 409/422 recargan franjas o enlazan a "Mis citas". La selección restaurada tras el login se sanea y se revalida con la disponibilidad. |
| V-04 | Verificación (Codex) | Frontend: lint, formato, build y Vitest **186/186** en Lima y en Madrid. Backend `verify` 1421/1421. HTTP por el proxy: registro, disponibilidad, creación, reprogramación (también con el servicio desactivado) y 4 rechazos CSRF. Edge a 360 y 1440 px, que detectó y permitió corregir dos defectos reales: "Sin preferencia" vacío y el paso 3 activo en el *store* pero no visible en el stepper. RNF-06: 5 interacciones principales. |

## Observación
| # | Observación | Acción |
|---|---|---|
| O-1 | El horizonte de 30 días está fijo en el frontend (calendario y saneamiento de la selección restaurada), duplicando `horizonte-dias` del servidor. Es solo experiencia de usuario: el servidor decide siempre. | Sin tarea. Si el parámetro cambiara, ajustar el frontend o exponerlo por la API. |
