# Revisión técnica de T-08 — Infraestructura común (errores, tiempo y paginación)

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 012 (`--approve-for-me`, código 0, 22 min).
- **Commits:** `31f1d68`, `c9cad56`, `fe68f54`, merge `4bef042`, registro `40011de`.
- **Resultado:** **Aprobada con observaciones.** O-1 y O-2 se corrigen dentro de T-10, que ya modifica la seguridad y el backend.

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | Solo `common/**` (`ErrorCodigo`, `NegocioException`, `ManejadorErrores`, `TiempoNegocio`, `PaginaDto` y `UsuarioActual`), pruebas, evidencia y `tareas.md`. Sin cambios en el pom, `SecurityConfig`, frontend, CI, APF2 ni documentos de diseño. |
| V-02 | Catálogo | `ErrorCodigo` contiene los 29 códigos de arquitectura §6.2, incluidos `CONFLICTO` y `ERROR_INTERNO`. |
| V-03 | **`mvnw.cmd verify` (JDK 21)** | **170 pruebas**, 0 fallos/errores/omitidas: 62 en `ManejadorErroresTest`, 4 en `ManejadorErroresIT` con `23P01` y `23505` reales de PostgreSQL, 9 en `NegocioExceptionTest`, 5 en `TiempoNegocioTest` y 9 en `PaginaDtoTest`. |
| V-04 | Sin filtraciones | El método común `comprobar(...)`, usado por todos los casos, exige `codigo` y estado y que el cuerpo no contenga "Exception", "org.", "SQL" ni nombres de restricción. Otra prueba captura el log y comprueba que la traza no incluye secretos, JWT, cookies ni correos. `type=about:blank` explícito. |
| V-05 | Traducción por SQLState y restricción | `23P01` → `reserva_sin_solape_barbero` / `reserva_sin_solape_cliente`; `23505` → `usuario_correo_uk` / `servicio_nombre_uk`; `55P03` / `40P01` → 503. Nunca por el texto del mensaje. |

## Observaciones
| ID | Observación | Acción |
|---|---|---|
| O-1 | `ManejadorErrores` obtiene `getServerErrorMessage()` y `getConstraint()` por **reflexión** para no cambiar el alcance `runtime` del driver, ya que el pom estaba fuera del alcance del encargo. Funciona y está probado, pero es frágil y el compilador no lo comprueba. | **Corrección C-1 incluida en T-10:** driver con alcance `compile` y `instanceof PSQLException` tipado. |
| O-2 | La 401 y la 403 que emite Spring Security salen **sin cuerpo** `ProblemDetail` (el hallazgo de Codex). | Ya previsto en T-10: *entry point* y `AccessDeniedHandler` con `ProblemDetail`. |
