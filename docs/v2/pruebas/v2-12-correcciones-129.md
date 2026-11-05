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
