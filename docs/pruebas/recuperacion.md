# Respaldo y recuperación · RNF-09

T-36 · 04/10/2026. Copia diaria, RPO de 24 horas y RTO de cuatro horas. Procedimiento local ensayado con PostgreSQL 18.6; la programación del servidor definitivo depende de P-03. Esta entrega no instala tareas ni publica el sistema.

## Preparación y almacenamiento

Use PostgreSQL 18 actualizado y los clientes de esa misma versión. Los scripts usan shell POSIX y utilidades de GNU disponibles en Linux y Git Bash (`find -maxdepth/-mmin`, `mktemp`, `cmp`, `awk`). `PG_BIN` permite escoger los ejecutables; en Git Bash local debe ser `/c/Program Files/PostgreSQL/18/bin`. Nunca use el `psql` 17 del PATH de este equipo. Los scripts se versionan con permiso ejecutable.

Reserve un directorio **fuera del repositorio**, por ejemplo `/var/backups/barberturno` o `D:\Respaldos\BarberTurno`; limite su acceso a la cuenta operadora y administración. `respaldo.sh` rechaza directorios dentro de la copia del proyecto. `.gitignore` excluye `*.dump` como protección adicional. El dump contiene datos de aplicación: proteja también los resúmenes y cualquier réplica del almacenamiento. Mantenga los archivos de credenciales fuera de Git, con modo 0600 en Linux y ACL de acceso restringido en Windows.

Configure `PGHOST`, `PGPORT`, `PGUSER` y `PGPASSFILE` (preferido) o `PGPASSWORD` **en el entorno**. Los scripts nunca reciben contraseñas por argumentos y usan `-w` para fallar en vez de solicitar entrada. No use `set -x`, `sh -x` ni registre el entorno. En Git Bash, `PGPASSFILE` para clientes nativos de Windows debe señalar un archivo con ruta Windows, por ejemplo `D:/Operaciones/pgpass.conf`. La cuenta de aplicación puede hacer el dump; crear una base requiere una cuenta con `CREATEDB` o el administrador.

## Copia

Pause las escrituras durante todo el respaldo y la comparación posterior; por ejemplo, detenga el servicio de la aplicación en una ventana de mantenimiento. `pg_dump` produce su propia instantánea consistente, pero los conteos auxiliares se toman antes y después en transacciones separadas. El script rechaza cambios en los conteos; eso no detecta actualizaciones o sustituciones de filas que mantengan el mismo número. La pausa evita que el resumen describa otra instantánea. Después del dump, reanude siempre el servicio, también ante errores. Durante la interrupción registre los turnos manualmente y concílielos como ADMIN por reserva asistida, según arquitectura §11.

```sh
export PGHOST=localhost PGPORT=5433 PGUSER=barberturno
export PG_BIN='/c/Program Files/PostgreSQL/18/bin'
# PGPASSFILE o PGPASSWORD ya debe estar en el entorno protegido.
./tools/respaldo.sh barberturno_demo_t32 /d/Respaldos/BarberTurno
```

Resultado: `<base>-<AAAAMMDD-HHMMSS>.dump` (fecha **UTC**) y `<dump>.resumen.tsv`. El formato es `pg_dump -Fc`. El resumen guarda versión de formato y, por cada tabla de usuario, esquema/nombre en hexadecimal UTF-8 y **COUNT(*) exacto**, ordenados por esquema/nombre con intercalación C. Incluye `flyway_schema_history`; excluye catálogos internos. En esquemas particionados incluye tablas padre y particiones, comparadas individualmente. No almacena filas, credenciales ni datos personales en el resumen.

Solo se publica un dump después de salida cero, archivo no vacío y conteos estables. El bloqueo por base y directorio evita dos respaldos simultáneos; si el equipo se apaga, revise manualmente el directorio `.<base>-respaldo.lock` antes de retirarlo. Una colisión de nombre falla sin sobrescribir. La retención se ejecuta **después de un respaldo correcto**: elimina únicamente archivos regulares del patrón de esa base, directamente en ese directorio, con modificación de más de 20160 minutos (14 días), y su resumen asociado. No desciende ni sigue enlaces; conserva otras bases y patrones. Las fechas de modificación deben mantenerse al copiar respaldos. Si un paso falla, el proceso devuelve un estado distinto de cero; consulte el registro y corrija la causa antes de repetir.

## Programación y vigilancia

En Linux prepare fuera de Git `/etc/barberturno/respaldo-diario.sh`, legible/ejecutable solo por administración; ajuste las rutas y el nombre real del servicio. Ejemplo sin secretos:

```sh
#!/bin/sh
set -eu
umask 077
export PGHOST=localhost PGPORT=5432 PGUSER=barberturno_prod
export PGPASSFILE=/etc/barberturno/pgpass
export PG_BIN=/usr/lib/postgresql/18/bin
systemctl stop barberturno
trap 'systemctl start barberturno' 0
trap 'exit 1' HUP INT TERM
/opt/barberturno/tools/respaldo.sh barberturno_prod /var/backups/barberturno
```

Prográmelo en el crontab de la cuenta autorizada a detener/arrancar el servicio, a las 02:00 del reloj del host:

```cron
0 2 * * * /etc/barberturno/respaldo-diario.sh >> /var/log/barberturno-respaldo.log 2>&1
```

En Windows prepare fuera de Git `D:\Operaciones\respaldo-diario.ps1`, con rutas ajustadas y el servicio aprobado en T-33. Ejemplo:

```powershell
$ErrorActionPreference = 'Stop'
$env:PGHOST = 'localhost'
$env:PGPORT = '5433'
$env:PGUSER = 'barberturno'
$env:PGPASSFILE = 'D:/Operaciones/pgpass.conf'
$env:PG_BIN = '/c/Program Files/PostgreSQL/18/bin'
Stop-Service -Name BarberTurno
try {
    & 'C:\Program Files\Git\bin\bash.exe' 'D:/Aplicaciones/BarberTurno/tools/respaldo.sh' barberturno 'D:/Respaldos/BarberTurno'
    if ($LASTEXITCODE -ne 0) { throw 'Falló el respaldo diario.' }
} finally { Start-Service -Name BarberTurno }
```

En **Programador de tareas**, cree una tarea diaria a las 02:00 con una cuenta dedicada con los permisos necesarios; acción `powershell.exe`, argumentos `-NoProfile -NonInteractive -File "D:\Operaciones\respaldo-diario.ps1"`, directorio inicial `D:\Operaciones`. Configure ejecución aunque no haya sesión iniciada, ejecución al recuperar un horario perdido, reintento ante fallo y **no iniciar otra instancia**. Git Bash se invoca directamente desde el wrapper. No añada credenciales a la acción. Verifique el código de última ejecución, la presencia/tamaño del dump y el arranque del servicio después de un fallo. La contraseña de ejecución, si el programador la requiere, se introduce en su interfaz protegida.

Revise cada día el estado y la antigüedad del último respaldo correcto; alerte y repita inmediatamente si falla. Una tarea instalada por sí sola no acredita el RPO. Puede ejecutar cada 12 horas para disponer de margen ante un fallo; la retención permanece en 14 días. Ensaye periódicamente la restauración completa, registre tiempos y vigile espacio libre. El almacenamiento externo al repositorio en el mismo equipo no cubre la pérdida del disco: el responsable debe decidir dónde conservar una segunda copia protegida, sin contratar servicios como parte de T-36.

## Restauración

Escoja un destino **nuevo** y mantenga la aplicación detenida mientras compara el origen, si usa esa modalidad. La base nueva se crea con dueño indicado, `template0` y la codificación/intercalación predeterminada del clúster. Se revocan CONNECT/TEMPORARY de PUBLIC y se concede CONNECT al dueño, conforme al procedimiento de despliegue; otros roles que deban conectarse requieren revisión explícita. Configure el clúster de recuperación según el entorno del despliegue. No se crean roles ni se restaura configuración global del clúster; el rol dueño debe existir.

```sh
# PGUSER/PGPASSFILE autentican al creador en postgres; sin PGPASSWORD de otro rol.
export PGUSER=postgres
unset PGPASSWORD
# Si el creador no se autentica en la base nueva, configure en el entorno:
# PGRESTORE_USER y PGRESTORE_PASSWORD o PGRESTORE_PASSFILE para el dueño.
./tools/restaurar.sh /d/Respaldos/BarberTurno/barberturno_demo_t32-AAAAMMDD-HHMMSS.dump barberturno_restore barberturno barberturno_demo_t32

# Si el origen se perdió, omita el cuarto argumento y conserve el resumen:
./tools/restaurar.sh /d/Respaldos/BarberTurno/barberturno_demo_t32-AAAAMMDD-HHMMSS.dump barberturno_restore barberturno
```

`PGMAINTENANCE_DB` cambia la base administrativa (por defecto `postgres`). `PGRESTORE_USER`, `PGRESTORE_PASSWORD` y `PGRESTORE_PASSFILE` son opcionales y solo afectan la restauración/conteos posteriores. Así el ensayo usa la entrada administrativa de `.local/pgpass.conf` para `CREATE DATABASE` y el rol dueño para conectarse al destino, sin necesitar una entrada administrativa por cada base.

El script valida el dump antes de crear la base, ejecuta `CREATE DATABASE` sin sobrescribir y restaura con `--no-owner --role=<rol> --exit-on-error --single-transaction`. Compara **el conjunto de tablas y cada cantidad de filas**, no solo su suma: con el origen actual si se indica o con el resumen adjunto. Un fallo devuelve código distinto de cero y deja la base nueva para diagnóstico; no contiene `DROP`, `--clean` ni eliminación automática de datos. Un administrador decide cómo retirar ese destino fallido antes de repetir. Una restauración correcta tampoco elimina el dump ni cambia la aplicación de base: revise permisos, arranque/health y acceso por los tres roles antes de hacer el cambio operativo. Esa promoción del servicio no se ejecutó en el ensayo académico.

## Ensayo local cronometrado

Comando reproducible con PowerShell 7, desde la raíz, sin servidores ni suites PostgreSQL concurrentes:

```powershell
.\tools\ensayar-recuperacion.ps1
```

El script carga únicamente `BT_DB_PASSWORD` de `.local/barberturno.env` en el entorno, sin imprimirla; usa los clientes 18.6, `barberturno_demo_t32` como origen y `barberturno_restore` como único destino permitido. Rechaza un destino previo. El administrador crea la base y el rol `barberturno` restaura y cuenta. Guarda temporalmente fuera del repositorio, comprueba origen y resumen, retira el primer destino y repite la restauración sin indicar el origen; comprueba también los permisos de PUBLIC. Al final elimina solo la base creada y los archivos propios; restaura las variables del proceso.

Resultado final del 04/10/2026 (Lima; nombre UTC 05/10): dump de **29421 bytes**, respaldo **1,442 s**, respaldo + restauración + comparación del origen **3,389 s**, ensayo completo con comparación adicional, rechazo del destino existente y segunda restauración usando el resumen **14,543 s**. RTO local ≤ 4 h cumplido; el volumen de esta demo no permite extrapolar esos tiempos a una base mayor. La copia diaria está documentada para instalarse en el servidor elegido; no se dejó una tarea programada local.

| Tabla | Origen | Restaurada |
|---|---:|---:|
| auditoria_reserva | 5 | 5 |
| barbero | 2 | 2 |
| bloqueo | 1 | 1 |
| flyway_schema_history | 1 | 1 |
| jornada | 24 | 24 |
| notificacion | 10 | 10 |
| reserva | 5 | 5 |
| servicio | 2 | 2 |
| usuario | 6 | 6 |
| **Total: 9 tablas** | **56** | **56** |

El rechazo real al segundo intento fue `ERROR: la base de datos «barberturno_restore» ya existe` (código 3); el destino conservó sus conteos. Limpieza comprobada: destino ausente y dump/resumen/directorio temporal retirados. No se escribieron `barberturno`, `barberturno_test`, otras bases ni el origen demo. Los datos del ensayo son ficticios.

Pruebas de retención y errores: `sh tools/verificar-respaldo.sh`, **33/33**, con binarios simulados y archivos temporales: antiguo de 15 días eliminado junto con su resumen, reciente de 13 días conservado, otra base/patrón/extensión/directorio conservados, errores sin publicar respaldo ni ejecutar retención, cambio del origen durante la copia, exclusión del repositorio, bloqueo/colisión, ausencia de resumen, destino existente, discrepancias de filas/tablas y restauración fallida. Evidencia completa y hashes en [T-36](t-36.md).
