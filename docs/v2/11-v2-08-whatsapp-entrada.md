# BarberTurno v2 · 11 · V2-08 · Entrada de WhatsApp: alcance, contrato y reparto

> Autorizada por el responsable el 28/10/2026. Rama `tarea/V2-08-whatsapp-entrada` desde `v2` = `ccdbb29`. Clase **Crítica**: revisión de Gemini *diff-first*.
>
> Implementan Claude y Codex, en subtareas con archivos separados; Claude coordina. Requisitos: V2-RN-02, V2-RN-07, V2-RN-09, V2-RNF-03, V2-CP-06. Arquitectura: `04-arquitectura-datos.md` §3 y §6.1; decisiones V2-DA-08, 09, 14, 15 y 26, y V2-DA-52…56 (nuevas).

## 1. Alcance
**Entra:**
- `canal_whatsapp`, con alta manual por la plataforma.
- Verificación del *webhook* y validación de la firma.
- `evento_whatsapp_entrante` idempotente.
- Procesador con `SKIP LOCKED`.
- Negocio resuelto por `phone_number_id` y ficha por `wa_id`.
- La entrega de cada mensaje y aviso de estado a un **puerto** que implementarán las tareas siguientes.

**No entra:**
- Respuestas ni salida (V2-09).
- Conversación, consentimiento y creación de fichas o conversaciones (V2-10).
- Derivación a una persona (V2-11) e IA (V2-12).
- Embedded Signup ni cifrado del token (V2-16).
- Pantallas.
- Purga de eventos: los cuerpos contienen datos personales y su retención se fija con la privacidad (P2-06) en V2-10. Queda anotada en §8.

## 2. P2-05 · Aplicación de Meta y número de prueba
**Estado comprobado el 28/10/2026:**
- No hay aplicación de Meta, número de prueba ni credenciales: el entorno local solo tiene claves de base de datos.
- Crearlos corresponde al responsable (acción externa).
- No se compran números ni se contratan intermediarios (V2-DA-09).

**En consecuencia:**
- Todo se implementa y se prueba en local con cuerpos de ejemplo del formato oficial, firmados en las pruebas con un *app secret* ficticio.
- **La conexión real con Meta no queda acreditada** hasta que exista P2-05: suscripción del *webhook*, `hub.challenge` real y un mensaje real del número de prueba.
- Con P2-05 resuelta, la comprobación será manual y fuera de `verify`: un túnel o un despliegue con HTTPS, `BT_WHATSAPP_*` en el entorno y un mensaje desde un destinatario de prueba.

**Formato comprobado** en la documentación oficial de Meta el 28/10/2026 ([webhooks de WhatsApp](https://developers.facebook.com/docs/whatsapp/cloud-api/guides/set-up-webhooks), [referencia de `messages`](https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/reference/messages), [webhooks de Meta](https://developers.facebook.com/documentation/business-messaging/messenger-platform/webhooks)):
- **Verificación:** `GET` con `hub.mode=subscribe`, `hub.verify_token` y `hub.challenge`. Se devuelve el *challenge*, o 403.
- **Firma:** `X-Hub-Signature-256: sha256=<hex>`, HMAC-SHA256 del cuerpo con el *app secret*.
- **Reintentos:** ante cualquier código distinto de 200, durante 7 días y con posibles duplicados.
- **Estructura:** `object = whatsapp_business_account` → `entry[].changes[]` con `field = messages` → `value`, que contiene:
  - `metadata.phone_number_id` y `metadata.display_phone_number`;
  - `contacts[]` con `profile.name` y `wa_id`;
  - `messages[]` con `from`, `id`, `timestamp` y `type`;
  - `statuses[]` con `id`, `status`, `timestamp` y `recipient_id`.

## 3. Contrato de datos (`V113__whatsapp_entrada.sql`, responsable: Claude)
- **`canal_whatsapp`:** el DDL de `04-arquitectura-datos.md` §3, con restricciones de formato (solo dígitos en los identificadores) y `numero_visible varchar(21)` para admitir el `+` y 20 dígitos.
- **`evento_whatsapp_entrante`:** el DDL de §3, más dos columnas (V2-DA-55):
  - `tipo varchar(8) NOT NULL CHECK (tipo IN ('MENSAJE','ESTADO'))`;
  - `motivo varchar(24)`: un código, nunca texto libre ni datos personales. Valores: `NUMERO_NO_REGISTRADO`, `CANAL_DESCONECTADO`, `NEGOCIO_NO_OPERATIVO`, `REMITENTE_INVALIDO` y `FALLO_PROCESAMIENTO`.
- **`cliente`:**
  - `ADD COLUMN wa_id varchar(20)`, solo dígitos (`CHECK (wa_id ~ '^[0-9]{1,20}$')`);
  - índice único parcial `cliente_wa_uk (negocio_id, wa_id) WHERE wa_id IS NOT NULL`;
  - `cliente_identificable` pasa a `usuario_id IS NOT NULL OR wa_id IS NOT NULL OR telefono IS NOT NULL`.
- **`auditoria_plataforma`:** acciones `REGISTRAR_CANAL_WHATSAPP` y `DESCONECTAR_CANAL_WHATSAPP`, con `negocio_id` obligatorio y sin `objetivo_id`.
- **Permisos de la aplicación** (sin propiedad):
  - `canal_whatsapp`: `SELECT`, `INSERT` y `UPDATE (waba_id, phone_number_id, numero_visible, estado, actualizado_en)`.
  - `evento_whatsapp_entrante`: `SELECT`, `INSERT` y `UPDATE (negocio_id, estado, motivo, intentos, proximo_intento, procesado_en)`, sin `DELETE`.
  - `cliente`: no cambia; la escritura de `wa_id` llega en V2-10.
- **RLS:** las dos tablas son de **plataforma** y no llevan RLS (§2 de la arquitectura). V2-DA-26 se aplica a sus repositorios: superficie mínima con su prueba.

## 4. Interfaces
### 4.1 API (responsable: Claude)
| Método y ruta | Acceso | Respuestas |
|---|---|---|
| `GET /api/whatsapp/webhook?hub.mode&hub.verify_token&hub.challenge` | Pública; ruta global | 200 `text/plain` con el *challenge* si `mode = subscribe` y el token coincide (comparación en tiempo constante). En otro caso, o sin token configurado, 403 sin detalle |
| `POST /api/whatsapp/webhook` | Pública; ruta global, **sin CSRF ni sesión** (exención solo en esta ruta) | Cuerpo crudo de hasta 1 MiB (si es mayor, 413 sin efectos). Firma ausente, mal formada o errónea, o sin *app secret* configurado: 401 sin efectos. JSON inválido con firma válida: 400 sin efectos. En otro caso, 200 sin cuerpo, aunque no haya nada que guardar |
| `GET /api/plataforma/negocios/{id}/canal-whatsapp` | `ADMIN_PLATAFORMA` | 200 `CanalWhatsAppDto`; 404 si no existe el negocio o el canal |
| `PUT /api/plataforma/negocios/{id}/canal-whatsapp` | `ADMIN_PLATAFORMA`, CSRF | Cuerpo `{wabaId, phoneNumberId, numeroVisible}`: los dos primeros, dígitos (1–32); el tercero, `+` opcional y 6–20 dígitos. 200 `CanalWhatsAppDto` (alta, o actualización que lo deja `ACTIVO`). 404 si el negocio no existe. 409 `NEGOCIO_SUSPENDIDO` si está suspendido. 409 `CONFLICTO` si no tiene propietario activo. 409 **`NUMERO_WHATSAPP_EN_USO`** (nuevo) si el `phone_number_id` es de otro negocio. Se audita `REGISTRAR_CANAL_WHATSAPP` |
| `POST /api/plataforma/negocios/{id}/canal-whatsapp/desconexion` | `ADMIN_PLATAFORMA`, CSRF | 204, idempotente; se audita `DESCONECTAR_CANAL_WHATSAPP` al cambiar de estado. 404 si no hay canal |

`CanalWhatsAppDto(long negocioId, String wabaId, String phoneNumberId, String numeroVisible, EstadoCanal estado, OffsetDateTime actualizadoEn)`. No hay token en el MVP: `token_cifrado` queda nulo con el número de prueba (V2-DA-14).

### 4.2 Recepción → cola (responsable: Claude)
Por cada `entry[].changes[]` con `field = messages` y `value.messaging_product = whatsapp`, con el `phone_number_id` de `value.metadata`:
- **Mensajes:** cada elemento de `messages[]` crea un evento `tipo = MENSAJE` con `clave_idempotencia = "m:" + id`.
- **Avisos de estado:** cada elemento de `statuses[]` crea un evento `tipo = ESTADO` con `clave = "s:" + id + ":" + status`.
- **`cuerpo` (jsonb):** `{"phoneNumberId", "contacto": {"waId","nombre"} | null, "dato": <el objeto messages[i] o statuses[i] tal como llega>}`. El contacto es el de `contacts[]` cuyo `wa_id` coincide con `from`.
- **Inserción:** con `ON CONFLICT (clave_idempotencia) DO NOTHING`, `estado PENDIENTE`, `intentos 0` y `recibido_en = proximo_intento = clock.instant()`, en la transacción de la petición.
- **Sin efectos:** `object` distinto, otros `field`, errores de cuenta o elementos sin `id`, o con una clave de más de 128 caracteres. Se ignoran con un registro sin datos personales.
- **Negocio:** el *webhook* **no** lo resuelve; `negocio_id` queda nulo (V2-DA-52).

### 4.3 Cola → procesamiento (responsable: Codex)
- **Reclamo:**
  - Transacción corta y sin negocio: `UPDATE … SET intentos = intentos + 1, proximo_intento = :ahora + :reclamo WHERE id IN (SELECT id … WHERE estado = 'PENDIENTE' AND proximo_intento <= :ahora ORDER BY proximo_intento, id LIMIT :lote FOR UPDATE SKIP LOCKED) RETURNING id, intentos`.
  - El valor de `intentos` devuelto es la **ficha del reclamo** (V2-DA-52).
- **Resolución,** sin negocio:
  - Canal por `phone_number_id`:
    - si no existe → `DESCARTADO`/`NUMERO_NO_REGISTRADO`;
    - si es `DESCONECTADO` → `DESCARTADO`/`CANAL_DESCONECTADO`.
  - Negocio suspendido o sin propietario activo → `DESCARTADO`/`NEGOCIO_NO_OPERATIVO` (V2-DA-53).
  - `from`, o `recipient_id` en los estados, que no sea un `wa_id` válido (dígitos, ≤ 20) → `DESCARTADO`/`REMITENTE_INVALIDO`.
- **Entrega:** con `EjecutorEnNegocio.ejecutar(negocioId, …)` (sobrecarga por id del contrato), una transacción por evento:
  1. Bloquea el evento `FOR UPDATE`, exigiendo `estado = PENDIENTE` e `intentos` = ficha. Si no coincide, no hace nada.
  2. Busca la ficha **solo** por `(negocio, wa_id)` exacto. Nunca por teléfono (V2-RN-09), nunca en otro negocio y nunca la crea (V2-DA-54).
  3. Llama al puerto.
  4. Marca `PROCESADO`, con `negocio_id` y `procesado_en`, en esa misma transacción.
- **Fallo del puerto:**
  - El reintento se programa en una transacción nueva, condicionada a la ficha, con espera creciente (base 5 s, ×2 por intento, máximo 5 min).
  - Al llegar a `max-intentos` (5) → `ERROR`/`FALLO_PROCESAMIENTO`, con un registro `WARN` que solo lleva el id del evento.
- **Recuperación:** un evento reclamado y no terminado (caída del proceso) vuelve a estar disponible al vencer `reclamo` (60 s).
- **Cola con varias instancias:** `SKIP LOCKED` impide que se reclame dos veces a la vez. La ficha del reclamo impide terminar un evento reclamado de nuevo.

### 4.4 Puertos (los define Claude en el contrato; los implementan V2-09 y V2-10)
- `ReceptorMensajesWhatsApp.recibir(MensajeEntrante)` y `ReceptorEstadosWhatsApp.recibir(EstadoEntrante)` se llaman dentro de la transacción del negocio. Si el receptor lanza una excepción, la transacción se revierte y se aplica el reintento.
- `MensajeEntrante(long eventoId, long negocioId, String waId, OptionalLong clienteId, String nombrePerfil, String wamid, String tipo, Instant enviadoEn, JsonNode dato)`.
- `EstadoEntrante(long eventoId, long negocioId, String wamid, String estado, String waId, Instant enEstadoDesde, JsonNode dato)`.
- Codex aporta implementaciones por defecto, sin efectos (`@ConditionalOnMissingBean`), que dejan los eventos en `PROCESADO`.

### 4.5 Configuración (responsable: Claude)
`barberturno.whatsapp` (`WhatsAppPropiedades`):

| Propiedad | Origen o valor por defecto |
|---|---|
| `verify-token` | `${BT_WHATSAPP_VERIFY_TOKEN:}` |
| `app-secret` | `${BT_WHATSAPP_APP_SECRET:}` |
| `procesador.habilitado` | `true`; `false` en `application-test.yml` (V2-DA-08) |
| `procesador.intervalo` | `1500ms` |
| `procesador.lote` | `20` |
| `procesador.max-intentos` | `5` |
| `procesador.espera-base` | `5s` |
| `procesador.espera-maxima` | `5m` |
| `procesador.reclamo` | `60s` |

- Sin token o sin secreto, el *webhook* **falla cerrado** (403 y 401) y se registra un aviso al arrancar, sin ningún valor (V2-DA-55).
- Las pruebas usan valores ficticios fijados en la propia prueba; ningún secreto se versiona.
- `@EnableScheduling` va en `ConfiguracionWhatsApp`. `ProcesadorEventosWhatsApp` está siempre presente y sus pruebas llaman directamente a `procesarLote()`; solo el disparo periódico (`ProgramadorEventosWhatsApp`) se condiciona a `procesador.habilitado`. *(Precisado el 28/10/2026, tras el hallazgo del encargo 110b.)*

## 5. Reparto y archivos (un implementador por archivo)
| Subtarea | Implementador | Archivos bajo su responsabilidad |
|---|---|---|
| **V2-08.0 · Contrato** (primero; es la base de las otras dos) | Claude | Este documento, V2-DA-52…56 y la fila de §4 de `10-backlog.md`; `V113__whatsapp_entrada.sql`; `ErrorCodigo` (`NUMERO_WHATSAPP_EN_USO`); `EjecutorEnNegocio` (sobrecarga por id); `Cliente.waId` (campo y lector, sin modificador); en `pe.barberturno.whatsapp`: `WhatsAppPropiedades`, `ConfiguracionWhatsApp`, `CanalWhatsApp`, `EstadoCanal`, `CanalWhatsAppRepository` (superficie mínima: `findByPhoneNumberId`, `findByNegocioId`, `bloquearPorNegocio`, `save`), `EventoEntrante` (solo lectura), `TipoEvento`, `EstadoEvento`, `MotivoEvento`, los dos puertos y sus *records*; `application.yml` y `application-test.yml` |
| **V2-08.1 · Canal y recepción** | Claude | `WebhookWhatsAppController`, `FirmaWebhook`, `ExtractorEventosWhatsApp`, `RecepcionWebhookService`, `EventoEntranteRepository` (inserción idempotente), `CanalWhatsAppService`, `CanalWhatsAppController`, `whatsapp/dto/*`; `SecurityConfig` (ruta pública y exención de CSRF), `ResolutorNegocio` (ruta global); la auditoría de plataforma (acciones nuevas) y sus pruebas (`WebhookWhatsAppIT`, `FirmaWebhookTest`, `ExtractorEventosWhatsAppTest`, `CanalWhatsAppIT`, pruebas de superficie) |
| **V2-08.2 · Procesamiento** | Codex | `ProcesadorEventosWhatsApp`, `ColaEventosWhatsApp` (repositorio del reclamo y los cambios de estado, de superficie mínima), `PoliticaReintentos`, `ReceptoresWhatsAppPorDefecto`; `customers/ClienteRepository` (`findByNegocioIdAndWaId`); pruebas `ProcesadorEventosWhatsAppIT`, `PoliticaReintentosTest` y la de superficie de `ColaEventosWhatsApp` |
| V2-08.3 · Cierre | Claude | `docs/v2/pruebas/v2-08.md`, integración de las dos ramas de trabajo en la de la tarea, `verify` completo y revisión de Gemini |

**Reglas de la frontera:**
- **Archivos compartidos:** las migraciones, los contratos (puertos, *records*, entidades y `ErrorCodigo`) y la configuración son de Claude. Si Codex necesita cambiarlos, se detiene y lo pide; no los edita.
- **Ramas de trabajo:**
  - Claude: `tarea/V2-08-whatsapp-entrada`, en `D:\Proyectos\BarberTurno-v2-08`.
  - Codex: `tarea/V2-08-whatsapp-entrada-procesador`, en `D:\Proyectos\BarberTurno-v2-08-procesador`, desde el commit del contrato.
- **Base de datos:**
  - Todas las pruebas que usan `barberturno_v2_test` se ejecutan **en serie** con el cerrojo `D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock`, que se crea de forma exclusiva antes y se borra después. Lo hace el ejecutor local `D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1`, fuera de Git, que además carga las variables `BT_*` solo en su proceso, sin mostrarlas.
  - Si el cerrojo existe, se espera; nunca se borra uno ajeno.
  - Nunca se ejecutan dos `mvnw` contra la misma base.

## 6. Criterios de aceptación (CH-1…CH-9)
| # | Criterio | Responsable |
|---|---|---|
| CH-1 | `GET` de verificación: el *challenge* con el token correcto; 403 con un token erróneo, sin token o sin configuración | Claude |
| CH-2 | V2-CP-06 (primera parte): firma ausente, mal formada, errónea o sobre un cuerpo alterado → 401 y **cero filas**; sin `app-secret` → 401; también 413 y 400, ambos sin efectos | Claude |
| CH-3 | Firma válida: una fila por mensaje y por aviso de estado; el mismo cuerpo dos veces deja **una** fila por clave; respuesta 200 en menos de 2 s (V2-RNF-03), medida en la prueba; sin CSRF ni sesión, y la exención no alcanza a ninguna otra ruta | Claude |
| CH-4 | API del canal: `ADMIN_PLATAFORMA`; otros roles o sin sesión → 403/401; CSRF ausente o incorrecto → 403; 404, 409 de número en uso, negocio suspendido y sin propietario; auditoría; un número por negocio y un negocio por número (V2-RN-07) | Claude |
| CH-5 | Resolución: número no registrado, canal desconectado, negocio suspendido o sin propietario y remitente inválido → `DESCARTADO` con su motivo, sin llamar al puerto ni abrir el contexto del negocio | Codex |
| CH-6 | Ficha por `wa_id` en el negocio del número: la del otro negocio con el mismo `wa_id` no se usa (dos negocios); una ficha con el mismo teléfono y sin `wa_id` no se vincula (V2-RN-09); no se crean fichas | Codex |
| CH-7 | V2-CP-06 (segunda parte): un mensaje recibido dos veces llega **una** sola vez al puerto. Dos procesadores concurrentes no entregan dos veces el mismo evento (`SKIP LOCKED` más la ficha del reclamo). Un reclamo vencido se recupera | Codex |
| CH-8 | Reintentos: el fallo del puerto revierte su transacción, programa una espera creciente y, al quinto intento, `ERROR`; los registros no contienen datos personales; se usa el `Clock` inyectado | Codex |
| CH-9 | Definición de Hecho de v2: `verify` completo verde, JaCoCo conforme, Javadoc sin avisos, superficie mínima V2-DA-26, evidencia en `pruebas/v2-08.md`, Gemini *diff-first*, nada en `main`, Azure ni las bases de v1 | Claude |

## 7. Pruebas de la frontera
- Las pruebas de V2-08.1 comprueban las filas que se insertan.
- Las de V2-08.2 insertan eventos directamente con el formato de §4.2 y usan receptores de prueba que cuentan las llamadas.
- Al cerrar, una prueba de extremo a extremo une las dos partes: `POST` firmado → procesar el lote → una entrega al puerto, también con el cuerpo repetido.

## 8. Pendientes y bloqueos registrados
- **P2-05** (responsable): aplicación de Meta, número de prueba y *app secret*. Sin ellos, la conexión real no está acreditada (§2).
- **Retención de `evento_whatsapp_entrante.cuerpo`:** datos personales; se fija con P2-06 en V2-10. Hasta entonces solo hay datos ficticios en v2.
- **Integración con V2-07:** V2-07 retira los alias en `ResolutorNegocio` y añade decisiones V2-DA-42…51 y la §10 del *backlog*. Se esperan conflictos pequeños en `ResolutorNegocio.esGlobal`, `07-decisiones.md` y `10-backlog.md`, que se resolverán al integrar la segunda de las dos ramas.
- **Vaciado de los E2E:** el `vaciar()` de `frontend/e2e/base-datos.ts` (reescrito por V2-07) debe incluir `canal_whatsapp` y `evento_whatsapp_entrante` al integrar, igual que `LimpiezaBaseDatos`: las dos tablas referencian `negocio` y `TRUNCATE usuario CASCADE` no las alcanza.
