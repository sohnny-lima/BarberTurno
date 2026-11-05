# V2-12 · Bloque XI · Encargo 128

Fecha local: 04/11/2026. Estado: **Bloqueada (CI-11: guiones ajenos inconsistentes; H-XI-06: conservación de la entrada al derivar por IA)**.
La implementación del contrato XI y su verificación local están terminadas; el cierre de V2-12 requiere
resolver esos hallazgos con coordinación. Se registra el estado aquí por el alcance explícito del encargo,
sin modificar `docs/tareas.md` ni el backlog.
Rama: `tarea/V2-12-conversacion-ia-backend`; partida: `cc10e1e6de40e690e8727c1e2c0a13c9842a04b5`.
Sin merge ni push. No se ejecuta la evaluación en vivo.

## Implementación

- V117: revisión monotónica por disparador; contador de entradas; configuración y marcador diario del negocio; cola con concesión y ficha; llamadas y agregado diario; RLS forzada, FK compuestas, restricciones e índices y permisos por columna.
- `TurnosIAService` / `ProgramadorTurnosIA`: encolado obligatorio en la entrega; reclamo con `SKIP LOCKED`; comprobación de conversación, revisión, modo, ficha, negocio y concesión antes del HTTP y de la aplicación; plazo de 90 s; máximo cuatro llamadas y cuatro herramientas por respuesta; una propuesta por turno, precedencia de derivación y consentimiento y menús de respaldo. El observador recibe el cierre después del commit.
- `HerramientasReserva`, `ContextoTurno`, `EjecucionHerramienta`: capacidades efímeras por finalidad, JSON cerrado (incluidos duplicados y tokens sobrantes), validación de rangos y horas, máximo veinte franjas por consulta y validación en seco con el dominio. Ninguna herramienta persiste una operación o reserva.
- `LimitesIAService` / `AvisosLimiteIA`: bloqueo consultivo por negocio, reserva única antes del HTTP, liquidación idempotente, contabilización conservadora del uso desconocido y aviso diario al propietario. Los importes usan `BigDecimal`, micro-USD y redondeo hacia arriba; las cuatro categorías de tokens se suman una sola vez.
- `ConstructorContextoModelo`: proyección explícita a alias, nombre de pila, tiempo de Lima y textos saneados. No serializa entidades ni cuerpos crudos.
- `ModeloAnthropic` / `ConfiguracionModeloIA`: SDK estable, Jackson 2 encapsulado, continuación opaca, caché del bloque estable, esfuerzo configurable, sin muestreo, `fallbacks` ni elección forzada de herramienta. Cero reintentos ocultos. Sin configuración no se construye cliente HTTP y el puerto devuelve `NO_CONFIGURADO`. El cliente se cierra al detener el contexto.
- Retención y anonimización: abandono tras quince minutos y borrado de llamadas y turnos bajo el bloqueo de conversación; `uso_ia` sobrevive.

## SDK y entorno

`com.anthropic:anthropic-java:3.5.0`, comprobado el **04/11/2026** en
[Maven Central, metadatos oficiales](https://repo.maven.apache.org/maven2/com/anthropic/anthropic-java/maven-metadata.xml).
`latest=release=3.5.0`, `lastUpdated=20261104203757`. Maven resolvió los artefactos
`anthropic-java`, `anthropic-java-core` y `anthropic-java-client-okhttp` 3.5.0 y compiló con ellos.
La lectura HTTP desde el sandbox falló por permisos de socket; la repetición escalada fue correcta.
No se solicitó ni utilizó ninguna clave real y no se llamó a la API de Anthropic.

JDK fijado a Temurin 21.0.8 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot`).
Maven: wrapper **3.9.16**, comprobado con `.\mvnw.cmd -v` (`target/xi-versiones.log`).
PostgreSQL de referencia: **18.6**, puerto **5433**, confirmado en los registros de Flyway; las pruebas con base se solicitan
exclusivamente por `D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1`.

## Órdenes de verificación y registros

Desde `backend/`, con `JAVA_HOME` anterior:

| Orden | Resultado | Registro (no versionado) |
|---|---|---|
| `.\mvnw.cmd -DskipTests compile` | Correcto, 290 fuentes iniciales; resuelve SDK 3.5.0 | `target/xi-compilar-inicial.log` |
| `.\mvnw.cmd -DskipTests compile` | Correcto, 300 fuentes | `target/xi-compilar-implementacion.log` |
| `.\mvnw.cmd -DskipTests test-compile` | Correcto, 179 fuentes de pruebas | `target/xi-compilar-pruebas.log` |
| `.\mvnw.cmd -DskipTests test-compile` | Correcto, 181 fuentes de pruebas | `target/xi-compilar-pruebas-completas.log` |
| `.\mvnw.cmd -DskipTests verify` | Correcto, Javadoc sin avisos; JaCoCo omitido por falta de datos de ejecución antes de las pruebas | `target/xi-verify-sin-pruebas.log` |
| `.\mvnw.cmd -DskipTests verify` | Correcto, JaCoCo y Javadoc sin avisos, antes del último refuerzo de cierre | `target/xi-verify-final.log` |
| `.\mvnw.cmd -DskipTests verify` | **Correcto sobre el código definitivo**, JaCoCo y Javadoc sin avisos | `target/xi-verify-definitivo.log` |
| `.\mvnw.cmd -v` | Maven 3.9.16, JDK 21.0.8, Windows 11, UTF-8 | `target/xi-versiones.log` |

Orden solicitada contra PostgreSQL (sin `--`):

```powershell
pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 `
  -Worktree D:\Proyectos\BarberTurno-v2-12-backend `
  -Registro target\xi-pruebas-iniciales.log `
  '-Dtest=ConversacionGuionesIT,OrquestadorConversacionTest,OrquestadorConversacionIT,DerivacionConversacionIT,EsquemaIT,EsquemaV2IT,ConversacionV115IT' test
```

La orden esperó el cerrojo común y empezó a generar el registro tras su liberación. No se borró el cerrojo
ajeno ni se inició otra ejecución contra PostgreSQL. El `verify` completo queda reservado al coordinador.

Resultado: **111 pruebas, 10 fallos, 0 errores**. Las otras seis clases (89 pruebas) quedaron verdes;
`ConversacionGuionesIT`: 22 pruebas, 10 fallos. Salida:

```text
[ERROR] Tests run: 111, Failures: 10, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

Segunda tanda, mismo ejecutor y `-Worktree`, registro `target/xi-pruebas-bloque.log`:

```text
-Dtest=ModeloAnthropicTest,TurnosIAIT,ConversacionV117IT,InstalacionNuevaIT,ActualizacionV1IT,RetencionWhatsAppIT,AtencionHumanaWhatsAppIT,ConversacionGuionesIT
-Dbarberturno.ia.habilitada=true
-Dbarberturno.ia.clave=clave-ficticia-local
-Dbarberturno.ia.trabajador.habilitado=false
test
```

Resultado: **103 pruebas, 11 fallos, 0 errores**. `ModeloAnthropicTest`: **10/10**;
`TurnosIAIT`: **29/29**; instalación nueva: **1/1**; retención: **5/5**; atención humana: **21/21**.
Quedan 8 fallos de los guiones y 3 fallos de preparación/aserción de mis pruebas, corregidos antes de la regresión:

```text
ActualizacionV1IT: Tests run: 10, Failures: 2, Errors: 0
expected: "0 (Short@...)"
 but was: "0 (Integer@...)"
ConversacionV117IT: Tests run: 5, Failures: 1, Errors: 0
expected: "23503"
 but was: "23505"
[ERROR] Tests run: 103, Failures: 11, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

Correcciones: se comprueba el valor cero mediante `Number.intValue()` y, por separado, el tipo SQL `smallint`;
la prueba de FK usa un turno terminado para que la unicidad del pendiente no se adelante a la FK.
No se relaja ninguna restricción ni aserción de aislamiento.

`.\mvnw.cmd jacoco:report` → correcto (`target/xi-cobertura-intermedia.log`). El informe de las suites
selectivas mostraba cobertura insuficiente en los paquetes sujetos al umbral del 70 % (por ejemplo,
tenancy 71/170 líneas y scheduling 183/431). Se amplía la regresión de dominio y aislamiento para cumplir
ese control sin modificar ni omitir sus umbrales.

Tercera tanda, mismo ejecutor, registro `target/xi-regresion-final.log`:

```text
-Dtest=pe.barberturno.reservations.*Test,pe.barberturno.reservations.*IT,pe.barberturno.scheduling.*Test,pe.barberturno.scheduling.*IT,ConfiguracionTenancyTest,EjecutorEnNegocioTest,GuardaTransaccionHibernateTest,ResolutorNegocioTest,ContextoNegocioIT,FichaClienteIT,RegistroInvitadoIT,ActualizacionV1IT,ConversacionV117IT,TurnosIAIT,ModeloAnthropicTest,OrquestadorConversacionTest,OrquestadorConversacionIT,DerivacionConversacionIT,RetencionWhatsAppIT,AtencionHumanaWhatsAppIT
test
```

Resultado: **1.379 pruebas, cero fallos, cero errores y cero omitidas**. Las correcciones dejan
`ActualizacionV1IT` **10/10** y `ConversacionV117IT` **5/5**. También repite correctamente las 29 pruebas
de turnos, las 10 del adaptador y la orquestación y derivación existentes. Salida:

```text
[INFO] Tests run: 1379, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Cuarta tanda, mismo ejecutor, registro `target/xi-continuacion-final.log`:

```text
-Dtest=TurnosIAIT,ModeloAnthropicTest test
```

Verifica el código final que separa la continuación del observador, diferencia un fallo de conexión de
JSON inválido y comprueba que el presupuesto agotado de A no bloquea una reserva de B.
Resultado: **41/41**, de ellas **30** de turnos y **11** del adaptador.

Quinta y sexta tandas, mismo ejecutor:

```text
-Registro target\xi-concesion-cierre.log -Dtest=TurnosIAIT test
-Registro target\xi-turnos-definitivo.log -Dtest=TurnosIAIT test
```

Ambas: **32/32**, sin errores ni omitidas. Las dos pruebas adicionales avanzan el reloj después de crear
la propuesta real dentro de la transacción final: al vencer el plazo se revierte la propuesta y se envía
solo el menú; si vence también la concesión, se revierte todo sin respuesta del trabajador antiguo.
La sexta tanda comprueba además el código definitivo que revalida el margen temporal después de los
bloqueos y de reservar el presupuesto, dentro de la misma transacción.

Última orden: `.\mvnw.cmd -DskipTests verify`, **BUILD SUCCESS**, 38,005 s
(`target/xi-verify-definitivo.log`, final 04/11/2026 20:16:06 -05:00):

```text
[INFO] All coverage checks have been met.
[INFO] --- javadoc:3.12.0:javadoc-no-fork (javadoc-verificacion) @ barberturno ---
[INFO] BUILD SUCCESS
```

Cobertura de líneas acumulada en los paquetes exigidos (umbral original 70 %, intacto):

| Paquete | Líneas | Cobertura |
|---|---:|---:|
| `common.tenancy` | 159/170 | 93,53 % |
| `scheduling` | 425/431 | 98,61 % |
| `scheduling.dto` | 19/19 | 100 % |
| `reservations` | 477/481 | 99,17 % |
| `reservations.dto` | 20/20 | 100 % |
| `customers` | 92/100 | 92 % |
| `customers.dto` | 3/3 | 100 % |

No se ejecutó `verify` con pruebas. La suite completa y su integración corresponden al coordinador.

## Hallazgos para GI y el arquitecto (fuera del alcance de escritura)

- **H-XI-01 · Configuración de los guiones.** `ConfiguracionPruebasIA` declara `@TestPropertySource` sobre
  la configuración importada, mientras `ConversacionGuionesIT` solo tiene `@Import`. Las propiedades no
  activan la IA del perfil test. Con las tres propiedades ficticias explícitas de la segunda tanda,
  el fallo del guion 05 desaparece, pero permanecen los otros cuatro guiones (duplicados por los dos modos de ejecución).
- **H-XI-02 · Guion 03.** `03-si-tardio-operacion-caducada.yaml` pide `proponer_reserva(f1)` sin consultar
  disponibilidad. CI-4 exige rechazar ese alias inexistente, pero el guion espera una operación vigente.
  Además, no avanza el reloj antes del «SI», por lo que no demuestra caducidad. Salida: `expected: true / but was: false`.
- **H-XI-03 · Guion 04.** `04-doble-boton.yaml` espera una reserva después de `m:RESERVAR` y `p:S`, sin
  elegir franja, generar propuesta ni confirmar una operación. `p:S` acepta privacidad; no confirma una reserva.
  Hacerlo pasar vulneraría V2-RN-10. Salida: `expected: 1 / but was: 0`.
- **H-XI-04 · Ficha no enlazada.** `asegurarFichaCliente()` crea únicamente `cliente`, sin enlazar
  `conversacion.cliente_id`. Los guiones 06 y 09 esperan modo HUMANO; V2-DA-71 y CI-3 exigen primero
  consentimiento cuando la conversación no tiene ficha enlazada. Salida: `expected: "HUMANO" / but was: "ASISTENTE"`.
- **H-XI-05 · Aserciones incompletas.** El comprobador omite las aserciones de mensaje cuando no existe
  salida y las de sustitución cuando el observador está vacío; tampoco comprueba `estadoTurno`. El guion
  11 procesa cada entrada y el trabajador inmediatamente, de modo que no provoca un mensaje nuevo durante
  un turno. Los casos de concurrencia reales están cubiertos en `TurnosIAIT` sin modificar los guiones ajenos.
- **H-XI-06 · Texto que provoca una derivación por IA.** V2-DA-72 indica conservar la entrada que provoca
  la derivación en `SOLO_HUMANO`. En la ruta IA, `OrquestadorConversacion.aLaIA()` encola sin guardar
  esa entrada; el efecto final llama a `DerivacionConversacion.derivar()`, que cambia el modo y envía el
  acuse, pero no registra la entrada. El turno limpia su texto al terminar. Por lectura de ambos caminos,
  el personal pierde el texto inicial del reclamo aunque exista ficha. El contrato XI solo transporta
  texto saneado de hasta 2.000 caracteres y no la identidad de la entrada original. Coordinación debe
  concretar cómo conservar esa entrada y añadir la prueba de bandeja correspondiente; requiere acordar
  el enlace con el registro de Claude o autorizar un registro mínimo desde el turno. No se cambia ese
  contrato por cuenta propia ni se inventa una identidad de mensaje de Meta.

No se modificaron esas clases ni sus YAML. CI-11 necesita corrección y revisión de GI/coordinación.

## Pruebas añadidas

- `ModeloAnthropicTest`: petición real del SDK observada por `HttpServer` local; herramientas estrictas,
  `cache_control`, esfuerzo y parámetros ausentes; continuación con pensamiento y resultados agrupados;
  caché, `refusal`, `max_tokens`, JSON inválido, 429, 500, tiempo agotado y configuración sin clave.
- `TurnosIAIT`: revisión cambiada durante el HTTP, concesión y ficha perdidas, segundo trabajador,
  precedencia y ambigüedad de propuestas, consentimiento, confirmación determinista, cambios de cita,
  límites de herramientas, llamadas, entradas, presupuesto concurrente, aviso diario, liquidación
  idempotente, contexto mínimo, alias inválidos y retención del gasto.
- `ConversacionV117IT` y `ConversacionIAEsquemaPruebas`: permisos exactos, RLS con el rol real, revisión,
  restricciones, `UPSERT` y aislamiento entre negocios y sin contexto.
- Se amplían las pruebas de esquema, instalación nueva y actualización conservando sus aserciones previas.

## Decisiones menores y límites prácticos

1. El agregado contabiliza el máximo al reservar y la liquidación aplica la diferencia. La purga no vuelve
   a sumar el detalle: así no hay doble conteo y una caída no hace desaparecer gasto.
2. La estimación de entrada usa bytes UTF-8, margen de protocolo y una reserva para la continuación opaca;
   se cobra provisionalmente al mayor precio de entrada/caché, sin presumir aciertos de caché.
3. Un reclamo nuevo nunca repite una llamada ya reservada con el mismo número: termina con menú y conserva
   el coste desconocido. El cierre de un trabajador antiguo no cambia el estado de la ficha nueva.
4. El estado guarda el metadato interno `ia_corte_mensaje`, fuera del borrador `ia`, con la identidad del
   último mensaje previo al primer turno de IA tras un vaciado. Se vacía con el resto del estado y nunca
   se proyecta al proveedor. Permite recuperar hasta seis respuestas posteriores sin confundir mensajes
   de igual sello temporal; en el primer turno se omite conservadoramente el historial anterior.
5. Las llamadas no tienen un plazo autónomo de conservación especificado. Se borran al purgar o anonimizar
   su conversación, y los turnos pendientes se terminan a los quince minutos. No se inventa un plazo D4.
   Una pasada exclusiva de abandono cada 30 s evita esperar la purga general horaria; el trabajador nunca
   reclama texto con quince minutos de antigüedad. El borrado físico sigue siendo periódico y puede
   demorarse por la cadencia, los lotes, un bloqueo o una caída; no es una garantía de eliminación instantánea.
6. El observador recibe una copia de la respuesta con `ContinuacionProveedor.VACIA`: puede conservar
   transcripciones de texto, herramientas y uso, pero nunca el pensamiento. La continuación original se
   devuelve intacta al proveedor dentro del bucle. Se añade una prueba específica de esta separación.
7. La renovación exige actualizar exactamente una fila. El cierre vuelve a comprobar plazo y concesión;
   perderlos después de validar el dominio revierte conjuntamente propuesta, estado y outbox. Las
   llamadas ya liquidadas conservan su coste en sus transacciones independientes del cierre.

## Javadoc y límites del encargo

Javadoc de propósito, parámetros y resultados en todos los tipos y miembros públicos añadidos,
`@author`/`@version` en las clases y herencia explícita del contrato en las implementaciones.
Se actualiza el Javadoc de retención y anonimización para explicar turnos y conservación de costes.
No se modifican firmas del contrato, clases reservadas a Claude/Gemini, YAML, documentos de arquitectura/requisitos,
AGENTS, CLAUDE, material APF2, evidencias v1, README, remotos ni entornos v1/Azure.
No se lee ni modifica `.local/coordinacion/`; los secretos solo los carga el ejecutor autorizado en su proceso.

## Inventario exacto

```text
backend/pom.xml
backend/src/main/resources/db/migration/V117__conversacion_ia.sql
backend/src/main/java/pe/barberturno/audit/AccionAuditoria.java
backend/src/main/java/pe/barberturno/conversation/ia/ConfiguracionModeloIA.java
backend/src/main/java/pe/barberturno/conversation/ia/ConstructorContextoModelo.java
backend/src/main/java/pe/barberturno/conversation/ia/ContextoTurno.java
backend/src/main/java/pe/barberturno/conversation/ia/EjecucionHerramienta.java
backend/src/main/java/pe/barberturno/conversation/ia/HerramientasReserva.java
backend/src/main/java/pe/barberturno/conversation/ia/LimitesIAService.java
backend/src/main/java/pe/barberturno/conversation/ia/ModeloAnthropic.java
backend/src/main/java/pe/barberturno/conversation/ia/ProgramadorTurnosIA.java
backend/src/main/java/pe/barberturno/conversation/ia/TurnosIAService.java
backend/src/main/java/pe/barberturno/conversation/ProgramadorRetencionWhatsApp.java
backend/src/main/java/pe/barberturno/conversation/RetencionWhatsApp.java
backend/src/main/java/pe/barberturno/customers/AnonimizacionFichas.java
backend/src/main/java/pe/barberturno/notifications/AvisosLimiteIA.java
backend/src/test/java/pe/barberturno/EsquemaIT.java
backend/src/test/java/pe/barberturno/common/tenancy/ActualizacionV1IT.java
backend/src/test/java/pe/barberturno/common/tenancy/ConfiguracionNegocioEsquemaPruebas.java
backend/src/test/java/pe/barberturno/common/tenancy/ConversacionIAEsquemaPruebas.java
backend/src/test/java/pe/barberturno/common/tenancy/ConversacionV115IT.java
backend/src/test/java/pe/barberturno/common/tenancy/ConversacionV117IT.java
backend/src/test/java/pe/barberturno/common/tenancy/EsquemaV2IT.java
backend/src/test/java/pe/barberturno/common/tenancy/InstalacionNuevaIT.java
backend/src/test/java/pe/barberturno/conversation/ia/ModeloAnthropicTest.java
backend/src/test/java/pe/barberturno/conversation/ia/TurnosIAIT.java
backend/src/test/java/pe/barberturno/support/LimpiezaBaseDatos.java
docs/v2/pruebas/v2-12-backend.md
```

## Commits y comprobación de cierre

| Hash | Contenido |
|---|---|
| `33a5238f92fbdd1d13a2c6a930de0d033d427423` | V117, dependencia/perfil y pruebas de esquema |
| `3514590f0db476895d3d2c1033f5f21025bde78f` | Herramientas, capacidades y contexto mínimo |
| `e8e1aa16cddd0991fe26027cf82cc9f37c244496` | Adaptador y pruebas HTTP locales |
| `dc825bbba41e21ff41b921ecc2c7a11ecba5a38f` | Presupuesto, liquidación y aviso diario |
| `91d48c728dde819f2be1ec64c050f403f9ae7d16` | Trabajador, aplicación final, retención y anonimización |
| `d444969016c59fa525b193eb3114c549836d1868` | Correcciones de las comprobaciones JDBC y de FK |
| `c59f33a07b15842c10f12e6e32a37611f240b5b8` | Continuación fuera del observador y clasificación de conexión |
| `0c644be83f21d91f5d098939ddf1d9019532b0e1` | Reversión al vencer el turno durante la aplicación final |

Esta evidencia se añade en un único commit `docs` separado, cuyo hash se entrega en el informe final.
**Merge: ninguno. Push: ninguno.** Misma rama que al comenzar.

`git diff --check` antes de cada commit: correcto. Inventario comprobado: **28 archivos del alcance**,
UTF-8 sin BOM y LF. La primera consulta de Git falló por propiedad dudosa del sandbox; se repitió
escalada sin cambiar ACL, propietarios, `safe.directory` ni configuración de Codex. `rg` no está instalado;
se usó `Select-String`. Una lectura inicial con llaves de Bash no era sintaxis válida en PowerShell y se
repitió con `ForEach-Object`, sin modificar archivos.

Todos los procesos Maven y JVM de pruebas iniciados por este encargo terminaron. La comprobación final de
procesos solo encontró un Java preexistente (inicio 04/11/2026 14:44:59), que se dejó intacto. Los servidores
HTTP locales y sus ejecutores se cerraron en las pruebas. No se crearon worktrees ni se dejaron servidores.
No se modificó ningún archivo fuera del inventario, ninguna etiqueta (incluida `T-03-registro`), remoto,
configuración de Codex, base v1, Azure, APF2 o entregable. P2-03, P2-06, el presupuesto y la revisión de la
evaluación real siguen pendientes del responsable; no se ejecutó el arnés ni se usó ninguna clave real.
