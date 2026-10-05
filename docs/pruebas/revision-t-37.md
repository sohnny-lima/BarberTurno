# Revisión técnica de T-37 — Evidencias de aceptación y cierre documental

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargos:** 040 (cortado al arrancar por el límite de uso, sin cambios) y 040b (Codex, código 0, 24 min). Clasificación: SIMPLE/NORMAL (solo documentación y evidencias).
- **Commits:** `868fefe`, `251dddb`, merge `fa820e5`, registro `48f959b`.
- **Resultado:** **Aprobada.** El revisor corrige los dos hallazgos editoriales de Codex.

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | Solo `docs/pruebas/**` (`aceptacion.md`, `t-37.md`, `usabilidad-plantilla.md`, `t-37-resultados.json`, `t-37-jacoco.csv`, informe E2E, `medicion-java.md`), `tareas.md` y `README.md` (recorrido de sustentación de 7–9 min). Sin código. |
| V-02 | Ejecución en el HEAD | Backend `verify` **1535/1535** (cobertura 1778/1797 líneas; reservas 356/356; agenda 397/399), frontend 317/317 y build 489,88 kB, E2E **28/28** (Chromium y Firefox, 1440/360 px) con 44 análisis axe sin violaciones; los **58 métodos** citados en la matriz existen y pasan. |
| V-03 | Casos y matriz | CP-01…CP-19 con entradas, esperado, observado y evidencia; 18 con evidencia técnica y **CP-11 pendiente del responsable** (P-07, plantilla preparada). Matriz con los 21 RF y los 14 RNF; pendientes justificados: RNF-02 y HTTPS real (P-03), RNF-06 humano (P-07), compatibilidad comercial de RNF-07. RNF-09 con T-36. |
| V-04 | Riesgos residuales | 403 intermitente en Firefox (T-34 O-1), CI sin ejecutar en remoto (P-04), PostgreSQL escuchando en todas las interfaces (decisión del responsable), poco espacio en C:, *bundle* cerca del presupuesto, horizonte fijo en el frontend (T-26 O-1), base de demo conservada. |
| V-05 | RA-02 (informativa) | 40,25 % de Java sin pruebas y 55,02 % con pruebas. |

## Hallazgos editoriales corregidos por el revisor
1. La tabla abreviada de requisitos §9 omitía RNF-02, RNF-13 y RNF-14: añadidas sus filas.
2. `tools/medir-java.mjs` seguía escribiendo en archivos nuevos "RA-02 exige ≥ 50 %" y "depende del docente (P-02)": texto actualizado a métrica informativa y P-02 resuelta, con su prueba ajustada (`node --test tools/verificar-medicion.test.mjs`: 8/8).
