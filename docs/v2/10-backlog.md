# BarberTurno v2 · 10 · Backlog, MVP recomendado y primera tarea

> Estado: propuesta · 21/10/2026. Orden de ejecución = orden de la tabla. Clase según `CLAUDE.md` (Simple / Normal / Crítica: las críticas pasan revisión de Gemini *diff-first*). Esfuerzo relativo: **S** ≈ un encargo de Codex; **M** ≈ uno o dos encargos con correcciones; **L** ≈ dos o tres encargos o subtareas. Un único implementador activo por tarea (Codex), Claude revisa y aprueba.

## 1. Backlog
| ID | Tarea | Fase | Depende de | Clase | Esf. | Requisitos y CP |
|---|---|---|---|---|---|---|
| V2-00 | Especificación de v2 (este conjunto de documentos) | — | — | — | — | Todos · *Claude, en revisión* |
| **V2-01** | **Fundación de aislamiento**: entorno v2, `V100`, `negocio_id`, FK compuestas, RLS `FORCE`, dos roles, contexto transaccional transitorio | 0 | V2-00 aprobada, P2-01, P2-09 | Crítica | M | V2-RN-01, V2-RNF-01/02, V2-CP-01 (nivel base) |
| V2-02 | **Identidad y membresías** (`V101`): `rol_plataforma`, `membresia`, revalidación de la membresía en cada petición, `ReservaAutorizacion` y `ReservaDto.permisos` con el rol del negocio, `AdminInicialRunner` → `ADMIN_PLATAFORMA`; comportamiento idéntico en el negocio único | 0 | V2-01 | Crítica | L | V2-RN-03/05, R2-06 |
| V2-03 | **Ficha de cliente** (`V102`): `cliente` con el mismo id, FK de `reserva`, bloqueo ① en la ficha, RN-04 y RN-20 por ficha, reserva asistida sobre fichas, registro público → cuenta + ficha | 0 | V2-02 | Crítica | L | V2-RN-08, V2-CP-02, V2-CP-05 (sin WhatsApp) |
| V2-04 | **Reglas por negocio**: parámetros de reserva en `negocio`, cupo de barberos y *advisory locks* por negocio, `barbero.nombre_visible`, V2-RN-16 (desactivar perfil sin desactivar la cuenta, restablecimientos acotados) | 0 | V2-03 | Crítica | M | RN-19, V2-RN-06 (bloqueo), V2-RN-16 |
| V2-05 | **Rutas por negocio**: `/api/n/{slug}/…`, `ResolutorNegocio` (retira la propiedad transitoria), 404 entre negocios, filtros explícitos en las consultas **G** de la auditoría, página pública, `GET /api/mis-negocios`; `AislamientoIT` completo a nivel API | 0 | V2-04 | Crítica | L | V2-RN-02, V2-RF-06/07, V2-CP-01 |
| V2-06 | **Plataforma y equipo**: alta, listado y suspensión de negocios; invitaciones (crear, consultar, aceptar, revocar); roles, revocación, salida y último propietario; restablecimientos con V2-RN-16 | 0 | V2-05 | Crítica | L | V2-RF-01…05/08, V2-CP-03/04 |
| V2-07 | **Frontend multinegocio**: rutas `/n/{slug}`, selector, limpieza de estado al cambiar de negocio, menús por rol del negocio, pantallas de plataforma, equipo e invitaciones; retirar las rutas de API antiguas | 0 | V2-06 | Normal | L | V2-RF-06/07; E2E de dos negocios |
| V2-08 | **WhatsApp · entrada**: `canal_whatsapp` (alta manual con número de prueba), verificación y firma del *webhook*, `evento_whatsapp_entrante` idempotente, procesador con `SKIP LOCKED`, negocio por `phone_number_id` y ficha por `wa_id` | 1 | V2-05; P2-05 (app de Meta y número de prueba) | Crítica | M | V2-RN-02/07/09, V2-RNF-03, V2-CP-06 |
| V2-09 | **WhatsApp · salida**: outbox transaccional, enviador con reintentos, estados por `wamid`, ventana de 24 h, plantillas *utility*, aviso interno ante fallo permanente | 1 | V2-08 | Normal | M | V2-RN-13, §10 de `06-flujos.md` |
| V2-10 | **Conversación guiada (sin IA)**: menús, consentimiento y privacidad, `operacion_pendiente` con *nonce*, reservar, mis citas, cancelar y reprogramar con confirmación explícita | 1 | V2-09; P2-06 (texto de privacidad) | Crítica | L | V2-RF-10…13/15, V2-RN-10/11, V2-CP-05/07 |
| V2-11 | **Derivación a una persona y bandeja web mínima** | 1 | V2-10, V2-07 | Normal | M | V2-RF-14, V2-RN-15, V2-CP-09 |
| V2-12 | **Conversación con IA**: puerto `ModeloConversacional`, adaptador falso y de Anthropic, herramientas de lectura y propuesta, guardas de salida, límites y `uso_ia` | 2 | V2-10; P2-03 (clave del proveedor) | Crítica | L | V2-RF-16, V2-RN-11/12/14, V2-RNF-05 |
| V2-13 | **Evaluación de conversaciones**: suite determinista en `verify`, arnés en vivo, conjunto de 120 conversaciones revisado por el responsable, informe comparativo de modelos | 2 | V2-12; presupuesto (≤ 10 USD) | Normal | M | V2-CP-08 |
| V2-14 | Recordatorios por plantilla *utility* con opt-in y baja (trabajo programado por negocio) | 3 | V2-09, V2-10 | Normal | M | V2-RF-15/17, V2-RN-13 |
| V2-15 | Vinculación WhatsApp ↔ cuenta web y recuperación por enlace de un solo uso | 3 | V2-10, V2-06 | Crítica | M | V2-RF-18, V2-RN-09 |
| V2-16 | Incorporación de números de otros negocios: Embedded Signup v4, intercambio de token, registro, suscripción de *webhooks*, cifrado del token | 3 | V2-09; P2-05 (Tech Provider, App Review) | Crítica | L | V2-RF-09/19, V2-RNF-04 |
| V2-17 | *Staging* de v2 y ensayo de migración sobre una copia de v1 (sin corte) | 3 | V2-07; P2-08 | Crítica | M | V2-CP-10, `08-pruebas…` §6–§7 |

Criterios comunes a todas (Definición de Hecho de v2): la de v1 (`docs/tareas.md`) + prueba de aislamiento con dos negocios para todo lo nuevo + CSRF ausente/incorrecto y rol no autorizado en cada escritura + Javadoc útil sin avisos de `doclint` + nada en `main`, en Azure ni en las bases de v1 + evidencia en `docs/v2/pruebas/v2-NN.md`.

## 2. Recomendación de MVP
**MVP = V2-01 … V2-13 (fases 0, 1 y 2), demostrable en local con dos barberías ficticias y el número de prueba de Meta.** Al terminarlo:
- dos negocios operan a la vez, con su equipo, sus clientes y su página pública, sin verse entre sí (V2-CP-01…05);
- un cliente reserva, consulta, reprograma y cancela por WhatsApp con confirmación explícita, por menús o en lenguaje natural, con las mismas reglas y el mismo protocolo de concurrencia que la web (V2-CP-05…09);
- la evaluación demuestra 0 horarios inventados, 0 confirmaciones falsas y 0 filtraciones, y da al responsable el dato para elegir el modelo (V2-CP-08).

El **piloto** (V2-14 … V2-17) empieza solo con P2-05 (Meta), P2-06 (privacidad), P2-07 (barberías) y P2-08 (alojamiento) resueltas. Orden de recorte si hiciera falta: V2-11 se puede sustituir por la coexistencia con la app WhatsApp Business en el piloto; las pantallas de plataforma de V2-07 pueden limitarse al alta de negocios.

Por qué este orden: el riesgo técnico mayor (aislamiento con RLS sobre el código de v1) se valida en la primera tarea con un cambio pequeño; WhatsApp llega **antes** que la IA para que la integración con Meta y las confirmaciones deterministas existan y estén probadas cuando se añada el modelo, que solo rellena los mismos datos del mismo flujo; los menús guiados quedan como respaldo permanente.

## 3. Primera tarea implementable: V2-01 · Fundación de aislamiento
**Objetivo:** demostrar que `negocio_id` + FK compuestas + RLS `FORCE` con contexto por transacción funcionan con Hikari, Hibernate 7.4 y el protocolo de v1, **sin cambiar el comportamiento ni los contratos** (un solo negocio).

**Prerrequisitos (antes de lanzar a Codex):**
1. El responsable aprueba esta especificación, la rama `v2` y levantar el freeze solo en `v2` (P2-01, P2-09).
2. Claude crea la rama `v2` desde `tarea/V2-00-especificacion` y el worktree `D:\Proyectos\BarberTurno-v2` (si no existe ya).
3. Claude prepara `tools/db-local-v2.sql` (roles `barberturno_v2` y `barberturno_v2_app`, bases `barberturno_v2` y `barberturno_v2_test`, permisos; idempotente, contraseñas con `\password`) y `tools/barberturno-v2.env.example`; **el responsable** los ejecuta con el cliente 18 y crea `D:\Proyectos\BarberTurno-v2\.local\barberturno.env`.

**Alcance (rama `tarea/V2-01-fundacion-aislamiento` desde `v2`):**
1. Configuración: la aplicación se conecta con `barberturno_v2_app`; Flyway con `barberturno_v2` (`spring.flyway.user/password`, variables nuevas `BT_FLYWAY_USER`/`BT_FLYWAY_PASSWORD`); `dev` y `test` apuntan a `barberturno_v2` y `barberturno_v2_test`; puerto 8081 en `dev`; `.github/workflows/ci.yml` crea los dos roles en el servicio `postgres:18`. `connection-init-sql` (`lock_timeout`) se conserva.
2. Migración `V100__negocio_y_rls.sql`: tabla `negocio` (DDL de `04-arquitectura-datos.md` §3.2) y el negocio único (`slug` por *placeholder*); función `app_negocio_actual()`; `negocio_id NOT NULL DEFAULT app_negocio_actual()` con relleno en `servicio`, `barbero`, `jornada`, `bloqueo`, `reserva`, `auditoria_reserva` y `notificacion`; `UNIQUE (negocio_id, id)` en `servicio`, `barbero` y `reserva`; FK compuestas jornada/bloqueo/reserva → barbero, reserva → servicio, auditoría/aviso → reserva; `servicio_nombre_uk` por `(negocio_id, lower(nombre))` **conservando su nombre** (lo traduce `ManejadorErrores`); RLS `ENABLE` + `FORCE` y política de lectura y escritura en las siete; permisos mínimos del rol de la aplicación. `usuario` sin RLS en esta tarea. `reserva.cliente_id` sigue apuntando a `usuario` (cambia en V2-03).
3. Java, paquete `pe.barberturno.common.tenancy`: proveedor del negocio actual con la implementación **transitoria** basada en `barberturno.tenancy.negocio-unico-id` (obligatoria, sin valor por defecto; definida solo en `application-dev.yml` y `application-test.yml`; el perfil `prod` falla al arrancar si existe o si falta un resolutor); mecanismo que ejecuta `set_config('app.negocio_id', ?, true)` al comenzar **cada** transacción de Spring (Codex verifica y documenta el punto de extensión correcto en Spring 7 / Hibernate 7.4). **Ninguna entidad JPA, DTO, controlador ni contrato cambia.**
4. Pruebas nuevas: `EsquemaV2IT` (políticas y `FORCE` en las siete tablas; rol de la aplicación sin propiedad, sin `BYPASSRLS` y sin superusuario; FK compuestas; unicidad del nombre por negocio), `ContextoNegocioIT` (*pool* de 1 conexión: con contexto ve filas; JDBC sin contexto ve 0 e `INSERT` falla; el contexto no sobrevive al `COMMIT` ni al `ROLLBACK`; `WITH CHECK` rechaza otro negocio), `AislamientoBaseIT` (negocio B creado con el rol propietario y datos homónimos: con el contexto de A no se ve nada de B en ninguna de las siete tablas).
5. Adaptación de las pruebas existentes solo donde el esquema cambie (`EsquemaIT` y `DatosDemoRunnerIT` cuentan 8 tablas; `LimpiezaBaseDatos` no debe borrar el negocio único o lo recrea), **sin debilitar aserciones**; lista de cada prueba tocada y por qué.
6. Documentación: sección de arranque de v2 en el README de la rama, evidencia `docs/v2/pruebas/v2-01.md`, estado y notas de cierre en `docs/v2/10-backlog.md`.

**Criterios de aceptación:**
| # | Criterio |
|---|---|
| CA-1 | `mvnw.cmd verify` en `barberturno_v2_test` con el rol de la aplicación: todas las pruebas de v1 (1578) y las nuevas en verde, JaCoCo ≥ 70 %, Javadoc sin avisos. |
| CA-2 | `ConcurrenciaReservaIT` (incluidas las 20 repeticiones) en verde: el protocolo ①②③ y las `EXCLUDE` no cambian. |
| CA-3 | Sin contexto: 0 filas en las siete tablas e `INSERT` rechazado; el contexto no pasa de una transacción a la siguiente en la misma conexión. |
| CA-4 | Una fila de otro negocio no se puede leer, insertar ni actualizar con el contexto del negocio único; una reserva con servicio y barbero de negocios distintos viola la FK compuesta. |
| CA-5 | El mismo nombre de servicio es válido en dos negocios y repetido en el mismo da 409 `NOMBRE_DUPLICADO`, como en v1. |
| CA-6 | La aplicación no arranca sin `barberturno.tenancy.negocio-unico-id`, ni con el perfil `prod`. |
| CA-7 | El *diff* de `backend/src/main/java` se limita a `common/tenancy` y configuración; ningún contrato de API (arquitectura v1 §6) ni entidad cambia. |
| CA-8 | Nada en `main`, en Azure ni en las bases de v1; sin push; merge `--no-ff` en `v2` solo tras la revisión de Claude y la de Gemini. |

**Revisión:** Claude (criterios, RLS con el rol real, *diff* de pruebas modificadas, repetición de `ContextoNegocioIT` y `ConcurrenciaReservaIT`) → Gemini *diff-first* (políticas, roles, contexto y *pool*, FK, migración) → decisión.

## 4. Estado
| ID | Estado | Fecha | Commit | Notas |
|---|---|---|---|---|
| V2-00 | En revisión del responsable | 21/10/2026 | (este commit) | Auditoría Codex 075; contraste Gemini registrado en `00-README.md` §4 |
| V2-01 … V2-17 | Pendiente | — | — | — |
