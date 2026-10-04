# Revisión técnica de T-27 — Mis citas y avisos en el frontend (P03)

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 031 (Codex, código 0, 28 min). Clasificación: NORMAL. Revisión de Gemini no requerida.
- **Commits:** hasta `5378bdf`, merge `44759cb`, registro `8a9bdee`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `features/mis-citas` (pestañas, filtros, paginación, cancelación), `shared/reserva-tarjeta` y `shared/estado-reserva-chip`, servicio de avisos y contador en el shell, verificadores; `t-27.md`, `tareas.md`, `README.md`. Sin Java. |
| V-02 | Reglas en el cliente | Los botones Reprogramar/Cancelar dependen solo de `permisos` del DTO (DA-15). La única condición temporal (`inicio > ahora` con estado `PENDIENTE`/`CONFIRMADA` y sin permisos) solo decide si mostrar el texto explicativo del prototipo. Cancelación con `version`; 409 recarga, 422 muestra el `detail`. |
| V-03 | Contador de avisos | Sondeo cada 60 s que se detiene sin sesión y con la pestaña oculta; cada refresco cancela la consulta anterior para no mostrar un conteo obsoleto. |
| V-04 | Verificación (Codex) | Frontend: lint, formato, Vitest **223/223** en Lima y Madrid, build 489,28 kB (presupuesto 500 kB). Backend `verify` en verde. HTTP con backend real: tarjeta, cancelación con versión, contador `1 → 0 → 1 → 0`, 8 rechazos CSRF; ADMIN del recorrido con el inicializador de T-10; limpieza a cero. Edge 1440/360 px sin excepciones (se corrigió un desplazamiento interno de las pestañas). |

## Observación
El *bundle* inicial está en 489,28 kB con un presupuesto de 500 kB. Las pantallas restantes son rutas *lazy*, pero conviene vigilarlo en T-28, T-30 y T-31 (si se supera, se revisa qué entra en el *bundle* inicial antes de subir el presupuesto).
