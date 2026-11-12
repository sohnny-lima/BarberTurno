# V2-TW · Canal de pruebas con Twilio Sandbox (solo perfil `twilio`)

> **Estado: contrato aprobado por el responsable el 11/11/2026** («implementa la conexión con Twilio Sandbox en una rama nueva y con la base local de pruebas»). Rama `tarea/V2-TWILIO-sandbox` desde `2f76afb` (worktree `D:\Proyectos\BarberTurno-v2-twilio`). Sin push, sin integración en `v2`, sin despliegue en Azure.

## 1. Motivo
La conexión con Meta está bloqueada: el número de prueba de Meta devolvió `131031 — Business Account locked` y Meta exige verificar el negocio (P2-05). Para probar el bot de reservas de extremo a extremo con un WhatsApp real **sin esperar a Meta**, se añade un adaptador para **Twilio Sandbox for WhatsApp**, que solo existe en un perfil de pruebas y reutiliza sin cambios la cola de entrada, el procesador, la conversación guiada (V2-10), la outbox (V2-09), `ReservaService` y la agenda.

**No es un canal de producción.** El perfil `prod` no puede activarlo. Meta sigue siendo el canal previsto (P2-05).

## 2. Hechos de Twilio que condicionan el diseño (documentación oficial, consultada el 11/11/2026)
- Sandbox: número compartido `+14155238886`; cada persona envía `join <código>`; la inscripción caduca a los **3 días**; el Sandbox envía **como máximo un mensaje cada 3 segundos**; solo a personas inscritas (error 63015).
- Dentro de la ventana de 24 h se responde con **texto libre**. La página del Sandbox no garantiza mensajes interactivos (listas o botones) ni plantillas propias → **los menús se envían como texto numerado** (§4.3).
- Webhook entrante: `POST application/x-www-form-urlencoded` con `MessageSid`, `AccountSid`, `From` (`whatsapp:+…`), `To`, `Body`, `NumMedia`, `ProfileName`, `WaId` y, opcionalmente, `ButtonPayload`, `ButtonText`. Espera TwiML en la respuesta.
- Firma `X-Twilio-Signature`: HMAC-SHA1 con el **Auth Token** como clave, sobre la URL exacta configurada en Twilio seguida de los parámetros del formulario ordenados alfabéticamente (nombre y valor concatenados); el resultado va en Base64. La URL debe ser la pública exacta (no la que ve el servidor detrás de ngrok).
- Envío: `POST https://api.twilio.com/2010-04-01/Accounts/{AccountSid}/Messages.json` con autenticación básica (`AccountSid:AuthToken`) y `From=whatsapp:+14155238886`, `To=whatsapp:+<número>`, `Body`, `StatusCallback`. Responde `201` con `sid`.
- Avisos de estado: `MessageSid`, `MessageStatus` (`queued`, `sent`, `delivered`, `read`, `failed`, `undelivered`), `ErrorCode`.

Fuentes: <https://www.twilio.com/docs/whatsapp/sandbox>, <https://www.twilio.com/docs/messaging/guides/webhook-request>, <https://www.twilio.com/docs/usage/webhooks/webhooks-security>.

## 3. Decisiones
| ID | Decisión | Motivo |
|---|---|---|
| V2-DA-97 | El adaptador vive en `pe.barberturno.whatsapp.twilio` y **solo se crea con el perfil `twilio`**. `prod` + `twilio` impide arrancar (como `prod` + `demo`). Sin el perfil, las rutas `/api/twilio/**` responden 404. | Es una herramienta de prueba; no debe poder activarse por error en Azure. |
| V2-DA-98 | **Entrada por traducción**: el controlador valida la firma, convierte el formulario de Twilio al JSON de notificación de Meta que ya entiende `ExtractorEventosWhatsApp` y llama a `RecepcionWebhookService.recibir`. La clave de idempotencia sale de `MessageSid`. | Se reutilizan la cola, la idempotencia en la base, el procesador, la resolución del negocio por `phone_number_id` y la conversación sin tocarlos. |
| V2-DA-99 | **Salida por una segunda implementación de `ClienteGraphWhatsApp`** (`ClienteTwilioWhatsApp`, `@Primary` con el perfil) que convierte el cuerpo de Meta en un mensaje de texto de Twilio. `ClienteGraphWhatsApp` gana `boolean configurado()`; el enviador consulta al cliente activo en lugar de mirar solo el token de la Graph API. El comportamiento con Meta no cambia. | La outbox, los reintentos, el presupuesto y los avisos de fallo se reutilizan. |
| V2-DA-100 | **Menús como texto numerado** con correspondencia en memoria por persona (§4.3). Al reiniciar el servidor la correspondencia se pierde y la persona escribe «menú». | El Sandbox no garantiza interactivos; es un canal de pruebas de una sola instancia. |
| V2-DA-101 | **Firma implementada con el JDK** (`HmacSHA1`, comparación en tiempo constante), probada con el vector de ejemplo de la documentación o del SDK oficial (código abierto), sin añadir el SDK de Twilio como dependencia. | Evita arrastrar Jackson 2 y un SDK grande para dos llamadas HTTP; el algoritmo es corto y queda probado. |
| V2-DA-102 | El canal del negocio de prueba se registra **al arrancar** con el perfil `twilio` (idempotente): `phone_number_id` = dígitos del número del Sandbox (`14155238886`), en el negocio `barberturno.whatsapp.twilio.negocio-slug` (por defecto `barberturno`). | La persona que prueba no tiene que dar de alta el canal a mano. |

## 4. Contrato
### 4.1 Configuración (`barberturno.whatsapp.twilio`, archivo `application-twilio.yml`)
| Propiedad | Variable | Por defecto | Regla |
|---|---|---|---|
| `account-sid` | `BT_TWILIO_ACCOUNT_SID` | vacío | `AC` + 32 hex; vacío → el adaptador no envía y el webhook responde 401 (falla cerrado) |
| `auth-token` | `BT_TWILIO_AUTH_TOKEN` | vacío | nunca se registra ni se muestra |
| `numero` | `BT_TWILIO_NUMERO` | `+14155238886` | E.164 |
| `url-publica` | `BT_TWILIO_URL_PUBLICA` | vacío | `https://…` sin barra final; base de las URL firmadas (`/api/twilio/webhook`, `/api/twilio/estado`) |
| `negocio-slug` | — | `barberturno` | negocio al que se asocia el canal |
| `intervalo-envio` | — | `3s` | separación mínima entre envíos (límite del Sandbox) |
| `api-base` | — | `https://api.twilio.com` | sustituible en pruebas por un servidor local |

### 4.2 Rutas
- `POST /api/twilio/webhook` (formulario): sin sesión ni CSRF (exención **exacta** de esta ruta y de la de estado, solo con el perfil). Sin firma válida → **401** sin efectos. `To` distinto del número configurado o `From` sin `whatsapp:` → 200 sin efectos. Con firma válida → traducción (§4.3) → `RecepcionWebhookService.recibir` → **200** `text/xml` con `<Response/>` vacío (la respuesta real sale por la outbox). Límite de tamaño como el webhook de Meta.
- `POST /api/twilio/estado` (formulario, firma igual): `MessageStatus` → `statuses[]` de Meta (`sent`/`delivered`/`read`; `failed` y `undelivered` → `failed` con `errors[0].code` = `ErrorCode`); `queued` se ignora.
- Ninguna otra ruta nueva. Ninguna ruta de Meta cambia.

### 4.3 Traducción
**Entrada** (formulario → JSON de Meta): `object=whatsapp_business_account`, un `entry` con un `changes` `field=messages`, `value.messaging_product=whatsapp`, `metadata.phone_number_id` = dígitos de `To`, `contacts[0]` = `{wa_id: WaId, profile.name: ProfileName}`, `messages[0]` con `id=MessageSid`, `from=WaId`, `timestamp` del `Clock`, y:
1. `ButtonPayload` presente → `type=interactive`, `interactive.button_reply.id = ButtonPayload`.
2. `Body` (recortado) es un entero `N` y hay un menú vigente para esa persona con la opción `N` → `type=interactive` con `list_reply.id` o `button_reply.id` = identificador guardado de la opción `N` (y `title` de la opción). El menú vigente se consume al usarse.
3. En otro caso, `type=text`, `text.body = Body` (las palabras clave de V2-10 siguen funcionando: «menú», «hola»…).
4. Con `NumMedia > 0` y sin `Body` → `type=unsupported`.

**Salida** (cuerpo de Meta → texto):
- `type=text` → `Body = text.body`.
- `interactive` de botones → cuerpo + línea en blanco + `1. <título>` … + `Responda con el número.`
- `interactive` de lista → encabezado y cuerpo si existen + filas numeradas en el orden de las secciones (`N. <título> — <descripción>` si hay descripción) + `Responda con el número.`
- Al enviar un interactivo se **guarda** la correspondencia número → identificador para ese `wa_id` (sustituye la anterior; caduca a las 24 h). Un texto posterior no la borra.
- `template` → no admitido en el Sandbox: resultado de fallo permanente (sin llamada HTTP).
- `Body` como máximo 1600 caracteres (límite de Twilio); si se excede, fallo permanente registrado (no se trunca en silencio).
- Resultado: `201` con `sid` → `Aceptado(sid)`; 429 y 5xx → transitorio; otros 4xx → permanente con el código de Twilio; error de red o respuesta ilegible tras enviar → `Desconocido` (como Graph). La conciliación por `biz_opaque_callback_data` no aplica a Twilio (limitación declarada).
- Se respeta `intervalo-envio` entre dos envíos aunque los pida el mismo lote.

### 4.4 Seguridad y datos
- Firma comprobada antes de leer nada del negocio; comparación en tiempo constante; la URL firmada se compone con `url-publica` (no con las cabeceras de la petición).
- Los registros no incluyen el número, `Body`, `ProfileName`, el SID de la cuenta ni el token: solo el `MessageSid` y el resultado.
- Solo perfil `twilio`; `prod` + `twilio` → `IllegalStateException` al arrancar.
- Base local separada `barberturno_v2_twilio` (datos ficticios de `dev,demo`); el número real de la persona que prueba solo queda ahí y se borra la base al terminar.

## 5. Criterios de aceptación (CT-1…CT-9)
| CT | Criterio |
|---|---|
| CT-1 | Firma: el vector oficial pasa; un parámetro alterado, otra URL u otro token → 401 sin insertar eventos. |
| CT-2 | Un mensaje válido inserta exactamente un `evento_whatsapp_entrante` con el formato de Meta; repetir el mismo `MessageSid` no inserta otro. |
| CT-3 | Respuesta numérica: tras enviar una lista, «2» llega a la conversación como la selección 2 (`list_reply.id`); sin menú vigente, «2» llega como texto. |
| CT-4 | Salida: texto, botones y listas se convierten como en §4.3 (incluidas las listas de horarios con fecha completa) y la petición a Twilio lleva `From`, `To`, `Body` y `StatusCallback` correctos con autenticación básica. |
| CT-5 | Avisos de estado: `delivered`, `read` y `failed` actualizan el mensaje por su `sid`. |
| CT-6 | **Recorrido completo** con un servidor Twilio simulado: «hola» → aceptar privacidad → Reservar → servicio → profesional → día → hora → Confirmar → la reserva existe en el negocio, con la ficha del `wa_id`, y aparece en la agenda (`GET /api/n/barberturno/reservas`); después Mis citas → cancelar → Confirmar → la reserva queda `CANCELADA`. Consultar disponibilidad y reservar usan `ReservaService` (sin lógica nueva de citas). |
| CT-7 | Sin el perfil `twilio`, `/api/twilio/webhook` y `/api/twilio/estado` → 404 y no hay bean del adaptador; `prod` + `twilio` no arranca; la exención de CSRF no se amplía (como `ExencionCsrfWebhookTest`). |
| CT-8 | Con Meta configurado y sin el perfil, el envío sigue usando la Graph API (las pruebas de V2-08/V2-09 pasan sin cambios). |
| CT-9 | Javadoc útil y `verify` completo sin avisos; registros sin datos personales ni secretos. |

## 6. Reparto
| Bloque | Autor | Revisor independiente |
|---|---|---|
| Contrato | Claude | Codex (al implementar) |
| Producción (§4) | Codex | Gemini (solo lectura, *diff-first*) |
| Pruebas CT-1…CT-8 | Gemini | Codex (solo lectura) |
| Ejecución, entorno local, ngrok y prueba con el teléfono | Claude y el responsable | — |

## 7. Prueba manual (responsable)
1. Twilio: consola clásica → Messaging → Try it out → Send a WhatsApp message; enviar `join <código>` al `+14155238886`.
2. Sandbox settings: «When a message comes in» = `<url-publica>/api/twilio/webhook` (POST); «Status callback URL» = `<url-publica>/api/twilio/estado`.
3. Escribir «hola» y seguir el recorrido de CT-6; comprobar la cita en la agenda local (`http://localhost:8081`, cuenta `multirol@ejemplo.test` o `carlos@ejemplo.test`).

## 8. Implementación (Codex 155)

**11/11/2026 · Código implementado; cierre Bloqueada (cobertura parcial de JaCoCo: la suite completa está reservada a Claude).**
Solo producción y el vector unitario de firma; las pruebas nuevas CT-1…CT-8 corresponden a Gemini y la
verificación completa al coordinador. No se acredita todavía CT-6 ni Twilio real. Sin integración ni push.

Archivos y módulos:

- `backend/src/main/java/pe/barberturno/whatsapp/twilio/`: `TwilioPropiedades`, `ConfiguracionTwilio`,
  `FirmaTwilio`, `WebhookTwilioController`, `TraductorEntradaTwilio`, `CorrespondenciaMenusTwilio`,
  `ClienteTwilioWhatsApp`, `RegistroCanalTwilioService` y `RegistroCanalTwilioRunner`.
- `ClienteGraphWhatsApp`, `ClienteGraphWhatsAppRest`, `EnviadorMensajesWhatsApp` y `ConfiguracionWhatsApp`:
  configuración del transporte activo y avisos coherentes con el perfil, conservando el transporte Meta.
- `SecurityConfig`, `ResolutorNegocio` y `ConfiguracionProduccion`: dos POST exactos solo en twilio,
  404 sin el perfil y prohibición de `prod,twilio`. `CanalWhatsApp`: visibilidad y Javadoc de los miembros
  de dominio usados desde el subpaquete, sin cambiar el modelo ni el comportamiento de Meta.
- `application-twilio.yml`, `tools/barberturno-v2.env.example`, `FirmaTwilioTest`, README,
  seguimiento en `10-backlog.md` y [evidencia completa](pruebas/v2-tw.md).

Decisiones menores: waba_id sintético con los dígitos del Sandbox; registro después de la demo (orden
200), sin actor ni auditoría ficticios; no sustituir un canal diferente; formato/configuración validados
sin mostrar valores. Se rechazan formularios ambiguos y consultas no configuradas antes de traducir.
El método `configurado()` por defecto conserva los dobles funcionales existentes, y ambos transportes
reales lo implementan con sus propias credenciales.

El cliente serializa todas sus solicitudes y espera como mínimo 3 s **desde el final de la anterior**,
también entre lotes/hilos, usando tiempo monotónico y sin transacciones abiertas. Las correspondencias
caducan a las 24 h: sin menú vigente, un número llega como texto y «menú» permite recuperarlo. Un
texto posterior no borra la correspondencia. Ante aceptación o resultado desconocido se instala el
menú enviado; un rechazo probado conserva el anterior. Los reintentos numéricos conservan la misma
traducción por MessageSid durante 24 h para no consumir otro menú. Todo ese estado es local a la instancia.

Verificación serializada y registros en `backend/target/`:

| Orden/argumentos del ejecutor | Resultado | Registro |
|---|---|---|
| `-Dtest=FirmaTwilioTest test` | 1 prueba aprobada; vector del SDK oficial Twilio, enlazado desde Javadoc | `v2-tw-firma.log` |
| Regresión indicada en el encargo (8 clases) | 154 pruebas aprobadas; 0 fallos, errores y omisiones | `v2-tw-regresion.log` |
| `-DskipTests verify` | Empaqueta correctamente; falla en cobertura JaCoCo antes de Javadoc | `v2-tw-verify.log` |
| `-DskipTests javadoc:javadoc` | Primer intento: tres descripciones ausentes, corregidas; repetición BUILD SUCCESS, 0 avisos | `v2-tw-javadoc.log`, `v2-tw-javadoc-2.log` |

No se alteraron umbrales, datos de cobertura ni pruebas existentes. Hallazgo para el coordinador:
`skipTests` no omite el check de cobertura; con solo la selección autorizada no se alcanza el 70 % de
reservas, agenda y fichas. Debe cerrar ese check con el verify completo tras las pruebas de Gemini.
Los problemas de permisos del cerrojo y del índice Git se resolvieron por escalación automática;
no se cambiaron ACL ni configuración. El fallo inicial por falta de target y todas las salidas relevantes
se conservan en la evidencia. Javadoc útil en toda la superficie pública nueva o modificada.

Commits de producción: `2f1ababfd299a777edebb1ea3a7478890136c8b7` (transporte activo) y
`4f0289a6aebfbcd5a6b0c52f3a2b24a88ffcd50a` (adaptador, seguridad, configuración y vector).
El cierre documental se identifica por `docs(whatsapp): registrar verificación parcial y bloqueo de cobertura [V2-TW]`;
su hash propio se entrega en el informe final. **Merge: no realizado, por instrucción expresa.**

Límites comprobados: §1–§7 intactos; ningún cambio en documentos protegidos, frontend, migraciones,
APF2, entregables, evidencias de v1, Azure o bases de v1; no se leyó `.local/coordinacion/`, ni se
mostraron secretos; sin ramas, worktrees ni etiquetas nuevos, sin push ni servicios reales/de pago.
Al terminar las verificaciones: 0 procesos Java, 0 escuchas en 8080/8081/4200 y cerrojo del ejecutor ausente.

## 9. Cierre técnico (coordinador)

**11/11/2026 · Hecha en local; pendiente de la prueba manual con el teléfono (§7).** Sin integración en `v2`, sin push y sin despliegue.

- **Producción (Codex 155):** revisada por Claude (conforme) y por Gemini en solo lectura, *diff-first* (g126 terminó sin respuesta tras una lectura denegada; g126b: **APROBADO**).
- **Pruebas (Gemini g125 → `8587ef1`):** Claude las ajustó a la API y al diálogo reales sin tocar producción (`07fde5f`). El recorrido CT-6 elige cada opción por su texto y exige `CONFIRMADA` antes de cancelar y `CANCELADA` después. Codex 156 las revisó en solo lectura y dejó 11 hallazgos. Gemini g127 y Claude los resolvieron (`3995710`): firma con `+`, espacios y caracteres no ASCII; parámetros repetidos; reintento del mismo `MessageSid`; menú caducado y número fuera de rango; `read`; restauración del negocio tras cada prueba. Un hallazgo alto también se corrigió: una prueba dejaba `limite_reservas_activas = 5` y rompía `ParametrosNegocioIT`.
- **Bloqueo de Codex 155:** quedó resuelto. `-DskipTests verify` no omite el check de JaCoCo; el `verify` completo sí lo cumple.
- **Resultados:**
  - Twilio + `ParametrosNegocioIT`: 93/0.
  - `verify` completo sobre `271d87c`: **3884 pruebas, 0 fallos/errores/omisiones**; «All coverage checks have been met»; Javadoc sin avisos; BUILD SUCCESS.
- **CT-1…CT-9:** cubiertos por pruebas automáticas con un servidor Twilio simulado. Sin acreditar: la conexión con Twilio real y el recorrido desde un teléfono (§7).

## 10. Ajuste por BSUID y respuesta por TwiML (12/11/2026)

**Hechos observados** (sonda local sin credenciales, 12/11/2026 03:56 h de Lima; solo se registró la forma de cada campo, no su valor):
- **Remitente:** el Sandbox clásico envió `From` y `ExternalUserId` con `whatsapp:PE.` seguido de 16 caracteres (BSUID de Meta), **sin `WaId`**. También llegaron `ProfileName`, `Username`, `ChannelMetadata` y `X-Twilio-Signature`.
- **TwiML:** una respuesta TwiML `<Response><Message>…</Message></Response>` devuelta por el webhook **llegó al teléfono**.
- **API REST:** según el responsable, la API de mensajes devolvió **HTTP 400** al responsable con plantilla y con texto libre.

Fuente: [Twilio · WhatsApp Key Concepts, «WhatsApp usernames»](https://www.twilio.com/docs/whatsapp/key-concepts), consultada el 12/11/2026. Si no hay número, Twilio pone el BSUID en `From`, `To` y `ExternalUserId`.

Con el adaptador de §8, ese remitente se descartaba: `wa_id` vacío no cumple `^[0-9]{1,20}$`. Además, la salida solo admitía `whatsapp:+<E.164>`.

### V2-DA-103 · Identidad por BSUID con un `wa_id` sintético
1. Si `WaId` cumple `[1-9][0-9]{1,14}`, se usa como hasta ahora.
2. Si no, y `From` cumple `whatsapp:[A-Z]{2}\.[A-Za-z0-9]{1,128}`, se usa un `wa_id` **sintético de 20 dígitos**:
   - se calcula como `"9"` + `(los 8 primeros bytes de SHA-256(UTF-8 de From sin "whatsapp:") como entero sin signo) mod 10^19`, rellenado a 19 dígitos;
   - es determinista: la misma persona conserva su ficha y su conversación tras reiniciar;
   - nunca coincide con un E.164, que tiene 15 dígitos como máximo;
   - cabe en las restricciones actuales (`^[0-9]{1,20}$`), así que **no hay migración**.
3. En cualquier otro caso no se genera ningún evento.
4. **Correspondencia en memoria:** se guarda `wa_id sintético → dirección BSUID` (la última vista; máximo 10 000 entradas; se pierde al reiniciar). Solo sirve para enviar por REST a esa dirección.
5. **Avisos de estado:** un `To` con BSUID se traduce con la misma función.
6. **Registros:** nunca incluyen el BSUID ni el `wa_id` sintético.
7. **Bandeja local:** muestra el identificador sintético en lugar de un teléfono. Es aceptable, porque es solo para la prueba.

### V2-DA-104 · Respuesta por TwiML dentro del mismo webhook
1. Con `barberturno.whatsapp.twilio.respuesta-twiml=true` (valor por defecto), el webhook de mensajes hace lo siguiente:
   - abre una **espera** para el `wa_id` **antes** de llamar a `RecepcionWebhookService.recibir`;
   - si `recibir` devuelve 0 (duplicado o ignorado), la cierra sin esperar y responde al instante con las respuestas que ya hubiera aceptado durante esa breve ventana, o `<Response/>` si no hay ninguna (corrección del coordinador en la revisión, `4f2babc`, 12/11/2026);
   - si no, espera las respuestas del bot y devuelve un `<Message>` por cada una, en orden y con XML escapado.
2. **Plazos de la espera:**
   - hasta `respuesta-espera` por el primer mensaje (por defecto 10 s);
   - tras cada mensaje, hasta `respuesta-silencio` por el siguiente (por defecto 1,5 s);
   - nunca más de 12 s en total, porque Twilio corta a los 15 s;
   - como máximo 5 mensajes por respuesta.
3. **Una espera por `wa_id`.** Si llega otro mensaje de la misma persona con una espera abierta, se encola y responde `<Response/>` de inmediato. Sus respuestas viajan en la espera abierta.
4. **Salida (`ClienteTwilioWhatsApp`):**
   - Antes de cualquier HTTP, y fuera de la sección `synchronized`, el texto ya convertido (§4.3, con las mismas validaciones) se entrega a la espera abierta.
   - Si la espera lo acepta, el resultado es `Aceptado("twiml:" + UUID)`, sin intervalo de 3 s y sin HTTP. El menú se guarda igual que con un envío aceptado.
   - Si no hay espera, o ya se cerró o se llenó, se envía por REST como en §4.3. El `To` es `whatsapp:+<E.164>` o la dirección BSUID de la correspondencia. Un sintético sin dirección conocida → rechazo permanente local, sin HTTP.
5. **Intervalos con el perfil `twilio`:** `barberturno.whatsapp.procesador.intervalo` y `barberturno.whatsapp.salida.intervalo` bajan a 300 ms para responder dentro del plazo.
6. **Límites aceptados para el Sandbox:**
   - si la respuesta HTTP no llega a Twilio, un mensaje marcado `twiml:` se pierde sin reintento;
   - los avisos de estado de Twilio para esos mensajes no coinciden con ningún `sid` y se ignoran;
   - el hilo del webhook queda ocupado hasta 12 s.
7. **Medición de tiempo:** se usa `System.nanoTime`; no son fechas de negocio.
8. **Configuración nueva**, validada al arrancar:
   - `respuesta-twiml` (`true`);
   - `respuesta-espera` (10 s, entre 1 y 12 s);
   - `respuesta-silencio` (1,5 s, entre 100 ms y `respuesta-espera`).

### Criterios añadidos
| CT | Criterio |
|---|---|
| CT-10 | Remitente BSUID sin `WaId` → un evento con `wa_id` sintético de 20 dígitos. Es estable para el mismo BSUID y distinto entre dos BSUID distintos. Un `From` inválido no genera evento. Si `WaId` existe, se usa el número. |
| CT-11 | **Recorrido CT-6 con un remitente BSUID y respuestas por TwiML:** cada webhook devuelve en su TwiML las respuestas del bot (menús numerados), sin llamar al servidor Twilio simulado. La reserva aparece en `GET /api/n/barberturno/reservas` y después queda `CANCELADA`. |
| CT-12 | Se responde sin esperar en tres casos: duplicado del mismo `MessageSid` (con lo ya aceptado en esa ventana, o `<Response/>`), segundo mensaje con una espera abierta y remitente inválido. Sin respuestas del bot dentro del plazo, también `<Response/>`. Un mensaje posterior se envía por REST con `To=whatsapp:PE.…` cuando la dirección es conocida, o se rechaza sin HTTP si no lo es. `<`, `&` y comillas quedan escapados. Más de 5 mensajes → los sobrantes van por REST. |
| CT-13 | Un aviso de estado con `To` BSUID actualiza el mensaje por su `sid`. `respuesta-twiml=false` mantiene el comportamiento de §8. Configuración fuera de rango → no arranca. Los registros no contienen BSUID, `wa_id` sintético ni texto. |

## 11. Implementación (Codex 157)

**12/11/2026 · Producción Hecha; pendientes CT-10…CT-13 de Gemini y revisión del coordinador.**
Rama existente `tarea/V2-TWILIO-sandbox`, desde `1c9e5b8`. Sin merge, integración ni push.

- Nuevos componentes `CorrespondenciaDireccionesTwilio` (identidad exacta de V2-DA-103, mapa
  sincronizado con máximo 10 000 entradas) y `BuzonRespuestasTwilio` (una reserva por persona,
  cinco mensajes, plazos monotónicos y cierre atómico con la entrega).
- `TraductorEntradaTwilio`: WaId E.164 prioritario, BSUID como alternativa, inválidos sin evento;
  avisos de estado con la misma identidad sintética.
- `WebhookTwilioController`: abre antes de recibir, no espera con cero eventos nuevos o reserva
  ajena; devuelve los mensajes en orden con los cinco caracteres XML escapados y cierra en finally.
- `ClienteTwilioWhatsApp`: validación/conversión previa; entrega TwiML fuera del bloqueo REST,
  sin intervalo ni HTTP, con `Aceptado("twiml:" + UUID)` y menú guardado. La ruta REST conserva
  el intervalo y admite el BSUID conocido; un sintético desconocido se rechaza localmente.
- `TwilioPropiedades`, `application-twilio.yml`: valores y rangos de §10; procesador/salida a 300 ms.
  Javadoc útil en toda la superficie pública nueva o modificada; registros solo de resultados.
- Seis pruebas existentes: ajustes mecánicos de constructores/componentes y configuración
  `respuesta-twiml=false`; ninguna aserción modificada y ninguna prueba nueva. README actualizado.
  Estado en `10-backlog.md`, conforme a AGENTS §5.1; evidencia en [v2-tw-157.md](pruebas/v2-tw-157.md).

Decisiones menores: retirada de la dirección menos recientemente vista al superar 10 000; el tiempo
inicial cuenta desde abrir, incluida la recepción; copia y cierre de la espera bajo el mismo monitor.
El cierre usa comparación de reserva para no retirar otra posterior. El orden dentro del TwiML es
el de aceptación; no se garantiza el orden de llegada entre TwiML y los sobrantes enviados por REST.
Si procesador/enviador llegan tarde, se toma REST, con el riesgo de HTTP 400 observado por el responsable.
Una cancelación por recepción duplicada/fallida puede descartar una respuesta anterior aceptada en
esa ventana; no se cambia la cola ni se añade compensación. Detalles y demás límites en la evidencia.

Verificación serializada con `mvn-v2-test.ps1`, JDK 21.0.8+9 y PostgreSQL 18.6 de pruebas:

| Argumentos | Resultado | Registro en `backend/target/` |
|---|---|---|
| `-Dtest=*Twilio* test` | 83 pruebas; 0 fallos/errores/omisiones; BUILD SUCCESS | `v2-tw-157-twilio.log` |
| Regresión del encargo 155 (ocho clases, orden completa en evidencia) | 154 pruebas; 0 fallos/errores/omisiones; BUILD SUCCESS | `v2-tw-157-regresion.log` |
| `-DskipTests javadoc:javadoc` | BUILD SUCCESS; 0 avisos | `v2-tw-157-javadoc.log` |

No se ejecutó verify completo ni se acredita todavía CT-10…CT-13, cobertura total o prueba real.
No hay bloqueos de implementación ni cambios necesarios de contrato, DDL o reglas de negocio.

Commit de producción: `38af27af6d2b1af398e2364731830f71d7ca5c7e`.
Cierre documental por asunto `docs(whatsapp): documentar BSUID, TwiML y verificación del encargo 157 [V2-TW]`;
el hash propio se entrega en el informe final. **Merge: ninguno**, por instrucción expresa.

§1–§10 intactos; sin cambios en documentos protegidos, APF2, v1, frontend, migraciones, conversación,
reglas de reserva o webhook Meta. Sin lectura de coordinación, secretos expuestos, datos reales,
servicios de pago/reales, Azure, ngrok, push, remotos, etiquetas, ramas/worktrees nuevos ni cambios de ACL.
Sin procesos propios, escuchas en 8080/8081/4200 ni cerrojo pendiente; se conserva únicamente el Java
preexistente de VS Code, ajeno al encargo.

## 12. Cierre técnico del ajuste BSUID/TwiML (coordinador)

**12/11/2026 · Hecha en local; pendiente de la prueba manual con el teléfono.** Sin integración en `v2`, sin push y sin despliegue.

- **Producción:**
  - Codex 157 (`38af27a`).
  - Corrección de Claude en la revisión (`4f2babc`): un duplicado devuelve lo ya aceptado en la espera.
  - Gemini g129b, solo lectura sobre el diff: **APROBADO**. g129 terminó sin respuesta porque se lanzó desde el worktree y el diff quedaba fuera de su carpeta.
- **Pruebas:**
  - Gemini g128 (`369e7b2`). Ajuste de Claude: el accesor real `estadoHttp()`.
  - Revisión Codex 158, solo lectura: 7 hallazgos medios y 2 bajos sobre la fuerza de las pruebas (orden exacto de los `<Message>`, carreras entre entregar y cerrar, cotas de tiempo, identidad exacta entre entrada y estado, TwiML fuera de la sección sincronizada, validaciones antes del buzón, captura de registros, cierre del servidor falso y una nota documental). La nota documental se resolvió en `5d1f92c`.
  - Los hallazgos se corrigieron en Gemini g130, con tres ajustes de Claude tras ejecutarlas (`2672a55`): los textos reales del bot («Listo:», «Cancelamos tu cita») y un marcador BSUID con formato válido.
- **Resultados:**
  - `*Twilio*`: **161/0**.
  - `verify` completo sobre `f09669d`: **3962 pruebas, 0 fallos/errores/omisiones**, «All coverage checks have been met», Javadoc sin avisos, BUILD SUCCESS (47:14 min).
- **CT-10…CT-13:** cubiertos con un servidor Twilio simulado. Queda la prueba real con el Sandbox desde el teléfono (§7).
