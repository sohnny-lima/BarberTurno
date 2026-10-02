# Revisión técnica de T-40 — Javadoc completo, generación HTML reproducible y muestra para la exposición

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code
- **Encargos:** 042 (51 min, código 0: **Bloqueada** con razón por 9 constructores públicos implícitos) y 042b (20 min, código 0: corrección autorizada por el arquitecto).
- **Commits:** `d03544c`, `bc58ad9`, `5792d46`, `ce725a5`, `ce6cc99` (042); `3951ea4`, `fe7e348`, `c0a5564` (042b); merge `0da66c5`; registro `3170962`.
- **Resultado:** **Aprobada.** El comportamiento de la aplicación se conserva (comprobado). Quedan una observación de estilo (→ **T-41**) y un ajuste documental que se corrige en este commit.

## Auditoría de partida (arquitecto, sobre `ab4ca0d`)
59 archivos Java, 259 elementos públicos o protegidos: **29 sin Javadoc** y **129 triviales** ("Consulta id."…). `javadoc:javadoc -Ddoclint=all` generaba el HTML (plugin 3.12.0 del parent, no declarado) con **100 avisos**. Se registró T-40 + DA-20 (commit `69484ff`).

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | 58 Java de `src/main` (comentarios y 9 constructores), `pom.xml`, `tools/verificar-enlaces-html.mjs` con *fixtures* y prueba, `entregables/apf2-final/javadoc/**`, evidencia, `tareas.md` y README. **Sin cambios** en las pruebas, el frontend, la CI, `docs/apf2/` ni los documentos de diseño. |
| V-02 | **Comportamiento conservado** (comparación propia, independiente de la de Codex) | Analizador Java que respeta cadenas y bloques de texto: de los 58 archivos cambiados, 47 son idénticos sin comentarios, 2 difieren solo en espacios y 9 añaden **exactamente** `public <Clase>(){}` (`BarberTurnoApplication`, `JwtConfig`, `ConfiguracionProduccion`, `ConfiguracionReservas`, `ManejadorErrores`, `CookieBearerTokenResolver`, `SecurityConfig`, `UsuarioActualSecurity` y `ClockConfig`). Un constructor público vacío equivale al implícito; Codex lo confirmó con `javap -c -p` (9/9 idénticas, y 66/66 clases idénticas en el encargo 042). |
| V-03 | **`mvnw.cmd clean verify` (JDK 21)** | **679 pruebas**, 0 fallos/errores/omitidas; `jacoco:check` cumplido; **`javadoc-no-fork (javadoc-verificacion)` en `verify` con 0 avisos**; BUILD SUCCESS. El único "warning:" del log es de la JVM ("Sharing is only supported for boot loader classes…", por el agente de Mockito), no de Javadoc. |
| V-04 | Generación HTML | `mvnw.cmd javadoc:javadoc` → código 0, 0 avisos y `backend/target/reports/apidocs/index.html`. Configuración: UTF-8, `doclint=all`, `failOnWarnings=true`, `detectJavaApiLink=false` y sin enlaces externos (funciona sin red y en la CI). |
| V-05 | Enlaces | `tools/verificar-enlaces-html.mjs`: API completa **185 HTML, 4948 enlaces relativos, 0 rotos**; muestra **18 HTML, 341 enlaces, 0 rotos**; con el *fixture* roto termina con código 1 (detecta los fallos). |
| V-06 | Muestra para la exposición | `entregables/apf2-final/javadoc/`: `fuentes/` con **copia idéntica** (`cmp`) de `ReglasTemporales`, `CalculadoraFranjas` y `PoliticaTransiciones`; `html/apidocs/`; `LEEME.md` con la justificación y el comando (`mvnw.cmd -o -P javadoc-muestra javadoc:javadoc`). Unos 498 KB. `docs/apf2/` intacto. |
| V-07 | Calidad del Javadoc | Auditoría repetida: 268 declaraciones, **0 triviales**, 0 sin Javadoc (el único aviso de la heurística es un falso positivo por una anotación partida en dos líneas; `doclint` confirma 0). Muestras: los *getters* explican el dato (RN-01, `timestamptz`, bloqueo optimista y `VERSION_DESACTUALIZADA`); los repositorios explican los bloqueos ①②③ y el centinela; los controladores indican ruta, rol, CSRF y códigos de error. |

## Observaciones
| ID | Observación | Acción |
|---|---|---|
| O-1 | **Error de instrucciones del arquitecto:** el criterio "solo comentarios" no preveía los constructores implícitos (bloqueo justificado del 042). En el 042b, además, el texto mezclaba un alcance general (`{@return}`) con un límite de nueve clases, y sugería poner una frase antes de `{@return}`, cosa que JDK 21 no admite. Codex resolvió ambos conflictos de forma prudente y lo documentó. | Lección: en las tareas de documentación, prever los constructores implícitos y no contradecir el alcance con los límites. |
| O-2 | Quedan **69** elementos (sobre todo *getters*) con la descripción y el `@return` duplicados. No son triviales, pero duplican texto. | Nueva tarea **T-41** (prioridad C). |
| O-3 | El README y `LEEME.md` seguían diciendo "T-40 bloqueada" y que `verify` devolvía código 1, ya falso tras el 042b. | **Corregido en este commit de revisión** (ajuste documental). |

## Regla incorporada al proceso (DA-20)
Cada tarea Java debe dejar Javadoc útil en lo público o protegido que añada o modifique, y `verify` falla ante cualquier aviso de `doclint`. La regla consta en la Definición de Hecho, en AGENTS.md §6, en CLAUDE.md (lista de revisión) y en la cabecera común de los encargos.
