# Revisión técnica de T-16 — Bloqueos backend (RF-06 y RF-20)

- **Fecha:** 02/10/2026 · **Revisor:** Claude Code · **Encargo:** 020 (`--approve-for-me`, código 0, 45 min).
- **Commits:** `570f498` (corrección de `LocalTime`), `3203660`, `c21dbdc`, `130449b`, merge `bea4080`, registro `c9b5bbe`.
- **Resultado:** **Aprobada.** Además corrige un defecto de persistencia heredado de T-07/T-15.

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `BloqueoController`, `BloqueoService`, `BloqueoRepository`, 3 DTO, rutas en `SecurityConfig`, `Jornada` (solo el mapeo de las horas) y pruebas. Sin cambios en el frontend, la CI, APF2 ni los documentos de diseño. |
| V-02 | **Defecto corregido (heredado de T-07/T-15)** | Con `hibernate.jdbc.time_zone=UTC`, el `LocalTime` de `Jornada` se guardaba desplazado +5 h. Corrección: `@JdbcTypeCode(SqlTypes.LOCAL_TIME)` en `horaInicio` y `horaFin` (enlace JDBC directo). **Pruebas del valor físico** en `BloqueoIT`: `select hora_inicio::text` = `08:00:00` y `hora_fin::text` = `19:00:00`, y un caso parametrizado con madrugada y cierre tardío. Es la única entidad con `LocalTime`. Base de desarrollo comprobada por el revisor: **0 jornadas**, nada que migrar. Adendas en las revisiones de T-07 y T-15; arquitectura §5.2 actualizada. |
| V-03 | Protocolo §8 | Alta, lote y borrado toman ② (el lote con todos los barberos en orden de id) y consultan las reservas **después** del bloqueo, comparando con el reloj también después del bloqueo; el lote es atómico (si uno falla, no se crea ninguno). |
| V-04 | Validaciones | inicio < fin; inicio ≥ ahora (422 `INICIO_EN_PASADO`); motivo obligatorio; rango ≤ 366 días; ids duplicados en el lote → 400; inexistentes → 404. Se permiten bloqueos solapados entre sí y en barberos inactivos (decisión documentada). |
| V-05 | **`mvnw.cmd clean verify` (JDK 21)** | **896 pruebas** (`BloqueoIT` 83, `BloqueoServiceTest` 2), 0 fallos/errores/omitidas; `jacoco:check` cumplido; **0 avisos de Javadoc**. Codex: 896/896 también en un worktree sin `.local/`; `BloqueoService` al 100 % de líneas y ramas. CSRF con sesión: `escritura_sesionValidaSinCsrfOCsrfIncorrecto_rechaza403`. |
| V-06 | Pendiente previsto | La carrera reserva frente a bloqueo entre las dos API se probará en T-20, como dice el plan. |
