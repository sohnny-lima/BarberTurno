# BarberTurno v2 · 17 · V2-12 y V2-13 · Conversación con IA y evaluación de conversaciones

> Autorizada por el responsable el 04/11/2026 (cierre técnico del MVP). Rama `tarea/V2-12-conversacion-ia` desde V2-11 (necesita la derivación, el modo humano y la anonimización); se une con V2-11 terminada antes de implementar XI. Se integra en `tarea/V2-MVP-revision`. **Sin integrar en `v2` ni publicar.** Clase **Crítica** (reservas, guardas, datos enviados a un proveedor, concurrencia, costes).
>
> Requisitos: V2-RF-16, V2-RN-11/12/14, V2-RNF-05, V2-CP-08. Arquitectura: `04-arquitectura-datos.md` §7; `06-flujos.md` §7; `08-pruebas-migracion-reversion.md` §4; `09-costes.md` §3; V2-DA-12 y V2-DA-16. Decisiones nuevas **V2-DA-77…82**. Consulta de diseño de Codex: encargo 127 (§12), **22 propuestas aceptadas**.
>
> **Bloqueos externos, que no se simulan:** **P2-03** (clave y modelo: no hay `BT_IA_*` en el entorno), el **presupuesto** de la evaluación en vivo y la **revisión del conjunto** de conversaciones por el responsable. Todo se construye y prueba con el adaptador falso y con un servidor HTTP local que imita la API de Anthropic. **Sin clave, la IA queda desactivada y la conversación sigue con los menús de V2-10/V2-11** (V2-RN-14).

## 1. Alcance
**V2-12 entra:**
1. Puerto `ModeloConversacional` con un adaptador **falso programable** y uno de **Anthropic** con el SDK oficial de Java (`com.anthropic:anthropic-java`; versión comprobada en Maven Central al implementarlo y anotada con fuente y fecha).
2. **Turno de IA fuera de la transacción de entrega** (§3, V2-DA-77), con revisión monotónica de la conversación y ficha de reclamo.
3. Herramientas de lectura y de **propuesta** con **alias opacos** (§4.3), ejecutadas con el negocio y la ficha del contexto.
4. **Guardas de salida** y franjas compuestas **siempre** por el servidor (§5).
5. **Límites y costes** con reserva previa del presupuesto (§6, V2-DA-79).
6. **Contexto mínimo** construido por lista blanca y saneado (§7, V2-RNF-05).

**V2-13 entra (solo lo local):** suite determinista `ConversacionGuionesIT` en `verify`; **arnés en vivo** listo (perfil Maven `evaluacion-en-vivo`, fuera de `verify`) con correctores por reglas, observador y tope de gasto; **borrador** del conjunto de 120 conversaciones (96 de ajuste + 24 de prueba) marcado «BORRADOR pendiente de revisión del responsable»; plantilla del informe sin resultados.

**No entra:** ejecutar el arnés contra el modelo real, elegir el modelo, el respaldo `fallbacks` del servidor (exige la API beta del SDK; el respaldo son los menús, V2-DA-80), recordatorios (V2-14), adjuntos, voz, imágenes, *streaming*, edición de los límites desde la web (las columnas de `negocio` se fijan por datos de demostración o SQL en el MVP).

## 2. Cuándo actúa la IA
En modo `ASISTENTE`, tras el ciclo de vida y la interpretación de V2-10/V2-11:
1. Botones, selecciones y palabras clave (`MENU`, `SI`, `NO`, `PERSONA`) → camino determinista, sin IA.
2. Texto libre (`OTRA`) con la IA **disponible** (§6) → se encola un turno; la entrega no responde. Sin IA → camino de V2-11 (no entendido; al segundo, derivación).
3. Sin ficha, la IA puede consultar; **proponer** o **derivar** exige ficha: el efecto final pide el consentimiento (§3.3).
4. En `HUMANO`, nunca.

## 3. Turno de IA (V2-DA-77)
### 3.1 Revisión de la conversación
`conversacion.revision bigint NOT NULL DEFAULT 0`, incrementada por un **disparador** `BEFORE UPDATE` (V117) en **cualquier** actualización de la fila: cada entrada registrada (el `ON CONFLICT DO UPDATE` de V2-08), cambios de estado, derivación, devolución, respuesta del personal. Es la marca de invalidación del turno; `ultima_entrada_en` sigue solo para la ventana de WhatsApp.

### 3.2 Encolar (en la transacción de entrega, Claude)
Inserta `turno_ia` (`PENDIENTE`) con `conversacion_id`, la `revision` leída bajo el bloqueo **después** de las escrituras de la entrega, el texto del cliente **saneado** (§7) y recortado a 2000, y `intentos = 0`. Si ya hay un turno `PENDIENTE` de esa conversación, se marca `DESCARTADO` (gana el último mensaje).

### 3.3 Resolver (trabajador, Codex)
1. **Reclamar** con `FOR UPDATE SKIP LOCKED`, por negocio con `EjecutorEnNegocio`, de uno en uno: `intentos = intentos + 1`, `reclamado_hasta = ahora + 2 min`. El valor de `intentos` es la **ficha de reclamo**: toda renovación y todo cierre exigen `estado = PENDIENTE AND intentos = ficha`. Plazo total del turno: 90 s; la concesión se renueva antes de cada llamada.
2. **Antes de cada llamada** comprueba en una transacción corta que la revisión y el modo no cambiaron (si cambiaron → `DESCARTADO` sin más llamadas) y **reserva** la llamada y su coste máximo (§6).
3. **Contexto** (§7) en una transacción corta.
4. **Bucle**, como máximo 4 llamadas y 4 herramientas por respuesta (las demás → `is_error` «demasiadas consultas»):
   - **lectura** (`listar_servicios`, `listar_profesionales`, `buscar_disponibilidad`, `mis_citas`): una transacción corta cada una;
   - **propuesta**: validación en seco con `ReservaService.prepararCreacionFicha` / `prepararCambioFicha` en una transacción corta (toman bloqueos: **no** `readOnly`), proyectada a datos inmutables; devuelve al modelo «el servidor enviará el resumen para que lo confirme». Sin ficha: no valida reglas de ficha y deja el efecto `SolicitarConsentimientoReserva`;
   - **`derivar_a_persona`**: efecto `Derivar` (o `SolicitarConsentimientoDerivacion` sin ficha); termina el bucle;
   - todos los `tool_result` de una respuesta van juntos en el siguiente mensaje, con sus `tool_use_id`; el contenido del asistente se devuelve **intacto** (pensamiento incluido) mediante la continuación opaca del proveedor (§4.1).
5. **Aplicar** (transacción final), orden de bloqueos **conversación → turno → operación pendiente → protocolo de dominio**:
   - comprueba que la conversación existe, `revision` y modo `ASISTENTE` sin cambios, la misma ficha (o la misma ausencia), el negocio operativo y la ficha del reclamo; si algo falla → `DESCARTADO`, sin salida;
   - aplica **un solo efecto** con esta precedencia: derivación > consentimiento > propuesta > franjas > texto. Una propuesta se crea con `OperacionesConversacion.proponer*` (revalida y anula la vigente) y su resumen y botones son del servidor. **Dos propuestas distintas en un turno** → ninguna; se responde pidiendo que el cliente elija una (texto del servidor);
   - si el efecto es `Derivar` y `entrantes = SOLO_HUMANO`, antes de derivar se guarda como entrante el texto **saneado** del turno, sin `wamid`, para que la bandeja muestre qué pidió el cliente (V2-DA-83);
   - el texto del modelo pasa por las guardas (§5); guarda el borrador (§3.4), liquida el uso (§6) y marca `HECHO` vaciando el texto.
6. **Fallos** (tiempo agotado, error del proveedor, `refusal`, `max_tokens`, JSON inválido, límite a mitad, reclamo perdido): transacción final con las mismas comprobaciones → respuesta de **menús** («No pude entenderte bien; elige una opción») → `FALLIDO` con motivo, sin texto. Una petición HTTP ya iniciada no se puede retirar (límite aceptado).
7. **Pendientes abandonados:** la retención termina `FALLIDO` (sin respuesta) los `PENDIENTE` con más de 15 min, aunque la IA esté desactivada, y vacía su texto.

### 3.4 Borrador
`conversacion.estado.ia` guarda, escritos por el servidor, **ids internos validados** de servicio y profesional y el día (nunca alias, franjas ni texto). El turno siguiente vuelve a consultar la disponibilidad y emite alias nuevos (V2-RN-12). Se vacía con el resto del estado (24 h, menú, operación ejecutada, derivación).

## 4. Contrato de tipos (paquete `pe.barberturno.conversation.ia`)
Los escribe Claude en el commit de contrato. **Nadie cambia una firma sin pedirlo al coordinador.**
### 4.1 Puerto
- `ModeloConversacional.responder(PeticionModelo)` → `RespuestaModelo`; lanza `FalloModelo(MotivoFalloModelo, Optional<UsoModelo> uso)` (`NO_CONFIGURADO`, `TIEMPO_AGOTADO`, `PROVEEDOR`, `LIMITE_PROVEEDOR`, `RECHAZO`, `SALIDA_INVALIDA`).
- `PeticionModelo(String instrucciones, String contexto, List<MensajeModelo> mensajes, List<HerramientaIA> herramientas)`.
- `MensajeModelo` (*sealed*): `DelCliente(texto)`, `DelAsistente(Optional<String> texto, List<LlamadaHerramienta> llamadas, ContinuacionProveedor continuacion)`, `ResultadosHerramientas(List<ResultadoHerramienta>)`.
- `ContinuacionProveedor`: valor **opaco** del adaptador (bloques del asistente en su orden, pensamiento incluido); solo vive durante el turno; nunca se registra ni se persiste.
- `RespuestaModelo(Optional<String> texto, List<LlamadaHerramienta> llamadas, ContinuacionProveedor continuacion, UsoModelo uso, String parada)`.
- `LlamadaHerramienta(String id, HerramientaIA herramienta, String argumentosJson)` (JSON como texto: los nodos Jackson 2 del SDK y los Jackson 3 de la aplicación no se mezclan).
- `ResultadoHerramienta(String llamadaId, String json, boolean error)`.
- `UsoModelo(String modelo, long entrada, long salida, long cacheLeida, long cacheEscrita, long duracionMs)`.

### 4.2 Herramientas
`HerramientaIA` (nombre de API, descripción y esquema JSON **estricto**: objetos cerrados, todos los campos en `required`, opcionales que admiten `null`):

| Herramienta | Argumentos |
|---|---|
| `listar_servicios` | — |
| `listar_profesionales` | — |
| `buscar_disponibilidad` | `servicio` (alias), `profesional` (alias o `null` = cualquiera), `desde`, `hasta` (`yyyy-MM-dd`, ≤ 7 días, no invertido, desde hoy), `horaDesde`, `horaHasta` (`HH:mm` inclusivas o `null`), `cita` (alias de una cita para reprogramar o `null`; si hay cita, se ignoran servicio y profesional: mismo profesional y duración, DA-21) |
| `mis_citas` | — |
| `proponer_reserva` | `franja` (alias de una franja de creación) |
| `proponer_reprogramacion` | `cita` (alias), `franja` (alias de una franja de cambio de esa cita) |
| `proponer_cancelacion` | `cita` (alias) |
| `derivar_a_persona` | `motivo` (`QUEJA`, `PETICION`, `FUERA_DE_ALCANCE`, `SIN_AVANCE`) |

### 4.3 Ejecución y alias (Codex)
- `HerramientasReserva.ejecutar(ContextoTurno, LlamadaHerramienta)` → `EjecucionHerramienta(ResultadoHerramienta paraModelo, Optional<EfectoTurno> efecto)`.
- `ContextoTurno` (mutable, de un solo turno): negocio, ficha (o vacía), hoy en Lima, y la **tabla de alias** (`s1`, `p1`, `c1`, `f1`…) ligada a ids internos, inicio, fin, finalidad (`CREAR` o `CAMBIO` de una cita) y profesional. Los alias de otro turno no existen.
- Un alias desconocido, de otra finalidad o de otro turno → `is_error` con código y mensaje genéricos, sin el argumento recibido, sin SQL ni nombres ajenos.
- Franjas devueltas: como máximo 20 por llamada (`FranjaOfrecida(alias, inicio, fin, profesional)`).
- `EfectoTurno` (*sealed*): `Proponer(TipoOperacion, datos validados)`, `Derivar(MotivoDerivacion)`, `SolicitarConsentimientoReserva(franja validada)`, `SolicitarConsentimientoDerivacion(MotivoDerivacion)`, `MostrarFranjas(List<FranjaOfrecida>)`.

### 4.4 Resto
- `GuardasSalida` (Gemini) → `ResultadoGuardas`: `Aceptado(texto)` | `Sustituido(MotivoGuarda)` (`HORARIO_NO_OFRECIDO`, `AFIRMA_OPERACION`, `DATOS_PROHIBIDOS`, `VACIO`, `DEMASIADO_LARGO`).
- `ComposicionTurnoIA` (Gemini): lista interactiva de franjas de varios días, agrupada por día (título de sección corto: «vie 23/10»), máx. 10 filas; cada fila: título = hora de inicio («17:30»), descripción = **fecha completa con el año** y profesional («viernes 23 de octubre de 2026 · Carlos», ≤ 72), que cumple V2-RN-12 (la fecha completa no cabe en los 24 caracteres de un título de sección); y los textos del servidor de §3.3 (aclarar entre dos propuestas, fallo).
- `InstruccionesAsistente` (Claude): texto fijo del sistema (estable y cacheable).
- `ConstructorContextoModelo` (Codex): §7.
- `TurnosIA` (Codex): `encolar(conversacionId, revision, texto)` (lo llama el orquestador), `procesarPendientes()` (trabajador).
- `LimitesIA` (Codex): `disponible(conversacionId)`, `reservar(turnoId, numeroLlamada)`, `liquidar(reserva, uso)`, `liberar(reserva)`.
- `ObservadorEvaluacion` (Claude, interfaz; implementación vacía en producción y de registro en la evaluación): texto antes de las guardas, franjas, llamadas, uso y tiempos.
- `IaPropiedades` (Claude, `barberturno.ia`): `habilitada`, `clave`, `modelo`, `esfuerzo`, `urlBase`, `tiempoLlamada`, `plazoTurno`, `maxLlamadas`, `maxHerramientas`, límites por defecto, precios por modelo, intervalo y lote del trabajador.

## 5. Guardas y composición (V2-RN-11/12)
> **Sustituido en el camino de salida por §13 (V2-DA-86, 05/11/2026):** el texto libre del modelo ya no se envía al cliente; las guardas quedan como indicador heurístico de la evaluación.
- **Las franjas que ve el cliente las compone siempre el servidor** (`ComposicionTurnoIA`), con fecha completa. El texto del modelo **no** debe contener horas ni fechas; las guardas son la red de seguridad:
  - **horarios**: **cualquier** referencia a una fecha o una hora (días, meses, «hoy», «mañana», «el viernes», «23/10», `2027-01-01`, «5», «5 y media», `17h`, `5 p. m.`) → `HORARIO_NO_OFRECIDO`, sin validar combinaciones: las franjas solo las muestra la composición del servidor (V2-DA-85);
  - **afirmaciones de operación** en cualquier persona y tiempo («reservé», «reservamos», «se reservó», «quedó confirmada», «agendada», «cancelé», «reprogramé», «te esperamos el…») → `AFIRMA_OPERACION`; no las preguntas ni las negaciones (V2-DA-85);
  - **datos prohibidos**: teléfonos, correos, `wa_id`, códigos `BT-…`, alias internos (`s1`, `f3`…), **cualquier enlace** y **cualquier importe** (`S/`, «soles», `PEN`, `$`): los precios y el aviso de privacidad solo los envía el servidor → `DATOS_PROHIBIDOS`; teléfonos y correos con `SaneadorTexto.contieneDatoPersonal` (V2-DA-85);
  - vacío o más de 1024 caracteres → `VACIO` / `DEMASIADO_LARGO`.
- Si se sustituye: se envía la composición del servidor (franjas del turno o menú) con un texto neutro.
- La medición de la evaluación cuenta las sustituciones **antes** de enviar (indicador de calidad).

## 6. Disponibilidad, límites y costes (V2-RN-14, V2-DA-79)
- **IA disponible**: clave y modelo configurados, `barberturno.ia.habilitada`, `negocio.ia_habilitada` y ningún límite alcanzado.
- **Mensajes entrantes por minuto** (V2-RN-14), por conversación: lo cuenta `RegistroEntradaWhatsApp.registrar` (Claude) en el mismo `UPSERT`, con minuto civil del reloj del servidor (`conversacion.minuto_entradas`, `entradas_minuto`); por encima del límite, el texto libre no va a la IA (camino de V2-11). Un reintento tras *rollback* no cuenta dos veces (se revierte con todo).
- **Llamadas por día y conversación** y **presupuesto mensual por negocio**: antes de cada HTTP se **reserva** una fila en `llamada_ia` (clave única turno + número de llamada) con el coste máximo (`max_tokens` y la entrada estimada por los precios configurados), bajo un bloqueo consultivo por negocio; después se **liquida** con el uso real (también si hubo fallo con uso conocido). Coste desconocido (tiempo agotado, caída) → se mantiene la reserva conservadora. Fecha contable en Lima; coste en micro-USD con `BigDecimal` y redondeo hacia arriba; fórmula por modelo: entrada no cacheada, salida, lectura y escritura de caché, sin doble conteo.
- **Al superar un límite**: camino de V2-11 y, si es el presupuesto del negocio, **un** aviso diario al PROPIETARIO: marcador `negocio.ia_aviso_limite_en date` reservado de forma atómica, aviso `AccionAuditoria.LIMITE_IA` ligado a la conversación que lo alcanzó.

## 7. Contexto mínimo (V2-RNF-05)
`ConstructorContextoModelo` (lista blanca):
- negocio: nombre; catálogo activo con **alias**, nombre, duración y precio; profesionales activos con alias y `nombre_visible` (el que el negocio publica en su página);
- fecha y hora actuales en Lima con el día de la semana;
- cliente: **nombre de pila** (primer término del nombre de la ficha, o nada) y si tiene ficha;
- borrador (§3.4) traducido a alias del turno;
- historial: como máximo los 6 últimos mensajes **de asistente y sistema** de la conversación posteriores al último vaciado del estado, saneados; nunca mensajes `PERSONA`, contenido retirado, selecciones, *nonces* ni cuerpos crudos;
- texto del cliente **saneado**: teléfonos y correos sustituidos por `[dato]`.

Excepción explícita a `16-…` §2.5: el texto de un cliente **sin ficha** se guarda en `turno_ia.texto` mientras el turno está pendiente (como máximo 15 min, §3.3.7) y se vacía al terminar. P2-06 debe cubrirlo (transferencia al proveedor de IA, D2/D7).

## 8. Modelo de datos: V117 (Codex)
- `conversacion`: `revision bigint` y su disparador; `minuto_entradas timestamptz`, `entradas_minuto smallint`; `GRANT UPDATE` de las dos últimas.
- `turno_ia` (negocio, RLS `ENABLE` + `FORCE`, `USING`/`WITH CHECK`): `id`, `negocio_id`, `conversacion_id` (FK compuesta), `revision`, `texto varchar(2000)`, `estado` (`PENDIENTE`, `HECHO`, `DESCARTADO`, `FALLIDO`), `motivo varchar(20)`, `intentos smallint CHECK (0..3)`, `reclamado_hasta`, `creado_en`, `actualizado_en`; `CHECK ((estado = 'PENDIENTE') = (texto IS NOT NULL))`; índice parcial único de un `PENDIENTE` por conversación; índices de cola `(negocio_id, reclamado_hasta) WHERE PENDIENTE`, de conversación y de terminados; `SELECT`, `INSERT`, `UPDATE` de columnas operativas; `DELETE` con política restrictiva (terminados, o de una conversación de ficha anonimizada).
- `llamada_ia` (negocio, RLS forzada): `turno_id`, `numero`, `conversacion_id`, `dia` (Lima), `modelo`, tokens, `coste_reservado_micro_usd`, `coste_micro_usd`, `estado` (`RESERVADA`, `LIQUIDADA`); `UNIQUE (turno_id, numero)`; se borra con su conversación (purga y anonimización), después de sumar su coste al agregado.
- `uso_ia` (negocio, RLS forzada): **agregado por `(negocio_id, dia)`** (corrige `04-…` §3: sin `conversacion_id`) con llamadas, tokens y `coste_micro_usd` no negativos; `INSERT … ON CONFLICT DO UPDATE`; sin `DELETE`. Conserva el gasto aunque se purgue o anonimice la conversación.
- `negocio`: `ia_habilitada boolean NOT NULL DEFAULT false`, `ia_presupuesto_mensual_usd numeric(8,2)`, `ia_llamadas_dia_conversacion integer`, `ia_entradas_minuto integer` (anulables, `CHECK` > 0), `ia_aviso_limite_en date`; permisos por columna como en V112 (sin edición web en el MVP).
- `notificacion`/`AccionAuditoria`: tipo `LIMITE_IA`.
- Retención y anonimización (Codex, ficheros de X11): fases para `turno_ia` y `llamada_ia`; la anonimización termina los turnos de la conversación bajo su bloqueo antes de borrarlos.
- Pruebas que cambian: `EsquemaIT`, `EsquemaV2IT`, `InstalacionNuevaIT`, `ActualizacionV1IT`, `ConversacionV115IT`, `ConfiguracionNegocioEsquemaPruebas` y `LimpiezaBaseDatos` (truncar `uso_ia`).

## 9. Criterios de aceptación
| # | Criterio | Responsable de la prueba |
|---|---|---|
| CI-1 | Texto libre con IA disponible → turno encolado (y el anterior pendiente descartado), sin respuesta en la entrega; botones y palabras clave → camino determinista; límite por minuto → camino de V2-11 | Claude |
| CI-2 | Reclamo con ficha; un trabajador con la concesión vencida no aplica nada; revisión cambiada (otro mensaje, botón, derivación, personal, retorno) → `DESCARTADO` sin salida ni operación; conversación borrada → terminado sin recrearla | Codex |
| CI-3 | Propuesta creada solo en la transacción final; una por turno; dos distintas → ninguna y aclaración; derivación con precedencia; «sí» tras una propuesta de IA confirma como en V2-10 | Codex |
| CI-4 | Herramientas: alias opacos para todo; alias desconocido, de otro turno, de otra finalidad, de otro negocio o de otra ficha → `is_error` genérico; ninguna escribe reservas; límites de herramientas y franjas | Codex |
| CI-5 | *(Retirado como garantía por V2-DA-86: las guardas ya no están en la salida; quedan como indicador de la evaluación.)* La composición de franjas cumple los límites de Meta y da fecha completa | Gemini |
| CI-6 | Límites: llamadas por día, presupuesto mensual con reserva previa (dos turnos concurrentes no superan el saldo), liquidación también con fallo, un aviso diario al propietario; por negocio | Codex |
| CI-7 | Fallos del modelo (tiempo, 429, 500, `refusal`, `max_tokens`, JSON inválido) → menús y `FALLIDO`; pendientes abandonados terminados por la retención | Codex |
| CI-8 | Adaptador contra el servidor local: herramientas estrictas, `cache_control`, modelo, esfuerzo, sin muestreo ni `tool_choice` forzado, continuación intacta (pensamiento), tokens de caché; la petición no contiene teléfono, correo, `wa_id`, ids internos ni mensajes `PERSONA` | Codex |
| CI-9 | Sin clave, la aplicación arranca, la IA queda desactivada y todo funciona con menús | Codex |
| CI-10 | V117: permisos exactos, RLS forzada, `CHECK`, disparador de revisión, `UPSERT` bajo RLS con el rol real (sin contexto y con otro negocio → falla) | Codex |
| CI-11 | `ConversacionGuionesIT`: los 8 casos mínimos de `08-…` §4.1 + derivación por herramienta + límite + dos negocios con nombres canario + turno descartado por un mensaje nuevo | Gemini (guiones) y Claude (ejecución) |
| CI-12 | Arnés en vivo: compila, se niega a ejecutar sin clave, sin base desechable o sin tope de gasto; calcula las métricas de `08-…` §4.2 sobre transcripciones del adaptador falso | Gemini |
| CI-13 | `verify` completo en verde con JaCoCo y Javadoc; registros sin contenido, clave ni continuación | Claude |
| CI-14 | V2-DA-86: con cualquier texto del proveedor (incluidas paráfrasis de confirmación, inyecciones, importes, enlaces, fechas y horas), lo que recibe el cliente en `mensaje_whatsapp` y lo que muestra la bandeja procede solo de `RespuestasAsistente` o de V2-10; ninguna subcadena del texto del proveedor aparece | Gemini (RD-2) |
| CI-15 | Terminal `responder_al_cliente`: orden, duplicada, inválida, alias, «cualquiera», degradaciones de §13.2.3 y precedencia de efectos; citas solo de la ficha revalidada; contexto sin nombre de pila ni historial textual | Codex (SE) |
| CI-16 | Una propuesta de IA confirmada por el cliente produce `TextosConversacion.resultado` con el código, la fecha y la hora reales de la base; antes de confirmar, ningún mensaje afirma la reserva | Gemini (RD-2) |
| CI-17 | La métrica «Confirmaciones falsas» detecta los ejemplos conocidos en lo que recibe el cliente con un criterio estructural (procedencia y operación ejecutada), no solo con el detector léxico | Claude (EV) |

## 10. Reparto, ramas y revisión cruzada
| Bloque | Responsable | Worktree y rama | Archivos (alcance) | Revisa |
|---|---|---|---|---|
| 0 · Contrato | Claude | `D:\Proyectos\BarberTurno-v2-12` · `tarea/V2-12-conversacion-ia` | este documento, decisiones, *backlog*, tipos de §4, `InstruccionesAsistente`, `ObservadorEvaluacion`, `IaPropiedades`, YAML | Codex (consulta 127) |
| XI · Turnos, herramientas, límites, contexto, adaptador, V117 | Codex | `D:\Proyectos\BarberTurno-v2-12-backend` · `…-backend` | `pom.xml` (dependencia y perfil `evaluacion-en-vivo`); `V117`; `conversation/ia/` salvo lo de Claude y Gemini; retención y anonimización (sus archivos de X11); `AccionAuditoria`, avisos; sus pruebas y las de esquema de §8 | Gemini Argon (*diff-first*) + Claude |
| CI · Enrutado y entradas por minuto | Claude | rama de la tarea | `OrquestadorConversacion` (encolar), `RegistroEntradaWhatsApp` (contador por minuto), sus pruebas | Codex (solo lectura) |
| GI · Guardas, composición, falso, guiones y evaluación | Gemini | `D:\Proyectos\BarberTurno-v2-12-gemini` · `…-gemini` | `conversation/ia/{GuardasSalida,ResultadoGuardas,MotivoGuarda,ComposicionTurnoIA}` y sus pruebas; `src/test/.../ia/` (adaptador falso, lector SnakeYAML seguro de guiones, `ConversacionGuionesIT`, arnés en vivo y correctores, observador de registro); `src/test/resources/{conversaciones,evaluacion}/`; `docs/v2/pruebas/v2-13-informe-plantilla.md` | Codex (solo lectura) + Claude |

- **Migraciones y `pom.xml`:** solo Codex. **Contrato y configuración compartida:** solo Claude.
- **Orden:** contrato → GI y CI en paralelo con la integración de V2-11 → unión de V2-11 terminada en esta rama → XI.

## 11. Pendientes del responsable (no se simulan)
- **P2-03:** clave (`BT_IA_API_KEY` en `D:\Proyectos\BarberTurno-v2\.local\barberturno.env`, la escribe el responsable), proveedor y modelo tras el informe.
- **Presupuesto** de la evaluación en vivo (propuesta de `09-costes.md` §3: ≤ 10 USD; primera ronda con Haiku 5.5 y Sonnet 5.5).
- **Revisión del conjunto** de conversaciones y del 20 % de prueba.
- **P2-06:** el aviso debe mencionar la transferencia al proveedor de IA y el texto temporal sin ficha (§7).
- Edición de `ia_habilitada` y de los límites por el propietario: fuera del MVP (*Could*).

## 12. Consulta de diseño de Codex (encargo 127, 04/11/2026): «Con propuestas», 22, aceptadas
| # | Propuesta | Decisión |
|---|---|---|
| 1 | `ultima_entrada_en` no identifica una entrada | `conversacion.revision` con disparador `BEFORE UPDATE` (§3.1) |
| 2 | Trabajador con la concesión vencida | Ficha de reclamo = `intentos`; plazo total y renovación (§3.3.1) |
| 3 | Comprobaciones finales y orden de bloqueos | §3.3.5 |
| 4 | El historial pierde el pensamiento | `ContinuacionProveedor` opaca (§4.1) |
| 5 | Validación en seco | `prepararCreacionFicha` / `prepararCambioFicha`, transacciones cortas no `readOnly` (§3.3.4) |
| 6 | El resultado no transporta la decisión | `EjecucionHerramienta` + `EfectoTurno` (§4.3) |
| 7 | «Última propuesta válida» ambigua | Una por turno; dos distintas → aclaración; derivación primero (§3.3.5) |
| 8 | Consentimiento en la aplicación final | Efectos `SolicitarConsentimiento*` con los pendientes de V2-10/V2-11 (§4.3) |
| 9 | Esquemas y referencias | Alias opacos para todo; propuestas solo con alias (§4.2–4.3) |
| 10 | Disponibilidad de varios días y reprogramación | `cita` en `buscar_disponibilidad`; límites de rango, horas, herramientas y franjas; composición de varios días (§4.2, §4.4) |
| 11 | Versión del SDK | Se comprueba en Maven Central al implementar (con red) y se anota fuente y fecha |
| 12 | Jackson 2 y 3 | Mapper del SDK encapsulado; argumentos como JSON en texto (§4.1) |
| 13 | `fallbacks` solo en la API beta | Se omite (V2-DA-80); `refusal`/`max_tokens` fallan antes de ejecutar herramientas; cliente condicional y cerrado al parar |
| 14 | Costes solo al terminar | Reserva previa en `llamada_ia`, liquidación siempre, coste desconocido conservador (§6) |
| 15 | Fuente del límite por minuto | Mensajes entrantes por minuto contados en `registrar` (§6) |
| 16 | Aviso diario sin destino | Marcador atómico en `negocio` + aviso `LIMITE_IA` ligado a la conversación (§6) |
| 17 | V117 imprecisa | §8 |
| 18 | Purga y anonimización con turnos | §3.3.7 y §8 |
| 19 | Contexto mínimo | Lista blanca, saneado, sin mensajes `PERSONA` (§7); excepción explícita del texto sin ficha |
| 20 | Alias y franjas heredados | El borrador guarda ids validados; disponibilidad siempre nueva; franjas compuestas por el servidor (§3.4, §5) |
| 21 | V2-13 | SnakeYAML seguro, observador, perfil Maven aparte, 96 + 24 (§1, §4.4) |
| 22 | Solapes y huecos de archivos | §10 |

## 13. Salida estructurada y respuestas deterministas (V2-DA-86, 05/11/2026) — **sustituye §5 en el camino de salida**
**Problema:** con §5, el texto libre del modelo llegaba al cliente si pasaba las guardas (cuerpo de la lista de franjas o mensaje de texto). Las guardas son patrones: una paráfrasis («Carlos ya te tiene un hueco») podía prometer una cita inexistente. **Decisión:** el texto libre del proveedor **no llega nunca al cliente** por ningún camino (WhatsApp, *outbox* `mensaje_whatsapp`, bandeja, vista previa, avisos). El modelo solo elige **intenciones y alias**; el servidor valida y compone el mensaje con los menús y textos deterministas de V2-10.

### 13.1 Qué produce el modelo
- Las herramientas de §4.2 (sin cambios) y una nueva herramienta **terminal** `responder_al_cliente` con esquema estricto `{"intencion": enum, "falta": enum | null, "servicio": alias | null, "profesional": alias | "cualquiera" | null}` (138-2: comunica lo elegido antes de buscar disponibilidad):
  - `IntencionRespuesta`: `PEDIR_DATO`, `MOSTRAR_SERVICIOS`, `MOSTRAR_PROFESIONALES`, `MOSTRAR_CITAS`, `SIN_DISPONIBILIDAD`, `FUERA_DE_ALCANCE`, `SALUDO`.
  - `DatoFaltante` (solo con `PEDIR_DATO`): `SERVICIO`, `PROFESIONAL`, `DIA`, `CITA`.
- No hay ningún campo de texto libre. El texto que el proveedor devuelva igualmente (`RespuestaModelo.texto`) se **descarta para la salida, la persistencia y los registros**: solo lo ve el observador de la evaluación (§4.4) y la continuación opaca del mismo bucle (pensamiento y firmas intactos, 138-6). `tool_choice` sigue en automático: forzar la terminal impediría consultar herramientas.

### 13.2 Qué valida el servidor (Codex)
1. `responder_al_cliente` cierra el bucle (138-3): se examinan **todas** las llamadas de la respuesta antes de despacharlas; se ejecutan primero las no terminales, aunque aparezcan después; no hay otra llamada al modelo. La terminal cuenta dentro de `maxHerramientas`. Terminal duplicada, esquema inválido, alias desconocido o `falta` incoherente → se cierra con los efectos válidos precedentes o con `NEUTRO`, sin seguir el bucle. `observador.herramienta` no se llama para la terminal; sí `observador.decision`.
2. **Precedencia:** derivación > consentimiento > propuesta > franjas > `responder_al_cliente` > respuesta por defecto (`MotivoMenu.NEUTRO`: menú principal). La intención nunca anula un efecto.
3. **Coherencia con los datos del servidor** (degradaciones deterministas, sin error visible):
   - `SIN_DISPONIBILIDAD` solo si en **este** turno la **última** `buscar_disponibilidad` válida devolvió `[]` (se registra explícitamente; hoy no produce efecto); si no → `NEUTRO`.
   - `PEDIR_DATO(PROFESIONAL)` o `MOSTRAR_PROFESIONALES` sin servicio conocido → `PEDIR_DATO(SERVICIO)`; `PEDIR_DATO(DIA)` sin servicio → `PEDIR_DATO(SERVICIO)`; sin profesional **elegido** → `PEDIR_DATO(PROFESIONAL)`; con «cualquiera» → días con `barberoId = 0` (g102-1).
   - Catálogo o profesionales vacíos, o una selección desactivada entre la consulta y el cierre → `menus.menuPrincipal(textos.sinOpciones())`, nunca una lista vacía (138-5, g102-2).
   - `MOSTRAR_CITAS` / `PEDIR_DATO(CITA)`: las citas se cargan **solo** con la ficha revalidada de la conversación (`citasFuturas(ficha)`), nunca con un identificador del modelo; sin ficha o sin citas → el mismo texto «sin citas» con el menú principal (no revela si existe una ficha).
   - «Conocido» = validado en este turno o en el borrador (§3.4), bajo RLS del negocio.
4. Carga los datos de la respuesta en la transacción final bajo RLS (`DatosRespuesta`) y llama a `RespuestasAsistente`; encola **solo** lo que esta devuelve o lo que ya compone V2-10 (`menus.confirmacion` con `textos.resumen`).
5. **Confirmaciones:** el turno de IA solo puede **proponer** (operación `VIGENTE` con *nonce*, protocolo de V2-10, sin una segunda implementación de las reglas). El único mensaje que afirma una reserva, un cambio o una cancelación es `TextosConversacion.resultado(ResultadoOperacion)`, que emite el flujo de V2-10 **después** de ejecutar la operación que el cliente confirmó (botón o «sí» determinista, `06-flujos.md`), con el código real (V2-RN-11).
6. **Mensajes anteriores (138-4):** V2-12 nunca se desplegó (no está en `v2` ni en producción); en `dev` y `test` la base de revisión se vacía en cada sesión. No hay mensajes antiguos con texto del modelo que tratar; si apareciera alguno en una base de desarrollo, no se reenvía (la *outbox* solo envía lo encolado por `SalidaWhatsApp`).

### 13.3 Composición (Gemini) — `RespuestasAsistente`
| Entrada | Mensaje |
|---|---|
| `PEDIR_DATO(SERVICIO)`, `MOSTRAR_SERVICIOS` | `menus.servicios(textos.elegirServicio(), servicios, 0)` (precios del catálogo del servidor) |
| `PEDIR_DATO(PROFESIONAL)`, `MOSTRAR_PROFESIONALES` | `menus.profesionales(textos.elegirProfesional(servicio), …)` |
| `PEDIR_DATO(DIA)` | `menus.dias(textos.elegirDia(), servicio, profesional o 0 si «cualquiera», hoy)` |
| `PEDIR_DATO(CITA)`, `MOSTRAR_CITAS` | `menus.citas(textos.misCitas(), citas)`; sin citas: `menus.menuPrincipal(textos.sinCitas())` |
| `SIN_DISPONIBILIDAD` | `menus.menuPrincipal(textos.sinOpciones())` |
| `FUERA_DE_ALCANCE` | `menus.menuPrincipal(<texto fijo de alcance de `ComposicionTurnoIA`>)` (g102-3: `noSoportado()` habla de adjuntos) |
| `SALUDO` | `menus.menuPrincipal(textos.bienvenida(negocio))` |
| franjas (efecto) | `ComposicionTurnoIA.franjas(<texto fijo del servidor>, franjas, hoy)` |
| `MotivoMenu.NEUTRO`, `FALLO`, `VARIAS_PROPUESTAS`, `NO_DISPONIBLE` | menú principal con el texto fijo correspondiente |

Mismos códigos de selección que V2-10: pulsar una opción continúa por el flujo guiado. Ningún parámetro de entrada procede del texto del modelo.

### 13.4 Tipos (contrato, Claude; nadie cambia una firma sin pedirlo al coordinador)
```java
enum IntencionRespuesta { PEDIR_DATO, MOSTRAR_SERVICIOS, MOSTRAR_PROFESIONALES, MOSTRAR_CITAS, SIN_DISPONIBILIDAD, FUERA_DE_ALCANCE, SALUDO }
enum DatoFaltante { SERVICIO, PROFESIONAL, DIA, CITA }
enum MotivoMenu { NEUTRO, FALLO, VARIAS_PROPUESTAS, NO_DISPONIBLE }
record DecisionRespuesta(IntencionRespuesta intencion, Optional<DatoFaltante> falta)   // ya validada y degradada
sealed interface SeleccionProfesional { record SinElegir(); record Cualquiera(); record Concreto(ProfesionalOpcion profesional); }   // 138-1
record DatosRespuesta(String negocio, LocalDate hoy, Optional<ServicioOpcion> servicio, SeleccionProfesional profesional,
                      List<ServicioOpcion> servicios, List<ProfesionalOpcion> profesionales, List<CitaOpcion> citas)
sealed interface MensajeAsistente { record Texto(String texto); record Interactivo(ObjectNode contenido); }
@Component class RespuestasAsistente {
  MensajeAsistente responder(DecisionRespuesta decision, DatosRespuesta datos);
  MensajeAsistente franjas(List<FranjaOfrecida> franjas, LocalDate hoy);
  MensajeAsistente menu(MotivoMenu motivo);
}
// ObservadorEvaluacion, método nuevo con implementación vacía:
default void decision(long turnoId, Optional<DecisionRespuesta> decision, Optional<String> textoDescartado) { }
```

### 13.5 Guardas
`GuardasSalida` deja de estar en el camino de salida (no queda texto libre que revisar). Se conserva **solo como indicador heurístico de la evaluación** sobre el texto descartado («qué habría dicho»), nunca como garantía.

### 13.6 Contexto mínimo (sustituye parte de §7)
Dejan de enviarse al proveedor el **nombre de pila** del cliente (ya no hay texto personalizado) y el **historial de textos** del asistente (podía contener códigos de cita y fechas). En su lugar, `ultimaRespuesta`: un valor de lista cerrada derivado de la respuesta **realmente aplicada** en el turno anterior (`MOSTRAR_FRANJAS`, `PROPUESTA`, `CONSENTIMIENTO`, `DERIVAR`, `NEUTRO`, el nombre de la intención o `PEDIR_DATO:<DATO>`), guardado en `conversacion.estado.ia.ultima` sin migración (138-7). `guardarBorrador` admite selecciones parciales (`ia.servicio`, `ia.profesional` con `0` = cualquiera, `ia.dia`) y el borrador se revalida al cargar; los vaciados existentes (menú, ejecución, derivación, inactividad) se mantienen. Lo que sí se envía: `15-decisiones-pendientes-p2-05-p2-06.md` §1.4.

### 13.7 Reparto (sin solapes de archivos)
| Bloque | Responsable | Rama · worktree | Archivos | Revisa |
|---|---|---|---|---|
| Contrato y tipos de 13.4 | Claude | `tarea/V2-12-conversacion-ia` · `-v2-12` | este documento, `07-decisiones`, tipos de 13.4 (`RespuestasAsistente` como esqueleto), `ObservadorEvaluacion` | Codex y Gemini (consulta) |
| SE · contrato estructurado, validación y conexión | Codex | `tarea/V2-12-salida-estructurada` · `-v2-12-se` | `HerramientaIA`, `HerramientasReserva`, validador nuevo, `TurnosIAService`, `ConstructorContextoModelo`, `InstruccionesAsistente`, `ModeloAnthropic` y sus pruebas (`TurnosIAIT`, `ModeloAnthropicTest`…) | Gemini Argon |
| RD · composición, canales y adversarias | Gemini | `tarea/V2-12-respuestas-deterministas` · `-v2-12-rd` | RD-1: cuerpo de `RespuestasAsistente`, `ComposicionTurnoIA`, `RespuestasAsistenteTest`; RD-2: `SalidaClienteIT` (nueva), `ModeloConversacionalFalso`, guiones y `ConversacionGuionesIT` | Codex (solo lectura) |

**Orden:** contrato → RD-1 (sin dependencias) → unión en `tarea/V2-12-conversacion-ia` → SE (Codex) y RD-2 (Gemini) en paralelo → unión → pruebas de canal → revisiones cruzadas. Consultas de acuerdo: 138 (Codex) y g102 (Gemini), 05/11/2026.
| EV · evaluación, métricas y conjunto | Claude | `tarea/V2-12-evaluacion` · `-v2-12-eval` | `Correctores`, `CorrectoresTest`, `EvaluacionEnVivo*`, `FixturesEvaluacion`, convenciones del conjunto | Codex (solo lectura) |
