# Revisión técnica de T-02 — Esqueleto backend Spring Boot 4.1

- **Fecha:** 01/10/2026 (America/Lima) · **Revisor:** Claude Code (arquitecto)
- **Commits revisados:** `94b77ec` (aplicación, perfiles y seguridad provisional), `49afb37` (pruebas), `68bc313` (documentación) y `4e958f3` (merge `--no-ff` en `main`). Base: `d782552`. Sin remoto ni push.
- **Resultado:** **Con observaciones (no bloqueantes).** T-02 queda aceptada. Se puede avanzar a T-03 cuando haya Node ≥ 24.15 (DA-18). Los ajustes menores van a la nueva tarea **T-39**.
- **Credenciales:** no aparece ningún valor de secreto en este documento ni en las salidas. La contraseña local solo se cargó en memoria para buscarla, en modo literal, en el historial y en los logs. Las pruebas de fallo usaron valores ficticios.

## 1. Verificado por el revisor (ejecutado en esta revisión)

| # | Comprobación | Método | Resultado |
|---|---|---|---|
| V-01 | Historial y estado | `git log --graph`, `git status`, `git remote -v` | `main` limpio; 3 commits en la rama `tarea/T-02-esqueleto-backend` + merge `--no-ff` `4e958f3` con padres `d782552` y `68bc313`; sin remotos. |
| V-02 | Alcance del cambio | `git diff --stat d782552 4e958f3` | 26 archivos: `backend/**`, README, `tools/barberturno.env.example`, `docs/pruebas/t-02.md` y la fila y la nota de `tareas.md`. **No** se tocaron `docs/apf2/`, `requisitos.md`, `arquitectura.md`, AGENTS ni CLAUDE. |
| V-03 | Coordenadas y versiones | Lectura de `pom.xml` y `maven-wrapper.properties`; log de `verify` | Parent 4.1.1; Java 21; starters `webmvc`, `data-jpa`, `flyway`, `security`, `security-oauth2-resource-server`, `validation`, `actuator`, `flyway-database-postgresql`, `postgresql` (runtime) y los `*-test` de Initializr; springdoc 3.1.1; JaCoCo 0.8.15 (`prepare-agent` + `report` en `verify`); Surefire con `**/*Test.java` + `**/*IT.java` y `spring.profiles.active=test`; Wrapper 3.3.4 `only-script` → Maven 3.9.16. Sin Lombok, MapStruct, H2 ni Testcontainers. |
| V-04 | Finales de línea y permisos del wrapper | `git ls-files --eol`, `git ls-files -s` | `mvnw`: índice LF / árbol LF, modo **100755**. `mvnw.cmd`: índice LF / árbol **CRLF**. Ningún otro archivo de T-02 tiene CRLF ni BOM. `git diff --check d782552 4e958f3` sin errores. |
| V-05 | **`mvnw.cmd clean verify` con JDK 21** | `JAVA_HOME` = Temurin 21.0.8, sin variables `BT_*` en el proceso | **Tests run: 42, Failures: 0, Errors: 0, Skipped: 0 · BUILD SUCCESS** (54 s). Las IT arrancan con "Java 21.0.8" contra `jdbc:postgresql://localhost:5433/barberturno_test (PostgreSQL 18.0)`. JaCoCo analiza 4 clases. |
| V-06 | Log de `verify` | Búsquedas en el log | Contraseña local ausente; 0 apariciones de `generated security password`; avisos solo de Flyway ("No migrations found", esperado hasta T-06) y de Mockito/ByteBuddy por carga dinámica del agente (O-4). |
| V-07 | Arranque real `dev` (jar) | `java -jar … --spring.profiles.active=dev` y peticiones HTTP | Perfil activo `dev`, Java 21.0.8, `jdbc:postgresql://localhost:5433/barberturno (PostgreSQL 18.0)`, Tomcat en 8080, 0 líneas ERROR, sin contraseña generada ni contraseña local en los logs. |
| V-08 | Respuestas HTTP en `dev` | `Invoke-WebRequest` sin seguir redirecciones | `GET /actuator/health` → 200 con exactamente `{"status":"UP"}`; `HEAD` health → 200; `POST` health → 401; `/swagger-ui.html` → 302 (redirección a la UI); `/swagger-ui/index.html` → 200; `/v3/api-docs` → 200 (OpenAPI 3.1.0); `/v3/api-docs/swagger-config` → 200; `GET /api/cualquier-ruta`, `POST /api/reservas`, `/actuator/env`, `/actuator/health/db`, `/login` → 401. Ninguna respuesta incluye `Set-Cookie`. |
| V-09 | Servidor detenido | `Stop-Process` + `Get-NetTCPConnection` | Proceso terminado; puertos 8080 y 18080 libres al final de todas las pruebas. |
| V-10 | `prod` sin variables | Jar con `--spring.profiles.active=prod` | Código **1**: `IllegalStateException: Faltan variables obligatorias de producción: BT_DB_URL, BT_DB_USER, BT_DB_PASSWORD, BT_JWT_SECRET, BT_ADMIN_CORREO, BT_ADMIN_PASSWORD, BT_ADMIN_NOMBRE, BT_COOKIE_SECURE` (solo nombres). |
| V-11 | `prod` **no importa** archivos locales | Desde `backend/` (donde `../.local/barberturno.env` existe), con las 8 variables salvo `BT_DB_PASSWORD` (valores ficticios) | Código 1: `Faltan variables obligatorias de producción: BT_DB_PASSWORD`. El archivo local, que sí contiene esa clave, no se leyó. |
| V-12 | **Prioridad del entorno sobre el archivo** (`dev`) | `BT_DB_PASSWORD` de entorno con un valor ficticio incorrecto; el archivo local tiene la correcta | Código 1, **SQLState 28P01** (autenticación rechazada): se usó el valor del entorno. Con el archivo solo (V-07), la conexión funciona. |
| V-13 | `prod` sin credenciales por defecto | Lectura de `application-prod.yml` y `ConfiguracionProduccion` | `${BT_DB_URL}`, `${BT_DB_USER}`, `${BT_DB_PASSWORD}` sin valores por defecto; sin `spring.config.import`; `BeanFactoryPostProcessor` que exige las 8 variables, JWT Base64 ≥ 32 bytes, `BT_COOKIE_SECURE=true` y rechaza combinarse con `dev`, `test` o `demo`. |
| V-14 | PostgreSQL de pruebas y `lock_timeout` | `ConfiguracionBaseDatosIT` (en V-05) + lectura | Comprueba `current_database()` = `barberturno_test`, versión mayor 18 y `SHOW lock_timeout` = `5s` **en dos conexiones simultáneas del pool**. Configurado con `hikari.connection-init-sql`. |
| V-15 | `Clock` inyectable | `ClockConfig` + `ClockConfigTest` + `BarberTurnoApplicationIT` | `Clock.system(ZoneId.of("America/Lima"))` como bean; la prueba comprueba que 14:00Z = 09:00 en Lima y que el bean del contexto tiene esa zona. |
| V-16 | Seguridad provisional | Lectura de `SecurityConfig` + `SecurityConfigIT` + `DocumentacionIT` | STATELESS; sin `formLogin`, `httpBasic` ni `logout`; `requestCache` desactivada; entry point 401; health (GET/HEAD) público; Swagger y OpenAPI públicos **solo** si `springdoc.*.enabled` es true (en `test` dan 401 y `DocumentacionIT` los habilita explícitamente → 200); `anyRequest().denyAll()`; un usuario simulado ADMIN recibe 403. No hay `UserDetailsService` (prueba explícita): la contraseña generada no aparece. |
| V-17 | Deuda de autenticación y CSRF | Javadoc de `SecurityConfig`, README, nota de cierre y `t-02.md` | Documentada como provisional hasta T-10. **Añadido** en esta revisión al alcance de T-10: sustituir la configuración provisional y reactivar CSRF (O-2). |
| V-18 | Calidad de las pruebas | Lectura de las 7 clases | Comprueban comportamiento real (HTTP, conexiones reales, arranque del contexto, validación de producción). Ninguna aserción depende de mensajes localizados de PostgreSQL: los mensajes comprobados son los propios de la aplicación. Con limitaciones en O-3. |
| V-19 | README y plantilla | Lectura y diff | Coherentes con DA-17, el arranque con JDK 21, la evidencia y los perfiles. La plantilla solo tiene marcadores y datos ficticios, pero deja activas claves exclusivas de `prod` (O-5). |
| V-20 | `docs/apf2/` intacto | SHA-256 contra `.local/apf2-inicial.json`; `git log -- docs/apf2` | 36/36 iguales; solo el commit `e780283`. |
| V-21 | Secretos | `git log --all --name-only` filtrado; `git grep` de patrones; búsqueda literal del valor real en todos los commits, el árbol versionable y los logs | Ninguna ruta sensible versionada; el único patrón detectado es un falso positivo de la revisión de T-01 (texto `BT_JWT_SECRET=` sin valor); el valor real de `BT_DB_PASSWORD` no aparece en ningún commit, archivo versionable ni log. |
| V-22 | Versión de Node para T-03 | `node -v`, `nvm list`, `fnm list`, `npm view` | Sistema 20.19.0; nvm: 24.0.2, 22.22.0, 20.x; fnm: 22.16.0, 18.20.8. Angular CLI 22.2.0 / core 22.2.1 exigen `^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0` → **ninguna instalada cumple**. Última 24 LTS: 24.21.0 (07/09/2026). |

## 2. Criterios de aceptación y Definición de Hecho de T-02

| Criterio | Estado |
|---|---|
| Generado con Initializr (4.1.1, Java 21, `pe.barberturno`), springdoc 3.1.1, JaCoCo 0.8.15, Surefire con `*Test` + `*IT` | Cumplido (V-03). |
| Perfiles según §9 y DA-17: `dev`/`test` importan `.local`; `prod` sin valores por defecto | Cumplido (V-11, V-12, V-13); ruta alternativa `./` pendiente (O-6). |
| `open-in-view=false`, `validate`, `time_zone=UTC`, `lock_timeout 5s`, `forward-headers-strategy`, solo `health` sin detalles, springdoc solo en `dev` | Cumplido (V-05, V-08, V-14). |
| `SecurityConfig` provisional con 401 y sin contraseña generada | Cumplido (V-08, V-16, V-06). |
| `Clock` America/Lima | Cumplido (V-15). |
| Plantilla `tools/barberturno.env.example`; `mvnw` ejecutable | Cumplido (V-19, V-04), con O-5. |
| `verify` en verde sin exportar variables; `dev` arranca y health UP; `prod` sin variables falla | Cumplido (V-05, V-07, V-10). |
| Pruebas indicadas en el plan | Cumplido y ampliado (42 pruebas). |
| DoD: Javadoc, sin secretos, commits Conventional `[T-02]`, `tareas.md` y README actualizados | Cumplido; la tabla de estado estaba incompleta (O-1, corregido). |

## 3. Observaciones

| ID | Tipo | Observación | Acción |
|---|---|---|---|
| O-1 | Documental | La tabla de estado solo citaba `94b77ec` y `49afb37`; faltaban `68bc313` y el merge `4e958f3`. Ya ocurrió en T-01. | **Corregida.** Recordatorio para Codex: anotar todos los commits de la tarea y el merge. |
| O-2 | Deuda registrada | CSRF desactivado y `denyAll` provisional. | Explícito en el alcance de **T-10**. |
| O-3 | Calidad de pruebas | `ConfiguracionPerfilesTest` simula la variable de entorno, pero no garantiza que exista un archivo importado: en la CI, sin `.local`, la prueba pasa sin demostrar la prioridad. La prioridad real sí se comprobó en esta revisión (V-12). | **T-39.1** |
| O-4 | Mantenimiento | Mockito y ByteBuddy se acoplan dinámicamente (avisos de que un JDK futuro lo prohibirá). | **T-39.2** |
| O-5 | Configuración | La plantilla deja activas claves exclusivas de `prod` (`BT_COOKIE_SECURE=true`, `BT_JWT_SECRET`, `BT_ADMIN_*`). Si se copia a `.local/`, `dev` usaría `Secure` y secretos de ejemplo, en contra del README ("false por defecto en dev/test"). | **T-39.3** |
| O-6 | Robustez | Solo se importa `../.local/barberturno.env`: ejecutar desde la raíz (IDE) deja `BT_DB_PASSWORD` sin resolver. El prompt de T-02 pedía también `./`. Cumple DA-17 tal como estaba escrita. | **T-39.4** |
| O-7 | Limpieza | `backend/HELP.md` sigue en el árbol (ignorado y no versionado); `@AutoConfigureMockMvc` innecesario en `ConfiguracionBaseDatosIT`. | **T-39.5** |
| O-8 | Entorno | No hay Node compatible con Angular 22.2 (V-22). | **DA-18** + prerrequisito de **T-03** (fnm, Node 24.21.0). |
| O-9 | Decisión aceptada | Probes de health desactivados para mantener el cuerpo exacto `{"status":"UP"}`. | Registrado en arquitectura §11. |

No hay defectos que bloqueen ni que invaliden la entrega.

## 4. Pendiente (no verificable o fuera de T-02)

| Pendiente | Motivo | Dónde |
|---|---|---|
| CI real | Sin remoto (P-04). | T-04 |
| Node 24.21.0 | No instalado. | T-03 (DA-18) |
| PostgreSQL 18.6 | Instalación con permisos de administrador. | T-38 |
| Autenticación, CSRF y JWT en cookie | Fuera del alcance de T-02. | T-10 |
| Ajustes O-3…O-7 | Calidad, sin impacto funcional. | T-39 |
| Propiedad de `.git` (O-3 de T-01) | Decisión del estudiante. | — |
