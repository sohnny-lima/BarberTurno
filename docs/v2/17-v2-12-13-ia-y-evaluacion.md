# BarberTurno v2 · 17 · V2-12 y V2-13 · Conversación con IA y evaluación de conversaciones

> Autorizada por el responsable el 04/11/2026 (cierre técnico del MVP). Rama `tarea/V2-12-conversacion-ia` desde el HEAD de V2-11 (necesita la derivación y el modo humano). Se une en `tarea/V2-MVP-revision`. **Sin integrar en `v2` ni publicar.** Clase **Crítica** (reservas, guardas, datos personales enviados a un proveedor, concurrencia).
>
> Requisitos: V2-RF-16, V2-RN-11/12/14, V2-RNF-05, V2-CP-08. Arquitectura: `04-arquitectura-datos.md` §7; `06-flujos.md` §7; `08-pruebas-migracion-reversion.md` §4; `09-costes.md` §3; V2-DA-12 y V2-DA-16. Decisiones nuevas V2-DA-77…82.
>
> **Bloqueos externos, que no se simulan:** **P2-03** (clave y modelo del proveedor: no hay `BT_IA_*` en el entorno), el **presupuesto** de la evaluación en vivo (V2-13) y la **revisión del conjunto** de 120 conversaciones por el responsable. Todo se construye y se prueba con el adaptador falso y con un servidor HTTP local que imita la API de Anthropic. **Sin clave, la IA queda desactivada y la conversación sigue con los menús guiados de V2-10/V2-11** (V2-RN-14).

## 1. Alcance
**V2-12 entra:**
1. Puerto `ModeloConversacional` con dos adaptadores: **falso programable** (pruebas y evaluación determinista) y **Anthropic** con el SDK oficial de Java (`com.anthropic:anthropic-java`, versión comprobada en Maven Central el día de la implementación y anotada en la arquitectura §2).
2. **Turno de IA fuera de la transacción de entrega** (V2-DA-77, §3): la entrega solo encola el turno; un trabajador lo resuelve sin bloqueos abiertos durante el HTTP y aplica el resultado en una transacción final que comprueba que no llegó otro mensaje.
3. Herramientas de lectura y de **propuesta** (`04-…` §7.2), ejecutadas por el servidor con el negocio y la ficha del contexto, nunca de los argumentos.
4. **Guardas de salida** (V2-RN-11/12, §5) y respuestas compuestas por el servidor para franjas, propuestas y resultados.
5. **Límites y costes** (V2-RN-14): `uso_ia`, límites por conversación y presupuesto mensual por negocio; al superarlos, menús o persona.
6. **Datos mínimos al proveedor** (V2-RNF-05).

**V2-13 entra (solo lo local):**
1. Suite determinista `ConversacionGuionesIT` en `verify` con guiones YAML y el adaptador falso (`08-…` §4.1).
2. **Arnés en vivo** listo para ejecutarse (perfil Maven aparte, fuera de `verify`), con correctores por reglas, tope de gasto y registro de tokens, latencia y transcripciones en `target/`.
3. **Borrador** del conjunto de ≥ 120 conversaciones (`backend/src/test/resources/evaluacion/conjunto-v1/`), marcado «BORRADOR pendiente de revisión del responsable», con el 20 % de prueba separado.
4. Plantilla del informe comparativo (`docs/v2/pruebas/v2-13-informe-plantilla.md`), sin resultados.

**No entra:** ejecutar el arnés contra el modelo real (P2-03 + presupuesto), elegir el modelo (decisión del responsable tras el informe), recordatorios (V2-14), adjuntos, voz, imágenes, *streaming* al cliente, «tono» evaluado por IA.

## 2. Cuándo actúa la IA
En modo `ASISTENTE`, tras el ciclo de vida y la interpretación de V2-10/V2-11:
1. Botones, selecciones de lista, palabras clave (`MENU`, `SI`, `NO`, `PERSONA`) → **camino determinista de siempre**, sin IA.
2. Texto libre (`OTRA`):
   - IA **disponible** (§6: configurada, negocio habilitado, dentro de los límites) → se encola un **turno de IA** y no se responde en la entrega;
   - no disponible → camino de V2-11 (no entendido; al segundo, derivación).
3. Sin ficha, la IA puede consultar (servicios, profesionales, franjas); **proponer** exige ficha: el servidor pide el consentimiento con la franja como pendiente (igual que V2-10 §7).
4. En modo `HUMANO`, nunca.

## 3. Turno de IA (V2-DA-77)
### 3.1 Encolar (en la transacción de entrega)
Inserta `turno_ia` (`PENDIENTE`) con `conversacion_id`, `entrada_en` (= la `ultima_entrada_en` registrada), el texto del cliente (recortado a 2000) y `intentos = 0`. El evento queda `PROCESADO`.

### 3.2 Resolver (trabajador, sin transacción durante el HTTP)
1. **Reclamar** con `FOR UPDATE SKIP LOCKED` y una concesión de 2 min (`reclamado_hasta`), por negocio con `EjecutorEnNegocio`, como el procesador de V2-08.
2. **Contexto** (transacción corta de lectura): catálogo activo del negocio, nombre de pila de la ficha (si hay), fecha y hora de Lima con el día de la semana, borrador de la conversación (§3.4) y los últimos mensajes **guardados** de la conversación (con D6 = `SOLO_HUMANO`, solo los salientes; nunca el cuerpo crudo de los eventos).
3. **Bucle del modelo**, como máximo `ia.max-llamadas-por-turno` (4) llamadas:
   - el modelo responde texto o pide herramientas;
   - las **de lectura** se ejecutan cada una en una transacción corta de solo lectura en el negocio;
   - las **de propuesta** solo **validan** en seco (misma validación que `proponer*`, sin escribir) y devuelven al modelo «propuesta preparada; el servidor enviará el resumen»; se recuerda la **última** propuesta válida;
   - `derivar_a_persona` termina el bucle.
4. **Aplicar** (transacción final): bloquea la conversación y comprueba `conversacion.ultima_entrada_en = turno.entrada_en` y `modo = ASISTENTE`.
   - Si cambió (llegó otro mensaje, se derivó o el personal intervino) → turno `DESCARTADO`, sin salida. El mensaje nuevo tiene su propio turno o su camino determinista.
   - Si no: aplica, en este orden, la derivación (`HERRAMIENTA`), o la propuesta (`OperacionesConversacion.proponer*`, que revalida y anula la vigente; resumen y botones **del servidor**), o la lista de franjas del turno (compuesta por el servidor) y el texto del modelo **tras las guardas** (§5). Guarda el borrador, suma `uso_ia` y marca el turno `HECHO` vaciando su texto.
5. **Fallos** (tiempo agotado, error del proveedor, `refusal`, salida truncada, JSON inválido, límite alcanzado a mitad): transacción final con las mismas comprobaciones y respuesta de **menús guiados** («No pude entenderte bien; elige una opción») → turno `FALLIDO` con un código, sin texto. Nunca falla en silencio.
6. **Concesión vencida** (trabajador caído): se reclama de nuevo una vez (`intentos ≤ 2`); después, `FALLIDO` con la respuesta de menús.

### 3.3 Por qué fuera de la transacción
La llamada al modelo dura segundos. Dentro de la entrega retendría la conexión, el bloqueo de la conversación y el del evento, y un error del proveedor revertiría y **repetiría** la llamada (coste) con los 5 reintentos de V2-08. Sigue la regla de V2-09: nada de HTTP con bloqueos abiertos. La propuesta real se crea **solo** en la transacción final, así que un turno descartado nunca deja una operación vigente que el cliente no ha visto (un «sí» posterior no confirmaría nada inesperado: V2-RN-10).

### 3.4 Borrador de la conversación
`conversacion.estado.ia` puede guardar, **escritos por el servidor a partir de los argumentos validados de las herramientas**, `servicioId`, `barberoId`, `dia` y `franja` (ids y fechas, nunca texto del cliente). Se vacía con el resto del estado (V2-10 §8: 24 h, menú principal, operación ejecutada) y al derivar. Amplía V2-DA-63 solo para la IA.

## 4. Contrato de tipos (paquete `pe.barberturno.conversation.ia`)
Los escribe Claude en el commit de contrato. **Nadie cambia una firma sin pedirlo al coordinador.**
- `ModeloConversacional`: `RespuestaModelo responder(PeticionModelo peticion)`; lanza `FalloModelo` (con `MotivoFalloModelo`: `NO_CONFIGURADO`, `TIEMPO_AGOTADO`, `PROVEEDOR`, `LIMITE_PROVEEDOR`, `RECHAZO`, `SALIDA_INVALIDA`).
- `PeticionModelo(String instrucciones, String contextoNegocio, List<MensajeModelo> historial, List<HerramientaIA> herramientas)`. `MensajeModelo` (*sealed*): `DelCliente(texto)`, `DelAsistente(texto, List<LlamadaHerramienta>)`, `ResultadoHerramienta(llamadaId, String json, boolean error)`.
- `RespuestaModelo(Optional<String> texto, List<LlamadaHerramienta> llamadas, UsoModelo uso, String parada)`; `LlamadaHerramienta(String id, HerramientaIA herramienta, JsonNode argumentos)`; `UsoModelo(entrada, salida, cacheLeida, cacheEscrita, duracionMs)`.
- `HerramientaIA`: `LISTAR_SERVICIOS`, `LISTAR_PROFESIONALES`, `BUSCAR_DISPONIBILIDAD`, `MIS_CITAS`, `PROPONER_RESERVA`, `PROPONER_REPROGRAMACION`, `PROPONER_CANCELACION`, `DERIVAR_A_PERSONA`, cada una con su nombre de API y su esquema JSON **estricto** (`additionalProperties: false`, todos los campos en `required`, opcionales como `null` admitido).
- `HerramientasReserva` (Codex): `ResultadoHerramienta ejecutar(ContextoTurno contexto, LlamadaHerramienta llamada)`. Los ids de franja que ve el modelo son **opacos** (`f1`, `f2`… del turno), no ids ni instantes internos (V2-RNF-05).
- `GuardasSalida` (Gemini): `ResultadoGuardas revisar(String texto, Set<FranjaOfrecida> franjasDelTurno, ZonedDateTime ahora)` → `Aceptado(texto)` | `Sustituido(MotivoGuarda)`; `MotivoGuarda`: `HORARIO_NO_OFRECIDO`, `AFIRMA_OPERACION`, `DATOS_PROHIBIDOS`, `VACIO`.
- `InstruccionesAsistente` (Claude): el texto fijo del sistema (estable y cacheable) y el bloque de contexto del negocio.
- `TurnosIA` (Codex): encolar, reclamar, resolver y aplicar (§3).
- `LimitesIA` (Codex): `Disponibilidad comprobar(long negocioId, long conversacionId)` → `Disponible` | `NoDisponible(MotivoLimite)`; `registrar(UsoModelo, ...)`.

## 5. Guardas (V2-RN-11/12)
- **Horarios:** toda hora («5», «17:30», «5 y media», «a las cinco de la tarde») y fecha («mañana», «el viernes», «23/10», «23 de octubre») del texto del modelo debe corresponder a una franja **devuelta por una herramienta en ese turno**; si no, el texto se descarta y se envía la lista de franjas del servidor (o, sin franjas, «Te muestro las opciones disponibles» + menú). La medición de la evaluación (`08-…` §4.2) cuenta los casos **antes** de la guarda.
- **Confirmaciones falsas:** se descarta el texto que afirme una operación hecha («reservé», «está confirmada», «quedó agendada», «cancelé», «reprogramé», «listo, te esperamos»…). Los resultados solo salen de `TextosConversacion` a partir de `ResultadoOperacion`.
- **Datos prohibidos:** teléfonos, correos, `wa_id`, códigos internos o enlaces que no sean el del aviso de privacidad → descartado.
- **Límites de Meta:** cuerpo ≤ 1024 en interactivos y ≤ 4096 en texto, con el recorte de V2-10.
- La propuesta y su resumen (fecha completa, servicio, profesional, precio) los compone **siempre** el servidor.

## 6. Disponibilidad, límites y costes (V2-RN-14, V2-DA-79)
- **IA disponible** si: hay clave (`BT_IA_API_KEY`) y modelo (`barberturno.ia.modelo`), `barberturno.ia.habilitada = true`, `negocio.ia_habilitada = true` y ningún límite alcanzado.
- **Límites** (valores por defecto en la configuración; anulables por negocio en columnas de `negocio`):
  - por conversación: llamadas al modelo por día (`20`) y textos libres por minuto (`6`);
  - por negocio: presupuesto mensual en USD (`5`), calculado con los tokens de `uso_ia` y los precios configurados por modelo (`barberturno.ia.precios.<modelo>`, USD por millón: entrada, salida, lectura y escritura de caché).
- **Al superarlos:** el texto libre va al camino de V2-11 (no entendido → derivación) y, si es el límite del negocio, un aviso interno al PROPIETARIO una vez al día.
- La configuración de `prod` no trae clave. En `dev` y `test`, `habilitada: false` salvo en las pruebas que usan el adaptador falso o el servidor local.

## 7. Adaptador de Anthropic (Codex, V2-DA-80)
- SDK oficial `com.anthropic:anthropic-java` (cliente `AnthropicOkHttpClient`), `messages().create`, **bucle manual** (no el *tool runner*, porque las herramientas se ejecutan con las reglas de §3).
- Herramientas con `strict(true)` y `additionalProperties: false`; `tool_choice` automático (los modelos actuales rechazan forzarlo).
- Modelo por configuración (`barberturno.ia.modelo`, por defecto `claude-opus-5-5`; la evaluación compara `claude-haiku-5-5`, `claude-sonnet-5-5` y `claude-opus-5-5`, `09-costes.md` §3). Esfuerzo configurable (`barberturno.ia.esfuerzo`, por defecto `low`, propio de un chat); sin parámetros de muestreo; sin desactivar el pensamiento.
- **Caché de instrucciones:** herramientas e instrucciones fijas primero, con `cache_control` al final del bloque estable; el contexto del negocio, la fecha y el historial después.
- `max_tokens` 2048; tiempo máximo por llamada configurable (15 s); reintentos del SDK 1.
- `stop_reason` `refusal` o `max_tokens` → `FalloModelo` (`RECHAZO` / `SALIDA_INVALIDA`): el turno responde con menús. Respaldo del servidor (`fallbacks`) **solo si el SDK lo admite sin rodeos**; si no, se documenta y se omite (el respaldo propio son los menús).
- Registra tokens (incluida la caché leída y escrita) y duración; **nunca** el contenido, la clave ni los argumentos.
- **Pruebas sin red:** servidor HTTP local (JDK `HttpServer`, sin dependencias nuevas) con `baseUrl` configurable: petición correcta (herramientas estrictas, caché, modelo, sin teléfono, correo ni `wa_id`), respuesta con herramientas, `refusal`, 429, 500 y tiempo agotado.

## 8. Modelo de datos: V117 (Codex)
- `turno_ia` (negocio, RLS `ENABLE` + `FORCE`): `id`, `negocio_id`, `conversacion_id` (FK compuesta), `entrada_en`, `texto varchar(2000)` (anulable: se vacía al terminar), `estado` (`PENDIENTE`, `HECHO`, `DESCARTADO`, `FALLIDO`), `motivo varchar(20)`, `intentos smallint`, `reclamado_hasta`, `creado_en`, `actualizado_en`; `CHECK (estado = 'PENDIENTE' OR texto IS NULL)`; índice parcial de pendientes; `DELETE` con política restrictiva sobre terminados (retención con `retencion.operaciones-terminadas`) y en la anonimización (V2-11).
- `uso_ia` según `04-…` §3 + `tokens_cache_escritura`, `coste_micro_usd bigint`; RLS forzada; `INSERT … ON CONFLICT DO UPDATE` con sumas.
- `negocio`: `ia_habilitada boolean NOT NULL DEFAULT false` y límites anulables (`ia_presupuesto_mensual_usd numeric(8,2)`, `ia_llamadas_dia_conversacion`, `ia_textos_minuto_conversacion`) con `CHECK` > 0.

## 9. Criterios de aceptación
| # | Criterio | Responsable de la prueba |
|---|---|---|
| CI-1 | Texto libre con IA disponible → turno encolado, sin respuesta en la entrega; botones y palabras clave → camino determinista sin IA | Claude |
| CI-2 | Turno: reclamo exclusivo, concesión, contexto mínimo, bucle acotado, aplicación con la comprobación de §3.2.4; mensaje nuevo, derivación o modo humano a mitad → `DESCARTADO` sin salida ni operación | Codex |
| CI-3 | La propuesta real se crea solo en la transacción final; un turno descartado no deja operación vigente; «sí» tras una propuesta de IA confirma como en V2-10 | Codex |
| CI-4 | Herramientas: solo datos del negocio del contexto; id de otro negocio o de otra ficha → «no encontrado» al modelo sin datos; ids de franja opacos; ninguna herramienta escribe reservas | Codex |
| CI-5 | Guardas: horario no ofrecido, afirmación de operación, datos prohibidos y vacío → sustituidos por la respuesta del servidor; textos válidos pasan | Gemini |
| CI-6 | Límites: llamadas por día, textos por minuto y presupuesto mensual por negocio → menús/derivación y aviso al propietario una vez al día; por negocio sin cruzar | Codex |
| CI-7 | Fallos del modelo (tiempo, 429, 500, `refusal`, truncado, JSON inválido) → menús, turno `FALLIDO`, nunca en silencio | Codex |
| CI-8 | Adaptador de Anthropic contra el servidor local: herramientas estrictas, caché, modelo, esfuerzo, tokens; la petición no contiene teléfono, correo, `wa_id` ni ids internos (V2-RNF-05) | Codex |
| CI-9 | Sin clave, la aplicación arranca, la IA queda desactivada y todo funciona con menús | Codex |
| CI-10 | V117: permisos exactos, RLS forzada, `CHECK`; esquema, actualización e instalación en verde | Codex |
| CI-11 | `ConversacionGuionesIT`: los 8 casos mínimos de `08-…` §4.1 + derivación por herramienta + límite + dos negocios con nombres canario | Gemini (guiones) y Claude (ejecución) |
| CI-12 | Arnés en vivo: compila, se niega a ejecutar sin clave ni tope de gasto explícito, calcula las métricas de `08-…` §4.2 sobre transcripciones de ejemplo del adaptador falso | Gemini |
| CI-13 | `verify` completo en verde con JaCoCo y Javadoc sin avisos; registros sin contenido ni clave | Claude |

## 10. Reparto, ramas y revisión cruzada
| Bloque | Responsable | Worktree y rama | Archivos (alcance) | Revisa |
|---|---|---|---|---|
| 0 · Contrato | Claude | `D:\Proyectos\BarberTurno-v2-12` · `tarea/V2-12-conversacion-ia` | este documento, decisiones, *backlog*, tipos de §4, `InstruccionesAsistente`, YAML | Codex (consulta de diseño, solo lectura) |
| XI · Turnos, herramientas, límites, adaptador y V117 | Codex | `D:\Proyectos\BarberTurno-v2-12-backend` · `tarea/V2-12-conversacion-ia-backend` | `V117`; `conversation/ia/{TurnosIA*,ProcesadorTurnosIA,HerramientasReserva*,LimitesIA*,ModeloAnthropic,ConfiguracionIA,…}`; `pom.xml` (dependencia); la purga de turnos en `RetencionWhatsApp` y la anonimización; sus pruebas | Gemini Argon (*diff-first*) + Claude |
| CI · Enrutado | Claude | rama de la tarea | `OrquestadorConversacion` (encolar el turno), `ConversacionPropiedades`/`IaPropiedades`, YAML; sus pruebas | Codex (solo lectura) |
| GI · Guardas, adaptador falso, guiones y evaluación | Gemini | `D:\Proyectos\BarberTurno-v2-12-gemini` · `tarea/V2-12-conversacion-ia-gemini` | `conversation/ia/{GuardasSalida,ResultadoGuardas,MotivoGuarda}` y sus pruebas; `src/test/.../ia/{ModeloConversacionalFalso,GuionConversacion*}`; `ConversacionGuionesIT` y `src/test/resources/conversaciones/*.yaml`; el arnés en vivo y los correctores; el borrador del conjunto; la plantilla del informe | Codex (solo lectura) + Claude |

- **Migraciones y `pom.xml`:** solo Codex. **Contrato y configuración compartida:** solo Claude.
- **Pruebas con base:** con el ejecutor serializado. Gemini no ejecuta órdenes: Claude compila y ejecuta sus pruebas.

## 11. Pendientes del responsable (no se simulan)
- **P2-03:** proveedor, clave (`BT_IA_API_KEY` en `D:\Proyectos\BarberTurno-v2\.local\barberturno.env`, la escribe el responsable) y elección del modelo tras el informe.
- **Presupuesto** de la evaluación en vivo (propuesta de `09-costes.md` §3: ≤ 10 USD, primera ronda con Haiku 5.5 y Sonnet 5.5).
- **Revisión del conjunto** de conversaciones (borrador de Gemini) y del 20 % de prueba.
- **P2-06:** la transferencia de datos al proveedor de IA debe figurar en el aviso (D2/D7), como la de Meta.
