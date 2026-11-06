# Encargo 147 · Scripts de demo v2 en Azure

**Fecha:** 05/11/2026. **Estado:** implementación terminada y validada en local; pendiente de revisión y ejecución remota por Claude con la sesión del responsable.

**Rama:** `tarea/V2-DEMO-provision`, desde `115eec720c2d5644f18e8eb2e5a5c93acc8cb533`. Trabajo limitado a `tools/demo-azure/**` y este informe. No se modifica el backlog ni `docs/tareas.md`: el encargo específico sustituye el flujo general de v1 y solo autoriza estos archivos. No se integra la rama ni se hace push.

## Archivos y comportamiento

| Archivo | Implementación |
|---|---|
| `tools/demo-azure/comun.ps1` | Destinos cerrados, secretos persistentes, SCRAM, raíces verificadas, procesos ocultos con entorno filtrado, psql y az.cmd con salida sensible suprimida, verificación de suscripción/F1 e intervalos IPv4. Importarlo no lee secretos ni ejecuta comandos. |
| `preparar-base.ps1`, `preparar-base.sql` | Verificadores locales enviados por entorno, roles mínimos, base de propietario separado, PUBLIC revocado, CONNECT de ejecución, extensión creada como admin y limpieza de permisos temporales en `finally`. Informe final sin credenciales. |
| `validar-base.sql` | Antes de escribir: PostgreSQL 18, btadmin no superusuario con CREATEDB/CREATEROLE, roles LOGIN/NOINHERIT sin privilegios especiales ni pertenencias, propietarios/ACL y dependencias compartidas. El rol de ejecución no puede poseer ningún objeto. Solo permite la concesión administrativa automática al creador, sin SET/INHERIT. |
| `sembrar.ps1` | Mismo jar parametrizable, `dev,demo`, roles separados, TLS JDBC, puerto aleatorio en loopback, directorio vacío nuevo y padre nuevo sin `.local`. Captura simultánea de stdout/stderr; espera disponibilidad, termina el hijo, revisa secretos y registra hash del jar/marcador una vez. Candado para evitar siembras simultáneas. |
| `configurar-app.ps1` | Solo app demo existente en su plan Linux F1 y HTTPS. Settings prod mediante JSON temporal privado y `-o none`, JWT de 48 bytes Base64, administrador ficticio, privacidad BORRADOR, interruptores explícitos, memoria/puertos del encargo, TWA opcional conjunta. `healthCheckPath` mediante JSON de siteConfig. Ambos JSON se eliminan en `finally`. |
| `cortafuegos.ps1` | Solo IP salientes sin cobertura, reglas exactas `demo-v2-*`. No modifica otras reglas; rechaza colisiones. Simulación sin red con inventario opcional. |
| `retirar.ps1`, `retirar.sql` | Simulación por defecto; ejecución solo con `-Confirmar` y UUID verificado. Comprueba grupo y dependencias antes de detener la app demo. Borra únicamente base/roles demo, reglas generadas y grupo demo; no utiliza FORCE/CASCADE ni termina sesiones. Permite reintentar retiradas parciales. |
| `inventario-ejemplo.json` | Solo IP reservadas para documentación; cubre IP exacta, intervalo y duplicados. Nunca se usa en ejecución real. |
| `validar-local.ps1` | Sintaxis, simulaciones, rechazos, intervalos, aislamiento de entorno, vector SCRAM RFC 7677 y prueba local de comillas cmd. Sin Azure, jar, PostgreSQL ni archivos privados. |
| `README.md` | Requisitos, orden, objetos/archivos, límites TLS, instrucciones de detener/retirar y recuperación de incidencias. |

La guía raíz, Java, YAML, migraciones y scripts locales anteriores permanecen intactos. No se añadieron endpoints ni Javadoc: no hay cambios en `backend/src/main/java`.

## Decisiones y diferencias concretas con el enunciado

1. **PostgreSQL 16+ conserva ADMIN al creador.** La concesión automática está otorgada por el bootstrap y el creador no superusuario no puede eliminarla. Se permite exclusivamente esa fila con ADMIN verdadero, SET/INHERIT falsos; la concesión propia temporal de SET tiene `GRANTED BY btadmin` y se revoca de forma explícita. El informe exige `sin_set_ni_herencia_temporal = t`. No se modifica ningún rol `azure_*` ni el rol `postgres`. [Documentación PostgreSQL 18](https://www.postgresql.org/docs/18/role-attributes.html).
2. **CONNECT administrativo temporal.** Tras revocar PUBLIC, `btadmin` necesita CONNECT para crear la extensión como admin. Se concede desde el propietario y se revoca antes de retirar SET; el propietario conserva los permisos de la base y la aplicación solo CONNECT.
3. **`Started` no acredita el fin de los runners.** El código de Spring Boot 4.1.1 llama a `logStarted`, luego a `callRunners` y finalmente a `listeners.ready`. Se espera además el mensaje de disponibilidad `ReadinessState ... ACCEPTING_TRAFFIC` y health UP, sin cambiar el jar ni contratos. [Fuente SpringApplication](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.1/core/spring-boot/src/main/java/org/springframework/boot/SpringApplication.java), [mensaje de disponibilidad](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.1/core/spring-boot/src/main/java/org/springframework/boot/availability/ApplicationAvailabilityBean.java).
4. **Terminación controlada tras la siembra:** se termina forzosamente el árbol Java después de confirmar disponibilidad, sin endpoint de apagado. El código de salida resultante de esa terminación no equivale a un fallo de carga; una salida espontánea, ERROR/excepción o tiempo agotado sí hacen fallar y no crean marcador.
5. **Simular no usa red ni lee secretos.** Cortafuegos enumera acciones exactas con `-Inventario`; sin inventario solo describe la consulta/operación pendiente. Retirada en simulación describe los destinos, sin afirmar que haya inspeccionado el estado remoto.
6. **PowerShell 7.2+ explícito** por las API .NET 6 utilizadas; entorno disponible 7.6.9. Se conserva UTF-8 sin BOM, CRLF en `.ps1`, LF en SQL/Markdown/JSON.
7. **Secretos separados, sin extracción del script existente.** El cálculo SCRAM se duplica con una nota para no cambiar `tools/preparar-entorno-v2.ps1`; la función privada admite sal explícita solo para contrastar el vector público RFC. Las llamadas operativas siempre generan sal aleatoria. Claves persistentes de 32 caracteres ASCII con mayúsculas, minúsculas y dígitos; JWT de 48 bytes Base64.
8. **Selección de suscripción verificable:** las operaciones de CLI exigen un UUID que debe coincidir con la suscripción activa; no cambian su configuración. Los nombres de recursos Azure son literales, y base/roles parametrizados solo aceptan el destino autorizado.
9. **No se borran secretos ni evidencias privadas al retirar.** Para reconstruir una demo vacía, Claude debe archivar/quitar el marcador local de siembra de la base anterior. El candado vacío permanece para evitar carreras; no contiene credenciales.

## Verificación ejecutada

Entorno observado: Windows/PowerShell **7.6.9**, cliente `psql (PostgreSQL) 18.6`. No se ejecutó el jar, JDK ni Azure CLI para consultar sus versiones. No se abrieron conexiones a PostgreSQL, ni local ni remoto.

```powershell
./tools/demo-azure/validar-local.ps1
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' --version
$PSVersionTable.PSVersion.ToString()
git diff --check
git diff --cached --check
```

Resultado final de la validación:

```text
OK: Sintaxis comun.ps1
OK: Sintaxis configurar-app.ps1
OK: Sintaxis cortafuegos.ps1
OK: Sintaxis preparar-base.ps1
OK: Sintaxis retirar.ps1
OK: Sintaxis sembrar.ps1
OK: Sintaxis validar-local.ps1
...
OK: SCRAM RFC 7677: firma del servidor
OK: SCRAM RFC 7677: verificación de la prueba del cliente
OK: Claves aleatorias cumplen longitud y composición
OK: SCRAM usa una sal nueva por cálculo
OK: cmd conserva comillas, paréntesis y @archivo
OK: Rechaza metacaracteres antes de lanzar cmd
Resultado: 33 comprobaciones correctas. Sin red, secretos privados ni objetos persistentes.
psql (PostgreSQL) 18.6
7.6.9
```

Las **33 comprobaciones** son: 7 análisis sintácticos con `Parser.ParseFile`, 5 simulaciones con ejecutables/secretos inexistentes, retirada sin confirmar y precedencia de Simular, simulación IP exacta/intervalo/duplicados, 9 casos de rechazo/idempotencia, 3 comprobaciones del entorno hijo, 4 de SCRAM/claves y 2 del paso por cmd.exe. Los rechazos esperados se capturan y forman parte de los casos correctos.

La simulación del inventario produce solo:

```text
SIMULAR: crear demo-v2-203-0-113-7 [203.0.113.7].
Reglas que faltaban: 1. Las demás se conservan.
```

SCRAM se contrasta con el vector público de [RFC 7677 §3](https://www.rfc-editor.org/rfc/rfc7677#section-3), verificando la firma del servidor y reconstruyendo la clave de cliente para comprobar StoredKey. No se realizó la prueba opcional de autenticación con PostgreSQL local: no se leyeron credenciales `.local/` ni se crearon objetos para ella. La compatibilidad de autenticación contra el servidor queda pendiente de la ejecución de Claude.

No se ejecutaron Maven, frontend ni E2E: el alcance solo contiene herramientas operativas y documentación, y la ejecución de la aplicación está expresamente prohibida en este encargo. Los SQL se revisaron estáticamente; no se presenta la validación PowerShell como ejecución o validación SQL en servidor.

Los `git diff --check` y `git diff --cached --check` anteriores a los commits terminaron sin salida ni errores. La auditoría final confirmó los 13 archivos autorizados en UTF-8 sin BOM, CRLF en los siete `.ps1` y LF en los demás; `git ls-files --eol` coincide con `.gitattributes`.

## Incidencias observadas y corregidas

**Acceso Git del sandbox.** El primer intento de lectura devolvió:

```text
fatal: detected dubious ownership in repository at 'D:/Proyectos/BarberTurno-v2-demo-codex'
```

Se repitieron las operaciones Git con escalación aprobada automáticamente, como autoriza el encargo. No se tocaron ACL, propietarios ni configuración global/local de Git. No hubo rechazo de aprobación.

**Herramienta de búsqueda:** `rg` no está en el PATH de esta sesión; la lectura inicial produjo:

```text
El término "rg" no se reconoce como nombre de un cmdlet, función, archivo de script o programa ejecutable.
```

Se utilizó `Get-ChildItem`/`Select-String`, limitado a los archivos documentales/de código correspondientes.

**Comillas cmd.exe:** el primer ensayo local con `ProcessStartInfo.ArgumentList` para `/s /c` falló:

```text
El proceso falló (código 1); salida suprimida por seguridad.
```

Se corrigió utilizando `ProcessStartInfo.Arguments` para la línea `/d /s /c` ya validada y entrecomillada. El resto de procesos usa `ArgumentList`. La repetición local produjo:

```text
Salida de la prueba de comillas: "prueba (local)" "@archivo"
```

La regresión y el rechazo de metacaracteres están incorporados a `validar-local.ps1`. No se llamó a `az`/`az.cmd` durante estas pruebas.

## Comprobaciones pendientes para Claude al ejecutar

- Confirmar la suscripción de la sesión, inventario real del grupo demo y plan Linux F1. No hay cambio de SKU ni creación de recursos en los scripts.
- Verificar en el servidor Azure 18 la concesión automática del creador y su `grantor`. Si Azure añade membresías distintas a las documentadas por PostgreSQL, la comprobación conservadora abortará: revisar el hallazgo antes de cambiar privilegios. Validar la creación de `btree_gist` como btadmin y la retirada de CONNECT/SET temporales. Si falta ADMIN sobre roles preexistentes, no se intentan apropiaciones ni cambios en roles Azure.
- Confirmar permisos y confianza TLS reales. psql utiliza las raíces DER verificadas y el PEM; JDBC `DefaultJavaSSLFactory` utiliza el almacén del JDK 21 local/Azure. No se incorpora ese PEM al jar ni se reduce la verificación TLS.
- Comprobar las variables en ejecución, Flyway con `bt_v2_demo`, conexiones con `bt_v2_demo_app` y el arranque de las dos barberías con el jar que se vaya a publicar. La lectura estática confirma `barberturno.ia.habilitada` en `IaPropiedades`/`ConfiguracionModeloIA` y los dos `ConditionalOnProperty` de los programadores WhatsApp: apagarlos no elimina los servicios que otros beans necesitan. No se ha ejecutado un contexto Spring para afirmarlo dinámicamente.
- Comprobar el mensaje de disponibilidad y health UP en la versión real del jar. Revisar únicamente el registro privado ya filtrado si falla la siembra; no imprimir contraseñas. El marcador solo se crea tras éxito y conserva el SHA-256 del jar.
- Revisar los ajustes existentes de la web app: se rechazan residuos conocidos de demo/reloj/configuración Spring/Java; los ajustes ajenos se conservan. Los TWA vacíos no se envían ni borran valores anteriores.
- Verificar las IP salientes actuales; al cambiar el conjunto se puede repetir cortafuegos. No se añade una regla global `0.0.0.0` ni se retiran reglas v1. La simulación offline depende del inventario proporcionado, no es una consulta del estado actual.
- Confirmar `/actuator/health` después de la publicación que ejecutará Claude. `healthCheckPath` se configura por `--generic-configurations @archivo`, la forma documentada para PowerShell. F1 permite health check, con las limitaciones de reemplazo de instancias de Free/Shared; no se escala el plan. [CLI webapp config](https://learn.microsoft.com/en-us/cli/azure/webapp/config?view=azure-cli-latest), [limitaciones health check](https://learn.microsoft.com/en-us/azure/app-service/monitor-instances-health-check#limitations).
- Para retirar, dejar terminar cualquier siembra, detener la app y cerrar las conexiones demo. Si la retirada falla tras parar la app, queda detenida y se puede reintentar. Un fallo de red puede dejar pasos parciales; los scripts no prometen atomicidad entre SQL/Azure. La eliminación del grupo solo procede si no hay recursos inesperados.

No se requiere cambiar reglas de negocio, contratos, DDL de migraciones ni diseño del producto. Los hallazgos para el arquitecto son el orden real de `Started`/runners, la concesión ADMIN automática y la necesidad de CONNECT administrativo temporal. La prueba remota y la publicación quedan expresamente a su cargo.

## Commits y límites

| Commit | Contenido |
|---|---|
| `7a12e0fdf2f5a6ec3fbed47c277d3267ab26ce47` | `feat(infra): preparar la base aislada de la demo con TLS y SCRAM [V2-DEMO]` |
| `6e00f5e0cdc78c6dfed6c12a0d2bcf123b42c140` | `fix(infra): preservar las comillas al invocar scripts cmd [V2-DEMO]` |
| `105a3b9f0e937ad899c92aafa0edebb39b39475d` | `feat(infra): automatizar siembra, configuración y retirada de la demo [V2-DEMO]` |
| Commit que añade este informe | `docs(infra): registrar la validación local de la demo Azure [V2-DEMO]`; su hash se entrega en la respuesta final y se obtiene con `git log -1 --format=%H -- docs/v2/pruebas/v2-demo-provision.md`. Se evita una referencia circular al hash del propio documento. |

**Merge: ninguno**, conforme al encargo específico de commits locales en esta rama. Sin push, remotos, etiquetas ni worktrees nuevos.

**Límites respetados:** solo archivos permitidos; sin lecturas de `.local/` de ningún worktree; sin Azure CLI, psql remoto, jar o servidores; sin acceso/modificación a datos v1; sin servicios/cargos nuevos; sin cambios a documentos de requisitos/arquitectura, AGENTS, CLAUDE, APF2 ni entregables. No se ha modificado ninguna ACL ni configuración de Codex. No quedan procesos de la tarea: no se arrancaron Java, backend, frontend ni PostgreSQL; los hijos cmd de prueba terminaron y fueron liberados. No hay secretos ni datos personales reales en los archivos o en la salida.
