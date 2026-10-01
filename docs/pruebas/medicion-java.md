# Medición del porcentaje de Java

RA-02 exige ≥ 50 % de LOC Java propias sobre el código fuente total. RA-03 exige una medición reproducible.
Se excluyen dependencias, generados, comentarios, líneas vacías y documentación. Se informan las variantes con y sin pruebas, incluyendo HTML, SCSS/CSS y SQL. La interpretación académica final y los lenguajes admitidos dependen del docente (P-02).

Repetir con `node tools/medir-java.mjs --escribir`; cada ejecución añade una sección fechada. Las plantillas TS se tratan como literales opacos, sin analizar interpolaciones anidadas. No se analizan regex TS ni dollar quoting SQL.

## Medición · 2026-10-01T18:19:34.121Z · commit 919e9ca

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 105 | 294 | 0 | 399 |
| TS | 39 | 55 | 0 | 94 |
| HTML | 20 | 0 | 0 | 20 |
| SCSS | 40 | 0 | 0 | 40 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 0 | 0 | 0 | 0 |
| Total | 204 | 349 | 0 | 553 |

**Java con pruebas:** 72.15 % (Java total ÷ total).
**Java sin pruebas:** 51.47 % (Java de producto ÷ producto).
La carga se informa aparte como subconjunto de pruebas y se suma una sola vez al total.

### Exclusiones auditables

- `backend/src/main/resources/application-demo.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application-dev.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application-prod.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application-test.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application.yml`: Extensión fuera del cálculo.
- `frontend/src/app/core/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/app/features/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/app/layout/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/app/shared/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/theme-colors.scss`: Generado explícito: schematic de Angular Material.

Raíces aún ausentes: frontend/e2e, perf.

Límites léxicos: plantillas TS opacas (incluidas interpolaciones), sin interpolaciones anidadas ni regex TS; SQL sin dollar quoting.
Se mide el árbol de trabajo; el commit identifica HEAD y no certifica ausencia de cambios locales.
