# Revisión técnica de T-25 — API de auditoría y avisos (RF-16, RF-17, CP-18)

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 029 (Codex, `--approve-for-me`, código 0, 42 min). Clasificación: NORMAL (lectura de datos propios con la autorización ya aprobada en T-21; sin bloqueos ni reglas nuevas). Revisión de Gemini no requerida.
- **Commits:** `808bede`, `8b22a16`, `d0fda86`, merge `f90cc73`, registro `2eb97de`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `AuditoriaDto`, `GET /api/reservas/{id}/auditoria` en `ReservaConsultaService`/`ReservaController`, `NotificacionController`, consultas en `NotificacionRepository`, lectura en `NotificacionService`, 4 reglas `authenticated()` en `SecurityConfig`, `AuditoriaAvisosIT` y la medición RA-02 escrita en `medicion-java.md`. Sin migraciones. |
| V-02 | Auditoría | `buscarDetalle` → `puedeVer` (404 a ajenos e inexistentes) → CLIENTE propietario 403 (§7.2) → historial cronológico. |
| V-03 | Avisos | Toda consulta y escritura filtra por `actor.id()`: listado paginado (≤ 100, más reciente primero), `count` JPQL sin cargar entidades, `findByIdAndUsuarioId` (aviso ajeno → 404) y `update` masivo limitado al usuario. Las POST con CSRF (probado sin token y con token incorrecto). |
| V-04 | Verificación (Codex) | `verify` **1369/1369** local y `clean verify` sin `.local/`; JaCoCo cumplido (reservas 100 %, auditoría y avisos 100 %); 0 avisos de Javadoc. |
| V-05 | RA-02 (cierre del backend de reservas) | Medición escrita: **61,76 %** de Java sin pruebas y **75,75 %** con pruebas, sin T-18. |
