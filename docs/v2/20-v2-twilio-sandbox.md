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

## 13. Navegación, consulta de agenda del barbero y varios números (12/11/2026)

**Hechos de la prueba manual del 12/11/2026** (responsable, Sandbox real, remitente BSUID):
- 11:13–11:15: reserva completa por WhatsApp (ocho pasos; HTTP 200 en 1,7–3,8 s; un `<Message>` por paso).
- 11:17:34: el responsable envió `3` en el menú de servicios, que solo tenía `1` y `2`. La respuesta estuvo lista a los 20 s, con el equipo saturado; la espera ya había vencido, la salida intentó REST al BSUID, Twilio la rechazó y **la respuesta se perdió**.
- Reproducción con mensajes firmados simulados y el equipo sin carga: `3` responde «No entendí…» con el menú **principal**, no con el actual. Un segundo error seguido deriva a una persona y el asistente deja de responder.
- La cuenta de Twilio es de prueba (*Trial*). Twilio no documenta límite de mensajes en el Sandbox, pero las cuentas de prueba tienen 100 unidades gratuitas de WhatsApp y un tope general de 50 mensajes al día. Fuentes: [Sandbox](https://www.twilio.com/docs/whatsapp/sandbox) y [restricciones de la prueba](https://help.twilio.com/articles/360036052753), consultadas el 12/11/2026.

### V2-DA-105 · Opción fuera de rango y «menu» desde cualquier paso
1. **Los menús numerados son un artefacto del adaptador** (§4.3): Meta usa botones y listas. Por eso el adaptador valida el número.
   - `CorrespondenciaMenusTwilio` guarda, junto con las opciones, el **texto completo** del último menú enviado a ese destinatario.
2. Un `Body` formado solo por dígitos, distinto de `0`, **fuera del rango** de un menú vigente:
   - **no genera evento** y no cuenta como fallo para la derivación;
   - el menú guardado sigue vigente;
   - el webhook responde al instante por TwiML con «Esa opción no está en el menú. Responde con un número del 1 al N o escribe «menu» para volver al inicio.» seguido del **texto del menú guardado**;
   - un duplicado del mismo `MessageSid` recibe la misma respuesta;
   - con `respuesta-twiml=false` se mantiene el comportamiento anterior: el número llega como texto.
3. **Pie de los menús numerados:** «Responde con el número o escribe «menu» para volver al inicio.» Se usa tuteo, como el resto del asistente.
4. **Palabras de inicio** (`menu`, `hola`, `inicio`, `0`, ya existentes en `PalabraClave.MENU`):
   - vuelven a la bienvenida desde **cualquier** paso: servicio, profesional, día, hora, consentimiento, resumen pendiente de confirmar y conversación derivada a una persona;
   - una operación pendiente sin confirmar se descarta y **no** se confirma;
   - las citas ya confirmadas **no cambian**;
   - si la conversación estaba derivada, vuelve al asistente y queda registrado igual que con «Devolver al asistente».
5. **Texto libre no reconocido:** el primero responde «No entendí…» y repite el **menú del paso actual**, generado de nuevo con la disponibilidad vigente. Si no hay paso actual, muestra el menú principal. El segundo seguido mantiene la derivación `SIN_PROGRESO` (V2-11).

### V2-DA-106 · Vinculación del barbero por código y consulta «agenda hoy/mañana»
1. **Código de vinculación:**
   - Lo genera una cuenta con **perfil profesional activo** (`barbero` con su `usuario_id`) y **membresía activa** en el negocio de la ruta. Cualquier otra cuenta → 404, igual que ante un recurso ajeno.
   - Ruta: `POST /api/n/{slug}/whatsapp/vinculo-profesional` → `201 {codigo, caducaEn}`.
   - Formato: 8 caracteres del alfabeto `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, mostrados como `XXXX-XXXX`, generados con `SecureRandom`.
   - Caduca a los **10 minutos** y es de **un solo uso**. Generar uno nuevo invalida el anterior sin usar.
   - En la base solo se guarda su SHA-256.
2. **Estado y baja:**
   - `GET /api/n/{slug}/whatsapp/vinculo-profesional` → `{vinculado, vinculadoEn}`. Nunca expone el número ni el `wa_id`.
   - `DELETE` en la misma ruta → 204 y desvincula.
   - Mismas reglas de acceso que la generación.
3. **Por WhatsApp, al número del negocio:**
   - **`vincular XXXX-XXXX`** (sin distinguir mayúsculas; guion y espacios opcionales):
     - busca el código **en el negocio del canal**; debe estar sin usar, sin caducar y con el perfil y la membresía todavía activos;
     - vincula `(negocio, barbero, wa_id)`, sustituyendo cualquier vínculo anterior de ese barbero o de ese `wa_id` en el negocio, y marca el código como usado;
     - responde «Listo, {nombre del barbero}: este WhatsApp quedó vinculado a tu agenda en {negocio}. Escribe «agenda hoy» o «agenda mañana».»
   - **Fallos:**
     - código inexistente, usado, caducado o de otro negocio → «El código no es válido o caducó. Genera uno nuevo desde tu cuenta de BarberTurno.»;
     - a partir del **quinto fallo en una hora** por `wa_id` y negocio → «Demasiados intentos. Vuelve a intentarlo más tarde.», sin consultar el código.
   - **`desvincular`:** borra el vínculo de ese `wa_id` en el negocio y lo confirma.
4. **`agenda hoy` / `agenda mañana`** (acepta `manana`, mayúsculas y espacios). **En cada consulta** se comprueba:
   - que hay vínculo `(negocio del canal, wa_id)`;
   - que el barbero está activo;
   - que su cuenta tiene membresía activa en ese negocio.
5. **Si alguna comprobación falla**, la respuesta es siempre la misma: «Este WhatsApp no tiene acceso a una agenda de esta barbería.» No revela si existe un vínculo en otro negocio. Si falló por membresía o perfil inactivos, el vínculo se borra.
6. **Respuesta de la agenda:**
   - Encabezado: «Tu agenda de {hoy|mañana}, {fecha larga en Lima} ({negocio}):».
   - Una línea por cita: `HH:mm · servicio · nombre e inicial del apellido del cliente`. Sin teléfono ni correo.
   - Solo citas **de ese barbero** en ese negocio, en estados `PENDIENTE` y `CONFIRMADA`, ordenadas por inicio.
   - Como máximo 25 líneas; si hay más, «y N más: revisa la agenda web».
   - Sin citas: «No tienes citas para {hoy|mañana}.»
   - El día se calcula con `TiempoNegocio` y el `Clock` inyectado.
7. **Prioridad:** estos comandos se atienden **antes** del flujo de cliente y **sin** el consentimiento de cliente. Vincularse es un acto de la cuenta del barbero, que acepta el aviso en la web al generar el código. Cualquier otro texto de un barbero vinculado sigue el flujo normal de cliente.
8. **Datos:** migración nueva `V118__whatsapp_vinculo_profesional.sql` con dos tablas, ambas con RLS forzada y la misma política por negocio que las tablas de V116/V117:
   - `codigo_vinculo_whatsapp` (`negocio_id`, `barbero_id`, `codigo_hash` `char(64)` único, `caduca_en`, `usado_en`, `creado_en`; un código sin usar por barbero);
   - `vinculo_whatsapp_profesional` (`negocio_id`, `barbero_id`, `wa_id` con el mismo `CHECK` que `conversacion_whatsapp`, `creado_en`; únicos `(negocio_id, wa_id)` y `(negocio_id, barbero_id)`).
9. **Interfaz web:**
   - En el área del barbero, una tarjeta **«WhatsApp de la barbería»** con el estado, el botón «Generar código» (muestra el código, su caducidad y la instrucción «Envía «vincular XXXX-XXXX» al WhatsApp de la barbería») y «Desvincular».
   - Usa la dirección visual vigente (T-55/T-57).
   - Antes de generar el código, muestra el aviso: «Se guardará el identificador de tu WhatsApp para enviarte tu agenda.»
10. **Registros:** nunca incluyen el código, su hash, el `wa_id`, el BSUID ni nombres de clientes.

### V2-DA-107 · Varios números de Twilio, uno por negocio
1. **Configuración:**
   - `barberturno.whatsapp.twilio.canales` es una lista de `{numero, negocio-slug}`. Si está vacía, se usa el par actual (`numero`, `negocio-slug`), así que la configuración anterior sigue funcionando.
   - Números y slugs no pueden repetirse; si se repiten, la aplicación no arranca.
   - `RegistroCanalTwilioService` registra cada par.
2. **Entrada:**
   - Se acepta el `To` de cualquier número configurado. El sobre usa ese número como `phone_number_id`, y el núcleo enruta al negocio de su canal, como con Meta.
   - Otro `To` → sin evento.
3. **Claves por número:** el menú guardado (V2-DA-105), la espera TwiML (V2-DA-104) y las respuestas retenidas (V2-DA-108) usan la clave **`(número del negocio, wa_id)`**, no solo `wa_id`.
   - **Integridad:** un número enviado a la barbería B nunca se traduce con el menú de A, y una respuesta de A nunca viaja en la espera de B.
4. **Salida REST:** `From` = el número del canal del negocio del mensaje.
5. **Prueba local:**
   - Sandbox `+14155238886` → `barberturno`, y un número ficticio `+15005550006` → `sur`.
   - Solo el primero recibe mensajes reales; el segundo se prueba con webhooks firmados simulados.

### V2-DA-108 · Respuestas tardías a un BSUID
1. Si la espera TwiML ya no está abierta y el destino es un `wa_id` **sintético**, no se intenta REST: el Sandbox lo rechazó el 12/11/2026.
2. La respuesta se **retiene** en memoria con la clave `(número, wa_id)`: como máximo 5 respuestas durante 10 minutos, con resultado `Aceptado("twiml-pendiente:" + UUID)`.
3. Se entrega **delante** de las respuestas del siguiente webhook de esa persona, en el orden original.
4. Las respuestas caducadas o desbordadas se descartan sin registrar el texto.
5. Con un `wa_id` E.164 se mantiene REST.

### Criterios añadidos
| CT | Criterio |
|---|---|
| CT-14 | Menú de servicios con dos opciones, `3` → respuesta inmediata con el aviso y **el mismo menú**, sin evento ni fallo contado. Después, `2` elige la opción 2 de ese menú. `0` y `menu` → bienvenida. Duplicado del `MessageSid` → la misma respuesta. |
| CT-15 | `menu` desde cada paso (servicio, profesional, día, hora, consentimiento, resumen y derivada) → bienvenida. La operación pendiente no se confirma, una cita `CONFIRMADA` sigue igual y la derivada vuelve al asistente. El texto no reconocido repite el menú del paso actual. |
| CT-16 | Vinculación: el código generado vincula una sola vez. Sin efecto: reutilizado, caducado (con el `Clock`), de otro negocio o sustituido por uno nuevo. Al quinto fallo en una hora → bloqueo. Generar sin perfil profesional (cliente o cuenta de otro negocio) → 404. `GET` no expone el `wa_id`. `DELETE` y `desvincular` quitan el acceso. Solo se guarda el hash. |
| CT-17 | `agenda hoy` y `agenda mañana` de un barbero vinculado → solo sus citas `PENDIENTE` y `CONFIRMADA` del día en Lima, en ese negocio, sin citas de otro barbero ni canceladas, y sin teléfono ni correo. Respuesta genérica de no acceso para: un cliente sin vínculo, un barbero vinculado en `sur` que escribe al número de `barberturno` y un barbero con la membresía revocada tras vincularse (además, el vínculo se borra). |
| CT-18 | Dos números: los mensajes al número A crean la reserva en la agenda de `barberturno`, y los del número B en la de `sur`. El mismo BSUID con menús abiertos en A y B no los cruza. El `From` de REST corresponde al negocio. Una configuración duplicada no arranca. |
| CT-19 | Una respuesta tardía a un sintético no hace HTTP y llega delante de las del siguiente webhook de esa persona, en ese número. Si caduca a los 10 min, se descarta. Un destino E.164 sigue por REST. |
| CT-20 | Registros sin código, hash, `wa_id`, BSUID ni nombres de clientes (captura con marcadores ficticios). |

### Plan de prueba manual (actualiza §7)
1. Arranque: `probar-twilio.ps1 -Dominio … -SinVentanas`.
2. Desde el teléfono:
   - reserva completa;
   - `3` en un menú de dos opciones → aviso y el mismo menú;
   - `menu` a mitad del flujo → bienvenida;
   - la cita confirmada se mantiene.
3. Comprobación del coordinador:
   - la cita está en la base temporal;
   - el barbero asignado la ve con `agenda hoy` (vinculado con un código generado por la API con su cuenta ficticia, mediante webhooks firmados simulados) y en `GET /api/n/barberturno/reservas`.
4. Multiempresa con webhooks firmados simulados: número A → `barberturno`, número B → `sur`, y acceso denegado entre negocios.

### Aclaraciones del coordinador tras Codex 159 (12/11/2026)
1. **Texto no reconocido en el resumen pendiente:**
   - repite el resumen y la operación sigue **vigente**;
   - esto sustituye la anulación de V2-11, escenario 5: el cliente vuelve a ver lo que confirmaría;
   - la confirmación posterior aplica igualmente el protocolo de bloqueos.
2. **En modo humano**, el orquestador interpreta la entrada solo para reconocer las palabras de inicio. Cualquier otro texto se guarda sin respuesta, como antes.
3. **Retenidas y aviso inmediato:** se admite que viajen las respuestas retenidas (máximo 5) más el aviso inmediato en una misma respuesta TwiML (hasta 6 `<Message>`). El tope de 5 de V2-DA-104 rige solo para las respuestas recogidas en la espera.
4. **Pruebas existentes** que fallan por estos cambios deliberados (pie de los menús, REST a sintéticos, resumen, modo humano, recuentos del esquema, petición de estado en Agenda): se adaptan al contrato nuevo conservando su intención, sin borrarlas.

## 14. Implementación (Codex 159)

**12/11/2026 · Producción Hecha en local; pendientes CT-14…CT-20 y revisión del coordinador.**
Rama existente `tarea/V2-TWILIO-sandbox`, desde `8b17ec2`. Sin merge ni push.
CT-14…CT-20 quedan a cargo de Gemini por reparto explícito. §1–§13 intactos.

- `whatsapp/twilio/`: canales con validación de duplicados y registro transaccional de todos los pares;
  traducción de entrada/estado y emisor REST por canal; menús completos, esperas y retenidas con clave
  `(número, wa_id)`. Aviso inmediato de rango idempotente por MessageSid sin evento ni fallo.
  Pie aprobado. Sintéticos sin espera se retienen, incluso en modo REST; en ese modo el siguiente
  webhook recoge solo lo previamente retenido, sin esperar respuestas nuevas.
- `conversation/`: navegación desde modo humano mediante la transición existente y bienvenida;
  repetición determinista del paso con consultas actuales. Se guarda un identificador de navegación,
  no una copia del resumen; la confirmación repite el interactivo de la outbox sin ejecutar operaciones.
  `VinculoProfesionalWhatsAppService` atiende comandos antes del flujo de cliente y sin su consentimiento.
- `V118__whatsapp_vinculo_profesional.sql`: las dos tablas, relaciones compuestas, restricciones con
  nombre, índices únicos y RLS forzada. Código de SecureRandom, SHA-256 y diez minutos; un solo uso,
  sustitución atómica y cinco fallos por hora. Serialización de modificaciones por negocio.
- `whatsapp/`: controlador y DTO de vínculo; `CodigoVinculoWhatsApp` protege el código **antes** de
  persistir la cola, también en el transporte Meta. No se cambia su webhook. Seguridad: sesión,
  excepción exacta al filtro para el 404 del perfil/membresía y CSRF obligatorio sin ampliar exenciones.
- `frontend/`: modelo/API y tarjeta de Agenda, estado, aviso, código, caducidad, instrucción y baja;
  proveedor limitado a la vista del negocio, fechas de Lima y estilo T-55/T-57. Sin reglas de negocio
  ni persistencia del código en el navegador. README actualizado.

**Permisos de agenda:** en cada comando se une vínculo local, perfil activo, membresía activa y cuenta
activa; un rechazo borra el vínculo local si existe y siempre responde lo mismo. La lectura de citas
repite esas uniones, limita explícitamente negocio y barbero además de RLS y solo proyecta los campos
del contrato. Día por `TiempoNegocio`/`Clock`, máximo 25 citas, mensajes divididos por líneas.

**Retenidas:** cinco primeras, diez minutos, en orden y delante del siguiente webhook. Un menú retenido
solo se instala cuando se incorpora al TwiML, después de interpretar la nueva entrada con el menú
conocido. Desbordadas y caducadas se descartan sin texto en los registros. Si la persona no vuelve,
no se envían: caducan y una limpieza cada minuto libera la memoria. Se pierden al reiniciar.

Decisiones y hallazgos: contador de fallos en memoria; cuenta activa revalidada adicionalmente;
DELETE web invalida códigos pendientes. La transición desde humano conserva sus dos respuestas y añade
la bienvenida. Un aviso inmediato tras cinco retenidas puede producir seis `<Message>`, conservando
el límite de cinco en el buzón. El modelo solo tiene `cliente.nombre`, sin apellidos estructurados.
Se conserva el enrutamiento a IA de V2-12 cuando está disponible; la repetición nueva rige el camino
determinista. El coordinador debe precisar si V2-DA-105 pretende sustituir también ese enrutamiento.

Verificación, incidencias, fallos deliberados y órdenes completas en
[v2-tw-159.md](pruebas/v2-tw-159.md). No se añaden pruebas nuevas ni se cambian aserciones; solo se
adaptan constructores, claves y configuración de fixtures existentes. Regresión Java ampliada:
639 pruebas, 623 aprobadas, 15 fallos y 1 error por el contrato anterior; focal final de webhook/traducción:
47/0. Javadoc BUILD SUCCESS, sin avisos. Migración V1, acceso cruzado y aislamiento aprobados; las
aserciones de 22 tablas y 17 FK compuestas deben reflejar las 24 y 19 de V118.
Lint y build aprobados con Node 24.21.0;
frontend aislado: 639 pruebas, 609 aprobadas y 30 fallidas en Agenda por el nuevo GET sin resolver
en el fixture y sus errores en cascada. No se ejecuta verify completo, JAR, aplicación manual,
ngrok ni Twilio real. Sin servicios externos, secretos expuestos, cambios en documentos protegidos,
APF2, v1, Azure, remotos, etiquetas, ACL, ramas/worktrees nuevos ni delegación.

Commits de producción: `5267d8f68401ea2d33189c95b68f9bd343cdb14e` y
`07049b292582305cdd0f62ba2c1e04f788b25dd2`. Cierre documental por asunto
`docs(whatsapp): cerrar implementación y evidencias del encargo 159 [V2-TW]`;
hash propio en informe final. **Merge: ninguno**, por instrucción expresa.
Comprobación final: sin procesos propios de verificación, sin escuchas en 8080/8081/4200,
sin cerrojo del ejecutor. Se conservan los procesos preexistentes y de herramientas.

## 15. Despliegue en el App Service de V2 (12/11/2026, autorizado por el responsable)

### V2-DA-109 · Panel del barbero solo donde hay WhatsApp
- **Motivo:** Azure se ejecuta con `BARBERTURNO_WHATSAPP_PROCESADOR_HABILITADO=false` y sin canal; el panel de la agenda invitaría a enviar un código a un número que no existe.
- **Decisión:** `barberturno.whatsapp.vinculo-profesional.habilitado` toma por defecto el valor de `barberturno.whatsapp.procesador.habilitado`. Apagado, `GET|POST|DELETE /api/n/{slug}/whatsapp/vinculo-profesional` responden 404 antes de llamar al servicio y el frontend (que ya trataba el 404) oculta el panel. Las pruebas fijan `true` porque llaman al procesador a mano.
- **Pruebas:** `VinculoProfesionalApagadoTest` (3) más las dirigidas `VinculoProfesionalEndpointsIT`, `VinculoProfesionalWhatsAppIT` y `AgendaProfesionalWhatsAppIT`: 24/0.

### Qué se despliega
- El JAR de esta rama **sin** el perfil `twilio`: el Sandbox, sus credenciales, ngrok y el proxy siguen solo en local (`prod` + `twilio` se rechaza al arrancar).
- La migración V118 (dos tablas nuevas con RLS) se aplica a `barberturno_v2_demo`. Es aditiva: el JAR anterior sigue arrancando (Flyway ignora migraciones futuras).

### Verificación previa (12/11/2026)
- `verify` completo sobre `3737d04`: **4013/0**, JaCoCo y Javadoc sin avisos (1 h 30 min). La primera pasada (`2ae1e26`) detectó que `EsquemaIT` no listaba las tablas de V118 (la migración llegó después del último `verify`, sobre `f09669d`); corregido en `3737d04`.
- Frontend: lint limpio, compilación correcta y **647/647** pruebas. Con 12 procesos en paralelo fallaron 2–3 pruebas distintas en cada pasada por tiempo agotado (5 s) en pantallas que esta rama no toca; con 4 procesos pasan todas.

### Resultado del despliegue (12/11/2026, 22:42–23:05, hora de Lima)
- `barberturno-web-202610` (plan B1 sin cambios, sin recursos nuevos): JAR de `3737d04` con frontend, SHA-256 `02727FD1E3D217F981DC775F6C60D269AB0212CC5D59C18824773A6982EBDB04`. Flyway 117 → **118** en `barberturno_v2_demo`. Arranque en 161 s.
- Respaldo previo: JAR anterior (`39133f58…`), esquema y datos por negocio de `barberturno_v2_demo` (`pg_dump` con RLS por negocio), más la restauración a un punto en el tiempo de Azure (7 días).
- Comprobaciones HTTPS: todas las críticas pasan (salud, `/ingresar`, PWA, inicio de sesión con cookie `Secure`, agendas de propietario, encargado y barbero, bandeja 200, cliente 403, vínculo del barbero 404 con WhatsApp apagado, `/api/twilio/webhook` 404, escritura sin CSRF 403).
- Incidencia: el primer inicio de sesión de `multirol` tras el arranque superó el tiempo de la pasarela (504) y dejó su fila de `usuario` bloqueada unos minutos (503 «recurso ocupado» en el reintento). Se liberó sola; después, 200 en 1,4 s. Observación: valorar `idle_in_transaction_session_timeout` para el rol de la aplicación.
- **WhatsApp en Azure:** apagado (`BARBERTURNO_WHATSAPP_PROCESADOR_HABILITADO=false`, `…_SALIDA_HABILITADO=false`, sin canal ni perfil `twilio`). Lo desplegado queda latente hasta conectar un número real.
