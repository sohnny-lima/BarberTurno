# Revisión técnica de T-39 — Ajustes menores del esqueleto backend

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 009 (`--approve-for-me`, código 0, 18 min).
- **Commits:** `84fd47e`, `5a6a374`, merge `7cfbfec`, registro `00c2329`.
- **Resultado:** **Aprobada.** Cierra las observaciones O-3…O-7 de la revisión de T-02. La CI ya puede ejecutarse por primera vez en cuanto exista el remoto (P-04).

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | Solo `backend/` (pom, YAML de dev/test, dos pruebas), plantilla, README, evidencia y `tareas.md`. Sin cambios en `prod`, en la seguridad, el frontend, `.github/`, APF2 ni los documentos de diseño. `backend/HELP.md` eliminado del árbol de trabajo. |
| V-02 | **`mvnw.cmd clean verify` (JDK 21)** | **45 pruebas, 0 fallos/errores/omitidas, BUILD SUCCESS.** **0** avisos de autoacoplamiento de Mockito o de carga dinámica de agentes. `-javaagent` de `mockito-core` presente en la configuración de Surefire. `jacoco.exec` e informe generados (el agente de JaCoCo se conserva gracias a `@{argLine}` y a la propiedad `argLine` vacía por defecto). |
| V-03 | O-3 · prueba de prioridad | Usa un `.properties` temporal (`@TempDir`) que existe de verdad. Comprueba que el archivo se importa y que la variable de entorno simulada prevalece. Codex documentó el control negativo: la prueba falla si se invierte el orden después de cargar ConfigData. Ya no depende de `.local/`, así que en la CI demuestra lo que dice. |
| V-04 | O-6 · doble ruta | `dev` y `test` importan `../.local/…` y `./.local/…` (lista YAML). Hay una prueba parametrizada para ambos perfiles. Codex comprobó el arranque desde la raíz con health 200. La arquitectura §9 está **actualizada en este commit de revisión** (era el hallazgo de Codex). |
| V-05 | O-5 · plantilla | Las claves exclusivas de `prod` están comentadas y se explica por qué. `BT_DB_PASSWORD` es la única clave activa, con un marcador. |
| V-06 | O-7 · limpieza | `@AutoConfigureMockMvc` retirado de `ConfiguracionBaseDatosIT`; `HELP.md` borrado. |

## Ajustes documentales de esta revisión
- Arquitectura §9: doble ruta de importación (DA-17, T-39).
- Arquitectura §6.2: se añaden los códigos `CONFLICTO` (409, violación de integridad no específica) y `ERROR_INTERNO` (500), que faltaban en el catálogo y necesita T-08.
