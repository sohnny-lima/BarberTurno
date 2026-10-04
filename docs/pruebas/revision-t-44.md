# Revisión técnica de T-44 — Disponibilidad en modo reprogramación (DA-21)

- **Fecha:** 04/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 049 (Codex, código 0, 31 min). Clasificación: CRÍTICA (coherencia entre lo mostrado y lo aceptado al reprogramar).
- **Origen:** hallazgo de Codex en el encargo 030 (T-26), que el arquitecto convirtió en DA-21 y T-44. Defecto de diseño no previsto en T-19 ni en T-23.
- **Commits:** `2fb1905`, `9e009de`, `333f7b9`, merge `2045004`, registro `5b7b1d7`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `ReservaReferenciaDisponibilidad` (proyección `long servicioId`, `short duracionRefMin`), `leerReferenciaDisponibilidad` y unas 10 líneas de `DisponibilidadService.consultarFranjas`; pruebas en `DisponibilidadIT`. Sin cambios en `validarFranja` ni en `reprogramar`. |
| V-02 | DA-21 | Tras la autorización existente (404 uniforme): lectura sin bloqueo de la referencia; `servicioId` distinto → 400; la comprobación de servicio activo solo se omite en modo reprogramación; `duracion_ref` en candidatas, fin y `duracionMin`; el barbero sigue debiendo estar activo. Comparación de identidades sobre `long` primitivo (correcta). |
| V-03 | Pruebas (Codex) | 14 casos nuevos: servicio que pasa de 30 a 40 min (reprogramación 30, normal 40), servicio desactivado (200 en reprogramación, 422 sin exclusión), 400/404, y **coincidencia: 72 reprogramaciones por HTTP aceptadas** para las franjas listadas. `clean verify` **1421/1421** local y sin `.local/`; JaCoCo (scheduling 99,50 %); 0 avisos de Javadoc. |
| V-04 | Revisión independiente (Gemini, g011) | `VEREDICTO: APROBADO`. Repositorio sin cambios. |
