# Revisión técnica de T-03 — Esqueleto frontend Angular 22

- **Fecha:** 01/10/2026 (America/Lima) · **Revisor:** Claude Code (coordinador y arquitecto)
- **Ejecución:** primera tarea realizada con la coordinación automática (CLAUDE.md, "Coordinación automática con Codex"). Encargo 004 (implementación) y encargo 005 (corrección C-1), ambos con `codex exec --approve-for-me` y código de salida 0. Registros en `.local/coordinacion/` (ignorada por Git).
- **Commits revisados:** `cfde88e`, `d610809`, `c89941c`, merge `0e80776`, registro `99cb3b5` (etiqueta local `T-03-registro`); decisión del coordinador `e966fc4` (DA-19); corrección `195cebb`, `24caca2`, merge `bd81f41`, registro `27a4b14`. Sin remoto ni push.
- **Resultado:** **Aprobada tras la corrección C-1.** Las observaciones restantes no son defectos de T-03 (ver §3).
- **Credenciales:** no aparece ningún valor de secreto en este documento ni en las salidas.

## 1. Verificado por el revisor

| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Comunicación con Codex (encargo 001, `-s read-only`) | Código 0; la respuesta coincidía con el estado real (`main` 8dd49ce, árbol limpio, T-03 Pendiente, DA-18). |
| V-02 | Diagnóstico del sandbox (encargos 002 y 003) | `workspace-write` no se inicializa: `deny ACE failed on …\.git` porque `.git` pertenece a `CodexSandboxOffline` (O-3 de T-01) y `limas` no tiene WRITE_DAC sin elevación. Con `--approve-for-me`, las escaladas pasan la revisión automática y se ejecutan como `limas` con red. No se usaron modos sin sandbox. |
| V-03 | Supervisión del encargo 004 | Avance real comprobado en disco a los 6 min (rama, `.node-version`, fnm 24.21.0, `frontend/`); 22 min en total; stderr solo con el rechazo esperado del sandbox; sin esperas de red, permisos ni límites. |
| V-04 | Node según DA-18 | fnm muestra v24.21.0; `.node-version` = 24.21.0; el Node global sigue en v20.19.0; perfiles de PowerShell sin cambios (fechas de 2025); sin variables `FNM*` de usuario; `--version-file-strategy recursive` solo como opción por sesión. |
| V-05 | Alcance y límites | No cambiaron `docs/apf2/` (36/36 SHA-256), requisitos, arquitectura, backend, AGENTS ni CLAUDE en los commits de Codex; T-38 y T-39 sin tocar; sin `.git` anidado. |
| V-06 | Generación y configuración | Proyecto `frontend`; Angular CLI/build 22.2.0, core 22.2.1, Material/CDK 22.2.1, angular-eslint 22.5.0, TypeScript 6.0.3, Vitest 5.0.3, Prettier 3.9.9; sin zone.js (0 en el lock); componente standalone con OnPush y signal; `provideHttpClient(withXsrfConfiguration({XSRF-TOKEN, X-XSRF-TOKEN}))`; `LOCALE_ID` es-PE con `registerLocaleData`; `lang="es"`; tema M3 generado desde #173c4d/#087f8c con tipografía del sistema (sin Google Fonts); `proxy.conf.json` enlazado en `serve`; `engines.node ^24.15.0`; `packageManager npm@11.19.0`; ESLint con `templateAccessibility`. |
| V-07 | `tsconfig.json` sin `strict` | Una generación de referencia con CLI 22.2.0 en el *scratchpad* produce el mismo archivo. TypeScript 6.0.3 rechaza un `any` implícito y `null` en `string` sin `strict` explícito: **`strict` está activo por defecto**. No es un defecto. |
| V-08 | Pruebas | `App` comprueba "BarberTurno" en `mat-toolbar h1` con los proveedores reales; `LOCALE_ID` = es-PE; un POST relativo lleva `X-XSRF-TOKEN` y un GET no; la cookie se limpia en `afterEach` y `verify()` comprueba que no quedan peticiones. Sin dependencia de zona horaria ni idioma. |
| V-09 | Cadena de calidad (Node 24.21.0, tras C-1) | `npm ci` código 0 (397 paquetes, 0 vulnerabilidades, **sin avisos `install-scripts`**); `install-scripts ls` → "No packages with unreviewed install scripts"; `lint` y `format:check` → 0; **4/4 pruebas**; `build` → 0 (inicial 241,27 kB / 65,33 kB transferidos); existe `dist/frontend/browser/index.html`. |
| V-10 | Ejecución real | Backend `dev` (jar, Java 21) + `ng serve`: `GET :4200/` → 200 con `<title>BarberTurno` y `lang="es"`; `GET :4200/api/x` → **401 del backend** a través del proxy (`X-Content-Type-Options: nosniff`, igual que `:8080/api/x`); `:4200/actuator/health` lo sirve la SPA (solo se reenvía `/api`). Ambos procesos detenidos; 4200 y 8080 libres. |
| V-11 | Git | Artefactos ignorados (`node_modules`, `dist`, `.angular`, `.vscode`); `package-lock.json` versionado; LF sin BOM; `git diff --check` limpio; sin secretos en los commits. |
| V-12 | Corrección C-1 | `npm install-scripts deny …` solo añadió `allowScripts` (4 × `false`) a `frontend/package.json`; el lock no cambió; los binarios precompilados siguen presentes. |

## 2. Criterios de aceptación de T-03

| Criterio | Estado |
|---|---|
| Node 24.21.0 con fnm, `.node-version`, sin tocar el Node global | Cumplido (V-04). |
| `npm ci`, lint, `format:check`, test y build en verde | Cumplido (V-09). |
| "BarberTurno" con el tema aplicado | Cumplido (V-06, V-10). |
| `npm start` en :4200 y proxy `/api` hacia el backend | Cumplido (V-10). |
| Artefactos fuera de Git y sin repositorio anidado | Cumplido (V-05, V-11). |
| Pruebas de título, locale y XSRF | Cumplido (V-08). |
| DoD: commits Conventional `[T-03]`, README, evidencias y `tareas.md` | Cumplido; hashes y merge anotados (se corrige la O-1 recurrente de T-01 y T-02). |

## 3. Observaciones

| ID | Observación | Acción |
|---|---|---|
| O-1 | npm 11 dejaba sin decidir 4 scripts de instalación (`esbuild`, `@parcel/watcher`, `lmdb`, `msgpackr-extract`). | **DA-19** (commit `e966fc4`) + **corrección C-1** de Codex (encargo 005), **verificada** (V-09, V-12). Cerrada. |
| O-2 | Codex creó la etiqueta local `T-03-registro` sin que se pidiera. Es inocua. | Regla añadida en AGENTS §6. **Decisión del responsable:** conservarla o eliminarla (`git tag -d T-03-registro`). |
| O-3 | fnm necesita `--version-file-strategy recursive` para leer el `.node-version` de la raíz desde `frontend/`. | Documentado por Codex en el README; AGENTS §7 actualizado (`e966fc4`). Cerrada. |
| O-4 | `tsconfig.json` sin `strict` explícito. | No es un defecto (V-07). |
| O-5 | El sandbox de escritura de Codex no funciona por la propiedad de `.git`. | Se trabaja con `--approve-for-me` (revisión automática). Solución definitiva en CLAUDE.md (requiere consola de administrador). |

## 4. Pendiente

| Pendiente | Dónde |
|---|---|
| CI real con Node desde `.node-version` | T-04 (requiere remoto, P-04) |
| Ajustes del backend | T-39 |
| PostgreSQL 18.6 | T-38 |
| Propiedad de `.git` y etiqueta `T-03-registro` | Decisión del responsable |
