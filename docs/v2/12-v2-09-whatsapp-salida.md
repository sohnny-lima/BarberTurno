# BarberTurno v2 · 12 · V2-09 · Salida de WhatsApp: alcance, contrato y reparto

> Autorizada por el responsable el 28/10/2026. Rama `tarea/V2-09-whatsapp-salida` desde `tarea/V2-08-whatsapp-entrada` = `233dfbf`, porque `v2` (`ccdbb29`) aún no contiene V2-08. Sin cambios de V2-07. Clase **Crítica**.
>
> **Reparto (responsable, 28/10/2026):**
> - Claude coordina, fija el contrato y revisa el conjunto.
> - Codex implementa el código de producción y la migración, y revisa las pruebas de Gemini.
> - Gemini diseña y escribe las pruebas, los dobles del proveedor y los ajustes de *fixtures*, y revisa el código de producción que no ha escrito.
>
> El contrato incorpora la revisión de diseño de Codex (encargo 111, 16 propuestas, todas aceptadas; §8).
>
> Requisitos: V2-RN-11, V2-RN-13, V2-RNF-01, V2-RNF-04 (sin secretos en registros). Arquitectura: `04-arquitectura-datos.md` §3, §5 y §6.2; `06-flujos.md` §10; decisiones V2-DA-08, 09, 52…56 y V2-DA-57…62 (nuevas).

## 1. Alcance
**Entra:**
- Outbox transaccional `mensaje_whatsapp` (`SALIENTE`) y su servicio de encolado, que exige la transacción del cambio que lo origina.
- Enviador periódico, por negocio, que reclama los mensajes de uno en uno con `SKIP LOCKED` y ficha, y respeta el orden por conversación.
- Cliente HTTP de la Graph API oficial (`POST /{versión}/{phone_number_id}/messages`).
- Reintentos de errores transitorios, tratamiento de los permanentes y del resultado desconocido, con conciliación por los avisos de estado.
- Estados `sent`, `delivered`, `read` y `failed` por `wamid`: implementación del puerto `ReceptorEstadosWhatsApp` de V2-08.
- Ventana de atención de 24 h: implementación del puerto `ReceptorMensajesWhatsApp` que **solo** registra la última entrada en `conversacion`.
- Catálogo de plantillas *utility* y selección entre texto y plantilla.
- Aviso interno al personal cuando falla de forma permanente un mensaje ligado a una reserva.
- Ajustes de V2-08 que la salida necesita: los avisos de estado de un canal desconectado o de un negocio no operativo se procesan; la clave de idempotencia admite `wamid` de 128 caracteres; el número de un canal con conversaciones no se sustituye.

**No entra:**
- Quién produce los mensajes: menús, consentimiento, fichas y operaciones (V2-10); recordatorios con *opt-in* (V2-14); bandeja humana (V2-11); IA (V2-12).
- Guardar el contenido de los mensajes entrantes (`ENTRANTE`/`RECIBIDO`): llega con V2-10, junto con su retención (P2-06).
- Crear las plantillas en Meta: con el número de prueba se crean a mano (P2-05); en la Fase 3, al conectar el canal (V2-16).
- Token cifrado por canal (V2-16): en la Fase 1 se usa el token de la app del entorno.
- Pantallas (ver §7 sobre la visibilidad de los avisos para propietarios y encargados).

## 2. Meta: lo comprobado y lo que sigue bloqueado
**Comprobado en la documentación oficial el 28/10/2026** (Codex no pudo corroborarlo: Meta le devolvió 429):
- **Versión:** Graph API **v26.0**, la vigente desde el 29/07/2026 ([versiones](https://developers.facebook.com/docs/graph-api/changelog/versions/)). Se fija en la configuración.
- **Envío:** `POST https://graph.facebook.com/{versión}/{phone_number_id}/messages` con `Authorization: Bearer <token>` y JSON ([referencia de `messages`](https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages/)):
  - campos comunes `messaging_product: "whatsapp"`, `recipient_type: "individual"`, `to`, `type`;
  - texto `{"type":"text","text":{"preview_url":false,"body":…}}`, hasta 4096 caracteres ([mensajes de texto](https://developers.facebook.com/documentation/business-messaging/whatsapp/messages/text-messages));
  - plantilla `{"type":"template","template":{"name":…,"language":{"code":"es"},"components":[{"type":"body","parameters":[{"type":"text","parameter_name":…,"text":…}]}]}}`, con parámetros con nombre ([plantillas *utility*](https://developers.facebook.com/documentation/business-messaging/whatsapp/templates/utility-templates/utility-templates));
  - interactivo `{"type":"interactive","interactive":{…}}`, con `type` `button` o `list`;
  - `biz_opaque_callback_data` opcional, hasta 512 caracteres: Meta no lo procesa y lo devuelve en los avisos de estado.
- **Respuesta correcta:** `{"messaging_product","contacts":[{"input","wa_id"}],"messages":[{"id":"wamid…","message_status"}]}`.
- **Errores:** `{"error":{"message","type","code","error_data":{"details"},"fbtrace_id"}}`. Meta recomienda decidir por `code`, no por el estado HTTP ([códigos de error](https://developers.facebook.com/documentation/business-messaging/whatsapp/support/error-codes)).
- **Sin clave de idempotencia en el envío:** la API no ofrece ninguna, así que **no hay envío exactamente una vez** (V2-DA-58).

**Bloqueado por P2-05 (responsable):** aplicación de Meta, número de prueba, token, destinatarios de prueba y aprobación de las tres plantillas de §3.4. Todo se prueba en local contra un **servidor Graph falso** con credenciales ficticias; no se envía ningún mensaje real. **La conexión real con Meta no queda acreditada.**

## 3. Contrato de datos (`V114__whatsapp_salida.sql`, implementador: Codex)
**Permisos:** V100 concede por defecto todos los permisos sobre las tablas nuevas. Como en V113, cada tabla empieza con `REVOKE ALL … FROM <rol de la aplicación>` y concede solo lo indicado. Las entidades son de solo lectura y las escrituras, SQL acotado (como `ColaEventosWhatsApp`); no se usa `@Version`.

### 3.1 `conversacion`
El DDL de `04-arquitectura-datos.md` §3 completo (`modo`, `estado`, `version` incluidos, aunque V2-09 no los usa), con:
- `modo varchar(10)`: el DDL de §3 decía `varchar(8)` y `ASISTENTE` tiene 9 caracteres (hallazgo 111-1; §3 se corrige).
- `negocio_id bigint NOT NULL DEFAULT app_negocio_actual()`, RLS habilitada y **forzada** con la política de negocio habitual (patrón de V100).
- `wa_id` con `CHECK (wa_id ~ '^[0-9]{1,20}$')`.
- Permisos de la aplicación: `SELECT`, `INSERT` y `UPDATE (ultima_entrada_en, actualizado_en)`. Sin `DELETE`. V2-10 ampliará las columnas que necesite.

### 3.2 `mensaje_whatsapp`
El DDL de §3 con estos ajustes (V2-DA-57):
- `negocio_id bigint NOT NULL DEFAULT app_negocio_actual()`, RLS habilitada y forzada.
- `reserva_id bigint` nulo, con `FOREIGN KEY (negocio_id, reserva_id) REFERENCES reserva (negocio_id, id)`.
- `enviado_como varchar(12)` nulo, `CHECK (enviado_como IN ('TEXTO','INTERACTIVO','PLANTILLA'))`: la forma elegida en la **preparación del último intento**, guardada antes de la petición HTTP (hallazgo 111-2).
- `inciertos smallint NOT NULL DEFAULT 0`: intentos cuyo resultado fue desconocido (V2-DA-58).
- `error_codigo varchar(24)` con `CHECK` en los valores de `MotivoFallo` (§4.1), y `codigo_meta integer` nulo.
- Coherencia:
  - `CHECK (intentos >= 0 AND inciertos >= 0)`;
  - `CHECK ((estado = 'FALLIDO') = (error_codigo IS NOT NULL))`;
  - `CHECK (estado <> 'PENDIENTE' OR proximo_intento IS NOT NULL)`;
  - `CHECK (estado NOT IN ('ENVIADO','ENTREGADO','LEIDO') OR (wamid IS NOT NULL AND enviado_como IS NOT NULL))`;
  - `CHECK ((direccion = 'ENTRANTE') = (estado = 'RECIBIDO'))`.
- Índices: `mensaje_wamid_uk (wamid) WHERE wamid IS NOT NULL` (global: la unicidad actúa por encima de RLS; una colisión se trata sin consultar la fila ajena), `mensaje_salida_cola_ix (negocio_id, proximo_intento, id) WHERE estado = 'PENDIENTE'` y `mensaje_conversacion_pendiente_ix (negocio_id, conversacion_id, id) WHERE estado = 'PENDIENTE'`.
- Permisos: `SELECT`, `INSERT` y `UPDATE (wamid, estado, intentos, inciertos, proximo_intento, error_codigo, codigo_meta, enviado_como, actualizado_en)`. Sin `DELETE`.

### 3.3 Ajuste de V2-08 en la misma migración
`evento_whatsapp_entrante.clave_idempotencia` pasa a `varchar(160)`, y el límite de `ExtractorEventosWhatsApp` a 160 caracteres: `s:<wamid de 128>:delivered` ocupa 140 (hallazgo 111-13).

### 3.4 Contenido (`contenido jsonb`)
| `tipo` | `contenido` |
|---|---|
| `TEXTO` | `{"texto": "…"}` (1–4096 caracteres, no en blanco) |
| `INTERACTIVO` | `{"interactivo": {…}}`: el objeto `interactive` de Graph, con `type` `button` o `list` y `body.text`; como máximo 8 KiB serializado. Su estructura detallada es de V2-10 |
| `PLANTILLA` | `{"plantilla": "bt_…", "idioma": "es", "parametros": {"nombre": "valor", …}, "textoEnVentana": true\|false}` |

### 3.5 Plantillas *utility* (`PlantillaUtility`, idioma `es`)
| Nombre | Parámetros, en orden | Texto (el mismo que se someterá a aprobación) |
|---|---|---|
| `bt_recordatorio_cita` | `negocio`, `fecha`, `profesional`, `codigo` | `Hola. Te recordamos tu cita en {{negocio}} el {{fecha}} con {{profesional}} (reserva {{codigo}}).` |
| `bt_cita_modificada_por_negocio` | `negocio`, `codigo`, `fecha`, `profesional` | `{{negocio}} cambió tu reserva {{codigo}}: ahora es el {{fecha}} con {{profesional}}.` |
| `bt_cita_cancelada_por_negocio` | `negocio`, `codigo`, `fecha` | `{{negocio}} canceló tu reserva {{codigo}} del {{fecha}}. Escríbenos si quieres reservar otra hora.` |

Validación de los parámetros: exactamente los del catálogo; cada valor no en blanco, de 1 a 200 caracteres, sin saltos de línea ni tabuladores y sin más de tres espacios seguidos (regla conservadora; un valor que Meta rechazara daría `PLANTILLA_RECHAZADA`).

## 4. Interfaces
### 4.1 Tipos del contrato (paquete `pe.barberturno.whatsapp`, responsable: Claude)
- `OrigenMensaje { CLIENTE, ASISTENTE, SISTEMA, PERSONA }`, `TipoMensaje { TEXTO, INTERACTIVO, PLANTILLA }`, `EstadoMensaje { RECIBIDO, PENDIENTE, ENVIADO, ENTREGADO, LEIDO, FALLIDO }`.
- `MotivoFallo`: `VENTANA_CERRADA`, `CANAL_DESCONECTADO`, `NEGOCIO_NO_OPERATIVO`, `MENSAJE_CADUCADO`, `DESTINO_NO_VALIDO`, `PLANTILLA_RECHAZADA`, `CREDENCIAL_RECHAZADA`, `CUENTA_RESTRINGIDA`, `SOLICITUD_RECHAZADA`, `REINTENTOS_AGOTADOS`, `RESULTADO_DESCONOCIDO`, `FALLO_ENTREGA`. Cada valor tiene un texto breve en español para el aviso interno.
- `PlantillaUtility` (§3.5): `nombre()`, `parametros()`, `idioma()`, `validar(Map)`, `textoEnVentana(Map)` y `porNombre(String)`.
- **`SalidaWhatsApp`** (encolado; lo implementa Codex en `SalidaWhatsAppService`):
  - `long encolarTexto(long conversacionId, String texto, OrigenMensaje origen, OptionalLong reservaId)`;
  - `long encolarInteractivo(long conversacionId, JsonNode interactivo, OrigenMensaje origen, OptionalLong reservaId)`;
  - `long encolarPlantilla(long conversacionId, PlantillaUtility plantilla, Map<String,String> parametros, OptionalLong reservaId)`: siempre se envía como plantilla;
  - `long encolarAviso(long conversacionId, PlantillaUtility plantilla, Map<String,String> parametros, OptionalLong reservaId)`: texto si la ventana está abierta **al preparar el envío**; plantilla si no (§4.3).
  - Reglas comunes: transacción **obligatoria** (`Propagation.MANDATORY`, aplicada por el *proxy* del *bean*) y contexto de negocio. La conversación y la reserva deben ser del negocio, o `NegocioException(NO_ENCONTRADO)`. Un contenido inválido o el origen `CLIENTE` dan `NegocioException(VALIDACION)`. Las plantillas y los avisos tienen origen `SISTEMA`. Se inserta `SALIENTE`/`PENDIENTE`, `intentos 0`, `inciertos 0` y `proximo_intento = creado_en = clock.instant()`. Devuelve el id del mensaje.
  - El encolado **no** comprueba el canal, el estado del negocio ni la ventana: lo hace el enviador, para que un fallo de entrega nunca deshaga la operación que origina el mensaje.
- **`EnviadorSalidaWhatsApp`**: `int enviarLote()`, que devuelve cuántos mensajes reclamó; lo implementa Codex en `EnviadorMensajesWhatsApp` y lo dispara `ProgramadorSalidaWhatsApp`.
- **`ClienteGraphWhatsApp`**: `ResultadoEnvio enviar(String phoneNumberId, ObjectNode cuerpo)`. Lo implementa Codex en `ClienteGraphWhatsAppRest`, con el constructor público `ClienteGraphWhatsAppRest(SalidaWhatsAppPropiedades)` para que las pruebas lo apunten a un servidor falso.
- **`ResultadoEnvio`** (sellado):
  - `Aceptado(String wamid)`: 2xx con `messages[0].id` (hasta 128 caracteres);
  - `Rechazado(int estadoHttp, OptionalInt codigoMeta, boolean transitorio, MotivoFallo motivo)`, con `motivo` obligatorio si no es transitorio;
  - `NoEnviado()`: consta que la petición no llegó a Meta (conexión rechazada, nombre no resuelto o tiempo agotado **al conectar**); se puede reintentar sin riesgo;
  - `Desconocido()`: la petición pudo llegar y no hay respuesta válida (tiempo de respuesta agotado, conexión cortada, 2xx sin `id` o con un cuerpo ilegible). Cualquier otra excepción de E/S se trata como desconocida.

### 4.2 Cliente y clasificación de los errores (Codex, `ClienteGraphWhatsAppRest` y `ClasificadorErroresGraph`)
- `RestClient` sobre `JdkClientHttpRequestFactory`: conexión con `HttpClient.Builder.connectTimeout(graph.conexion)`, respuesta con `setReadTimeout(graph.respuesta)`, **sin redirecciones** (`Redirect.NEVER`) ni reintentos HTTP ocultos; `exchange()` para leer estado y cuerpo sin las excepciones por defecto. Ninguna traza, cabecera ni cuerpo se registra.

| Caso | Resultado |
|---|---|
| `code` 130429, 131000, 131016, 131056, 1, 2, 4; HTTP 429 o 5xx sin `code` reconocido | `Rechazado` transitorio |
| 131047 | `VENTANA_CERRADA` |
| 131026, 131021, 131051 | `DESTINO_NO_VALIDO` |
| 132000, 132001, 132005, 132007, 132012, 132015, 132016 | `PLANTILLA_RECHAZADA` |
| 0, 10, 190, 200, 131005 | `CREDENCIAL_RECHAZADA` |
| 368, 131031, 131042, 131045, 131048, 131057, 133010 | `CUENTA_RESTRINGIDA` |
| Cualquier otro 4xx (100, 131008, 131009, 135000…) y cualquier 3xx (con las redirecciones desactivadas, la petición no se procesó) | `SOLICITUD_RECHAZADA` |

### 4.3 Enviador (Codex, V2-DA-57 y V2-DA-58)
1. **Sin token configurado** (`salida.graph.token` vacío), `enviarLote()` devuelve 0 sin reclamar nada; al arrancar se registra un aviso sin valores.
2. **Negocios:** recorre los `negocio_id` de `canal_whatsapp` (cualquier estado), en orden. Un mensaje solo puede existir para un negocio con canal, porque la conversación nace de una entrada por un canal.
3. **Reclamo de uno en uno** (hallazgo 111-3): para cada negocio, hasta `lote` veces, una transacción corta con `EjecutorEnNegocio.ejecutar(negocioId, …)`: `UPDATE … SET intentos = intentos + 1, proximo_intento = :ahora + :reclamo WHERE id = (SELECT id … WHERE estado = 'PENDIENTE' AND direccion = 'SALIENTE' AND proximo_intento <= :ahora AND NOT EXISTS (un PENDIENTE de la misma conversación con id menor) ORDER BY proximo_intento, id LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id, intentos`. El valor de `intentos` es la ficha. Si no devuelve nada, se pasa al negocio siguiente. Así, el plazo de cada reclamo empieza justo antes de trabajar ese mensaje.
   - **Orden por conversación:** el `NOT EXISTS` incluye los pendientes reclamados y los que esperan un reintento, de modo que un mensaje aplazado retiene a los posteriores. El orden es el de los id entre mensajes **confirmados y visibles**; no ordena inserciones concurrentes aún sin confirmar ni garantiza el orden de recepción en el teléfono (hallazgo 111-9).
4. **Preparación,** transacción del negocio: bloquea el mensaje `FOR UPDATE` exigiendo `PENDIENTE` y la ficha (si no, nada). Por este orden, deja `FALLIDO` con:
   - `REINTENTOS_AGOTADOS` si la ficha supera `max-intentos` (un error interno repetido no lo deja en bucle);
   - `CANAL_DESCONECTADO` si el canal no está `ACTIVO`;
   - `NEGOCIO_NO_OPERATIVO` si el negocio está suspendido o no tiene propietario activo (la regla de V2-DA-53, `contarPropietariosActivos`);
   - `MENSAJE_CADUCADO` si `creado_en + antiguedad-maxima ≤ ahora`;
   - `VENTANA_CERRADA` si es `TEXTO` o `INTERACTIVO` y la ventana está cerrada.

   **Ventana abierta:** `ultima_entrada_en` no nula y `ahora < ultima_entrada_en + 24 h − margen-ventana`. Una `PLANTILLA` con `textoEnVentana` y la ventana abierta se envía como **texto** (`textoEnVentana(parametros)`); en otro caso, como plantilla. Se guarda `enviado_como` y se compone el cuerpo con `to = conversacion.wa_id` y `biz_opaque_callback_data = "bt:m:<id>:<ficha>"`.
5. **Envío,** fuera de toda transacción y sin bloqueos: `ClienteGraphWhatsApp.enviar(phone_number_id del canal, cuerpo)`. `conexion + respuesta < reclamo`; aun así, una pausa arbitraria del proceso puede hacer vencer el reclamo durante la petición, así que la ficha protege el cierre pero **no** impide un segundo POST en ese caso (CI-4 se limita a lo demostrable).
6. **Cierre,** transacción del negocio: bloquea `FOR UPDATE` exigiendo `PENDIENTE` y la ficha (si un aviso de estado ya lo concilió o la ficha cambió, no hace nada) y aplica:
   - `Aceptado` → `ENVIADO` con su `wamid`;
   - `Rechazado` transitorio o `NoEnviado` → `PENDIENTE` con `proximo_intento = ahora + espera(ficha)` (base 30 s, ×2, máximo 5 min), o `FALLIDO`/`REINTENTOS_AGOTADOS` si la ficha llega a `max-intentos`;
   - `Rechazado` permanente → `FALLIDO` con su motivo y `codigo_meta`;
   - `Desconocido` → `inciertos + 1`; si `inciertos < max-inciertos`, `PENDIENTE` con `proximo_intento = ahora + espera-conciliacion` (para que un aviso de estado lo concilie antes del reenvío); si no, `FALLIDO`/`RESULTADO_DESCONOCIDO`. La ficha sigue contando para `max-intentos`.
7. **Aislamiento de fallos** (hallazgo 111-16): una excepción en la preparación, el envío o el cierre de un mensaje se registra con su id (sin contenido) y no detiene el lote. Si el cierre falla, el mensaje sigue reclamado y vuelve al vencer el plazo, como tras una caída: puede reenviarse (V2-DA-58).
8. Toda transición a `FALLIDO`, o cambio de su motivo, genera en la misma transacción el aviso interno de §4.5. Los registros solo llevan el id del mensaje y el motivo.
9. **Hilos** (hallazgo 111-10): `spring.task.scheduling.pool.size: 2`, para que el envío no retrase el procesador de entrada.

### 4.4 Estados y ventana (Codex, V2-DA-59 y V2-DA-60)
**Cambio en V2-08** (`ProcesadorEventosWhatsApp`, hallazgo 111-7): los eventos `ESTADO` de un canal **registrado** se entregan aunque el canal esté desconectado o el negocio no sea operativo: son la contabilidad de mensajes que ya salieron. Los `MENSAJE` siguen la regla de V2-DA-53. `NUMERO_NO_REGISTRADO` y `REMITENTE_INVALIDO` se mantienen para ambos.

**`ActualizadorEstadosWhatsApp`** implementa `ReceptorEstadosWhatsApp`, dentro de la transacción del negocio de V2-08:
- Correspondencia: `sent` → `ENVIADO`, `delivered` → `ENTREGADO`, `read` → `LEIDO` y `failed` → `FALLIDO`/`FALLO_ENTREGA`, con `codigo_meta = errors[0].code`. Otros valores se ignoran.
- **Localización,** con `FOR UPDATE`:
  1. por `wamid`, que RLS limita al negocio del canal;
  2. si no, por `biz_opaque_callback_data` = `bt:m:<id>:<ficha>`: un `SALIENTE` del negocio cuya conversación tiene `wa_id = recipient_id`.
     - Sin `wamid` y en `PENDIENTE` o `FALLIDO`/`RESULTADO_DESCONOCIDO` → se le asigna este `wamid` (**conciliación**) y se aplica el estado.
     - Con **otro** `wamid` (un reenvío duplicado tras una caída) → `WARN` con los ids y la ficha, sin cambiar nada ni lanzar excepción (hallazgo 111-5).
  3. si tampoco, lanza una excepción: V2-08 reintenta (por la carrera entre la respuesta de Graph y el cierre) y acaba en `ERROR`.
- **Orden:** `PENDIENTE` y `FALLIDO`/`RESULTADO_DESCONOCIDO` < `ENVIADO` < `ENTREGADO` < `LEIDO`. Solo se avanza: un duplicado o un aviso atrasado no cambian nada.
- **Transiciones** (hallazgo 111-6):
  - un estado positivo (`ENVIADO`, `ENTREGADO`, `LEIDO`) vacía `error_codigo` y `codigo_meta`;
  - `failed` fija `FALLO_ENTREGA` y su `codigo_meta`, solo desde `PENDIENTE`, `ENVIADO` o `FALLIDO`/`RESULTADO_DESCONOCIDO`; tras `ENTREGADO` o `LEIDO` se ignora;
  - cualquier otro `FALLIDO` es definitivo.
- Un `PENDIENTE` conciliado deja de estar pendiente: el enviador no lo reenvía y su cierre en curso no hace nada.

**`RegistroEntradaWhatsApp`** implementa `ReceptorMensajesWhatsApp`: `INSERT … ON CONFLICT (negocio_id, wa_id) DO UPDATE` en `conversacion`, con `ultima_entrada_en = GREATEST(actual, LEAST(enviadoEn, ahora))`. No guarda el contenido ni crea fichas. V2-10 lo sustituirá por el orquestador, que conservará esta llamada.

### 4.5 Aviso interno (Codex, V2-DA-61)
- Solo si el mensaje tiene `reserva_id`.
- Destinatarios: los usuarios **activos** con membresía activa `PROPIETARIO` o `ENCARGADO` del negocio, más el usuario del profesional **asignado a la reserva en ese momento** si su cuenta y su membresía están activas; sin repetir.
- `NotificacionService.notificar(usuario, reserva, AccionAuditoria.WHATSAPP, mensaje)`, con `AccionAuditoria.WHATSAPP` nuevo (solo para avisos; el `CHECK` de `auditoria_reserva` no lo admite) y su espejo en `frontend/src/app/core/modelos/notificaciones.ts`.
- Texto (sin nombre, teléfono ni contenido del mensaje):
  - `RESULTADO_DESCONOCIDO`: `No se pudo confirmar si se envió por WhatsApp el mensaje de la reserva BT-<id>. Compruébelo con el cliente.`
  - Otros motivos: `No se pudo enviar por WhatsApp el mensaje de la reserva BT-<id>: <texto del motivo>. Contacte al cliente por otro medio.`
- **Un aviso por transición** (entrada en `FALLIDO` o cambio de motivo), no uno por mensaje: un mensaje desconocido que después falla de verdad genera dos (hallazgo 111-15).
- Sin reserva: solo un `WARN` con el id y el motivo.

### 4.6 Canal (Codex, ajuste de V2-DA-56, hallazgo 111-8)
`PUT /api/plataforma/negocios/{id}/canal-whatsapp` con un `phone_number_id` **distinto** del actual da 409 `CONFLICTO` si el negocio ya tiene conversaciones: los avisos de estado de los mensajes enviados por el número antiguo dejarían de encontrar su canal. Sin conversaciones se permite, como hasta ahora.

### 4.7 Configuración (`SalidaWhatsAppPropiedades`, prefijo `barberturno.whatsapp.salida`, responsable: Claude)
| Propiedad | Valor por defecto |
|---|---|
| `habilitado` | `true`; `false` en `application-test.yml` (solo condiciona `ProgramadorSalidaWhatsApp`) |
| `intervalo` | `2s` |
| `lote` | `20` (mensajes por negocio y pasada) |
| `max-intentos` | `5` (máximo 100, por `smallint`) |
| `espera-base` / `espera-maxima` | `30s` / `5m`: con 5 intentos, unos 7 min 30 s de esperas |
| `max-inciertos` | `2`: un resultado desconocido se reenvía una sola vez |
| `espera-conciliacion` | `2m` |
| `reclamo` | `60s` |
| `antiguedad-maxima` | `6h` |
| `margen-ventana` | `5m` |
| `graph.url-base` | `https://graph.facebook.com` (HTTP solo hacia `localhost`/`127.0.0.1`, para pruebas) |
| `graph.version` | `v26.0` |
| `graph.token` | `${BT_WHATSAPP_ACCESS_TOKEN:}`; el `toString` lo oculta |
| `graph.conexion` / `graph.respuesta` | `5s` / `15s`; su suma debe ser menor que `reclamo` |

## 5. Reparto y archivos (un implementador por archivo)
| Subtarea | Implementador | Archivos |
|---|---|---|
| **V2-09.0 · Contrato** (primero) | Claude | Este documento, V2-DA-57…62 y los ajustes de V2-DA-53/54/56, la fila de §4 de `10-backlog.md`, las precisiones de `04-arquitectura-datos.md` §3 y §6 y de `06-flujos.md` §10; los tipos de §4.1 (`OrigenMensaje`, `TipoMensaje`, `EstadoMensaje`, `MotivoFallo`, `PlantillaUtility`, `SalidaWhatsApp`, `EnviadorSalidaWhatsApp`, `ClienteGraphWhatsApp`, `ResultadoEnvio`); `SalidaWhatsAppPropiedades`; `ConfiguracionWhatsApp`; `application.yml` y `application-test.yml` |
| **V2-09.1 · Producción** | Codex | `V114__whatsapp_salida.sql`; `Conversacion`, `MensajeWhatsApp` (solo lectura); `ColaSalidaWhatsApp` (reclamo y cambios de estado, sin CRUD heredado); `SalidaWhatsAppService`; `EnviadorMensajesWhatsApp`; `ProgramadorSalidaWhatsApp`; `ClienteGraphWhatsAppRest`; `ClasificadorErroresGraph`; `ActualizadorEstadosWhatsApp`; `RegistroEntradaWhatsApp`; `AvisoFalloWhatsApp`; cambios acotados en `ProcesadorEventosWhatsApp` (§4.4 y extracción de la regla de negocio operativo), `ExtractorEventosWhatsApp` (§3.3), `CanalWhatsAppService` (§4.6), `CanalWhatsAppRepository` (ids de negocio con canal), `MembresiaRepository` (destinatarios), `AccionAuditoria` y `notificaciones.ts` |
| **V2-09.2 · Pruebas** | Gemini | Archivos nuevos de prueba en `backend/src/test/java/pe/barberturno/whatsapp/` (servidor Graph falso y pruebas de §6) y `common/tenancy/WhatsAppSalidaEsquemaPruebas`; ajustes de *fixtures* y pruebas existentes afectados: `LimpiezaBaseDatos`, `EsquemaIT`, `InstalacionNuevaIT`, `EsquemaV2IT`, `CanalWhatsAppRepositoryTest`, `ExtractorEventosWhatsAppTest`, `ProcesadorEventosWhatsAppIT` (descartes de estados), `CanalWhatsAppIT` y, si hiciera falta, `ActualizacionV1IT` |
| V2-09.3 · Unión y cierre | Claude | Integración de las dos ramas, ejecución de todas las pruebas, `pruebas/v2-09.md`, revisiones cruzadas |

**Reglas de la frontera:**
- Codex no crea ni modifica archivos de prueba; Gemini no toca `src/main`. Si alguno necesita cambiar un archivo ajeno o del contrato, se detiene y lo pide a Claude.
- **Ramas:** Codex en `tarea/V2-09-whatsapp-salida-produccion` (`D:\Proyectos\BarberTurno-v2-09-produccion`); Gemini en `tarea/V2-09-whatsapp-salida-pruebas` (`D:\Proyectos\BarberTurno-v2-09-pruebas`); las dos desde el commit del contrato.
- **Base de datos:** todo `mvnw` que use `barberturno_v2_test` pasa por `D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1` (cerrojo exclusivo). Gemini no puede ejecutar órdenes en modo desatendido: **Claude ejecuta sus pruebas** con ese ejecutor y le devuelve los resultados.
- Los defectos de producción que encuentren las pruebas van a Codex; los de las pruebas, a Gemini.

## 6. Criterios de aceptación (CI-1…CI-11) y pruebas que se exigen
| # | Criterio | Pruebas (Gemini) |
|---|---|---|
| CI-1 | **Atomicidad:** un mensaje encolado en una transacción que se revierte no existe; el encolado fuera de transacción falla; una conversación o reserva de otro negocio da `NO_ENCONTRADO` y deshace la transacción; validaciones de §3.4 y §3.5 | `SalidaWhatsAppIT`, `PlantillaUtilityTest` |
| CI-2 | **Antes del envío:** canal desconectado, negocio suspendido o sin propietario y mensaje caducado → `FALLIDO` con su motivo y **ninguna** petición a Graph | `EnviadorMensajesWhatsAppIT` |
| CI-3 | **Aislamiento:** cada mensaje sale con el `phone_number_id` de su negocio; un aviso de estado recibido por el canal de B no cambia un mensaje de A, ni por `wamid` ni por el dato de conciliación | `EnviadorMensajesWhatsAppIT`, `EstadosMensajesWhatsAppIT` |
| CI-4 | **Concurrencia:** dos enviadores a la vez hacen **una** petición por mensaje cuando ninguna supera el plazo; un lote lento no deja vencer los reclamos de los mensajes aún no empezados; un reclamo vencido se recupera; una ficha antigua no cierra un mensaje reclamado de nuevo; un pendiente aplazado retiene a los posteriores de su conversación | `EnviadorMensajesWhatsAppIT` |
| CI-5 | **Estados:** duplicados y desordenados solo avanzan; `failed` tras `delivered` se ignora; `failed` desde `ENVIADO` → `FALLIDO` con un aviso; `wamid` desconocido → reintento de V2-08; los estados de un canal desconectado o de un negocio suspendido se aplican | `EstadosMensajesWhatsAppIT`, `ProcesadorEventosWhatsAppIT` |
| CI-6 | **Reintentos y fallos permanentes:** 429, 5xx y `130429` reintentan con espera creciente y acaban en `REINTENTOS_AGOTADOS`; cada clase de §4.2 da su motivo sin reintentar; 3xx sin seguir la redirección | `EnviadorMensajesWhatsAppIT`, `ClienteGraphWhatsAppTest` |
| CI-7 | **Resultado desconocido:** tiempo de respuesta agotado o conexión cortada → espera de conciliación y **un** reenvío; el segundo desconocido → `FALLIDO`/`RESULTADO_DESCONOCIDO` con aviso; un `sent` con su dato de conciliación durante la espera lo pasa a `ENVIADO` **sin reenvío**; un segundo `wamid` del mismo mensaje no sobrescribe el primero; conexión rechazada → `NoEnviado` y reintento | `EnviadorMensajesWhatsAppIT`, `EstadosMensajesWhatsAppIT`, `ClienteGraphWhatsAppTest` |
| CI-8 | **Ventana y plantillas:** texto e interactivo solo con la ventana abierta (límites exactos con el margen); `encolarAviso` como texto dentro y como plantilla fuera; `encolarPlantilla`, siempre plantilla; `enviado_como` guardado antes del envío; cuerpo JSON exacto de §2; la entrada registra la ventana sin retroceder ni adelantarse al reloj | `EnviadorMensajesWhatsAppIT`, `RegistroEntradaWhatsAppIT` |
| CI-9 | **Aviso interno:** destinatarios correctos (cuentas y membresías activas, profesional actual), sin datos del cliente, solo con reserva, en la misma transacción y uno por transición | `EnviadorMensajesWhatsAppIT` |
| CI-10 | **Ajustes de V2-08:** clave de 160 caracteres en los extremos; número del canal con conversaciones → 409 | `ExtractorEventosWhatsAppTest`, `CanalWhatsAppIT` |
| CI-11 | Definición de Hecho de v2: `verify` completo verde, JaCoCo, Javadoc sin avisos, esquema (`EsquemaV2IT`: tablas, `CHECK`, permisos exactos, RLS forzada), sin secretos ni datos personales en registros, evidencia en `pruebas/v2-09.md`, revisiones cruzadas | Claude |

## 7. Pendientes y bloqueos registrados
- **P2-05:** token, número de prueba, destinatarios y aprobación de las tres plantillas. Sin ellos, la conexión real no está acreditada.
- **Retención** de `mensaje_whatsapp.contenido`, de las conversaciones sin ficha y de `evento_whatsapp_entrante.cuerpo`: se fija con P2-06 en V2-10.
- **Garantía de entrega:** al menos una vez, con duplicados acotados y conciliación; no es exactamente una vez (V2-DA-58).
- **Visibilidad de los avisos** (hallazgo 111-14): la cabecera oculta los avisos al rol efectivo `ADMIN` (propietario y encargado), herencia de RN-15 de v1. Los avisos de V2-09 se guardan y el profesional los ve, pero mostrarlos a propietarios y encargados es un cambio de interfaz que se hará tras integrar V2-07, que reescribe esa cabecera.
- **Integración con V2-07:** los conflictos ya anotados en V2-08 (`ResolutorNegocio.esGlobal`, `07-decisiones.md`, `10-backlog.md`) y el `vaciar()` de los E2E, que debe incluir `canal_whatsapp`, `evento_whatsapp_entrante`, `conversacion` y `mensaje_whatsapp`. **Cierre robusto del entorno de revisión** de V2-07: el `finally` del script no se ejecuta si se cierra la consola (visto el 28/10); pendiente de una solución verificada.

## 8. Revisión de diseño de Codex (encargo 111, 28/10/2026)
Veredicto «Con propuestas»; las 16 se aceptan:

| Nº | Propuesta | Dónde queda |
|---|---|---|
| 1 | `modo varchar(8)` no admite `ASISTENTE` | §3.1 y `04-arquitectura-datos.md` §3 |
| 2 | Guardar `enviado_como` antes del envío | §3.2, §4.3.4 |
| 3 | Reclamar de uno en uno para que el plazo no venza en lote | §4.3.3, CI-4 |
| 4 | Reenviar tras una espera de conciliación en lugar de «como mucho una vez» | V2-DA-58, §4.3.6, CI-7 |
| 5 | Ficha en el dato de conciliación y segundo `wamid` | §4.3.4, §4.4 |
| 6 | Transiciones de error al conciliar | §4.4 |
| 7 | Estados de canales desconectados y negocios no operativos | §4.4, V2-DA-53 |
| 8 | Sustitución del número de un canal con historial | §4.6, V2-DA-56 |
| 9 | Alcance del orden por conversación | §4.3.3 |
| 10 | Un hilo de programación compartido | §4.3.9 |
| 11 | Permisos por defecto de V100, `@Version`, rangos de `smallint` | §3, §4.7 |
| 12 | Traducción de excepciones y 3xx | §4.1, §4.2 |
| 13 | Clave de idempotencia y `wamid` de 128 | §3.3 |
| 14 | Visibilidad de los avisos para `ADMIN`; cuentas activas; profesional actual | §4.5, §7 |
| 15 | Texto del aviso desconocido; un aviso por transición | §4.5 |
| 16 | Aislamiento de fallos por mensaje; contradicciones documentales | §4.3.7, V2-DA-54, `06-flujos.md` §10, *backlog* |
