# Revisión técnica de T-46 — Errores estándar de Spring MVC con su estado HTTP

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 052 (Codex, código 0, 28 min). Clasificación: NORMAL (pequeña, solo `ManejadorErrores`).
- **Commits:** `f622687`, `30cad4e`, merge `5076e13`, registro `c1552bc`.
- **Resultado:** **Aprobada.** Cierra O-1 de T-33.

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Cambio | Nuevo manejador para `HttpMediaTypeNotSupportedException`, `HttpRequestMethodNotSupportedException` y `HttpMediaTypeNotAcceptableException`: si es `ErrorResponse` 4xx, 405/415 con `ProblemDetail` y `codigo = VALIDACION`, 406 **sin cuerpo**; `WARN` sin traza; si no, el 500 interno de siempre. Nota en el catálogo de errores (arquitectura §6). |
| V-02 | Pruebas (Codex) | 7 casos unitarios y 3 IT: `POST /api/reservas` con `text/plain` → 415; health con `Accept: text/html` → 406 vacío; 405 cubierto en una prueba directa del manejador porque la seguridad responde 403 antes de MVC (comprobado). `clean verify` **1535/1535** local y sin `.local/`; JaCoCo cumplido; 0 avisos de Javadoc. |
