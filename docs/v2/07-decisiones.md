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
