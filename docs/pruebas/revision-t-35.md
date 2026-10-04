# Revisión técnica de T-35 — Prueba de carga (RNF-01, CP-12)

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargos:** 039 (Codex, cortado por el límite de uso a las 04:34 sin commits; trabajo conservado en un *stash*) y 039b (continuación, código 0, 28 min). Clasificación: NORMAL (proyecto de pruebas aislado, sin cambios en el backend). Revisión de Gemini no requerida.
- **Commits:** `a782d90`, `4fcebac`, `e464b20`, cierre `472b268`, merge `7591e7a`, registro `4bd49ea`.
- **Resultado:** **Aprobada con observaciones** (O-1 → T-43; O-2 resuelta en la revisión).

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `perf/` (Maven independiente con Gatling 3.16.0 y plugin 4.21.12, comprobados como últimas estables), scripts de ejecución y resumen, `docs/pruebas/carga/`, `t-35.md`, `tareas.md` y `README.md`. **Backend sin cambios.** No forma parte del build ni de la CI. |
| V-02 | Datos | Generador reproducible (semilla 35) sobre una base **dedicada y efímera** (`DROP DATABASE` al terminar; nunca `barberturno_test`, desarrollo ni demo), BCrypt de coste 12, bloqueos del diseño, ~41,7 % de ocupación con reservas asistidas (exentas de RN-20) en un grupo de clientes y otro grupo libre para el autoservicio (decisión del arquitecto). Tres pruebas de protección. |
| V-03 | Resultado RNF-01 | 50 usuarios concurrentes durante 300 s: **14 380 consultas de disponibilidad, p95 = 11 ms (meta ≤ 2000 ms), 0 errores**; 19 768 peticiones, 29 errores = **0,147 % (< 1 %)**; 300 reservas creadas y 856 rechazos esperados por RN-20. Aserciones originales sin rebajar. |
| V-04 | Correcciones durante el ensayo | Dependencia de ejecución de BCrypt (`commons-logging`), `%` sin escapar en el resumen y la simulación, que reutilizaba un XSRF anterior (ahora lo lee justo antes del POST, como la SPA). Ninguna afecta al producto. |
| V-05 | Limpieza | Sin procesos Java/Maven/Gatling del encargo, puertos 8080/4200 libres, base de carga eliminada; la suite y la carga nunca se ejecutaron a la vez contra PostgreSQL. |

## Observaciones
| # | Observación | Acción |
|---|---|---|
| O-1 | **29 POST de reserva (2,4 % de los intentos) con un código distinto de los dos aceptados, sin registrar cuál.** Pueden ser rechazos de negocio legítimos (por ejemplo, `CLIENTE_CON_RESERVA_SOLAPADA` cuando el cliente elige una franja que se cruza con otra suya) o un 503 por `lock_timeout` bajo contención, que sería relevante para RNF-05. | **T-43:** conservar el código y el estado HTTP inesperados en la simulación, repetir la carga y clasificar los 29. No bloquea RNF-01. |
| O-2 | La tarea figuraba como *Should* y RNF-01 es *Must* en los requisitos. | Prioridad de T-35 alineada a **M** en `tareas.md` (precedencia de requisitos). |
