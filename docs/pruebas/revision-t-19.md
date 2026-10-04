# Revisión técnica de T-19 — Disponibilidad backend (RF-07, RF-21)

- **Fecha:** 03/10/2026 · **Revisores:** Claude Code (final) y Gemini 3.8 Flash (independiente, solo lectura, diff-first) · **Encargo:** 023 (Codex, `--approve-for-me`, código 0, 41 min). Clasificación: CRÍTICA.
- **Commits:** `2b8a526`, `aa65400`, `38ba7e6`, merge `b5db0c5`, registro `b53f847`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `DisponibilidadService`, `DisponibilidadController`, 2 DTO, consultas `buscarDia` en los repositorios de jornada, bloqueo y reserva, `buscarClienteId`, `findByActivoTrueOrderByIdAsc` y una ruta `GET /api/disponibilidad` `permitAll` exacta en `SecurityConfig` (el resto sigue en `denyAll`). Solo backend, `t-19.md` y `tareas.md`. |
| V-02 | Misma lógica para listar y validar | Ambos caminos pasan por `evaluar` → `CalculadoraFranjas.esFranjaValida` (sin ocupaciones para distinguir `FUERA_DE_HORARIO` y con ellas para `FRANJA_NO_DISPONIBLE`). Prueba de coincidencia de toda candidata de la rejilla con su código. |
| V-03 | Tiempo | `Clock` inyectado; días de Lima con `TiempoNegocio.inicioDelDia`; horizonte inclusivo por fecha (`hoy … hoy + 30`); respuesta con `-05:00` (la prueba deserializa sin ajustar a UTC y comprueba el desfase). El desplazamiento de 5 h visto durante el desarrollo era de la lectura JSON de la prueba, no de la persistencia (el valor físico `09:00:00` se comprueba en la misma prueba). |
| V-04 | Ocupación | Reservas `estado <> CANCELADA`, igual que las restricciones `EXCLUDE` del DDL y `EstadoReserva.ocupaFranja()`; bloqueos por cruce con el día. |
| V-05 | Autorización de la exclusión | Antes de cualquier cálculo; sin sesión, BARBERO, cliente ajeno o inexistente → 404 uniforme "Recurso no encontrado." |
| V-06 | Pruebas y rendimiento (Codex) | `clean verify` **958/958** (62 nuevas), JaCoCo cumplido (scheduling 99,49 %), 0 avisos de Javadoc, también en un worktree sin `.local/`. 5 SELECT por consulta (sin N+1). p95 indicativo 24 ms (10 barberos, ~300 reservas). |
| V-07 | Revisión independiente (Gemini, g005) | `VEREDICTO: APROBADO`: bordes de medianoche y fin de jornada, horizonte, 404 uniforme, coherencia de estados con el DDL y preparación para T-20. Repositorio sin cambios comprobado por el lanzador. |
| V-08 | RA-02 | 56,51 % de Java sin pruebas en `main` (sin T-18) y 69,10 % con pruebas. |

## Notas para T-20
`validarFranja` no toma bloqueos ni comprueba el servicio: T-20 debe adquirir ① y ② antes de llamarla, autorizar la exclusión al reprogramar (T-23) y comprobar el servicio activo.
