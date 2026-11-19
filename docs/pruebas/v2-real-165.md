# V2-REAL · Encargos 165, 165b y 165c · Bot de WhatsApp

Fecha de inicio: 18/11/2026. Estado: **En curso, verificación en ejecución**.
Rama: `tarea/V2-REAL-bot-mensajes`, desde `tarea/V2-REAL-apk-version`
(`a75ef1e`, con los commits anteriores `a4160cf` y `a75ef1e`).
**Sin merge ni push**, por instrucción expresa. Se conserva
`b110b2f52455eccc759f95d33cccec498bdd7f71`.

## Correcciones por causa comprobada

| Causa | Código responsable y corrección |
|---|---|
| 1. Tres respuestas al volver de HUMANO | `DerivacionConversacionService.devolver` encolaba texto e interactivo; `OrquestadorConversacion.recibir` añadía la bienvenida. Ahora el servicio encola **un solo interactivo**, con el aviso de retorno y el menú principal; el orquestador termina esa entrada. Nombre obtenido con JOIN negocio/conversacion por el identificador de conversación. |
| 2. Unos consecutivos ejecutaban otros menús | `CorrespondenciaMenusTwilio` conserva la decisión por canal, persona y MessageSid; fija la selección con el instante de recepción capturado al entrar al webhook. Bloquea mientras hay respuesta pendiente y durante la ventana mínima de reacción. `BuzonRespuestasTwilio` publica el menú al tomar/devolver el TwiML, mediante callback, en vez de publicarlo al encolarlo. |
| 3. RN-07 dejaba solo «Menú» | `TextosConversacion.avisoSinAcciones` explica la restricción cuando los permisos actuales no permiten actuar; `MenusConversacion.accionesCita` añade «Hablar con alguien» y «Menú principal» para citas activas sin acciones. No habilita cambios ni cancelación ni modifica RN-07. |
| 4. «del 1 al 1» | `CorrespondenciaMenusTwilio.traducir` usa «Solo hay una opción en este menú. Responde con 1 o escribe «menu» para volver al inicio.» y repite el menú. |

El aviso de mensajes seguidos se conserva en el acumulado que ya tenía
`WebhookTwilioController`; no se cambia ese camino descartado por el revisor.
Su única variación es capturar y pasar el instante monotónico de recepción.

## Criterio determinista propuesto para V2-DA (165b/c)

Decisión pendiente de numeración y registro por el coordinador:

1. La clave idempotente es (canal, persona, MessageSid), durante 24 h. Un reintento
   conserva su selección o su aviso inicial aunque después cambie el menú.
   Dos MessageSid distintos con el mismo texto son entradas distintas.
2. Un texto libre o selección admitida marca una respuesta pendiente **20 s**.
   El plazo supera la espera TwiML máxima de 12 s más un margen de 8 s. Nunca
   hereda las 24 h del menú. Una respuesta perdida se recupera por caducidad.
3. Al entregar una respuesta con opciones se publica el nuevo menú y se libera
   la marca. Una respuesta **sin opciones** también libera la marca: al tomar el
   TwiML, al entregar una respuesta retenida o al aceptar Twilio el envío REST.
   Un resultado REST desconocido sin opciones espera la caducidad, sin afirmar
   que fue entregado.
4. Una selección numérica o ButtonPayload recibida antes de publicarse el menú,
   mientras está pendiente la respuesta anterior o dentro del mínimo de
   reacción, **no crea evento ni ejecuta acciones**. Devuelve el aviso siguiente
   junto al menú vigente:
   «Recibí varios mensajes seguidos; para evitar errores, elige de nuevo:».
5. Propiedad nueva `barberturno.whatsapp.twilio.reaccion-minima`: predeterminado
   `1500ms`; validación de `500ms` a `5s`, inclusive. Se usa tiempo monotónico
   de recepción/publicación. Las pruebas controlan el proveedor de nanosegundos
   y el Clock, sin dormir para probar los límites.
6. El menú se conserva para repetirlo al rechazar una ráfaga. La decisión por
   MessageSid, la autorización, el nonce de confirmación, la caducidad de la
   propuesta y la idempotencia persistida siguen aplicándose. El adaptador no
   cambia las reglas de reservas.

La publicación representa el momento en que el servidor devuelve el cuerpo
TwiML o acepta el resultado REST; no es un acuse de lectura del teléfono.
La ventana mínima es una protección temporal explícita, no una inferencia de
que la persona haya leído el menú. La correspondencia sigue siendo local a una
instancia y se pierde al reiniciar, como en V2-DA-100.

## Cambios por módulo

- `conversation/TextosConversacion`: resumen previo, detalle y resultado con
  barbería, código, servicio, profesional, fecha completa en Lima, inicio y fin,
  y estado legible. Cancelar emplea el mismo «18:00 a 18:30» que crear.
  `devuelta` sanea nombres nulos, vacíos o con espacios.
- `conversation/OrquestadorConversacion`: crear/reprogramar deja la vista
  `CITA` y las acciones de **esa** cita, consultando los permisos actuales.
  Se protege la consulta cuando no existe ficha. El resultado mantiene su
  instantánea y origen SISTEMA; una repetición no cambia el resultado histórico,
  pero sí consulta permisos actuales. Cancelar vuelve al menú principal.
- `conversation/MenusConversacion`: orden Reprogramar/Cancelar/Menú principal;
  cancelar pide «Sí, cancelar» / «Mantener cita», con los mismos identificadores
  de operación y nonce existentes. Meta conserva botones/listas interactivos.
- `conversation/ia/TurnosIAService`: usa el resumen completo y los mismos
  botones específicos al proponer una cancelación.
- `conversation/PalabraClave`: `menu`, `menú`, `volver` y `ayuda` llevan al
  menú principal en cualquier paso y también desde HUMANO. **Ayuda significa
  volver al asistente y mostrar sus opciones**. Abandonar una propuesta muestra
  «Descarté la propuesta pendiente sin guardar cambios.»; no confirma cambios.
- `conversation/DerivacionConversacionService`: retorno con un solo menú y
  nombre de su conversación; derivación con el plazo real de
  `BandejaPropiedades.retornoAutomatico()`.
- `whatsapp/twilio`: publicación al entregar, protección de selecciones,
  caducidad corta, configuración validada y títulos de acciones con código.
  No se cambian las rutas HTTP ni las credenciales/configuración de producción.
- `demo/DatosDemoMultinegocioService`: la nueva cuenta
  `personal-cliente@ejemplo.test` y su perfil se llaman «Jorge», con el mismo
  correo y papeles. `git grep` no encontró otras dependencias del nombre
  anterior. Se conserva la idempotencia: no se renombran perfiles ya existentes.
- Pruebas existentes ampliadas y nuevas regresiones unitarias donde faltaban.
  Se completa `RecorridoTwilioBsuidIT.elegirConRafagaYReintento`; el recorrido
  ya existente prueba ambos MessageSid, una única reserva y sus transiciones.

Javadoc útil añadido/actualizado en `TextosConversacion` (resultado, resumen,
detalle, derivada, devuelta, avisoSinAcciones y propuestaDescartada),
`MenusConversacion` (accionesCita, confirmacionCancelacion),
`DerivacionConversacion`, `CorrespondenciaMenusTwilio` (constructor,
decidir, entregadoSinOpciones, registros), `TraductorEntradaTwilio`
(sobrecargas con instante), `BuzonRespuestasTwilio.entregar` y los constructores
y propiedad de `TwilioPropiedades`. Se mantienen los contratos Javadoc útiles
de los métodos públicos cuyo cuerpo cambia. No hay endpoints nuevos.

## Textos literales antes y después

Ejemplos ficticios; horarios en Lima. El saludo principal ya incluía el nombre
de la barbería. Se conserva esa identificación.

| Acción | Antes | Después |
|---|---|---|
| Crear | Listo: BT-105 el jueves 19 de noviembre de 2026 a las 10:10 con Carlos. | Listo, tu cita en Barbería X: BT-105 · Corte clásico · jueves 19 de noviembre de 2026 · 10:10 a 10:40 · con Carlos · Estado: Confirmada. |
| Reprogramar | Cambiamos tu cita BT-105 para el viernes 20 de noviembre de 2026 a las 11:00 con Carlos. | Reprogramamos tu cita en Barbería X: BT-105 · Corte clásico · viernes 20 de noviembre de 2026 · 11:00 a 11:30 · con Carlos · Estado: Confirmada. |
| Cancelar | Cancelamos tu cita BT-105 del viernes 20 de noviembre de 2026 a las 11:00 con Carlos. | Cancelamos tu cita en Barbería X: BT-105 · Corte clásico · viernes 20 de noviembre de 2026 · 11:00 a 11:30 · con Carlos · Estado: Cancelada. |

Confirmación manual:

> Listo, tu cita en Barbería X: BT-105 · Corte clásico · jueves 19 de noviembre de 2026 · 10:10 a 10:40 · con Carlos · Estado: Pendiente de confirmación por la barbería.

Resumen antes de cancelar:

```text
Barbería: Barbería X
Código: BT-105
Cancelación de tu cita BT-105:
Corte clásico con Carlos
viernes 20 de noviembre de 2026, 11:00 a 11:30 (30 min)
Precio: S/ 25,00
Estado: Confirmada. Aún no se ha aplicado ningún cambio.

1. Sí, cancelar BT-105
2. Mantener cita BT-105
Responde con el número o escribe «menu» para volver al inicio.
```

Antes, los botones eran «Confirmar» / «Cambiar». El título «Mantener cita»
anula la propuesta de cancelación, conservando la cita.

Menú tras crear o reprogramar, si ambos permisos están habilitados:

```text
Antes:
1. Reservar
2. Mis citas
3. Hablar con alguien

Después:
1. Reprogramar BT-105
2. Cancelar BT-105
3. Menú principal
Responde con el número o escribe «menu» para volver al inicio.
```

Una cita PENDIENTE ofrece Cancelar y Menú principal conforme a los permisos.
RN-07, cuando no permite ninguna acción:

```text
Faltan menos de 2 horas para tu cita: ya no se puede cambiar ni cancelar por este medio. Puedes elegir «Hablar con alguien» para contactar con el equipo.

1. Hablar con alguien
2. Menú principal
Responde con el número o escribe «menu» para volver al inicio.
```

Derivación, antes: «Te paso con el equipo de Barbería X. Te responderán por aquí en cuanto puedan.»
Después (retorno predeterminado de 24 h):

> El equipo de Barbería X verá tu conversación en su bandeja y podrá responderte por este chat. Tras 24 horas sin respuesta del equipo, el asistente retomará la conversación automáticamente. Puedes volver antes al asistente escribiendo «menu».

Al volver, antes había aviso TEXTO + interactivo con el mismo aviso + saludo.
Ahora un solo mensaje con este texto y el menú principal:

> Tu conversación en Barbería X ha vuelto al asistente virtual. Puedes reservar, consultar tus citas o hablar con el equipo. ¿En qué te puedo ayudar?

El retorno automático usa el plazo desde
`coalesce(ultima_respuesta_personal_en, derivada_en)`, con revalidación y
bloqueo, y revisión periódica (cinco minutos por defecto). Se encola el aviso
solo si sigue abierta la ventana efectiva de WhatsApp. Sin nuevas entradas
durante 24 h no se promete un mensaje espontáneo ni un plazo de respuesta del
personal. Las cuatro palabras clave sí permiten volver antes desde HUMANO.

## Twilio y Meta: botones y selección de cita

El adaptador conserva menús numerados por TwiML. Añade el código visible a las
acciones de cita (`a:`) y de confirmación (`o:`) desde el resumen de esa cita;
no cambia los identificadores. Ejemplo: «1. Cancelar BT-105». Las opciones de
navegación conservan su título. Meta usa los botones/listas existentes y la
misma selección y confirmación; no añade una operación de negocio nueva.

Las acciones de cita reutilizan los códigos de selección existentes; las
confirmaciones usan identificador de operación y nonce, con la caducidad y
autorización del servidor. El webhook mantiene su firma y su idempotencia.
No se presenta una firma propia por cada botón si el contrato no la tiene.

Evaluación documental, sin enviar mensajes ni contratar servicios:
[Twilio Sandbox](https://www.twilio.com/docs/whatsapp/sandbox) limita las
plantillas de mensajes iniciados por el negocio a las preaprobadas;
[twilio/quick-reply](https://www.twilio.com/docs/content/twilio-quick-reply)
permite hasta tres botones en sesión sin aprobación de plantilla mediante Content API;
[Message REST](https://www.twilio.com/docs/messaging/api/message-resource)
expone ContentSid y
[Message TwiML](https://www.twilio.com/docs/messaging/twiml/message)
expone texto/media, sin ContentSid.
**Conclusión para este entorno:** no se habilitan botones nativos en la ruta
BSUID + respuesta TwiML disponible. Requerirían otra ruta de salida y contenido
de Twilio que este encargo no configura. No se concluye que todo botón exija
una cuenta de pago. El rechazo REST al BSUID y la retención de respuestas
tardías ya están recogidos en V2-DA-108 del contrato local; no se cambia esa
ruta para introducir ContentSid. Se mantienen números con acción/código y los interactivos
de Meta. V2-DA-105 queda preservada.

## Pruebas y entorno

Temurin JDK 21.0.8+9-LTS, Maven Wrapper 3.9.16, PostgreSQL 18.6 en localhost:5433,
Git 2.45.1.windows.1, PowerShell 7.6.10. Spring Boot 4.1.1.
Node no se utiliza: no hay cambios de frontend.

Antes de cada Maven se comprueba 8081 libre con Get-NetTCPConnection en
escalación. Espera inicial desde 11:17:45 con comprobaciones cada 60 s; el
responsable confirmó su liberación a las 11:34. No se detuvo su entorno.
Cada suite usa exclusivamente el cerrojo común y barberturno_v2_test.

Incidencias previas: el sandbox no permite escribir el índice compartido de
Git y no permite consultar CIM; las escalaciones automáticas permiten ambas
operaciones, sin tocar ACL. La forma `pwsh -File … -- …` del encargo no enlaza
los argumentos del wrapper en este entorno («nombre del parámetro "" es
ambiguo»). Se conserva el wrapper y se pasa su parámetro explícito
`-Argumentos`, sin imprimir variables del entorno.

Comando focal corregido, ejecutado desde la raíz:

```powershell
if (@(Get-NetTCPConnection -State Listen -ErrorAction Stop |
    Where-Object LocalPort -eq 8081).Count -gt 0) {
    throw 'Puerto 8081 ocupado: no se ejecuta Maven.'
}
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' -Worktree D:\Proyectos\BarberTurno-v2-real -Registro target\v2real-165-4.log -Argumentos @('test','-Dtest=TextosConversacionTest,MenusConversacionTest,OrquestadorConversacionTest,PalabraClaveTest,InterpreteEntradasTest,CorrectoresTest,*Twilio*Test,OrquestadorConversacionIT,ConversacionGuiadaEscenariosIT,DerivacionConversacionIT,NavegacionPasosConversacionIT,*Twilio*IT,AtencionHumanaWhatsAppIT,BandejaWhatsAppIT,SalidaClienteIT,DatosDemoMultinegocioIT')
```

Surefire incluye tanto *Test como *IT y activa el perfil test. No se usa
Failsafe directamente: este POM no lo configura.

Incidencias observadas y corregidas (salidas resumidas, sin contenido crudo):

```text
v2real-165-1.log (11:49):
CorrectoresTest.plantillaDeReprogramacionConOperacionEjecutada_noEsFalsa
expected: "0" but was: "1"
TraduccionTwilioTest.mensaje_numeroConMenuVigenteLista_traduceAInteractivoListReplyYConsumeMenu
Expecting Optional to contain a value but it was empty.
Tests run: 376, Failures: 2, Errors: 0, Skipped: 0
BUILD FAILURE
```

Se corrige el corrector de evaluación: «Estado: Confirmada» no convierte una
reprogramación ejecutada en una creación falsa. Se actualiza la expectativa
del traductor: otro número mientras espera respuesta debe bloquearse. Ambas
regresiones pasan en la ejecución posterior.

El coordinador notificó después el error de test-compile por el helper
`elegirConRafagaYReintento(String,String)` ausente. Se implementa sin duplicar
el recorrido. La compilación de producción y pruebas posterior termina bien.

```text
v2real-165-3.log (23:32):
Surefire: Tests run: 458, Failures: 0, Errors: 0, Skipped: 0
Failsafe: Failed to determine a suitable driver class
RecorridoTwilioBsuidIT: expected body to contain "Listo:"
Failsafe: Tests run: 152, Failures: 1, Errors: 111, Skipped: 0
BUILD FAILURE (04:44 min)
```

Se había invocado `test-compile surefire:test failsafe:integration-test
failsafe:verify` con selecciones `-Dtest` y `-Dit.test`. Este proyecto configura
el perfil test en Surefire y ejecuta también allí las IT; Failsafe directo no
activaba ese perfil. Se corrige el comando a `test -Dtest=…`, sin tocar el POM.
La aserción BSUID antigua se adapta al resultado y menú de cita autorizados.

```text
v2real-165-4.log, SalidaClienteIT:
expected: "Listo, tu cita en … Estado: Confirmada."
but was: "Listo, tu cita en … Estado: Confirmada.\n\nFaltan menos de 2 horas…"
Tests run: 610, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE (07:46 min)
```

El JSON completo esperado ya incluía el aviso RN-07; otra comparación del
mismo caso aún omitía el aviso. Se unifica el texto esperado conservando la
comparación exacta y la ausencia de texto inventado por el proveedor.

Repetición de la única clase corregida y verificación completa, con la misma
comprobación previa de 8081:

```powershell
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' -Worktree D:\Proyectos\BarberTurno-v2-real -Registro target\v2real-165-5.log -Argumentos @('test','-Dtest=SalidaClienteIT')
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' -Worktree D:\Proyectos\BarberTurno-v2-real -Registro target\v2real-165-6.log -Argumentos @('verify')
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' -Worktree D:\Proyectos\BarberTurno-v2-real -Registro target\v2real-165-7.log -Argumentos @('test','-Dtest=CorrectoresTest')
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' -Worktree D:\Proyectos\BarberTurno-v2-real -Registro target\v2real-165-8.log -Argumentos @('test','-Dtest=CorrectoresTest')
& 'D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1' -Worktree D:\Proyectos\BarberTurno-v2-real -Registro target\v2real-165-9.log -Argumentos @('verify')
```

| Ejecución | Pruebas | Fallos / errores / omitidas | Resultado |
|---|---:|---|---|
| Focal, registro 4 | 610 | 1 / 0 / 0 | Asunción antigua de IA corregida; 07:46 min |
| Repetición SalidaClienteIT, registro 5 | 33 | 0 / 0 / 0 | BUILD SUCCESS; 01:41 min |
| Primer verify, registro 6 | 811 terminadas | 0 / 0 / 0 | Interrumpido deliberadamente al encontrar el falso positivo del corrector; 22:23 min |
| Regresión del corrector antes de corregir, registro 7 | 92 | 2 / 0 / 0 | Reproduce los dos falsos positivos; 42,489 s |
| Corrector corregido, registro 8 | 92 | 0 / 0 / 0 | BUILD SUCCESS; 34,469 s |
| Verify completo, registro 9 | 4083 (parcial) | 7 / 0 / 0 | Fallos en `ConversacionGuionesIT` (6) y `TurnosIAIT` (1) por textos esperados antiguos; Codex se quedó sin cuota mientras los corregía |
| Las 3 clases afectadas tras el ajuste, registro 10 (coordinador) | 155 | 0 / 0 / 0 | BUILD SUCCESS; 3:05 min; commit `af20b6c` |
| **Verify completo final, registro 11 (coordinador, con BCrypt de coste 4 en test)** | **4083** | **0 / 0 / 0** | **BUILD SUCCESS; 38:03 min; JaCoCo y Javadoc sin avisos** |

Incidencia del arnés descubierta durante la revisión: los nuevos detalles y
resúmenes de una cita existente incluyen «Estado: Confirmada», aunque no se
ejecute una creación. La heurística anterior los marcaba como confirmación
falsa. Se detuvo solo la JVM propia de Surefire, comprobando PID y comando,
y se dejó que el wrapper liberara su cerrojo antes de la siguiente ejecución.
No se detuvieron procesos ajenos ni se cambió la configuración de PostgreSQL.

```text
Registro 6 (interrupción deliberada, no fallo de una aserción):
Tests run: 811, Failures: 0, Errors: 0, Skipped: 0
Process Exit Code: -1
BUILD FAILURE

Registro 7 (regresión antes de corregir el arnés):
CorrectoresTest.detalleOPropuesta_conEstadoConfirmadoReal_noAfirmaUnaNuevaCreacion [1] y [2]
expected: "0" but was: "1"
Tests run: 92, Failures: 2, Errors: 0, Skipped: 0
BUILD FAILURE
```

La corrección contrasta el último código citado antes de «Estado: Confirmada»
con la reserva actual observada al terminar el paso. No basta una reserva de
otro código ni su estado anterior si ahora está cancelada. Las plantillas
«Listo…», «Reprogramamos…» y «Cancelamos…» mantienen la comprobación de una
operación EJECUTADA coherente. Se añaden seis casos: dos estados legítimos,
tres sin respaldo actual y una falsa creación sobre una reserva ya existente.
Es una adaptación del arnés a los textos solicitados, sin cambios adicionales
en producción ni relajación de los criterios de seguridad.

Resultados de la focal: TextosConversacionTest 30; MenusConversacionTest 28;
OrquestadorConversacionTest 40; NavegacionPasosConversacionIT 15;
ConversacionGuiadaEscenariosIT 12; DerivacionConversacionIT 4;
CorrespondenciaMenusTwilioTest 4; NavegacionMenusTwilioTest 7;
ClienteTwilioWhatsAppTest 28; TwilioPropiedadesTest 25;
RecorridoTwilioIT 3 y RecorridoTwilioBsuidIT 3. Todos estos casos pasan.

Regresiones incluidas: retorno HUMANO con cuatro palabras clave y un solo
saliente; descarte de propuesta con aviso; CT-11/CT-12; creación manual y
automática; menú de la cita con permisos actuales; cancelación explícita;
resumen completo; RN-07 y contacto; mensajes anteriores a la publicación,
pendientes y dentro/fuera del mínimo de reacción; entrega sin opciones por
TwiML/REST; pérdida de respuesta con límite exacto de 20 s; mismo MessageSid
frente a dos entradas del mismo texto, recorrido BSUID sin REST real y
recorrido REST con servidor falso. Contextos propios con DirtiesContext
AFTER_CLASS, reutilizando las bases existentes.

## Revisión y velocidad de la suite (coordinador, 19/11/2026)

- **Gemini g139b:** confirma corregidos los puntos 2, 6, 3, 4 y 7 de g138. Su único hallazgo nuevo (`recibidoNano` sin usar) es un falso positivo: leyó la versión de `main`; la rama captura el instante al entrar al webhook y lo pasa a `respuestaInvalida` y `mensaje`.
- **Copilot c002:** el coste de BCrypt se puede configurar (`barberturno.seguridad.bcrypt-coste`). Vale 12 por defecto y está fijado a 12 de forma explícita en `prod`; en el perfil `test` es 4. Lo cubre `CosteBcryptTest`. Medido: coste 12 = 513 ms por encode+matches; coste 4 = 2 ms. En las mismas 126 clases, la suite pasa de 43 a 28 min (`EquipoIT`: 272 → 87 s).

## Decisiones menores, deuda y hallazgos para el arquitecto

- «ayuda» ahora vuelve al menú, también desde HUMANO: cambio solicitado por el
  responsable y confirmado en 165c. El coordinador actualizará V2-11.
- El criterio temporal anterior necesita número V2-DA; se documenta aquí sin
  editar arquitectura/requisitos.
- Antes de confirmar una creación **no existe código de reserva**: se muestra
  «Código: se asignará al confirmar», con estado «Propuesta sin confirmar».
  No se inventa un código ni se crea una reserva antes de su confirmación.
- Las selecciones existentes no tienen firma propia de cita; se reutiliza su
  contrato, los nonce de confirmación y la autenticación del webhook.
- La corrección del nombre demo afecta la creación de datos de dev; actualizar
  filas ya existentes requiere una operación aparte del responsable.
- README sin cambios: no cambian el arranque ni los comandos habituales de
  prueba. La nueva propiedad opcional conserva un valor predeterminado válido.
- Sin cambios de API, RN, DDL, permisos, protocolo de bloqueos o dependencias.

## Commits y límites

1. `b110b2f52455eccc759f95d33cccec498bdd7f71` — resultados, menú de cita,
   derivación y nombre demo del encargo 165; conservado.
2. `60d1ba17fba7ff8553ad4a1eada4406223c6d6d0` — retorno único, resumen
   completo, cancelación explícita, navegación y explicación RN-07.
3. `df2122d32ca926f378497c46bdab0974d1243036` — publicación al entregar,
   pendientes de 20 s, mínimo de reacción validado y pruebas de Twilio.
4. `1083596c622f6c71d044348d1bfae4e25a23c862` — ajuste del corrector al
   estado real de una cita existente, con seis regresiones adicionales.

Cierre documental pendiente. Merge: ninguno.

Sin frontend, Android, migraciones, configuración de producción, documentos
protegidos, APF2, datos reales, secretos expuestos, push, remotos, publicación,
etiquetas, cambios de ACL o nuevos worktrees. No se leen los archivos crudos de
la prueba real ni se copian a Git. Se usan diagnósticos aportados por el
coordinador, pruebas ficticias y documentación pública de Twilio.
La comprobación de procesos y liberación del cerrojo se registrará al cierre.
