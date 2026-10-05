# Revisión técnica de T-36 — Respaldo y restauración (RNF-09)

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 053 (Codex, código 0, 21 min). Clasificación: NORMAL (scripts de operación; el ensayo solo crea y borra su propia base). Revisión de Gemini no requerida.
- **Commits:** hasta `bb7f4a7`, cierre `68d5b5a`, merge `b2e274e`, registro `a782028`.
- **Resultado:** **Aprobada.**

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | `tools/{respaldo.sh,restaurar.sh,respaldo-comun.sh,verificar-respaldo.sh,ensayar-recuperacion.ps1}`, `.gitignore` (`*.dump`), `docs/pruebas/{recuperacion,t-36}.md`, sección de copias de `docs/despliegue.md`, `tareas.md`, `README.md`. Sin cambios en `backend/` ni en el frontend. |
| V-02 | Respaldo | `pg_dump -Fc` a un temporal; resumen de filas exactas antes y después (si cambia, falla y pide pausar escrituras); solo entonces publica `<base>-AAAAMMDD-HHMMSS.dump` y su `.resumen.tsv`. Si `pg_dump` falla, no publica nada ni aplica la retención. |
| V-03 | Retención (destructiva, revisada línea a línea) | `find` de profundidad 1, solo archivos regulares, patrón estricto de nombre de la base, más de 20 160 min (14 días); borra la copia y su resumen si no es un enlace. Prueba con archivos ficticios: conserva los recientes, los de otra base, los de otro patrón y los de subdirectorios. |
| V-04 | Restauración | Solo en una base **nueva** (si existe, falla; nunca sobrescribe); comparación de tablas y filas exactas con el origen o con el resumen guardado (recuperación sin origen); detecta una tabla distinta aunque el total coincida. |
| V-05 | Credenciales | Solo por variables estándar (`PGPASSWORD`/`PGPASSFILE`, y `PGRESTORE_*` para el rol dueño), nunca en argumentos ni en los documentos. |
| V-06 | Ensayo real (criterio de aceptación) | `barberturno_demo_t32` → `barberturno_restore`: 9 tablas y 56 filas idénticas; respaldo + restauración + comparación en 3,4–4,4 s (meta ≤ 4 h); segunda modalidad sin origen también correcta. Base temporal y respaldos de prueba eliminados; otras bases intactas. Scripts 33/33; backend `verify` 1535/1535. |

## Ajuste del revisor
Se corrigió en `docs/despliegue.md` la referencia antigua a "PostgreSQL 18.0 / T-38" (fuera del alcance que el encargo permitía a Codex): el entorno local es 18.6 desde el 04/10/2026.
