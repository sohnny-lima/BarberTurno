# Revisión técnica de T-24 — Transiciones de estado (RF-12, CP-09)

- **Fecha:** 04/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 028 (Codex, `--approve-for-me`, código 0, 35 min). Clasificación: CRÍTICA.
- **Commits:** `836586b`, `24d876f`, `37ff5d7`, merge `718e86b`, registro `e97187e`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `ReservaService.transicionar`, `TransicionarReservaDto`, `POST /api/reservas/{id}/transiciones` (BARBERO o ADMIN) en controlador y `SecurityConfig`, `TransicionesIT` y un caso en `ReservaConfirmacionManualIT`. Sin carpetas sobrantes. |
| V-02 | Patrón | Bloqueo ③ sin precarga → `puedeVer` (404; barbero ajeno) → destino operativo (`PENDIENTE`/`CANCELADA` → 400) → versión (409) → `PoliticaTransiciones.evaluar` de T-09 con barbero asignado por usuario y tolerancia → 409/403/422 → `cambiarEstado` → auditoría `CONFIRMAR`/`INICIAR`/`COMPLETAR`/`NO_ASISTIO` → avisos (cliente siempre; barbero en `CONFIRMAR` si no es el actor). `Clock` inyectado. El CLIENTE recibe 403 en la capa de seguridad. |
| V-03 | Verificación (Codex) | `clean verify` **1325/1325** (TransicionesIT 59: flujo completo, ventanas de 15 min y de inicio, terminales, versión, 10 carreras de "completar" → 200 + 409 con una auditoría, rollback, corrida R102), también sin `.local/`; JaCoCo cumplido (reservas 100 %); 0 avisos de Javadoc. |
| V-04 | Revisión independiente (Gemini, g010) | `VEREDICTO: APROBADO`. Repositorio sin cambios. |
| V-05 | RA-02 | 60,81 % de Java sin pruebas (sin T-18); 75,09 % con pruebas. |
