# Despliegue de BarberTurno

T-33 · 04/10/2026. Un jar sirve Angular y la API en el mismo origen (DA-06, DA-12). El proveedor, dominio y certificado siguen pendientes del responsable (P-03); esta guía no publica el sistema.

## Requisitos y construcción

El servidor requiere Java 21, PostgreSQL 18 **en la última versión menor publicada** (DA-16), almacenamiento persistente y TLS en el proveedor o un proxy inverso. El entorno local de desarrollo usa la 18.6 desde el 04/10/2026 (T-38). Node 24.21.0 y npm solo se necesitan para construir el artefacto.

Desde una copia limpia, active Java 21 y Node 24.21.0 con fnm (DA-18). En Windows:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
& 'C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe\fnm.exe' env --use-on-cd --version-file-strategy recursive --shell powershell | Out-String | Invoke-Expression
fnm use
Set-Location backend
.\mvnw.cmd clean verify -Pcon-frontend
```

En Linux, con esas versiones activas, ejecute `cd backend && ./mvnw clean verify -Pcon-frontend`. `verify` necesita la base de pruebas preparada y su contraseña en el entorno. Ejecute una sola suite contra PostgreSQL a la vez.

El perfil usa `npm` del PATH: ejecuta `npm ci` y `npm run build` en `frontend/`, y copia `dist/frontend/browser/**` a `target/classes/static` durante `process-resources`. Los scripts de instalación denegados en `package.json` se conservan (DA-19). El resultado es `backend/target/barberturno-0.0.1-SNAPSHOT.jar`; Java basta para ejecutarlo. El build normal sin el perfil no ejecuta npm ni copia el frontend. Use `clean` al cambiar entre modalidades para eliminar recursos de un empaquetado anterior. La CI mantiene su flujo existente.

## Base y rol de aplicación

Como administrador PostgreSQL, en una instalación nueva, ejecute con `psql -X` y deténgase ante errores (`\set ON_ERROR_STOP on`):

```sql
SET password_encryption = 'scram-sha-256';
CREATE ROLE barberturno_prod LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\password barberturno_prod
CREATE DATABASE barberturno_prod OWNER barberturno_prod ENCODING 'UTF8' TEMPLATE template0;
REVOKE CONNECT, TEMPORARY ON DATABASE barberturno_prod FROM PUBLIC;
GRANT CONNECT ON DATABASE barberturno_prod TO barberturno_prod;
\connect barberturno_prod
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO barberturno_prod;
```

`\password` solicita el secreto sin incluirlo en el SQL ni el historial. No ejecute estas instrucciones sobre una base existente sin revisar sus permisos. El dueño de la base no es superusuario: puede aplicar Flyway y crear la extensión confiable `btree_gist`. Flyway necesita crear tablas, secuencias, restricciones y su historial; Hibernate solo valida el esquema. Esta opción de un único rol dueño simplifica la operación del proyecto. Separar un rol migrador y uno de ejecución con privilegios mínimos requiere preparar y verificar los permisos de las migraciones futuras.

`PUBLIC` no tiene CONNECT ni TEMPORARY (observación O-5 de T-01); el rol de aplicación recibe CONNECT explícito. El dueño conserva los privilegios inherentes a la propiedad. Restrinja `pg_hba.conf` al rol, base y origen autorizados, use autenticación SCRAM y limite el puerto de PostgreSQL a la red de la aplicación. Para una BD remota use TLS con validación del certificado, por ejemplo `sslmode=verify-full`, con el certificado raíz apropiado.

## Variables y arranque

Inyecte las ocho variables en el entorno del proceso o mediante el gestor de secretos del host. No use valores de ejemplo para un despliegue real ni incluya secretos en argumentos de línea de comandos, Git o registros.

| Variable | Valor requerido |
|---|---|
| `BT_DB_URL` | URL JDBC de la base de producción |
| `BT_DB_USER` | Rol de aplicación sin privilegios administrativos |
| `BT_DB_PASSWORD` | Secreto del rol |
| `BT_JWT_SECRET` | Base64 de al menos 32 bytes aleatorios decodificados |
| `BT_ADMIN_CORREO` | Correo del administrador inicial |
| `BT_ADMIN_PASSWORD` | Contraseña inicial de 8–72 caracteres con letra y dígito |
| `BT_ADMIN_NOMBRE` | Nombre del administrador inicial |
| `BT_COOKIE_SECURE` | `true` obligatorio |

```sh
java -jar /opt/barberturno/barberturno.jar --spring.profiles.active=prod
```

`prod` no importa `.local/` y rechaza variables ausentes/vacías, Base64 inválido, un secreto corto, cookies inseguras, reloj fijo o su combinación con `dev`, `test` o `demo`. El administrador se crea solo cuando aún no hay uno activo. Cambie la contraseña inicial por la API tras el primer ingreso y conserve las variables requeridas de forma protegida para los siguientes arranques.

## Proxy TLS y cabeceras

Termine HTTPS en el proxy/proveedor y redirija HTTP a HTTPS. La configuración usa `server.forward-headers-strategy=framework`. El proxy debe **sobrescribir**, no aceptar del cliente, `Forwarded` o `X-Forwarded-For`, `X-Forwarded-Proto` y `X-Forwarded-Host`. Mantenga el puerto del jar accesible solo al proxy (por ejemplo `--server.address=127.0.0.1` si están en el mismo equipo). Configure el host público y el esquema HTTPS correctos; no exponga un camino directo al backend que permita falsificar estas cabeceras.

Todas las respuestas de la cadena de seguridad, incluidos errores de la API y recursos SPA, incluyen:

```text
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'self'; form-action 'self'; object-src 'none'; frame-ancestors 'none'
```

HSTS es `max-age=31536000 ; includeSubDomains`: se emite en solicitudes seguras y en todo `prod`, incluso cuando el tramo proxy→jar es HTTP. Esto exige TLS público; active HTTPS en los subdominios antes de aplicar `includeSubDomains`. No se solicita inclusión en listas preload. En HTTP local con `dev`/`test` no se emite HSTS.

La excepción `style-src 'unsafe-inline'` permite los estilos que Angular 22 y Material insertan en tiempo de ejecución. Los scripts siguen limitados al mismo origen. `optimization.styles.inlineCritical=false` evita el script de critical CSS generado por el build. Como mejora futura, un nonce aleatorio por respuesta, propagado a Angular mediante `ngCspNonce`, permitiría sustituir la excepción de estilos; exige coordinar CSP, HTML y caché.

Las navegaciones GET que acepten HTML y no tengan punto en la ruta reenvían a `index.html`. GET/HEAD de archivos y rutas SPA son públicos. Los prefijos `/api`, `/actuator`, `/v3/api-docs` y `/swagger-ui` quedan excluidos, incluso sus raíces; la matriz de autorización y CSRF de la API se conserva. La SPA puede cargar con una contraseña temporal para mostrar su formulario de cambio, mientras la API mantiene la restricción.

## Servicio, salud y operación

Ejemplo Linux de `/etc/systemd/system/barberturno.service`, con cuenta de sistema sin acceso interactivo y un archivo de entorno protegido fuera del repositorio:

```ini
[Unit]
Description=BarberTurno
After=network-online.target
Wants=network-online.target

[Service]
User=barberturno
Group=barberturno
WorkingDirectory=/opt/barberturno
EnvironmentFile=/etc/barberturno/produccion.env
ExecStart=/usr/bin/java -jar /opt/barberturno/barberturno.jar --spring.profiles.active=prod --server.address=127.0.0.1
Restart=on-failure
RestartSec=10
SuccessExitStatus=143
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true

[Install]
WantedBy=multi-user.target
```

Proteja `/etc/barberturno/produccion.env` con permisos de lectura solo para administración; el archivo contiene las variables, no argumentos de Java. Instale el jar legible para la cuenta del servicio. Revise la ruta de Java 21 y active el servicio con `systemctl daemon-reload` y `systemctl enable --now barberturno`. Consulte su estado y registros mediante `systemctl status` y `journalctl -u barberturno`.

En Windows use el gestor de servicios aprobado por el responsable y una cuenta dedicada sin privilegios administrativos. Configure Java 21, directorio de trabajo, entorno protegido, arranque automático y reinicio ante fallos; verifique parada limpia y evite contraseñas en la línea de comandos. No se instala un gestor de servicios como parte de T-33.

El monitor externo deberá consultar por HTTPS `GET /actuator/health` cada minuto, con timeout y alertas tras fallos consecutivos. Respuesta saludable: HTTP 200 y exactamente `{"status":"UP"}`; un estado DOWN devuelve 503. Se expone únicamente health, sin detalles/componentes ni probes. Registre intervalos de caída y mantenimiento para medir el 99 % mensual de RNF-02: el ensayo local no acredita esa disponibilidad.

Swagger/OpenAPI están desactivados en `prod`; `server.error.*` omite mensajes internos, validaciones, excepciones y trazas. La consola de `prod` usa JSON ECS. Los logs de aplicación registran IDs y eventos sin correos completos, contraseñas, tokens ni cookies; las excepciones no controladas llevan mensajes sanitizados. Aplique retención y acceso limitado también a los registros del proxy y evite capturar cuerpos, cookies o cabeceras de autorización.

## Copias de seguridad

El [procedimiento de respaldo y recuperación](pruebas/recuperacion.md) de T-36 incluye `tools/respaldo.sh` (`pg_dump -Fc`, retención de 14 días, almacenamiento fuera del repositorio) y `tools/restaurar.sh` (base nueva, comparación exacta de tablas y filas), programación diaria con cron y Programador de tareas de Windows invocando Git Bash, y manejo de credenciales solo por entorno/archivo protegido. Pause escrituras mientras genera el resumen adjunto al dump. El ensayo local PostgreSQL 18.6 recuperó 9 tablas/56 filas en 3,389 s y retiró únicamente sus artefactos temporales; la meta es RPO 24 h / RTO 4 h. Instale y vigile la programación en el servidor elegido antes de considerarlo operativo; siguen pendientes el proveedor/TLS de P-03 y el monitor externo.
