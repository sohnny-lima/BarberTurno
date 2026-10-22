# BarberTurno v2 · 04 · Arquitectura y modelo de datos

> Estado: propuesta · 21/10/2026. Parte de la arquitectura de v1 (`docs/arquitectura.md`), que se conserva salvo donde aquí se indique. Las decisiones y sus alternativas están en `07-decisiones.md` (V2-DA-xx).

## 1. Vista general
Se mantiene el **monolito modular Spring Boot + SPA Angular + PostgreSQL** (DA-01). v2 añade tres módulos y un contexto transversal; no hay servicios nuevos que desplegar salvo los externos (Meta y el proveedor de IA).

```mermaid
flowchart LR
  subgraph Clientes
    SPA[SPA Angular<br/>/n/{slug}/… y /plataforma]
    WA[WhatsApp del cliente]
  end
  META[Meta WhatsApp<br/>Cloud API]
  LLM[Proveedor de IA<br/>tool use]
  subgraph Monolito["Spring Boot (un único jar)"]
    SEC[Seguridad: JWT cookie + CSRF]
    TEN[tenancy: ResolutorNegocio<br/>ContextoNegocio + set_config]
    API[Controladores /api/n/{slug}/… y /api/plataforma/…]
    DOM[Dominio v1: catalog · scheduling · reservations<br/>audit · notifications · reporting]
    PLAT[platform: negocios · membresías · invitaciones]
    WHK[whatsapp: webhook · cola entrante · outbox]
    CONV[conversation: estado · herramientas · guardas]
    JOBS[Trabajos @Scheduled por negocio]
  end
  DB[(PostgreSQL 18<br/>RLS FORCE + EXCLUDE)]
  SPA --> SEC --> TEN --> API --> DOM --> DB
  API --> PLAT --> DB
  WA <--> META
  META -- webhook firmado --> WHK
  WHK --> DB
  JOBS --> WHK
  JOBS --> CONV --> DOM
  CONV <--> LLM
  WHK -- envío --> META
```

## 2. Módulos
| Módulo | Novedad | Responsabilidad |
|---|---|---|
| `tenancy` (nuevo, en `common`) | `ContextoNegocio`, `ResolutorNegocio`, `GestorTransaccionesNegocio`, `EjecutorEnNegocio` | Resolver y autorizar el negocio de cada petición o trabajo; fijar `app.negocio_id` al comenzar cada transacción. |
| `platform` (nuevo) | `Negocio`, `Membresia`, `Invitacion`, `RolNegocio`, `RolPlataforma`, servicios y controladores `/api/plataforma/**` y `/api/n/{slug}/equipo/**` | Alta de negocios, equipo, invitaciones, último propietario. |
| `customers` (nuevo, extraído de `users`) | `Cliente` (ficha por negocio) | Fichas, búsqueda, alta por el personal, vínculo con cuenta y con `wa_id`. |
| `whatsapp` (nuevo) | `CanalWhatsApp`, `EventoEntrante`, `MensajeSaliente`, `WebhookController`, `ClienteGraphApi`, `CifradorCredenciales` | *Webhook*, idempotencia, colas, envío, estados, plantillas. |
| `conversation` (nuevo) | `Conversacion`, `OperacionPendiente`, `Orquestador`, `HerramientasReserva`, `ModeloConversacional` (puerto) + `ModeloAnthropic` (adaptador), `GuardasSalida`, `MenusGuiados` | Máquina de estados del diálogo, herramientas, confirmaciones, derivación, límites. |
| `users`, `auth` | `usuario` pierde `rol` y gana `rol_plataforma`; JWT sin cambios de formato | Cuentas globales. |
| `catalog`, `scheduling`, `reservations`, `audit`, `notifications`, `reporting` | `negocio_id` en sus tablas; reglas y protocolo **sin cambios** | Igual que v1, siempre dentro de un negocio. |

Dependencias nuevas permitidas: `conversation → reservations, scheduling, catalog, customers, whatsapp(outbox)`; `whatsapp → tenancy, customers`; nadie depende de `conversation`. **El orquestador solo llama a los servicios públicos del dominio** (los mismos que usan los controladores), nunca a repositorios.

## 3. Modelo de datos
### 3.1 Clasificación de tablas
| Ámbito | Tablas | RLS |
|---|---|---|
| **Plataforma** (sin negocio o mapeo global) | `negocio`, `usuario`, `membresia`, `invitacion`, `canal_whatsapp`, `evento_whatsapp_entrante` | No (acceso solo desde `platform`, `auth`, `tenancy` y `whatsapp`) |
| **Negocio** (`negocio_id NOT NULL`) | `servicio`, `barbero`, `jornada`, `bloqueo`, `cliente`, `reserva`, `auditoria_reserva`, `notificacion`, `conversacion`, `mensaje_whatsapp`, `operacion_pendiente`, `uso_ia` | **Sí, `ENABLE` + `FORCE`** |

### 3.2 DDL propuesto (borrador; se fija en cada tarea)
```sql
-- Plataforma -------------------------------------------------------------
CREATE TABLE negocio (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slug          varchar(40)  NOT NULL,
  nombre        varchar(100) NOT NULL,
  zona_horaria  varchar(40)  NOT NULL DEFAULT 'America/Lima',
  estado        varchar(12)  NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO','SUSPENDIDO')),
  -- parámetros de reserva (antes barberturno.reservas.*), con los valores de v1
  anticipacion_cambio_cliente_min smallint NOT NULL DEFAULT 120,
  horizonte_dias                  smallint NOT NULL DEFAULT 30,
  limite_reservas_activas         smallint NOT NULL DEFAULT 3,
  limite_barberos_activos         smallint NOT NULL DEFAULT 10,
  confirmacion_manual             boolean  NOT NULL DEFAULT false,
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT negocio_slug_formato CHECK (slug ~ '^[a-z0-9](-?[a-z0-9])*$'),
  CONSTRAINT negocio_id_rango CHECK (id < 2147483648)   -- cabe en la clave int4 de los advisory locks de dos claves
);
CREATE UNIQUE INDEX negocio_slug_uk ON negocio (slug);

ALTER TABLE usuario ADD COLUMN rol_plataforma varchar(16) NOT NULL DEFAULT 'NINGUNO'
  CHECK (rol_plataforma IN ('NINGUNO','ADMIN_PLATAFORMA'));
-- usuario.rol se elimina en una migración posterior (expandir → migrar → contraer, 08-pruebas-migracion-reversion.md §6.1)

CREATE TABLE membresia (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  negocio_id bigint NOT NULL REFERENCES negocio(id),
  usuario_id bigint NOT NULL REFERENCES usuario(id),
  rol    varchar(12) NOT NULL CHECK (rol IN ('PROPIETARIO','ENCARGADO','BARBERO')),
  estado varchar(10) NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA','REVOCADA')),
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT membresia_unica_uk UNIQUE (negocio_id, usuario_id)
);

CREATE TABLE invitacion (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  negocio_id  bigint NOT NULL REFERENCES negocio(id),
  correo      varchar(254) NOT NULL CHECK (correo = lower(correo)),
  rol         varchar(12)  NOT NULL CHECK (rol IN ('PROPIETARIO','ENCARGADO','BARBERO')),
  token_hash  bytea        NOT NULL,          -- SHA-256 del token; el token solo se muestra una vez
  estado      varchar(10)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','ACEPTADA','REVOCADA')),
  expira_en   timestamptz  NOT NULL,
  invitada_por bigint NOT NULL REFERENCES usuario(id),
  aceptada_por bigint REFERENCES usuario(id),
  barbero_id   bigint,                        -- perfil que se vincula al aceptar (solo rol BARBERO)
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT invitacion_barbero_fk  FOREIGN KEY (negocio_id, barbero_id) REFERENCES barbero (negocio_id, id),
  CONSTRAINT invitacion_barbero_rol CHECK (barbero_id IS NULL OR rol = 'BARBERO')
);
CREATE UNIQUE INDEX invitacion_token_uk ON invitacion (token_hash);
CREATE UNIQUE INDEX invitacion_pendiente_uk ON invitacion (negocio_id, correo) WHERE estado = 'PENDIENTE';

CREATE TABLE canal_whatsapp (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  negocio_id      bigint NOT NULL UNIQUE REFERENCES negocio(id),   -- V2-RN-07: uno por negocio
  waba_id         varchar(32) NOT NULL,
  phone_number_id varchar(32) NOT NULL,
  numero_visible  varchar(20) NOT NULL,
  token_cifrado   bytea,          -- AES-GCM; null con el número de prueba (token de la app en el entorno)
  estado varchar(12) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO','DESCONECTADO')),
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL
);
CREATE UNIQUE INDEX canal_whatsapp_numero_uk ON canal_whatsapp (phone_number_id);

CREATE TABLE evento_whatsapp_entrante (            -- bandeja de entrada idempotente
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  clave_idempotencia varchar(128) NOT NULL,        -- wamid del mensaje o id+estado del aviso de estado
  phone_number_id    varchar(32)  NOT NULL,
  negocio_id         bigint REFERENCES negocio(id),-- null si el número no está registrado
  cuerpo             jsonb        NOT NULL,
  estado varchar(12) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','PROCESADO','DESCARTADO','ERROR')),
  intentos smallint NOT NULL DEFAULT 0, proximo_intento timestamptz NOT NULL,
  recibido_en timestamptz NOT NULL, procesado_en timestamptz
);
CREATE UNIQUE INDEX evento_whatsapp_clave_uk ON evento_whatsapp_entrante (clave_idempotencia);
CREATE INDEX evento_whatsapp_cola_ix ON evento_whatsapp_entrante (proximo_intento) WHERE estado = 'PENDIENTE';

-- Negocio (todas con negocio_id; cada padre expone UNIQUE (negocio_id, id)) --
ALTER TABLE servicio ADD COLUMN negocio_id bigint NOT NULL REFERENCES negocio(id);
ALTER TABLE servicio ADD CONSTRAINT servicio_negocio_id_uk UNIQUE (negocio_id, id);
DROP INDEX servicio_nombre_uk;
CREATE UNIQUE INDEX servicio_nombre_uk ON servicio (negocio_id, lower(nombre));     -- antes global

ALTER TABLE barbero ADD COLUMN negocio_id bigint NOT NULL REFERENCES negocio(id);
ALTER TABLE barbero ADD CONSTRAINT barbero_negocio_id_uk UNIQUE (negocio_id, id);
ALTER TABLE barbero ALTER COLUMN usuario_id DROP NOT NULL;     -- perfil creado antes de aceptar la invitación
ALTER TABLE barbero ADD COLUMN nombre_visible varchar(100) NOT NULL;  -- nombre local; el negocio no edita usuario.nombre (V2-RN-16)
-- usuario_id UNIQUE global pasa a UNIQUE (negocio_id, usuario_id): la misma persona puede ser barbera en dos negocios

CREATE TABLE cliente (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  negocio_id bigint NOT NULL REFERENCES negocio(id),
  usuario_id bigint REFERENCES usuario(id),                 -- cuenta web vinculada (opcional)
  nombre     varchar(100) NOT NULL,
  telefono   varchar(16),                                   -- E.164 sin '+', no verificado
  wa_id      varchar(20),                                   -- remitente verificado por WhatsApp
  privacidad_aceptada_en timestamptz,
  whatsapp_optin_en      timestamptz, whatsapp_optout_en timestamptz,
  activo boolean NOT NULL DEFAULT true,
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT cliente_negocio_id_uk UNIQUE (negocio_id, id),
  CONSTRAINT cliente_identificable CHECK (usuario_id IS NOT NULL OR wa_id IS NOT NULL OR telefono IS NOT NULL)
);
CREATE UNIQUE INDEX cliente_usuario_uk ON cliente (negocio_id, usuario_id) WHERE usuario_id IS NOT NULL;
CREATE UNIQUE INDEX cliente_wa_uk      ON cliente (negocio_id, wa_id)      WHERE wa_id IS NOT NULL;

-- reserva: FK compuestas que impiden mezclar negocios
ALTER TABLE reserva ADD COLUMN negocio_id bigint NOT NULL REFERENCES negocio(id);
ALTER TABLE reserva ADD CONSTRAINT reserva_negocio_id_uk UNIQUE (negocio_id, id);
ALTER TABLE reserva ADD CONSTRAINT reserva_barbero_fk  FOREIGN KEY (negocio_id, barbero_id)  REFERENCES barbero  (negocio_id, id);
ALTER TABLE reserva ADD CONSTRAINT reserva_servicio_fk FOREIGN KEY (negocio_id, servicio_id) REFERENCES servicio (negocio_id, id);
ALTER TABLE reserva ADD CONSTRAINT reserva_cliente_fk  FOREIGN KEY (negocio_id, cliente_id)  REFERENCES cliente  (negocio_id, id);
-- reserva.cliente_id pasa a referenciar cliente (no usuario). EXCLUDE de barbero y de cliente sin cambios:
-- barbero_id y cliente_id ya son de un único negocio, así que la exclusión es por negocio de forma implícita.
-- jornada, bloqueo, auditoria_reserva, notificacion: negocio_id + FK compuesta al padre (barbero o reserva).

CREATE TABLE conversacion (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  negocio_id bigint NOT NULL REFERENCES negocio(id),
  wa_id      varchar(20) NOT NULL,                  -- la conversación existe antes de la ficha (consentimiento pendiente)
  cliente_id bigint,                                -- se rellena al aceptar la privacidad (06-flujos.md §6)
  modo   varchar(8)  NOT NULL DEFAULT 'ASISTENTE' CHECK (modo IN ('ASISTENTE','HUMANO')),
  estado jsonb       NOT NULL DEFAULT '{}',          -- datos ya fijados: servicio, profesional, fecha, franja (ciclo de vida en §7.1)
  ultima_entrada_en timestamptz,                     -- ventana de 24 h (V2-RN-13)
  version integer NOT NULL DEFAULT 0,
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT conversacion_negocio_id_uk UNIQUE (negocio_id, id),
  CONSTRAINT conversacion_cliente_fk FOREIGN KEY (negocio_id, cliente_id) REFERENCES cliente (negocio_id, id),
  CONSTRAINT conversacion_wa_uk      UNIQUE (negocio_id, wa_id),
  CONSTRAINT conversacion_cliente_uk UNIQUE (negocio_id, cliente_id)   -- admite varios NULL
);
-- Invariante de servicio: si cliente_id no es nulo, cliente.wa_id = conversacion.wa_id.
-- Las conversaciones sin ficha (no se aceptó la privacidad) se purgan con sus mensajes a los 30 días.

CREATE TABLE mensaje_whatsapp (                     -- historial y outbox de salida
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  negocio_id bigint NOT NULL, conversacion_id bigint NOT NULL,
  direccion varchar(8) NOT NULL CHECK (direccion IN ('ENTRANTE','SALIENTE')),
  origen    varchar(10) NOT NULL CHECK (origen IN ('CLIENTE','ASISTENTE','SISTEMA','PERSONA')),
  tipo      varchar(12) NOT NULL CHECK (tipo IN ('TEXTO','INTERACTIVO','PLANTILLA')),
  contenido jsonb NOT NULL,
  wamid     varchar(128),
  estado varchar(10) NOT NULL CHECK (estado IN ('RECIBIDO','PENDIENTE','ENVIADO','ENTREGADO','LEIDO','FALLIDO')),
  intentos smallint NOT NULL DEFAULT 0, proximo_intento timestamptz, error_codigo varchar(20),
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT mensaje_conversacion_fk FOREIGN KEY (negocio_id, conversacion_id) REFERENCES conversacion (negocio_id, id)
);
CREATE UNIQUE INDEX mensaje_wamid_uk ON mensaje_whatsapp (wamid) WHERE wamid IS NOT NULL;
CREATE INDEX mensaje_salida_cola_ix ON mensaje_whatsapp (proximo_intento) WHERE estado = 'PENDIENTE';

CREATE TABLE operacion_pendiente (                  -- V2-RN-10
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  negocio_id bigint NOT NULL, conversacion_id bigint NOT NULL, cliente_id bigint NOT NULL,
  tipo  varchar(14) NOT NULL CHECK (tipo IN ('CREAR','REPROGRAMAR','CANCELAR')),
  datos jsonb NOT NULL,                              -- {servicioId, barberoId, inicio} | {reservaId, version, …}
  nonce bytea NOT NULL,                              -- va en el id del botón; impide adivinar o reutilizar
  estado varchar(10) NOT NULL DEFAULT 'VIGENTE' CHECK (estado IN ('VIGENTE','EJECUTADA','CADUCADA','RECHAZADA','ANULADA')),
  expira_en timestamptz NOT NULL, resultado jsonb,
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT operacion_conversacion_fk FOREIGN KEY (negocio_id, conversacion_id) REFERENCES conversacion (negocio_id, id),
  CONSTRAINT operacion_cliente_fk      FOREIGN KEY (negocio_id, cliente_id)      REFERENCES cliente (negocio_id, id)
);
CREATE UNIQUE INDEX operacion_vigente_uk ON operacion_pendiente (conversacion_id) WHERE estado = 'VIGENTE';
-- Proponer una operación nueva marca antes como ANULADA la VIGENTE de la conversación (misma transacción).
-- La caducidad se evalúa al usarla (expira_en <= ahora → CADUCADA), sin depender de un trabajo programado.

CREATE TABLE uso_ia (                               -- límites y costes (V2-RN-14)
  negocio_id bigint NOT NULL, dia date NOT NULL, conversacion_id bigint NOT NULL,
  llamadas integer NOT NULL DEFAULT 0, tokens_entrada bigint NOT NULL DEFAULT 0,
  tokens_salida bigint NOT NULL DEFAULT 0, tokens_cache bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (negocio_id, dia, conversacion_id)
);
```

### 3.3 RLS
```sql
CREATE FUNCTION app_negocio_actual() RETURNS bigint LANGUAGE sql STABLE
  AS $$ SELECT nullif(current_setting('app.negocio_id', true), '')::bigint $$;

-- para cada tabla de negocio T:
ALTER TABLE T ENABLE ROW LEVEL SECURITY;
ALTER TABLE T FORCE  ROW LEVEL SECURITY;
CREATE POLICY T_aislamiento ON T
  USING      (negocio_id = app_negocio_actual())
  WITH CHECK (negocio_id = app_negocio_actual());
```
- **Falla cerrado:** sin contexto, `app_negocio_actual()` es `NULL` y la política no devuelve ni admite filas. `nullif` cubre el caso de PostgreSQL en que, tras un `set_config(..., true)` en una transacción anterior de la misma conexión, la variable vuelve como cadena vacía.
- **Roles (V2-RNF-02):** `barberturno_v2` es propietario y ejecuta Flyway (`spring.flyway.user`); `barberturno_v2_app` tiene solo `SELECT/INSERT/UPDATE/DELETE` y uso de secuencias, sin `BYPASSRLS`. El superusuario `postgres` ignora RLS: las pruebas de aislamiento se conectan como el rol de la aplicación.
- **Las restricciones no se filtran por RLS** (`UNIQUE`, `EXCLUDE`, FK): protegen la integridad global. Por eso los únicos que podrían revelar datos de otro negocio (`servicio_nombre_uk`) pasan a incluir `negocio_id`. `usuario_correo_uk` sigue siendo global (cuenta única por persona); el registro ya revelaba en v1 que un correo tiene cuenta.
- **Rendimiento:** índices que empiezan por `negocio_id` donde las consultas filtran por negocio; la función `STABLE` se evalúa una vez por consulta.

### 3.4 Primera fundación sin tocar entidades (V2-01)
En la primera tarea, `negocio_id` se añade **en tres pasos** (columna anulable → `UPDATE` con el id del negocio único → `SET NOT NULL` y `SET DEFAULT app_negocio_actual()`): añadirla directamente como `NOT NULL DEFAULT app_negocio_actual()` fallaría sobre tablas con filas, porque Flyway se ejecuta sin contexto y la función devuelve `NULL` (hallazgo g038-4). Con el `DEFAULT`, las entidades JPA de v1 no cambian (Hibernate no inserta la columna y `ddl-auto=validate` admite columnas no mapeadas), y la política `WITH CHECK` garantiza que nada se escriba fuera del negocio del contexto. Mientras no exista el `ResolutorNegocio` (V2-05), el contexto sale de una propiedad **explícita y transitoria** `barberturno.tenancy.negocio-unico-id` sin valor por defecto: si falta, la aplicación no arranca (nada de un negocio por defecto silencioso, hallazgo de la auditoría 075) y el perfil `prod` la rechaza. Las tareas siguientes mapean `negocioId` en las entidades cuando lo necesitan. Así se valida pronto la decisión más arriesgada (RLS + Hikari + Hibernate) con un cambio pequeño y revisable.

## 4. Autenticación, negocio activo y autorización
1. **Sesión:** igual que v1 (JWT HS256 en cookie `HttpOnly`, CSRF, `token_version`). El JWT **no** lleva el negocio ni el rol del negocio: así no hay que reemitirlo al cambiar de negocio, varias pestañas pueden trabajar con negocios distintos y una revocación de membresía tiene efecto inmediato.
2. **Rutas:** `/api/plataforma/**` (administración de la plataforma), `/api/n/{slug}/**` (todo lo del negocio), `/api/auth/**`, `/api/perfil`, `/api/mis-negocios`, `/api/invitaciones/{token}`, `/api/whatsapp/webhook`. Las rutas de v1 sin prefijo se mantienen solo para el negocio por defecto hasta que el frontend migre (V2-07) y después se retiran.
3. **`ResolutorNegocio`** (filtro después de la autenticación): `slug` → `negocio` (404 si no existe); si está suspendido, solo lectura; con el actor autenticado carga en **una consulta** su membresía activa y su ficha de cliente en ese negocio; construye `ContextoNegocio`. Para las rutas públicas del negocio, `ContextoNegocio` no tiene actor.
4. **`GestorTransaccionesNegocio`:** al comenzar cada transacción gestionada por Spring ejecuta `select set_config('app.negocio_id', ?, true)` con el negocio del contexto (o nada, si no hay contexto). Una prueba con un *pool* de una sola conexión demuestra que el contexto no pasa de una transacción a otra.
5. **Autorización en servicio:** `ReservaAutorizacion` pasa a recibir `ContextoNegocio` (rol del negocio, `clienteId`, `barberoId`) en lugar del `Rol` global. `SecurityConfig` deja de decidir por `ROLE_ADMIN`/`ROLE_BARBERO` en las rutas de negocio y solo exige autenticación o `ADMIN_PLATAFORMA`.
6. **Tareas sin petición** (trabajos, *webhook*): `EjecutorEnNegocio.ejecutar(negocioId, accion)` abre el contexto y la transacción. El negocio sale de datos de la plataforma (el `phone_number_id` del canal, el recorrido de `negocio`), nunca del contenido de un mensaje.

## 5. Concurrencia (protocolo de v1, extendido)
- Orden global **sin cambios**: ⓪ *advisory* → ① ficha del cliente → ② barberos por id ascendente → ③ reserva. Las reservas por WhatsApp llaman a `ReservaService.crear/reprogramar/cancelar`, así que heredan el protocolo, la revalidación tras el bloqueo y las `EXCLUDE`.
- ① pasa de la fila `usuario` a la fila `cliente` (la ficha del negocio): el límite de reservas activas (RN-20) y el solape (RN-04) son por ficha.
- ⓪ pasa a la forma de dos claves `pg_advisory_xact_lock(clase int4, negocio_id int4)`: `141414` cupo de barberos (por negocio) y `313131` último propietario (por negocio). PostgreSQL distingue las claves de una y de dos partes (`pg_locks.objsubid` 1 y 2), así que la clave global `313131` de un solo valor se conserva para el último `ADMIN_PLATAFORMA`. Cada camino toma como máximo un bloqueo ⓪ y siempre antes de ①②③; ninguno se toma tras bloquear filas. (En v1 el cupo también se toma al desactivar, `BarberoService.cambiarEstado`; se conserva.)
- **Un solo negocio por transacción y por contexto de persistencia:** el contexto no cambia a mitad de una transacción (las entidades ya cargadas son de un negocio). Los trabajos abren una transacción por negocio.
- Nuevo: la ejecución de una `operacion_pendiente` toma `FOR UPDATE` sobre su fila **antes** de llamar al servicio de reservas y la marca `EJECUTADA` en la misma transacción: dos pulsaciones del botón (o una pulsación y un reintento del *webhook*) producen una sola reserva. Esa fila no participa en el protocolo ①②③ de ninguna otra operación, así que no forma ciclos (se toma siempre la primera).
- Cola de eventos y outbox: `SELECT … FOR UPDATE SKIP LOCKED LIMIT n` en transacciones cortas, sin bloqueos del dominio.

## 6. WhatsApp
### 6.1 Entrada
1. `GET /api/whatsapp/webhook`: verificación (`hub.mode=subscribe`, `hub.verify_token` comparado en tiempo constante con la variable de entorno, devuelve `hub.challenge`).
2. `POST /api/whatsapp/webhook` (sin sesión ni CSRF: exento solo en esta ruta, con su propia autenticación): calcula HMAC-SHA256 del **cuerpo crudo** con el *app secret* y lo compara en tiempo constante con `X-Hub-Signature-256`; si no coincide → 401 sin efectos. Si coincide, inserta un `evento_whatsapp_entrante` por mensaje o aviso de estado con `ON CONFLICT (clave_idempotencia) DO NOTHING` y responde 200 inmediatamente. Meta reintenta durante días ante cualquier respuesta distinta de 200 y puede duplicar avisos: la idempotencia está en la base, no en memoria.
3. Trabajo `ProcesadorEventos` (`@Scheduled` cada 1–2 s): toma eventos con `SKIP LOCKED`, resuelve el negocio por `phone_number_id` (número no registrado → `DESCARTADO`), abre el contexto del negocio, localiza o crea la ficha y la conversación, y entrega el mensaje al orquestador. Reintentos con espera creciente; tras 5 → `ERROR` y aviso operativo.

### 6.2 Salida
- Toda respuesta se guarda como `mensaje_whatsapp` `SALIENTE`/`PENDIENTE` **en la misma transacción** que el cambio de estado que la origina (outbox). Así, si la reserva se revierte, el mensaje «reservada» no existe.
- `EnviadorMensajes` (`@Scheduled`): envía por la Graph API con el token del canal, guarda el `wamid`, reintenta 429/5xx con espera exponencial y marca `FALLIDO` los errores permanentes (número inválido, ventana cerrada, plantilla rechazada). Un fallo de entrega **no** deshace la operación: genera un aviso interno al personal para que contacte al cliente.
- Avisos de estado (`sent`, `delivered`, `read`, `failed`) actualizan el mensaje por `wamid`.
- Ventana de 24 h: `conversacion.ultima_entrada_en`; fuera de ella solo plantillas (`bt_recordatorio_cita`, `bt_cita_modificada_por_negocio`, `bt_cita_cancelada_por_negocio`, categoría *utility*, en español). Las plantillas se crean en la cuenta de cada negocio al conectar el canal.
- Para reducir coste (desde el 01/10/2026 los mensajes de servicio que superan 1000/mes por número se cobran), el diseño **agrupa** la información en mensajes interactivos (listas y botones) en lugar de varios textos seguidos.

### 6.3 Varios negocios
- Un solo *webhook* de la app de la plataforma para todos los números; el negocio se identifica por `metadata.phone_number_id` (V2-RN-02, V2-RN-07).
- **Fase 1 (desarrollo):** número de prueba de Meta y token de la app en variables de entorno, asociado al negocio de prueba.
- **Fase 3:** la plataforma como **Tech Provider**: el propietario conecta su número con **Embedded Signup v4** desde la configuración del negocio; el servidor intercambia el código por un *business token*, registra el número en la Cloud API, suscribe la app a los *webhooks* de la cuenta del negocio y guarda el token cifrado. Requisitos y límites en `09-costes.md` §5.

## 7. Conversación con IA
### 7.1 Principio y flujo
El modelo **interpreta y redacta**; el servidor **consulta, valida, propone y ejecuta**. Flujo por mensaje entrante:
1. Si es la pulsación de un botón de confirmación → `EjecutorOperaciones` (sin IA): valida nonce, ficha, vigencia y estado; ejecuta el servicio de dominio; compone la respuesta con una **plantilla del servidor** a partir del resultado (V2-RN-11).
2. Si la conversación está en modo `HUMANO` → solo se guarda y se avisa al personal (V2-RN-15).
3. Si se superan los límites o el modelo no está disponible → **menús guiados** (la Fase 1, que se conserva como respaldo permanente).
4. **Ciclo de vida de `conversacion.estado`:** se vacía (`{}`) cuando una operación pasa a `EJECUTADA`, cuando el cliente vuelve al menú principal y cuando pasan 24 h sin mensajes del cliente; así el modelo nunca hereda servicio, profesional o fecha de una interacción anterior.
5. En otro caso → `Orquestador` llama al modelo con: instrucciones fijas del sistema, el catálogo del negocio (solo ese negocio), el estado de la conversación, la fecha y hora actuales en Lima con el día de la semana, y los últimos N mensajes. El modelo puede responder texto o pedir herramientas.

### 7.2 Herramientas (definidas y ejecutadas por el backend, con `strict: true`)
| Herramienta | Tipo | Hace |
|---|---|---|
| `listar_servicios` | lectura | Servicios activos del negocio (id, nombre, duración, precio). |
| `listar_profesionales` | lectura | Profesionales activos (id, nombre). |
| `buscar_disponibilidad(servicioId, barberoId?, desde, hasta, horaDesde?, horaHasta?)` | lectura | Llama a `DisponibilidadService` (máx. 7 días); devuelve franjas con id opaco y fecha completa. |
| `mis_citas` | lectura | Reservas futuras de la ficha, con `permisos` calculados por el servidor. |
| `proponer_reserva(servicioId, barberoId, inicio)` | propuesta | Revalida la franja; crea `operacion_pendiente`; el servidor envía el resumen y los botones. **No reserva.** |
| `proponer_reprogramacion(reservaId, inicio, barberoId?)` / `proponer_cancelacion(reservaId)` | propuesta | Igual, con las reglas de RN-07 y RN-09 comprobadas antes de proponer. |
| `derivar_a_persona(motivo)` | acción segura | Pasa la conversación a modo humano y avisa al personal. |

No existe ninguna herramienta que escriba reservas directamente, que lea datos de otros clientes o de otro negocio, ni que acepte un `negocioId` o `clienteId` como argumento: esos valores salen del contexto del servidor.

### 7.3 Guardas
- **Horarios inventados (V2-RN-12):** antes de enviar el texto del modelo, `GuardasSalida` extrae las horas y fechas que menciona y comprueba que pertenecen a las franjas devueltas por herramientas en ese turno; si no, descarta el texto y envía la lista de franjas compuesta por el servidor. Las franjas se ofrecen además como lista interactiva generada por el servidor.
- **Confirmaciones falsas (V2-RN-11):** el texto del modelo no puede afirmar que algo se reservó, canceló o reprogramó (se rechazan formas como «reservé», «confirmada», «cancelé» fuera de las plantillas). Las confirmaciones solo salen de `EjecutorOperaciones`.
- **Fechas completas:** el resumen de la propuesta lo compone el servidor con `TiempoNegocio` («jueves 23 de octubre de 2026, 17:30–18:00, Corte con Carlos, S/ 25,00»).
- **Ambigüedad:** las instrucciones obligan a preguntar si faltan servicio, profesional o día, o si «mañana», «el viernes» o «después de las 5» admiten varias lecturas; en caso de duda se ofrece una opción concreta para elegir.
- **Inyección de instrucciones:** el texto del cliente es dato; las herramientas solo pueden hacer lo que el cliente podría hacer en la web; los resultados de herramientas no incluyen texto libre de otras personas.
- **Datos mínimos al proveedor (V2-RNF-05):** nombre de pila del cliente, nombres de servicios y profesionales, franjas; nunca teléfonos, correos, `wa_id` ni identificadores internos legibles.
- **Límites (V2-RN-14):** contador en `uso_ia`; al alcanzarlos, menús guiados o persona.
- **Derivación automática:** dos turnos seguidos sin avance, petición explícita («hablar con alguien»), queja o tema fuera de las reservas.

### 7.4 Proveedor de modelo
Puerto `ModeloConversacional` con una implementación falsa programable (pruebas y evaluación determinista) y un adaptador de **Anthropic** con el SDK oficial de Java (V2-DA-12). El modelo concreto se elige con la evaluación (`09-costes.md` §3); la clave va en variable de entorno; el adaptador registra tokens y duración, nunca el contenido.

## 8. Entornos (desarrollo aislado)
| Elemento | v1 (no cambia) | v2 |
|---|---|---|
| Carpeta | `D:\Proyectos\BarberTurno` (rama `main`) | `D:\Proyectos\BarberTurno-v2` (worktree de la rama `v2`) |
| Bases | `barberturno`, `barberturno_test`, copias de CP-11 | `barberturno_v2`, `barberturno_v2_test` (PostgreSQL 18.6, puerto 5433) |
| Roles | `barberturno` | `barberturno_v2` (propietario, Flyway) y `barberturno_v2_app` (aplicación, sin `BYPASSRLS`) |
| Secretos | `.local/barberturno.env` | `D:\Proyectos\BarberTurno-v2\.local\barberturno.env` (lo crea el responsable; mismas claves + `BT_DB_APP_*`, `BT_WA_*`, `BT_IA_*`). Las rutas relativas de DA-17 lo resuelven solas dentro del worktree |
| Puertos | 8080 / 4200 | 8081 / 4201 |
| Producción | Azure `d2f3107` | **Ninguna** hasta la Fase 3 y la autorización del responsable |

Regla operativa: nunca dos suites contra PostgreSQL a la vez (v1 y v2 comparten servidor; se agotan conexiones).
