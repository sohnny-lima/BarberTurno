# Revisión técnica de T-05 — Medición del % de Java

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 008 (`--approve-for-me`, código 0, 20 min).
- **Commits:** `919e9ca`, `55cefdf`, merge `ae86e72`, registro `7c48b97`.
- **Resultado:** **Aprobada.**

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | Solo `tools/`, `docs/pruebas/`, `docs/tareas.md` y README; sin cambios en `backend/`, `frontend/`, `.github/`, APF2, requisitos, arquitectura, AGENTS ni CLAUDE. Sin worktrees residuales. |
| V-02 | Fixtures | `node tools/medir-java.mjs --verificar-fixtures` → 14 archivos, 7 exclusiones, código 0. |
| V-03 | Pruebas de CLI | `node --test tools/verificar-medicion.test.mjs` → 8/8 con Node 20.19.0 (Codex también con 24.21.0). |
| V-04 | **Contraste independiente con cloc 1.96** (Perl 5.38) sobre las mismas raíces y exclusiones | Producto: Java 105, TS 39, HTML 20, SCSS 40 = **204**; pruebas: Java 294, TS 55 = **349**. **Idénticos** al medidor. |
| V-05 | Resultado actual (commit `7c48b97`) | **72,15 % de Java con pruebas** (399/553) y **51,47 % sin pruebas** (105/204). Exclusiones listadas y auditables (`theme-colors.scss` generado, YAML y `.gitkeep`). |
| V-06 | Analizador léxico | Respeta los marcadores dentro de cadenas (casos `/**` y `//` cubiertos por los fixtures). Las simplificaciones están documentadas: plantillas TS opacas, sin regex TS, sin *dollar quoting* SQL. |

## Observaciones
| ID | Observación | Acción |
|---|---|---|
| O-1 | La sección de T-05 en `tareas.md` cita `perf/src`, pero la arquitectura §12 y el medidor usan `perf/`. | Prevalece la arquitectura. El criterio de la tarea terminada no se reescribe; queda anotado aquí. |
| O-2 | **Riesgo RA-02:** sin pruebas, Java está en 51,47 %, muy cerca del 50 %. Hoy el frontend es solo un esqueleto, pero crecerá con las pantallas (T-12, T-17, T-18 y T-26…T-31). | Volver a medir con `--escribir` al cerrar cada fase (identidad, catálogo, reservas y entrega). Si la cifra sin pruebas baja de 55 %, el arquitecto revisará qué lógica puede pasar al servidor (DA-15). Se añade como paso de las revisiones. |
| O-3 | P-02 (método de conteo aceptado por el docente) sigue pendiente. | Se informan ambas cifras, como prevé RA-02. |
