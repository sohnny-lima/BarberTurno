# Revisión técnica de T-13 — Servicios backend (RF-04)

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 017 (`--approve-for-me`, código 0, 32 min).
- **Commits:** `310651a`, `98e464b`, `7499a70`, merge `5acf25d`, registro `b59011e`.
- **Resultado:** **Aprobada.**

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `catalog/**` (controlador, servicio, repositorio y 3 DTO), las rutas en `SecurityConfig`, `ServicioIT` y el ajuste de una prueba de T-10 (que esperaba 403 en `GET /api/servicios`, ahora público; las rutas no implementadas siguen denegadas). Sin cambios en el frontend, la CI, APF2 ni los documentos de diseño. |
| V-02 | Permisos | `GET /api/servicios` → `permitAll`; POST, PUT `/{id}` y PATCH `/{id}/estado` → `hasRole("ADMIN")`. `incluirInactivos` se ignora para quien no es ADMIN. |
| V-03 | Regla de CSRF (lección de T-11) | `escritura_sesionValidaSinCsrfOCsrfIncorrecto_devuelve403` y `escritura_sinSesionConCsrf_devuelve401`, parametrizadas por método. |
| V-04 | **`mvnw.cmd verify` (JDK 21)** | **679 pruebas** (61 de `ServicioIT`), 0 fallos/errores/omitidas, `jacoco:check` cumplido. Codex: 679/679 también en un worktree sin `.local/`. La diferencia de una línea en JaCoCo venía del informe incremental: con `clean verify` coincide. |
| V-05 | Reglas | Nombre único sin distinguir mayúsculas (comprobación previa y también el índice real `servicio_nombre_uk`); duración de 10–180 en múltiplos de 10; precio ≥ 0 con 2 decimales como máximo; desactivar y editar el precio no alteran las reservas existentes (`precio_ref` intacto); bloqueo de fila para serializar la edición y el cambio de estado. |

## Hallazgo aplicado
Precio máximo de 999999,99, que ya impone `numeric(8,2)`: se explicita en el contrato de arquitectura §6.3.
