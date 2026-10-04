# Revisión técnica de T-30 — Reportes en el frontend (P08)

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 034 (Codex, código 0, 22 min). Clasificación: NORMAL. Revisión de Gemini no requerida.
- **Commits:** hasta `b714242`, cierre `e106cd4`, merge `48bbf65`, registro `919c1ee`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `features/admin/reportes`, API y modelos del resumen, utilidades de periodo en `core/tiempo`/`shared`, verificadores; `t-30.md`, `tareas.md`, `README.md`. Sin Java ni bibliotecas de gráficos nuevas. Ruta *lazy* de 14,35 kB; *bundle* inicial 489,80 kB. |
| V-02 | Reglas en el cliente | Los agregados vienen del servidor (T-29); el cliente solo los presenta y muestra la comprobación pedida "Los estados suman el total". La validación del rango (inclusivo, ≤ 366 días) es de experiencia de usuario; el servidor también la impone. Resumen e historial se piden en paralelo con los mismos filtros, y la paginación conserva los filtros aplicados. |
| V-03 | Verificación (Codex) | Frontend: lint, formato, Vitest **301/301** en Lima y Madrid, build sin avisos. Backend `verify` 1421/1421. HTTP por el proxy: resumen, estados, servicios, profesionales e historial coinciden (3 reservas) en 4 combinaciones de filtros; Edge 1440/360 px con fechas de Lima desde Madrid; limpieza a cero. |
