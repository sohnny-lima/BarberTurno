# Revisión técnica de T-06 — Esquema Flyway V1 y pruebas de restricciones

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 010 (`--approve-for-me`, código 0, 14 min).
- **Commits:** `d844a82`, `b2daada`, merge `00d7f37`, registro `4ec2930`.
- **Resultado:** **Aprobada.**

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | Solo `V1__esquema_inicial.sql`, `EsquemaIT`, la evidencia y `tareas.md`. Sin cambios en el código Java de producción, el pom, el frontend, la CI, APF2 ni los documentos de diseño. Sin worktrees residuales. |
| V-02 | **V1 = DDL de la arquitectura §5.1** | Comparación normalizada (sin comentarios ni espacios, en minúsculas): 4672 = 4672 caracteres, **idénticos**. |
| V-03 | Estado real en `barberturno_test` (rol de aplicación) | `flyway_schema_history` con la versión 1 y `success = t`; `btree_gist` 1.8 instalada sin superusuario; restricciones `x`: `reserva_sin_solape_barbero` y `reserva_sin_solape_cliente`; 14 `CHECK` y una `UNIQUE`; **0 filas** en las tablas de negocio tras las pruebas. |
| V-04 | **`mvnw.cmd verify` (JDK 21)** | **64 pruebas** (19 de `EsquemaIT`), 0 fallos/errores/omitidas, BUILD SUCCESS. |
| V-05 | Calidad de `EsquemaIT` | Cubre los 8 casos del plan y los 6 adicionales del encargo (cancelada contigua, `NO_ASISTIO` que sí ocupa franja, rol inválido, cliente incompleto, nombre de servicio sin distinguir mayúsculas e id ≥ 100). Comprueba el SQLState y el nombre de la restricción: **0** aserciones sobre `getMessage()` o `hasMessage`. Rollback por caso. |
| V-06 | Simulación de la CI | Codex ejecutó `verify` en un worktree sin `.local/` con las variables `BT_DB_*`: 64/64. |

## Hallazgo resuelto
Algunos `CHECK` en línea del DDL tienen el nombre automático de PostgreSQL (`<tabla>_<columna>_check`). Ese nombre es determinista y se acepta: la arquitectura §5.1 queda actualizada en este commit. Las restricciones que el manejador de errores traduce tienen nombre explícito.
