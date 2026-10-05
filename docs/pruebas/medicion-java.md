# Medición del porcentaje de Java

RA-02 es una métrica informativa, sin umbral, desde la reclasificación del responsable del 04/10/2026. RA-03 exige una medición reproducible. Los registros anteriores conservan sus resultados históricos.
Se excluyen dependencias, generados, comentarios, líneas vacías y documentación. Se informan las variantes con y sin pruebas, incluyendo HTML, SCSS/CSS y SQL. P-02 está resuelta: el backend en Java es el requisito académico y el porcentaje no condiciona el diseño ni el cierre.

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


## Medición · 2026-10-01T21:41:43.397Z · commit bebbf5a

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 2045 | 2857 | 0 | 4902 |
| TS | 778 | 712 | 0 | 1490 |
| HTML | 230 | 0 | 0 | 230 |
| SCSS | 170 | 0 | 0 | 170 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 109 | 0 | 0 | 109 |
| Total | 3332 | 3569 | 0 | 6901 |

**Java con pruebas:** 71.03 % (Java total ÷ total).
**Java sin pruebas:** 61.37 % (Java de producto ÷ producto).
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

## Medición · 2026-10-04T01:47:44.717Z · commit f3e53a4

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 2767 | 4529 | 0 | 7296 |
| TS | 1832 | 1651 | 0 | 3483 |
| HTML | 737 | 0 | 0 | 737 |
| SCSS | 281 | 0 | 0 | 281 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 109 | 0 | 0 | 109 |
| Total | 5726 | 6180 | 0 | 11906 |

**Java con pruebas:** 61.28 % (Java total ÷ total).
**Java sin pruebas:** 48.32 % (Java de producto ÷ producto).
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

## Medición · 2026-10-04T01:52:51.440Z · commit 37d29c6

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 2767 | 4529 | 0 | 7296 |
| TS | 1400 | 1232 | 0 | 2632 |
| HTML | 559 | 0 | 0 | 559 |
| SCSS | 226 | 0 | 0 | 226 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 109 | 0 | 0 | 109 |
| Total | 5061 | 5761 | 0 | 10822 |

**Java con pruebas:** 67.42 % (Java total ÷ total).
**Java sin pruebas:** 54.67 % (Java de producto ÷ producto).
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

## Medición · 2026-10-04T07:47:20.956Z · commit 8b22a16

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 3705 | 7308 | 0 | 11013 |
| TS | 1400 | 1232 | 0 | 2632 |
| HTML | 559 | 0 | 0 | 559 |
| SCSS | 226 | 0 | 0 | 226 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 109 | 0 | 0 | 109 |
| Total | 5999 | 8540 | 0 | 14539 |

**Java con pruebas:** 75.75 % (Java total ÷ total).
**Java sin pruebas:** 61.76 % (Java de producto ÷ producto).
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

## Medición · 2026-10-04T18:37:02.128Z · commit 5ac8d01

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 4015 | 8432 | 345 | 12447 |
| TS | 3446 | 3358 | 0 | 6804 |
| HTML | 1378 | 0 | 0 | 1378 |
| SCSS | 434 | 0 | 0 | 434 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 109 | 0 | 0 | 109 |
| Total | 9382 | 11790 | 345 | 21172 |

**Java con pruebas:** 58.79 % (Java total ÷ total).
**Java sin pruebas:** 42.79 % (Java de producto ÷ producto).
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
- `perf/ejecutar-carga.ps1`: Extensión fuera del cálculo.
- `perf/pom.xml`: Extensión fuera del cálculo.
- `perf/resumir-carga.ps1`: Extensión fuera del cálculo.
- `perf/target/`: Dependencias, salida, documentación o datos locales.

Raíces aún ausentes: frontend/e2e.

Límites léxicos: plantillas TS opacas (incluidas interpolaciones), sin interpolaciones anidadas ni regex TS; SQL sin dollar quoting.
Se mide el árbol de trabajo; el commit identifica HEAD y no certifica ausencia de cambios locales.

## Medición · 2026-10-04T18:38:05.969Z · commit a19d1a1

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 4015 | 8432 | 345 | 12447 |
| TS | 3446 | 3358 | 0 | 6804 |
| HTML | 1378 | 0 | 0 | 1378 |
| SCSS | 434 | 0 | 0 | 434 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 109 | 0 | 0 | 109 |
| Total | 9382 | 11790 | 345 | 21172 |

**Java con pruebas:** 58.79 % (Java total ÷ total).
**Java sin pruebas:** 42.79 % (Java de producto ÷ producto).
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
- `perf/ejecutar-carga.ps1`: Extensión fuera del cálculo.
- `perf/pom.xml`: Extensión fuera del cálculo.
- `perf/resumir-carga.ps1`: Extensión fuera del cálculo.
- `perf/target/`: Dependencias, salida, documentación o datos locales.

Raíces aún ausentes: frontend/e2e.

Límites léxicos: plantillas TS opacas (incluidas interpolaciones), sin interpolaciones anidadas ni regex TS; SQL sin dollar quoting.
Se mide el árbol de trabajo; el commit identifica HEAD y no certifica ausencia de cambios locales.

## Medición · 2026-10-05T03:37:15.285Z · commit 94dbe99

Node: v24.21.0. Raíces: `backend/src`, `frontend/src`, `frontend/e2e`, `perf`.
Extensiones contadas: .java, .ts, .html, .scss, .css, .sql.
LOC físicas sin líneas vacías ni comentarios; las cadenas conservan sus marcadores de comentario.
Fuera del cálculo: JSON/YAML de configuración, package-lock.json, mvnw*, Markdown, docs, dependencias y generados.

| Lenguaje | Producto | Pruebas | Carga (incluida en pruebas) | Total |
|---|---:|---:|---:|---:|
| Java | 4284 | 9040 | 345 | 13324 |
| TS | 3978 | 4534 | 0 | 8512 |
| HTML | 1704 | 1 | 0 | 1705 |
| SCSS | 568 | 0 | 0 | 568 |
| CSS | 0 | 0 | 0 | 0 |
| SQL | 109 | 0 | 0 | 109 |
| Total | 10643 | 13575 | 345 | 24218 |

**Java con pruebas:** 55.02 % (Java total ÷ total).
**Java sin pruebas:** 40.25 % (Java de producto ÷ producto).
La carga se informa aparte como subconjunto de pruebas y se suma una sola vez al total.

### Exclusiones auditables

- `backend/src/main/resources/application-demo.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application-dev.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application-prod.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application-test.yml`: Extensión fuera del cálculo.
- `backend/src/main/resources/application.yml`: Extensión fuera del cálculo.
- `backend/src/test/resources/static/main-prueba.js`: Extensión fuera del cálculo.
- `backend/src/test/scripts/verificar-spa-prod.mjs`: Extensión fuera del cálculo.
- `frontend/src/app/core/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/app/features/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/app/layout/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/app/shared/.gitkeep`: Extensión fuera del cálculo.
- `frontend/src/theme-colors.scss`: Generado explícito: schematic de Angular Material.
- `frontend/e2e/ejecutar.ps1`: Extensión fuera del cálculo.
- `perf/ejecutar-carga.ps1`: Extensión fuera del cálculo.
- `perf/pom.xml`: Extensión fuera del cálculo.
- `perf/resumir-carga.ps1`: Extensión fuera del cálculo.
- `perf/target/`: Dependencias, salida, documentación o datos locales.

Raíces aún ausentes: ninguna.

Límites léxicos: plantillas TS opacas (incluidas interpolaciones), sin interpolaciones anidadas ni regex TS; SQL sin dollar quoting.
Se mide el árbol de trabajo; el commit identifica HEAD y no certifica ausencia de cambios locales.
