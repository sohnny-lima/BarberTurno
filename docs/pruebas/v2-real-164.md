# V2-REAL · Encargo 164 · Backend para uso real

Fecha: 18/11/2026. Rama `tarea/V2-REAL-uso-real`, base `716d260`.
Estado: **Hecha en local; pendiente de revisión**. Sin merge ni push, por
instrucción del encargo. Producción no intervenida.

Entorno comprobado: Windows/PowerShell, Temurin **21.0.8+9**, PostgreSQL y
psql **18.6** en 5433, Maven **3.9.16** (wrapper 3.3.4), Spring Boot **4.1.1**.
Aplicación: `barberturno_v2_app`; limpieza IT: propietario `barberturno_v2`,
con aserciones de `rolsuper=false` y `rolbypassrls=false` en cada conexión.

## Cambios

- `AdminInicialRunner`: con slug vacío o en blanco acredita únicamente la cuenta
  global. Con slug explícito conserva la resolución y propiedad anteriores.
- `AuthService.crearAdminInicial`: sin contexto no consulta ni crea membresías;
  conserva nombre, contraseña, estado, versión de sesión y revocaciones de las
  cuentas existentes. La tabla `usuario` es global, sin RLS; `EjecutorEnNegocio`
  añade `ejecutarSinNegocio` para delimitar explícitamente el ámbito vacío, sin
  consultar el directorio y restaurando el ámbito anterior incluso ante errores.
  Sigue rechazando un slug inexistente. No se necesita modificar RLS ni el DDL.
- `InstruccionesAsistente`: elimina la sede de relleno y conserva la hora de Lima.
- `tools/v2/limpiar-demo.sql`: bloque transaccional compartido por JDBC y psql;
  `limpiar-demo.psql` adapta los parámetros de psql sin imprimir sus valores.
- Pruebas de limpieza, administrador desde cero, conservación de credenciales y
  bajas, CSRF en el alta existente, perfiles de producción y texto de IA.
- README: configuración del administrador global y referencia a esta operación.

Javadoc actualizado: clase, constructor y `run` de `AdminInicialRunner`, y
`AuthService.crearAdminInicial` (ámbito opcional, permisos y conservación de bajas)
y `EjecutorEnNegocio.ejecutarSinNegocio` (RLS sin contexto, restauración y rechazo
si hay una transacción abierta).
No hay endpoints nuevos ni cambios de permisos o CSRF.

## Operación de limpieza

Preparación del coordinador: respaldo validado, detener la aplicación y retirar
el slug inicial de la configuración de producción antes del siguiente arranque.
Mantener `BT_ADMIN_*` configurados. El administrador conservado debe existir,
estar activo y ser `ADMIN_PLATAFORMA`. El script no cambia su contraseña ni
su rol. Esta implementación solo se ejecuta contra `barberturno_v2_test`.

Parámetros obligatorios de sesión (GUC, todos texto):

| GUC JDBC | Variable psql | Formato |
|---|---|---|
| `bt.limpieza_slugs` | `slugs` | Array JSON no vacío de slugs existentes |
| `bt.limpieza_admin` | `admin` | Correo de la cuenta conservada |
| `bt.limpieza_nombre` | `nombre` | Nombre visible, 1–100 caracteres; `Administración` |
| `bt.limpieza_modo` | `modo` | Exactamente `ensayo` o `aplicar` |

Ejemplo reproducible en PowerShell 7 con PostgreSQL 18 (valores ficticios):

```powershell
# Credencial de propietario por PGPASSFILE/PGSERVICE; nunca incluir contraseña.
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
& $psql -X -h localhost -p 5433 -U barberturno_v2 -d barberturno_v2_test `
  -v 'slugs=["barberturno","sur"]' -v 'admin=plataforma@ejemplo.test' `
  -v 'nombre=Administración' -v 'modo=ensayo' -f tools/v2/limpiar-demo.psql
# Revisar los recuentos del ensayo; aplicar con los mismos parámetros:
& $psql -X -h localhost -p 5433 -U barberturno_v2 -d barberturno_v2_test `
  -v 'slugs=["barberturno","sur"]' -v 'admin=plataforma@ejemplo.test' `
  -v 'nombre=Administración' -v 'modo=aplicar' -f tools/v2/limpiar-demo.psql
```

La conexión de producción la elige el coordinador explícitamente al operar;
este encargo no la abre. Usar el propietario de Flyway, sin superusuario ni
`BYPASSRLS`. No usar `--single-transaction`, `BEGIN` externo ni JDBC con
`autoCommit=false`: el único `DO` abre su transacción y termina en `ROLLBACK`
para ensayo o `COMMIT` para aplicar. Un error aborta todos los cambios.
Con JDBC, fijar los cuatro GUC con `SELECT set_config(?, ?, false)` en sentencias
separadas y ejecutar el contenido completo de `limpiar-demo.sql` en esa misma
conexión en autocommit. Cerrar la conexión al terminar.

Orden del script:

1. Valida parámetros y adquiere bloqueos de escritura sobre tablas participantes,
   con `lock_timeout=10s`; valida existencia de todos los slugs y del administrador.
2. Fija `app.negocio_id` por negocio; inspecciona todas las FK a `usuario` de las
   tablas del negocio. Cualquier correo fuera de `@ejemplo.test` aborta, incluso
   en bajas o referencias históricas. No imprime identidades ni slugs.
3. Cuenta filas por tabla. Prepara las condiciones de las políticas restrictivas:
   marca fichas demo como anonimizadas, mensajes pendientes como fallidos y
   turnos IA pendientes como descartados. Todo se revierte en ensayo/error.
4. Descubre todas las tablas con `negocio_id` en `pg_catalog`, ordena sus FK desde
   hijos a padres y borra solo las filas del negocio. Comprueba cero después de
   cada tabla. Un ciclo de FK o un borrado bloqueado por RLS aborta.
5. Borra los negocios solicitados. Recorre los ámbitos restantes y todas las FK
   a `usuario` para conservar identidades aún referenciadas, incluso bajo RLS.
   Borra exclusivamente cuentas `@ejemplo.test` huérfanas, salvo el administrador.
6. Renombra al administrador, emite los recuentos y confirma o revierte.

Las 22 tablas actuales descubiertas son: `auditoria_plataforma`,
`auditoria_reserva`, `barbero`, `bloqueo`, `canal_whatsapp`, `cliente`,
`codigo_vinculo_whatsapp`, `conversacion`, `evento_whatsapp_entrante`,
`invitacion`, `jornada`, `llamada_ia`, `membresia`, `mensaje_whatsapp`,
`notificacion`, `operacion_pendiente`, `reserva`, `servicio`, `turno_ia`,
`uso_ia`, `vinculo_cliente`, `vinculo_whatsapp_profesional`.
Además se tratan `negocio` y las identidades huérfanas de `usuario`.
`flyway_schema_history` queda intacta (comparación de todas sus filas en IT).
La fixture exige filas de cada tabla descubierta antes del borrado: una tabla
nueva vacía exige ampliar la preparación de la prueba.

La segunda ejecución con los mismos slugs **aborta sin cambios** porque ya no
existen. No se ignoran errores de destino. Una cuenta ficticia referenciada por
un negocio conservado o por una auditoría global se conserva: no es huérfana.

## Verificación e incidencias

Todas las ejecuciones Maven usan exclusivamente el cerrojo común y el JDK 21.
La forma `pwsh -File ... -- <argumentos>` del encargo falló antes de ejecutar Maven:

```text
No se puede procesar el parámetro porque el nombre del parámetro "" es ambiguo.
```

Se usa el parámetro explícito `-Argumentos` del mismo wrapper, sin modificarlo.
El sandbox rechazó crear el cerrojo externo:

```text
Access to the path 'D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock' is denied.
```

La ejecución escalada fue aprobada. Primer intento sin `backend/target`:

```text
Could not find a part of the path 'D:\Proyectos\BarberTurno-v2-real\backend\target\v2real-1.log'.
```

Se creó el directorio ignorado y se repitió. Sin ejecución de Maven fuera del
cerrojo ni modificación de ACL, propietarios, configuración o secretos.

Comando focal inicial:

```powershell
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' `
  -Worktree 'D:\Proyectos\BarberTurno-v2-real' -Registro 'target\v2real-1.log' `
  -Argumentos @('-Dtest=*Test,AuthIT,MembresiaIT,MotivoRevocacionIT,PlataformaIT,PlataformaProtegidaIT,DatosDemoRunnerIT,DatosDemoMultinegocioIT,LimpiezaDemoIT',
    '-Dsurefire.failIfNoSpecifiedTests=false','verify')
```

Primera focal: **1863 pruebas, 1 fallo, 4 errores, 0 omitidas**, `BUILD FAILURE`,
06:54 min. Las clases anteriores pasan; los cinco resultados negativos son de
`LimpiezaDemoIT`. Salidas relevantes:

```text
Tests run: 9, Failures: 1, Errors: 4, Skipped: 0 -- LimpiezaDemoIT
Expecting actual: "" to contain: ["Ensayo terminado: ROLLBACK", "tabla=mensaje_whatsapp"]
ERROR: el nuevo registro para la relación «usuario» viola la restricción «check» «usuario_cliente_completo»
ERROR: insert or update on table "membresia" violates foreign key constraint "membresia_negocio_fk"
Detail: Key (negocio_id)=(1) is not present in table "negocio".
Tests run: 1863, Failures: 1, Errors: 4, Skipped: 0
BUILD FAILURE
```

Correcciones: recoger los avisos del `Statement` JDBC; aportar teléfono y
consentimiento a la identidad CLIENTE ficticia; delimitar explícitamente el
trabajo global para evitar el negocio 1 implícito de `ProveedorFixturesLegados`
(componente de pruebas). No se debilitaron aserciones ni reglas. La primera
focal compiló las fixtures antes de estas correcciones.

Segunda focal: mismo wrapper y opciones, registro `target/v2real-2.log`,
`-Dtest=LimpiezaDemoIT,PerfilesDemoTest,InstruccionesAsistenteTest,ConfiguracionProduccionTest,EjecutorEnNegocioTest`.
Segunda focal: **36 pruebas, 0 fallos, 0 errores, 0 omitidas** (9 de limpieza).
`verify` termina en rojo por cobertura parcial del dominio, 01:28 min. No se
desactiva ni cambia ningún umbral; la suite completa resuelve esta comprobación.

```text
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
Rule violated for package pe.barberturno.reservations: lines covered ratio is 0.46, but expected minimum is 0.70
Rule violated for package pe.barberturno.customers: lines covered ratio is 0.32, but expected minimum is 0.70
Rule violated for package pe.barberturno.customers.dto: lines covered ratio is 0.00, but expected minimum is 0.70
Rule violated for package pe.barberturno.scheduling.dto: lines covered ratio is 0.52, but expected minimum is 0.70
BUILD FAILURE
```

Completa, sobre el commit `a437ab4`, mismo wrapper:

```powershell
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' `
  -Worktree 'D:\Proyectos\BarberTurno-v2-real' `
  -Registro 'target\v2real-3-completo.log' -Argumentos @('verify')
```

Git también necesitó escalación por la ACL preexistente del repositorio común:

```text
fatal: Unable to create 'D:/Proyectos/BarberTurno/.git/worktrees/BarberTurno-v2-real/index.lock': Permission denied
```

Escalación aprobada; sin cambios de ACL o configuración. `git diff --check`
antes de cada commit. Implementación:

- `f18bc9e4149162e0100ea3f06abee73a0cecdf6f`: administrador global, ámbito vacío
  explícito, texto IA y pruebas.
- `a437ab46566bc7158e6f35d96ee698f1fc354f40`: limpieza, pruebas PostgreSQL/HTTP
  y README.

Completa: **4028 pruebas, 0 fallos, 0 errores, 0 omitidas**, **BUILD SUCCESS**,
**47:57 min**, final 18/11/2026 09:09:52 -05:00. Incluye las 36 focales,
`AuthIT`, `MembresiaIT`, `MotivoRevocacionIT`, `PlataformaIT`,
`PlataformaProtegidaIT`, las dos cargas de demo, instalación/actualización y
`ConcurrenciaReservaIT` (31/31 con sus repeticiones). El log completo tiene
cero líneas `[WARNING]` y `[ERROR]`; Javadoc con doclint termina sin avisos.

```text
Tests run: 4028, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
--- javadoc:3.12.0:javadoc-no-fork (javadoc-verificacion) @ barberturno ---
BUILD SUCCESS
Total time: 47:57 min
mvnw rc=0
```

Cobertura final (líneas cubiertas/total; ramas cubiertas/total):

| Paquete | Líneas | Ramas |
|---|---:|---:|
| `reservations` | 479/481 | 360/370 |
| `scheduling` | 429/431 | 236/242 |
| `customers` | 98/100 | 30/36 |

No hay cambios de dominio; todos los umbrales activos se cumplen. La duración
supera la estimación del encargo; se conserva la suite y todas las repeticiones.
Registros locales ignorados: `backend/target/v2real-1.log`, `v2real-2.log` y
`v2real-3-completo.log`. No se versionan salidas con cuerpos o credenciales.

Cierre documental por asunto único
`docs: cerrar verificación del backend para uso real [V2-REAL]`; hash propio en
el informe final y resoluble con `git log`. **Merge: ninguno**.

Comprobación final: los PID propios 39088 (primera focal), 36984 (segunda focal)
y 36896 (completa) terminaron; cero JVM/Javadoc/psql propios del worktree y
cero escuchas asociadas. Consulta CIM en solo lectura escalada tras denegación
del sandbox; sin detener procesos ajenos. El cerrojo común ya no existe.
Sesiones de pruebas cerradas por `DirtiesContext`/`try-with-resources`;
la fixture restaura la fila inicial original y limpia exclusivamente la base
de pruebas. Ningún servidor, proceso o worktree creado queda pendiente.
Archivos cambiados con LF y sin BOM; `git diff --check` correcto y comparación
contra `716d260` sin cambios en las rutas protegidas. `T-03-registro` intacta.

## Límites y riesgos

No se modifica frontend, Android, requisitos, arquitectura, AGENTS, CLAUDE,
APF2 ni ninguna migración. Sin servicios externos, datos personales reales,
push, merge, etiquetas, cambio de rama/remoto o lectura de coordinación.
La prueba usa `example.org`, dominio reservado, exclusivamente para simular
correos fuera de `@ejemplo.test` en personas ficticias.

El script es destructivo solo en `aplicar`; su operación requiere respaldo y
ventana con la aplicación detenida. Los bloqueos son globales por tabla durante
la transacción, por lo que pueden bloquear temporalmente otros escritores.
Una base recién migrada sigue recibiendo el negocio de V100; esta tarea permite
arrancar después de retirarlo, sin alterar migraciones históricas.
