# BarberTurno v2 · 16 · V2-11 · Derivación a una persona, bandeja web y mecanismos de privacidad y retención

> Autorizada por el responsable el 04/11/2026 (cierre técnico del MVP). Rama `tarea/V2-11-derivacion-bandeja` desde `ddd3fce` (rama conjunta V2-07…V2-10, que contiene V2-07 y V2-10, las dos dependencias de V2-11). Se une en la rama local de revisión `tarea/V2-MVP-revision`. **Sin integrar en `v2` ni publicar.**
>
> Clase: **Crítica** para la migración, la retención, la anonimización y los permisos; **Normal** para la bandeja y su pantalla.
>
> Requisitos: V2-RF-14, V2-RN-15, V2-CP-09; P2-06 (mecanismos técnicos: documento 15 §2). Arquitectura: `04-arquitectura-datos.md` §3 y §7; `05-roles-permisos.md` (bandeja: ENCARGADO y PROPIETARIO); `06-flujos.md` §9. Decisiones nuevas V2-DA-71…76.
>
> **Privacidad:** nada de este documento aprueba P2-06. Los plazos propuestos del documento 15 se usan **solo** como configuración de `dev` y `test`, marcada «BORRADOR P2-06». En la configuración base (la de `prod`) las purgas nuevas quedan **desactivadas**. La decisión D5 (anonimizar o borrar) y la D6 (guardar los entrantes) siguen pendientes: se implementa la opción propuesta, la más conservadora, sin darla por aprobada.

## 1. Alcance
**Entra:**
1. **Derivación** (`06-flujos.md` §9):
   - disparadores de V2-11: el botón «Hablar con alguien» del menú principal (`m:PERSONA`), la palabra clave `PERSONA` y **dos textos seguidos no reconocidos** (sin progreso);
   - `LIMITE` y `HERRAMIENTA` quedan reservados para V2-12 (el `CHECK` ya los admite).
2. **Modo humano** (V2-RN-15): el asistente no responde; solo se guarda el mensaje. Única salida automática: el acuse de la derivación y los mensajes de sistema de §5.
3. **Bandeja web** `/n/{slug}/whatsapp` para ENCARGADO y PROPIETARIO: lista de conversaciones en modo humano, historial, respuesta de texto libre dentro de la ventana de 24 h y «Devolver al asistente».
4. **Retorno automático** al asistente tras 24 h sin actividad del personal.
5. **Avisos internos** de derivación (`notificacion` sin reserva).
6. **Mecanismos de P2-06** (documento 15 §2):
   - vaciar el contenido de los mensajes de conversaciones con ficha;
   - purgar las operaciones pendientes terminadas;
   - anonimizar una ficha de WhatsApp a petición (opción propuesta de D5);
   - guardar los mensajes entrantes solo cuando hacen falta (opción mínima de D6, §4).

**No entra:** IA (V2-12), coexistencia con la app WhatsApp Business, plantillas fuera de la ventana para responder (el personal no puede escribir fuera de la ventana), horario de atención del equipo, adjuntos, edición o borrado de mensajes enviados, una pantalla de fichas (la anonimización es solo API en V2-11).

## 2. Flujo por mensaje entrante (cambios sobre V2-10, `13-…` §2)
Tras registrar la entrada (paso 3) y aplicar el ciclo de vida (paso 4):
1. **Modo `HUMANO`:** guarda el entrante (§4), no interpreta ni responde. Fin. *(Una pulsación de un botón antiguo también se guarda y no se ejecuta: la derivación anuló la operación vigente.)*
2. **Modo `ASISTENTE`:**
   - si la configuración es `TODOS`, guarda el entrante (§4);
   - interpreta. `m:PERSONA` o `PalabraClave.PERSONA` → **pedir persona**;
   - texto no reconocido (`OTRA`) → `estado.fallos += 1`; si llega a **2** → **pedir persona** con motivo `SIN_PROGRESO`; si no, el texto y el menú de V2-10;
   - cualquier entrada reconocida pone `fallos` a 0 (el estado se guarda con el paso como hoy).
3. **Pedir persona:**
   - **con ficha** → `DerivacionConversacion.derivar(conversacionId, motivo)`;
   - **sin ficha** → consentimiento con `estado.pendiente = "m:PERSONA"` (y el motivo en `estado.motivo`); [Aceptar] crea o enlaza la ficha y deriva; [No, gracias] → texto «Sin aceptar no podemos pasarte con el equipo» + menú principal. El personal no ve conversaciones sin ficha (V2-DA-72).
4. La derivación guarda **también** el entrante que la provocó (es el primer mensaje que verá el personal), **una sola vez** aunque la configuración sea `TODOS` (125-14).
5. **Sin ficha**, el texto previo al consentimiento no se conserva (no hay base para guardarlo): el primer mensaje visible es la aceptación, y el motivo de la derivación se muestra en la bandeja.
6. El contador `fallos` sobrevive al menú que se muestra tras el primer texto no reconocido (el menú vacía el resto del estado).
7. El modo `HUMANO` se comprueba **antes** que cualquier confirmación (corrige el orden de `04-arquitectura-datos.md` §7.1).

## 3. Contrato de tipos
Los escribe Claude en el commit de contrato, con Javadoc y cuerpos `UnsupportedOperationException` donde implementa otro. **Nadie cambia una firma sin pedirlo al coordinador.**

### 3.1 Conversación (`pe.barberturno.conversation`)
- `OpcionMenu.PERSONA` (código `m:PERSONA`, título del botón «Hablar con alguien»).
- `PalabraClave.PERSONA`: frases exactas tras la normalización de V2-10: `hablar con alguien`, `hablar con una persona`, `persona`, `humano`, `asesor`, `agente`, `ayuda`. Ninguna otra frase (sin búsqueda de subcadenas).
- `MotivoDerivacion`: `PETICION`, `SIN_PROGRESO`, `LIMITE`, `HERRAMIENTA`.
- `DerivacionConversacion` (interfaz; implementación de Codex). Las dos primeras exigen la transacción y el bloqueo de la conversación del llamante (`Propagation.MANDATORY`):
  - `boolean derivar(long conversacionId, MotivoDerivacion motivo)`: si ya está en `HUMANO`, `false` sin cambios. Si no: `modo = HUMANO`, `derivada_en = ahora`, `motivo_derivacion`, `ultima_respuesta_personal_en = null`; anula la operación vigente; vacía el estado; encola el acuse (`TextosConversacion.derivada(nombreNegocio)`, origen `SISTEMA`); crea un aviso para cada PROPIETARIO y ENCARGADO **activo** del negocio. `true`.
  - `boolean devolver(long conversacionId, OptionalLong usuarioId)`: si no está en `HUMANO`, `false` sin cambios. Si no: `modo = ASISTENTE`, `derivada_en`, `motivo_derivacion` y `ultima_respuesta_personal_en` a `null`, estado vacío; **si la ventana está abierta**, encola `TextosConversacion.devuelta()` y el menú principal (origen `SISTEMA`). `usuarioId` vacío = retorno automático.
  - `int devolverInactivas()`: retorno automático (§5.3), por negocio y en lotes, con su propia transacción por negocio.
- `TextosConversacion` (Gemini) añade: `derivada(String negocio)`, `devuelta()`, `consentimientoDerivacionRechazado()`, `noEntendido(int fallos)` (el texto del primer fallo avisa de que el siguiente pasa con el equipo).
- `MenusConversacion.menuPrincipal(...)` (Gemini): tres botones, **Reservar · Mis citas · Hablar con alguien**.

### 3.2 WhatsApp (`pe.barberturno.whatsapp`)
- `RegistroEntradaWhatsApp.guardarEntrante(long conversacionId, MensajeEntrante mensaje)` (Claude): inserta en `mensaje_whatsapp` con `direccion ENTRANTE`, `origen CLIENTE`, `estado RECIBIDO`, `wamid` del mensaje y el contenido de §4.
- `SalidaWhatsApp.encolarRespuestaPersonal(long conversacionId, String texto, long usuarioId)` (Codex): texto con origen `PERSONA` y `enviado_por`. Mismas reglas de ventana y envío que `encolarTexto`.
- `BandejaWhatsApp` (Codex): servicio de la API de §6.
- `AnonimizacionFichas` (Codex): `void anonimizar(long fichaId)` (§7.3).

### 3.3 Configuración (Claude): `BandejaPropiedades` (prefijo `barberturno.whatsapp.bandeja`, *bean* propio) y `ConversacionPropiedades.Retencion`
| Propiedad | Base (`prod`) | `dev` y `test` |
|---|---|---|
| `barberturno.whatsapp.bandeja.entrantes` | `SOLO_HUMANO` | `SOLO_HUMANO` (D6 pendiente; `TODOS` probado en su IT) |
| `barberturno.whatsapp.bandeja.retorno-automatico` | `24h` | `24h` |
| `barberturno.whatsapp.bandeja.intervalo` | `5m` | `5m` |
| `…conversacion.retencion.cuerpo-eventos` | vacío (desactivado) | `30d` **BORRADOR P2-06** |
| `…conversacion.retencion.contenido-mensajes` | vacío (desactivado) | `90d` **BORRADOR P2-06** |
| `…conversacion.retencion.operaciones-terminadas` | vacío (desactivado) | `30d` **BORRADOR P2-06** |

`dev` usa en el entorno de revisión la base desechable `barberturno_v2_test`; ningún plazo de `dev` alcanza datos de menos de 30 días. Los comentarios del YAML dicen «propuesta del documento 15, no aprobada».

## 4. Mensajes entrantes guardados (D6, opción mínima)
- **`SOLO_HUMANO`** (predeterminado): se guardan el mensaje que provoca la derivación y los que llegan en modo humano. En modo asistente no se guarda nada nuevo (como en V2-10).
- **`TODOS`**: también los del modo asistente, para que el personal vea el contexto. Solo si el responsable lo decide en D6.
- Contenido (`jsonb`), sin el cuerpo crudo de Meta:
  - texto: `{"texto": "<cuerpo, recortado a 4096>"}`;
  - respuesta de botón o lista: `{"seleccion": "<id>", "titulo": "<título visible>"}`;
  - otros tipos: `{"tipo": "<tipo de Meta>"}` (sin descargar adjuntos).
- Los guarda el orquestador en la transacción de entrega; un reintento tras *rollback* no duplica (todo se revierte).

## 5. Modo humano
### 5.1 Ventana (125-6)
Una sola política temporal, la del enviador de V2-09, compartida por la bandeja, la derivación, la devolución y el enviador: se puede encolar si `ahora < ultima_entrada_en + 24 h − margenVentana` (5 min por defecto). La API publica el vencimiento **nominal** (`ventanaAbiertaHasta = ultima_entrada_en + 24 h`) y además `puedeResponder` y `motivoNoResponder`, calculados por el servidor con el límite **efectivo**. Fuera de él el personal no puede responder (`422 FUERA_DE_VENTANA`) y `devolver` no envía nada. Se prueban la igualdad con el límite, la entrada nula y el cierre entre el encolado y el envío.

### 5.2 Acciones del personal
- **Responder:** exige `modo = HUMANO` (si no, `409 TRANSICION_INVALIDA`) y la ventana abierta. Bloquea la conversación, encola la respuesta y fija `ultima_respuesta_personal_en = ahora`.
- **Devolver al asistente:** `devolver(id, usuarioId)`. Si ya estaba en `ASISTENTE`, `204` sin cambios (idempotente).
- Ambas bloquean primero la conversación (`FOR UPDATE`), el mismo orden que la entrega (V2-DA-65): no hay ciclos.

### 5.3 Retorno automático
- Trabajo programado propio (`barberturno.whatsapp.bandeja.intervalo`, 5 min), **fuera de toda transacción exterior**: fija el negocio con `EjecutorEnNegocio` y después abre la transacción, en lotes de 200 (125-10).
- Conversaciones `HUMANO` con `coalesce(ultima_respuesta_personal_en, derivada_en) < ahora − retorno-automatico`: bloquea, **vuelve a comprobar la condición bajo el bloqueo** y llama a `devolver(id, vacío)`.

### 5.4 Salidas automáticas pendientes al derivar (125-5)
El enviador, antes de preparar un mensaje con origen `ASISTENTE`, comprueba el modo: si la conversación está en `HUMANO`, lo termina `FALLIDO` con motivo `MODO_HUMANO`, sin aviso y sin consumir ni reiniciar presupuestos (V2-DA-70). Los mensajes `SISTEMA` (acuse, resultados con reserva) y `PERSONA` se envían. Límite aceptado: una petición HTTP ya preparada o en curso no se puede retirar.

### 5.5 Fallos de las respuestas del personal
Una respuesta `PERSONA` que termina `FALLIDO` **no genera aviso**: evita un bloqueo implícito de la conversación (la FK del aviso) después de bloquear el mensaje (125-7). La bandeja muestra el estado y el motivo junto al mensaje.

## 6. API de la bandeja (V2-DA-73)
Rutas bajo `/api/n/{slug}` (resolución del negocio de V2-05). Autorización (125-11): regla gruesa `hasRole("ADMIN")` sobre las rutas normalizadas (PROPIETARIO y ENCARGADO se convierten en `ROLE_ADMIN`, `RolNegocio`) y, en el servicio, membresía activa del negocio con rol PROPIETARIO o ENCARGADO, repetida tras esperar por el bloqueo de la conversación en las escrituras. Semántica de V2-DA-34: negocio existente donde el actor no tiene papel (también el administrador de plataforma sin membresía) → `403`; *slug* inexistente o id de otro negocio bajo un ámbito autorizado → `404`. Escrituras con CSRF.

| Método y ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|
| `GET /whatsapp/conversaciones` | `pagina`, `tamano` (≤ 50) | `PaginaDto<ConversacionBandejaDto>`, solo `modo = HUMANO`, por `ultima_entrada_en` descendente y `id` descendente |
| `GET /whatsapp/conversaciones/{id}` | `antesDe` (id de mensaje > 0, opcional) | `DetalleConversacionDto` con los 50 mensajes anteriores a `antesDe` (o los últimos 50), en orden ascendente de `id`. `404` si no está en `HUMANO` |
| `POST /whatsapp/conversaciones/{id}/respuestas` | `{ "texto": string 1…4096 }` | `201` `MensajeBandejaDto` (`encolarRespuestaPersonal` devuelve el id, `long`) |
| `POST /whatsapp/conversaciones/{id}/devolver` | — | `204` |

DTO (espejo en `frontend/src/app/core/modelos/bandeja-whatsapp.ts`):
- `ConversacionBandejaDto(id, cliente, telefonoEnmascarado, motivo, derivadaEn, ultimaEntradaEn, ventanaAbiertaHasta, pendienteDeRespuesta, puedeResponder, motivoNoResponder)`:
  - `cliente`: nombre de la ficha;
  - `telefonoEnmascarado`: máscara genérica `•••…123` (solo los 3 últimos dígitos; si el `wa_id` tiene 4 o menos, todo oculto); el `wa_id` completo nunca sale en la API;
  - `pendienteDeRespuesta`: `ultima_entrada_en` posterior a `ultima_respuesta_personal_en` (o sin respuesta del personal); las salidas automáticas no cuentan;
  - `puedeResponder` y `motivoNoResponder` (`FUERA_DE_VENTANA`, `NEGOCIO_NO_OPERATIVO` o `null`): los calcula el servidor (§5.1); el *frontend* no repite la regla;
  - instantes como `OffsetDateTime` de Lima.
- `DetalleConversacionDto(conversacion: ConversacionBandejaDto, mensajes: MensajeBandejaDto[], hayAnteriores)`.
- `MensajeBandejaDto(id, direccion, origen, texto, retirado, estado, creadoEn, enviadoPor)`:
  - `texto`: el texto, o el título de la selección, o el cuerpo del interactivo con sus botones entre corchetes, o `null` si `retirado`;
  - `enviadoPor`: nombre **actual** del usuario que respondió (solo `PERSONA`; también si ya no es miembro);
  - `estado` y `motivoFallo` permiten mostrar una respuesta que no se pudo enviar (§5.5).
- **Avisos:** `NotificacionDto` pasa a `(id, reservaId: number|null, conversacionId: number|null, tipo, mensaje, leida, creadoEn)`. Al pulsar un aviso de derivación, el *frontend* abre `/n/{slug}/whatsapp/{conversacionId}`; si la conversación ya se devolvió (404), lo explica. El panel de avisos no muestra «BT-null»: los avisos de conversación tienen su texto y enlace propios (125-9).

## 7. Modelo de datos: V116 (responsable: Codex)
`V116__derivacion_bandeja_retencion.sql`. Todo con nombres explícitos y comprobado de forma exacta en las pruebas de esquema (sin debilitar las existentes).

### 7.1 Derivación y bandeja
1. **`conversacion`:**
   - `derivada_en timestamptz`, `motivo_derivacion varchar(12)` con `CHECK` de `MotivoDerivacion`, `ultima_respuesta_personal_en timestamptz`;
   - `CHECK` de dos ramas (125-1): `(modo = 'HUMANO' AND cliente_id IS NOT NULL AND derivada_en IS NOT NULL AND motivo_derivacion IS NOT NULL) OR (modo = 'ASISTENTE' AND derivada_en IS NULL AND motivo_derivacion IS NULL AND ultima_respuesta_personal_en IS NULL)`. Exigir ficha en `HUMANO` respalda en SQL que el personal no vea conversaciones sin consentimiento;
   - `GRANT UPDATE (modo, derivada_en, motivo_derivacion, ultima_respuesta_personal_en)`;
   - índice parcial `(negocio_id, ultima_entrada_en DESC) WHERE modo = 'HUMANO'`.
2. **`mensaje_whatsapp`:**
   - `enviado_por bigint REFERENCES usuario(id)` y `CHECK ((origen = 'PERSONA') = (enviado_por IS NOT NULL))`;
   - `CHECK (direccion = 'SALIENTE' OR origen = 'CLIENTE')` (V114 ya liga `ENTRANTE` con `RECIBIDO`, pero no el origen);
   - motivo de fallo nuevo `MODO_HUMANO` en `MotivoFallo` y en su `CHECK` (§5.4).
3. **`notificacion`:**
   - `reserva_id` anulable; `conversacion_id bigint` con FK compuesta `(negocio_id, conversacion_id)` a `conversacion`;
   - `CHECK (num_nonnulls(reserva_id, conversacion_id) = 1)`;
   - tipo **`AccionAuditoria.DERIVACION`**, solo para avisos: cabe en `varchar(12)`, no amplía el `CHECK` de `auditoria_reserva.accion` ni afecta a los reportes (125-9).

### 7.2 Retención (V2-DA-74)
4. **Contenido de los mensajes:** `GRANT UPDATE (contenido)` y un disparador que actúa solo si `NEW.contenido IS DISTINCT FROM OLD.contenido` y exige (125-2): `NEW.contenido = '{"retirado": true}'::jsonb`, `OLD.estado` y `NEW.estado` distintos de `PENDIENTE` y conversación con ficha. Las demás actualizaciones (las del enviador) no se ven afectadas. El plazo y la habilitación son del servicio; el SQL solo impide lo que nunca se puede retirar.
5. **`operacion_pendiente`:** `GRANT DELETE` con una política **restrictiva** `FOR DELETE USING (estado <> 'VIGENTE')`. La purga marca antes `CADUCADA` las vigentes vencidas (`UPDATE` ya concedido).

### 7.3 Anonimización de una ficha (D5, opción propuesta; V2-DA-75)
6. **`cliente`:** `anonimizada_en timestamptz`; `GRANT UPDATE (nombre, wa_id, telefono, anonimizada_en)` si faltan; `CHECK` de identificabilidad ampliado: `… OR anonimizada_en IS NOT NULL`.
7. Las políticas restrictivas de `DELETE` de `conversacion` y `mensaje_whatsapp` (V115) se **sustituyen** (añadir otra restrictiva se combinaría con `AND`), manteniendo la permisiva de aislamiento (125-3):
   - `conversacion`: `cliente_id IS NULL OR` ficha del mismo negocio con `anonimizada_en` no nula (`EXISTS` correlacionado por `negocio_id` e `id`; funciona bajo la RLS de `cliente`, que solo filtra por negocio);
   - `mensaje_whatsapp`: `estado <> 'PENDIENTE' AND ((conversación sin ficha AND reserva_id IS NULL) OR ficha de la conversación anonimizada)`;
   - `operacion_pendiente` (punto 5): `estado <> 'VIGENTE' OR` ficha anonimizada.

   El disparador de `conversacion.cliente_id` no cambia (valida inserciones y actualizaciones, no borrados). `cliente_wa_uk` (parcial, `wa_id IS NOT NULL`) se conserva: admite varias fichas anonimizadas y libera el número. `cliente` ya hereda `UPDATE` de tabla (V100/V102): los permisos de columna no lo restringen.

`ActualizacionV1IT`, `InstalacionNuevaIT`, `EsquemaV2IT`, `EsquemaIT` y las pruebas de esquema de WhatsApp se ajustan a la nueva regla, sin debilitarlas.

## 8. Retención y anonimización (comportamiento)
- **Contenido** (`retencion.contenido-mensajes`, desactivado por defecto): en las conversaciones **con ficha**, los mensajes no `PENDIENTE` con `creado_en < ahora − plazo` pasan a `{"retirado": true}`. Se conservan dirección, origen, estado, `wamid`, `reserva_id` y fechas. La bandeja los muestra como «Mensaje retirado por la política de retención».
- **Operaciones terminadas** (`retencion.operaciones-terminadas`): marca `CADUCADA` las vigentes vencidas y borra las no vigentes con `actualizado_en < ahora − plazo`. El resultado ejecutado ya consta en `reserva` y `auditoria_reserva`.
- **Cuerpo de los eventos:** el mecanismo de V2-10, sin cambios; solo se configura en `dev` y `test`.
- Todo en el trabajo de retención existente (`RetencionWhatsApp`, ahora de Codex), con **una transacción por negocio y fase** (conversaciones, contenido, operaciones), lotes con `SKIP LOCKED` y el `Clock` inyectado (125-10). Contenido: excluye los ya retirados y revalida el estado bajo bloqueo. Operaciones: bloquea antes la conversación. No toca pendientes, reservas de intentos, esperas de conciliación ni contadores del enviador. Una pulsación de una operación purgada responde «no encontrada» y nunca ejecuta nada.
- **Anonimizar una ficha** (`POST /api/n/{slug}/clientes/{id}/anonimizar`, solo PROPIETARIO, CSRF, `204`):
  - solo fichas **sin cuenta web** (`usuario_id` nulo); si tiene cuenta → `409 CONFLICTO` (la baja de una cuenta web es de v1);
  - si tiene reservas futuras `PENDIENTE` o `CONFIRMADA` → `409 CONFLICTO_CON_RESERVAS` (el personal las cancela antes);
  - si su conversación tiene mensajes `PENDIENTE` → `409 CONFLICTO` (reintentar más tarde);
  - **orden** (125-3, 125-7, 125-8): bloquea la conversación de la ficha (si existe), después sus operaciones y después la ficha. **Tras esperar por los bloqueos** comprueba la cuenta, las reservas (consulta de existencia sin paginación: cualquier reserva `PENDIENTE`, `CONFIRMADA` o `EN_ATENCION`, sea cual sea su inicio → `409 CONFLICTO_CON_RESERVAS`) y los mensajes `PENDIENTE`. Después **marca la ficha** (`nombre = 'Cliente eliminado'`, `wa_id` y `telefono` a `null`, `activo = false`, `anonimizada_en = ahora`) y solo entonces borra, en este orden, los avisos de derivación de la conversación, las operaciones, los mensajes y la conversación. **Se conservan** las reservas y la auditoría (`AuditoriaDto` mostrará «Cliente eliminado»);
  - una ficha anonimizada no se puede editar ni reactivar por ninguna ruta (`409 CONFLICTO`);
  - **límite declarado:** no vacía los cuerpos crudos de los eventos entrantes, que conservan número, nombre y texto hasta su propia retención (desactivada en la base). Hacerlo aquí invertiría el orden evento → conversación de la entrega. Queda pendiente de P2-06 (D4/D5);
  - idempotente: una ficha ya anonimizada → `204` sin cambios. Otro negocio → `404`;
  - los registros no incluyen nombre, `wa_id` ni teléfono.

## 9. Criterios de aceptación
| # | Criterio | Responsable de la prueba |
|---|---|---|
| CB-1 | El menú principal tiene tres botones dentro de los límites de Meta; `m:PERSONA` y las frases exactas de `PERSONA` se reconocen; otras frases no | Gemini |
| CB-2 | Derivar con ficha: modo `HUMANO`, operación vigente anulada, estado vacío, acuse `SISTEMA` en la outbox, un aviso por PROPIETARIO y ENCARGADO activos (ninguno a PROFESIONAL, a miembros revocados ni a otro negocio); repetir no duplica nada | Codex |
| CB-3 | Sin ficha: pedir persona → consentimiento; aceptar → ficha y derivación; rechazar → menú, sin ficha ni derivación | Claude |
| CB-4 | Dos textos no reconocidos seguidos derivan (`SIN_PROGRESO`); uno reconocido entre ellos reinicia la cuenta | Claude |
| CB-5 | En modo humano no hay ninguna respuesta automática (V2-RN-15) y el entrante se guarda; un botón de confirmación antiguo no ejecuta nada | Claude |
| CB-6 | Bandeja: solo PROPIETARIO y ENCARGADO; PROFESIONAL, CLIENTE y anónimo → 403/401; otro negocio → 404; sin CSRF → 403; el `wa_id` completo no aparece en ninguna respuesta | Codex |
| CB-7 | Responder dentro de la ventana encola un texto `PERSONA` con `enviado_por`; fuera → `422 FUERA_DE_VENTANA`; en `ASISTENTE` → `409 TRANSICION_INVALIDA` | Codex |
| CB-8 | Devolver: modo `ASISTENTE`, texto y menú solo con la ventana abierta; idempotente; el siguiente mensaje del cliente lo atiende el asistente | Codex |
| CB-9 | Retorno automático tras 24 h sin actividad del personal, comprobado bajo bloqueo, por negocio sin cruzar datos; una respuesta reciente lo impide | Codex |
| CB-10 | Retención: el servicio retira contenido solo si está configurado y vencido, y purga operaciones terminadas; con SQL directo, la política y el disparador impiden retirar contenido de un `PENDIENTE` o de una conversación sin ficha, cambiarlo a otro valor y borrar operaciones vigentes | Codex |
| CB-11 | Anonimización: reglas y orden de §8 (con reserva activa o `EN_ATENCION` → 409; con mensajes pendientes → 409), avisos de derivación, operaciones, mensajes (también con `reserva_id`) y conversación borrados; reservas y auditoría intactas; idempotente; por negocio; ficha anonimizada no editable; un mensaje posterior del mismo número empieza sin ficha | Codex |
| CB-16 | Al derivar, un menú `ASISTENTE` aún pendiente termina `FALLIDO`/`MODO_HUMANO` sin enviarse; los `SISTEMA` y `PERSONA` se envían | Codex |
| CB-12 | V116: permisos exactos, `CHECK`, disparador, políticas restrictivas; `ActualizacionV1IT` e `InstalacionNuevaIT` en verde | Codex |
| CB-13 | Pantalla de la bandeja: lista, detalle, responder (deshabilitado fuera de la ventana con la explicación), devolver con confirmación, aviso que abre la conversación; accesible (axe sin críticos), sin reglas de negocio en el *frontend* | Gemini |
| CB-14 | E2E: derivación por WhatsApp (evento firmado con el secreto ficticio) → aviso → respuesta en la bandeja → devolver; con el servidor Graph falso | Gemini (escenario) y Claude (ejecución) |
| CB-15 | `verify` completo en verde, con JaCoCo y Javadoc sin avisos; *frontend* lint, unitarias y build; registros sin `wa_id`, texto ni nombre | Claude |

## 10. Reparto, ramas y revisión cruzada
| Bloque | Responsable | Worktree y rama | Archivos (alcance) | Revisa |
|---|---|---|---|---|
| 0 · Contrato | Claude | `D:\Proyectos\BarberTurno-v2-11` · `tarea/V2-11-derivacion-bandeja` | este documento, decisiones, *backlog*, tipos de §3, modelos del *frontend* de §6, YAML | Codex (consulta de diseño, solo lectura) |
| X11 · Migración, bandeja, derivación, avisos, retención y anonimización | Codex | `D:\Proyectos\BarberTurno-v2-11-backend` · `tarea/V2-11-derivacion-bandeja-backend` | `V116`; `conversation/{DerivacionConversacionService,RetencionWhatsApp,ProgramadorRetencionWhatsApp}` y el programador del retorno automático; `whatsapp/{SalidaWhatsAppService,ColaSalidaWhatsApp,EnviadorMensajesWhatsApp,MotivoFallo,BandejaWhatsApp*,Conversacion,MensajeWhatsApp}` y sus DTO; `notifications/*`; `audit/AccionAuditoria`; `customers/` (anonimización, su endpoint y la guarda de edición); `common/security/SecurityConfig` y la autorización local; sus pruebas y las de esquema de §11 | Gemini Argon (*diff-first*) + Claude |
| C11 · Orquestador y registro | Claude | rama de la tarea | `conversation/OrquestadorConversacion`, `whatsapp/RegistroEntradaWhatsApp`, `conversation/ConversacionPropiedades`, `application*.yml`; sus pruebas | Codex (solo lectura) |
| G11 · Menús, textos y pantalla | Gemini | `D:\Proyectos\BarberTurno-v2-11-gemini` · `tarea/V2-11-derivacion-bandeja-gemini` | `conversation/{PalabraClave,MenusConversacion,TextosConversacion}` y sus pruebas; `frontend/src/app/core/api/bandeja-whatsapp.service.ts`, `features/bandeja-whatsapp/**`, la ruta, la entrada del menú y la navegación del aviso; sus pruebas; el escenario E2E | Codex (solo lectura) + Claude |

- **Migraciones:** solo Codex. **Contrato y configuración compartida:** solo Claude. `RetencionWhatsApp` y `ProgramadorRetencionWhatsApp` pasan de Claude (V2-10) a Codex (reasignación anotada en el estado).
- **Pruebas con base:** siempre con el ejecutor serializado (`mvn-v2-test.ps1`). Gemini no puede ejecutar órdenes: Claude ejecuta sus pruebas y registra autor y ejecutor por separado.

## 11. Consulta de diseño de Codex (encargo 125, 04/11/2026): «Con propuestas», 14, aceptadas
Aplicadas en §2, §5, §6, §7 y §8 (referencias 125-n). Además:
- `EstadoConversacion` (Claude) añade `modo`; X11 usa su propia proyección SQL para los campos de derivación. `Conversacion` y `MensajeWhatsApp` son `@Immutable`: las escrituras siguen con SQL acotado.
- `ColaSalidaWhatsApp.insertar` no rellena `enviado_por`: `encolarRespuestaPersonal` inserta texto y actor de forma atómica, y los métodos generales rechazan el origen `PERSONA`.
- Pruebas afectadas (de X11, 125-13): `ActualizacionV1IT` (huella de `notificacion.conversacion_id` y columnas nuevas; actualización V115 → V116 con datos), `InstalacionNuevaIT` (FK compuestas 13 → 14), `EsquemaV2IT` (`notificacion_conversacion_fk`), `WhatsAppSalidaEsquemaPruebas` (pasa de Gemini a Codex en esta tarea), `ConversacionV115IT`, `WhatsAppEsquemaPruebas` (identificabilidad), `RepositoriosIT` (segundo destino del aviso) y `RetencionWhatsAppIT` (la desactivación se prueba con configuración explícita, porque `test` activa 30 días). `EsquemaIT` no cambia (19 tablas, 11 con RLS forzada).
- Documentos: `04-arquitectura-datos.md` §7.1 (orden), V2-DA-65 (excepción: la bandeja y la anonimización toman la conversación desde la web, en el mismo orden) y V2-DA-67 (excepción de la anonimización al borrado de mensajes con reserva).

## 12. Pendientes del responsable (no se simulan)
- **P2-06:** D1…D7 del documento 15 (texto, aviso, papeles, plazos, D5, D6, transferencia a Meta). Lo implementado aquí es la opción propuesta y no la aprueba.
- **P2-05:** conexión real con Meta. Todo se prueba con el servidor Graph falso y eventos firmados con un secreto ficticio.
