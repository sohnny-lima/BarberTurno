# BarberTurno

Sistema web de reservas y turnos para una barbería de una sede en Huamanga, Perú. Proyecto académico de Integrador I: Sistemas Software (UTP). Arquitectura A1: Angular 22, Spring Boot 4.1.1 / Java 21 y PostgreSQL 18; toda la lógica de negocio reside en Java. Los datos de demostración son ficticios.

El repositorio y las bases locales están preparados (T-01). El backend ya arranca, ejecuta sus pruebas y ofrece health y Swagger en desarrollo (T-02). El frontend dispone del esqueleto Angular 22, con Material, locale peruano y protección XSRF (T-03).

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

`verify` ejecuta tanto `*Test` como `*IT` con el perfil `test` y PostgreSQL real. Reportes: `backend/target/surefire-reports/` y `backend/target/site/jacoco/index.html`; el umbral de cobertura de reservations y scheduling es ≥ 70 % (T-09). El perfil `demo` se activa junto con `dev`, pero sus datos se implementarán en T-32. En Linux/macOS use `./mvnw`; está marcado como ejecutable en Git.

### Obtener una sesión en desarrollo

Primero consulte `GET /api/auth/sesion` y conserve la cookie `XSRF-TOKEN` aunque la respuesta sea 401. Envíe su valor en `X-XSRF-TOKEN` junto con la cookie al registrar o iniciar sesión:

```http
POST /api/auth/registro
Content-Type: application/json
X-XSRF-TOKEN: <valor de la cookie XSRF-TOKEN>

{"nombre":"Cliente de prueba","correo":"cliente@ejemplo.test","telefono":"999111222","password":"ClaveCliente123","aceptaPrivacidad":true}
```

El ejemplo es exclusivamente ficticio. El registro responde 201 y fija `BT_SESION`; conserve ambas cookies para las siguientes solicitudes. `POST /api/auth/login` recibe `{"correo":"cliente@ejemplo.test","password":"ClaveCliente123"}` y responde 200. El JWT solo se acepta por cookie. `POST /api/auth/logout`, también con CSRF, responde 204 y borra `BT_SESION`, incluso si ya había caducado. Angular envía automáticamente XSRF en el mismo origen; las pantallas se implementan en T-12.

Para crear el administrador inicial, configure `BT_ADMIN_CORREO`, `BT_ADMIN_PASSWORD` (RN-25) y `BT_ADMIN_NOMBRE` en el entorno o en su archivo local privado antes de arrancar en `dev`. Solo se crea si no existe un ADMIN activo; no requiere teléfono ni cambio de contraseña. Si faltan valores en dev/test, se informa sin crear una cuenta. Nunca hay credenciales administrativas predeterminadas.

`BT_JWT_SECRET` es Base64 y debe decodificar al menos 32 bytes. Si se omite en dev/test se genera una clave aleatoria al arrancar: las sesiones caducan al reiniciar. En producción es obligatorio. Cinco fallos consecutivos bloquean la cuenta durante quince minutos, con mensajes genéricos.

Después de erify, puede arrancar el jar en desarrollo desde la raíz del repositorio:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar backend/target/barberturno-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Detenga el servidor con Ctrl+C. Para comprobar producción, desde `backend/`:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/barberturno-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Sin las variables obligatorias, ese comando falla explícitamente antes de abrir el pool; solo informa sus nombres. Los secretos locales no se cargan en `prod`. [Evidencias de T-02](docs/pruebas/t-02.md).

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

La página inicial contiene la barra Material «BarberTurno», un tema M3 generado desde navy `#173c4d` y teal `#087f8c`, y locale `es-PE`. HttpClient usa la cookie `XSRF-TOKEN` y la cabecera `X-XSRF-TOKEN`. El servidor ya emite la cookie y verifica la cabecera (T-10); las pantallas de negocio empiezan en T-12.

Calidad del frontend, con Node activo y desde `frontend/`:

```powershell
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
```

`npm run format` aplica Prettier únicamente al frontend. El build deja la SPA en `frontend/dist/frontend/browser/index.html`; dependencias, caché, cobertura y salida quedan ignoradas por Git. `package-lock.json` está versionado. [Evidencias de T-03](docs/pruebas/t-03.md).

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
