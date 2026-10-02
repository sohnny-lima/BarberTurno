# Revisión técnica de T-14 — Barberos backend (RF-05)

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 018b (reintento del 018, que se cortó por el límite de uso), `--approve-for-me`, código 0, 32 min.
- **Commits:** `ad9da1e`, `521235c`, `49884d4`, merge `0d37f16`, registro `9b664bc`.
- **Resultado:** **Aprobada.** Primera tarea Java con la regla de Javadoc (DA-20) aplicada desde el principio.

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `scheduling` (controlador, servicio, repositorio, entidad y 5 DTO), `ReservaRepository` (conteo de reservas futuras), las rutas en `SecurityConfig` y pruebas. `ServicioIT` solo cambia una ruta de su lista de denegadas (`GET /api/barberos` pasó a ser pública). Sin cambios en el frontend, la CI, APF2 ni los documentos de diseño. |
| V-02 | Permisos | `GET /api/barberos` público; POST, PUT `/{id}` y PATCH `/{id}/estado` → `hasRole("ADMIN")`; correo y teléfono solo para ADMIN; `denyAll` y CSRF intactos. |
| V-03 | **Bloqueos** | Crear y reactivar toman `pg_advisory_xact_lock(141414)` (cupo RN-19) y después ① usuario → ② barbero (`bloquearPorIds`), respetando el orden global. El bloqueo consultivo no puede formar ciclos con las reservas, que nunca lo esperan: se documenta como ⓪ en arquitectura §8 en este commit. |
| V-04 | Convenciones | Sin `Instant.now()` ni `LocalDateTime.now()` ("futuro" = `inicio > Clock`). |
| V-05 | **`mvnw.cmd clean verify` (JDK 21)** | **760 pruebas** (81 de `BarberoIT`), 0 fallos/errores/omitidas; `jacoco:check` cumplido; **0 avisos de Javadoc** (DA-20). Codex: 760/760 también en un worktree sin `.local/`; cobertura de `reservations` al 100 % y de `scheduling` al 98,83 %. |
| V-06 | Pruebas destacadas | Ciclo de acceso del barbero (contraseña temporal → cambio → acceso; desactivar → cookie 401 y login fallido; reactivar); 11.º activo → 422; **tres carreras por el último cupo** (dos altas, dos reactivaciones y una mezcla): solo una gana; vincular un ADMIN con `barberoId` en la sesión; `reservasFuturasVigentes` excluye canceladas y pasadas; escrituras con sesión y sin CSRF o con CSRF incorrecto → 403 (`escritura_sesionValidaSinCsrfOCsrfIncorrecto_devuelve403`). |
