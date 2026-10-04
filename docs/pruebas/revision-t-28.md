# Revisión técnica de T-28 — Agenda en el frontend (P04)

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 032 (Codex, código 0, 28 min). Clasificación: NORMAL. Revisión de Gemini no requerida.
- **Commits:** hasta `a19d1a1`, cierre `7f14528`, merge `d18c0bb`, registro `9970f2f`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `features/agenda` (agenda, diálogos de transición, reprogramación y auditoría), APIs y modelos, verificadores; `t-28.md`, `medicion-java.md`, `tareas.md`, `README.md`. Sin Java. Ruta *lazy*: el *bundle* inicial queda en 489,69 kB (presupuesto 500 kB). |
| V-02 | Reglas en el cliente | Ninguna: las acciones salen de `permisos.transiciones`; el motivo del ADMIN se valida en el diálogo solo como experiencia de usuario (el servidor manda); reprogramación con franjas del servidor; 409 recarga. La única operación con fechas del componente es ordenar filas. Semanas en días de Lima con `core/tiempo`, probadas en cruce de mes y año y con `TZ=Europe/Madrid`. |
| V-03 | Verificación (Codex) | Frontend: lint, formato, Vitest **266/266** en Lima y Madrid, build sin avisos. Backend `verify` 1421/1421. HTTP `dev` con cuentas ficticias creadas por API: el BARBERO ve su día, inicia y completa (su propia reserva de ensayo se movió a un minuto antes de la hora real y se eliminó después; documentado), el ADMIN consulta las 3 auditorías; CSRF 403, CLIENTE 403, versión antigua 409. Edge sin excepciones. |
| V-04 | Medición RA-02 (informativa) | 42,79 % de Java sin pruebas y 58,79 % con pruebas. Sin umbral ni acción (RA-02 reclasificada el 04/10/2026). |
