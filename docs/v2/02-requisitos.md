# BarberTurno v2 · 02 · Requisitos

> Estado: propuesta · 21/10/2026. Prefijo `V2-` para no mezclar con los ID de v1, que siguen vigentes y **se heredan** salvo que aquí se diga lo contrario. Prioridad MoSCoW referida al MVP (fases 0–3 de `01-vision-alcance.md` §4).

## 1. Herencia de v1
- Se heredan sin cambios **RF-04…RF-21** y **RN-01…RN-25**, aplicados **dentro de cada negocio**. Los parámetros de RN-07, RN-19, RN-20 y RN-21 pasan a ser configuración del negocio (con los valores de v1 por defecto).
- Cambian: **RF-01** (registro: crea cuenta y, si se hace desde la página de un negocio, la ficha de cliente de ese negocio), **RF-19** (gestión de usuarios: pasa a gestión del equipo por negocio), **RN-04** (el solape del cliente se comprueba por ficha, es decir, dentro del negocio; ver V2-RN-08), **RN-19** (10 profesionales activos **por negocio**), **RN-26** (sustituida por V2-RN-04).

## 2. Requisitos funcionales nuevos
| ID | Requisito | Prioridad | Fase |
|---|---|---|---|
| V2-RF-01 | El administrador de la plataforma crea un negocio (nombre, `slug` único, zona horaria, parámetros) y emite la invitación de su primer propietario. | Must | 0 |
| V2-RF-02 | El administrador de la plataforma lista, suspende y reactiva negocios. | Must | 0 |
| V2-RF-03 | Un propietario o encargado invita a una persona por correo con un rol (`ENCARGADO` o `BARBERO`; `PROPIETARIO` solo lo invita un propietario). La invitación es un enlace de un solo uso, caduca a los 7 días y se puede revocar. | Must | 0 |
| V2-RF-04 | La persona invitada acepta con una cuenta cuyo correo coincide con el invitado (si no tiene cuenta, la crea en el mismo flujo). Aceptar crea la membresía. | Must | 0 |
| V2-RF-05 | El propietario cambia roles y revoca membresías; cualquier miembro puede salir del negocio. Nunca queda un negocio sin propietario activo. | Must | 0 |
| V2-RF-06 | Una persona con varias membresías o fichas elige el negocio con el que trabaja; la URL del negocio (`/n/{slug}`) identifica el negocio en cada pantalla. | Must | 0 |
| V2-RF-07 | Cada negocio tiene una página pública (`/n/{slug}`) para explorar servicios, profesionales y disponibilidad, y para reservar con cuenta. | Must | 0 |
| V2-RF-08 | Restablecimiento de acceso con contraseña temporal (como RF-19), sujeto a V2-RN-16: la plataforma puede restablecer cualquier cuenta (auditado); el propietario o encargado, solo las cuentas de su equipo o de sus clientes que **no** tengan membresías ni fichas en otros negocios. | Must | 0 |
| V2-RF-09 | El propietario configura el canal de WhatsApp del negocio (en el MVP, alta asistida por la plataforma; en la Fase 3, Embedded Signup) y ve su estado. | Must | 1 |
| V2-RF-10 | Un cliente consulta por WhatsApp servicios, profesionales y disponibilidad del negocio dueño del número. | Must | 1 |
| V2-RF-11 | Un cliente reserva por WhatsApp; la reserva se crea solo tras una **confirmación explícita** sobre un resumen con fecha completa, hora, profesional, servicio, duración y precio. | Must | 1 |
| V2-RF-12 | Un cliente consulta por WhatsApp sus citas futuras en ese negocio. | Must | 1 |
| V2-RF-13 | Un cliente reprograma o cancela por WhatsApp con las reglas de la web (RN-07, RN-09) y con confirmación explícita. | Must | 1 |
| V2-RF-14 | El cliente pide una persona (o el asistente deriva por reglas); la conversación pasa a modo humano, el personal recibe un aviso y responde desde una bandeja web; puede devolverla al asistente. | Must | 1 |
| V2-RF-15 | Consentimiento: antes de crear la ficha por WhatsApp se informa del aviso de privacidad; los recordatorios proactivos requieren opt-in explícito y se pueden dar de baja con una palabra clave. | Must | 1 |
| V2-RF-16 | El asistente entiende mensajes en lenguaje natural (servicio, profesional, fecha y franja relativas) y pide aclaraciones cuando falta o es ambiguo un dato. | Should | 2 |
| V2-RF-17 | Recordatorio de cita por plantilla *utility* para clientes con opt-in (p. ej., 24 h antes), con botones para confirmar asistencia o cancelar. | Should | 3 |
| V2-RF-18 | Vinculación de la ficha de WhatsApp con la cuenta web del cliente mediante un enlace de un solo uso enviado al propio chat; también sirve para recuperar el acceso web. | Could | 3 |
| V2-RF-19 | Incorporación de números de otros negocios mediante Embedded Signup (Tech Provider). | Should | 3 |

## 3. Reglas de negocio nuevas
| ID | Regla |
|---|---|
| V2-RN-01 | Todo dato operativo (servicio, profesional, jornada, bloqueo, cliente, reserva, auditoría, aviso, conversación, mensaje, operación pendiente) pertenece a **exactamente un negocio** y solo se relaciona con datos del mismo negocio. La base de datos lo impone (claves compuestas con `negocio_id`, RLS). |
| V2-RN-02 | El negocio de una petición web se identifica por la ruta y se **autoriza** por la membresía o la ficha del actor en ese negocio; el de un mensaje de WhatsApp, por el número receptor (`phone_number_id`) registrado en un único negocio. Un identificador recibido del cliente nunca autoriza. |
| V2-RN-03 | El rol de plataforma no concede permisos en los negocios; la membresía no concede permisos en la plataforma. |
| V2-RN-04 | Solo se obtiene una membresía aceptando una invitación vigente, no revocada, no usada y emitida para el mismo correo (comparación en minúsculas), o en el alta del negocio. El registro público solo crea cuentas y fichas de cliente. |
| V2-RN-05 | Una persona tiene como máximo una membresía por negocio. |
| V2-RN-06 | Cada negocio tiene al menos un `PROPIETARIO` activo. No se puede revocar, degradar ni desactivar al último. |
| V2-RN-07 | Un número de WhatsApp (`phone_number_id`) pertenece a un solo negocio y un negocio tiene como máximo un número en el MVP. |
| V2-RN-08 | El solape del cliente (RN-04) se comprueba **por ficha**, es decir, dentro de cada negocio. La plataforma no cruza datos de una persona entre negocios (comprobarlo revelaría a un negocio la agenda de otro). |
| V2-RN-09 | Por WhatsApp, el cliente es la ficha cuyo `wa_id` coincide con el remitente en el negocio receptor. Una ficha creada por el personal con un teléfono **no** se vincula sola a un `wa_id`: solo por una prueba de control del número (V2-RF-18) o por una acción explícita del personal (aplazada). |
| V2-RN-10 | Ninguna operación que modifique reservas se ejecuta por WhatsApp sin una confirmación explícita y deterministica: la pulsación de un botón (o la respuesta «sí» a un único resumen vigente) que referencia una **operación pendiente** creada por el servidor, vigente (caduca a los 10 min), no usada y de la misma ficha. |
| V2-RN-11 | Todo mensaje que afirme que una operación se ha realizado lo compone el servidor **a partir del resultado confirmado en la base de datos** (plantillas de texto del backend), nunca el modelo de IA. |
| V2-RN-12 | El asistente solo ofrece horarios devueltos por la consulta de disponibilidad del servidor en ese mismo turno; los horarios se presentan siempre con fecha completa (día de la semana, día, mes, año y hora en Lima). |
| V2-RN-13 | Fuera de la ventana de atención de 24 h de WhatsApp solo se envían plantillas aprobadas; los recordatorios exigen opt-in vigente. |
| V2-RN-14 | Límites de uso (parametrizables por negocio): por conversación, mensajes entrantes por minuto y llamadas al modelo por día; por negocio, presupuesto mensual de IA. Al superarlos, el asistente pasa a menús sin IA o a una persona, nunca falla en silencio. |
| V2-RN-15 | Una conversación en modo humano no recibe respuestas automáticas salvo el acuse inicial y los mensajes de sistema (p. ej., fuera del horario de atención). |
| V2-RN-16 | Un negocio no modifica la **identidad global** de una persona (correo, contraseña, nombre de la cuenta, estado de la cuenta) si esa persona tiene membresías o fichas en otro negocio: en ese caso solo actúa sobre lo suyo (revocar la membresía, desactivar el perfil de profesional o la ficha, y el nombre visible local `barbero.nombre_visible`) y el restablecimiento lo hace la plataforma. Desactivar a un profesional **no** desactiva su cuenta global (cambia RN-16 y el comportamiento de v1 en `BarberoService`). *Hallazgo de la auditoría 075, riesgo 4.* |

## 4. Requisitos no funcionales nuevos
| ID | Requisito | Verificación |
|---|---|---|
| V2-RNF-01 | **Aislamiento verificable**: batería de pruebas con dos negocios que cubre base de datos (RLS y claves compuestas), API (404 en recursos de otro negocio), tareas en segundo plano y conversación. | `AislamientoIT` y evaluación de conversaciones |
| V2-RNF-02 | La aplicación se conecta con un rol **sin** propiedad de las tablas ni `BYPASSRLS`; las migraciones las ejecuta el rol propietario. | Prueba de esquema que consulta `pg_roles` y `pg_class.relforcerowsecurity` |
| V2-RNF-03 | *Webhook*: se valida `X-Hub-Signature-256` sobre el cuerpo crudo antes de cualquier proceso; se responde 200 en < 2 s (procesamiento asíncrono); el procesamiento es idempotente por identificador de mensaje. | `WebhookWhatsAppIT` |
| V2-RNF-04 | Credenciales de WhatsApp y claves de IA cifradas en reposo (AES-GCM con clave de entorno) o en un almacén de secretos; nunca en registros, respuestas de API ni prompts. | Pruebas de registro con `ListAppender` (como T-58) |
| V2-RNF-05 | Al modelo de IA solo se envían los datos mínimos del negocio y del cliente (nombre de pila; nunca teléfono, correo ni datos de otros negocios). | Prueba que inspecciona la petición al adaptador falso |
| V2-RNF-06 | Latencia de respuesta del asistente: p95 ≤ 8 s desde que llega el *webhook* hasta que se envía la respuesta (con IA); ≤ 3 s en menús sin IA. | Medición en el piloto |
| V2-RNF-07 | Las reglas de v1 (RNF-03, 04, 05, 10, 13) siguen vigentes; JaCoCo ≥ 70 % se extiende a los paquetes `tenancy`, `whatsapp` y `conversation`. | `verify` |

## 5. Casos de prueba de aceptación nuevos (resumen; detalle en `08-pruebas-migracion-reversion.md`)
| ID | Caso | Requisitos |
|---|---|---|
| V2-CP-01 | Dos negocios con datos homónimos: cada actor ve solo los suyos; accesos cruzados → 404; consultas sin contexto → 0 filas. | V2-RN-01/02, V2-RNF-01 |
| V2-CP-02 | Registro público en la página de un negocio: crea cuenta + ficha; nunca membresía. | V2-RN-04 |
| V2-CP-03 | Invitación: aceptar con otro correo, caducada, revocada o reutilizada → rechazo; válida → membresía. | V2-RF-03/04, V2-RN-04 |
| V2-CP-04 | Dos propietarios se revocan mutuamente a la vez → queda uno. | V2-RN-06 |
| V2-CP-05 | CP-03 de v1 con negocio: 10 reservas simultáneas, mezclando web y WhatsApp → 1 éxito. | RN-03, V2-RF-11 |
| V2-CP-06 | *Webhook* sin firma o con firma errónea → 401 sin efectos; el mismo mensaje dos veces → una sola respuesta y una sola operación. | V2-RNF-03 |
| V2-CP-07 | Reserva por WhatsApp de principio a fin con confirmación por botón; «sí» tardío sobre una operación caducada → no reserva. | V2-RF-11, V2-RN-10/11 |
| V2-CP-08 | Evaluación de conversaciones con umbrales de `08-pruebas-migracion-reversion.md` §4. | V2-RF-16, V2-RN-11/12 |
| V2-CP-09 | Derivación a una persona y vuelta al asistente. | V2-RF-14, V2-RN-15 |
| V2-CP-10 | El negocio por defecto migrado desde una copia de v1 conserva todas sus reservas, auditorías y avisos (conteos y sumas idénticos). | `08-pruebas-migracion-reversion.md` §6 |
