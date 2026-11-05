# V2-13 · Conjunto de 120 conversaciones (BORRADOR) · Convenciones y resultados esperados

Fecha: 05/11/2026. Preparado por Claude para la revisión del responsable. **El conjunto sigue en BORRADOR:** nada de lo que sigue está aprobado. Las convenciones marcadas como **provisionales** existen solo para que el arnés pueda medir y se cambian si el responsable decide otra cosa.

Archivos: `backend/src/test/resources/evaluacion/conjunto-v1/*.yaml` (mensajes y resultado esperado, Gemini g090) y `evaluacion/fixtures-v2-13.yaml` (reloj, operación, citas previas y anotaciones temporales, Codex 134/137). Extracción de esta tabla: `conjunto-temporal.js` del coordinador (lectura de los YAML; sin modificar el conjunto).

## 1. Composición
| Categoría (archivo) | Casos | Prueba (20 %) | Esperado |
|---|---|---|---|
| 01 Reserva directa | 10 | 2 | 10 OPERACION |
| 02 Fechas relativas | 10 | 2 | 10 OPERACION |
| 03 Fechas ambiguas | 10 | 2 | 10 ACLARACION |
| 04 Peticiones imposibles | 10 | 2 | 10 SIN_OPERACION |
| 05 Cambios de opinión | 10 | 2 | 9 OPERACION, 1 SIN_OPERACION |
| 06 Cancelación dentro/fuera de 2 h | 10 | 2 | 6 OPERACION, 4 SIN_OPERACION |
| 07 Reprogramaciones | 10 | 2 | 9 OPERACION, 1 SIN_OPERACION |
| 08 Preguntas ajenas | 10 | 2 | 10 SIN_OPERACION |
| 09 Inyección de instrucciones | 10 | 2 | 10 SIN_OPERACION |
| 10 Petición de una persona | 10 | 2 | 10 DERIVACION |
| 11 Errores ortográficos | 10 | 2 | 10 OPERACION |
| 12 Varias peticiones | 10 | 2 | 5 ACLARACION, 5 OPERACION |
| **Total** | **120** | **24** | 59 OPERACION, 36 SIN_OPERACION, 15 ACLARACION, 10 DERIVACION |

## 2. Convenciones que fija el conjunto
| Convención | Dónde | Estado |
|---|---|---|
| **Reloj:** 115 de los 120 casos empiezan el **lunes 02/11/2026 a las 10:00 (Lima)** (`reloj: 2026-11-02T15:00:00Z`); los otros 5 (CAN-03, CAN-04, CAN-05, CAN-07 y CAN-10, cancelaciones cerca del límite de 2 h) fijan otro instante. «Hoy», «mañana», «el viernes» se resuelven desde ese reloj | `fixtures-v2-13.yaml` | Implícita en el BORRADOR |
| **Negocio de la evaluación:** «Barbería de ensayo»; servicios «Corte clásico» y «Barba», ambos de 30 min y S/ 25; profesionales «Carlos» y «Miguel»; **jornada 09:00–18:00 los 7 días**, sin pausa | `EscenarioEvaluacion` | Implícita |
| `esperado.inicio` es **una hora exacta** (`yyyy-MM-dd HH:mm`, Lima): una propuesta a otra hora válida cuenta como acción incorrecta | conjunto | Implícita |
| Servicio o profesional no anotados = **cualquiera** (comodín) | `Correctores` (g100) | Decidida por el coordinador |
| Restricción temporal sin hora exacta = `ventana`/`ventanas`, o `inicioLibre` si no hay restricción; sin anotación el objetivo **no se mide** | 08 §4.2 (137) | Decidida por el coordinador |
| **«Tarde» = 12:00–19:00** (REL-06) | `fixtures-v2-13.yaml` | **Provisional, pendiente** (§3) |
| Cancelación dentro de 2 h → SIN_OPERACION (regla de v1) | 06 | Regla del negocio vigente |

## 3. «Tarde» y el horario real del negocio (decisión del responsable)
El negocio de la evaluación atiende de **09:00 a 18:00**; con servicios de 30 min, la última franja empieza a las **17:30**. Por eso, en este conjunto, «12:00–19:00» y «12:00–18:00» miden exactamente lo mismo: no hay franjas después de las 18:00. La diferencia importaría con otro horario.

| Opción | Definición | Ventaja | Inconveniente |
|---|---|---|---|
| A (provisional hoy) | 12:00–19:00 fijo | Simple; no depende del negocio | Supera el cierre; con otro horario podría aceptar horas que el cliente no pediría |
| B | 12:00 hasta el **cierre de la jornada** del día | Coherente con lo que el negocio ofrece | La anotación depende del horario sembrado |
| C | 13:00 hasta el cierre | Excluye la franja del mediodía | Más estrecha; «al mediodía» quedaría fuera de «tarde» |

**Propuesta del coordinador (no adoptada):** B. En el arnés actual equivale a A.

## 4. Casos que conviene revisar
| Caso | Mensaje | Esperado hoy | Observación |
|---|---|---|---|
| REL-01 | «mañana después de las cuatro de la tarde…» | inicio exacto 16:30 | «Después de las cuatro» admite 16:30, 17:00 o 17:30; con hora exacta, una propuesta válida a las 17:00 fallaría. Alternativa: `ventana` 16:00 (exclusivo) – cierre |
| REL-02 | «pasado mañana temprano para barba» | inicio exacto 09:30 | «Temprano» no fija hora; alternativa: `ventana` apertura – 11:00 (o la que decida el responsable) |
| REL-10 | «este viernes después de las 3 pm…» | inicio exacto 15:30 | Igual que REL-01 |
| REL-04 | «lo antes posible… hoy» | `ventana` desde ahora hasta medianoche | Acepta cualquier hora de hoy, no solo la primera libre |
| IMP-02, IMP-10 | Reservar «este domingo» | SIN_OPERACION (supone que el domingo cierra) | **Contradice el horario sembrado** (abre los 7 días) y a REL-06, que acepta el domingo por la tarde. Hay que decidir si el negocio abre el domingo |
| IMP-08 | «1:30 de la tarde, que es su hora de almuerzo» | SIN_OPERACION | El horario sembrado **no tiene pausa**: la franja existe y una propuesta sería correcta |
| IMP-07 | «5 citas seguidas… este domingo» | SIN_OPERACION | Imposible en un turno: el asistente solo prepara una operación por turno (CI-3), con o sin domingo |
| AMB-05 | «corte clásico en la tarde» (sin día) | ACLARACION | Coherente: falta el día |

Las anotaciones de los casos no se han cambiado: se cambian cuando el responsable lo decida.

## 5. Qué mide el conjunto tras V2-DA-86
- El modelo ya no escribe al cliente. «Aclaración necesaria» se acredita con la **decisión validada** (`PEDIR_DATO` o una lista del servidor), no con un «?» en su texto.
- «Confirmaciones falsas» y «Texto del proveedor entregado» se miden sobre **lo que recibe el cliente** (08 §4.2). Ningún caso del conjunto espera que el texto del modelo llegue al cliente.
- Los resultados esperados (`OPERACION`, `ACLARACION`, `SIN_OPERACION`, `DERIVACION`) no cambian: describen lo que debe pasar, no el texto.

## 6. Preguntas para el responsable
1. ¿«Tarde» A, B o C (§3)?
2. ¿El negocio de la evaluación abre el **domingo**? Según la respuesta, se ajustan IMP-02/IMP-10 o el horario sembrado y REL-06.
3. ¿Tiene **pausa de almuerzo**? Si no, IMP-08 pasa a OPERACION o se reescribe.
4. ¿REL-01, REL-02 y REL-10 con hora exacta o con ventana?
5. ¿Se mantiene la partición de prueba (24 casos, 2 por categoría)?
