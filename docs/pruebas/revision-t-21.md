# Revisión técnica de T-21 — Consulta de reservas y autorización (RF-11, RF-13, CP-02)

- **Fecha:** 03/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 025 (Codex, `--approve-for-me`, código 0, 48 min). Clasificación: CRÍTICA (autorización).
- **Commits:** `1479eb7`, `ec10777`, `3a4205c`, merge `d39ee6b`, registro `8275bd7`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `ReservaAutorizacion`, `ReservaConsultaService`, 3 GET en `ReservaController`, `findAll(Specification)` y `buscarDetalle` con `@EntityGraph`, 3 reglas en `SecurityConfig`, `ReservaConsultaIT` y un ajuste en `ReservaCrearIT`. Solo backend, `t-21.md` y `tareas.md`; sin carpetas sobrantes. |
| V-02 | Autorización | `puedeVer`: ADMIN, CLIENTE propietario o BARBERO asignado por `barbero.usuario.id` (no por un dato del solicitante). Detalle ajeno o inexistente → mismo 404 y mismo mensaje. `/mias` toma el cliente de la sesión; en `/api/reservas` el BARBERO tiene forzado su perfil (otro `barberoId`, `clienteId` o sin perfil → 403). Reglas de `SecurityConfig` en orden correcto (`/mias` y `/api/reservas` antes de `/{id}`). |
| V-03 | Tiempo y paginación | Días de Lima `[inicioDelDia(desde), finDelDia(hasta))`, con `finDelDia` = medianoche siguiente; rango obligatorio ≤ 366 días en la agenda; `pagina ≥ 0`, `1 ≤ tamano ≤ 100`; orden estable `inicio, id`. |
| V-04 | Prueba heredada | En `ReservaCrearIT` solo `GET /api/reservas/100` pasa de 403 a 404 (la ruta ya existe y CP-02 exige 404); los otros cinco casos siguen esperando 403 `PROHIBIDO`. No se debilita nada. |
| V-05 | Verificación (Codex) | `clean verify` **1096/1096**, también sin `.local/`; JaCoCo cumplido (reservas 100 % de líneas; servicio y autorización con todas las ramas); 0 avisos de Javadoc; 4 SELECT por página de 20 y 3 por detalle; contexto nuevo con `@DirtiesContext(AFTER_CLASS)`. |
| V-06 | Revisión independiente (Gemini, g007) | `VEREDICTO: APROBADO`: 404 uniforme sin filtrar existencia, filtros aislados, precedencia de reglas, sin N+1, ajuste de la prueba heredada correcto. Repositorio sin cambios. |
| V-07 | RA-02 | 59,34 % de Java sin pruebas en `main` (sin T-18); 72,41 % con pruebas. |
