# Revisión técnica de T-15 — Jornadas backend (RF-06)

- **Fecha:** 02/10/2026 · **Revisor:** Claude Code · **Encargo:** 019 (`--approve-for-me`, código 0, 33 min).
- **Commits:** `39178b6`, `73a47ef`, `6755387`, merge `d8fd944`, registro `8edd3c8`.
- **Resultado:** **Aprobada.**

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `JornadaController`, `JornadaService`, `JornadaDto`, consultas de `JornadaRepository` y `ReservaRepository`, errores con índice (`NegocioException`/`ManejadorErrores`), rutas en `SecurityConfig` y pruebas. `BarberoIT` solo cambia una ruta de su lista de denegadas (la de jornadas ya existe). Sin cambios en el frontend, la CI, APF2 ni los documentos de diseño. |
| V-02 | **Protocolo §8** | `reemplazar`: valida → `bloquearPorIds([id])` (②) → **después** `buscarFuturasQueOcupan(id, clock.instant())` → 409 `CONFLICTO_CON_RESERVAS` con los ids si alguna no cabe entera en un intervalo de su día ISO de Lima (`TiempoNegocio`) → `borrarPorBarbero` + `saveAllAndFlush` en la misma transacción. Javadoc completo con la regla, los parámetros y los errores. |
| V-03 | Permisos | GET: ADMIN o BARBERO, y el BARBERO solo el suyo (si no, 404 `NO_ENCONTRADO`); PUT: solo ADMIN. Prueba de CSRF con sesión: `put_sesionValidaSinCsrfOCsrfIncorrecto_devuelve403SinCambios`. |
| V-04 | **`mvnw.cmd clean verify` (JDK 21)** | **811 pruebas** (`JornadaIT` 48, `JornadaServiceTest` 2), 0 fallos/errores/omitidas; `jacoco:check` cumplido; **0 avisos de Javadoc**. Codex: 811/811 también en un worktree sin `.local/`; `scheduling` al 99,14 % y `JornadaService` al 100 % de líneas y ramas. |
| V-05 | Concurrencia (pruebas de Codex) | Dos `PUT` simultáneos: el último confirmado queda completo, sin mezcla; un cambio de jornada que espera el bloqueo detecta una reserva confirmada durante la espera (consulta posterior al bloqueo). |
