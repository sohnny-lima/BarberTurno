# Revisión técnica de T-04 — Integración continua

- **Fecha:** 01/10/2026 (America/Lima) · **Revisor:** Claude Code (coordinador y arquitecto)
- **Ejecución:** coordinación automática. Encargo 006 (implementación, 16 min) y encargo 007 (correcciones C-1 y C-2, 4 min), ambos con `codex exec --approve-for-me` y código 0. Registros en `.local/coordinacion/`.
- **Commits revisados:** `af1f79f`, `74adb3a`, merge `dae77f1`, registro `089dbff`; correcciones `38fee6f`, `269f5aa`, merge `a32e9ac`, registro `b7593b4`. Sin remoto ni push.
- **Resultado:** **Aprobada tras las correcciones C-1 y C-2.** La ejecución real en GitHub queda pendiente de P-04 (crear el remoto), tal como prevé el criterio de la tarea.
- **Credenciales:** el workflow usa una credencial efímera propia de la CI, distinta de la contraseña local (comprobado: la contraseña local no aparece en ningún commit de T-04). No se requieren secretos de GitHub.

## 1. Verificado por el revisor

| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | Solo `.github/workflows/ci.yml`, README, `docs/pruebas/t-04.md` y `docs/tareas.md`. Sin cambios en `backend/`, `frontend/`, `docs/apf2/`, requisitos, arquitectura, AGENTS ni CLAUDE. Sin etiquetas nuevas ni worktrees residuales. |
| V-02 | Versiones de las acciones | API de GitHub (`releases/latest`) el 01/10/2026: checkout v7.0.1 (20/07/2026), setup-java v6.0.1 (09/09/2026), setup-node v7.0.0 (14/07/2026) y upload-artifact v7.0.1 (10/04/2026), ninguna *prerelease*. Las etiquetas mayores `v7`/`v6`/`v7`/`v7` existen (HTTP 200). |
| V-03 | actionlint | Volví a descargar el archivo oficial de checksums de la v1.7.12: el SHA-256 del ZIP local coincide (`6e7241b5…f6e9`). `actionlint 1.7.12` sobre el workflow final → código 0, sin diagnósticos. |
| V-04 | Contenido del workflow | `push` + `pull_request`; `permissions: contents: read`; trabajo **backend**: `postgres:18` con `pg_isready`, Temurin 21, caché de Maven sobre `backend/pom.xml`, `./mvnw -B -ntp verify` con `SPRING_PROFILES_ACTIVE=test` y `BT_DB_*` por entorno (DA-17), artefacto JaCoCo + Surefire con `if: always()` y retención de 7 días; trabajo **frontend**: `node-version-file: .node-version` (DA-18), caché npm sobre el lock, `npm ci`, lint, `format:check`, test y build; `timeout-minutes` en ambos. LF sin CRLF. |
| V-05 | **El backend no depende de `.local/`** | Worktree nuevo en el *scratchpad* (fuera del repositorio, sin `.local/`) en `089dbff`; solo variables de entorno (contraseña cargada sin imprimirla): `mvnw.cmd -B -ntp verify` → **42 pruebas, 0 fallos/errores/omitidas, BUILD SUCCESS**; la contraseña no aparece en el log. Worktree eliminado; `git worktree list` solo muestra el principal. |
| V-06 | Frontend | Sin cambios desde la revisión de T-03, donde la misma cadena (`npm ci` sin avisos, lint, formato, 4/4, build) se verificó con Node 24.21.0. Codex la repitió en su simulación sin `.local/`. Los binarios opcionales de Linux están en el lock (DA-19), según la evidencia de Codex. |
| V-07 | Correcciones C-1 y C-2 | Diff exacto revisado (abajo); actionlint en verde; `git diff --check` limpio. |
| V-08 | Documentación | El README explica los trabajos, la activación con P-04 (`git remote add` y primer push, a cargo del responsable), los artefactos y que no hacen falta secretos; la evidencia recoge versiones, SHA-256, simulación y diferencias entre la CI y el entorno local (puerto 5432/5433, superusuario en la CI, versión menor 18.x, idioma e intercalación). |

## 2. Criterios de aceptación de T-04

| Criterio | Estado |
|---|---|
| Workflow con trabajos backend (Temurin 21, `postgres:18`, `verify`, JaCoCo como artefacto) y frontend (Node desde `.node-version`, `npm ci`, lint, `format:check`, test, build), con cachés | Cumplido (V-04). |
| YAML válido con actionlint | Cumplido (V-03). |
| README con la activación al crear el remoto (P-04) | Cumplido (V-08). |
| Pruebas independientes del idioma y la intercalación | Cumplido; ninguna prueba depende de ellos (revisión de T-02, §10). |
| Ejecución local equivalente | Cumplido (V-05, V-06). |
| Ejecución real | **Pendiente de P-04**, como prevé la tarea. |

## 3. Observaciones

| ID | Tipo | Observación | Acción |
|---|---|---|---|
| O-1 | Defecto | **Colisión de concurrencia:** el grupo `workflow-(head_ref \|\| ref_name)` coincidía en `push` y `pull_request` de la misma rama, así que `cancel-in-progress` cancelaba uno de los dos runs. | **C-1** (encargo 007): se añade `github.event_name` al grupo. Verificada. Cerrada. |
| O-2 | Endurecimiento | `actions/checkout` conservaba el token en `.git/config`, aunque el workflow nunca hace push. | **C-2**: `persist-credentials: false` en ambos checkouts. Verificada. Cerrada. |
| O-3 | Diferencia aceptada | En la CI, el rol del servicio PostgreSQL es superusuario; en local, no. | Documentada. La prueba sin privilegios de `btree_gist` se hace en local (revisión de T-01) y en T-06. |
| O-4 | Pendiente externo | La CI no se ha ejecutado en GitHub. | P-04 (responsable). En el primer run, revisar los dos trabajos y el artefacto. |

Diff final de las correcciones:

```diff
-  group: ${{ github.workflow }}-${{ github.head_ref || github.ref_name }}
+  # El evento separa push y pull_request para evitar que se cancelen entre sí.
+  group: ${{ github.workflow }}-${{ github.event_name }}-${{ github.head_ref || github.ref_name }}
 …
         uses: actions/checkout@v7
+        with:
+          persist-credentials: false
```

## 4. Pendiente
| Pendiente | Dónde |
|---|---|
| Primer run real y revisión de sus resultados | Tras P-04 |
| Ajustes del backend (prueba de prioridad autocontenida, agente de Mockito…) | T-39 (recomendada antes del primer run real) |
| PostgreSQL 18.6 local | T-38 |
