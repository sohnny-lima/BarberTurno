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
