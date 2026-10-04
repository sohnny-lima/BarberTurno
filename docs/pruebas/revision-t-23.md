# Revisión técnica de T-23 — Reprogramación (RF-09)

- **Fecha:** 04/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 027 (Codex, `--approve-for-me`, código 0, 32 min). Clasificación: CRÍTICA.
- **Commits:** `de24a36`, `c0578d4`, `8559899`, merge `b43e264`, registro `6506895`.
- **Resultado:** **Aprobada.** El hallazgo documental de Codex (RN-06) se resuelve aclarando la redacción.

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `ReservaService.reprogramar`, `ReservaLectura` (proyección escalar), `leerParaReprogramar`, `ReservaAutorizacion.puedeVer(actor, clienteId, asignadoId)`, `ReprogramarReservaDto`, la ruta `POST /api/reservas/{id}/reprogramacion` (CLIENTE o ADMIN) y `ReprogramacionIT`. Solo backend, `t-23.md` y `tareas.md`. |
| V-02 | Protocolo §8 | Lectura escalar previa (sin entidades en el contexto JPA) → autorización (el cliente de una reserva es inmutable) → ① cliente → ② barbero actual y nuevo en `TreeSet` (únicos, ascendentes) → ③ reserva → **revalidación** de versión, estado, ventana y motivo con `clock.instant()` posterior. Un cambio concurrente entre la lectura y los bloqueos incrementa la versión y produce 409. |
| V-03 | Reglas | Solo `CONFIRMADA` (RN-09); ventana y motivo como la cancelación (RN-07/08); barbero de destino activo; `fin` con `duracion_ref` (RN-13); `validarFranja` y solape del cliente excluyendo la propia reserva; auditoría con estado anterior `CONFIRMADA` (único posible) y datos previos capturados antes de mutar; avisos al cliente y a los barberos anterior y nuevo, deduplicados y sin autoaviso. |
| V-04 | Verificación (Codex) | `verify` **1265/1265** (ReprogramacionIT 115, 60 carreras < 5 s, 4 esperas reales), también `clean verify` sin `.local/`; JaCoCo cumplido (reservas 100 %); 0 avisos de Javadoc. |
| V-05 | Revisión independiente (Gemini, g009) | `VEREDICTO: APROBADO`: orden global sin interbloqueos frente a creación, cancelación y bloqueos de agenda; ventana previa protegida por la revalidación; autorización previa segura; trazabilidad íntegra. Repositorio sin cambios. |
| V-06 | RA-02 | 60,42 % de Java sin pruebas (sin T-18); 74,38 % con pruebas. |

## Hallazgo documental resuelto
RN-06 decía que solo servicios y barberos activos sirven "como destino de una reprogramación", y el encargo permitía conservar un servicio desactivado. Como en una reprogramación el servicio no cambia (RN-09) y sus referencias se conservan (RN-13), se aclara RN-06: el **barbero de destino** debe estar activo; el servicio se conserva aunque se haya desactivado después. Así, desactivar un servicio no impide mover las reservas que ya lo tienen.
