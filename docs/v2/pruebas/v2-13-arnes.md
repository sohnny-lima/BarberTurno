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
