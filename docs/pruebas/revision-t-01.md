# Revisión técnica de T-01 — Repositorio Git y estructura base

- **Fecha:** 01/10/2026 (America/Lima) · **Revisor:** Claude Code (arquitecto)
- **Commits revisados:** `e780283` (base) y `9fdb89e` (evidencias y cierre), autor `sohnny-lima`, rama `main`, sin remoto.
- **Resultado:** **Con observaciones (no bloqueantes).** T-01 queda aceptada y se puede iniciar T-02.
- **Credenciales:** ninguna contraseña aparece en este documento ni en las salidas. Se usó `PGPASSFILE=.local/pgpass.conf` para `postgres` y `.local/barberturno.env` para el rol de aplicación, cargado en memoria sin imprimirlo.

## 1. Verificado por el revisor (ejecutado en esta revisión)

| # | Comprobación | Método | Resultado |
|---|---|---|---|
| V-01 | Árbol limpio e historial | `git status --short --branch`; `git log --all` | `main` limpio; 2 commits; sin remotos. |
| V-02 | Integridad del repositorio | `git fsck` | Código 0, sin errores. |
| V-03 | `docs/apf2/` intacto frente al blob versionado | SHA-256 del árbol de trabajo vs `git cat-file -p HEAD:<ruta>` | 36/36 idénticos. |
| V-04 | `docs/apf2/` intacto frente a la instantánea previa de Codex | SHA-256 vs `.local/apf2-inicial.json` | 36/36 idénticos. |
| V-05 | `docs/apf2/` intacto frente a la medición de la primera etapa del arquitecto | Tamaños y fechas de modificación | Mismos tamaños (PDF 5 377 175 B, DOCX 3 758 744 B, drawio 297 228 B, XLSX 18 275 B, HTML 29 326 B, LEEME 3 735 B) y fechas del 28/09/2026. |
| V-06 | `docs/apf2/` solo en el commit base | `git log -- docs/apf2` | Solo `e780283`. |
| V-07 | Sin normalización de finales de línea en APF2 | `git ls-files --eol` | `attr/-text`; índice y árbol iguales. |
| V-08 | Rutas sensibles nunca versionadas | `git log --all --name-only` filtrado por `.local/`, `.env`, `pgpass`, `*.pem`, `*.key`, `*.p12`, `*.pfx` | Ninguna. |
| V-09 | Sin valores de secretos en el historial | `git grep` en todos los commits: `BT_*PASSWORD=`, `BT_JWT_SECRET=`, `PGPASSWORD=`, entradas pgpass y `PASSWORD '…'` en SQL | Ninguna coincidencia. |
| V-10 | Exclusiones efectivas | `git check-ignore -v` | `.local/pgpass.conf`, `.local/barberturno.env`, `backend/.env.local`, `.env`, `backend/target/`, `frontend/node_modules/`, `frontend/dist/` ignorados. `.local/` es lo único sin rastrear. |
| V-11 | `.local/barberturno.env` | Solo nombres de claves | Contiene únicamente `BT_DB_PASSWORD`. |
| V-12 | Versión real de PostgreSQL | `psql --version` (cliente 18), `SELECT version()`, `pg_isready` | Cliente y servidor **18.0** (msvc, 64 bits) en `localhost:5433`, aceptando conexiones. |
| V-13 | Perfil del rol de aplicación | `pg_authid` | `barberturno`: LOGIN; sin SUPERUSER, CREATEDB, CREATEROLE, REPLICATION ni BYPASSRLS; sin pertenencia a otros roles; contraseña presente con esquema **SCRAM-SHA-256**. |
| V-14 | Bases de datos | `pg_database` | `barberturno` y `barberturno_test`, dueño `barberturno`, UTF8, intercalación `Spanish_Peru.1252`, `datacl` por defecto. |
| V-15 | Idempotencia (3.ª ejecución) | `db-local.sql` con `psql` 18, comparando OID, dueños y huella MD5 del hash de la contraseña antes y después | Código 0; mensaje "ya tiene contraseña"; **sin cambios**. |
| V-16 | Acceso y permisos de la aplicación | Conexión como `barberturno` a ambas bases | `CREATE` en `public` = true; `CREATE` en la base = true. |
| V-17 | Requisito de T-06: extensión y exclusión | Como `barberturno`, en transacción: `CREATE EXTENSION btree_gist` + tabla con `EXCLUDE USING gist (barbero_id WITH =, tstzrange(inicio,fin,'[)') WITH &&) WHERE (estado <> 'CANCELADA')` + 4 inserciones + `ROLLBACK` | Extensión creada sin superusuario; contigua 10:30 aceptada; solapada cancelada aceptada; solapada confirmada **rechazada por la restricción**; tras el ROLLBACK, `btree_gist` no queda instalada. Igual en las dos bases. |
| V-18 | `db-local.sql` sin secretos | Lectura enmascarada | No hay contraseñas literales; usa `\password`; `ON_ERROR_STOP`; valida la versión 18 y el puerto 5433 antes de escribir; no cambia dueños ni borra datos. |
| V-19 | Configuración global de Git y propiedad | `git config --global --get-all safe.directory`; `Get-Acl` | `safe.directory` = solo `D:/Proyectos/BarberTurno`. Dueño de `.git`: `Sohnny\CodexSandboxOffline`; dueño de la raíz: `SOHNNY\limas`. |
| V-20 | Coherencia documental | Lectura de README, `t-01.md` y la nota de cierre | Coherentes entre sí y con el plan. Corregido: faltaba `9fdb89e` en la tabla de estado. |
| V-21 | Documentos de diseño no alterados por Codex | Número de líneas y contenido de AGENTS.md, CLAUDE.md, requisitos, arquitectura | Coinciden con lo entregado en la etapa 1; Codex solo modificó la fila de T-01 y las notas de cierre de `tareas.md`. |

## 2. Criterios de aceptación de T-01

| Criterio | Estado |
|---|---|
| `git init -b main`, `.gitignore`, `.gitattributes`, `.editorconfig`, README, `tools/db-local.sql`, `docs/pruebas/.gitkeep` | Cumplido (V-01, V-10). |
| `git status` limpio tras el commit inicial, que incluye `docs/apf2/` sin modificar y los documentos de la etapa 1 | Cumplido (V-01, V-03…V-07, V-21). |
| `db-local.sql` idempotente | Cumplido (V-15; Codex documentó 2 ejecuciones, el revisor ejecutó la 3.ª). |
| `git check-ignore` sobre `backend/target` y `frontend/node_modules` | Cumplido (V-10). |

Desviaciones aceptadas: (a) commit directo en `main`, inevitable en el primer commit; (b) contraseña del rol pedida con `\password` en lugar de una contraseña de desarrollo fija en el SQL, una mejora de seguridad.

## 3. Diferencia 18.0 / 18.6 — resolución

- **Hecho:** el entorno real es PostgreSQL **18.0** (V-12). La arquitectura citaba **18.6**, que es la última menor publicada según postgresql.org. No existe una 18.5.
- **Riesgo:** postgresql.org/support/security enumera unos 46 CVE corregidos entre 18.1 y 18.6. Varios son de severidad alta (CVSS 8.8), aunque la mayoría requieren un usuario autenticado. Algunos afectan a herramientas cliente (`psql`, `pg_dump`, `pg_restore`, `libpq`), que se usarán en T-36. El uso local es de un solo desarrollador, solo en `localhost` y con datos ficticios.
- **Funcionalidad:** el diseño (`btree_gist`, `EXCLUDE`, `tstzrange`, `make_interval`) funciona en 18.0 (V-17). Una versión menor no cambia el comportamiento SQL ni el formato de datos.
- **Decisión (DA-16):** la versión mayor 18 es fija. Producción y la CI usan la última menor. En local se tolera 18.0 de forma transitoria. **Sí hace falta una tarea de actualización: T-38**, a cargo del estudiante (el instalador requiere permisos de administrador), verificada por Codex. No bloquea T-02…T-05; se recomienda antes de T-06 y es obligatoria antes de T-36.

## 4. Observaciones

| ID | Tipo | Observación | Acción |
|---|---|---|---|
| O-1 | Documental | La tabla de estado de T-01 solo citaba `e780283`. | **Corregida** en `tareas.md`. |
| O-2 | Entorno | PostgreSQL 18.0 frente a 18.6. | DA-16 + **T-38** (arquitectura §2, AGENTS.md §7). |
| O-3 | Entorno | `.git` pertenece a la cuenta del sandbox de Codex; Git depende de `safe.directory` (V-19). Funciona y la integridad es correcta (V-02). | **Decisión del estudiante**, opcional: desde una consola de administrador, `takeown /F D:\Proyectos\BarberTurno\.git /R /D Y` y después `git config --global --unset safe.directory D:/Proyectos/BarberTurno`. Mientras tanto no impide trabajar. |
| O-4 | Diseño | Convivían dos ubicaciones de secretos (`backend/.env.local` en el plan y `.local/` de hecho) y había que exportarlos a mano. | **Aplicada:** DA-17. `.local/barberturno.env` se importa en `dev`/`test`; el README se actualiza en T-02. |
| O-5 | Diseño | Las bases conservan los privilegios por defecto de PUBLIC (CONNECT y TEMP) (V-14). Es aceptable en local porque no hay otros roles de login. | **Aplicada** en el alcance de T-33 para producción. |
| O-6 | Diseño | `lc_messages` en español e intercalación `Spanish_Peru.1252` (V-14, V-17), distintos de la CI en Linux. | **Aplicada** en arquitectura §10: las pruebas comprueban SQLState y nombres de restricción, no mensajes ni orden por intercalación. |

No se detectaron defectos de implementación que requieran correcciones de Codex en T-01.

## 5. Pendiente (no verificable o fuera de T-01)

| Pendiente | Motivo | Dónde se resuelve |
|---|---|---|
| Ejecución real de la CI | No hay remoto (P-04). | T-04 + decisión del estudiante. |
| Node 24 LTS | El equipo tiene Node 20.19. | Prerrequisito de T-03. |
| PostgreSQL 18.6 | Requiere el instalador con permisos de administrador. | T-38. |
| Propiedad de `.git` | Requiere una consola de administrador. | O-3 (opcional). |
| Comandos del README para backend y frontend | Aún no existen esos proyectos. | T-02 / T-03. |
