# Encargo 131 · Correcciones de la revisión 129 · V2-11/V2-12

Fecha: 05/11/2026. Rama: `tarea/V2-12-correcciones-129`. Base: `1eaeafa0890eaedd3f25a48dfa604d512d9175e0`. Implementación de los defectos 1–4 y 13 descritos en el encargo y de V2-DA-84, sin integración ni publicación.

## Cambios y regresiones

- `GuardasSalida.java`: rechaza importes (`S/`, soles, PEN y `$`) y enlaces, incluidos dominios sin esquema; teléfonos con puntos, paréntesis, guiones y espacios Unicode. Excluye las fechas con guiones del detector de teléfono y las comprueba como fechas. Conserva las propuestas y solicitudes de confirmación; detecta participios y formas personales de las operaciones. Distingue el pretérito acentuado del subjuntivo, incluso en «la cita que confirmé» y con tilde descompuesta. Las horas digitales sin período son exactas; las habladas conservan las dos lecturas. Las fechas ISO y las referencias reconocidas pero imposibles se rechazan si no corresponden a las franjas. Cada hora se asocia a la fecha de su opción, también con la fecha detrás de la hora.
- `GuardasSalidaTest.java`: regresiones positivas y negativas de cada grupo anterior, incluidas ambas opciones correctas viernes 17:00/sábado 17:30, las horas intercambiadas, `05:00` contra una franja de 17:00 y contra una franja de 05:00, fechas y horas imposibles, años incompletos, teléfonos con U+00A0/U+202F/U+2003 y propuestas válidas.
- `bandeja.ts`: revisión de selección más id de conversación; invalida consultas, paginación, respuestas POST, errores y finalizadores de visitas anteriores, incluso A → B → A. Al cambiar, limpia detalle, borrador, avisos y estados de carga/envío. Las consultas sucesivas de lista/detalle aplican solo su última respuesta. La paginación conserva los mensajes del detalle actual. La confirmación y el resultado de devolver al asistente también quedan asociados a la selección original.
- `bandeja.spec.ts`: 24 pruebas en total. Respuestas GET de A antes/después de B, errores tardíos 404/500, A → B → A, consultas del mismo detalle fuera de orden, paginación tardía con éxito/error, POST de A pendiente al seleccionar y enviar en B, errores de POST 404/422/500 y devolución/diálogo pendientes al cambiar. Comprueba texto, id/ruta/cuerpo del POST, borrador, carga, envío y ausencia de efectos en B.
- Sin cambios en plantilla, estilos, API, `MotivoGuarda`, DDL, permisos ni reglas de negocio del frontend. Javadoc de la clase y de `revisar` actualizado con V2-DA-84, significado de la fecha actual y condición de `NullPointerException`; constructor documentado conservado.

## Entorno y órdenes

Windows/PowerShell; Temurin **21.0.8+9**, Maven **3.9.16**, Spring Boot **4.1.1**, Node **24.21.0** por fnm. Vitest **5.0.3**. Maven mediante `backend/mvnw.cmd`; sin instalar dependencias ni cambiar Node global. Sin PostgreSQL ni API real de Anthropic.

Inicialización de Java y Node usada en cada proceso correspondiente:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
& 'C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe\fnm.exe' env --use-on-cd --version-file-strategy recursive --shell powershell | Out-String | Invoke-Expression
fnm use
```

Los registros están en `backend/target/`, ignorado por Git. Se obtuvieron con `2>&1 | Tee-Object -FilePath <registro>` y se propagó `$LASTEXITCODE`.

| Directorio | Orden | Resultado | Registro |
|---|---|---|---|
| `backend` | `.\mvnw.cmd -Dtest=GuardasSalidaTest,ComposicionTurnoIATest,SaneadorTextoTest "-Djacoco.skip=true" test` | Primera ronda: 182/0/0/0; segunda: 188/0/0/0, ambas `BUILD SUCCESS` | `encargo-131-unitarias.log`, `encargo-131-unitarias-final.log` |
| `backend` | Misma orden, regresiones adicionales de pretérito y año incompleto | 196 pruebas, **6 fallos**, 0 errores/omisiones; corregidos los límites Unicode del pretérito | `encargo-131-unitarias-cierre.log` |
| `backend` | Misma orden, versión corregida | **196/0/0/0**, `BUILD SUCCESS`, 36,930 s; Guardas 170, Composición 13, Saneador 13 | `encargo-131-unitarias-verificadas.log` |
| `backend` | `.\mvnw.cmd -DskipTests verify` | Primera ejecución: `BUILD SUCCESS`, 41,330 s; cierre sobre el código final: **BUILD SUCCESS**, **26,470 s**, Javadoc sin avisos | `encargo-131-verify-sin-pruebas.log`, `encargo-131-verify-verificado.log` |
| `frontend` | `npm run lint` | Código 0; `All files pass linting`, incluida comprobación TypeScript de E2E | `encargo-131-lint.log` |
| `frontend` | `npm test -- --watch=false --include 'src/app/features/bandeja-whatsapp/bandeja.spec.ts'` | Código 0; 1 archivo y **24/24** pruebas, 46,69 s | `encargo-131-frontend-test-final.log` |
| `frontend` | `npm run build` | Código 0; 20,665 s, inicial **499,56 kB** | `encargo-131-build.log` |
| `frontend` | `node_modules/.bin/prettier.cmd --write src/app/features/bandeja-whatsapp/bandeja.ts src/app/features/bandeja-whatsapp/bandeja.spec.ts` | Código 0; solo ambos archivos autorizados | Salida de consola |
| raíz | `git diff --check` y comprobación de bytes | Sin errores; los cuatro archivos de código son UTF-8 sin BOM y LF | Salida de consola |

No se ejecutaron suites con base ni `ConversacionGuionesIT`. Las unitarias se ejecutaron sin agente JaCoCo por la orden recibida; `verify -DskipTests` omite Surefire/Failsafe y JaCoCo informa ausencia de datos de ejecución. Esto acredita empaquetado y Javadoc, no cobertura global ni integración con PostgreSQL. Los mensajes informativos de javac sobre APIs obsoletas/operaciones no comprobadas pertenecen a clases preexistentes; no son avisos de Javadoc.

Salida final del backend:

```text
[INFO] Tests run: 170, Failures: 0, Errors: 0, Skipped: 0 -- in GuardasSalidaTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in ComposicionTurnoIATest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in SaneadorTextoTest
[INFO] Tests run: 196, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 36.930 s

[INFO] --- javadoc:3.12.0:javadoc-no-fork (javadoc-verificacion) @ barberturno ---
[INFO] BUILD SUCCESS
[INFO] Total time: 26.470 s
```

## Incidencias y resolución

1. Git dentro del sandbox terminó con `fatal: detected dubious ownership in repository at 'D:/Proyectos/BarberTurno-v2-12-r129'`. Se ejecutó con escalación aprobada automáticamente, sin modificar `safe.directory`, ACL ni propietarios.
2. El wrapper dentro del sandbox no pudo iniciar Maven; fnm no pudo crear su enlace de sesión. Se repitieron las órdenes con escalación aprobada automáticamente. Salidas relevantes iniciales:

```text
icm : No se puede indizar en una matriz nula.
FullyQualifiedErrorId : NullArray,Microsoft.PowerShell.Commands.InvokeCommandCommand
Cannot start maven from wrapper

error: Can't create the symlink for multishells at "C:\\Users\\limas\\AppData\\Local\\fnm_multishells\\65484_1793855804297". Maybe there are some issues with permissions for the directory? Acceso denegado. (os error 5)
Invoke-Expression: No se puede enlazar el argumento al parámetro "Command" porque es una cadena vacía.
error: We can't find the necessary environment variables to replace the Node version.
node: El término 'node' no se reconoce como nombre de un cmdlet, función, archivo de script o programa ejecutable.
npm: El término 'npm' no se reconoce como nombre de un cmdlet, función, archivo de script o programa ejecutable.
```

3. El glob de prueba indicado en el encargo incluye HTML/SCSS con este Angular CLI. Se conserva el fallo (`encargo-131-frontend-test.log`, código 1) y se sustituye únicamente el argumento por el spec explícito, cubriendo toda la suite de la bandeja:

```text
Application bundle generation failed. [42.960 seconds]
X [ERROR] No loader is configured for ".html" files: src/app/features/bandeja-whatsapp/bandeja.html
X [ERROR] No loader is configured for ".scss" files: src/app/features/bandeja-whatsapp/bandeja.scss
X [ERROR] TS6054: File 'D:/Proyectos/BarberTurno-v2-12-r129/frontend/src/app/features/bandeja-whatsapp/bandeja.html' has an unsupported extension. The only supported extensions are '.ts', '.tsx', '.d.ts', '.cts', '.d.cts', '.mts', '.d.mts'.
  The file is in the program because:
    Root file specified for compilation [plugin angular-compiler]
X [ERROR] TS6054: File 'D:/Proyectos/BarberTurno-v2-12-r129/frontend/src/app/features/bandeja-whatsapp/bandeja.scss' has an unsupported extension. The only supported extensions are '.ts', '.tsx', '.d.ts', '.cts', '.d.cts', '.mts', '.d.mts'.
  The file is in the program because:
    Root file specified for compilation [plugin angular-compiler]
```

4. `rg` no está en el PATH: se usaron `Select-String` y `Get-ChildItem`. Una primera lectura por rangos de §5 encontró varias cabeceras coincidentes y falló al calcular el intervalo; se volvió a leer con números de línea explícitos. Una búsqueda inicial de guiones bajo `resources/guiones` no encontró el directorio; se localizaron en `resources/conversaciones`.

5. Las seis regresiones añadidas de pretérito en una subordinada detectaron que `UNICODE_CASE` no basta para que `\\b` incluya la letra acentuada en Java 21. Se corrigió el patrón con `UNICODE_CHARACTER_CLASS`, que también implica el tratamiento Unicode de mayúsculas. Salida del fallo antes de corregir (el registro original presenta algunas tildes con caracteres de sustitución al capturar la consola):

```text
[ERROR] Tests run: 170, Failures: 6, Errors: 0, Skipped: 0 -- in GuardasSalidaTest
expected: Sustituido[motivo=AFIRMA_OPERACION]
 but was: Aceptado[texto=La cita que reservé ya está lista.]
expected: Sustituido[motivo=AFIRMA_OPERACION]
 but was: Aceptado[texto=La cita que confirmé ya está lista.]
expected: Sustituido[motivo=AFIRMA_OPERACION]
 but was: Aceptado[texto=Después de que cancelé tu cita, no hay nada pendiente.]
expected: Sustituido[motivo=AFIRMA_OPERACION]
 but was: Aceptado[texto=La cita que reprogramé ya está lista.]
expected: Sustituido[motivo=AFIRMA_OPERACION]
 but was: Aceptado[texto=La cita que agendé ya está lista.]
expected: Sustituido[motivo=AFIRMA_OPERACION]
 but was: Aceptado[texto=La cita que confirme\u0301 ya está lista.]
[ERROR] Tests run: 196, Failures: 6, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
[INFO] Total time: 44.629 s
```

6. La consulta CIM de procesos dentro del sandbox terminó con `Get-CimInstance: Acceso denegado`; se repitió con escalación para comprobar el cierre sin mostrar líneas de comando ni valores sensibles.

## Decisiones menores, límites y hallazgos para el arquitecto

- Se eligió comprobar id y revisión de selección en cada callback, sin cancelar ni reenviar un POST. Un POST ya iniciado sigue dirigido a A y puede completarse en el servidor después de seleccionar B; su resultado no modifica B. La revisión evita que una respuesta antigua afecte a una nueva visita al mismo id. Todo se mantiene dentro de los permisos devueltos por el servidor.
- Las consultas de lista y detalle tienen además un contador para evitar que una respuesta anterior sustituya a la más reciente; sus finalizadores solo terminan la carga que corresponda. La misma protección se aplica a la devolución al asistente, que compartía la causa del defecto 13.
- Los teléfonos usan la misma familia de separadores del saneador, con la bandera Unicode para incluir espacios no separables. **Coordinación g097:** el `SaneadorTexto` de esta base todavía usa `\\s` sin bandera Unicode; debe incorporar ese ajuste fuera de este encargo para que ambas capas coincidan. No se modificó ese archivo.
- **Defecto editorial preexistente:** `17-v2-12-13-ia-y-evaluacion.md:103` inserta el comienzo de una copia del propio documento dentro de la lista de datos prohibidos; hay dos cabeceras §5 (líneas 99 y 201), y la segunda lista de datos (205) está truncada. Se aplica V2-DA-84, inequívoca y también reproducida en el encargo. No se modifica el documento protegido.
- El encargo combina «no leas `.local/coordinacion/`» con una petición de leer un informe dentro de esa carpeta. Se respetó la prohibición: no se accedió a coordinación ni a ningún archivo `.local`; los defectos se implementaron a partir de su descripción explícita, los documentos v2 y el código.
- Se respetó la excepción concreta de flujo: misma rama, commits sin merge ni push y sin cambiar `docs/tareas.md`, backlog, README o documentos de diseño. No hay cambios de arranque.
- **Guiones:** se inspeccionaron en solo lectura los 11 YAML existentes en `backend/src/test/resources/conversaciones/`. No se prevé cambiar sus resultados por estas correcciones: 01 sigue rechazando la hora no ofrecida y 02 la afirmación de confirmación; 03/04 conservan los textos de propuesta. No se ejecutó ni modificó la suite de Gemini. Cualquier guion nuevo con precios/enlaces, teléfonos con estos separadores, pretéritos o participios ahora puede dar `DATOS_PROHIBIDOS`/`AFIRMA_OPERACION`; las fechas ISO/horas digitales antes confundidas pueden dar `HORARIO_NO_OFRECIDO`, y dos opciones correctas de días distintos pasan. Corresponde a g097 contrastar el conjunto ajustado.
- Riesgo residual y deuda: estas son guardas deterministas de patrones, no una prueba de comprensión de cualquier paráfrasis; se conserva la composición de franjas y resultados por el servidor. Revisión independiente e integración del MVP a cargo del coordinador; sin afirmar validación con el proveedor real, cobertura global o BD.

## Commits y cierre

- `38919d754c1b10c8a21e48b4270dbc1364902a1c` · `fix(whatsapp): aislar respuestas de la conversación seleccionada [V2-11]` · componente y pruebas de bandeja.
- `2036e6d22e98a8cdba63862b830c89a0b9175276` · `fix(ia): reforzar guardas de datos y referencias temporales [V2-12]` · guardas y regresiones.
- Esta evidencia se añade en el commit `docs: registrar correcciones y pruebas de la revisión 129 [V2-12]`; su hash se entrega en el informe final y se obtiene con `git log -1 --format=%H -- docs/v2/pruebas/v2-12-correcciones-129.md`.
- Sin merge: expresamente excluido del encargo. `git diff --check` ejecutado antes de cada commit. Rama conservada.

Solo se han escrito los cuatro archivos de código y esta evidencia, además de salidas ignoradas de herramientas. Sin cambios en APF2, entregables, evidencias v1, bases, Azure, requisitos, arquitectura, AGENTS o CLAUDE; sin secretos, datos personales reales, remotos, etiquetas, servicios externos o pagos. No se instalaron dependencias, crearon worktrees ni iniciaron servidores. Todas las sesiones de verificación terminaron. Comprobación CIM posterior: ningún Java, navegador o Node de pruebas/Angular asociado a este worktree; se preserva el Node preexistente PID 178628 (creado a las 00:11:04, antes de las verificaciones, sin marcadores de Vitest/Angular/pruebas/servidor). Sin procesos propios pendientes.

## Correcciones de g098 y V2-DA-85

**Encargo 132 · 05/11/2026 · terminado.** Misma rama. Estado inicial limpio y `git log -3` comprobado: los merges del coordinador `125caec` (V2-DA-85, contrato reparado y correcciones de Claude) y `5482ae3` (g097, guiones/arnés) ya estaban presentes. Base de esta continuación: `5482ae38543fe9dbceedce8bd304594dc1e5fd17`. No se realizó ningún merge propio.

Se leyeron AGENTS, la Definición de Hecho, V2-DA-85, el §5 reparado del contrato 17, el contrato de bandeja y el nuevo saneador. Por la referencia expresa del encargo 132 se leyó únicamente el informe `g098-revision-131.md` indicado, sin explorar otras rutas de coordinación ni mostrar valores de configuración local. Los hallazgos editoriales y la divergencia Unicode anotados en 131 pertenecen a la base anterior: el coordinador ya trajo sus correcciones.

### Implementación

- **GuardasSalida.java:** de 620 a 133 líneas. Se retiran la extracción/validación de combinaciones, `esValido`, `fechasPara`, resolución de días y años, mapas de franjas y patrones propios de teléfono/correo. Cualquier referencia temporal detectada se sustituye incluso si coincide con las franjas. Se conserva la firma pública; Javadoc de clase, constructor y `revisar` documenta V2-DA-85 y que `franjas`/`ahora` solo permanecen por compatibilidad, sin uso. Se mantiene el orden vacío → longitud → datos → afirmaciones → horarios y el texto aceptado intacto.
- **Detección temporal:** se reconocen días, meses, relativos y expresiones habladas de hora, además de cualquier cifra. Esto cubre `17h`, `17h30`, `17 hs`, `18 horas`, `5 a. m.`, «cinco treinta», «diecisiete treinta», «próximo viernes» y fechas con guiones Unicode, sin interpretarlos. Se acepta conscientemente sustituir números de opciones y duraciones, como permite la decisión conservadora. Un patrón de forma de fecha solo evita confundirla con un teléfono en la guarda previa: no valida calendarios, años ni disponibilidad.
- **Afirmaciones:** formas personales, participios y tiempos de reservar/confirmar/cancelar/reprogramar/agendar; preguntas y negaciones quedan fuera de esa guarda. Se conserva la revisión de datos y horarios dentro de preguntas/negaciones: «¿Te reservo el viernes a las 17:00?» da `HORARIO_NO_OFRECIDO`, mientras «¿Te reservo ese turno?» se acepta. Las afirmaciones fuera de una pregunta o posteriores a «pero», «y ya…» o a otra cláusula siguen rechazándose. Se distinguen nombres e instrucciones («tu reserva», «confirma con el botón») de afirmaciones. Las tildes distinguen indicativo y subjuntivo.
- **Datos personales e importes:** llamadas a `SaneadorTexto.contieneDatoPersonal`, sin otro detector personal. Se canonizan espacios alrededor de separadores antes de una segunda llamada al mismo helper para cubrir `987 - 654 - 321` sin cambiar el saneador, cuyo patrón conserva un máximo de dos separadores consecutivos. Se añaden «1 sol», «25 sol» y «25 lucas», incluidos decimales. Los datos personales de los tests siguen siendo ficticios.
- **GuardasSalidaTest.java:** expectativas de horarios antes admitidos actualizadas a V2-DA-85 y nombres ajustados; regresiones de los textos de g098, personas/tiempos, preguntas, negaciones y sus límites, Unicode, importes y parámetros de compatibilidad sin uso. **271 casos** de guardas, con comprobaciones con/sin franjas en los nuevos casos temporales.
- **bandeja.ts:** `mensajeAccion` se conserva al navegar a la lista y se limpia al seleccionar otra conversación. Un POST confirmado invalida las consultas de detalle anteriores y cierra su estado de carga, por lo que el sondeo no borra el mensaje creado. La lista se refresca antes de descartar efectos de un POST de una conversación anterior; también al terminar una devolución anterior. No cambia el destino de ningún POST ni los permisos recibidos del servidor.
- **bandeja.spec.ts:** **28 casos**. La devolución emite `paramMap` vacío desde la navegación espiada y comprueba el aviso en `aria-live`. Nuevas regresiones de dos consultas de lista fuera de orden con éxito/error y sondeo previo al POST con éxito/404. Las pruebas de cambio A → B comprueban también la recarga lateral tras los POST tardíos.

### Verificación y registros

Mismo entorno que 131: Temurin 21.0.8+9, wrapper Maven 3.9.16, Spring Boot 4.1.1, Node 24.21.0 por fnm y Vitest 5.0.3. Los guiones usaron PostgreSQL **18.6** en la base de pruebas v2 y el adaptador falso, nunca Anthropic real. Inicialización de Java/fnm igual a la sección anterior. Las órdenes se ejecutaron con escalación por las restricciones de entorno ya comprobadas en 131, sin tocar ACL, propietarios ni configuración.

| Directorio | Orden | Resultado | Registro bajo `backend/target/` |
|---|---|---|---|
| `backend` | `.\mvnw.cmd -Dtest=GuardasSalidaTest,ComposicionTurnoIATest,SaneadorTextoTest "-Djacoco.skip=true" test` | Primera ronda: **298/1/0/0**, corregida; final **303/0/0/0**, `BUILD SUCCESS`, 36,608 s (271 guardas + 13 composición + 19 saneador) | `encargo-132-unitarias-inicial.log`, `encargo-132-unitarias-final.log` |
| `backend` | `.\mvnw.cmd -DskipTests verify` | **BUILD SUCCESS**, 28,138 s, Javadoc/doclint sin avisos | `encargo-132-verify.log` |
| raíz | `pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-12-r129 -Registro target\encargo-132-guiones.log "-Dtest=ConversacionGuionesIT" test` | Ejecutor serializado, `mvnw rc=0`; **24/0/0/0**, `BUILD SUCCESS`, 2:07 min | `encargo-132-guiones.log` |
| `frontend` | `npm test -- --watch=false --include src/app/features/bandeja-whatsapp/bandeja.spec.ts` | **28/28**, 1 archivo, código 0, 27,41 s | `encargo-132-frontend-test.log` |
| `frontend` | `npm run lint` | Código 0; `All files pass linting`, TypeScript E2E conforme | `encargo-132-lint.log` |
| `frontend` | `npm run build` | Código 0; 30,563 s; inicial **499,56 kB** | `encargo-132-build.log` |
| `frontend` | `node_modules/.bin/prettier.cmd --write src/app/features/bandeja-whatsapp/bandeja.ts src/app/features/bandeja-whatsapp/bandeja.spec.ts` | Solo los dos archivos autorizados | Consola |
| raíz | `git diff --check`, comprobación de bytes y CIM | Sin errores; UTF-8 sin BOM y LF; ningún proceso Java/Node/navegador asociado al worktree al cerrar | Consola |

Se reutilizó la configuración existente de `ConversacionGuionesIT` (con `@DirtiesContext`), sin añadir contextos o pools; ninguna otra suite ni aplicación se ejecutó contra PostgreSQL durante los guiones. Las unitarias y el empaquetado se completaron antes de iniciar el ejecutor serializado. No se ejecutó el arnés en vivo, ni una batería global con BD, ni se acredita cobertura global con este `verify -DskipTests`.

**Único fallo funcional intermedio:** el detector confundía el pronombre «Tú» con el posesivo «tu» al permitir nombres/instrucciones. La regresión «Tú confirmas tu cita.» esperaba `AFIRMA_OPERACION` y se aceptaba. Se separaron esas excepciones y se preservó la tilde del sujeto. Salida resumida del fallo (tildes del texto reconstruidas por la codificación de consola):

```text
[ERROR] Tests run: 266, Failures: 1, Errors: 0, Skipped: 0 -- in GuardasSalidaTest
expected: Sustituido[motivo=AFIRMA_OPERACION]
 but was: Aceptado[texto=Tú confirmas tu cita.]
[ERROR] Tests run: 298, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

Después de corregirlo y añadir regresiones de negación coordinada, «no solo», «sin problemas» y futuro en subordinadas:

```text
[INFO] Tests run: 303, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] --- javadoc:3.12.0:javadoc-no-fork (javadoc-verificacion) @ barberturno ---
[INFO] BUILD SUCCESS
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0 -- in ConversacionGuionesIT
[INFO] BUILD SUCCESS
mvnw rc=0
```

### Guiones, decisiones y cierre

- Los **12 YAML de g097 y sus 24 pruebas** pasan sin modificar guiones ni su suite. **Ningún guion afectado con espera incompatible** en esta base: 01 conserva `sustitucionGuardas: HORARIO_NO_OFRECIDO`, 02 conserva `AFIRMA_OPERACION` y 03/04 conservan propuestas y confirmaciones deterministas. Se respetó la propiedad de Gemini.
- Riesgo aceptado por V2-DA-85: se sustituye de más el texto con cifras, duraciones o referencias temporales ambiguas, manteniendo la lista compuesta por el servidor. Las afirmaciones siguen basadas en patrones y contexto de cláusula; estas regresiones no constituyen una garantía de comprensión de cualquier paráfrasis. No se introducen contratos, DDL, reglas de negocio ni dependencias nuevas.
- El patrón compartido del saneador no se modificó: la canonización de separadores en salida resuelve el caso de tres separadores de g098 con el helper existente. No queda una segunda expresión de teléfono que mantener en las guardas.
- `fe7eaa598f5cbf5e14d12c16bb58bcaa1c651b86` · `fix(ia): simplificar guardas según V2-DA-85 y revisión g098 [V2-12]`.
- `9724b6980bbd0bd81ce5adfc90666c09684419df` · `fix(whatsapp): conservar avisos y respuestas tras el sondeo [V2-11]`.
- Esta sección se registra en `docs: registrar cierre de g098 y V2-DA-85 [V2-12]`; hash en el informe final y mediante `git log -1 --format=%H -- docs/v2/pruebas/v2-12-correcciones-129.md`.
- Sin cambio de rama, merge propio, push, publicación, tags, remotos, instalaciones ni worktrees nuevos. Solo los cuatro archivos de código autorizados y esta evidencia, más salidas ignoradas de herramientas. No se tocaron `docs/tareas.md`, backlog, README, documentación protegida, APF2, entregables, evidencias v1, Azure ni bases v1. Sin secretos ni datos reales expuestos. Todas las sesiones de verificación finalizaron y la comprobación CIM no encontró procesos Java/Node/navegador asociados al worktree; sin servidores ni procesos propios pendientes.
