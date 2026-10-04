# BarberTurno

Sistema web de reservas y turnos para una barbería de una sede en Huamanga, Perú. Proyecto académico de Integrador I: Sistemas Software (UTP). Arquitectura A1: Angular 22, Spring Boot 4.1.1 / Java 21 y PostgreSQL 18; toda la lógica de negocio reside en Java. Los datos de demostración son ficticios.

El repositorio y las bases locales están preparados (T-01). El backend ya arranca, ejecuta sus pruebas y ofrece health y Swagger en desarrollo (T-02). El frontend permite registrarse, ingresar, cambiar la contraseña, editar el perfil y salir, con Material, navegación por rol y protección XSRF (T-12). El ADMIN también gestiona servicios y barberos, con altas, edición, estados y contraseña temporal (T-17).

## Entorno

| Herramienta | Requisito |
|---|---|
| Java | JDK 21; instalación de referencia: `C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot` |
| Backend | Spring Boot 4.1.1 y Maven Wrapper; no requiere instalar Maven |
| Frontend | Angular 22 y Node 24.21.0 con fnm; versión fijada en `.node-version` (DA-18) |
| Base de datos | PostgreSQL 18 en `localhost:5433`; el PostgreSQL 17 de `:5432` no se usa |
| Control de versiones | Git, rama principal `main` |

No se requiere Docker para las pruebas locales. La zona horaria del negocio es `America/Lima`. PostgreSQL local es 18.0, aceptado transitoriamente por DA-16; su actualización corresponde a T-38.

## Preparar PostgreSQL local

Desde la raíz del proyecto, con PostgreSQL 18 iniciado:

```powershell
psql -X -h localhost -p 5433 -U postgres -d postgres -f tools/db-local.sql
```

Si `psql` no está en el PATH, o apunta al cliente 17, use el ejecutable 18:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -h localhost -p 5433 -U postgres -d postgres -f tools/db-local.sql
```

Introduzca la contraseña de `postgres` cuando se solicite. Al crear el rol `barberturno` (o si ya existe sin contraseña), el script solicita dos veces una contraseña de desarrollo para ese rol mediante `\password`, sin mostrarla. Consérvela fuera del repositorio y úsela posteriormente como `BT_DB_PASSWORD`.

En la preparación de este equipo se generó una contraseña aleatoria y se conservó únicamente en `.local/barberturno.env`, ignorado por Git. Ese archivo contiene `BT_DB_PASSWORD` y el backend lo importa automáticamente solo con los perfiles `dev` y `test`, según DA-17. No comparta su contenido.

El script crea únicamente lo que falta: el rol de aplicación sin privilegios administrativos y las bases `barberturno` y `barberturno_test`, ambas propiedad de ese rol. Puede ejecutarlo dos veces para comprobar la idempotencia. Conserva los datos y las contraseñas existentes; si una base ya tiene otro dueño o el rol tiene permisos incompatibles, se detiene para su revisión. No crea tablas: las migraciones Flyway corresponden a T-06.

Para automatizar la autenticación administrativa, puede usar un archivo local `.local/pgpass.conf` (ignorado por Git) y definir `$env:PGPASSFILE` con su ruta absoluta. El formato de una entrada es `localhost:5433:postgres:postgres:<contraseña local>`. Restrinja el acceso al archivo a su usuario; no comparta ni versione su contenido.

## Configuración de la aplicación

Los perfiles `dev` y `test` importan las rutas opcionales `../.local/barberturno.env[.properties]` y `./.local/barberturno.env[.properties]`. Así el archivo se carga al ejecutar Maven desde `backend/` o el jar desde la raíz del repositorio. Use formato `CLAVE=valor`, sin `export` ni comillas. Las variables de entorno prevalecen sobre el archivo. `prod` exige las ocho variables de la tabla y no importa secretos locales; tampoco debe combinarse con `dev`, `test` o `demo`. La convención anterior `backend/.env.local` queda sustituida por DA-17.

La [plantilla de variables](tools/barberturno.env.example) separa desarrollo/pruebas de producción y contiene únicamente marcadores y datos ficticios. Las claves exclusivas de producción están comentadas: no las active en `.local/`, para evitar secretos de ejemplo y cookies `Secure` en desarrollo HTTP. Edite su archivo local sin sobrescribir la contraseña ya generada. En local conviene omitir `BT_DB_URL`: `dev` elige `barberturno` y `test` elige `barberturno_test`.

| Variable | Uso |
|---|---|
| `BT_DB_URL` | Desarrollo: `jdbc:postgresql://localhost:5433/barberturno`; pruebas: `jdbc:postgresql://localhost:5433/barberturno_test` |
| `BT_DB_USER` | `barberturno` |
| `BT_DB_PASSWORD` | Contraseña local del rol de aplicación |
| `BT_JWT_SECRET` | Secreto Base64 con ≥ 32 bytes decodificados; obligatorio en `prod` |
| `BT_ADMIN_CORREO`, `BT_ADMIN_PASSWORD`, `BT_ADMIN_NOMBRE` | Administrador inicial |
| `BT_COOKIE_SECURE` | `false` por defecto en dev/test; debe ser `true` en producción |

Los perfiles común, `dev`, `test`, `demo` y `prod` están configurados. La autenticación y la creación del administrador están implementadas (T-10); véase [arquitectura §9](docs/arquitectura.md#9-configuración-y-entornos).

## Arranque y pruebas del backend

Desde la raíz, fije el JDK 21 en la sesión PowerShell (sin modificar otras instalaciones):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location backend
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

La API usa `http://localhost:8080`; `GET /actuator/health` devuelve exactamente `{"status":"UP"}` sin detalles. En `dev`, Swagger está en `http://localhost:8080/swagger-ui.html` y OpenAPI en `/v3/api-docs`; ambos están deshabilitados por defecto en los demás perfiles. La autenticación usa JWT HS256 de ocho horas en la cookie HttpOnly BT_SESION y CSRF para toda escritura. GET /api/auth/sesion devuelve 401 sin sesión y emite XSRF-TOKEN; con sesión devuelve la identidad pública. Las demás rutas de API siguen cerradas hasta su tarea. No se crean sesiones HTTP ni se registran contraseñas o tokens.

`verify` ejecuta tanto `*Test` como `*IT` con el perfil `test` y PostgreSQL real. Reportes: `backend/target/surefire-reports/` y `backend/target/site/jacoco/index.html`; el umbral de cobertura de reservations y scheduling es ≥ 70 % (T-09). En Linux/macOS use `./mvnw`; está marcado como ejecutable en Git.

### Demostración reproducible (T-32)

Configure `BT_DEMO_PASSWORD` en el entorno del proceso o en el archivo local privado.
Elija una clave exclusiva de demostración que cumpla RN-25 (8–72 caracteres, letra y dígito,
máximo 72 bytes UTF-8). Se usa para las cuentas ficticias `cliente@ejemplo.test`,
`ana@ejemplo.test`, `luis@ejemplo.test`, `carlos@ejemplo.test` y `miguel@ejemplo.test`.
Si no hay ADMIN activo después del inicial de T-10, se crea `admin-demo@ejemplo.test` con esa clave.
El ADMIN inicial conserva su propia contraseña. No hay contraseña demo predeterminada;
si falta `BT_DEMO_PASSWORD`, se emite un WARN y se omite toda la carga.

Para una demostración académica local puede usar el ejemplo público
`$env:BT_DEMO_PASSWORD = 'DemoSustentacion2026'`. Este valor es ficticio y exclusivo de la
demo; para conservar una base con acceso privado, elija su propia clave antes de la primera carga.

Desde `backend/`, con JDK 21:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,demo' '-Dspring-boot.run.arguments=--barberturno.reloj-fijo=2026-09-28T09:00:00-05:00'
```

También puede arrancar el jar desde la raíz:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar backend/target/barberturno-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev,demo --barberturno.reloj-fijo=2026-09-28T09:00:00-05:00
```

Se importan los dos servicios, Carlos y Miguel, sus jornadas de lunes a sábado (09–13 y 14–18),
K1 del 01/10/2026 (16–17, Carlos, «Trámite») y las cinco reservas del prototipo con sus fechas
y estados exactos. Los identificadores son generados: la auditoría conserva `referenciaDemo`
con BT-100…BT-104 para reconocer sus equivalencias. La carga de estas instantáneas históricas,
incluida la completada del domingo 27/09, conserva sus datos sin ejecutar acciones del cliente.
Cada reserva importada recibe una auditoría y avisos al cliente y al barbero.

El marcador `cliente@ejemplo.test` evita repetir la carga y conserva las ediciones al reiniciar.
Use una base de desarrollo preparada para la demo: no se sobrescriben servicios existentes;
si su descripción, duración, precio o estado difieren, la carga falla y se revierte completa.
Los correos demo deben estar libres antes de la primera carga. No se borra ni reinicia información.

La comprobación de T-32 preparó la base local exclusiva `barberturno_demo_t32`, propiedad del
rol `barberturno`, y conservó allí los datos ficticios. Para usarla, añada al arranque del jar
`--spring.datasource.url=jdbc:postgresql://localhost:5433/barberturno_demo_t32`.
En otro equipo, un administrador de PostgreSQL puede prepararla con
`CREATE DATABASE barberturno_demo_t32 OWNER barberturno;`.
Consulte el catálogo después de completar el arranque: `health UP` puede responder
antes de que el runner confirme la transacción de carga.

El reloj fijo solo se admite en `demo` y `test`; en `dev` solo se ignora y en `prod`
la propiedad, incluso vacía, impide arrancar antes de abrir conexiones. Sin propiedad se usa
el reloj real: las fechas de 2026 se conservan, pero la disponibilidad descarta lo pasado.
Detenga el servidor con Ctrl+C al terminar.

La corrida XLSX tiene un escenario independiente de la demo del prototipo:
`CorridaManualIT` reproduce sus ocho pasos mediante la API con login y CSRF reales,
avanza el reloj para atender R102 y concilia tres reservas, ocho auditorías y ocho avisos
a clientes (Ana: cuatro; Luis: cuatro), más cinco avisos a Carlos por RN-15.
`DatosDemoRunnerIT` comprueba perfiles del reloj, ausencia de clave, rollback, conservación
del ADMIN, reinicios completos sin duplicados y disponibilidad del 01/10.

```powershell
.\mvnw.cmd '-Dtest=CorridaManualIT,DatosDemoRunnerIT' test
```

### Obtener una sesión en desarrollo

Primero consulte `GET /api/auth/sesion` y conserve la cookie `XSRF-TOKEN` aunque la respuesta sea 401. Envíe su valor en `X-XSRF-TOKEN` junto con la cookie al registrar o iniciar sesión:

```http
POST /api/auth/registro
Content-Type: application/json
X-XSRF-TOKEN: <valor de la cookie XSRF-TOKEN>

{"nombre":"Cliente de prueba","correo":"cliente@ejemplo.test","telefono":"999111222","password":"ClaveCliente123","aceptaPrivacidad":true}
```

El ejemplo es exclusivamente ficticio. El registro responde 201 y fija `BT_SESION`; conserve ambas cookies para las siguientes solicitudes. `POST /api/auth/login` recibe `{"correo":"cliente@ejemplo.test","password":"ClaveCliente123"}` y responde 200. El JWT solo se acepta por cookie. `POST /api/auth/logout`, también con CSRF, responde 204 y borra `BT_SESION`, incluso si ya había caducado. Angular envía automáticamente XSRF en el mismo origen; las pantallas de identidad ya están disponibles (T-12).

Para crear el administrador inicial, configure `BT_ADMIN_CORREO`, `BT_ADMIN_PASSWORD` (RN-25) y `BT_ADMIN_NOMBRE` en el entorno o en su archivo local privado antes de arrancar en `dev`. Solo se crea si no existe un ADMIN activo; no requiere teléfono ni cambio de contraseña. Si faltan valores en dev/test, se informa sin crear una cuenta. Nunca hay credenciales administrativas predeterminadas.

`BT_JWT_SECRET` es Base64 y debe decodificar al menos 32 bytes. Si se omite en dev/test se genera una clave aleatoria al arrancar: las sesiones caducan al reiniciar. En producción es obligatorio. Cinco fallos consecutivos bloquean la cuenta durante quince minutos, con mensajes genéricos.

Después de verify, puede arrancar el jar en desarrollo desde la raíz del repositorio:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar backend/target/barberturno-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Detenga el servidor con Ctrl+C. Para comprobar producción, desde `backend/`:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/barberturno-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Sin las variables obligatorias, ese comando falla explícitamente antes de abrir el pool; solo informa sus nombres. Los secretos locales no se cargan en `prod`. [Evidencias de T-02](docs/pruebas/t-02.md).

## Documentación Javadoc

Con JDK 21 y las dependencias Maven ya descargadas, desde `backend/`:

~~~powershell
.\mvnw.cmd -o javadoc:javadoc
~~~

El HTML completo queda en [backend/target/reports/apidocs/index.html](backend/target/reports/apidocs/index.html).
La versión 3.12.0 del plugin procede del parent de Spring Boot. La generación usa UTF-8,
sin enlaces externos, con `doclint=all` y `failOnWarnings=true`. `verify` ejecuta
`javadoc-no-fork` sin repetir fases previas del ciclo.

**Estado de T-40: Hecha.** `mvnw.cmd verify` genera el Javadoc con `doclint=all` y `failOnWarnings=true`: cualquier aviso hace fallar el build (DA-20). Los nueve constructores públicos que antes eran implícitos se declararon vacíos y documentados (corrección 042b), con el mismo bytecode. Detalle en la [evidencia T-40](docs/pruebas/t-40.md) y la [revisión](docs/pruebas/revision-t-40.md).

Desde la raíz, compruebe enlaces locales y pruebe el verificador sin dependencias (Node 20+ o 24):

~~~powershell
node tools/verificar-enlaces-html.mjs backend/target/reports/apidocs
node --test tools/fixtures-enlaces-html/verificar.test.mjs
~~~

La [muestra para la exposición](entregables/apf2-final/javadoc/LEEME.md) incluye fuentes exactas
por paquete y HTML de ReglasTemporales, CalculadoraFranjas y PoliticaTransiciones.
Regeneración desde `backend/`, seguida de comprobación desde la raíz:

~~~powershell
.\mvnw.cmd -o -P javadoc-muestra javadoc:javadoc
Set-Location ..
node tools/verificar-enlaces-html.mjs entregables/apf2-final/javadoc/html
~~~

## Arranque y pruebas del frontend

Node **24.21.0** se instala con fnm 1.38.1 para este proyecto, sin cambiar el Node global. La raíz contiene `.node-version`; `frontend/package.json` exige `^24.15.0`. Desde la raíz, en otra terminal PowerShell:

```powershell
$env:Path = 'C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe;' + $env:Path
fnm install 24.21.0
fnm env --use-on-cd --version-file-strategy recursive --shell powershell | Out-String | Invoke-Expression
fnm use
node -v
npm -v
Set-Location frontend
npm ci
npm start
```

La búsqueda recursiva permite que fnm encuentre `.node-version` también desde `frontend/`. Repita la activación de fnm en cada sesión; no use `nvm use`. Versiones verificadas: Node `v24.21.0`, npm `11.19.0`.

Según DA-19, npm 11 deniega por defecto los scripts de instalación y registra las denegaciones en `frontend/package.json` (`allowScripts`); para aprobar uno, primero se documenta el motivo en DA-19 y luego se ejecuta `npm install-scripts approve <paquete>` desde `frontend/`.

La SPA se sirve en `http://localhost:4200`. `npm start` carga `proxy.conf.json` desde `angular.json`: `/api` se reenvía a `http://localhost:8080`. Arranque el backend en `dev` desde `backend/` para usarlo. Sin una sesión, `GET http://localhost:4200/api/x` devuelve 401. Detenga cada servidor con Ctrl+C.

La página inicial contiene la barra Material «BarberTurno», un tema M3 generado desde navy `#173c4d` y teal `#087f8c`, y locale `es-PE`. HttpClient usa la cookie `XSRF-TOKEN` y la cabecera `X-XSRF-TOKEN`. El servidor emite la cookie y verifica la cabecera en toda escritura (T-10/T-11). La SPA carga la sesión antes de arrancar; abre /ingresar sin sesión y redirige a /reservar (CLIENTE) o /agenda (personal) tras el acceso. La reserva guiada y Mis citas están disponibles para el cliente; /agenda ofrece día/semana, atención y auditoría para BARBERO y ADMIN, con reprogramación y cancelación administrativa por permisos del servidor. /registro incluye el aviso de privacidad académico; /perfil permite editar nombre y teléfono, con correo de solo lectura. Una contraseña temporal obliga a /cambiar-password.

Calidad del frontend, con Node activo y desde `frontend/`:

```powershell
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
```

`npm run format` aplica Prettier únicamente al frontend. El build deja la SPA en `frontend/dist/frontend/browser/index.html`; dependencias, caché, cobertura y salida quedan ignoradas por Git. `package-lock.json` está versionado. [Evidencias de T-03](docs/pruebas/t-03.md).

Prueba de hora de Lima desde frontend, también con otra zona del proceso:

~~~powershell
$env:TZ = 'Europe/Madrid'
npm test -- --watch=false --include=src/app/core/tiempo/fecha-lima-pipe.spec.ts
Remove-Item Env:TZ
~~~

Recorrido HTTP de identidad (sin navegador), desde la raíz y con Node 24.21.0 activo:

~~~powershell
./frontend/tools/verificar-identidad-http.ps1
~~~

Requiere PowerShell 7, PostgreSQL 18 en 5433, la credencial local de desarrollo y puertos 8080/4200 libres. Inicia backend dev y ng serve, conserva cookies con WebRequestSession, comprueba CSRF, registro, perfil y logout; elimina únicamente la cuenta ficticia creada por id y correo y detiene sus servidores incluso si falla. Los logs locales del ensayo quedan en frontend/*.log, ignorados por Git. [Evidencia T-12](docs/pruebas/t-12.md).

Recorrido HTTP y visual de catálogo, desde la raíz y con Node 24.21.0 activo:

~~~powershell
pwsh -NoProfile -File ./frontend/tools/verificar-catalogo-http.ps1
~~~

Requiere PowerShell 7, PostgreSQL 18 en 5433, la credencial local de desarrollo, Edge instalado en su ruta habitual de Windows y puertos 8080/4200 libres. Por seguridad exige una base de desarrollo sin usuarios: así identifica el ADMIN inicial creado exclusivamente para este ensayo. Genera valores ficticios BT_ADMIN_* solo en el entorno del proceso y los restaura al terminar. Arranca backend dev y Angular, accede por el proxy, crea/lista/edita servicios y barberos, comprueba CSRF en las seis escrituras, desactiva/reactiva y verifica tablas y diálogos a 1280/360 px con Edge headless. La limpieza autorizada para este ensayo elimina solo sus IDs (servicio, perfil de barbero y las dos cuentas) y detiene sus árboles de procesos incluso si falla. Logs, capturas y perfil temporal del navegador quedan en frontend/tmp/, ignorado por Git; no se imprimen credenciales ni cookies. [Evidencia T-17](docs/pruebas/t-17.md).
Recorrido HTTP y visual de horarios (en la rama `tarea/T-18-horarios-bloqueos` mientras T-18 esté bloqueada por RA-02), con Node 24.21.0 activo:

~~~powershell
pwsh -NoProfile -File ./frontend/tools/verificar-horarios-http.ps1
~~~

Requiere los mismos recursos locales y la base dev sin usuarios que el recorrido de catálogo. Crea un ADMIN y un barbero ficticios, guarda y consulta la semana, comprueba el 400 por índice y ocho rechazos CSRF, crea/borra bloqueos individuales y en lote. Edge comprueba 1280/360 px y horas de Lima con la zona del navegador en Madrid. Limpia únicamente los IDs propios, restaura el entorno y detiene los servidores incluso si falla; los logs y capturas quedan ignorados. El 409 de jornada se cubre en JornadaIT mientras no exista la API pública de reservas. [Evidencia y bloqueo T-18/T-42](docs/pruebas/t-18.md).
Recorrido HTTP y visual de reserva/reprogramación (T-26), con Node 24.21.0 activo:

~~~powershell
.\frontend\tools\verificar-reserva-http.ps1
~~~

Requiere PowerShell 7, el JAR de `backend verify`, puertos 8080/4200 libres, Edge local y PostgreSQL 18 en 5433 con la base dev sin usuarios. La credencial de BD se carga solo en el proceso desde `.local/barberturno.env`. El ensayo genera `BT_ADMIN_*` ficticios en el entorno del proceso y usa el administrador inicial de T-10, sin modificar cuentas ni el archivo local. Arranca dev y el proxy Angular; registra un cliente, consulta disponibilidad, crea y reprograma con cookies/CSRF. Comprueba DA-21 con catálogo editado y desactivado, y la vista a 360/1440 px en Europe/Madrid, incluida la restauración tras login. Guarda capturas en `frontend/tmp/` (ignoradas por Git), limpia exclusivamente sus registros por ID —incluido el ADMIN inicial— y detiene sus servidores/navegadores en `finally`. [Evidencia de T-26](docs/pruebas/t-26.md).

Recorrido HTTP y visual de Mis citas y avisos (T-27), con Node 24.21.0 activo:

~~~powershell
.\frontend\tools\verificar-mis-citas-http.ps1
~~~

Requiere los mismos recursos locales y la base dev sin usuarios que el recorrido de T-26.
Crea un ADMIN inicial con valores ficticios solo en el proceso, prepara catálogo y jornada por API,
registra un cliente y crea su reserva. Edge en Europe/Madrid comprueba la tarjeta y el contador,
marca un aviso, cancela con versión y motivo, y marca todos: contador 1 → 0 → 1 → 0.
También verifica filtros, historial y vistas de 1440/360 px; HTTP comprueba CSRF y el resultado persistido.
Limpia exclusivamente sus IDs y detiene servidores y navegador en finally. Logs y capturas en
frontend/tmp/ (ignorados). [Evidencia T-27](docs/pruebas/t-27.md).

Recorrido HTTP y visual de Reportes (T-30), con Node 24.21.0 activo y después de `backend/mvnw.cmd verify`:

~~~powershell
.\frontend\tools\verificar-reportes-http.ps1
~~~

Requiere PowerShell 7, JDK 21, PostgreSQL 18 en 5433, el JAR de verify, Edge local, puertos 8080/4200 libres y dev sin usuarios. Crea el ADMIN inicial de T-10 con `BT_ADMIN_*` ficticias solo en el proceso, prepara servicio/barbero/jornadas como ADMIN y registra un CLIENTE que crea tres reservas por API (la reserva asistida sigue pendiente de T-31). El ADMIN cancela una, compara el resumen y las tres páginas del historial por el proxy con cuatro combinaciones de filtros y comprueba los rechazos por rol y rango. Edge en Europe/Madrid verifica `/admin/reportes`, seis estados y ceros, filtros de catálogo inactivo, barras etiquetadas, horas de Lima y adaptación a 1440/360 px. Limpia solo sus IDs, restaura el entorno y detiene los procesos en `finally`; logs y capturas quedan ignorados en `frontend/tmp/`. No ejecute otra suite PostgreSQL ni otro backend durante el ensayo. [Evidencia T-30](docs/pruebas/t-30.md).

E2E (`npx playwright test`) se incorporará en T-34.

## Medición del porcentaje de Java

Desde la raíz, con Node 20 o 24 (sin instalar dependencias):

```powershell
node tools/medir-java.mjs
node tools/medir-java.mjs --json
node tools/medir-java.mjs --escribir
node tools/medir-java.mjs --verificar-fixtures
node --test tools/verificar-medicion.test.mjs
```

El medidor de T-05 cuenta LOC físicas sin comentarios ni líneas vacías de Java, TS, HTML, SCSS/CSS y SQL en `backend/src`, `frontend/src`, `frontend/e2e` y `perf/`. Informa Java con y sin pruebas; la carga es un subconjunto de pruebas y se desglosa aparte. Las exclusiones aparecen con su motivo, incluida la paleta generada de Material. `--json` incluye el detalle por archivo; `--escribir` añade una sección fechada a la [medición acumulada](docs/pruebas/medicion-java.md), y puede combinarse con `--json`.

Las plantillas TS se cuentan como literales opacos, sin analizar sus interpolaciones; el analizador no interpreta regex TS ni dollar quoting SQL. El commit identifica HEAD; se cuenta el árbol de trabajo, por lo que conviene medir sin cambios en las fuentes. La interpretación final de RA-02 corresponde al docente (P-02). [Evidencia de T-05](docs/pruebas/t-05.md).

Recorrido HTTP y visual de Agenda (T-28), con Node 24.21.0 activo y después de backend/mvnw.cmd verify:

~~~powershell
.\frontend\tools\verificar-agenda-http.ps1
~~~

Requiere JDK 21, PostgreSQL 18 en 5433, el JAR de verify, Edge local, puertos 8080/4200 libres y dev sin usuarios para reconocer su ADMIN inicial. Crea ADMIN con BT_ADMIN_* ficticias solo en el proceso, barbero/servicio/jornadas y cliente por API; cambia la contraseña temporal del barbero por API. Comprueba las filas, motivos obligatorios de ADMIN y el BARBERO sin selector a 1440/360 px, con Edge en Europe/Madrid. Luego ajusta exclusivamente su reserva ficticia a un minuto antes del reloj real (inicio/fin y versión), consulta la agenda del día, inicia/completa por HTTP y consulta la auditoría como ADMIN. Verifica CSRF, rol y versión antigua. No promueve roles por SQL. Limpia por IDs y detiene sus procesos en finally; logs/capturas quedan en frontend/tmp/, ignorado. No ejecute otra suite PostgreSQL ni otro backend durante el recorrido. [Evidencia T-28](docs/pruebas/t-28.md).

## Reserva asistida y gestión de usuarios (T-31)

El ADMIN dispone de **Reserva asistida** en el menú: selecciona un cliente registrado por nombre o correo antes del servicio y la franja. El servidor valida el cliente y la disponibilidad, registra al administrador como creador y actor y confirma la reserva; el límite de tres reservas futuras solo se aplica al autoservicio.

En **Usuarios**, el ADMIN busca clientes o personal, restablece el acceso y activa o desactiva cuentas. La contraseña temporal se entrega en el diálogo existente una sola vez, con Copiar y cierre explícito; su titular debe cambiarla al ingresar. La desactivación revoca sesiones, conserva historia y protege al último ADMIN y la propia cuenta. El estado de acceso de un usuario y la agenda del profesional se gestionan en Usuarios y Barberos respectivamente.

Recorrido HTTP y Edge a 1440/360 px, tras el verify del backend y con Node 24.21.0 activado:

~~~powershell
.\frontend\tools\verificar-usuarios-http.ps1
~~~

Requiere JDK 21, PostgreSQL 18 en 5433, el JAR de verify, Edge local, puertos 8080/4200 libres y dev sin usuarios. Crea ADMIN mediante el inicializador T-10 con BT_ADMIN_* ficticias solo en el proceso y crea el resto por API, sin promoción SQL. Verifica CP-17/19, CSRF, actor y avisos, búsqueda, Copiar, estados y los cuatro pasos. Limpia exclusivamente sus IDs y detiene sus procesos en finally; logs y capturas sin contraseñas quedan en frontend/tmp/, ignorado. No ejecute otra suite PostgreSQL ni otro backend simultáneamente. [Evidencia T-31](docs/pruebas/t-31.md).

## Integración continua

El workflow [CI](.github/workflows/ci.yml) se ejecuta con cada push a cualquier rama y cada pull request. Dos trabajos independientes en Ubuntu verifican el proyecto:

- **Backend:** Temurin 21, caché de Maven y PostgreSQL `postgres:18`; ejecuta `./mvnw -B -ntp verify` con el perfil `test` y las variables de conexión del servicio efímero.
- **Frontend:** Node tomado de `.node-version`, caché de npm y `npm ci`, `npm run lint`, `npm run format:check`, `npm test -- --watch=false` y `npm run build`. `allowScripts` conserva la denegación de scripts de instalación (DA-19); el lock incluye los binarios opcionales de Linux.

El flujo solo requiere permiso de lectura del repositorio, cancela las ejecuciones anteriores del mismo evento y rama y tiene límites de tiempo por trabajo. Los dos pasos de checkout usan `persist-credentials: false` y no conservan credenciales después de descargar el código. No requiere secretos de GitHub ni el archivo `.local/barberturno.env`: la contraseña del servicio de pruebas es una credencial efímera exclusiva de la CI.

Para activarlo, el responsable debe completar **P-04**: crear el repositorio en GitHub, añadirlo como remoto y realizar el primer push desde la raíz:

```powershell
git remote add origin <URL-del-repositorio>
git push -u origin main
```

Esos pasos corresponden al responsable; esta entrega no configura ni publica el remoto. En GitHub, abra **Actions → CI → ejecución** para ver los trabajos. La sección **Artifacts** de esa ejecución permite descargar `backend-reportes` (JaCoCo y Surefire), que se publica incluso si falla la verificación cuando hay reportes disponibles, con retención de 7 días. Dentro del artefacto, abra `site/jacoco/index.html` para consultar la cobertura.

La ejecución real queda pendiente hasta que exista el remoto (P-04). [Evidencias de T-04](docs/pruebas/t-04.md).

## Organización y trabajo

- [AGENTS.md](AGENTS.md): responsabilidades, autonomía y convenciones comunes.
- [CLAUDE.md](CLAUDE.md): instrucciones del arquitecto.
- [Requisitos](docs/requisitos.md): RF, RNF, reglas y trazabilidad.
- [Arquitectura](docs/arquitectura.md): módulos, DDL, API, seguridad y concurrencia.
- [Tareas](docs/tareas.md): estado, dependencias y notas de cierre.
- [Material APF2](docs/apf2/): referencia original congelada; sus bytes y finales de línea se conservan sin normalización.
- [Evidencias](docs/pruebas/): comprobaciones y resultados.
- `tools/db-local.sql`: preparación idempotente de PostgreSQL local.

T-01 establece el primer commit en `main`. Para las tareas posteriores se usan ramas `tarea/T-XX-descripcion-corta`, commits Conventional Commits en español con `[T-XX]` e integración local mediante `git merge --no-ff`. No se configura ni se publica un remoto en esta etapa.

Los archivos de trabajo usan UTF-8 y LF; `.cmd`, `.bat` y `.ps1` usan CRLF según `.gitattributes`. `docs/apf2/` está exceptuado de la normalización y del formato automático para preservar el original.

## Prueba de carga (T-35, RNF-01)

Proyecto Maven independiente en `perf/`, fuera del build del backend y de la CI. Desde la raíz, con JDK 21:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\backend\mvnw.cmd -B -ntp -f perf/pom.xml test
cd backend
.\mvnw.cmd -B -ntp verify
cd ..
# Ensayo breve y después carga oficial; ejecutar siempre de forma secuencial.
.\perf\ejecutar-carga.ps1 -Usuarios 5 -Segundos 20
.\perf\ejecutar-carga.ps1
```

El script requiere PowerShell 7, puerto 8080 libre, PostgreSQL 18 en 5433, el rol `barberturno`, `BT_DB_PASSWORD`/`BT_JWT_SECRET` en el entorno o en `.local/barberturno.env` y acceso administrativo mediante `.local/pgpass.conf`. Rechaza una `barberturno_perf` preexistente; crea esa base, aplica Flyway arrancando el jar con `dev`, genera datos ficticios y ejecuta 50 usuarios sostenidos durante 300 s. El `finally` detiene su backend y elimina exclusivamente la base creada, también ante un fallo; restaura las variables del proceso. No ejecute `verify`, otro backend ni otra carga simultáneamente contra PostgreSQL.

Se genera una contraseña efímera solo en el entorno, o se acepta `BT_PERF_PASSWORD` externa conforme a RN-25; nunca se muestra ni se guarda en el manifiesto. El generador Java usa semilla 35 y empieza mañana en Lima; `-Fecha yyyy-MM-dd` permite repetir la agenda dentro del horizonte vigente. Crea 200 clientes, 10 barberos, 2 servicios, jornadas lunes–sábado y 30 días con 41,67 % de ocupación mediante reservas asistidas por un ADMIN ficticio (exentas del límite RN-20). Los clientes 100–199 quedan libres para autoservicio.

La mezcla 90/10 corresponde a recorridos: el 10 % incluye XSRF, login, consulta y creación en una franja recién vista. Los domingos pueden no ofrecer franjas y se registran aparte. Solo `409 FRANJA_NO_DISPONIBLE` y `422 LIMITE_RESERVAS_ACTIVAS` son OK esperados, comprobando estado **y** código; los demás fallos cuentan. Se exige p95 de disponibilidad ≤ 2000 ms, errores de disponibilidad y globales < 1 %, y al menos una creación real. La pausa de 1 s forma parte de cada usuario concurrente. Las propiedades de duración y concurrencia solo se reducen en el ensayo breve.

Para extraer un resumen JSON agregado de una ejecución finalizada, use `perf/resumir-carga.ps1 -Ejecucion perf/target/ejecuciones/<ejecución> -Informe perf/target/gatling/<informe> -Salida docs/pruebas/carga/resumen.json`.

Logs por ejecución y metadatos sin secretos en `perf/target/ejecuciones/`; HTML y estadísticas locales en `perf/target/gatling/`, todo ignorado por Git. El resumen compartible se conserva en [docs/pruebas/carga](docs/pruebas/carga/) y la evidencia en [T-35](docs/pruebas/t-35.md). Si no hay permiso para crear la base, el script falla sin tocar otra: la alternativa manual prevista es vaciar y usar exclusivamente `barberturno_test`, comprobar antes que no contiene datos que deban conservarse, adaptar explícitamente la protección de destino y documentarlo; no hay fallback automático que borre datos preexistentes.
