# Diagnóstico Java de v2 y comparación del pom sin guardar · 06/11/2026

## Resultado y alcance

**Se reproducen 125 de las 127 advertencias de main en una compilación JDT de v2.** Las otras dos son importaciones de `Barbero` que v2 sí utiliza. Se identificaron **270 advertencias y 0 errores en total en esa ejecución de JDT**. No es un recuento verificado del panel vivo de VS Code de v2.

No se modifica código de producción ni de pruebas: tras contrastar individualmente los **siete posibles accesos nulos**, no se encontró un camino ejecutable de NPE en los puntos señalados. El resto conserva su clasificación y sus límites de revisión; **no se declara que las 270 sean falsas ni que pasar pruebas las descarte**.

Cambios de este encargo: esta evidencia y los dos inventarios JSON. Revisión de Claude pendiente. Sin integración, push, intervención en Azure, cambios de dependencias o de contratos, modificaciones de entregables ni acceso a las bases de v1.

## Ramas, responsables y entorno

- `main` académico: `D:\Proyectos\BarberTurno`, SHA `efd52aa19759017f24e3e171ecf626df33dc9c9d`, árbol versionado limpio. Sus 127 advertencias siguen visibles; no se cambia su configuración ni el análisis.
- Coordinación consultada: `AGENTS.md`, `CLAUDE.md` y sección vigente de `.local/coordinacion/estado.md`. La demo corresponde a Claude.
- Línea actual real de v2: `D:\Proyectos\BarberTurno-v2-demo`, rama `tarea/V2-DEMO-azure`, **HEAD `b505ccf05cf53e9388af8802a61c9cb106e5432c`**, limpio antes y después. La rama denominada `v2` sigue en `ccdbb29` y no se tomó como base actual.
- Worktree propio: **`D:\Proyectos\BarberTurno-v2-diagnosticos`**, rama **`tarea/V2-DIAG-advertencias-java`**, creado desde ese SHA completo y sin modificar archivos del worktree de Claude. La evidencia vive solo en esta rama.
- Maven wrapper 3.9.16, Temurin 21.0.8+9; dependencias ya disponibles, ejecución offline. El proyecto mantiene `release 21` y Spring Boot 4.1.1.
- Reproducción estática: extensión Red Hat Java 1.58.0, compilador `org.eclipse.jdt.core.compiler.batch_3.46.200.v20261008-1115.jar`, empaquetado en esa extensión. Se verificó que `org.eclipse.jdt.core_3.48.0` depende del bundle del compilador batch y que este contiene `ReferenceExpression.class`.
- Se copiaron al worktree propio las preferencias locales ya existentes de main, sin desactivar avisos. JDT usa el classpath de pruebas real generado por Maven y las mismas preferencias de nulabilidad del proyecto importado de main. No se alteró ninguna preferencia global.
- No se leyó ningún archivo de secretos. No se ejecutaron pruebas con PostgreSQL ni scripts que cargan credenciales. No se usó la base compartida ni fue necesario tomar su cerrojo.

## `pom.xml` del editor, disco y HEAD

Se accedió al contenido completo del editor de main mediante copia desde el editor enfocado, sin guardar ni descartar. La lectura accesible directa no ofrecía el contenido completo. Las tres capturas están en `D:\Proyectos\BarberTurno\.local\coordinacion\diagnostico-vscode-v2-20261106\pom-{editor,disco,head}.xml.txt`.

| Comparación | Resultado |
| --- | --- |
| Disco frente a `HEAD:backend/pom.xml` | Idénticos, 10 901 caracteres. |
| Búfer frente a disco, normalizando CRLF de la copia a LF | Una sola línea vacía antes de `<?xml ...?>`; el resto es idéntico. |
| Validación XML en memoria de disco | Bien formado. |
| Validación XML en memoria del búfer | Rechaza la declaración XML en línea 2 porque hay espacio anterior. |
| Estado al terminar la comparación | **Sin guardar**, con esa línea inicial conservada. |

La automatización de lectura introdujo temporalmente una `a` antes de la declaración. Se deshizo **solo esa inserción**, comparando el resultado con la captura anterior: se verificó que se retiró exclusivamente el carácter introducido y que el resto, incluida la línea vacía previa, permaneció. No se guardó, descartó ni corrigió el cambio previo del búfer.

**Pendiente del responsable:** decidir si retira manualmente la línea vacía del editor. No debe confundirse ese búfer con el pom en disco sobre el que Maven compiló. La evidencia de XML inválido corresponde exclusivamente al contenido sin guardar.

## Cómo se contrastaron los diagnósticos

Se abrió una ventana separada del worktree propio, pero se cerró antes de disponer de un panel de v2 comprobable; el extension host terminó con código 0. No se atribuye una causa sin evidencia ni se insiste en abrir ventanas. Main conserva su ventana y su búfer.

Como alternativa se ejecutó el compilador JDT local sobre las **501 fuentes Java** de `backend/src/main/java` y `backend/src/test/java`, con `--release 21`, UTF-8, `-proc:none`, preferencias existentes, classpath de Maven, `-d none` y salida XML. No se desactivó ninguna categoría de aviso. La configuración de proyectos, el contexto incremental y los avisos mostrados por el editor pueden diferir del modo batch; no se afirma que el panel de v2 muestre exactamente 270.

Se comparó el JSON exportado anteriormente del panel real de main con la salida XML actual de JDT. Cada coincidencia requiere **mismo archivo relativo, mismo código y mismo mensaje**, consumiendo una ocurrencia por diagnóstico para no duplicar coincidencias. Se conserva la línea de main y la línea correspondiente de v2.

| Advertencias de main | Total main | Reproducidas en JDT de v2 |
| --- | ---: | ---: |
| Nulabilidad en referencias a métodos | 94 | 94 |
| Posibles accesos nulos | 2 | 2 |
| Genérico acotado por `CsrfFilter` final | 1 | 1 |
| Importaciones sin uso | 19 | 17 |
| Variable local sin uso | 1 | 1 |
| APIs obsoletas | 10 | 10 |
| **Total** | **127** | **125** |

Las dos no emitidas tienen explicación concreta en v2: `CancelacionIT.java:25` importa `Barbero`, usado en `new Barbero(...)` en la línea 215; `ReprogramacionIT.java:28` lo importa y lo usa en la línea 163. No se copió ninguna limpieza desde main.

La comparación individual está en [diagnostico-java-20261106-comparacion.json](diagnostico-java-20261106-comparacion.json). Los otros 145 avisos de esta ejecución no figuran en el inventario inicial de main; eso **no prueba que todos se hayan introducido en v2**, pues también cambia el contexto de análisis.

## Posibles accesos nulos: análisis de los siete puntos

| Ubicación en v2 | Camino y protección comprobados | Decisión y validación |
| --- | --- | --- |
| `common/error/ManejadorErrores.java:302`, `anterior.initCause` | La rama `raiz != null` implica que ya terminó una iteración que asignó `anterior = segura`, siendo `segura` un objeto nuevo. Una excepción interrumpe el método; no continúa a una iteración con la asignación omitida. El conjunto de identidad detiene las causas repetidas. | Conservar código y aviso. Prueba focal existente de traza encadenada y saneada correcta; el argumento de no nulidad procede del flujo. |
| `conversation/OrquestadorConversacion.java:410`, `franja.servicioId` | `inicio = franja == null ? null : instante(...)`. Antes del acceso, `if (inicio == null)` retorna. Por tanto alcanzar la propuesta con `inicio != null` implica `franja != null`. No hay reasignación posterior de esos locales. | Conservar. Dos pruebas focales existentes: aceptar con franja pendiente y aceptar sin aviso; ambas correctas. La correlación entre locales explica el aviso. |
| `auth/AuthIT.java:225`, `nueva.getPath` | `assertThat(nueva != null && ...).isTrue()` es una aserción dura de `Assertions`, no una aserción diferida. Si la cookie es null, el cortocircuito y `AssertionError` impiden llegar al acceso. | Conservar. IT con BD **no ejecutada**. No se usa como evidencia una ejecución anterior de main. |
| `common/tenancy/CupoBarberosIT.java:50`, `primero.getId` | El bucle tiene diez iteraciones y asigna `primero = perfil(...)`. `perfil` retorna `saveAndFlush` de un `Barbero` nuevo. Si la persistencia lanza, no se alcanza `getId`. | Conservar. IT con BD **no ejecutada**. |
| `conversation/ia/ConversacionGuionesIT.java:528` y `:529`, desempaquetado de `turnoIdPaso` | Inmediatamente antes se exige `assertThat(turnoIdPaso).isNotNull()` con AssertJ duro. Una referencia nula aborta antes de llamar a `buscarTurno(long)`. | Conservar ambos avisos. IT con BD **no ejecutada**. |
| `conversation/MenusConversacionTest.java:477`, `botonPersona.path` | Se afirma que hay exactamente un botón y después `assertThat(botonPersona).isNotNull()` antes del acceso. Si no se asignó el botón, la prueba se interrumpe antes. | Conservar. Prueba focal de composición del menú correcta. |

Son conclusiones locales sobre esas desreferencias y las firmas inspeccionadas, **no una garantía global de ausencia de errores nulos en v2**. Tampoco justifican anotar todas las entidades o sustituir los flujos para satisfacer al analizador.

## Advertencias restantes y justificación de conservación

El [inventario completo de 270 avisos](diagnostico-java-20261106-inventario.json) incluye archivo relativo, línea, identificador, código, severidad, mensaje, causa, decisión y estado de revisión de cada entrada.

| Cantidad | Categoría | Causa y tratamiento |
| ---: | --- | --- |
| 179 | Nulabilidad en referencias a métodos | La interfaz funcional genérica no acredita la no nulidad del receptor implícito o del valor desempaquetado. Se comprobaron los flujos priorizados y se mapearon las 94 coincidencias de main; **no se afirma una revisión exhaustiva de los 179 contratos**. Conservar y dejar los restantes para revisión específica, sin anotaciones ni supresiones generales. |
| 7 | Posibles accesos nulos | Análisis individual arriba; no se demuestra un camino nulo en esos puntos. Se conservan los avisos. |
| 1 | Genérico acotado por tipo final | `ObjectPostProcessor<T>` exige `<O extends T> O postProcess(O)`. `CsrfFilter` es final. Se verificaron las firmas reales de Spring Security 7.1.1; no cambiar CSRF para eliminar una advertencia de forma. |
| 30 | APIs obsoletas disponibles | Avisos legítimos de mantenimiento. Compilar no demuestra que su comportamiento sea correcto en todos los escenarios. No se actualizan versiones ni se migra el código por el solo aviso. |
| 44 | Importaciones, locales, campos o métodos privados sin uso | Incluye 30 importaciones, 8 locales, 3 campos y 3 métodos. Un valor sobrante no elimina los efectos del inicializador. Los helpers de prueba sin uso pueden justificar revisar cobertura; no demuestran por sí solos un fallo del producto ni autorizan una limpieza general. |
| 6 | `serialVersionUID` ausente | Clases que heredan o implementan `Serializable` sin identificador explícito. Se registra el riesgo de compatibilidad de serialización; no se verificó que estas clases se serialicen binariamente en un flujo del producto. |
| 2 | Conversión genérica no comprobada de JPA | `DirectorioNegocios.java:72` pide mapeo `Object[].class`, y `ColaEventosWhatsApp.java:22` pide `EventoEntrante.class`; `Query.getResultList()` devuelve un `List` sin parámetro. El aviso de tipo es real; no se demostró un resultado incompatible. No se altera persistencia ni concurrencia. |
| 1 | Supresión preexistente innecesaria en esta ejecución | `GuionConversacion.java:626` ya contiene `@SuppressWarnings("unchecked")`; JDT no encuentra ese aviso allí. No se añadió ni amplió ninguna supresión, ni se elimina por cosmética. |
| **270** | | |

## Verificación realmente ejecutada

1. **Compilación desde cero de fuentes y pruebas de v2, sin ejecutarlas**, y generación del classpath:

   ```text
   mvnw.cmd -o -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/diagnostico-java/classpath.txt
   ```

   **BUILD SUCCESS**. `javac release 21`: **311 fuentes principales y 190 de pruebas**. Registro local `.local-diagnostico-maven.log`. No se reutilizaron clases de otro worktree.

2. **Compilación estática JDT**, misma dependencia del compilador empaquetada con la extensión instalada, sin generar clases ni ejecutar procesadores: **rc=0, 270 advertencias, 0 errores**. Argumentos, preferencias, XML y registro en `.local/diagnostico-java/{argumentos-jdt.txt,org.eclipse.jdt.core.prefs,jdt-v2.xml,jdt-v2.log}`. El inventario versionado evita las rutas de classpath personales de esos registros locales.

3. **Cuatro pruebas focales existentes**, sin BD, todas correctas:

   - `ManejadorErroresTest#errorImprevisto_registraTrazaSinMensajesNiDatosSensibles`.
   - `OrquestadorConversacionTest#aceptarConFranjaPendiente_creaLaFichaYProponeEsaFranja`.
   - `OrquestadorConversacionTest#aceptarSinAvisoPresentado_noCreaFicha`.
   - `MenusConversacionTest#menus_soloMenuPrincipalIncluyeHablarConAlguien`.

   Comando: `mvnw.cmd -o -Dtest=<métodos anteriores, separados por coma; métodos de la misma clase unidos con +> test`. Resultado **4 pruebas, 0 fallos, 0 errores, 0 omitidas**, BUILD SUCCESS. Registro `.local/diagnostico-java/pruebas-focales.log`.

4. `git diff --check` y comprobación de main y del worktree de Claude. No se repiten suites completas, `verify`, IT con BD, E2E o comprobaciones contra Azure. No hay cambios de código que exijan esas ejecuciones.

## Pendientes para Claude

- Revisar la clasificación y los argumentos de flujo anteriores. No hay un parche de código que aprobar; sí hay evidencia nueva en una rama propia.
- Comprobar el panel vivo de v2 cuando su ventana/servidor Java esté disponible. Los 270 avisos son de JDT batch, no del panel.
- Decidir si abordar contratos de nulabilidad individuales o deuda de APIs/tipos como tareas posteriores. No se propone una migración masiva ni se declara segura cada advertencia por haber compilado.
- El responsable conserva el pom de main sin guardar y decide sobre su línea inicial. No se modifica automáticamente.
- No se invocó Gemini ni se reintentó el envío de código rechazado. La solicitud para Claude queda localmente en el canal de coordinación en disco.
