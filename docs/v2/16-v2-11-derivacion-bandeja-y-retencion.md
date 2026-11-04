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
4. La derivación guarda **también** el entrante que la provocó (es el primer mensaje que verá el personal).

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

### 3.3 Configuración (`ConversacionPropiedades`, Claude)
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
### 5.1 Ventana
`ventanaAbiertaHasta = ultima_entrada_en + 24 h`. Fuera de ella el personal no puede responder (`422 FUERA_DE_VENTANA`) y `devolver` no envía nada.

### 5.2 Acciones del personal
- **Responder:** exige `modo = HUMANO` (si no, `409 TRANSICION_INVALIDA`) y la ventana abierta. Bloquea la conversación, encola la respuesta y fija `ultima_respuesta_personal_en = ahora`.
- **Devolver al asistente:** `devolver(id, usuarioId)`. Si ya estaba en `ASISTENTE`, `204` sin cambios (idempotente).
- Ambas bloquean primero la conversación (`FOR UPDATE`), el mismo orden que la entrega (V2-DA-65): no hay ciclos.

### 5.3 Retorno automático
- Trabajo programado (`barberturno.whatsapp.bandeja.intervalo`), por negocio con `EjecutorEnNegocio` y lotes de 200, como la retención.
- Conversaciones `HUMANO` con `coalesce(ultima_respuesta_personal_en, derivada_en) < ahora − retorno-automatico`: bloquea, **vuelve a comprobar la condición bajo el bloqueo** y llama a `devolver(id, vacío)`.

## 6. API de la bandeja (V2-DA-73)
Rutas bajo `/api/n/{slug}` (resolución del negocio de V2-05). Rol del negocio **PROPIETARIO o ENCARGADO**; los demás → `403 PROHIBIDO`; otro negocio → `404`. Escrituras con CSRF.

| Método y ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|
| `GET /whatsapp/conversaciones` | `pagina`, `tamano` (≤ 50) | `PaginaDto<ConversacionBandejaDto>`, solo `modo = HUMANO`, por `ultima_entrada_en` descendente |
| `GET /whatsapp/conversaciones/{id}` | `antesDe` (id de mensaje, opcional) | `DetalleConversacionDto` con los 50 mensajes anteriores a `antesDe` (o los últimos 50). `404` si no está en `HUMANO` |
| `POST /whatsapp/conversaciones/{id}/respuestas` | `{ "texto": string 1…4096 }` | `201` `MensajeBandejaDto` |
| `POST /whatsapp/conversaciones/{id}/devolver` | — | `204` |

DTO (espejo en `frontend/src/app/core/modelos/bandeja-whatsapp.ts`):
- `ConversacionBandejaDto(id, cliente, telefonoEnmascarado, motivo, derivadaEn, ultimaEntradaEn, ventanaAbiertaHasta, pendienteDeRespuesta)`:
  - `cliente`: nombre de la ficha;
  - `telefonoEnmascarado`: `+51 9•• ••• 123` (solo los 3 últimos dígitos del `wa_id`); el `wa_id` completo nunca sale en la API;
  - `pendienteDeRespuesta`: el último mensaje es entrante;
  - instantes como `OffsetDateTime` de Lima.
- `DetalleConversacionDto(conversacion: ConversacionBandejaDto, mensajes: MensajeBandejaDto[], hayAnteriores)`.
- `MensajeBandejaDto(id, direccion, origen, texto, retirado, estado, creadoEn, enviadoPor)`:
  - `texto`: el texto, o el título de la selección, o el cuerpo del interactivo con sus botones entre corchetes, o `null` si `retirado`;
  - `enviadoPor`: nombre del miembro (solo `PERSONA`).
- **Avisos:** `NotificacionDto` pasa a `(id, reservaId: number|null, conversacionId: number|null, tipo, mensaje, leida, creadoEn)`. Al pulsar un aviso de derivación, el *frontend* abre `/n/{slug}/whatsapp/{conversacionId}`.

## 7. Modelo de datos: V116 (responsable: Codex)
`V116__derivacion_bandeja_retencion.sql`. Todo con nombres explícitos y comprobado de forma exacta en las pruebas de esquema (sin debilitar las existentes).

### 7.1 Derivación y bandeja
1. **`conversacion`:**
   - `derivada_en timestamptz`, `motivo_derivacion varchar(12)` con `CHECK` de `MotivoDerivacion`, `ultima_respuesta_personal_en timestamptz`;
   - `CHECK ((modo = 'HUMANO') = (derivada_en IS NOT NULL AND motivo_derivacion IS NOT NULL))` y `ultima_respuesta_personal_en IS NULL OR modo = 'HUMANO'`;
   - `GRANT UPDATE (modo, derivada_en, motivo_derivacion, ultima_respuesta_personal_en)`;
   - índice parcial `(negocio_id, ultima_entrada_en DESC) WHERE modo = 'HUMANO'`.
2. **`mensaje_whatsapp`:**
   - `enviado_por bigint REFERENCES usuario(id)` y `CHECK ((origen = 'PERSONA') = (enviado_por IS NOT NULL))`;
   - `CHECK (direccion = 'SALIENTE' OR (origen = 'CLIENTE' AND estado = 'RECIBIDO'))` si no existe ya uno equivalente.
3. **`notificacion`:**
   - `reserva_id` anulable; `conversacion_id bigint` con FK compuesta `(negocio_id, conversacion_id)` a `conversacion`;
   - `CHECK (num_nonnulls(reserva_id, conversacion_id) = 1)`;
   - el tipo de aviso de derivación (nombre a proponer por Codex en la consulta de diseño, sin tocar el `CHECK` de `auditoria_reserva.accion`).

### 7.2 Retención (V2-DA-74)
4. **Contenido de los mensajes:** `GRANT UPDATE (contenido)` y un disparador que solo admite cambiar `contenido` a `{"retirado": true}` en mensajes no `PENDIENTE`. Cualquier otro cambio de `contenido` falla.
5. **`operacion_pendiente`:** `GRANT DELETE` con una política **restrictiva** `FOR DELETE USING (estado <> 'VIGENTE')`. La purga marca antes `CADUCADA` las vigentes vencidas (`UPDATE` ya concedido).

### 7.3 Anonimización de una ficha (D5, opción propuesta; V2-DA-75)
6. **`cliente`:** `anonimizada_en timestamptz`; `GRANT UPDATE (nombre, wa_id, telefono, anonimizada_en)` si faltan; `CHECK` de identificabilidad ampliado: `… OR anonimizada_en IS NOT NULL`.
7. Las políticas restrictivas de `DELETE` de `conversacion` y `mensaje_whatsapp` (V115) se **amplían** a las conversaciones de una ficha anonimizada (`EXISTS` sobre `cliente.anonimizada_en`), y la de `operacion_pendiente` (punto 5) también admite sus vigentes. El disparador de `conversacion.cliente_id` no cambia.

`ActualizacionV1IT`, `InstalacionNuevaIT`, `EsquemaV2IT`, `EsquemaIT` y las pruebas de esquema de WhatsApp se ajustan a la nueva regla, sin debilitarlas.

## 8. Retención y anonimización (comportamiento)
- **Contenido** (`retencion.contenido-mensajes`, desactivado por defecto): en las conversaciones **con ficha**, los mensajes no `PENDIENTE` con `creado_en < ahora − plazo` pasan a `{"retirado": true}`. Se conservan dirección, origen, estado, `wamid`, `reserva_id` y fechas. La bandeja los muestra como «Mensaje retirado por la política de retención».
- **Operaciones terminadas** (`retencion.operaciones-terminadas`): marca `CADUCADA` las vigentes vencidas y borra las no vigentes con `actualizado_en < ahora − plazo`. El resultado ejecutado ya consta en `reserva` y `auditoria_reserva`.
- **Cuerpo de los eventos:** el mecanismo de V2-10, sin cambios; solo se configura en `dev` y `test`.
- Todo en el trabajo de retención existente (`RetencionWhatsApp`, ahora de Codex), por negocio, en lotes y con el `Clock` inyectado.
- **Anonimizar una ficha** (`POST /api/n/{slug}/clientes/{id}/anonimizar`, solo PROPIETARIO, CSRF, `204`):
  - solo fichas **sin cuenta web** (`usuario_id` nulo); si tiene cuenta → `409 CONFLICTO` (la baja de una cuenta web es de v1);
  - si tiene reservas futuras `PENDIENTE` o `CONFIRMADA` → `409 CONFLICTO_CON_RESERVAS` (el personal las cancela antes);
  - si su conversación tiene mensajes `PENDIENTE` → `409 CONFLICTO` (reintentar más tarde);
  - si no: bloquea la conversación y la ficha (orden de V2-DA-65), borra las operaciones, los mensajes y la conversación, y deja la ficha con `nombre = 'Cliente eliminado'`, `wa_id = null`, `telefono = null`, `activo = false` y `anonimizada_en = ahora`. **Se conservan** las reservas y la auditoría, que la referencian;
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
| CB-10 | Retención: contenido retirado solo en conversaciones con ficha, solo no `PENDIENTE` y solo si está configurado; operaciones: solo no vigentes; las políticas y el disparador impiden lo demás aunque se intente con SQL directo | Codex |
| CB-11 | Anonimización: reglas de §8, reservas y auditoría intactas, conversación y mensajes borrados, idempotente, por negocio; un mensaje posterior del mismo número empieza sin ficha | Codex |
| CB-12 | V116: permisos exactos, `CHECK`, disparador, políticas restrictivas; `ActualizacionV1IT` e `InstalacionNuevaIT` en verde | Codex |
| CB-13 | Pantalla de la bandeja: lista, detalle, responder (deshabilitado fuera de la ventana con la explicación), devolver con confirmación, aviso que abre la conversación; accesible (axe sin críticos), sin reglas de negocio en el *frontend* | Gemini |
| CB-14 | E2E: derivación por WhatsApp (evento firmado con el secreto ficticio) → aviso → respuesta en la bandeja → devolver; con el servidor Graph falso | Gemini (escenario) y Claude (ejecución) |
| CB-15 | `verify` completo en verde, con JaCoCo y Javadoc sin avisos; *frontend* lint, unitarias y build; registros sin `wa_id`, texto ni nombre | Claude |

## 10. Reparto, ramas y revisión cruzada
| Bloque | Responsable | Worktree y rama | Archivos (alcance) | Revisa |
|---|---|---|---|---|
| 0 · Contrato | Claude | `D:\Proyectos\BarberTurno-v2-11` · `tarea/V2-11-derivacion-bandeja` | este documento, decisiones, *backlog*, tipos de §3, modelos del *frontend* de §6, YAML | Codex (consulta de diseño, solo lectura) |
| X11 · Migración, bandeja, derivación, avisos, retención y anonimización | Codex | `D:\Proyectos\BarberTurno-v2-11-backend` · `tarea/V2-11-derivacion-bandeja-backend` | `V116`; `conversation/{DerivacionConversacionService,RetencionWhatsApp,ProgramadorRetencionWhatsApp}`; `whatsapp/{SalidaWhatsAppService,BandejaWhatsApp*,…}` y sus DTO; `notifications/*` (aviso sin reserva); `customers/` (anonimización y su endpoint); permisos del servidor; sus pruebas y las de esquema afectadas | Gemini Argon (*diff-first*) + Claude |
| C11 · Orquestador y registro | Claude | rama de la tarea | `conversation/OrquestadorConversacion`, `whatsapp/RegistroEntradaWhatsApp`, `conversation/ConversacionPropiedades`, `application*.yml`; sus pruebas | Codex (solo lectura) |
| G11 · Menús, textos y pantalla | Gemini | `D:\Proyectos\BarberTurno-v2-11-gemini` · `tarea/V2-11-derivacion-bandeja-gemini` | `conversation/{PalabraClave,MenusConversacion,TextosConversacion}` y sus pruebas; `frontend/src/app/core/api/bandeja-whatsapp.service.ts`, `features/bandeja-whatsapp/**`, la ruta, la entrada del menú y la navegación del aviso; sus pruebas; el escenario E2E | Codex (solo lectura) + Claude |

- **Migraciones:** solo Codex. **Contrato y configuración compartida:** solo Claude. `RetencionWhatsApp` y `ProgramadorRetencionWhatsApp` pasan de Claude (V2-10) a Codex (reasignación anotada en el estado).
- **Pruebas con base:** siempre con el ejecutor serializado (`mvn-v2-test.ps1`). Gemini no puede ejecutar órdenes: Claude ejecuta sus pruebas y registra autor y ejecutor por separado.

## 11. Pendientes del responsable (no se simulan)
- **P2-06:** D1…D7 del documento 15 (texto, aviso, papeles, plazos, D5, D6, transferencia a Meta). Lo implementado aquí es la opción propuesta y no la aprueba.
- **P2-05:** conexión real con Meta. Todo se prueba con el servidor Graph falso y eventos firmados con un secreto ficticio.
