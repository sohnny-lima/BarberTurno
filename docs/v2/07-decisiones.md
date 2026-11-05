# BarberTurno v2 · 07 · Decisiones técnicas y alternativas

> Estado: **propuestas** (21/10/2026) hasta que el responsable apruebe la especificación. Registro con el mismo criterio que la arquitectura v1 §13; los ID `V2-DA-xx` son estables. Las decisiones de v1 (DA-01…DA-29) siguen vigentes salvo que una de estas las sustituya.

### V2-DA-01 · Rama de integración `v2` y worktree en el mismo repositorio
- **Decisión:** v2 se desarrolla en este repositorio, en una rama de larga vida **`v2`** creada desde `main` (`efd52aa`), con un **worktree** en `D:\Proyectos\BarberTurno-v2`. Las tareas usan ramas `tarea/V2-NN-descripcion` desde `v2` y se integran en `v2` con `merge --no-ff`. `main` sigue siendo v1 académica en freeze: sus correcciones se integran hacia `v2` con `git merge main` (nunca al revés hasta un corte aprobado). Se propone la etiqueta `v1.0-apf3` sobre `3bcd5d0` (requiere permiso, P2-01).
- **Por qué:** conserva la historia y la trazabilidad de v1 (v2 parte de su código y de sus pruebas), una sola CI (`ci.yml` se ejecuta en cualquier rama), cero coste y la entrega académica intacta. El worktree evita cambiar de rama en la carpeta que usan VS Code, las sesiones de CP-11 y los scripts de v1, y separa `backend/target` (la compilación del JDT LS de VS Code, observación O-1, no pisa la de v2).
- **Alternativas:** trabajar en `main` (rompe el freeze y mezcla v2 con la versión entregada); repositorio nuevo (pierde la historia, duplica CI, documentación y scripts; solo se justificaría si v2 debe ser **privado**, P2-02); *fork* (mismas desventajas sin ventajas en un proyecto individual).
- **Consecuencias:** al subir `v2` se ejecutará la CI con el workflow de esa rama; v2 mantiene su propio `docs/v2/` y no modifica `docs/apf2/` ni `entregables/`.

### V2-DA-02 · Base compartida con `negocio_id`, FK compuestas y RLS `FORCE`
- **Decisión:** una base PostgreSQL y un esquema para todos los negocios; `negocio_id NOT NULL` en todas las tablas operativas; `UNIQUE (negocio_id, id)` en los padres y FK compuestas en los hijos; RLS `ENABLE` + `FORCE` con política `negocio_id = app_negocio_actual()` en lectura y escritura; filtro explícito por negocio también en los servicios.
- **Por qué:** tres barreras independientes (servicio, FK compuestas, RLS) con un único esquema que migrar; las restricciones `EXCLUDE` y el protocolo de bloqueos de v1 funcionan sin cambios; coste operativo mínimo para un desarrollador y un servidor B1. La auditoría 075 mostró que solo filtrar en repositorios no basta (métodos heredados de `JpaRepository`).
- **Alternativas:** *esquema por negocio* (N migraciones de Flyway, `search_path` por conexión, consultas de plataforma entre esquemas, y la misma necesidad de autorizar; mejor aislamiento lógico, peor operación); *base por negocio* (coste y *pools* por negocio, inviable en Azure for Students); *discriminador sin RLS* (una sola barrera: un olvido filtra datos); *Hibernate `@TenantId`* solo (no cubre SQL nativo ni JDBC; se puede añadir más adelante como comodidad, no como barrera).
- **Consecuencias:** las pruebas de aislamiento se conectan con el rol de la aplicación; los trabajos fijan el negocio explícitamente; los índices empiezan por `negocio_id`.

### V2-DA-03 · Identidad global, membresías y ficha de cliente por negocio
- **Decisión:** `usuario` = persona con credenciales (correo único global) y `rol_plataforma`; `membresia (negocio, usuario, rol)` para el personal; `cliente` = ficha **por negocio**, con `usuario_id` (cuenta web) y/o `wa_id` (WhatsApp) opcionales; `barbero` = perfil profesional por negocio con `nombre_visible` local.
- **Por qué:** una persona puede estar en varios negocios con roles distintos (requisito); el cliente de WhatsApp no necesita cuenta ni contraseña; cada negocio solo ve a sus clientes; las reglas del cliente (RN-04, RN-20) quedan por negocio sin cruzar agendas.
- **Alternativas:** cuentas separadas por negocio (login ambiguo, una persona con N contraseñas); cliente = usuario global compartido (expone a un negocio la relación del cliente con otros y obliga a cuenta para WhatsApp); `negocio_id` en `usuario` (no admite varios negocios).
- **Consecuencias:** V2-RN-16 (el negocio no edita identidades compartidas); el bloqueo ① pasa a la ficha; migración de v1: cada `usuario` CLIENTE genera su ficha con el **mismo id**, de modo que `reserva.cliente_id` no cambia de valor (`08-pruebas-migracion-reversion.md` §6).

### V2-DA-04 · Negocio identificado por la ruta y autorizado en cada petición; el JWT no cambia
- **Decisión:** todas las operaciones de negocio van bajo `/api/n/{slug}/…`; un filtro resuelve el negocio y la relación del actor con él en cada petición; el JWT conserva `sub`, `tv` (y `rol` deja de usarse para autorizar).
- **Por qué:** el identificador del navegador solo selecciona; la autorización es la membresía o la ficha revalidada (V2-RN-02); varias pestañas con negocios distintos; revocación inmediata; sin reemitir cookies ni nuevos estados de sesión. La revalidación por petición ya existe (`UsuarioJwtConverter`): se amplía la misma consulta.
- **Alternativas:** negocio activo como *claim* del JWT con un endpoint de cambio (requiere reemitir, una sola pestaña coherente y revalidar igualmente); cabecera `X-Negocio` (equivalente a la ruta pero invisible en URL y enlaces); subdominio por negocio (DNS y certificados comodín, cookies por subdominio; aplazado para una marca propia de cada barbería).
- **Consecuencias:** las rutas de v1 sin prefijo se mantienen temporalmente para el negocio único y se retiran en V2-07; el frontend pasa a rutas `/n/{slug}/…`.

### V2-DA-05 · Negocio = barbería independiente de una sede; sin sucursales en el MVP
- **Decisión:** `negocio` modela una barbería independiente con una sola sede, como v1. Las sucursales (un negocio con varias sedes) se aplazan.
- **Por qué:** ninguna barbería objetivo lo necesita aún y añadiría un nivel a horarios, personal, agenda, disponibilidad, reportes y permisos.
- **Cómo se añadiría:** `sede (negocio_id, id, nombre, dirección)`, `sede_id` en `barbero` y `reserva` (FK compuesta `(negocio_id, sede_id)`), filtro por sede en disponibilidad y reportes; el aislamiento sigue siendo por `negocio_id`. Dos barberías distintas que comparten dueño son **dos negocios** con el mismo propietario, no sucursales.

### V2-DA-06 · Contexto de RLS fijado al comenzar cada transacción
- **Decisión:** un gestor de transacciones (o el mecanismo equivalente que Codex verifique en Spring 7/Hibernate 7.4) ejecuta `select set_config('app.negocio_id', ?, true)` al comenzar cada transacción, con el negocio de `ContextoNegocio`; si no hay contexto, no fija nada y RLS no devuelve filas.
- **Por qué:** `true` (local a la transacción) se descarta en el `COMMIT`/`ROLLBACK`, así que una conexión devuelta al *pool* no arrastra el negocio; la función `app_negocio_actual()` con `nullif` falla cerrada.
- **Alternativas:** `SET` de sesión o `connection-init-sql` (contamina el *pool*); un `DataSource` por negocio (no escala y complica la transacción); `StatementInspector` de Hibernate (no cubre JDBC ni consultas fuera de Hibernate).
- **Consecuencias:** toda lectura y escritura de negocio debe ocurrir dentro de una transacción gestionada (los repositorios de Spring Data ya la abren); los hilos de las pruebas de concurrencia fijan su contexto.

### V2-DA-07 · Dos roles de base de datos
- **Decisión:** `barberturno_v2` (propietario; Flyway con `spring.flyway.user`) y `barberturno_v2_app` (aplicación; DML y secuencias; sin `BYPASSRLS` ni propiedad).
- **Por qué:** el propietario podría desactivar RLS o alterar el esquema; el superusuario y los roles con `BYPASSRLS` la ignoran. Con dos roles, ni un fallo de la aplicación salta la política. Producción en Azure ya usa un rol de aplicación propio (p-03 §12).
- **Alternativa:** un solo rol con `FORCE` (funciona, pero el rol puede `ALTER TABLE … DISABLE ROW LEVEL SECURITY` y Flyway tendría que fijar contexto para los rellenos).

### V2-DA-08 · Colas en PostgreSQL (`FOR UPDATE SKIP LOCKED`) y outbox transaccional
- **Decisión:** eventos entrantes de WhatsApp y mensajes salientes en tablas; trabajos `@Scheduled` cada 1–2 s que toman lotes con `SKIP LOCKED`; los mensajes salientes se insertan en la misma transacción que el cambio de dominio.
- **Por qué:** durable (sobrevive a reinicios), transaccional (ningún «reservada» sin reserva) y sin infraestructura nueva; el volumen previsto es de cientos de mensajes al día.
- **Alternativas:** `@Async` en memoria (se pierde en un reinicio y no es transaccional); RabbitMQ, Kafka o Azure Service Bus (un servicio más que pagar y operar); eventos de Spring Modulith (dependencia nueva sin ventaja con este volumen).
- **Consecuencias:** con varias instancias de la aplicación seguiría funcionando gracias a `SKIP LOCKED`; los trabajos se desactivan en las pruebas que no los necesitan.

### V2-DA-09 · API oficial de WhatsApp Cloud integrada directamente en Spring Boot
- **Decisión:** llamadas HTTP a la Graph API con `RestClient`, *webhooks* recibidos por el propio monolito y versión de la Graph API fijada en configuración.
- **Por qué:** cumple la prioridad del responsable (API oficial de Meta); sin recargo de intermediarios; la lógica sigue en Java; los SDK de terceros no aportan lo bastante para justificar otra dependencia.
- **Alternativas:** un BSP (Twilio, 360dialog, Gupshup: añaden un recargo por mensaje y otra cuenta, a cambio de soporte; reconsiderar si Meta no aprueba la app); bibliotecas no oficiales que automatizan WhatsApp Web (**prohibidas**: incumplen los términos y arriesgan el número).

### V2-DA-10 · Otros negocios mediante Tech Provider + Embedded Signup; cada negocio paga sus mensajes
- **Decisión:** en la Fase 3, la app de la plataforma se registra como **Tech Provider**; cada barbería conecta su número con Embedded Signup v4 y asocia su método de pago a su cuenta de mensajería.
- **Por qué:** es la vía de Meta para que un software opere números de otros negocios; el negocio sigue siendo dueño de su número y de su relación con Meta; la plataforma no adelanta ni revende el coste de los mensajes.
- **Requisitos y límites (comprobados el 21/10/2026):** acceso avanzado a `whatsapp_business_management` y `whatsapp_business_messaging` mediante App Review; app en modo *Live*; 10 negocios nuevos por semana sin verificaciones y 200 con verificación del negocio, App Review y Access Verification; Embedded Signup v2/v3 retirados el 15/10/2026 (usar v4); dominio del flujo en *Allowed domains*. Ver `09-costes.md` §5.
- **Alternativa:** Solution Partner (línea de crédito propia y facturación a los clientes; fuera de alcance porque implica cobrar).

### V2-DA-11 · Sin n8n ni Activepieces en el MVP
- **Decisión:** no se usan.
- **Por qué:** las reglas deben vivir en Java junto al protocolo de bloqueos; serían otro servicio que alojar, asegurar y respaldar, con su propio almacén de credenciales (tokens de WhatsApp de varios negocios); añaden latencia y un punto de fallo a cada mensaje; el *webhook*, la cola y el envío son unas pocas clases. n8n usa la *Sustainable Use License*, con restricciones para productos cuyo valor dependa de él; Activepieces es MIT en su edición comunitaria (directorios *enterprise* aparte).
- **Cuándo reconsiderarlo:** automatizaciones de baja criticidad y configurables por cada negocio (p. ej., avisos a hojas de cálculo o CRM) que no toquen reservas; Activepieces sería la opción preferida por su licencia.

### V2-DA-12 · IA detrás de un puerto propio, con herramientas de propuesta y confirmación determinista
- **Decisión:** puerto `ModeloConversacional` (Java) con un adaptador falso programable y un adaptador de Anthropic con el **SDK oficial de Java** (`com.anthropic`); *tool use* con `strict: true`; las herramientas solo leen o **proponen**; las confirmaciones las ejecuta el servidor al recibir el botón; las respuestas de resultado son plantillas del servidor; los menús guiados son el respaldo permanente.
- **Por qué:** «la IA interpreta, el backend decide» se impone por construcción y no por instrucciones; el adaptador falso permite probar las guardas en `verify` sin red ni coste; cambiar de proveedor solo afecta al adaptador.
- **Alternativas:** dejar que el modelo ejecute reservas con una herramienta de escritura (una alucinación o una inyección se convierte en una operación); NLU clásica por intenciones (Rasa, Dialogflow: más trabajo de entrenamiento y peor con fechas relativas y frases libres); Spring AI como abstracción (posible, pero añade una capa y su compatibilidad con Boot 4.1 debe verificarse; se reevalúa en V2-12).
- **Pendiente:** proveedor y modelo los aprueba el responsable con los resultados de la evaluación (P2-03).

### V2-DA-13 · Confirmación por operación pendiente con *nonce*
- **Decisión:** `operacion_pendiente` (una vigente por conversación, 10 min) cuyo id y *nonce* viajan en el id del botón; ejecutar = bloquear la fila, comprobar estado, vigencia, ficha y *nonce*, llamar al servicio de dominio y marcarla `EJECUTADA` en la misma transacción.
- **Por qué:** idempotente frente a dobles pulsaciones y reintentos de *webhook*; impide confirmar una propuesta antigua o ajena; separa la intención (modelo) de la ejecución (servidor).
- **Alternativa:** interpretar «sí» con el modelo (no determinista; una respuesta tardía podría confirmar otra cosa).

### V2-DA-14 · Credenciales cifradas en la base con clave de entorno
- **Decisión:** `canal_whatsapp.token_cifrado` con AES-256-GCM (JCA estándar, IV aleatorio por valor, versión de clave) y la clave en `BT_CLAVE_CIFRADO`; el *app secret* y la clave del proveedor de IA solo en variables de entorno.
- **Alternativas:** texto plano (no); Azure Key Vault (mejor rotación y auditoría, pero coste y configuración; para *staging* y producción de v2, P2-08).

### V2-DA-15 · *Webhook*: firma sobre el cuerpo crudo, idempotencia en la base y 200 inmediato
- **Decisión:** el controlador lee el cuerpo como bytes, valida `X-Hub-Signature-256` (HMAC-SHA256 con el *app secret*, comparación en tiempo constante) antes de deserializar, inserta con `ON CONFLICT DO NOTHING` y responde 200; todo lo demás es asíncrono. La ruta queda fuera de CSRF y de la sesión porque tiene autenticación propia.
- **Por qué:** Meta reintenta hasta 7 días ante cualquier respuesta distinta de 200 y avisa de que puede duplicar notificaciones; procesar dentro de la petición arriesga tiempos de espera y duplicados.
- **Alternativas:** lista de IP de Meta (cambian; Meta recomienda mTLS en su lugar, que se puede añadir después); procesamiento síncrono (duplicados y tiempos de espera).

### V2-DA-16 · Evaluación de conversaciones en dos niveles
- **Decisión:** (1) suite **determinista** en `verify` con el adaptador falso: guiones de llamadas a herramientas que comprueban guardas, confirmaciones, límites, aislamiento y efectos en la base; (2) evaluación **en vivo** bajo demanda con el modelo real y un presupuesto fijado, fuera de la CI, con correctores basados en reglas y en el estado final de la base.
- **Por qué:** lo crítico (no inventar horarios, no confirmar sin ejecutar, no filtrar) se comprueba siempre sin coste; la calidad lingüística y la elección del modelo se miden con el modelo real.
- Detalle en `08-pruebas-migracion-reversion.md` §4.

### V2-DA-17 · Un único artefacto; v2 no se despliega en los recursos académicos
- **Decisión:** v2 sigue siendo un jar con la SPA dentro (DA-12). No se despliega en `barberturno-web-202610` ni usa `pg-barberturno-202610`; *staging* y producción de v2 serán recursos nuevos cuando el responsable los apruebe (P2-08).
- **Por qué:** RNF-02 de v1 se mide en noviembre de 2026 sobre el despliegue académico, y el crédito de Azure for Students está calculado para v1.

### V2-DA-18 · Identificadores con prefijo `V2-`
- **Decisión:** `V2-RF`, `V2-RN`, `V2-RNF`, `V2-CP`, `V2-DA`, tareas `V2-NN` y ramas `tarea/V2-NN-…`. Los ID de v1 no se reutilizan ni se renumeran.

---
*Decisiones V2-DA-19…22: ajustes pedidos por el responsable al aprobar la especificación y autorizar solo V2-01 (22/10/2026). Vigentes.*

### V2-DA-19 · Convivencia de la estructura nueva con las operaciones de v1 durante V2-01
- **Cómo se transmite el negocio sin tocar entidades ni contratos.** (1) `ProveedorNegocioActual` (interfaz) entrega el negocio de la operación; en V2-01 su única implementación de producción lee `barberturno.tenancy.negocio-unico-id` (obligatoria y sin valor por defecto; el perfil `prod` se niega a arrancar). (2) Al **comenzar cada transacción** gestionada por Spring se ejecuta `select set_config('app.negocio_id', <id>, true)`. (3) **Asignación en escritura:** `negocio_id` tiene `DEFAULT app_negocio_actual()`, así que los `INSERT` de Hibernate, que no mapean la columna, reciben el negocio de la transacción; la política `WITH CHECK` rechaza cualquier otro valor. (4) **Lectura:** la política `USING` filtra cada consulta, incluidas las de métodos heredados de `JpaRepository`. Ningún DTO, ruta, entidad ni código de error cambia.
- **Qué opera y qué no.** Todas las operaciones de v1 funcionan dentro del negocio único: catálogo, personal, jornadas, bloqueos, disponibilidad, reservas, transiciones, auditoría, avisos, reportes y su `.xlsx`, `AdminInicialRunner` y `DatosDemoRunner` (los dos ejecutan en transacciones). `usuario` es identidad global y **no** tiene RLS en V2-01. Operar con dos negocios a la vez solo es posible en las pruebas (proveedor programable, §DA-20); en producción llega con el `ResolutorNegocio` (V2-05).
- **Consultas rotas: se hacen visibles, no se esconden.** Un acceso a tablas de negocio fuera de una transacción no tiene contexto: devuelve 0 filas o falla el `INSERT`. Si alguna prueba de v1 falla por eso, es un defecto de V2-01 que se corrige haciendo transaccional ese camino (y se anota en la evidencia con archivo y motivo). Quedan **prohibidos** como solución: un valor por defecto del negocio dentro de `app_negocio_actual()`, conectar con el rol propietario, `BYPASSRLS`, `NO FORCE`, desactivar políticas o debilitar aserciones.
- **Migraciones de datos futuras bajo `FORCE`.** `FORCE` también obliga al rol propietario, así que Flyway no ve ni escribe filas de negocio sin contexto: el relleno de V100 se hace **antes** de activar RLS; las migraciones posteriores que escriban datos de negocio (p. ej., V102, fichas de cliente) fijan `set_config('app.negocio_id', …, true)` por negocio dentro de la propia migración.

### V2-DA-20 · Rol de migraciones separado del rol de ejecución; aislamiento probado con el rol real
- **Roles.** `barberturno_v2`: propietario de las bases y del esquema; solo lo usan Flyway, la limpieza entre pruebas y la preparación del estado «v1» de la prueba de actualización. `barberturno_v2_app`: `LOGIN`, sin `SUPERUSER`, `CREATEDB`, `CREATEROLE`, `REPLICATION` ni `BYPASSRLS`; no es propietario de nada; tiene `CONNECT`, `USAGE` del esquema, `SELECT/INSERT/UPDATE/DELETE` en las tablas, `USAGE/SELECT` en las secuencias y `EXECUTE` de `app_negocio_actual()`; **sin** `TRUNCATE`, `REFERENCES`, `TRIGGER` ni `CREATE`. Los permisos los concede `V100` con un *placeholder* de Flyway (`${rolAplicacion}`) y privilegios por defecto, de modo que son iguales en dev, test, CI y en una futura producción de v2.
- **Pruebas.** El contexto de Spring de **todas** las pruebas usa el rol de la aplicación; una prueba lo comprueba (`current_user`, `rolbypassrls`, `rolsuper`, ausencia de propiedad). Ninguna aserción de aislamiento, ni ningún dato de negocio de una prueba de aislamiento, se hace con el propietario. Única excepción: la fila de `negocio` del negocio B de prueba (tabla de plataforma sin RLS; en V2-01 la aplicación solo la lee, `SELECT`, y no recibe `INSERT` antes de V2-06); todas las filas de negocio de B se crean con el rol de la aplicación bajo el contexto de B. La limpieza entre pruebas (`TRUNCATE`, que no es una aserción y que RLS no filtra) usa una conexión aparte con el propietario, limitada a las bases `*_test`/`*_migracion`.
- **Contexto limitado a la transacción.** Se prueba con un *pool* de una sola conexión física: (a) transacción con el negocio B y, a continuación, transacción con el A sobre la misma conexión → el A no ve nada de B; (b) tras `COMMIT` y tras `ROLLBACK`, `current_setting('app.negocio_id', true)` vuelve vacío en esa conexión; (c) sin contexto → 0 filas y `INSERT` rechazado; (d) hilos concurrentes (`ConcurrenciaReservaIT`) obtienen su contexto en cada transacción. Para (a) las pruebas usan un `ProveedorNegocioActual` programable por hilo que solo existe en `src/test`.

### V2-DA-21 · Correcciones y migraciones de `main` después de `V100`
- **Hecho de partida.** Flyway aplica por versión. Con `outOfOrder=false`, una migración pendiente con versión menor que la última aplicada hace fallar `validate` («ignored»), así que una `Vk` nueva de `main` (k < 100) que llegue a una base v2 que ya tiene `V100` **no se salta en silencio: bloquea el arranque**. Es el comportamiento buscado. El rango `V100+` solo evita choques de nombre, no resuelve el orden.
- **Política.**
  1. Las migraciones de v1 (< 100) entran en `v2` **sin cambios** (ni renumerar ni editar). Un manifiesto `backend/src/main/resources/db/migration/v1-manifiesto.sha256` lista su SHA-256 y una prueba (`MigracionesV1InalteradasTest`) falla si una migración < 100 difiere del manifiesto o aparece sin estar en él: aceptar una nueva es un paso deliberado.
  2. Cada `git merge main` hacia `v2` es una tarea propia (`tarea/V2-NN-integrar-main-…`) que actualiza el manifiesto y pasa las pruebas de instalación nueva y de actualización (DA-22) con la nueva `Vk` incluida. Si `Vk` choca con el esquema de v2, se corrige con una migración **nueva** `V1xx`; nunca se edita `Vk` ni una `V1xx` aplicada.
  3. Mientras no exista una base v2 cuyos datos haya que conservar (todo el MVP), las bases v2 locales son desechables: una base v2 que ya pasó `V100` y recibe una `Vk` se **recrea** desde cero (con el visto bueno del responsable si tiene datos de ensayo).
  4. **Condición previa** a crear cualquier base v2 que haya que conservar (*staging* o piloto, V2-17/P2-08): `main` se cierra a migraciones nuevas; una corrección de esquema necesaria a partir de entonces se hace solo en v2 (`V1xx`) y llega a v1 con el corte.
  5. **Prohibido:** `outOfOrder`, editar migraciones aplicadas, `ignoreMigrationPatterns` para ocultar migraciones pendientes o ausentes y `flyway repair` como rutina.
- **Contexto:** en 58 tareas, v1 solo ha tenido `V1`; la probabilidad es baja, pero queda detectado y resuelto por procedimiento.

### V2-DA-22 · Pruebas de instalación nueva y de actualización desde datos de v1
- **Base propia** `barberturno_v2_migracion` (propietario `barberturno_v2`), para no alterar `barberturno_v2_test`; las pruebas solo pueden limpiar esa base (comprobación del nombre, como `LimpiezaBaseDatos`).
- **Instalación nueva** (`InstalacionNuevaIT`): esquema vacío → todas las migraciones → negocio único, nueve tablas, RLS `FORCE` en las siete de negocio, FK compuestas, permisos del rol de la aplicación, y una reserva completa creada con el rol de la aplicación.
- **Actualización** (`ActualizacionV1IT`): esquema vacío → solo las migraciones de v1 (`target` = la última < 100) → datos **equivalentes a v1** cargados con el propietario (en v1 no existe RLS: el propietario hace de aplicación v1): clientes, un ADMIN que además es barbero, barberos, servicios, jornadas, bloqueos, reservas en los seis estados (canceladas solapadas y citas contiguas), auditorías y avisos → huella por tabla (número de filas y `md5` de las filas ordenadas por id, sin `negocio_id`) → resto de migraciones → **la misma huella**, todas las filas en el negocio único, identidades que continúan (el siguiente id supera al máximo) y `EXCLUDE` vigente. Después, con el rol de la aplicación: con contexto ve todo; sin contexto, nada; un negocio B con el mismo nombre de servicio es válido; una reserva del negocio A con barbero o servicio de B y una jornada o bloqueo de A sobre un barbero de B violan las FK compuestas.
- Las dos pruebas se ejecutan en `verify`, en serie con el resto (una sola suite contra PostgreSQL a la vez). Un ensayo manual con una copia de `barberturno_demo_t32` queda fuera de V2-01 (no se tocan bases existentes).

### V2-DA-23 · Ningún acceso a datos sin transacción pasa en silencio (23/10/2026)
- **Problema (revisión de V2-01; Gemini g039c lo confirma):** los métodos de consulta declarados en las interfaces de repositorio (`countByActivoTrue`, `listarPorBarbero`, `@Query`…) **no** son transaccionales por defecto en Spring Data. Llamados fuera de una transacción no reciben contexto y RLS devuelve 0 filas sin error: una consulta rota oculta. En V2-01 solo ocurría en pruebas (toda la API pasa por servicios `@Transactional`), pero el riesgo es real para el código futuro.
- **Decisión:** (1) las interfaces de repositorio del dominio se anotan con `@Transactional(readOnly = true)`, y los métodos que escriben (`@Modifying`), con `@Transactional`; dentro de un servicio se unen a su transacción (`REQUIRED`). (2) Una **guarda de Hibernate** (por ejemplo, un `StatementInspector`; el mecanismo exacto lo verifica la implementación en Hibernate 7.4) lanza `IllegalStateException` si Hibernate prepara SQL **sin una transacción de Spring activa**: un acceso sin contexto **falla con error**, no devuelve vacío. (3) JDBC directo fuera de transacción queda prohibido en producción (hoy no existe); si se introduce, se añadirá una guarda equivalente en el `DataSource` (deuda anotada para V2-05).
- **Se mantiene** el fallo cerrado de RLS (`app_negocio_actual()` nula → 0 filas) como **segunda barrera** en la base; la guarda es la primera, en la aplicación.
- **Alternativas descartadas:** una función de política que lance excepción sin contexto (no se evalúa si la tabla está vacía y rompe los diagnósticos con el propietario); envolver cada llamada en las pruebas (no protege la producción).

---
*Decisiones V2-DA-24 y V2-DA-25: alcance de V2-02 (23/10/2026). Vigentes.*
### V2-DA-24 · `membresia` es una tabla de plataforma sin RLS
- **Decisión:** `membresia` no lleva RLS; sus consultas reciben siempre el `negocio_id` explícito y se prueban con dos negocios.
- **Por qué:** se lee antes de conocer el negocio (resolución por petición en V2-05, `GET /api/mis-negocios`) y hay que poder saber si una persona tiene relación con **otro** negocio (V2-RN-16) sin exponer cuáles. Con RLS por negocio, ni la aplicación ni una función `SECURITY DEFINER` podrían comprobarlo, porque `FORCE` obliga también al propietario. Es el mismo tratamiento que `usuario` y `negocio`.
- **Alternativa descartada:** RLS con una segunda política «propias del usuario» (necesita `app.usuario_id` en cada transacción; se reconsiderará en V2-05 si se fija el usuario en el contexto).

### V2-DA-25 · Rol efectivo por negocio y compatibilidad con v1
- **Decisión:** la autorización del negocio se basa en la membresía activa del negocio actual, revalidada en cada petición; para no reescribir las reglas de v1, `UsuarioAutenticado.rol()` devuelve el rol efectivo con la equivalencia `PROPIETARIO`/`ENCARGADO` → `ADMIN`, `BARBERO` → `BARBERO`, cliente → `CLIENTE`. Las restricciones propias del encargado (R2-08) y de la identidad compartida (V2-RN-16) se aplican además con `rolNegocio`. El *claim* `rol` del JWT y `usuario.rol` dejan de autorizar al personal. `ROLE_ADMIN_PLATAFORMA` es independiente.
- **Por qué:** cambio mínimo y verificable sobre un código con 1604 pruebas; los permisos finos por rol de negocio llegan con las pantallas y rutas de V2-05/V2-06.
- **Consecuencia:** el contrato de API no cambia salvo el código nuevo `IDENTIDAD_COMPARTIDA` (409); `UsuarioSesionDto.rol` sigue siendo el rol efectivo.

### V2-DA-26 · Repositorios de tablas de plataforma con superficie mínima (23/10/2026)
- **Problema (bloqueo de Codex en el encargo 078, válido):** V2-DA-24 deja `membresia` sin RLS, pero un repositorio que herede de `JpaRepository` (o de `RepositorioTransaccional`) expone `findAll`, `findById`, `count`, consultas por ejemplo (`Example`), `Specification` y borrados masivos **sin negocio**: devolverían o borrarían membresías de todas las barberías. Filtrar en los servicios no protege esos métodos.
- **Decisión:** `MembresiaRepository` extiende **solo** `org.springframework.data.repository.Repository<Membresia, Long>` (interfaz de marca, sin métodos) y declara **únicamente** operaciones acotadas: por negocio explícito (buscar la membresía de un usuario en un negocio, contar propietarios activos de un negocio, bloquear/leer por `negocioId` + `id`), la comprobación booleana de V2-RN-16 (`existe una membresía activa del usuario en un negocio distinto de X`, que no devuelve cuál) y `save`. Transacciones como V2-DA-23 (`@Transactional(readOnly = true)` en la interfaz y `@Transactional` en las escrituras). **No** extiende `JpaRepository`, `CrudRepository`, `RepositorioTransaccional`, `JpaSpecificationExecutor` ni `QueryByExampleExecutor`.
- **Prueba de superficie:** una prueba unitaria recorre los métodos públicos de `MembresiaRepository` (incluidos los heredados) y falla si existe alguno sin un parámetro `negocioId` que no esté en la lista blanca (`save` y la comprobación booleana de V2-RN-16), o si la interfaz hereda de las anteriores. Se completa con la prueba de CB-5 por servicios.
- **Se aplica también** a cualquier tabla de plataforma futura con datos por negocio (`invitacion`, `canal_whatsapp`…). `usuario` (identidad global) y `negocio` quedan fuera: no contienen datos de un negocio en concreto.
- **Alternativa descartada:** RLS con dos políticas (negocio actual o filas del propio usuario), que exige fijar `app.usuario_id` en cada transacción; se reconsidera en V2-05 junto con la resolución por petición.

### V2-DA-27 · Ámbito de las personas que gestiona un negocio y protección de las cuentas de plataforma (23/10/2026)
- **Problema (bloqueo de Codex en el encargo 079, reproducido y válido):** la gestión de usuarios heredada de v1 (`/api/usuarios`) trabaja sobre la tabla **global** `usuario`. Un `PROPIETARIO` podía encontrar una cuenta `ADMIN_PLATAFORMA` sin membresías, restablecer su contraseña, iniciar sesión con ella y obtener `ROLE_ADMIN_PLATAFORMA`, o desactivarla. El listado sin filtro de rol mostraba además al personal de otros negocios. La regla de V2-02 §5.1.4 solo protegía a quien tenía membresía en **otro** negocio, y la cuenta de plataforma no tiene ninguna.
- **Decisión:**
  1. **Ámbito de gestión del negocio N** (listado, detalle y cualquier operación de `/api/usuarios` y de las altas o vínculos de `BarberoService`): solo (a) las personas con una membresía **activa en N**, y (b) mientras no existan las fichas (V2-03), los usuarios con `rol = CLIENTE`, `rol_plataforma = NINGUNO` y **ninguna** membresía, únicamente cuando N es el negocio único (`barberturno.tenancy.negocio-unico-id`). Fuera del ámbito → no aparecen en el listado y cualquier operación sobre ellas da **404 `NO_ENCONTRADO`** (como un recurso ajeno).
  2. **Cuentas de plataforma:** una cuenta con `rol_plataforma ≠ NINGUNO` cuenta siempre como **identidad compartida**. Aunque esté dentro del ámbito (p. ej., la cuenta inicial `BT_ADMIN_*`, que además es `PROPIETARIO` del negocio único), el negocio no puede restablecer su contraseña, cambiar el estado de su cuenta, editar su nombre o teléfono globales ni desactivar su cuenta al desactivar su perfil: **409 `IDENTIDAD_COMPARTIDA`**. Solo la propia persona (perfil y contraseña propios) o la plataforma (V2-06) pueden cambiarla.
  3. Se mantienen V2-RN-16 (persona con membresía activa en otro negocio → 409), R2-08 (encargado) y la regla del último propietario.
- **Prueba permanente:** el diagnóstico del encargo 079 pasa a ser una prueba de regresión que exige el **rechazo** de cada paso (listado sin la cuenta, restablecimiento y estado → 404 o 409, ningún inicio de sesión con credenciales temporales emitidas por un negocio sobre una cuenta de plataforma), más casos para el personal de otro negocio y para los clientes con un negocio distinto del único.
- **Requisitos:** V2-RN-03 (separación plataforma/negocio) y V2-RN-16; nueva regla **V2-RN-17**: *un negocio solo ve y gestiona a las personas de su ámbito; nunca modifica una cuenta de plataforma*.

---
*Decisiones V2-DA-28 y V2-DA-29: alcance de V2-03 (23/10/2026). Vigentes.*
### V2-DA-28 · Ficha con RLS e índice de plataforma de vínculos mantenido por la base
- **Decisión:** `cliente` es tabla de negocio con RLS `FORCE` (contiene datos personales por negocio). Para que un negocio sepa —sin ver nada más— si una cuenta tiene ficha en **otro** negocio (V2-RN-16), existe `vinculo_cliente (negocio_id, usuario_id)`, tabla de plataforma sin RLS ni datos personales, mantenida **solo** por un disparador de `cliente` (función `SECURITY DEFINER` del propietario con `search_path` fijo). El rol de la aplicación solo puede leerla y su repositorio expone solo la comprobación booleana.
- **Por qué:** con RLS en `cliente`, ni la aplicación ni una función del propietario pueden ver fichas de otro negocio (`FORCE` obliga también al propietario). El disparador garantiza que el índice no se desincroniza ni se falsifica desde la aplicación.
- **Alternativas descartadas:** `cliente` sin RLS (expondría nombres y teléfonos de clientes entre negocios); índice escrito por los servicios (puede desincronizarse); un rol con `BYPASSRLS` para una función (requiere crear roles con superusuario).

### V2-DA-29 · Contrato de reservas con el id de la cuenta durante V2-03
- **Decisión:** hasta que cambie el frontend (V2-07), `clienteId` (reserva asistida) y `ReservaDto.cliente.id` siguen siendo el id de la **cuenta**; el servidor lo traduce a la ficha activa del negocio actual (sin ficha → 404). Los ids de ficha solo aparecen en `/api/clientes/{id}`.
- **Por qué:** evita tocar el frontend en esta etapa y mantiene válidos los datos migrados (en v1, ficha y cuenta comparten id; en v2 una cuenta puede tener varias fichas).
- **Consecuencia:** las fichas sin cuenta (WhatsApp, alta por el personal) no se pueden reservar por la web hasta V2-07, que pasa el contrato a ids de ficha.
- **Actualización (26/10/2026, V2-07, backlog §10.5):** V2-07 **conserva** el id de la cuenta: no hay en V2-07 ninguna pantalla que cree fichas sin cuenta, y el cambio no añade aislamiento. El paso a ids de ficha se hará con la primera funcionalidad que reserve para fichas sin cuenta (WhatsApp o bandeja web, V2-10/V2-11).

---
*Decisiones V2-DA-30 y V2-DA-31: alcance de V2-04 (24/10/2026). Vigentes.*

### V2-DA-30 · Parámetros de reserva leídos del negocio en cada transacción
- **Decisión:** las reglas parametrizables (RN-07, RN-12, RN-20, RN-21 y el horizonte) se leen de la fila `negocio` del negocio actual al usarlas, dentro de la transacción. La rejilla sigue global. Los valores iniciales de las columnas son los de v1, y una prueba compara la fila con `application.yml`.
- **Por qué:** una sola fuente por negocio y sin cachés que invalidar: la lectura es por clave primaria. Mantiene el comportamiento del negocio único.
- **Alternativa descartada:** una caché por negocio (habría que invalidarla cuando exista la edición por API en V2-06).

### V2-DA-31 · El perfil profesional es local; desactivarlo revoca solo la membresía `BARBERO` del negocio
- **Decisión:** `barbero.nombre_visible`, `telefono_contacto` y `especialidad` son datos del negocio y el negocio nunca escribe la cuenta. Desactivar el perfil no toca `usuario` (ni `activo` ni `token_version`); revoca la membresía `BARBERO` de ese negocio, si existe, y reactivarlo la restablece. Las membresías de gestión y las de otros negocios no cambian.
- **Por qué:** en v1, desactivar a un barbero bloqueaba su cuenta, que era de una sola barbería. En v2, la cuenta puede trabajar en otros negocios (V2-RN-16): el efecto local equivalente es quitarle el acceso a ese negocio, con efecto inmediato gracias a la revalidación por petición.
- **Consecuencia:** el 409 `IDENTIDAD_COMPARTIDA` al editar el perfil desaparece; se mantiene en el restablecimiento de contraseña y en el estado de la cuenta.

### V2-DA-32 · Un perfil profesional por cuenta y negocio (24/10/2026)
- **Problema (bloqueo de Codex en el encargo 082, reproducido y válido):** `barbero` conserva de v1 `barbero_usuario_id_key UNIQUE (usuario_id)`, que impide que la misma cuenta tenga perfil profesional en dos negocios (`23505` con el rol de la aplicación). El DDL de `04-arquitectura-datos.md` §3.2 ya preveía sustituirla, pero ninguna tarea lo incluía; sin ello, las condiciones 4 y 5 de V2-04 no se pueden demostrar.
- **Decisión:** migración aditiva `V105__perfil_por_negocio.sql` que elimina `barbero_usuario_id_key` y crea `barbero_usuario_uk UNIQUE (negocio_id, usuario_id)`. Las búsquedas de perfil por cuenta siguen bajo RLS (devuelven el perfil del negocio actual). Un segundo perfil de la misma cuenta en el mismo negocio sigue rechazándose.
- **Por qué una migración nueva:** `V104` ya está aplicada en la base de pruebas; V2-DA-21 prohíbe editar migraciones aplicadas.

---
*Decisiones V2-DA-33 y V2-DA-34: alcance de V2-05 (24/10/2026). Vigentes. V2-DA-34 precisa R2-03 de `05-roles-permisos.md`.*

### V2-DA-33 · Resolución del negocio por ruta con un único camino de autorización y alias legado explícito
- **Decisión:** un filtro dentro de la cadena de Spring Security resuelve `/api/n/{slug}` (404 si no existe), fija el negocio en un contexto de petición que se limpia siempre y presenta la petición al resto de la cadena como `/api/{resto}`, de modo que las reglas de `SecurityConfig`, CSRF y los controladores existentes se aplican una sola vez y por igual. Las rutas sin prefijo son un **alias legado** de un negocio fijado por configuración (solo en `dev`/`test`/`demo`) para no tocar el frontend hasta V2-07. Se retira el proveedor transitorio de V2-01; las tareas sin petición usan un ejecutor con `slug` explícito.
- **Por qué:** duplicar 45 reglas y 9 controladores para el prefijo abriría dos caminos de autorización que podrían divergir; el envoltorio conserva uno. El cortafuegos de Spring Security (`StrictHttpFirewall`) **rechaza** las rutas ambiguas (barras dobles, segmentos `.`/`..`, `;`, barras invertidas o codificadas) antes de que el filtro las resuelva; no las normaliza (precisado por V2-DA-36).
- **Alternativas descartadas:** mapear todos los controladores a `/api/n/{slug}/…` (duplica reglas y rehace las 1744 pruebas); negocio en una cabecera o en el JWT (V2-DA-04); cambiar ya el frontend (V2-07).

### V2-DA-34 · 403 para un actor sin rol en un negocio existente; 404 para recursos de otro negocio
- **Decisión:** precisa R2-03. El negocio existe públicamente (su página), así que un actor autenticado sin rol en él recibe **403 `PROHIBIDO`** en las operaciones protegidas. Un recurso concreto de otro negocio (reserva, ficha, aviso, perfil…) sigue dando **404**, y un negocio inexistente, **404**.
- **Por qué:** coherente con el comportamiento de v1 por rol y sin revelar recursos ajenos; el 404 por recurso lo garantizan RLS y los filtros explícitos.

### V2-DA-35 · El índice de vínculos refleja si la ficha está activa (24/10/2026)
- **Problema (bloqueo de Codex en el encargo 084, reproducido y válido):** `GET /api/mis-negocios` debe listar las fichas **activas** de la cuenta en todos sus negocios, pero `vinculo_cliente` (V2-DA-28) solo guarda `(negocio_id, usuario_id)` y no cambia al desactivar una ficha; y la RLS de `cliente` impide consultar su estado sin el contexto de cada negocio.
- **Decisión:** migración `V106__vinculo_cliente_activo.sql`: `vinculo_cliente.activo boolean NOT NULL`, rellenado desde `cliente` (con el contexto de cada negocio, V2-DA-19) y mantenido por el **mismo disparador** (`CREATE OR REPLACE FUNCTION` en la migración nueva, sin editar `V102`), que ahora copia `NEW.activo` en cada inserción y actualización. `mis-negocios` lee `vinculo_cliente` (`usuario_id` = cuenta autenticada, `activo`) junto con `membresia` y `negocio`, sin abrir la RLS de `cliente`. El estado activo no es un dato personal; la aplicación sigue con solo `SELECT` sobre el índice.
- **Alternativa descartada:** recorrer cada negocio de la cuenta con su contexto y consultar `cliente` (una transacción por negocio, más compleja y más lenta).
- **Consecuencia:** los índices de §8.2.6 pasan a `V107__indices_negocio.sql`.

### V2-DA-36 · Las rutas de la API no admiten codificación y se clasifican igual que las enruta Spring (24/10/2026)
- **Problema (hallazgo H-085-01 de Codex, reproducido y válido):** `ResolutorNegocio` clasificaba la ruta (global, de negocio o alias) comparando `getRequestURI()` **sin decodificar**, mientras Spring MVC enruta por segmentos **decodificados**. `GET /api/n/a/%70erfil` llegaba a `/api/perfil` dentro de un negocio (200 en lugar de 404), y `/api/%70erfil` se habría tomado como ruta de negocio del alias. La autorización de `SecurityConfig` no se eludía (sus *matchers* usan la ruta decodificada), pero la frontera entre identidad global y negocio sí.
- **Decisión:** (1) toda petición a `/api/**` cuya ruta (sin la consulta) contenga una secuencia `%` se rechaza con **400 `VALIDACION`** antes de clasificarla: ninguna ruta de la API necesita codificación (slugs `[a-z0-9-]`, ids numéricos, tramos fijos), así que la forma cruda y la decodificada coinciden siempre. (2) La clasificación y el envoltorio usan **la misma representación decodificada** que Spring MVC (`ServletRequestPathUtils`/`UrlPathHelper`), como segunda barrera. (3) Se conservan las protecciones del cortafuegos de Spring Security (barras dobles, segmentos `.`/`..`, `;`, barras invertidas y barras codificadas).
- **Pruebas:** batería de variantes para las rutas prefijadas y las del alias (letras codificadas en cualquier tramo, `slug` codificado, `%2F`, `%2E`, `//`, `/./`, `/../`, `;…`, mayúsculas, barra final, barra invertida): cada una termina en 400 o 404 y **ninguna** ejecuta un controlador con una clasificación distinta de la de su forma decodificada. La regresión de H-085-01 pasa a exigir el rechazo.

---
*Decisiones V2-DA-37 y V2-DA-38: alcance de V2-06 (24/10/2026). Vigentes. V2-DA-38 concreta R2-04 de `05-roles-permisos.md`.*

### V2-DA-37 · La revocación de una membresía guarda su motivo y solo se deshace por la misma vía
- **Decisión:** toda revocación registra su motivo (`PERFIL_DESACTIVADO`, `SALIDA`, `DECISION_GESTOR`, `PLATAFORMA`). Reactivar un perfil profesional solo deshace la revocación `PERFIL_DESACTIVADO`. Una revocación por cualquier otro motivo solo la deshace una invitación nueva aceptada. El arranque (`AdminInicialRunner`) crea la membresía inicial si falta, pero nunca reactiva una revocada.
- **Por qué:** cierra la deuda de V2-04 antes de que existan las revocaciones nuevas de V2-06, y un segundo camino detectado en la revisión (el arranque reactivaba la membresía inicial).
- **Alternativa descartada:** un estado `SUSPENDIDA` aparte para la desactivación del perfil (duplica los estados; el motivo es más simple de consultar y probar).

### V2-DA-38 · Efectos de un negocio suspendido
- **Decisión:** un negocio `SUSPENDIDO` es invisible en sus rutas públicas (404), su personal conserva las lecturas y toda escritura de negocio da 409 `NEGOCIO_SUSPENDIDO`. Las invitaciones no se pueden aceptar y solo la plataforma lo reactiva. Se impone en la resolución del negocio, de modo que ningún controlador lo elude.
- **Por qué:** concreta R2-04 («no acepta reservas… su personal conserva el acceso de lectura») con un comportamiento uniforme y comprobable.

### V2-DA-39 · La invitación no vincula perfiles profesionales; un perfil nunca cambia de cuenta (24/10/2026)
- **Problema (hallazgo H-090-01 de Codex, válido):** el diseño de la invitación con `barberoId` suponía un perfil profesional previo **sin cuenta**, pero `barbero.usuario_id` es `NOT NULL` y lo usan la agenda, los avisos y los permisos de la reserva. Vincular al aceptar un perfil que ya tuviera cuenta **reasignaría su agenda a otra persona**.
- **Decisión:** en V2-06.3 la invitación **no** lleva `barbero_id` (se omite de `V110`). Aceptar una invitación `BARBERO` crea o restablece solo la **membresía**. El perfil profesional lo crea después el personal de gestión con el vínculo existente `POST /api/n/{slug}/barberos {usuarioId, especialidad}`, ampliado a cualquier cuenta con **membresía activa en ese negocio** (`BARBERO`, `ENCARGADO` o `PROPIETARIO`) y **sin perfil** en él; fuera de eso, 404. `barbero.usuario_id` es **inmutable**: ningún camino reasigna un perfil a otra cuenta (prueba explícita).
- **Aplazado:** perfiles creados antes de la aceptación (con horario preparado) y perfiles sin cuenta, si un negocio piloto los necesita; exigiría `usuario_id` anulable y revisar a todos sus consumidores.
- **Alternativa descartada:** hacer `barbero.usuario_id` anulable ahora (toca agenda, avisos, permisos y reservas en una subtarea de invitaciones).

### V2-DA-40 · Orden total de los bloqueos ⓪ (24/10/2026)
- **Problema (hallazgo H-093-01 de Codex, válido):** revocar o retirar la membresía de una persona con perfil profesional (V2-06.4) necesita el bloqueo del último propietario `(313131, negocio)` y, al desactivar su perfil con la regla de V2-04, el del cupo `(141414, negocio)`; la arquitectura (§5) solo permitía uno por camino.
- **Decisión:** orden total `(313131, negocio)` → `(141414, negocio)` → ① → ② → ③. Un camino toma los bloqueos ⓪ que necesite **en ese orden** y siempre antes de bloquear filas. Los caminos actuales encajan: `UsuarioAdminService.cambiarEstado` toma solo el primero; altas, reactivaciones y desactivaciones de perfil, solo el segundo; la retirada de una membresía en el equipo, los dos. El global de un solo valor `313131` (último `ADMIN_PLATAFORMA`) no comparte espacio de claves con los de dos partes.
- **Prueba:** una carrera entre una revocación del equipo (los dos bloqueos) y un alta o desactivación de perfil del mismo negocio (solo el segundo) termina sin interbloqueo ni `RECURSO_OCUPADO`, con 20 repeticiones.
- **Alternativa descartada:** que la desactivación por revocación no tome el bloqueo del cupo (rompe la serialización de cambios de estado del perfil que V2-04 conserva).

### V2-DA-41 · Rol, perfil y revocaciones repetidas en el equipo (24/10/2026)
- **Problema (revisión del coordinador del avance parcial de 094):** el borrador de `EquipoService` desactivaba el perfil profesional en **cualquier** cambio de rol, permitía cambiar el rol de una membresía **revocada** (que la reactivación del perfil podría restablecer después con el rol nuevo) y, al revocar una membresía ya revocada, sobrescribía siempre su motivo e instante.
- **Decisión:**
  1. El perfil profesional es independiente del rol (V2-04: cualquier membresía activa puede tener perfil). **Cambiar el rol no desactiva el perfil**; solo lo desactiva la pérdida de la membresía (revocación o salida), con el motivo de esa pérdida (V2-DA-37). El cambio de rol sigue tomando `(313131, negocio)` por el último propietario y puede tomar también el segundo bloqueo en el orden de V2-DA-40, sin efecto adicional.
  2. **Cambiar el rol de una membresía revocada → 409 `TRANSICION_INVALIDA`**, sin cambios: una membresía revocada solo se restablece por las vías de V2-DA-37.
  3. **Revocar una membresía ya revocada:** si su motivo es `PERFIL_DESACTIVADO`, pasa a `DECISION_GESTOR` (la decisión del gestor prevalece y la reactivación del perfil ya no devuelve el acceso); con cualquier otro motivo, la operación es idempotente (200) y conserva el motivo y el instante originales. Los permisos del actor (encargado solo sobre `BARBERO`) se comprueban igual.
- **Prueba (`EquipoIT`):** promover y degradar a una persona con perfil activo lo conserva activo y no cambia el cupo; cambio de rol sobre revocada → 409 sin cambios; perfil desactivado (membresía `PERFIL_DESACTIVADO`) → revocación del propietario → reactivación del perfil → sin acceso y motivo `DECISION_GESTOR`; revocar una `SALIDA` → 200 con motivo e instante intactos.

### V2-DA-42 · Papel solicitado por petición (26/10/2026)
- **Problema:** una cuenta que es personal y cliente de la misma barbería (R2-01) no puede actuar como cliente: `AuthService.principal` da prioridad a la membresía (deuda registrada en V2-05).
- **Decisión:** cabecera `X-BT-Papel: CLIENTE`, válida solo en `/api/n/{slug}/…`. **Solo reduce privilegios:** con ficha activa en ese negocio, la petición se atiende como `CLIENTE` (sin rol de negocio ni perfil profesional); sin ficha, sin rol en el negocio (403 en lo protegido; lo público sigue público). Otro valor o la cabecera en una ruta global → 400 `VALIDACION`. Sin cabecera, el comportamiento actual. Se fija y se limpia con el contexto de la petición (`ResolutorNegocio`). No sustituye a CSRF ni a las comprobaciones del servicio.
- **Alternativas descartadas:** rutas duplicadas `/api/n/{slug}/cliente/…` (duplica la superficie y la matriz de permisos) y un papel guardado en el JWT (obligaría a reemitir la sesión al cambiar de papel o de barbería).

### V2-DA-43 · Exposición mínima de identidades en el equipo (26/10/2026)
- **Decisión:** `EquipoDto` añade `nombre` (todas las membresías del negocio) y `correo` (solo las activas; `null` en las revocadas, para no seguir mostrando el correo de quien salió). Solo propietario y encargado del negocio; ninguna búsqueda ni listado general de cuentas. Motivo: sin nombre ni correo el equipo no es gestionable (observación de V2-06).

### V2-DA-44 · Parámetros públicos del negocio y papeles en mis-negocios (26/10/2026)
- **Decisión:** `GET /api/n/{slug}/negocio` (público como el catálogo; suspendido → 404) → `{slug, nombre, horizonteDias, anticipacionCambioClienteMin, limiteReservasActivas, confirmacionManual}`, para que los controles reflejen los parámetros reales; sin cupo de barberos, tolerancia, estado ni ids. `GET /api/mis-negocios` añade `papeles` (rol de la membresía activa y `CLIENTE` si hay ficha activa), conservando `rol`.
- **Límites técnicos frente a reglas de producto:** la interfaz valida y rotula solo los límites que impone el servidor (p. ej. `smallint`); los rangos de producto siguen pendientes de validación con el negocio.

### V2-DA-45 · Roles de presentación en las sesiones (26/10/2026)
- **Problema (hallazgos H-102-01 y H-102-03 de Codex, válidos):** `/api/auth/sesion` no expone el rol de plataforma y `/api/n/{slug}/sesion` da `ADMIN` tanto al propietario como al encargado, así que la interfaz no puede mostrar la plataforma ni las acciones que difieren entre ambos (cambiar roles, configuración, roles invitables).
- **Decisión:** `UsuarioSesionDto` añade `rolPlataforma` (`ADMIN_PLATAFORMA` o `null`, de la propia cuenta) y `rolNegocio` (`PROPIETARIO`, `ENCARGADO`, `BARBERO` o `null`: la membresía activa del negocio de la ruta; `null` en rutas globales y con `X-BT-Papel: CLIENTE`). Solo describen a la cuenta autenticada; **son de presentación**: el servidor sigue decidiendo con la membresía y el rol global en cada operación. `rol` se conserva.

### V2-DA-46 · Estados de una invitación ante quien tiene el enlace (26/10/2026)
- **Problema (hallazgo H-102-02 de Codex, válido):** la consulta y la aceptación responden 404 genérico a una invitación caducada, revocada, usada o de otra cuenta; la interfaz no puede explicar qué ocurre.
- **Decisión:** el token (32 bytes aleatorios) ya es la capacidad secreta: quien lo tiene puede conocer el estado de **esa** invitación sin riesgo de enumeración. `GET /api/invitaciones/{token}`: token desconocido → 404; conocido → 200 con `InvitacionPublicaDto` y `estado` (`PENDIENTE`, `CADUCADA`, `REVOCADA`, `USADA`). `POST …/aceptacion`: desconocido → 404; no pendiente → 409 `INVITACION_NO_VIGENTE` (con el estado en el detalle); correo distinto del de la cuenta → 403 `INVITACION_OTRA_CUENTA`; ya miembro → 409 `YA_ES_MIEMBRO` (sin cambios); negocio suspendido → 409 `NEGOCIO_SUSPENDIDO`, también en la **consulta** de una invitación pendiente (como antes de esta decisión; una caducada, revocada o usada muestra su estado). El correo sigue enmascarado; nunca se devuelve el correo completo ni datos de otra invitación. Las aserciones de `InvitacionIT` que exigían el 404 uniforme se sustituyen por este contrato (cambio de contrato decidido aquí, no un debilitamiento) y se mantiene el 404 para tokens desconocidos, mal formados o de otra capacidad.

### V2-DA-47 · Registro de una cuenta desde una invitación (26/10/2026)
- **Problema (hallazgo H-103-01 de Codex, válido):** la única forma de crear una cuenta es el registro de clientes (`POST /api/n/{slug}/registro`, o `/api/auth/registro` por el alias legado), que crea además una **ficha de cliente** en esa barbería. Una persona invitada como personal acabaría siendo cliente de la barbería del alias y fallaría si esa barbería está suspendida.
- **Decisión:** `POST /api/invitaciones/{token}/registro` (global, público, con CSRF) con `{nombre, telefono, password, aceptaPrivacidad}`: en **una transacción** crea la cuenta con el **correo de la invitación** (no se elige), sin ficha de cliente ni rol global, y acepta la invitación con las mismas reglas y bloqueos que la aceptación (`(313131, negocio)` antes de ①; pendiente y vigente; barbería no suspendida); después inicia la sesión como el registro actual (cookies y CSRF). Errores: token desconocido → 404; no vigente → 409 `INVITACION_NO_VIGENTE`; barbería suspendida → 409 `NEGOCIO_SUSPENDIDO`; ya existe una cuenta con ese correo → 409 `CORREO_DUPLICADO` (la interfaz ofrece «Ingresar»); validación y RN-25 → 400. Ninguna otra barbería ni ficha cambia.
- **Consecuencia para V2-07.5:** al retirar el alias, `/api/auth/registro` deja de existir; el registro de clientes queda solo en `/api/n/{slug}/registro` y el de invitados en esta ruta.
- **Alternativa descartada:** un registro global sin barbería (abre la creación de cuentas sin motivo y obliga a decidir después dónde vive la persona).
### V2-DA-48 · La cookie de sesión solo se lee en la API (26/10/2026)
- **Problema (detectado por la batería de capturas de V2-07.6; anterior a V2-07):** `CookieBearerTokenResolver` leía y validaba `BT_SESION` en **cualquier** petición. Con una cookie caducada o revocada (p. ej., tras un restablecimiento de contraseña o la desactivación de la cuenta), la carga directa de una ruta de la aplicación (`/n/{slug}/agenda`, `/negocios`, `/`) respondía el JSON del 401 en lugar de la entrada Angular: la persona veía un texto técnico y no la pantalla de ingreso (CG-4).
- **Decisión:** el lector devuelve nulo fuera de `/api/`. Ninguna ruta fuera de la API necesita identidad: la entrada Angular y sus archivos son públicos (`SpaForwardFilter.esRecursoPublico`), `/actuator/health` es público y sin detalles, y el resto queda en `denyAll`. Dentro de `/api/` nada cambia: una cookie inválida sigue dando 401 `NO_AUTENTICADO`, también en `GET /api/auth/sesion` y `/api/n/{slug}/sesion`, y la interfaz lleva a «Ingresar» con `returnUrl`. Una ruta de API codificada queda sin identidad (fallo hacia menos acceso; DA-36 ya la rechaza con 400).
- **Pruebas:** `CookieBearerTokenResolverTest` (fuera de `/api/` no lee la cookie, incluso ambigua; dentro sí), `SpaForwardIT` (cookie inválida: entrada Angular y archivos 200; API 401) y la captura `sesion-caducada-ingresar` del E2E.
- **Alcance:** solo v2. La entrega académica (`main`) conserva el comportamiento anterior; queda registrado aparte como **INC-V1-01** (`incidencias-v1.md`), sin cambios en v1.

### V2-DA-49 · Con la barbería suspendida, la interfaz del personal queda en solo lectura (27/10/2026)
- **Problema (observación de la crítica de diseño de V2-07.6, priorizada por el responsable):** con la barbería suspendida, el personal veía formularios y acciones de escritura activos; solo al enviarlos el servidor respondía 409 `NEGOCIO_SUSPENDIDO` (V2-DA-38) y se mostraba el motivo.
- **Decisión:** `NegocioActual.soloLectura` refleja el estado `suspendido` que ya informó el servidor: la consulta pública da 404 y la sesión del negocio responde, o llega un 409 `NEGOCIO_SUSPENDIDO` con la sesión abierta (V2-07.6, `marcarSuspendido`). Con ella, las pantallas del personal desactivan sus escrituras:
  - configuración e invitaciones, con el formulario completo;
  - horarios y bloqueos, con sus formularios y «Eliminar»;
  - servicios y barberos, en «Nuevo», «Editar» y «Activar/Desactivar»;
  - usuarios, en «Restablecer contraseña» y «Activar/Desactivar»;
  - equipo, en «Cambiar rol», «Revocar acceso» y «Salir»;
  - la agenda, en sus transiciones, «Reprogramar» y «Cancelar».
- **Lo que no se desactiva:** las lecturas siguen disponibles (filtros, «Mostrar revocadas», «Ver cambios», reportes y su descarga). El aviso fijo indica el motivo: «Barbería suspendida: puede consultar, pero no hacer cambios. Las acciones de cambio quedan desactivadas hasta que la plataforma la reactive.».
- **Por qué no es una regla de negocio en el frontend:** solo presenta un estado que el servidor ya comunicó. No concede ni retira permisos, y el servidor sigue rechazando toda escritura. Una escritura directa con la misma sesión da 409 `NEGOCIO_SUSPENDIDO` en el E2E 21.
- **Pruebas:**
  - unitaria en `gestion.spec.ts`: configuración, equipo e invitaciones en solo lectura, con «Mostrar revocadas» activo;
  - E2E 21: tras el 409 con la sesión abierta, la pantalla pasa a solo lectura sin recargar; tras recargar siguen desactivados, la escritura directa da 409, y en equipo «Cambiar rol» está desactivado y el interruptor de lectura activo;
  - captura `suspendida-personal`.
- **Alternativa descartada:** `fieldset disabled` sobre toda la vista, porque desactivaría también los filtros y la navegación de lectura.

### V2-DA-50 · Un error se anuncia una sola vez (27/10/2026)
- **Problema (observación heredada de v1, priorizada por el responsable):** el interceptor abría un snackbar con el `detail` de cualquier error, y la vista mostraba el mismo mensaje junto a la acción. El mismo error se veía y se anunciaba dos veces.
- **Decisión:** la vista que presenta el error junto a la acción lo marca con `marcarPresentado`. Lo hacen `mostrarErrores` (todas sus llamadas pintan el mensaje en una región `role="alert"` o `status`), el asistente de reserva, horarios y la aceptación de invitaciones. El interceptor difiere el snackbar a una microtarea, que se ejecuta después del manejador de la vista, y solo lo abre si nadie presentó el error. Los errores sin aviso en la página (p. ej., una acción de una fila que no muestra mensaje propio) siguen llegando al snackbar. Las ramas especiales del interceptor no cambian: 401, 403 revalidado y 409 `NEGOCIO_SUSPENDIDO`.
- **Accesibilidad:** el anuncio se conserva, porque el mensaje de la página está en una región `role="alert"` o `status`.
- **Pruebas:**
  - en `errores-interceptor.spec.ts`, el mismo 409 presentado no abre snackbar y sin presentar sí lo abre; las aserciones del snackbar esperan la microtarea, también las negativas;
  - los E2E de error de reservar, mis citas y agenda exigen ahora que no haya snackbar (antes lo cerraban).

### V2-DA-51 · El salto al contenido no navega (27/10/2026)
- **Problema (detectado en el recorrido 13 del E2E, anterior a V2-07 y presente también en v1):** con `<base href="/">`, el enlace «Saltar al contenido» (`href="#contenido"`, sin manejador) se resolvía como `/#contenido`. Activarlo, con el menú abierto o cerrado, llevaba a la raíz de la aplicación en lugar de mover el foco al contenido de la página actual.
- **Decisión:** `Shell.saltarAlContenido` cancela la navegación y mueve el foco a `main` (`tabindex="-1"`). Si el menú móvil está abierto, lo cierra antes y enfoca al terminar el cierre, para que Material no devuelva el foco al botón que abrió el menú. Es el mismo patrón que ya usa la navegación del shell. El enlace conserva `href` para que siga siendo un enlace accesible.
- **Pruebas:**
  - unitaria en `shell.spec.ts`, con el menú cerrado (escritorio) y abierto (móvil): sin `preventDefault`, las dos fallan;
  - recorrido 13: con el teclado, la URL no cambia y el foco queda en `main`; con el menú abierto, el menú se cierra, la URL no cambia, el foco queda en `main` y el contenido termina visible.
- **v1:** registrado aparte como **INC-V1-02** (`incidencias-v1.md`), sin cambios en `main` ni en Azure.

### V2-DA-52 · Entrada de WhatsApp: el *webhook* solo guarda y el procesador reclama con ficha (28/10/2026)
- **Decisión:**
  - El *webhook* inserta los eventos sin resolver el negocio (`negocio_id` nulo).
  - El procesador los reclama en una transacción corta y sin negocio, con `FOR UPDATE SKIP LOCKED`, que incrementa `intentos` y aplaza `proximo_intento` el tiempo del reclamo.
  - Termina cada evento en otra transacción, ya con el contexto del negocio, después de bloquear la fila y comprobar que `intentos` sigue siendo el de su reclamo (la «ficha»).
- **Por qué:**
  - Un solo negocio por transacción (`04-arquitectura-datos.md` §5): `EjecutorEnNegocio` exige fijar el negocio antes de abrir la transacción, así que el reclamo y el trabajo no pueden compartirla.
  - La ficha impide que un procesador lento termine un evento que otro ya reclamó tras vencer el plazo, y el vencimiento recupera los eventos de un proceso caído.
  - El *webhook* responde antes, porque no consulta canales.
- **Alternativa:** reclamar y procesar en la misma transacción. Exigiría cambiar de negocio dentro de ella o abrir una transacción anidada, y ninguna de las dos cosas se permite.

### V2-DA-53 · Mensajes a un negocio no operativo: se descartan con motivo (28/10/2026)
- **Decisión:** un evento cuyo número no está registrado, cuyo canal está desconectado o cuyo negocio está suspendido o sin propietario activo queda `DESCARTADO`, con un `motivo` codificado. No se entrega al puerto ni se abre el contexto del negocio.
- **Por qué:** con la barbería suspendida no se admiten escrituras (V2-DA-38), y sin propietario no hay canal (`06-flujos.md` §1). En V2-08 todavía no hay salida con la que avisar al cliente.
- **Revisable en V2-09/V2-10:** una respuesta fija («esta barbería no atiende por WhatsApp ahora») si se considera útil.
- **Excepción (28/10/2026, V2-09, hallazgo 111-7):** los avisos de **estado** de un canal registrado se procesan aunque el canal esté desconectado o el negocio no sea operativo, porque registran el resultado de mensajes que ya salieron. Los mensajes siguen esta regla.

### V2-DA-54 · Ficha por `wa_id` exacto, sin crearla en V2-08 (28/10/2026)
- **Decisión:**
  - El procesador busca la ficha **solo** por `(negocio del número, wa_id del remitente)`, y entrega al puerto `ReceptorMensajesWhatsApp` el `clienteId`, o vacío si no hay ficha.
  - No la busca por teléfono ni en otro negocio, y no crea fichas ni conversaciones: eso llega en V2-10, tras el consentimiento (`06-flujos.md` §6).
  - El remitente sale de `messages[].from` (verificado por WhatsApp), nunca del texto ni de `contacts[]`.
- **Por qué:** V2-RN-09 (una ficha creada con un teléfono no se vincula sola a un `wa_id`) y V2-RN-02 (el negocio sale del número receptor). Crear fichas sin consentimiento contradiría el flujo de privacidad.
- **Matiz (28/10/2026, V2-09):** la **conversación** se crea ya en V2-09, al recibir un mensaje, solo con `wa_id` y `ultima_entrada_en`, porque la ventana de 24 h la necesita (V2-DA-60). La ficha sigue sin crearse hasta V2-10.

### V2-DA-55 · Columnas añadidas a la cola y configuración que falla cerrada (28/10/2026)
- **Decisión:**
  - `evento_whatsapp_entrante` añade `tipo` (`MENSAJE` o `ESTADO`) y `motivo` (código, sin texto libre) al DDL de §3.
  - `cliente` añade `wa_id`, solo con dígitos y único por negocio, y amplía `cliente_identificable`.
  - Sin `BT_WHATSAPP_VERIFY_TOKEN` o sin `BT_WHATSAPP_APP_SECRET`, la verificación da 403 y toda notificación 401: nunca se acepta un evento sin firma comprobada. Al arrancar se registra un aviso, sin valores.
- **Por qué:** el procesador necesita distinguir mensajes y estados sin volver a analizar el cuerpo; el motivo permite diagnosticar sin registrar datos personales; un secreto vacío no puede convertirse en una firma válida.

### V2-DA-56 · Alta manual del canal por la plataforma (28/10/2026)
- **Decisión:**
  - `ADMIN_PLATAFORMA` registra o actualiza el canal de un negocio con `PUT /api/plataforma/negocios/{id}/canal-whatsapp` y lo desconecta con `POST …/desconexion`. Las dos operaciones se auditan en `auditoria_plataforma`.
  - Un `phone_number_id` que ya es de otro negocio da 409 `NUMERO_WHATSAPP_EN_USO` (código nuevo). No hay pantalla en V2-08.
- **Por qué:** V2-RF-09 prevé el alta asistida por la plataforma en el MVP. Una ruta de API auditable evita escribir datos de un negocio desde la configuración del despliegue, y el índice único de `phone_number_id` impone V2-RN-07 también en la base.
- **Alternativa:** registrar el número de prueba desde variables de entorno al arrancar. Más simple, pero mezcla configuración y datos de negocio, y no deja auditoría.
- **Ajuste (28/10/2026, V2-09, hallazgo 111-8):** sustituir el `phone_number_id` de un canal cuyo negocio ya tiene conversaciones da 409 `CONFLICTO`: los avisos de estado de lo enviado por el número antiguo dejarían de encontrar su canal.
### V2-DA-57 · Outbox por negocio con ficha y orden por conversación (28/10/2026)
- **Decisión:**
  - `mensaje_whatsapp` es una tabla de negocio con RLS forzada: el enviador no puede reclamar sin contexto. Recorre los `negocio_id` de `canal_whatsapp` (tabla de plataforma) y, por cada uno, reclama **de uno en uno** con `SKIP LOCKED` en transacciones cortas dentro de `EjecutorEnNegocio`, para que el plazo del reclamo empiece justo antes de trabajar cada mensaje (propuesta 111-3).
  - El reclamo incrementa `intentos`, que es la ficha (como en V2-DA-52); la preparación y el cierre se condicionan a ella, y la petición HTTP va entre ambos, sin transacción ni bloqueos. La forma del envío (`enviado_como`) se guarda en la preparación, antes de la petición.
  - No se reclama un mensaje si hay otro `PENDIENTE` anterior de la misma conversación: el cliente los recibe en el orden en que se encolaron.
  - Columnas añadidas al DDL de §3: `reserva_id`, `enviado_como`, `inciertos`, `codigo_meta`; `error_codigo` acotado a los motivos. Permisos exactos tras `REVOKE ALL` (V100 concede todos por defecto), entidades de solo lectura y escrituras SQL acotadas.
  - Dos hilos de programación (`spring.task.scheduling.pool.size: 2`), para que el envío no retrase el procesador de entrada.
- **Por qué:** RLS no admite una consulta global (V2-DA-07) y un *security definer* la saltaría; mantener una transacción abierta durante la llamada a Meta retendría conexiones y bloqueos. El volumen previsto (cientos de mensajes al día y decenas de negocios) admite una transacción corta por negocio y pasada.
- **Consecuencias:** un mensaje con errores transitorios retrasa los siguientes de su conversación hasta agotar sus reintentos (unos 7 min). Con muchos negocios habría que recorrer solo los que tengan pendientes (una marca en una tabla de plataforma); queda para la Fase 3.

### V2-DA-58 · Garantía de entrega: no hay «exactamente una vez» (28/10/2026)
- **Hecho:** la Cloud API no admite una clave de idempotencia en el envío. Si Meta acepta un mensaje y la respuesta no llega, reenviarlo puede duplicarlo y no reenviarlo puede perderlo.
- **Decisión: al menos una vez, con duplicados acotados y conciliación.**
  - **Conciliación:** cada envío lleva `biz_opaque_callback_data = "bt:m:<id>:<ficha>"`, que Meta devuelve en los avisos de estado. Un aviso para un `wamid` desconocido cuyo dato apunta a un mensaje del mismo negocio y del mismo destinatario, sin `wamid`, le asigna ese `wamid` y su estado; si el mensaje ya tiene otro `wamid` (un duplicado), solo se registra la anomalía.
  - **Resultado desconocido** (tiempo de respuesta agotado, conexión cortada o éxito sin `id`): se espera `espera-conciliacion` (2 min) por si un aviso de estado lo concilia y, si no, se reenvía **una sola vez** (`max-inciertos = 2`). Un segundo resultado desconocido deja `FALLIDO`/`RESULTADO_DESCONOCIDO` con aviso al personal («no se pudo confirmar»).
  - **La petición no llegó a Meta** (consta un fallo al conectar) y los rechazos transitorios: reintento con espera, sin riesgo de duplicar.
  - **Caída del proceso, o fallo del cierre,** entre la aceptación de Meta y el cierre: el reclamo vence a los 60 s y el mensaje se reenvía, salvo que un aviso de estado lo haya conciliado antes.
- **Por qué** (propuesta 111-4 de Codex, aceptada): el mensaje acompaña a una operación ya confirmada (V2-RN-11), y perder un aviso de cancelación o de cambio perjudica más al cliente que recibirlo dos veces. La espera de conciliación evita el duplicado en el caso habitual, en que Meta sí aceptó el mensaje y su aviso `sent` llega en segundos.
- **Alternativa descartada:** como mucho una vez (sin reenviar un resultado desconocido). No duplica, pero pierde los mensajes que no llegaron a Meta y, ante una caída del proceso, seguiría reenviando: la garantía dependería de dónde se produjera el fallo.
- **Revisable por el responsable:** es una decisión de producto con contrapartida (duplicado frente a pérdida); se puede cambiar con `max-inciertos = 1`.
- **Precisión de lo que garantiza el código (29/10/2026, revisión de Codex 116; la política no cambia):**
  - **Persistencia:** el mensaje vive en la outbox desde la transacción que lo origina. Antes de cada POST, el reclamo incrementa y confirma `intentos` (la ficha) en su propia transacción.
  - **Intentos acotados, no entrega garantizada:** como máximo `max-intentos` POST por mensaje (5 con la configuración actual), también con caídas o reclamos vencidos. Agotados los intentos, o ante un fallo permanente, una caducidad o un segundo desconocido, el mensaje queda `FALLIDO` y no se entrega. «Al menos una vez» describe la intención de reenviar ante la duda, **no** una garantía de que todo mensaje llegue.
  - **Resultado desconocido:** el cierre confirmado incrementa `inciertos` y guarda, en la misma transacción, el siguiente intento tras la espera de conciliación. Eso **sí** se conserva frente a un reinicio. Con `max-inciertos = 2`, el segundo desconocido **contabilizado** deja `FALLIDO`/`RESULTADO_DESCONOCIDO`.
  - **Lo que el límite no cubre** (hallazgos H-116-01 y H-116-02, abiertos):
    - un desconocido cuyo cierre se pierde (caída del proceso entre la respuesta y el cierre) o se descarta por ficha antigua (reclamo vencido y otro enviador) no incrementa `inciertos`;
    - un rechazo transitorio (429, 5xx) o `NoEnviado` después de un desconocido sigue la vía de reintentos.

    Por eso puede haber **más de un reenvío** tras un primer desconocido, siempre dentro de `max-intentos`.
  - **Duplicados:** son posibles tantas aceptaciones de Meta como POST cuyo resultado no llegó a cerrarse; con la configuración actual, hasta 5 envíos del mismo mensaje en el peor caso. La conciliación evita los reenvíos cuando el aviso `sent` llega a tiempo, pero no retira una solicitud ya enviada ni deduplica en Meta. `max-inciertos = 1` no elimina los duplicados por cierres perdidos.
  - **Decisión pendiente del responsable:** si «un único reenvío» debe ser un límite absoluto, hay que cambiar la política. Por ejemplo, con un presupuesto de envíos potencialmente aceptados que se consuma y confirme **antes** del HTTP, y que cuente un intento preparado sin cierre como desconocido. No se aplica sin su decisión.

### V2-DA-59 · Avisos de estado monótonos por `wamid` (28/10/2026)
- **Decisión:**
  - `sent` < `delivered` < `read`: un aviso solo hace avanzar el mensaje, así que los duplicados y los desordenados no cambian nada.
  - `failed` → `FALLIDO`/`FALLO_ENTREGA` con el código de Meta, salvo tras `delivered` o `read`. Cualquier otro `FALLIDO` es definitivo, excepto `RESULTADO_DESCONOCIDO`, que la conciliación puede corregir. Un estado positivo vacía el motivo y el código.
  - Un aviso para un `wamid` que no se encuentra en el negocio, ni por conciliación, lanza una excepción y se reintenta con la política de V2-08, por la carrera entre la respuesta de Graph y el cierre del enviador; si persiste, el evento acaba en `ERROR`.
- **Por qué:** Meta reintenta durante días y no garantiza el orden de los avisos; RLS limita la búsqueda al negocio del canal que recibió el aviso.

### V2-DA-60 · Ventana de 24 h y selección de plantillas al enviar (28/10/2026)
- **Decisión:**
  - Cada mensaje entrante actualiza `conversacion.ultima_entrada_en` (crea la conversación si no existe, sin ficha ni contenido). Es la única escritura de V2-09 en la entrada; V2-10 la conservará en su orquestador.
  - La ventana se evalúa **al enviar**, con un margen de 5 min: el texto y el interactivo con la ventana cerrada fallan sin llamar a Meta (`VENTANA_CERRADA`).
  - `encolarAviso` guarda la plantilla con `textoEnVentana`: con la ventana abierta se envía su texto como mensaje de servicio y, si no, como plantilla. `encolarPlantilla` envía siempre la plantilla.
  - Catálogo fijo de tres plantillas *utility* en español con parámetros con nombre (12-v2-09 §3.4); Graph API v26.0.
- **Por qué:** un mensaje encolado con la ventana abierta puede enviarse tras cerrarse (reintentos); desde el 01/10/2026 las plantillas *utility* dentro de la ventana se cobran y los mensajes de servicio tienen 1000 gratuitos al mes por número (`09-costes.md`).
- **Matiz de V2-DA-54:** crear la conversación (sin ficha) al recibir un mensaje se adelanta de V2-10 a V2-09, porque la ventana la necesita. La ficha sigue creándose en V2-10, tras el consentimiento. La retención de las conversaciones sin ficha queda con P2-06.

### V2-DA-61 · Aviso interno ante un fallo permanente (28/10/2026)
- **Decisión:**
  - Toda transición a `FALLIDO` (o cambio de su motivo) de un mensaje con `reserva_id` crea, en la misma transacción, un aviso (`notificacion`, tipo nuevo `AccionAuditoria.WHATSAPP`) a los propietarios y encargados con cuenta y membresía activas y al profesional asignado en ese momento, sin repetir. Es un aviso por transición, no por mensaje.
  - El texto da el código de la reserva y el motivo, sin datos del cliente ni el contenido del mensaje; para `RESULTADO_DESCONOCIDO` dice «no se pudo confirmar si se envió».
  - **Pendiente de interfaz:** la cabecera oculta los avisos al rol efectivo `ADMIN` (propietario y encargado), por RN-15 de v1. Mostrárselos es un cambio de pantalla posterior a la integración de V2-07 (`12-v2-09-whatsapp-salida.md` §7).
  - Sin reserva, solo se registra un `WARN` con el id y el motivo.
- **Por qué:** `06-flujos.md` §10 exige avisar al personal para que contacte al cliente, y la operación no se deshace. `notificacion` exige una reserva; los mensajes sin reserva (menús, respuestas) tendrán su seguimiento en la bandeja (V2-11).

### V2-DA-62 · Configuración de la salida y token de la Fase 1 (28/10/2026)
- **Decisión:** `barberturno.whatsapp.salida` en un registro propio (`SalidaWhatsAppPropiedades`), con el token de la app en `BT_WHATSAPP_ACCESS_TOKEN` mientras `canal_whatsapp.token_cifrado` sea nulo (número de prueba, V2-DA-14). Sin token, el enviador no reclama nada y los mensajes quedan pendientes; cuando por fin se preparan, los de más de 6 h caducan (`MENSAJE_CADUCADO`) en lugar de llegar tarde. El token nunca se registra: el `toString` lo oculta. La versión de la Graph API (v26.0) se fija en la configuración.
- **Por qué:** no cambia el registro de configuración de V2-08 y falla cerrado, como V2-DA-55.

### V2-DA-63 · Conversación guiada: un solo recorrido e identificadores autocontenidos (29/10/2026)
- **Decisión:** menú principal con **Reservar** y **Mis citas**. El recorrido de reserva se puede seguir sin ficha (consulta de servicios, profesionales y franjas, `06-flujos.md` §6) y pide el consentimiento solo al elegir una franja; tras aceptar, continúa con esa franja. Cada botón o fila lleva en su identificador lo que el paso siguiente necesita (`13-v2-10-conversacion-guiada.md` §3.1). El identificador no es de confianza: el servidor revalida el negocio por RLS, la ficha, el estado y las reglas. `conversacion.estado` guarda solo la intención y el paso. «Hablar con alguien» no aparece hasta V2-11. El *opt-in* y la baja de recordatorios pasan a V2-14, junto a los recordatorios. Los mensajes entrantes no se guardan (no hacen falta sin bandeja ni IA).
- **Por qué:** un recorrido sin estado oculto es determinista y fácil de probar. Lo que caduca o se sustituye es la operación pendiente, no una sesión de menú. Separar la consulta del consentimiento cumple el flujo de privacidad sin pedirlo antes de tiempo.
- **Alternativa descartada:** guardar servicio, profesional y fecha en `estado`. Es más frágil frente a mensajes desordenados y reintentos, y contradice el vaciado del §7.1.

### V2-DA-64 · Reservas de una ficha sin cuenta web (29/10/2026)
- **Problema:** `ReservaService` exige un `UsuarioAutenticado`. Además, `reserva.creada_por` y `auditoria_reserva.actor_id` son `NOT NULL` hacia `usuario`. Una ficha creada por WhatsApp no tiene cuenta.
- **Decisión (V115):**
  - en `reserva`: `canal` (`WEB`/`WHATSAPP`, por defecto `WEB`) y `creada_por` anulable solo si `canal = 'WHATSAPP'`;
  - en `auditoria_reserva`: `actor_cliente_id` (FK compuesta a `cliente`) y exactamente un actor (`num_nonnulls = 1`);
  - en `ReservaService`: métodos de ficha (crear, reprogramar y cancelar como la propia ficha) con las reglas, el protocolo ①②③ y la revalidación tras los bloqueos de la web, y la propiedad comprobada tras ③;
  - sin aviso interno al cliente si no tiene cuenta: la respuesta de WhatsApp lo sustituye. El profesional sí recibe su aviso;
  - los consumidores de `creador` y `actor` (historial, DTO) toleran el actor de ficha.
- **Alternativas descartadas:**
  - un usuario técnico «asistente» como creador: atribuye mal la auditoría;
  - crear una cuenta web para cada ficha: contradice V2-RN-09 y V2-15.

### V2-DA-65 · Una transacción por mensaje y orden de bloqueos de la conversación (29/10/2026)
- **Decisión:**
  - todo el tratamiento de un mensaje ocurre en la transacción de entrega de V2-08, que también marca el evento: conversación, ficha, operación, reserva, auditoría, avisos y outbox se confirman o se revierten juntos;
  - orden de bloqueos: **conversación → operación pendiente → ⓪ → ① → ② → ③**. La web nunca toma los dos primeros, así que no hay ciclos;
  - dentro de la transacción se comprueba de nuevo que el negocio no está suspendido y que el canal sigue activo: si no, no hay respuesta ni escritura.
- **Por qué:** sin un *outbox* coherente, un «Listo» podría salir para una reserva revertida (V2-RN-11). Bloquear la conversación serializa los mensajes de un mismo cliente que procesan dos hilos.

### V2-DA-66 · Rechazos de dominio sin revertir la entrega (29/10/2026)
- **Problema:** una `NegocioException` que atraviesa un método `@Transactional` que participa en la transacción de entrega la marca *rollback-only*. El rechazo («la franja se ocupó») se perdería y el evento se reintentaría hasta `ERROR`.
- **Decisión:**
  - los métodos de ficha de `ReservaService` llevan `noRollbackFor = NegocioException.class`, como `AuthService`;
  - toda `NegocioException` de esos caminos se lanza **antes de la primera escritura**, y una prueba lo demuestra (sin reserva, auditoría ni aviso tras el rechazo);
  - la operación queda `RECHAZADA` y la respuesta va al outbox;
  - los errores técnicos (la `EXCLUDE` como red de seguridad, la base) revierten todo y el evento se reintenta: la operación sigue `VIGENTE` y, en el reintento, la revalidación la rechaza.
- **Alternativas descartadas:**
  - `REQUIRES_NEW` para la reserva: separa la reserva del outbox y de la operación;
  - *savepoints* con Hibernate: el contexto de persistencia queda incoherente tras volver al punto.

### V2-DA-67 · Retención configurable de la conversación (29/10/2026)
- **Decisión:** trabajo programado por negocio (`EjecutorEnNegocio`), en lotes:
  1. borra las conversaciones **sin ficha** sin entradas en `retencion.conversaciones-sin-ficha` (**30 días**, ya decidido en `04-arquitectura-datos.md` §3 y `06-flujos.md` §6) y sin mensajes `PENDIENTE`, con sus mensajes;
  2. vacía (`{}`) el cuerpo de los eventos entrantes terminados tras `retencion.cuerpo-eventos`. **Sin valor por defecto:** desactivado hasta que P2-06 fije el plazo.
- **Defensa en la base (V115):** `DELETE` solo con políticas **restrictivas**, que impiden borrar una conversación con ficha o un mensaje pendiente o ligado a una reserva aunque el código lo intente.
- **Fuera:** el contenido de los mensajes de conversaciones con ficha (V2-11 y P2-06).
- **Por qué:** cumple lo decidido sin inventar plazos. Los plazos pendientes quedan como propuesta en `13-v2-10` §10.

### V2-DA-68 · Texto de privacidad por configuración, sin valor en producción (29/10/2026)
- **Decisión:**
  - el texto y el enlace del aviso de privacidad vienen de `barberturno.whatsapp.conversacion.privacidad`;
  - solo `dev` y `test` traen un borrador marcado «BORRADOR (pendiente de P2-06)»;
  - sin valor, la aplicación no arranca (`@Validated`), así que nunca se usa en producción un texto no aprobado;
  - el resto de textos de la conversación (menús, resultados) son textos de interfaz del servidor (V2-RN-11), no textos legales.
- **Por qué:** P2-06 sigue pendiente. El texto legal lo aprueba el responsable (con el docente o el asesor).

### V2-DA-69 · La ficha en `ReservaDto` sin cambiar el significado de `cliente.id` (29/10/2026)
- **Problema (consulta 117 de Codex, punto 2):** `ReservaDto.cliente.id` es el id de la **cuenta** (V2-DA-29), y una reserva de una ficha de WhatsApp no tiene cuenta. Usar ahí el id de la ficha mezclaría dos espacios de identificadores que pueden coincidir y nombrar a personas distintas.
- **Decisión:** `cliente.id` pasa a anulable y sigue siendo la cuenta. Se añade `cliente.fichaId`, siempre presente. El nombre y el teléfono salen de la ficha. El *frontend* de la rama de V2-10 no cambia: el tipo `reservas.ts` se ajusta al integrar con V2-07, que ya planea pasar a ids de ficha (V2-10/11).
- **Por qué:** es un cambio compatible para los clientes con cuenta y evita adelantar la migración completa a ids de ficha.

### V2-DA-70 · Presupuestos de envío confirmados antes del HTTP (04/11/2026; corrige H-116-01/02)
- **Política autorizada por el responsable (04/11/2026):**
  - se conserva el máximo global de **5 intentos** por mensaje;
  - después del **primer intento cuyo resultado sea desconocido**, se permite **como máximo un intento HTTP adicional**;
  - ese intento consume el permiso aunque reciba un 429 o vuelva a quedar desconocido;
  - ni los reinicios ni los trabajadores concurrentes restablecen ninguno de los dos presupuestos.
- **Decisión:** los dos presupuestos se registran y se **confirman en la base antes de cada llamada HTTP**, que sigue fuera de la transacción:
  1. **intento reservado:** el reclamo incrementa `intentos` (la ficha), como hasta ahora;
  2. **presunción de resultado desconocido:** la transacción que prepara el envío deja el intento contado como desconocido (`inciertos`) y, si ya había uno, consume el intento adicional. Así, un intento cuyo cierre se pierde (caída, reclamo vencido, cierre descartado por ficha antigua) **queda contado sin depender del cierre**;
  3. **cierre:** solo **retira** la presunción cuando la respuesta demuestra que Meta no aceptó esa solicitud (rechazo transitorio o permanente, `NoEnviado`), salvo en el intento adicional, que se consume igualmente;
  4. tras el intento adicional, cualquier resultado distinto de la aceptación termina el mensaje como `FALLIDO`/`RESULTADO_DESCONOCIDO` (o con su motivo permanente).
- **Estados que se distinguen:**
  - **intento reservado:** `intentos`, confirmado al reclamar;
  - **solicitud efectuada:** solo se sabe que se iba a efectuar. Una caída tras confirmar la preparación y antes o durante el HTTP es indistinguible de una solicitud sin respuesta, y se cuenta como desconocida;
  - **aceptación del proveedor:** respuesta con `wamid` → `ENVIADO`;
  - **entrega confirmada:** aviso de estado `delivered` o `read` → `ENTREGADO`/`LEIDO`.
- **Consecuencias, documentadas sin prometer más:**
  - una caída **antes** de la llamada puede consumir un intento reservado e incluso el presupuesto del resultado desconocido sin que haya salido ninguna solicitud;
  - la política **no garantiza la entrega ni la ausencia de duplicados**: el primer intento desconocido y el adicional pueden haber sido aceptados ambos por Meta;
  - el tope de solicitudes potencialmente aceptadas tras el primer desconocido es **una**, y el total sigue acotado por `max-intentos`;
  - la espera de conciliación (2 min) se respeta también cuando el desconocido se detecta por un cierre perdido.
- **Sin migración nueva:** se reutilizan `intentos` e `inciertos` (V114), con un significado precisado en el Javadoc. `max-inciertos` deja de ser configurable: el presupuesto adicional lo fija esta política.
- **Sustituye** a las reglas de reenvío de V2-DA-58 (la precisión del 29/10 queda como historial) y cierra **H-116-01/02**.

### V2-DA-71 · Derivación a una persona solo con ficha (04/11/2026, V2-11)
- **Decisión:** pedir una persona (botón «Hablar con alguien», palabra clave `PERSONA` o dos textos no reconocidos seguidos) deriva solo si la conversación tiene ficha; sin ficha, se pide antes el consentimiento con la derivación como pendiente. Un `CHECK` exige ficha en modo `HUMANO`.
- **Por qué:** el personal ve el nombre y el historial del cliente; sin consentimiento no hay base para mostrárselo ni para guardar sus mensajes (P2-06).
- **Alternativas:** derivar sin ficha mostrando solo el número enmascarado (el personal respondería a alguien que no aceptó el tratamiento).

### V2-DA-72 · Mensajes entrantes guardados: opción mínima configurable (04/11/2026, V2-11; D6 pendiente)
- **Decisión:** `barberturno.whatsapp.bandeja.entrantes = SOLO_HUMANO` (predeterminado en todos los perfiles): se guarda el mensaje que provoca la derivación y los del modo humano, con un contenido mínimo (texto, título de la selección o tipo), nunca el cuerpo crudo. `TODOS` existe y se prueba, pero solo se activa si el responsable lo decide en D6.
- **Por qué:** la bandeja necesita leer al cliente en modo humano; guardar todo el modo asistente no es necesario para el MVP y amplía los datos tratados.

### V2-DA-73 · Bandeja web con permisos del servidor y ventana efectiva compartida (04/11/2026, V2-11)
- **Decisión:** API bajo `/api/n/{slug}/whatsapp/conversaciones` para PROPIETARIO y ENCARGADO (regla gruesa `ROLE_ADMIN` + membresía comprobada en el servicio, repetida tras el bloqueo). La ventana es la del enviador de V2-09 (24 h menos el margen); la API publica el vencimiento nominal y `puedeResponder`/`motivoNoResponder` calculados por el servidor. Respuesta con origen `PERSONA` y `enviado_por`; los fallos se muestran en la bandeja, sin avisos. El enviador descarta (`MODO_HUMANO`) los mensajes del asistente pendientes de una conversación ya derivada. Avisos de derivación con `notificacion.conversacion_id` y `AccionAuditoria.DERIVACION`.
- **Excepción a V2-DA-65:** la bandeja y la anonimización toman la conversación desde la web, siempre como primer bloqueo; nunca se bloquea una conversación después de un mensaje.

### V2-DA-74 · Retención de contenidos y operaciones (04/11/2026, V2-11; plazos D4 pendientes)
- **Decisión:** dos fases nuevas en `RetencionWhatsApp` (una transacción por negocio y fase): retirar el contenido (`{"retirado": true}`) de los mensajes no pendientes de conversaciones con ficha y borrar las operaciones no vigentes. Un disparador y una política restrictiva impiden en SQL lo que nunca se puede retirar o borrar. **Plazos:** vacíos (desactivado) en la configuración base y en `prod`; 30/90/30 días solo en `dev` y `test`, marcados «BORRADOR P2-06».

### V2-DA-75 · Anonimización de una ficha de WhatsApp a petición (04/11/2026, V2-11; opción propuesta de D5, no aprobada)
- **Decisión:** `POST /api/n/{slug}/clientes/{id}/anonimizar` (solo PROPIETARIO): fichas sin cuenta web y sin reservas activas ni mensajes pendientes; marca la ficha («Cliente eliminado», sin `wa_id` ni teléfono, inactiva, `anonimizada_en`) y borra después sus avisos de derivación, operaciones, mensajes y conversación; conserva reservas y auditoría. **Excepción a V2-DA-67:** se pueden borrar mensajes con `reserva_id` de una ficha anonimizada.
- **Límite declarado:** no vacía los cuerpos crudos de los eventos (orden de bloqueos de la entrega); queda para P2-06.
- **Alternativas:** borrado en cascada (pierde el historial de reservas y auditoría); la decisión final es del responsable (D5).

### V2-DA-76 · Retorno automático al asistente (04/11/2026, V2-11)
- **Decisión:** trabajo programado cada 5 min, sin transacción exterior, por negocio y en lotes: conversaciones en `HUMANO` sin respuesta del personal durante 24 h (`barberturno.whatsapp.bandeja.retorno-automatico`) vuelven al asistente, comprobado bajo bloqueo; el aviso al cliente y el menú solo si la ventana efectiva está abierta.

### V2-DA-77 · Turno de IA fuera de la transacción de entrega (04/11/2026, V2-12; consulta 127)
- **Decisión:** la entrega solo encola un `turno_ia`; un trabajador lo reclama con ficha (`intentos`) y concesión renovable, llama al modelo **sin bloqueos abiertos**, ejecuta las herramientas en transacciones cortas y aplica un único efecto en una transacción final que comprueba `conversacion.revision` (disparador en cada actualización), el modo, la ficha y la operatividad. La propuesta real solo se crea en esa transacción.
- **Por qué:** una llamada de segundos dentro de la entrega retendría la conexión y los bloqueos, y un fallo del proveedor repetiría la llamada (coste) con los reintentos de V2-08. Sigue la regla de V2-09: nada de HTTP con bloqueos. Un turno descartado nunca deja una operación vigente que el cliente no haya visto (V2-RN-10).
- **Alternativas:** llamar al modelo en la entrega con un tiempo máximo (más simple, pero con los problemas anteriores); un contador solo de entradas (no cubre derivación, bandeja ni estado).

### V2-DA-78 · Herramientas con alias opacos y una sola propuesta por turno (04/11/2026, V2-12)
- **Decisión:** el modelo solo ve alias del turno (`s1`, `p1`, `c1`, `f1`); las propuestas aceptan solo alias de franjas devueltas en ese turno, con su finalidad (crear o cambiar una cita). Como mucho un efecto por turno (derivación > consentimiento > propuesta > franjas); dos propuestas distintas → ninguna y aclaración. Las franjas las compone siempre el servidor como lista cuyas filas son selecciones de V2-10.
- **Por qué:** V2-RNF-05 (sin ids internos), V2-RN-12 (sin horarios heredados ni inventados) y V2-RN-10 (un único resumen vigente).

### V2-DA-79 · Costes con reserva previa y agregado que sobrevive a la purga (04/11/2026, V2-12)
- **Decisión:** antes de cada llamada se reserva en `llamada_ia` el coste máximo bajo un bloqueo consultivo del negocio; después se liquida con el uso real (también en los fallos con uso conocido); el coste desconocido queda reservado. `uso_ia` agrega por `(negocio_id, dia)` (corrige `04-…` §3, que lo indexaba por conversación) y no se borra con la conversación. Mensajes entrantes por minuto contados en el registro de la entrada.
- **Por qué:** dos turnos concurrentes no deben superar el presupuesto; los turnos descartados o fallidos también cuestan.

### V2-DA-80 · Adaptador de Anthropic con la API estable, sin `fallbacks` del servidor (04/11/2026, V2-12)
- **Decisión:** SDK oficial `com.anthropic:anthropic-java`, `messages().create` y bucle manual; herramientas `strict`, `tool_choice` automático, sin parámetros de muestreo, esfuerzo configurable, `cache_control` en el bloque estable; la continuación del asistente (pensamiento incluido) vuelve intacta dentro del turno; Jackson 2 del SDK encapsulado. El respaldo `fallbacks` del servidor solo existe en la API beta del SDK de Java: **se omite**; el respaldo son los menús. `refusal` y `max_tokens` fallan antes de ejecutar herramientas.

### V2-DA-81 · Contexto del modelo por lista blanca (04/11/2026, V2-12; V2-RNF-05)
- **Decisión:** el contexto se construye solo con: nombre del negocio, catálogo y profesionales con alias, fecha y hora de Lima, nombre de pila del cliente, borrador con ids validados y los últimos mensajes de asistente y sistema saneados. Nunca mensajes del personal, teléfonos, correos, `wa_id`, códigos ni cuerpos crudos; el texto del cliente se sanea. El texto de un cliente sin ficha se guarda solo mientras su turno está pendiente (≤ 15 min).

### V2-DA-82 · Evaluación determinista en `verify` y arnés en vivo aparte (04/11/2026, V2-13)
- **Decisión:** `ConversacionGuionesIT` con guiones YAML (SnakeYAML seguro, ya en el *classpath*) y el adaptador falso en `verify`; arnés en vivo en el perfil Maven `evaluacion-en-vivo`, con nombre fuera de `*Test`/`*IT`, que se niega a ejecutarse sin clave, base desechable y tope de gasto; observador de evaluación vacío en producción. El conjunto (96 + 24) es un borrador hasta que lo revise el responsable.

### V2-DA-83 · El texto que lleva a la IA a derivar se guarda para la bandeja (04/11/2026, V2-12; hallazgo H-XI-06 de Codex)
- **Contexto:** con `entrantes = SOLO_HUMANO` (V2-DA-72) los mensajes que llegan en modo asistente no se guardan. Cuando un turno de IA aplica el efecto `Derivar`, el texto que lo provocó quedaba solo en `turno_ia.texto` (que se vacía al cerrar) y el personal veía la conversación derivada sin saber qué pidió el cliente.
- **Decisión:** en la transacción final del turno, con la conversación ya bloqueada y **antes** de `DerivacionConversacion.derivar`, `RegistroEntradaWhatsApp.guardarTextoDerivado` guarda un `ENTRANTE`/`CLIENTE`/`RECIBIDO` de tipo `TEXTO` con el texto **saneado** del turno (`SaneadorTexto`, hasta 2000 caracteres) y **sin `wamid`** (no se inventa la identidad de Meta). Precede al acuse (la bandeja ordena por `id`). Con `entrantes = TODOS` no se guarda otra vez (ya lo guardó la entrega). Sin ficha no aplica: la IA pide consentimiento y, al aceptarlo, la aceptación es el primer mensaje visible (como en V2-11).
- **Motivo:** completa V2-DA-72 sin ampliar los datos guardados: el personal ve la versión saneada (sin teléfonos ni correos), que es la misma que ya se envió al proveedor; si D6 se aprueba con otra opción, la regla se revisa con ella.
- **Afecta:** `17-v2-12-13-ia-y-evaluacion.md` §3.3 (paso 5); `TurnosIAService`, `RegistroEntradaWhatsApp`; prueba `TurnosIAIT.derivacionPorIA_guardaElTextoSaneadoDelCliente_antesDelAcuse`.
