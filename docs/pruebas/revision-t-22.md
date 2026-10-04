# Revisión técnica de T-22 — Cancelación (RF-10, CP-06)

- **Fecha:** 04/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 026 (Codex, `--approve-for-me`, **cortado por el límite de uso** a las 23:39 del 03/10 tras los commits de código y pruebas). Clasificación: CRÍTICA.
- **Commits de Codex:** `7537763` (implementación), `b58e26f` (pruebas). **Cierre por Claude Code** (sin cambios de código): verificación, evidencia, estado y merge.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `ReservaService.cancelar`, `CancelarReservaDto`, `POST /api/reservas/{id}/cancelacion` (CLIENTE o ADMIN) en controlador y `SecurityConfig`, `CancelacionIT` y ajustes en `ReservaConfirmacionManualIT` y `ReservaCrearIT` (la guardia de la ruta ahora implementada pasa a 404 ante reserva inexistente). |
| V-02 | Protocolo y orden | Bloqueo ③ sin precarga → `puedeVer` (404 uniforme antes de revelar versión o estado) → rol (BARBERO asignado 403) → versión (409) → estado (409) → ventana y motivo con `ReglasTemporales.puedeModificar` → `excepcional` → `saveAndFlush` → auditoría y avisos `MANDATORY`. `Clock` inyectado. La comparación de versión es correcta (`Integer` del DTO frente a `int` de la entidad). |
| V-03 | Verificación (Claude Code) | `clean verify` **1150/1150** (CancelacionIT 53), código 0, JaCoCo y Javadoc incluidos, 512 s; **worktree sin `.local/`** 1150/1150, 439 s. Base de pruebas vacía después. El worktree temporal de Codex se retiró antes de `clean`. |
| V-04 | Concurrencia y atomicidad (Codex) | 10 carreras de doble cancelación → 200 + 409 con una sola auditoría; espera real por ③ comprobada en `pg_stat_activity`; fallo forzado de auditoría o del segundo aviso → nada persiste. |
| V-05 | Revisión independiente (Gemini, g008) | `VEREDICTO: APROBADO`: orden tras ③, ventana y `excepcional`, motivo ADMIN también a más de 2 h, atomicidad y destinatarios RN-15, cobertura sin huecos relevantes. Repositorio sin cambios. |
| V-06 | RA-02 | 59,78 % de Java sin pruebas (sin T-18); 73,36 % con pruebas. |
