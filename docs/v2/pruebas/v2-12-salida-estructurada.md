# V2-12 · SE · Salida estructurada (encargo 139)

Estado: **Hecha (bloque SE), pendiente de revisión e integración por el coordinador** · 05/11/2026 · rama `tarea/V2-12-salida-estructurada`. La batería global conserva 12 fallos de guiones de Gemini pendientes de RD-2; no se declara V2-12 globalmente en verde.

Contrato: `17-v2-12-13-ia-y-evaluacion.md` §13 y V2-DA-86. Se conserva el contrato de tipos y se consume la composición RD-1 sin modificar sus archivos. Sin uniones ni push; no se actualizan los cierres de v1.

Entorno de ejecución: Windows, Temurin 21.0.8, Maven Wrapper 3.9.16, Spring Boot 4.1.1, PostgreSQL 18.6 (puerto 5433, base v2_test). Adaptador falso y HTTP exclusivamente local; sin proveedor real. Las pruebas y compilaciones usan el ejecutor serializado autorizado; no se leen archivos de `.local/`.

Incidencias de preparación: Git en el sandbox devolvió `fatal: detected dubious ownership`; se ejecuta con escalada revisada automáticamente, sin tocar ACL ni configuración. `rg` no está disponible; se usa `Select-String`. El primer intento del ejecutor no creó registro porque faltaba `backend/target` (`Could not find a part of the path ... backend\target\se-compilar.log`); creado ese directorio, la compilación terminó con `mvnw rc=0`, `BUILD SUCCESS` (32,097 s). No se llegó a ejecutar Maven en el intento sin directorio.

## Implementación

- `HerramientaIA`: terminal `responder_al_cliente`, objeto cerrado, cuatro campos obligatorios, intención y dato faltante enumerados; admite alias y «cualquiera».
- `HerramientasReserva`: validación estricta (incluye duplicación de claves y tokens sobrantes), coherencia intención/falta y capacidades del turno. Actualización atómica de selecciones solo tras validar todos los argumentos. Registra explícitamente la última búsqueda válida vacía; una búsqueda fallida no reemplaza ese marcador.
- `TurnosIAService`: examina todas las llamadas, despacha primero las no terminales, cuenta la terminal en el límite y cierra sin otra llamada al proveedor. Conserva efectos válidos si la terminal es inválida, duplicada o excede el límite. Datos de composición bajo RLS después de revalidar conversación, revisión y ficha. Precedencia de derivación/consentimiento/propuesta/franjas; degradaciones y menús vacíos mediante `RespuestasAsistente`. Se mantienen los resúmenes, nonces y la confirmación de V2-10. No usa `GuardasSalida` en la salida; `fin` recibe guardas vacías y `decision` notifica tras confirmar la transacción, con el texto descartado.
- `ContextoTurno` / `ConstructorContextoModelo`: selecciones parciales (profesional nulo = no elegido, cero = cualquiera), revalidación de ids activos y fecha real no pasada, `ia.ultima` de lista cerrada. Se eliminan consulta de nombre, historial textual y marcador de corte; `ultimaRespuesta` vuelve a NEUTRO si el estado se vació o el valor no pertenece a la lista.
- `InstruccionesAsistente`: pide herramientas y cierre estructurado; informa que el texto libre se descarta. `ModeloAnthropic` ya publica las herramientas de la petición de forma genérica: se documenta ese comportamiento y se verifica la terminal en HTTP real local; sin cambio de `tool_choice` ni de la continuación opaca.
- Pruebas: `TurnosIAIT` adapta los cuatro casos indicados por el encargo y añade esquema/alias/coherencia, duplicados, orden, límites, todas las intenciones, degradaciones, selecciones parciales, catálogo/profesionales vacíos o desactivados, última búsqueda vacía, aislamiento de fichas y cambio de ausencia de ficha durante HTTP. Conserva concurrencia, concesión, costes, consentimiento y confirmación. `ModeloAnthropicTest` verifica el esquema terminal y el retorno intacto de pensamiento, firma, `redacted_thinking` y resultados conjuntos. No existe `HerramientasReservaTest`; las herramientas se verifican en `TurnosIAIT` con RLS real.

Javadoc: actualizado el propósito de `ConstructorContextoModelo`, `TurnosIAService`, `ModeloAnthropic`, el contrato de ejecución de `HerramientasReserva` y la constante terminal de `HerramientaIA`; las clases conservan autor y versión. No se añaden métodos públicos/protegidos sin documentación.

## Órdenes y resultados

Todas las órdenes Maven tienen este prefijo, desde la raíz del worktree:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-12-se -Registro target\<registro>.log <argumentos>
```

Los registros están en `backend/target/` (ignorados por Git). Una sola ejecución a la vez. Argumentos de compilación: `-DskipTests test-compile` → `se-compilar.log`, `BUILD SUCCESS`.

Argumentos de las tres ejecuciones de clases afectadas:

```text
-Dtest=TurnosIAIT,OrquestadorConversacionIT,DerivacionConversacionIT,ModeloAnthropicTest test
```

| Registro | Pruebas | Fallos / errores / omitidas | Resultado |
|---|---:|---|---|
| `se-afectadas-1.log` | 98 | 14 / 0 / 0 | Falló; corregido (detalle debajo) |
| `se-afectadas-2.log` | 106 | 2 / 1 / 0 | Falló; corregidos fixtures y expectativa (detalle debajo) |
| `se-afectadas-3.log` | 106 | 0 / 0 / 0 | `BUILD SUCCESS`, 2 min 35 s; TurnosIAIT 83, ModeloAnthropicTest 11, OrquestadorConversacionIT 8, DerivacionConversacionIT 4 |
| `se-rd1-guiones.log` | 71 | 12 / 0 / 0 | Composición RD-1: 38 verdes (15 + 23); guiones: 21 verdes y 12 fallidos; 1 min 40 s |
| `se-verify-sin-pruebas.log` | omitidas por orden del encargo | — | `BUILD SUCCESS`, 23,194 s; Javadoc sin avisos |

Primera ejecución, salida resumida de todos los fallos (solo `TurnosIAIT`; las otras clases pasaron):

```text
Tests run: 98, Failures: 14, Errors: 0, Skipped: 0
datosFaltantes_seDegradan_yCualquieraUsaCero [1..4]:
  Expecting Optional to contain: DecisionRespuesta[PEDIR_DATO, SERVICIO/PROFESIONAL]
  but was empty.
datosFaltantes_seDegradan_yCualquieraUsaCero [5]: expected to contain "dia:1:0:";
  actual selection was "d:1:0:20261104:0".
seleccionesParciales_seRecuperanSinBuscarDisponibilidad: expected 1L but was 0L.
datosVaciosODesactivados_noEmitenListasVacias [1..3]: expected sinOpciones(); actual neutro().
mostrarCitas_usaSoloFichaRevalidada_ySinFichaNoRevelaExistencia [1..2]: expected sinCitas(); actual neutro().
contexto_sinHistorialTextual_conUltimaRespuesta_yVaciado: expected SALUDO but was NEUTRO.
fichaCambiadaDuranteHttp_descartaAntesDeConsultarCitas: expected DESCARTADO but was FALLIDO.
terminalCuentaEnElLimite_ySiempreCierra [1]: expected true but was false.
BUILD FAILURE
```

Causa de los rechazos de terminales válidas: el condicional de un `Long` opcional con una rama primitiva forzaba un desempaquetado de nulo. Se preserva el tipo envoltorio en ambas ramas. También se corrigen las expectativas de ids de V2-10 (`d:` / `c:`).

Segunda ejecución:

```text
Tests run: 106, Failures: 2, Errors: 1, Skipped: 0
intenciones_soloComposicionDelServidor_yTextoDescartadoEnObservador [3]:
  expected MOSTRAR_PROFESIONALES but was PEDIR_DATO:SERVICIO.
mostrarCitas_usaSoloFichaRevalidada_ySinFichaNoRevelaExistencia [2]:
  ERROR: conflicting key value violates exclusion constraint "reserva_sin_solape_barbero"
fichaCambiadaDuranteHttp_descartaAntesDeConsultarCitas:
  expected DESCARTADO but was FALLIDO.
BUILD FAILURE
```

Correcciones de pruebas: respetar la degradación a pedir servicio; sembrar citas de dos fichas en horas distintas; simular el enlace permitido de una ficha durante HTTP partiendo de ausencia, ya que V115 impide cambiar o retirar una ficha fijada y exige el mismo remitente. No se debilitan disparadores ni restricciones de exclusión.

La ejecución de composición/guiones usa estos argumentos:

```text
-Dtest=ConversacionGuionesIT,RespuestasAsistenteTest,ComposicionTurnoIATest test
```

Verificación de empaquetado y Javadoc: antes se borra exclusivamente `backend/target/jacoco.exec` con `Remove-Item -LiteralPath`, tal como ordena el encargo. Argumentos del ejecutor: `-DskipTests verify`. Salida:

```text
Tests are skipped.
Skipping JaCoCo execution due to missing execution data file.
--- javadoc:3.12.0:javadoc-no-fork (javadoc-verificacion) @ barberturno ---
BUILD SUCCESS
Total time: 23.194 s
```

No se atribuye cobertura global a una ejecución parcial ni se presenta ese `verify` como suite completa.

## Fallos de Gemini para RD-2 (archivos intactos)

Registro `backend/target/se-rd1-guiones.log`; detalle en `backend/target/surefire-reports/pe.barberturno.conversation.ia.ConversacionGuionesIT.txt`. Los 12 fallos son seis guiones ejecutados dos veces: prueba individual y método parametrizado. Estas son las esperas exactas y la salida observada; no hay errores técnicos ni omisiones:

| Prueba individual de `ConversacionGuionesIT` | Caso de `ejecutarGuion_casosMinimos_cumplenExpectativas` | Espera exacta que falla | Resultado real |
|---|---|---|---|
| `guion01_modeloInventaHora_guardaSustituyePorServidor` | `[1]` · `01-inventa-hora` | `to contain: "Te muestro las opciones disponibles."` | Cuerpo fijo `Estos son los horarios disponibles. Elige uno:`; lista con franjas del servidor. El guion también conserva `sustitucionGuardas: HORARIO_NO_OFRECIDO`, que deberá retirarse de la salida |
| `guion02_modeloAfirmaConfirmadaSinEjecucion_bloqueadoPorGuarda` | `[2]` · `02-afirma-confirmada-sin-ejecucion` | `[Se esperaba sustitución por guarda: AFIRMA_OPERACION] Expecting value to be true but was false` | Menú neutro; `fin.guardas` vacío por V2-DA-86 |
| `guion05b_limiteDiarioAlcanzado_derivaAMenusSinLlamarModelo` | `[6]` · `05-limite-diario-superado` | `to contain: "Ofrecemos corte clásico"` (primer paso) | Menú `Te muestro las opciones disponibles.`; el texto libre del proveedor no se envía |
| `guion07_herramientaConAliasAjeno_errorYNoFiltraDatos` | `[8]` · `07-herramienta-alias-ajeno` | `expected: "TEXTO" but was: "INTERACTIVO"` | Menú neutro. El YAML espera además texto del proveedor `No encontré ese servicio en la barbería.` |
| `guion08_inyeccionPrompt_sinEfectoPerjudicial` | `[9]` · `08-inyeccion-ignora-reglas` | `expected: "TEXTO" but was: "INTERACTIVO"` | Menú neutro. El YAML espera además `primero debes elegir uno de los servicios disponibles` del proveedor |
| `guion11_turnoDescartadoPorMensajeNuevo_sinSalida` | `[12]` · `11-turno-descartado-mensaje-nuevo` | `expected: "TEXTO" but was: "INTERACTIVO"` (segundo paso) | El primer turno se descarta sin salida; el siguiente cierra con menú neutro. El YAML espera además texto del proveedor `Con gusto te ayudo con tu reserva.` |

Salida final de esa orden:

```text
ComposicionTurnoIATest: Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
ConversacionGuionesIT: Tests run: 33, Failures: 12, Errors: 0, Skipped: 0
RespuestasAsistenteTest: Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
Tests run: 71, Failures: 12, Errors: 0, Skipped: 0
BUILD FAILURE
```

## Decisiones menores y límites

- Los nulos de la terminal conservan las selecciones parciales conocidas; «cualquiera» se guarda como cero. Un esquema o alias inválido no guarda una selección parcialmente validada.
- Para obtener el menú fijo `sinOpciones` cuando una selección acaba de desactivarse o no quedan profesionales, se usa la composición existente de servicios sin opciones. No se amplían firmas ni enums del contrato de §13.4.
- El contenido de fallos y menús procede de `RespuestasAsistente`; consentimiento y derivación mantienen el flujo determinista de V2-10/V2-11. El README no cambia: arranque y comandos públicos se conservan.
- La documentación general aún describe nombre de pila/historial/redacción y guardas en algunos apartados heredados (`02` V2-RNF-05, `04` §7, `08` §4.1); en este bloque se sigue la sustitución expresa de §13 y V2-DA-86. No se modifica documentación protegida ni se requiere cambiar el diseño aprobado.

## Commits y cierre

Base recibida: `d9c8f6f` (unión RD-1 existente; no realizada por este encargo).

- `5d938a5c66bb91e958b4e0ad61f495d65e02d5db` · `feat(ia): validar el cierre estructurado y componer la salida en el servidor [V2-12]` (producción y prueba del adaptador).
- `4ffc04cec4acd6c956d598df6e2ba52e28fb169e` · `test(ia): cubrir terminales, degradaciones y aislamiento de la salida estructurada [V2-12]` (83 casos de turnos).
- Cierre documental en el commit `docs(ia): registrar la verificación SE y los guiones pendientes de RD-2 [V2-12]`; su hash se entrega en el informe final y queda consultable con `git log`.

`git diff --check` conforme antes de cada commit. Archivos nuevos/modificados en UTF-8 sin BOM y LF. Solo cambia este documento de evidencia y nueve archivos Java del bloque SE. No hay cambios de contrato, DDL, RN, endpoints, permisos o protocolo de bloqueos, ni desviaciones del alcance. El adaptador no necesitó alterar su algoritmo: la terminal ya viaja al enumerar las herramientas del contrato.

Pendiente de integración: Gemini adapta los seis guiones y sus doce ejecuciones en RD-2; el coordinador integra los bloques y ejecuta la batería global con cobertura. No se ejecutó evaluación en vivo ni se usó un proveedor real. No se comprobó envío real a Meta; la prueba de canal ampliada CI-14/16 pertenece a RD-2. No se simulan los pendientes de clave, presupuesto o revisión del conjunto.

Límites respetados: sin modificaciones de `RespuestasAsistente`, archivos de Gemini, documentos protegidos, `docs/apf2/`, entregables ni evidencias de v1; sin lectura de `.local/` (solo invocación del ejecutor expresamente autorizado), secretos, servicios externos, push, publicación, etiquetas, remotos, ACL o configuración de Codex. No se crean ramas ni worktrees; se permanece en la rama asignada, sin uniones. Pruebas de BD siempre en serie. No quedan procesos propios de Maven, Java, servidores HTTP o frontend. Los procesos `cmd`/`node` de herramientas Codex preexistentes (08:58:04, sin escuchas TCP) se conservan.
