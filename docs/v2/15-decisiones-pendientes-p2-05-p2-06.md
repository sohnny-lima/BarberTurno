# BarberTurno v2 · 15 · Decisiones pendientes: P2-06 (privacidad y retención) y P2-05 (Meta)

> Preparado por Claude el 04/11/2026 para el responsable. **Nada de esto está aprobado.**
> - Este documento **no declara el cumplimiento** de la Ley 29733 ni de su reglamento (D. S. 016-2024-JUS): describe qué guarda el sistema y propone decisiones. La conformidad la valora el responsable con el docente o el asesor.
> - El único plazo acordado es el de **30 días para las conversaciones sin ficha** (`04-arquitectura-datos.md` §3 y `06-flujos.md` §6), y no se extiende a ningún otro dato.

## 1. P2-06 · Texto de privacidad

### 1.1 Texto configurado hoy (solo en `dev` y `test`)
Texto íntegro de `barberturno.whatsapp.conversacion.privacidad.texto` en `application-dev.yml` y `application-test.yml`:

> BORRADOR (pendiente de P2-06): para reservar en {negocio} guardaremos tu nombre de WhatsApp y tu número. Aviso de privacidad: {enlace}. ¿Aceptas?

- **Botones:** [Aceptar] [No, gracias]. **Enlace configurado:** `https://barberturno.ejemplo.test/privacidad`, ficticio.
- **En `prod` no hay valor:** la aplicación no arranca sin él (V2-DA-68).

### 1.2 Propuesta para aprobar (`13-v2-10-conversacion-guiada.md` §10)
> Hola, soy el asistente de reservas de {negocio}. Para reservar guardaremos tu nombre de WhatsApp y tu número, y los usaremos solo para gestionar tus citas con {negocio}. Puedes leer el aviso de privacidad en {enlace}. ¿Aceptas?

### 1.3 Qué debería contener el aviso enlazado (lista de comprobación, no texto legal)
Identidad y contacto del responsable del tratamiento, y del encargado si lo hay (§3, D3); finalidad; datos tratados (§2); destinatarios y transferencias (Meta como proveedor de WhatsApp); plazos (§2); cómo ejercer los derechos de acceso, rectificación, cancelación y oposición; consecuencias de no aceptar (se puede consultar, pero no reservar); y fecha y versión del aviso.

## 2. P2-06 · Datos almacenados, finalidad y plazos
«Implementado» = existe en el código de la rama conjunta. «Propuesto» = pendiente de aprobación; no hay mecanismo activo.

| Dato (dónde) | Finalidad | Plazo propuesto | Mecanismo de eliminación | Efecto en el historial y la auditoría | Estado |
|---|---|---|---|---|---|
| Conversación **sin ficha** y sus mensajes (`conversacion` con `cliente_id` nulo, `mensaje_whatsapp`) | Responder consultas, ventana de 24 h | **30 días** sin entradas (acordado) | `RetencionWhatsApp`, por negocio y en lotes; políticas restrictivas de `DELETE` (V115) | Ninguno: sin ficha no hay reservas | **Implementado** |
| Cuerpo crudo de los eventos de Meta (`evento_whatsapp_entrante.cuerpo`: texto del cliente, nombre de perfil, `wa_id`) | Idempotencia y diagnóstico | Propuesto: **30 días** tras terminar | `RetencionWhatsApp` vacía `cuerpo` a `{}`; se conserva la fila (clave de idempotencia y estado) | Ninguno | Mecanismo implementado, **desactivado** (`retencion.cuerpo-eventos` vacío) |
| Contenido de los mensajes salientes de conversaciones **con ficha** (`mensaje_whatsapp.contenido`: textos y menús con fechas, servicios y profesionales) | Reenvío, conciliación y seguimiento | Propuesto: 12 meses, o vaciar el contenido a los 90 días conservando estado y `wamid` | **No implementado** | Los avisos de fallo (`notificacion`) no copian datos del cliente | Pendiente |
| Estado de la conversación (`conversacion.estado`, `ultima_entrada_en`) | Paso del menú y franja pendiente | Se vacía a las 24 h de inactividad, al volver al menú y al ejecutarse una operación | `OrquestadorConversacion` | Ninguno | **Implementado** |
| Operaciones pendientes (`operacion_pendiente`: ids, inicio, precio, *nonce*, instantánea del resultado) | Confirmación explícita (V2-RN-10) e idempotencia | Propuesto: 30 días tras terminar | **No implementado** (V115 no concede `DELETE`) | El resultado ejecutado ya consta en `reserva` y en `auditoria_reserva` | Pendiente |
| Ficha de WhatsApp (`cliente`: nombre del perfil, `wa_id`, `privacidad_aceptada_en`) | Reservar y consultar citas | Propuesto: mientras haya relación; supresión a petición | **No implementado** | Reservas y auditoría la referencian (`reserva.cliente_id`, `auditoria_reserva.actor_cliente_id`): borrarla rompería el historial; la alternativa es **anonimizarla** (D5) | Pendiente |
| Reservas y auditoría hechas por WhatsApp (`reserva.canal`, `auditoria_reserva.actor_cliente_id`) | Agenda, historial y responsabilidad (RN de v1) | Los de v1 (sin plazo definido) | Ninguno en v2 | — | Sin cambios |

## 3. P2-06 · Decisiones que requieren tu aprobación
| # | Decisión | Opciones / propuesta |
|---|---|---|
| D1 | Texto del mensaje de consentimiento | §1.2, u otro tuyo. Debe caber en un cuerpo de mensaje (≤ 1024 caracteres) |
| D2 | Aviso enlazado: contenido y URL pública | §1.3; necesita un alojamiento (P2-08) |
| D3 | Papeles: ¿la plataforma es responsable y cada barbería otra responsable, o la plataforma es encargada de cada barbería? | Determina quién figura en el aviso y quién atiende los derechos |
| D4 | Plazos de §2 marcados «Propuesto» | Aprobarlos, cambiarlos o dejarlos sin plazo (sin mecanismo activo) |
| D5 | Supresión de una ficha a petición | Anonimizar (nombre → «Cliente eliminado», `wa_id` nulo) conservando reservas y auditoría, o borrar en cascada (pierde el historial) |
| D6 | ¿Se guardará el contenido de los mensajes **entrantes**? | Lo necesitará la bandeja de V2-11; hoy no se guarda (solo el cuerpo crudo del evento) |
| D7 | Mención de la transferencia a Meta | Redacción del aviso (D2) |

## 4. P2-05 · Pasos en Meta (los realizas tú; no pegues tokens ni secretos en el chat)
Comprueba cada paso en la documentación oficial de WhatsApp Cloud API el día que lo hagas (consultada por última vez en este proyecto el 28/10/2026: Graph API v26.0). **Los secretos van solo en `.local/barberturno.env` del worktree** (`BT_WHATSAPP_VERIFY_TOKEN`, `BT_WHATSAPP_APP_SECRET`, `BT_WHATSAPP_ACCESS_TOKEN`), y los escribes tú; los agentes nunca los muestran.

### 4.1 Común (una vez)
1. Cuenta de Meta para desarrolladores y un **portafolio comercial** (Business Manager) de la plataforma.
2. Una **app** de tipo empresa con el producto **WhatsApp** añadido. Meta asigna una cuenta de WhatsApp Business de prueba y un **número de prueba**.
3. Anota, sin secretos: el **Phone number ID** y el **WABA ID** del número de prueba. Se registran como canal del negocio de prueba (`PUT /api/plataforma/negocios/{id}/canal-whatsapp`, V2-DA-56).

### 4.2 Para **recibir** mensajes (V2-08)
1. Un **endpoint HTTPS público** que llegue a `POST /api/whatsapp/webhook`. En local hace falta un túnel o un *staging* (P2-08): decides tú; no se ha contratado nada.
2. En la app, configura el *webhook* con la URL de devolución y un **token de verificación** que tú eliges (→ `BT_WHATSAPP_VERIFY_TOKEN`), y suscríbete al campo **`messages`**.
3. El **app secret** (→ `BT_WHATSAPP_APP_SECRET`) para la firma `X-Hub-Signature-256`.
4. Prueba: escribe al número de prueba desde un teléfono tuyo. El evento queda `PROCESADO` y la conversación responde con el menú (en la outbox).

### 4.3 Para **enviar texto** (V2-09/10)
1. Un **token de acceso**: el temporal de la consola (caduca en unas 24 h) o el de un usuario del sistema del portafolio (→ `BT_WHATSAPP_ACCESS_TOKEN`).
2. Añade los números destinatarios de prueba permitidos en la consola de la app (el número de prueba solo envía a destinatarios registrados).
3. El texto y los mensajes interactivos solo salen **dentro de la ventana de 24 h** desde el último mensaje del cliente.

### 4.4 Para **probar plantillas** (fuera de la ventana)
1. Crea en WhatsApp Manager las tres plantillas *utility* en español: `bt_recordatorio_cita`, `bt_cita_modificada_por_negocio` y `bt_cita_cancelada_por_negocio`, con los parámetros con nombre de `12-v2-09-whatsapp-salida.md` §3.5.
2. Espera su **aprobación** por Meta.
3. Las plantillas de *utility* tienen coste por conversación según la tarifa de Meta (`09-costes.md`): confírmalo antes de enviar.

### 4.5 Lo que no se hará sin tu autorización expresa
Enviar mensajes reales, crear túneles o servicios públicos, verificar el negocio (Fase 3), activar pagos, Tech Provider o Embedded Signup (V2-16).
