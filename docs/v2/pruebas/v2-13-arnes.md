# V2-13 · Arnés en vivo, correctores y guiones · Evidencia del encargo 134 y de la revisión g100

Fecha: 05/11/2026. Rama `tarea/V2-12-evaluacion`. **El arnés no se ha ejecutado contra ningún proveedor** (P2-03: sin clave ni presupuesto aprobado); todo lo de abajo son pruebas locales con el adaptador falso y PostgreSQL desechable.

## Historia
| Paso | Autor | Commit | Revisión |
|---|---|---|---|
| Arnés, correctores y guiones originales (GI 2/2) | Gemini (g090/g092) | `4a42ac5` | Codex 129: **rechazado** (el arnés fabricaba resultados) |
| Primera corrección | Gemini (g097) + 2 ajustes de Claude | `53b2654` | Codex 133: arnés y correctores **rechazados** (base por subcadena, modelo no impuesto, sin transacción RLS, tope inexacto, sin aislamiento, turno anterior atribuido, aprobaciones en falso) |
| Segunda corrección (reasignada) | Codex (134), **cortada por el servicio** antes del informe | `dbf6419` (conservada por Claude) | Gemini Argon g100: 1–6 y 9–10 de 133 **resueltos**; 4 defectos y 1 mejora nuevos |
| Correcciones de g100 | Claude | este commit | Codex (pendiente, solo lectura) |

## Salvaguardas del arnés (verificadas por g100 y por pruebas)
- Destino exacto `localhost:5433/barberturno_v2_test` resuelto de la configuración efectiva y comprobado antes de migraciones, escrituras y trabajadores.
- Modelo, esfuerzo y `habilitada` impuestos sobre YAML y argumentos; el `IaPropiedades` efectivo y el modelo devuelto se comprueban en cada llamada.
- Lecturas y escrituras en transacciones cortas dentro de `EjecutorEnNegocio` con el rol de la aplicación.
- Saldo **global** del ensayo reservado en memoria antes de cada llamada, además del presupuesto del negocio; un solo negocio con canal activo.
- Negocio y conversación nuevos por caso y por modelo; reloj fijo; *fixtures* explícitos con citas previas (`fixtures-v2-13.yaml`); cada entrada con **su** turno.
- Canarios en el negocio, un profesional y un servicio de B, comprobados en peticiones, herramientas y salidas.

## Correcciones de Claude sobre g100
1. **Lote íntegro (g100-1):** un caso sin turnos, truncado, desalineado o de otro modelo invalida la medición de todo el lote (`Tarea completada o derivada` = «no medida», sin aprobación).
2. **Objetivos validados antes de gastar (g100-2):** `FixturesEvaluacion.exigir` comprueba los nombres anotados contra el catálogo sembrado y el formato `yyyy-MM-dd HH:mm` de `inicio` para los 120 casos antes de abrir Spring. Los campos no anotados son **comodines** (el cliente no los fijó: «lo antes posible», sin servicio); una creación necesita al menos un dato fijado y una cancelación o reprogramación, la cita objetivo.
3. **Interactivos completos (g100-3):** `ConversacionGuionesIT` comprueba cuerpo, encabezado, pie, botones y filas.
4. **Derivación conservada (g100-4):** tras derivar, los mensajes del cliente no exigen saliente automático ni anulan la derivación.
5. **Modelo distinto (g100-5):** prueba negativa de `MonitorModelo`.

## Resultados (ejecutor serializado sobre `barberturno_v2_test`)
| Qué | Resultado |
|---|---|
| `CorrectoresTest` | 35/0 |
| `EvaluacionEnVivoTest` (incluye los 120 casos validados contra sus *fixtures* y la negativa sin clave) | 30/0 |
| `ConversacionGuionesIT` | 29/0 (`target/g100-claude-1.log`) |
| `TurnosIAIT` (sobre `dbf6419`) | 40/0 |
| `-DskipTests verify` | correcto (Javadoc sin avisos) |

## Lo que no se puede comprobar sin clave (no se simula)
Llamadas reales al proveedor, tokens y costes reales, latencias, la calidad de las respuestas y el informe comparativo de modelos. El conjunto de 120 sigue siendo **BORRADOR** pendiente de revisión.

## Correcciones de la revisión 136

Encargo 137, Codex · 05/11/2026 · rama `tarea/V2-12-correcciones-136`, base `bfce0d6`. Estado de las correcciones locales: **Hecha, incluida la continuación de REL-06**, con `ventanas` y la convención de «tarde» autorizadas por el coordinador. Sin bloqueos de implementación pendientes; el conjunto sigue en BORRADOR. Sin uniones ni push; las reglas de cierre de v1 y la edición de `docs/tareas.md` no aplican a este encargo.

### Cambios

- `SaneadorTexto.TELEFONO`: separadores sin límite (`*`). El Javadoc del patrón explica que su clase no incluye dígitos y no admite particiones ambiguas entre repeticiones ni retroceso catastrófico; versión de clase 1.2. Regresiones con 4, 6 y 10 separadores mixtos ASCII/Unicode tanto en `sanear` como en `contieneDatoPersonal`. Las pruebas existentes de fechas/precios y las 355 guardas se conservan sin cambios.
- `FixturesEvaluacion`: `ventana: {desde, hasta}`, `ventanas: [{desde, hasta}, …]` e `inicioLibre: true` en el complemento YAML, excluyentes entre sí y con `esperado.inicio`. `ventanas` exige uno o más intervalos, sin solapes; se permiten intervalos adyacentes y anotaciones desordenadas. `exigir` valida instantes con desfase, orden estricto, catálogo y fechas reales (incluidos 30/02 y 24:00). No anotar ninguna opción temporal deja un objetivo `CREAR`/`REPROGRAMAR` sin inicio **no medible**, sin inventar un comodín. `CANCELAR` sigue necesitando solo la cita objetivo.
- `Correctores`: resuelve las restricciones por el id explícito del fixture (no por categoría ni texto), mantiene `ObjetivoOperacion` y `EscenarioEvaluacion` sin cambios y compara `desde <= inicio < hasta`. Los campos servicio/profesional omitidos siguen siendo comodines; `CREAR` mantiene al menos un dato fijado (una ventana cuenta). Un objetivo no medible incrementa «Objetivos de operación no medidos»; las métricas de acciones y tarea del lote quedan «no medida», sin aprobación. El overload de paquete permite inyectar fixtures aislados en las pruebas.
- Tras una derivación, el silencio solo acredita un paso si este termina con `humano=true`. Volver al asistente o emitir salientes automáticos falla tanto «Derivación cuando se pide» como «Tarea completada o derivada».
- `CorrectoresTest`/`EvaluacionEnVivoTest`: negativas aisladas para g100-1 (truncado, entrada desalineada, modelo del caso y del uso incorrectos), validación del catálogo y del inicio, comodines explícitos, objetivos mínimos de cambio/cancelación, ventanas dentro/fuera y límites inclusivo/exclusivo, objetivo no medible y las negativas de derivación. `ConversacionGuionesIT`: contenido prohibido exclusivamente en encabezado, pie, botón o fila; cada negativa exige el mensaje concreto de la aserción de contenido.

### Casos anotados y convención del conjunto

| Caso | Anotación / resultado |
|---|---|
| REL-04 «hoy, lo antes posible» | `ventana: {desde: "2026-11-02T10:00:00-05:00", hasta: "2026-11-03T00:00:00-05:00"}`. Desde el reloj del caso (lunes 02/11, 10:00 en Lima) hasta el fin de ese día exclusivo; el resto del dominio sigue exigiendo disponibilidad real. |
| REL-06 «este fin de semana en la tarde» | `ventanas` del sábado 07/11/2026 y domingo 08/11/2026, cada una desde `T12:00:00-05:00` hasta `T19:00:00-05:00` exclusivo. Bloqueo resuelto por la decisión del coordinador en la continuación de 137; se retira su comentario del fixture. Las jornadas siguen limitando la disponibilidad real a 09:00–18:00. |
| Resto de `CREAR`/`REPROGRAMAR` con resultado `OPERACION` | Todos tienen `esperado.inicio`; no requieren anotación complementaria. |
| `inicioLibre` | Ningún caso del conjunto lo necesita: los únicos dos sin inicio tienen restricción temporal. La opción se verifica con fixtures sintéticos, sin atribuirla a REL-04/06. |

**Convención provisional del coordinador, no decidida (corrección del 05/11/2026 a petición del responsable):** «tarde» = 12:00 a 19:00 exclusivo, hora de Lima, solo para que REL-06 sea medible; el negocio de la evaluación cierra a las 18:00 y la decisión está en `v2-13-conjunto-convenciones.md`. `coincide` acepta el inicio dentro de cualquiera de las ventanas (`desde <= inicio < hasta`), sin incluir el hueco entre ellas. El formato singular se normaliza a una lista de un intervalo para la comparación; se conserva la presencia de ambas claves para que `exigir` pueda rechazar su combinación. Se revisaron los 120 casos: no hay otro objetivo `CREAR`/`REPROGRAMAR` sin `inicio` que necesite intervalos separados; los otros casos de tarde ya tienen `esperado.inicio`. El conjunto y su complemento siguen en **BORRADOR**, sin revisión del responsable ni ensayo contra proveedores.

### Verificación e incidencias

Entorno: Windows, Temurin 21.0.8+9, Maven 3.9.16 (distribución fijada en el wrapper). PostgreSQL 18.6 en localhost:5433, confirmado por el arranque de las IT; estas se ejecutaron exclusivamente mediante el ejecutor serializado autorizado, sin leer `.local/`.

1. Unitarias preliminares: `cd backend; .\mvnw.cmd '-Dtest=SaneadorTextoTest,GuardasSalidaTest,ComposicionTurnoIATest,CorrectoresTest,EvaluacionEnVivoTest' test` con `JAVA_HOME` de JDK 21. **483 pruebas, 0 fallos/errores/omisiones; BUILD SUCCESS**. Registro `backend/target/r137-unitarias.log`. Incluye 23 del saneador, 355 guardas, 13 composición, 50 correctores y 42 arnés. Se añadieron después tres regresiones más para el complemento real y el objetivo previo ausente.
2. Verificación final de las siete clases, mediante `pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-12-r136 -Registro target\r137-pruebas.log '-Dtest=SaneadorTextoTest,GuardasSalidaTest,ComposicionTurnoIATest,CorrectoresTest,EvaluacionEnVivoTest,ConversacionGuionesIT,TurnosIAIT' test`: **559 pruebas, 0 fallos/errores/omisiones; BUILD SUCCESS; `mvnw rc=0`**. Maven: 02:27 min, terminado a las 05:47:00 de Lima, tras esperar aproximadamente 26 minutos el cerrojo. Registro real `backend/target/r137-pruebas.log`; salida del ejecutor en `backend/target/r137-ejecutor.log`.
3. Desde `backend`, `Remove-Item -LiteralPath target/jacoco.exec` y `.\mvnw.cmd -DskipTests verify`: **BUILD SUCCESS**, 25,039 s, empaquetado correcto y Javadoc sin avisos. Registro `backend/target/r137-verify.log`. JaCoCo omite informe y umbral por ausencia del fichero retirado según la instrucción del encargo; esta ejecución no mide cobertura ni ejecuta la suite completa.

| Clase de la ejecución final | Pruebas | Fallos / errores / omitidas |
|---|---:|---|
| `SaneadorTextoTest` | 23 | 0 / 0 / 0 |
| `GuardasSalidaTest` | 355 | 0 / 0 / 0 |
| `ComposicionTurnoIATest` | 13 | 0 / 0 / 0 |
| `CorrectoresTest` | 51 | 0 / 0 / 0 |
| `EvaluacionEnVivoTest` | 44 | 0 / 0 / 0 |
| `ConversacionGuionesIT` | 33 | 0 / 0 / 0 |
| `TurnosIAIT` | 40 | 0 / 0 / 0 |

Incidencias de entorno, anteriores a las pruebas: Git en sandbox rechazó la propiedad del worktree (`fatal: detected dubious ownership`); funcionó con escalación, sin cambiar ACL/configuración. El primer intento de redirección falló porque no existía `backend/target`; se creó el directorio. El wrapper en sandbox falló antes de Maven; la misma orden escalada funcionó:

```text
icm : No se puede indizar en una matriz nula.
FullyQualifiedErrorId : NullArray,Microsoft.PowerShell.Commands.InvokeCommandCommand
Cannot start maven from wrapper
```

`rg` no está en el PATH del sandbox y `python` resuelve al alias de WindowsApps sin acceso; se usaron `Select-String`, `Get-Content` y .NET. Ninguna incidencia supuso debilitar pruebas ni cambiar dependencias.

Comprobación estática: `git diff --check` correcto y los 10 archivos del diff contra `bfce0d6` dentro del alcance, con UTF-8 sin BOM y LF comprobados por lectura de bytes. El ejecutor serializado quedó esperando el cerrojo de otra suite de Maven/Surefire; no se abrió una segunda ejecución contra PostgreSQL.

### Commits y límites

- `049d6c41c2ed7b49396cd183c588ff4e44ad39f1`: `fix(ia): sanear teléfonos con separadores sin límite [V2-12]`.
- `32d6775af56dadcbb7475b52acc27d30e88080d3`: `fix(evaluacion): exigir ventanas y conservar la derivación [V2-13]`.
- `899b3284597d48e55a0c2491763356522835df94`: cierre documental inicial, `docs(evaluacion): registrar correcciones y bloqueo temporal [V2-13]`.
- Continuación identificable por el asunto solicitado `fix(evaluacion): ventanas separadas para restricciones temporales [V2-13]`; hash propio en el informe final. Sin merge por instrucción del encargo.
- Solo archivos del alcance; sin cambios a API, DDL, permisos, protocolo de concurrencia, tecnología, dependencias ni arranque. README sin cambios. Sin modificaciones de documentos protegidos, v1, APF2, Azure, remotos o etiquetas; sin lectura de `.local/` (solo invocación autorizada del ejecutor), servicios externos, secretos ni datos personales reales. No se crearon servidores ni worktrees.
- Cierre inicial comprobado tras ambas órdenes Maven: ningún proceso Java de este worktree y los procesos del ejecutor serializado terminados; los dos pools de las IT se cerraron. Sin worktrees adicionales. La continuación resuelve el formato de REL-06; se conservan los bloqueos externos P2-03, presupuesto y revisión del conjunto.

### Continuación de 137 · ventanas separadas

Decisión del coordinador: lista de intervalos excluyentes, sin solapes; «tarde» = [12:00, 19:00) en Lima. Regresiones con el fixture real de REL-06: primera y segunda tarde, sus inicios inclusivos, ambos límites superiores exclusivos, sábado antes de mediodía y domingo por la mañana. Validación negativa de `ventana` + `ventanas`, `ventanas` + `inicioLibre`/inicio exacto, lista vacía, miembros mal formados/invertidos y solapes (también duplicados con distinto desfase e intervalos contenidos); positiva de intervalos adyacentes/desordenados.

- `pwsh -File D:\Proyectos\BarberTurno-v2\.local\mvn-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-12-r136 -Registro target\r137-rel06-pruebas.log '-Dtest=CorrectoresTest,EvaluacionEnVivoTest' test`: **112 pruebas, 0 fallos/errores/omisiones; BUILD SUCCESS; `mvnw rc=0`**, 27,270 s. `CorrectoresTest`: 60; `EvaluacionEnVivoTest`: 52 (incluye `exigir`). Registro real `backend/target/r137-rel06-pruebas.log`; salida del ejecutor `backend/target/r137-rel06-ejecutor.log`.
- Desde `backend`, retirada de `target/jacoco.exec` y `.\mvnw.cmd -DskipTests verify`: **BUILD SUCCESS**, 7,572 s, empaquetado y Javadoc correctos, cero avisos Maven/Javadoc. Registro `backend/target/r137-rel06-verify.log`. JaCoCo omite informe/umbral al retirar los datos obsoletos; esta orden no mide cobertura.
- No se modificó `backend/src/main/java`: no hay Javadoc de producción nuevo o modificado en esta continuación.
- Sin incidencias de pruebas ni deuda nueva. `git diff --check`, UTF-8 sin BOM y LF comprobados en los siete archivos de esta continuación. Verificación final de procesos: ningún Java de este worktree ni ejecutor de pruebas propio pendiente. Se mantienen todos los límites del encargo; sin push, uniones, servidores o worktrees adicionales.
