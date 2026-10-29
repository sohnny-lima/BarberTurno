# BarberTurno v2 · 13 · V2-10 · Conversación guiada sin IA: alcance, contrato y reparto

> Autorizada por el responsable el 29/10/2026. Rama `tarea/V2-10-conversacion-guiada` desde el HEAD verificado de V2-09 (`d4bc401`, cierre documental tras la revisión 116), porque `v2` (`ccdbb29`) no contiene V2-08 ni V2-09. Sin cambios de V2-07 (`3ded886`). Clase **Crítica** (reservas, transacciones, concurrencia, consentimiento y datos personales).
>
> **Reparto (responsable, 29/10/2026): los tres programan.**
> - **Claude** coordina, fija este contrato y revisa la coherencia. Implementa el ciclo de vida de la conversación, el consentimiento y la retención (bloque C).
> - **Codex** implementa la migración V115, las operaciones pendientes con *nonce*, la confirmación y la ejecución de consultas, reservas, reprogramaciones y cancelaciones con los servicios existentes (bloque X).
> - **Gemini** implementa los menús, las respuestas deterministas, la interpretación de selecciones y la validación de entradas (bloque G). También escribe escenarios de prueba, *fixtures* y utilidades.
> - Un implementador por archivo. Nadie aprueba su propio bloque (§11).
>
> Requisitos: V2-RF-10…13 y V2-RF-15 (consentimiento; el *opt-in* de recordatorios pasa a V2-14), V2-RN-09/10/11, V2-CP-05 y V2-CP-07. Arquitectura: `04-arquitectura-datos.md` §3, §5 y §7.1; `06-flujos.md` §6–§8 y §10; decisiones V2-DA-12/13, 53/54, 57/58 y V2-DA-63…68 (nuevas).

## 1. Alcance
**Entra:**
- **Conversación guiada sin IA**, solo con botones y listas generados por el servidor:
  - menú principal: **Reservar** · **Mis citas** (V2-DA-63);
  - reservar: servicio → profesional («Cualquiera» incluido) → día (hoy y los 6 siguientes) → franja (máx. 9 por página y «Ver más») → resumen con **[Confirmar] [Cambiar]** → resultado;
  - mis citas: citas futuras de la ficha → cita → **[Cancelar] [Reprogramar] [Volver]**, según los permisos del servidor → resumen → confirmación → resultado;
  - sin ficha se recorre igual (consulta de servicios, profesionales y franjas); al elegir una franja se pide el consentimiento y, tras aceptarlo, se continúa con esa franja. «Mis citas» sin ficha responde que no hay citas.
- **Consentimiento** antes de crear la ficha (`06-flujos.md` §6): botones **[Aceptar] [No, gracias]**; aceptar crea o enlaza la ficha (`cliente.wa_id`, `privacidad_aceptada_en`) y la conversación (`conversacion.cliente_id`).
- **Operación pendiente** (V2-RN-10, V2-DA-13) con *nonce* de 16 bytes, una vigente por conversación, caducidad de 10 min, confirmación por botón o «sí» escrito con una única vigente; ejecución con `ReservaService`.
- **Ruta de ficha en `ReservaService`** (crear, reprogramar y cancelar como la propia ficha, sin cuenta web), con las mismas reglas y el mismo protocolo ①②③ (V2-DA-64).
- **Ciclo de vida** de `conversacion.estado` (`04-arquitectura-datos.md` §7.1 punto 4) y **retención configurable** (V2-DA-67).
- **V115**: `operacion_pendiente`, permisos de `conversacion`, creador y actor por ficha en `reserva` y `auditoria_reserva`, purga acotada por políticas.

**No entra:**
- *Opt-in* y baja de recordatorios («BAJA», «STOP»): V2-14, con los recordatorios.
- «Hablar con alguien», modo `HUMANO` y bandeja: V2-11. El menú **no** muestra esa opción.
- IA (V2-12), vinculación con la cuenta web (V2-15) y fichas creadas por el personal.
- Guardar el contenido de los mensajes entrantes: no hace falta para la conversación guiada; llega con la bandeja (V2-11) y su retención (P2-06).
- **Textos aprobados** de privacidad y bienvenida (P2-06), **plazos** de retención no decididos y aprobación de plantillas en Meta (P2-05). Ver §10.

## 2. Flujo por mensaje entrante
Todo ocurre en la **transacción de entrega** de V2-08 (`ProcesadorEventosWhatsApp.entregar`), con el contexto del negocio del número receptor. El evento se marca `PROCESADO` en esa misma transacción.
1. `OrquestadorConversacion` (Claude, `Propagation.MANDATORY`) es el **único** `ReceptorMensajesWhatsApp`: `RegistroEntradaWhatsApp` deja de implementarlo y el orquestador lo llama.
2. **Comprueba antes de escribir nada** (`RegistroEntradaWhatsApp.operativo`) que el canal sigue `ACTIVO` y que el negocio no está suspendido y tiene algún propietario activo (como V2-08). Si no, termina sin escritura ni respuesta y el evento queda `PROCESADO` sin efectos. Cierra la ventana entre la resolución y la entrega; la que queda entre esta comprobación y el *commit* la cubre el enviador de V2-09, que no envía para negocios no operativos (consulta 117, punto 9).
3. Registra la entrada (`RegistroEntradaWhatsApp.registrar`): el `INSERT … ON CONFLICT DO UPDATE` **bloquea la conversación** y devuelve, con `RETURNING old.ultima_entrada_en` (PostgreSQL 18), la entrada anterior leída bajo ese bloqueo.
4. **Ciclo de vida:** si la entrada anterior es nula o tiene más de 24 h, vacía `estado` y anula la operación vigente.
5. **Interpreta** el mensaje (`InterpreteEntradas`, Gemini) → `Entrada`.
6. **Enruta** (Claude): confirmación → operaciones (Codex); selección o palabra clave → paso del menú; sin ficha y el paso la exige → consentimiento; no reconocido o tipo no soportado → texto + menú principal.
7. **Responde** con `SalidaWhatsApp` (outbox de V2-09), en la misma transacción. Menús y textos: origen `ASISTENTE`. Resultado de una operación: origen `SISTEMA`, con `reservaId`.

Si algo falla con una excepción técnica, la transacción entera se revierte (conversación, ficha, operación, reserva, auditoría, avisos y outbox) y V2-08 reintenta el evento (5 intentos; después, `ERROR` sin respuesta).

## 3. Contrato de tipos (paquete `pe.barberturno.conversation`)
Los escribe Claude en el commit de contrato, con firmas, Javadoc y cuerpos `UnsupportedOperationException` donde implementa otro. **Nadie cambia una firma del contrato sin pedirlo al coordinador.**

### 3.1 Entradas y selecciones (Gemini)
- `Entrada` (*sealed*): `Seleccion(CodigoSeleccion codigo)`, `Texto(PalabraClave clave)`, `NoSoportada(String tipo)`.
- `PalabraClave`: `MENU`, `SI`, `NO`, `OTRA`. Normalización: recorte, minúsculas, sin tildes ni signos. `MENU` = {`menu`, `hola`, `inicio`, `0`}; `SI` = {`si`, `confirmar`, `confirmo`}; `NO` = {`no`}. Cualquier otro texto → `OTRA`. **Nunca** se interpreta texto libre como fecha, servicio o profesional (eso es V2-12).
- `CodigoSeleccion` (*sealed*, `String codificar()` y `static Optional<CodigoSeleccion> decodificar(String)`). Identificadores de botón y fila, como máximo 200 caracteres:

| Tipo | Formato | Notas |
|---|---|---|
| `Menu(OpcionMenu)` | `m:RESERVAR` · `m:MIS_CITAS` · `m:INICIO` | |
| `Privacidad(boolean acepta)` | `p:S` · `p:N` | |
| `PaginaServicios(int pagina)` | `sp:<pág>` | `pág` de 0 a 9 |
| `Servicio(long servicioId)` | `s:<sid>` | |
| `PaginaProfesionales(long servicioId, int pagina)` | `bp:<sid>:<pág>` | |
| `Profesional(long servicioId, long barberoId)` | `b:<sid>:<bid>` | `bid = 0` = cualquiera |
| `Dia(long servicioId, long barberoId, LocalDate dia, int pagina)` | `d:<sid>:<bid>:<aaaammdd>:<pág>` | |
| `Franja(long servicioId, long barberoId, long inicioEpochSeg)` | `f:<sid>:<bid>:<seg>` | `bid > 0` |
| `Cita(long reservaId)` | `c:<rid>` | |
| `AccionCita(long reservaId, boolean cancelar)` | `a:<rid>:C\|R` | |
| `DiaCambio(long reservaId, LocalDate dia, int pagina)` | `rd:<rid>:<aaaammdd>:<pág>` | mismo profesional y servicio |
| `FranjaCambio(long reservaId, long inicioEpochSeg)` | `rf:<rid>:<seg>` | |
| `Confirmacion(long operacionId, String nonce, boolean confirma)` | `o:<oid>:<nonce>:S\|N` | *nonce* base64url sin relleno, 22 caracteres |

  - Validación estricta: expresión regular completa, ids de 1 a 18 cifras sin ceros a la izquierda y > 0 (salvo `bid = 0` donde se indica), fecha real, página 0–9.
  - Cualquier desviación → `Optional.empty()` → se trata como no reconocido.
  - El identificador **no es de confianza**: solo dice qué se pidió. El servidor revalida todo: negocio por RLS, ficha, estado, reglas y franja.

### 3.2 Menús y textos (Gemini)
- `MenusConversacion` construye el `interactive` de Meta (`type` `button` o `list`) que acepta `SalidaWhatsApp.encolarInteractivo`.
- Límites de Meta, aplicados de forma determinista (recorte con «…»): cuerpo ≤ 1024; título de botón ≤ 20; como máximo 3 botones; texto del botón de la lista ≤ 20; título de fila ≤ 24; descripción ≤ 72; como máximo 10 filas en total.
- `TextosConversacion` contiene los textos del servidor:
  - fecha completa en Lima («jueves 23 de octubre de 2026»), hora «18:00» y precio «S/ 25,00»;
  - traducción de cada `ErrorCodigo` que pueda devolver una operación;
  - resultados compuestos **solo** a partir de `ResultadoOperacion` (V2-RN-11). Si la reserva quedó `PENDIENTE`, dice que la barbería debe confirmarla.
- **Sin textos de privacidad propios:** el consentimiento recibe el texto y el enlace de la configuración (§7).
- Datos de entrada (*records* del contrato): `ServicioOpcion(id, nombre, duracionMin, precio)`, `ProfesionalOpcion(id, nombre)`, `FranjaOpcion(barberoId, profesional, inicio, fin)`, `CitaOpcion(reservaId, codigo, servicio, profesional, inicio, fin, estado, version, puedeCancelar, puedeReprogramar)`, `ResumenOperacion(tipo, servicio, profesional, inicio, fin, duracionMin, precio, Optional<CitaOpcion> antes)`.

### 3.3 Consultas y operaciones (Codex)
- `ConsultasConversacion` (lectura, en la transacción del negocio):
  - `servicios()` y `servicio(id)` (activos); `profesionales(servicioId)`: todos los profesionales activos, porque el modelo no tiene relación servicio-profesional;
  - `franjas(servicioId, OptionalLong barberoId, LocalDate dia)` para reservar (con «Cualquiera», una franja por hora de inicio) y `franjasCambio(fichaId, reservaId, dia)` para reprogramar, con propiedad por ficha, mismo profesional y duración de referencia (DA-21), **sin sesión web** (`UsuarioActual` no existe en la entrega);
  - `citasFuturas(fichaId)` (las 10 próximas) y `cita(fichaId, reservaId)`, con los permisos de una **política compartida** con la web que recibe la propiedad por cuenta o por ficha. No se fabrica un `UsuarioAutenticado` con el id de una ficha. Vacío si la reserva no es de esa ficha.
- `OperacionesConversacion`:
  - `Propuesta proponerCreacion(conversacionId, fichaId, servicioId, barberoId, Instant inicio)`;
  - `proponerReprogramacion(conversacionId, fichaId, reservaId, Instant inicio)` (mismo profesional y servicio);
  - `proponerCancelacion(conversacionId, fichaId, reservaId)`.

  Cada propuesta revalida lo que la web validaría (servicio y profesional activos, franja libre con `validarFranja`, RN-04, RN-20, RN-07/09 con la versión leída). Si pasa, **anula la vigente** de la conversación y crea una nueva `VIGENTE` con `expira_en = ahora + 10 min`. `Propuesta` = `Creada(operacionId, nonce, ResumenOperacion)` | `Rechazada(ErrorCodigo, String)`.
- **Confirmar y anular:**
  - `ResultadoOperacion confirmar(conversacionId, fichaId, operacionId, nonce)`;
  - `ResultadoOperacion confirmarVigente(conversacionId, fichaId)` (el «sí» escrito);
  - `ResultadoOperacion anular(conversacionId, fichaId, operacionId, nonce)`;
  - `boolean anularVigente(conversacionId)`.
- `ResultadoOperacion` (*sealed*): `Ejecutada(tipo, CitaOpcion cita, boolean repetida)`, `Rechazada(ErrorCodigo)`, `NoVigente(MotivoNoVigente)`, `Anulada()`. `Propuesta.Rechazada(ErrorCodigo)`. Los textos salen solo del código (§3.2). `MotivoNoVigente`: `CADUCADA`, `SUSTITUIDA`, `USADA`, `NO_ENCONTRADA`.

### 3.4 Conversación, consentimiento y retención (Claude)
- `RegistroEntradaWhatsApp.registrar(MensajeEntrante)` → `EntradaRegistrada(conversacionId, Optional<Instant> entradaAnterior)` (`recibir` se conserva y delega).
- `ConversacionesWhatsApp`: bloquear, leer el estado y el `cliente_id`, guardar el estado (incrementa `version`) y vaciarlo.
- `ConsentimientoConversacion.aceptar(conversacionId, waId, nombrePerfil)` → `fichaId`. Es idempotente: si ya hay ficha con ese `wa_id` en el negocio, la enlaza. `rechazar(conversacionId)`.
- `RetencionWhatsApp` y `ProgramadorRetencionWhatsApp`, con `ConversacionPropiedades` (`barberturno.whatsapp.conversacion.*`).

## 4. Modelo de datos: V115 (responsable: Codex)
1. **`operacion_pendiente`**, según `04-arquitectura-datos.md` §3, con:
   - `nonce bytea` y `CHECK (octet_length(nonce) = 16)`;
   - `CHECK ((estado IN ('EJECUTADA','RECHAZADA')) = (resultado IS NOT NULL))`;
   - `operacion_vigente_uk` parcial;
   - FK compuestas a `conversacion` y `cliente`;
   - RLS `ENABLE` + `FORCE` con la política del negocio;
   - `REVOKE ALL`, y después `SELECT, INSERT` y `UPDATE (estado, resultado, actualizado_en)`, sin `DELETE`.
2. **`conversacion`:**
   - `GRANT UPDATE (cliente_id, estado, version)`;
   - disparador: `cliente_id` no cambia una vez fijado; al fijarlo, la ficha existe en el mismo negocio con `cliente.wa_id = conversacion.wa_id` (invariante de §3);
   - `GRANT DELETE`, conservando la política permisiva de aislamiento y añadiendo una **restrictiva** `FOR DELETE USING (cliente_id IS NULL)`: un `DELETE` debe cumplir las dos.
3. **`mensaje_whatsapp`:** `GRANT DELETE` con una política **restrictiva** `FOR DELETE`: solo mensajes no `PENDIENTE`, sin `reserva_id` y de una conversación sin ficha.
4. **`reserva`:**
   - **`creada_por`** (nombre real desde V1; `creado_por` es de `bloqueo`) pasa a anulable;
   - `canal varchar(10) NOT NULL DEFAULT 'WEB' CHECK (canal IN ('WEB','WHATSAPP'))`;
   - `CHECK (creada_por IS NOT NULL OR canal = 'WHATSAPP')`. `canal` es el origen de la **creación** y no cambia si después se cancela o reprograma por otro canal.
5. **`auditoria_reserva`:** `actor_id` pasa a anulable; `actor_cliente_id bigint` con FK compuesta a `cliente` y `CHECK (num_nonnulls(actor_id, actor_cliente_id) = 1)`.
6. **`evento_whatsapp_entrante`:** `GRANT UPDATE (cuerpo)`, para vaciar el cuerpo de los eventos terminados (§8).

Las pruebas de esquema existentes que cambian con V115 (permisos de `conversacion` en `WhatsAppSalidaEsquemaPruebas`, que hoy exige `UPDATE` denegado en `cliente_id`, `estado` y `version` y `DELETE` denegado) se ajustan **a la nueva regla, sin debilitarla**: cada permiso concedido o denegado se sigue comprobando de forma exacta.

## 5. Transacciones, bloqueos y errores
- **Orden de bloqueos** (V2-DA-65): conversación (`FOR UPDATE`) → operación pendiente (`FOR UPDATE`) → ⓪ → ① ficha → ② barberos → ③ reserva.
  - La web nunca toma la conversación ni la operación, así que no hay ciclos.
  - Dos mensajes de la misma conversación se serializan en el primer bloqueo.
- **Rechazo de dominio sin revertir la entrega** (V2-DA-66):
  - los métodos de ficha de `ReservaService` **y las lecturas de `DisponibilidadService` que puedan lanzar `NegocioException`** (`consultarFranjas`, `validarFranja`) llevan `noRollbackFor = NegocioException.class` (o devuelven el rechazo como valor). Ninguna `NegocioException` atraviesa un proxy transaccional con la regla por defecto durante la entrega;
  - **toda** `NegocioException` de esos caminos se lanza antes del primer cambio de dominio (puede existir ya la ficha creada por el consentimiento en la misma entrega). La prueba ejecuta la **entrega real hasta el *commit***: evento `PROCESADO`, operación `RECHAZADA`, respuesta en la outbox, y sin reserva, auditoría ni aviso nuevos;
  - el ejecutor marca la operación `RECHAZADA` con `resultado = {codigo}` y responde con el texto de §3.2.
- **Errores técnicos** (p. ej., la `EXCLUDE` como red de seguridad o un fallo de la base) → *rollback* completo; la operación sigue `VIGENTE` y el evento se reintenta. En el reintento, `validarFranja` detecta el conflicto → `RECHAZADA`.
- **Ejecución** (`confirmar`):
  - bloquea la fila;
  - comprueba la conversación y la ficha (si no coinciden → `NO_ENCONTRADA`, sin cambiar nada) y el *nonce* en tiempo constante (`MessageDigest.isEqual`; si no coincide → `NO_ENCONTRADA`);
  - según el estado: `EJECUTADA` → `Ejecutada(repetida = true)` con el resultado guardado, sin otra escritura en reservas; `ANULADA` → `SUSTITUIDA`; `CADUCADA` o `RECHAZADA` → `USADA`; `expira_en ≤ ahora` → marca `CADUCADA` y devuelve `CADUCADA`;
  - si sigue vigente, comprueba que las precondiciones guardadas en `datos` siguen valiendo (precio y duración del servicio, profesional y, al cambiar una cita, su `version`); si no, `RECHAZADA` con `VERSION_DESACTUALIZADA` (hay que proponer de nuevo). Después ejecuta el servicio, marca `EJECUTADA` con la **instantánea** de la cita resultante en `resultado` y vacía el estado de la conversación.
- **Doble pulsación** (dos eventos): el segundo encuentra `EJECUTADA` y repite la respuesta **a partir de la instantánea guardada**, aunque la reserva haya cambiado después (`06-flujos.md` §10), sin otra reserva. Conversación, ficha y *nonce* se comprueban también antes de devolver un resultado ya ejecutado.
- **Reloj:** siempre el `Clock` inyectado.

## 6. Aislamiento y propiedad
- El negocio sale **solo** del `phone_number_id` (V2-08). Las consultas y escrituras van con RLS forzada.
- Una operación de otro negocio es invisible (`NO_ENCONTRADA`). Una reserva de otra ficha → `cita()` vacío → «Esa cita no está disponible».
- `ReservaService` (ruta de ficha) comprueba `reserva.cliente_id = fichaId` **después** del bloqueo ③; si no coincide → 404 `NO_ENCONTRADO`.
- Ningún texto revela si existe una operación, cita o ficha ajena.

## 7. Consentimiento (Claude)
- Sin ficha, el cliente puede recorrer servicios, profesionales, días y franjas. «Mis citas» sin ficha responde que no hay citas.
- **Elegir una franja exige ficha.** El servidor guarda la franja elegida en `estado.pendiente` (su identificador codificado) y envía el consentimiento: texto y enlace de `barberturno.whatsapp.conversacion.privacidad.{texto,enlace}`.
- **[Aceptar]**:
  - crea la ficha (`nombre` = nombre del perfil, recortado a 100, o «Cliente de WhatsApp»; `wa_id`; `privacidad_aceptada_en = ahora`; sin teléfono) o enlaza la existente con ese `wa_id`;
  - fija `conversacion.cliente_id` y continúa con la franja pendiente: la vuelve a validar y propone la operación.
- **[No, gracias]** → no crea nada; ofrece la consulta.
- **Textos:** solo `application-dev.yml` y `application-test.yml` traen un **borrador** marcado «BORRADOR — pendiente de P2-06». El perfil `prod` no trae valor por defecto y la aplicación **no arranca** sin él (como `negocio-unico-id`, V2-DA-68).

## 8. Ciclo de vida y retención (Claude)
- **`conversacion.estado`:**
  - JSON con `paso` (informativo) y, mientras se espera el consentimiento, `pendiente` (identificador de la franja elegida);
  - se vacía al ejecutarse una operación, al volver al menú principal (que también anula la vigente) y con más de 24 h sin entradas;
  - nunca guarda servicio, profesional ni fecha: viajan en el identificador y se revalidan.
- **Retención** (V2-DA-67), trabajo programado por negocio con `EjecutorEnNegocio` y lotes acotados:
  1. **Conversaciones sin ficha:** con `coalesce(ultima_entrada_en, creado_en)` anterior a `retencion.conversaciones-sin-ficha` (**30 días**, decidido en `04-arquitectura-datos.md` §3 y `06-flujos.md` §6) y sin mensajes `PENDIENTE` → se borran sus mensajes y la conversación. Las políticas restrictivas de V115 impiden borrar otra cosa.
  2. **Cuerpo de los eventos entrantes terminados** (`PROCESADO`, `DESCARTADO`, `ERROR`): se sustituye por `{}`, conservando el resto de la fila para la idempotencia. **Sin plazo por defecto** (`retencion.cuerpo-eventos` vacío = desactivado) hasta P2-06.
  3. **Contenido de los mensajes y conversaciones con ficha:** sin mecanismo en V2-10 (V2-11 y P2-06).

## 9. Criterios de aceptación
| # | Criterio | Responsable de la prueba |
|---|---|---|
| CJ-1 | Menús y listas dentro de los límites de Meta, deterministas, sin «Hablar con alguien»; textos con fecha completa en Lima, hora y precio | Gemini |
| CJ-2 | Interpretación: todos los formatos de §3.1 se codifican y decodifican de ida y vuelta; cualquier desviación → no reconocido; palabras clave exactas | Gemini |
| CJ-3 | Sin ficha: consulta posible y sin escrituras en `cliente`; reservar pide consentimiento; aceptar crea la ficha (o enlaza la del mismo `wa_id`) y continúa; rechazar no crea nada; el disparador impide cambiar `cliente_id` o enlazar otra `wa_id` | Claude |
| CJ-4 | Reserva de principio a fin por botón (V2-CP-07): una reserva `canal = WHATSAPP`, `creada_por` nula, auditoría con `actor_cliente_id`, respuesta de resultado con el código BT, estado `CONFIRMADA` o `PENDIENTE` según el negocio | Codex |
| CJ-5 | Confirmaciones: dos pulsaciones → una reserva y la misma respuesta; caducada → no reserva; de otra conversación o ficha, con *nonce* incorrecto o de otro negocio → `NO_ENCONTRADA` sin cambios; propuesta sustituida → `SUSTITUIDA`; «sí» con una vigente → ejecuta; «sí» sin vigente → menú | Codex |
| CJ-6 | Disponibilidad cambiada entre propuesta y confirmación → `RECHAZADA` con texto, sin reserva, auditoría ni aviso, y con la respuesta en el outbox; 10 reservas simultáneas mezclando web y WhatsApp → 1 éxito (V2-CP-05) | Codex |
| CJ-7 | Reprogramar y cancelar como en la web (RN-07/09; versión; la cita original intacta si falla); cita de otra ficha o negocio → no disponible | Codex |
| CJ-8 | *Rollback* coherente: un fallo técnico tras ejecutar la reserva revierte operación, reserva, auditoría, avisos, ficha y outbox; el evento se reintenta | Codex (ejecutor) y Claude (orquestador) |
| CJ-9 | Negocio suspendido o canal desconectado entre la resolución y la entrega → ninguna respuesta ni escritura | Claude |
| CJ-10 | Retención: borra solo conversaciones sin ficha vencidas y sin pendientes; las políticas impiden lo demás aunque se intente; vaciado de cuerpos solo si está configurado; por negocio sin cruzar datos | Claude |
| CJ-11 | V115: permisos exactos, RLS forzada, `CHECK`, disparador, políticas restrictivas, `ActualizacionV1IT` e `InstalacionNuevaIT` en verde | Codex |
| CJ-12 | Aislamiento: dos negocios con el mismo `wa_id`; ninguna consulta, cita, operación o ficha cruza | Gemini (escenarios) |
| CJ-13 | `verify` completo en verde, con JaCoCo y Javadoc sin avisos; registros sin `wa_id`, texto ni *nonce* | Claude |

## 10. Pendientes del responsable y propuestas (no aplicadas)
- **P2-06 · texto de privacidad (propuesta, borrador):** «Hola, soy el asistente de reservas de {negocio}. Para reservar guardaremos tu nombre de WhatsApp y tu número, y los usaremos solo para gestionar tus citas con {negocio}. Puedes leer el aviso de privacidad en {enlace}. ¿Aceptas?». Pendiente de revisión por el responsable (y el docente o asesor). Nombra el responsable y el encargado del tratamiento y la transferencia a Meta.
- **P2-06 · plazos (propuesta, no aplicada):**
  - cuerpo de los eventos terminados: 30 días (solo sirve para diagnosticar);
  - contenido de los mensajes de conversaciones con ficha: 12 meses o hasta que se pida la supresión;
  - las conversaciones sin ficha siguen con 30 días.
- **P2-05:** conexión real con Meta no acreditada. Todo se prueba con el servidor Graph falso y eventos firmados con un secreto ficticio.

## 11. Reparto, ramas y revisión cruzada
| Bloque | Responsable | Worktree y rama | Archivos (alcance) | Revisa |
|---|---|---|---|---|
| 0 · Contrato | Claude | `D:\Proyectos\BarberTurno-v2-10` · `tarea/V2-10-conversacion-guiada` | este documento, decisiones, *backlog*, tipos de §3 | Codex (consulta de diseño, solo lectura) |
| X · Operaciones y V115 | Codex | `D:\Proyectos\BarberTurno-v2-10-operaciones` · `tarea/V2-10-conversacion-guiada-operaciones` | `V115__conversacion_guiada.sql`; `conversation/{OperacionPendiente*,OperacionesConversacionService,ConsultasConversacionService}.java`; `reservations/ReservaService.java` (ruta de ficha) y los consumidores de `creada_por`/`actor_id`, `ProcesadorEventosWhatsApp` (id escalar de la ficha), `DisponibilidadService` (ruta por ficha y `noRollbackFor`), `AuditoriaRepository`/`AuditoriaDto`, `ReservaDto` (`cliente.fichaId`); sus pruebas | Gemini Argon (*diff-first*) + Claude |
| G · Menús e interpretación | Gemini | `D:\Proyectos\BarberTurno-v2-10-menus` · `tarea/V2-10-conversacion-guiada-menus` | `conversation/{CodigoSeleccion,InterpreteEntradas,PalabraClave,MenusConversacion,TextosConversacion}.java`; sus pruebas unitarias; después, escenarios, *fixtures* y utilidades | Codex (solo lectura) + Claude |
| C · Ciclo de vida, consentimiento y retención | Claude | rama de la tarea | `conversation/{OrquestadorConversacion,ConversacionesWhatsApp,ConsentimientoConversacion,RetencionWhatsApp,ProgramadorRetencionWhatsApp}.java`; `whatsapp/RegistroEntradaWhatsApp.java`; sus pruebas | Codex (solo lectura) + Gemini Argon |

- **Migraciones:** solo Codex (V115). Si otro bloque necesita un cambio de esquema, lo pide al coordinador.
- **Contrato:** solo Claude.
- **Configuración compartida** (`application*.yml`): solo Claude.
- **Ajuste de `WhatsAppSalidaEsquemaPruebas`:** Gemini (es suyo desde V2-09), cuando V115 esté unida.
- **Pruebas con base:** siempre con el ejecutor serializado. Gemini no puede ejecutar órdenes: Claude compila y ejecuta sus pruebas y registra por separado el autor y el ejecutor.

## 12. Consulta de diseño de Codex (encargo 117, 29/10/2026): «Con propuestas», 10, aceptadas
| # | Propuesta | Decisión |
|---|---|---|
| 1 | La columna es `reserva.creada_por` | Corregido en §4 y V2-DA-64 |
| 2 | Inventario de consumidores de creador, actor y cuenta del cliente; `ReservaDto.cliente.id` es id de **cuenta** (V2-DA-29) | **V2-DA-69**: `cliente.id` anulable (sigue siendo la cuenta) y `cliente.fichaId` nuevo. El historial (`AuditoriaRepository` con `left join fetch`, `AuditoriaDto.actorNombre`) admite el actor de ficha. Al cliente sin cuenta no se le crea aviso interno (también en `transicionar` de la web). Reportes, Excel, avisos de fallo y datos de demostración no cambian, pero se añade una regresión con reserva sin cuenta. El tipo del *frontend* (`reservas.ts`) se ajusta **al integrar con V2-07** (V2-10 no toca el *frontend*) |
| 3 | `DisponibilidadService` también puede marcar *rollback-only* | §5: `noRollbackFor` (o rechazo como valor) también en sus lecturas; la prueba confirma la entrega real |
| 4 | Reprogramar necesita disponibilidad sin sesión web; los permisos de acción viven en `ReservaDto.desde` | `ConsultasConversacion.franjasCambio(fichaId, reservaId, dia)` y política de permisos compartida, por cuenta o por ficha |
| 5 | Precondiciones y resultado reproducible | `datos` guarda servicio, profesional, inicio, duración, precio y versión. Al confirmar se comparan y, si cambiaron, `RECHAZADA`/`VERSION_DESACTUALIZADA`. `resultado` guarda la instantánea de la cita. **Limitación aceptada:** un «sí» escrito confirma la única vigente cuando se procesa, aunque el cliente pensara en un resumen anterior ya sustituido (V2-RN-10 lo admite: hay un único resumen vigente, el último enviado) |
| 6 | La entrada anterior, bajo el bloqueo | Ya es así: `RETURNING old.*` del propio `ON CONFLICT DO UPDATE` (§2.3). Orden completo: evento → conversación (UPSERT) → operación → ⓪①②③ |
| 7 | La ficha que carga V2-08 puede quedar obsoleta en el contexto de persistencia | El procesador localiza solo el **id** de la ficha; la ruta de ficha la refresca tras bloquearla ①. Prueba de carrera: desactivación de la ficha frente a una creación por WhatsApp |
| 8 | Las políticas restrictivas son válidas si se conserva la permisiva; hay más pruebas afectadas | Conservada (§4). La retención bloquea, vuelve a comprobar ficha, antigüedad y pendientes, y después borra hijos y padre. Se ajustan, sin debilitarlos, `WhatsAppSalidaEsquemaPruebas` (V2-09) **y** `WhatsAppEsquemaPruebas` (V2-08: cuerpo actualizable) |
| 9 | Comprobar la operatividad antes de registrar | §2: la comprobación va antes de cualquier escritura; el evento queda `PROCESADO` sin efectos |
| 10 | Firmas, límites y documentos | Firmas de Java como referencia (§3.3). Límites explícitos: servicios y profesionales hasta 90 por paginación (10 páginas de 9); franjas de «Cualquiera» una por hora; «Mis citas» muestra las 10 próximas. Precisiones en `04-arquitectura-datos.md` §3 (estado) y `06-flujos.md` §6–§7. La purga de cuerpos de eventos es de **plataforma** (sin RLS ni contexto; incluye los descartados con `negocio_id` nulo) |
