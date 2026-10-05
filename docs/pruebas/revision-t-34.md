# Revisión técnica de T-34 — E2E, responsive, compatibilidad y accesibilidad

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 038 (Codex, código 0, 57 min). Clasificación: NORMAL (pruebas y correcciones de accesibilidad en el frontend). Revisión de Gemini no requerida.
- **Commits:** `e057625` (foco visible), `623e312`, `e62938c`, merge `ce24f27`, registro `c73f536`.
- **Resultado:** **Aprobada con una observación.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `frontend/e2e` (Playwright, fixtures, `npm run e2e`), una corrección de foco en los campos Material, `docs/pruebas/{t-34.md,e2e/}` (608 kB), `tareas.md`, `README.md`. Backend, APF2 y CI intactos. Navegadores (Chromium 153, Firefox 155) descargados en `frontend/tmp/` (ignorado) porque **C: no tenía espacio**. |
| V-02 | Entorno | Jar con `-Pcon-frontend` en `dev,demo` con `reloj-fijo` 28/09 09:00 y `BT_DEMO_PASSWORD` en el entorno del proceso; base `barberturno_test` (el rol no puede crear `barberturno_e2e`), vaciada antes y después; demo reiniciada entre proyectos; `timezoneId: Europe/Madrid`. Escenario del barbero con una reserva propia a las 09:10 creada por la API (alternativa autorizada). |
| V-03 | Resultado | **28/28** casos en Chromium y Firefox a 1440 y 360 px, sin omisiones ni reintentos: los 6 escenarios (más CP-01 y CP-04), horas de Lima desde Madrid, conflicto de dos contextos (201 + 409 con recarga), redirecciones de seguridad. **44 análisis axe sin ninguna violación** (tampoco `moderate`/`minor`); sin desplazamiento horizontal a 360 px; reserva en 3 pasos y 5,6–8,3 s (RNF-06). |
| V-04 | Defectos corregidos | Material quitaba el contorno de foco del campo de contraseña (prueba de teclado) → estilo explícito. Ajustes de la suite: espera de la carga de la demo tras el health; conteo de pasos del stepper en vertical. |
| V-05 | Regresión | Frontend 317/317, lint, tipos, formato, build 489,88 kB; backend `verify` 1535/1535 y cobertura sin descenso. Base de pruebas a cero, sin procesos. |
| V-06 | RA-02 (informativa) | 40,25 % de Java sin pruebas y 55,02 % con pruebas. |

## Observación
| # | Observación | Acción |
|---|---|---|
| O-1 | En una ejecución, la cancelación en Firefox 1440 devolvió **403 intermitente**; no se reprodujo en las repeticiones ni en la ejecución final (28/28). Con `csrf.spa()` el token solo se regenera al cambiar la autenticación, así que lo más probable es una carrera de la prueba, pero no está demostrado. | Sin tarea. La suite conserva un diagnóstico booleano (presencia de sesión y CSRF, coincidencia cookie/cabecera) sin reintentos. Se anota como riesgo residual en el cierre (T-37); si reaparece, se abre una tarea con el diagnóstico. |
