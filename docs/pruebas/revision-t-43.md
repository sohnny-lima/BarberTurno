# Revisión técnica de T-43 — Carga: registrar y clasificar los rechazos de reserva inesperados

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 048 (Codex, código 0, 24 min). Clasificación: NORMAL (solo `perf/`).
- **Commits:** `79aef82`, `1af9640`, `de68dd8`, merge `e3f11db`, registro `8d33b18`.
- **Resultado:** **Aprobada.** Cierra la observación O-1 de la revisión de T-35.

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `perf/` (`RechazosReserva`, simulación y scripts de resumen, con 3 pruebas nuevas; 6 en total), `docs/pruebas/carga/`, `t-43.md` y `tareas.md`. Backend sin cambios; respuestas aceptadas y aserciones sin cambios. |
| V-02 | Clasificación | Misma semilla y generador que T-35 (200 clientes, 10 barberos, 1560 reservas, 41,67 %). Los **23 rechazos inesperados son todos 409 `CLIENTE_CON_RESERVA_SOLAPADA`**: rechazos de negocio legítimos (RN-04) provocados por la simulación, que reutiliza 100 clientes y elige franjas sin mirar sus citas. **Ningún 500 ni 503**; los logs del backend no tienen `ERROR`, `55P03`, `40P01`, `lock_timeout` ni `deadlock`. Sin hallazgo de concurrencia (RNF-05). |
| V-03 | RNF-01 | Disponibilidad p95 **11 ms**, 0 errores; global **0,116 %** de errores; todas las aserciones aprobadas. |
| V-04 | Verificación y limpieza | Backend `verify` 1407/1407 antes de la carga; ninguna otra conexión de aplicación a PostgreSQL durante la carga; base efímera eliminada; sin procesos propios. |

La propuesta de Codex (que una variante futura consulte `/api/reservas/mias` para no elegir franjas cruzadas) se registra como posible mejora del modelo de carga, sin tarea: no afecta a RNF-01 ni al producto.
