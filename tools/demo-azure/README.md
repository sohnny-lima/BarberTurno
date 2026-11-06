# Demo v2 en Azure · Encargo 147

Scripts para que **Claude, con la sesión del responsable**, prepare y retire la demo autorizada. La validación de Codex es local; estos scripts todavía no han sido ejecutados contra Azure. No publican el jar ni crean grupos, planes o aplicaciones. Usan los tres recursos demo ya creados y el PostgreSQL compartido; no cambian su SKU ni configuran servicios adicionales.

## Destinos y requisitos

| Elemento | Destino fijo |
|---|---|
| Grupo demo | `rg-barberturno-v2-demo` |
| Plan | `plan-barberturno-v2-demo`, Linux F1 |
| Web app | `barberturno-v2-demo-202611` |
| Servidor compartido | `pg-barberturno-202610.postgres.database.azure.com:5432`, grupo `rg-barberturno-prod` |
| Base demo | `barberturno_v2_demo` |
| Propietario / Flyway | `bt_v2_demo` |
| Ejecución / RLS | `bt_v2_demo_app`, sin propiedad ni privilegios especiales |

PowerShell **7.2 o superior** (validado con 7.6.9), psql 18 y JDK 21. Los ejecutables tienen los valores predeterminados del encargo; pueden sustituirse con `-Psql`, `-Java` y `-Az`. Los parámetros `-Base`, `-RolMigraciones` y `-RolAplicacion` solo aceptan los valores de la tabla: fallan antes de leer secretos si se intenta cambiar de destino.

El responsable deja `pg-admin.secreto` (una línea) en `D:\Proyectos\BarberTurno\.local\demo-azure`. `-Secretos` permite otro directorio privado **fuera de este repositorio**. Los scripts no importan archivos `.env`, no leen `.local/coordinacion/` ni cambian permisos del sistema. Mantener el directorio privado y no activar transcripciones, depuración de CLI ni volcados de variables mientras se ejecutan.

Los comandos Azure exigen `-Suscripcion <UUID>` y verifican que coincide con la suscripción activa. Configurar app/cortafuegos comprueban además HTTPS y el plan F1 propio antes de escribir. No hay cambio de sesión ni de suscripción automático.

## Orden de ejecución del coordinador

Desde la raíz del worktree, en PowerShell 7. Los siguientes comandos **realizan operaciones remotas**, salvo la validación y las simulaciones.

```powershell
# Sin red ni lectura de secretos; ejecutable por Codex.
./tools/demo-azure/validar-local.ps1
./tools/demo-azure/preparar-base.ps1 -Simular
./tools/demo-azure/sembrar.ps1 -Simular
./tools/demo-azure/configurar-app.ps1 -Simular
./tools/demo-azure/cortafuegos.ps1 -Simular -Inventario ./tools/demo-azure/inventario-ejemplo.json
./tools/demo-azure/retirar.ps1  # Simula por defecto.

# Solo Claude/responsable: UUID comprobado en su sesión existente, sin cambiarla.
$suscripcionDemo = '<UUID de la suscripción autorizada>'

# 1. Descargar/verificar raíces, comprobar destino y preparar roles/base/extensión.
./tools/demo-azure/preparar-base.ps1

# 2. Cargar una vez, usando exactamente el jar que después revisará/publicará Claude.
./tools/demo-azure/sembrar.ps1 -Jar ./backend/target/barberturno-0.0.1-SNAPSHOT.jar

# 3. Autorizar las IP salientes de la demo; conserva las reglas existentes.
./tools/demo-azure/cortafuegos.ps1 -Suscripcion $suscripcionDemo

# 4. App Settings prod y siteConfig.healthCheckPath, sin mostrar los valores.
./tools/demo-azure/configurar-app.ps1 -Suscripcion $suscripcionDemo
# Opcional, conjuntamente: -TwaPaquete 'pe.ejemplo.demo' -TwaHuellas '<huellas>'
```

Después, Claude revisa y publica por su procedimiento autorizado el **mismo jar**. Esta carpeta no incluye un comando de despliegue. Verificar `https://barberturno-v2-demo-202611.azurewebsites.net/actuator/health` y la navegación de ambas barberías. La app publicada solo lleva `prod`; la carga `dev,demo` se ejecuta y detiene en el equipo del responsable.

## Qué hace cada paso

`preparar-base.ps1` comprueba psql 18, PostgreSQL 18, `btadmin` no superusuario con CREATEDB/CREATEROLE, atributos/membresías de los roles, propietarios y ACL existentes. Rechaza propietarios ajenos, concesiones adicionales, objetos fuera de la demo y cualquier propiedad del rol de ejecución. No corrige estados inesperados. Calcula SCRAM-SHA-256 localmente, sin enviar contraseñas en SQL. La contraseña administrativa va solo en `PGPASSWORD` del proceso psql; no se modifica el entorno del padre. Al volver a ejecutar, conserva los archivos de credenciales y solo crea los objetos ausentes.

Para `CREATE DATABASE ... OWNER`, PostgreSQL 16+ necesita SET sobre el propietario. Se concede temporalmente solo SET, con INHERIT/ADMIN falsos y `GRANTED BY btadmin`; se revoca en `finally`. También se concede CONNECT temporal a `btadmin` para crear `btree_gist` en la base después de revocar PUBLIC, y se retira al finalizar. PostgreSQL conserva su **concesión automática ADMIN al creador**, otorgada por el bootstrap, sin SET/INHERIT; un administrador no superusuario no puede revocarla. El informe final exige `sin_set_ni_herencia_temporal = t`, privilegios especiales falsos y bases propias 1/0.

`sembrar.ps1` conserva el hash SHA-256 del jar en `siembra-completa.json`. Si existe ese marcador, omite el jar; los runners originales también conservan lo ya creado. Un candado impide dos siembras simultáneas. El proceso usa un directorio nuevo vacío con un padre también nuevo: las importaciones relativas `./.local` y `../.local` de `dev` no encuentran archivos. El entorno heredado de Spring, BarberTurno, PostgreSQL y opciones Java se elimina antes de pasar las variables del hijo. No se activa reloj fijo. HTTP escucha solo en `127.0.0.1`, puerto aleatorio.

**El mensaje `Started` precede a los runners en Spring Boot 4.1.1.** Por eso se espera también el mensaje `ReadinessState ... ACCEPTING_TRAFFIC` y `/actuator/health = UP`, con sondas activadas únicamente en ese hijo. Solo entonces se detiene el árbol del proceso. Se capturan ambas tuberías sin mostrarlas; se falla por excepción, ERROR, salida espontánea o tiempo agotado. Antes de guardar el registro se buscan todas las contraseñas utilizadas, la administrativa y el JWT; si aparece un secreto no se guarda. La terminación es forzada tras confirmar la disponibilidad: no requiere cambios al jar ni un endpoint de apagado. Los temporales vacíos se retiran en `finally`; el archivo vacío del candado permanece para evitar carreras.

`configurar-app.ps1` establece roles separados, JWT de 48 bytes Base64, administrador ficticio, cookie segura, puertos 80, límites JVM del encargo, privacidad marcada `BORRADOR (demo; pendiente de P2-06)` e interruptores de IA/WhatsApp falsos. Los dos parámetros TWA son opcionales y se exigen juntos. Si están vacíos no se envían; tampoco se borran ajustes TWA de una ejecución anterior. Rechaza residuos peligrosos conocidos, como contraseña demo, reloj fijo u opciones externas de Spring/Java, antes de actualizar. Los App Settings van en JSON privado y `-o none`; `healthCheckPath` va en otro JSON mediante `--generic-configurations`. Ambos temporales se borran en `finally`.

`cortafuegos.ps1` consulta `outboundIpAddresses` y los intervalos existentes. Crea únicamente las IP sin cobertura, una regla exacta `demo-v2-<IP con guiones>`. No duplica IP ni cambia reglas homónimas con otro intervalo. Una IP ya cubierta por una regla v1 no crea regla demo ni se elimina al retirar. `-Simular` nunca consulta Azure: con `-Inventario` enumera el plan exacto del JSON; sin él describe la operación pendiente de inventariar. El ejemplo solo contiene direcciones reservadas para documentación. Un inventario solo se acepta en simulación.

## Certificados y archivos privados

Las dos raíces se descargan desde las URL oficiales, se comprueba la huella **SHA-256 del DER**, y solo después se guardan. Los archivos existentes se verifican nuevamente; el PEM combinado se reconstruye. Una raíz inesperada o caducada aborta. Fuentes y huellas en `comun.ps1`: [Azure CA](https://learn.microsoft.com/en-us/azure/security/fundamentals/azure-certificate-authority-details), [DigiCert](https://knowledge.digicert.com/general-information/digicert-trusted-root-authority-certificates) y [programa de raíces Microsoft](https://ccadb.my.salesforce-sites.com/microsoft/includedcacertificatereportformsft).

psql usa `verify-full` y ese PEM como `sslrootcert`. JDBC usa `verify-full` y `DefaultJavaSSLFactory`: la confianza procede del almacén de raíces del **JDK 21** del equipo o de Azure, no del PEM de psql. Claude debe comprobar ambas cadenas de confianza al ejecutar; no hay degradación a `require` o TLS sin verificar.

| Archivo en `-Secretos` | Uso |
|---|---|
| `pg-admin.secreto` | Aportado por el responsable; nunca se genera ni sobrescribe |
| `migraciones.secreto` / `aplicacion.secreto` | Contraseñas distintas de 32 letras/dígitos; se crean solo si faltan |
| `demo-cuentas.secreto` | Contraseña de las identidades ficticias de los runners |
| `admin-plataforma.secreto` | Contraseña de `plataforma-demo@ejemplo.test`, compartida por siembra/configuración |
| `jwt.secreto` | 48 bytes aleatorios Base64, persistentes |
| `digicert-g2.crt`, `microsoft-rsa-2017.crt`, `azure-postgresql-raices.pem` | Raíces verificadas, sin secretos |
| `siembra-*.log`, `siembra-completa.json`, `siembra-en-curso.lock` | Registro privado revisado, marcador con hash del jar y candado vacío |

No imprimir estos archivos ni incorporarlos a Git. No se rotan claves existentes automáticamente.

## Detener y retirar

Detener solo la aplicación demo, sin borrar nada (Claude/responsable):

```powershell
& 'D:\Herramientas\azure-cli\bin\az.cmd' webapp stop `
  --resource-group rg-barberturno-v2-demo --name barberturno-v2-demo-202611 `
  --subscription $suscripcionDemo --only-show-errors -o none

# Retirada: primero simulación sin red; después confirmación explícita.
./tools/demo-azure/retirar.ps1 -Simular
./tools/demo-azure/retirar.ps1 -Confirmar -Suscripcion $suscripcionDemo
```

La retirada comprueba primero inventario y dependencias. El grupo solo puede contener la app y el plan nombrados; una regla `demo-v2-*` debe coincidir con su IP exacta. Detiene la app, borra únicamente la base/roles demo, después sus reglas y finalmente el grupo demo. No termina sesiones SQL ni usa FORCE/CASCADE, DROP OWNED o REASSIGN OWNED. Si siguen abiertas conexiones de la demo, falla antes de borrar la base; esperar su cierre y reintentar. Una dependencia ajena bloquea la retirada. Los borrados no forman una transacción global: un fallo puede dejar una retirada parcial; se puede reintentar tras revisar el motivo. `-Simular` prevalece incluso junto a `-Confirmar`.

No se borran secretos, registros ni el marcador local. Si se vuelve a crear la demo desde cero tras retirarla, el coordinador debe **archivar/quitar `siembra-completa.json`** antes de sembrar la nueva base. No quitarlo para volver a cargar una base que conserva datos.

Los procesos externos fallidos muestran solo código de salida; su stderr no se reenvía porque podría contener SQL/configuración sensible. La siembra sí deja un registro privado previamente revisado. Interrumpir el proceso anfitrión de forma forzada puede impedir `finally`: antes de reintentar, Claude debe revisar temporales privados, concesiones propias de `btadmin` y procesos Java que hubiera iniciado ese anfitrión.

## Alcance de la validación

`validar-local.ps1` comprueba sintaxis mediante `Parser.ParseFile`, simulaciones con rutas de ejecutables/secretos inexistentes, rechazos de destinos, cobertura IP y aislamiento del entorno. El cálculo SCRAM se contrasta sin servidor con el vector de [RFC 7677 §3](https://www.rfc-editor.org/rfc/rfc7677#section-3), tanto firma del servidor como prueba del cliente. No crea objetos ni necesita credenciales PostgreSQL. No sustituye una comprobación real de los permisos Azure, migraciones, autenticación y arranque del jar. Evidencia y pendientes: `docs/v2/pruebas/v2-demo-provision.md`.
