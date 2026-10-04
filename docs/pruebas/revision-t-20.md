# Revisión técnica de T-20 — Crear reserva con control de concurrencia (RF-08, RNF-05)

- **Fecha:** 03/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 024 (Codex, `--approve-for-me`, código 0, 38 min). Clasificación: CRÍTICA.
- **Commits:** `053a1ee`, `7939cf3`, `82d2b21`, merge `895bde7`, registro `5bf0de7`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `ReservaService`, `ReservaController`, `CrearReservaDto`, `ReservaDto`, `AuditoriaService`, `NotificacionService`, una ruta `POST /api/reservas` solo CLIENTE en `SecurityConfig` y pruebas. Solo backend, `t-20.md` y `tareas.md`. |
| V-02 | Protocolo §8.1 | Existencia con `existsById` (sin precargar entidades); ① `usuarios.bloquearPorId(cliente)` → ② `barberos.bloquearPorIds([barbero])`; **después**: lectura del servicio, estados activos, `fin` con `ReglasTemporales`, `validarFranja` (participa en la transacción de escritura), solape del cliente, límite de activas con `clock.instant()` tomado tras los bloqueos; `saveAndFlush`; auditoría y avisos `MANDATORY` en la misma transacción. |
| V-03 | Autorización y datos | Solo CLIENTE para sí mismo (`clienteId` → 403 hasta T-31); BARBERO 403 en la capa de seguridad; teléfono del cliente solo para el personal; `permisos` calculados en el servidor con `ReglasTemporales` y `PoliticaTransiciones` (DA-15). |
| V-04 | Concurrencia (CP-03) | 10 hilos × **20 repeticiones** → exactamente `OK` + 9 × `FRANJA_NO_DISPONIBLE`; solape parcial 10:00/10:10 → 1 éxito; reserva frente a bloqueo con espera real (`pg_blocking_pids`) → uno gana; HTTP concurrente → 1 × 201 + 4 × 409; `INSERT` JDBC → `23P01`; aserción de ausencia de `40P01`/`55P03`. 11 s. |
| V-05 | Atomicidad | Fallo forzado del aviso → no queda ni la reserva ni la auditoría. |
| V-06 | Verificación (Codex) | `clean verify` **1053/1053**, también sin `.local/`; JaCoCo cumplido (reservas 100 % de líneas, scheduling 99,49 %); 0 avisos de Javadoc. |
| V-07 | Revisión independiente (Gemini, g006) | `VEREDICTO: APROBADO`: orden de bloqueos, participación `REQUIRED` de `validarFranja`, atomicidad `MANDATORY`, DA-15 y exposición mínima de datos, cobertura de carreras. Repositorio sin cambios. |
| V-08 | RA-02 | 58,15 % de Java sin pruebas en `main` (sin T-18); 71,36 % con pruebas. |

## Observación
| # | Observación | Acción |
|---|---|---|
| O-1 | Cada configuración de contexto de prueba distinta retiene un pool; con las nuevas clases PostgreSQL agotó las conexiones (192 errores). Codex lo resolvió con `@DirtiesContext(AFTER_CLASS)` en las tres clases nuevas. | Regla añadida a la cabecera común de los encargos: reutilizar las bases de prueba y cerrar el contexto si una clase necesita configuración propia. Vigilar en la CI (servicio `postgres:18`, 100 conexiones por defecto). |
