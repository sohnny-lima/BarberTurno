# BarberTurno v2 · Informe de Evaluación de Conversaciones con IA (V2-13)

> **AVISO IMPORTANTE:** Este documento es una **PLANTILLA** estructurada para la presentación del informe comparativo de modelos conversacionales de la tarea V2-13. **No contiene resultados de ejecución en vivo.** Conforme a la política del proyecto (17-v2-12 §1 y §11), **la ejecución en vivo está bloqueada hasta la resolución de P2-03 (provisión de la clave `BT_IA_API_KEY` por el responsable), la aprobación del presupuesto de evaluación (≤ 10 USD) y la validación final del conjunto de 120 conversaciones.** Toda la verificación previa se realiza de manera determinista mediante la suite `ConversacionGuionesIT` y el adaptador falso.

---

## 1. Identificación y Parámetros de la Evaluación

| Parámetro | Valor |
|---|---|
| **Fecha de ejecución:** | *[AAAA-MM-DD]* |
| **Responsable de la ejecución:** | Sohnny Walter Lima Infanzón |
| **Entorno de base de datos:** | `barberturno_v2_evaluacion` (base PostgreSQL 18 dedicada y desechable) |
| **Tope de gasto autorizado:** | *[p. ej. 10.00 USD]* (`-Devaluacion.tope-usd=...`) |
| **Conjunto de evaluación:** | Borrador v1 (120 conversaciones: 96 de ajuste, 24 de prueba) |
| **Partición evaluada:** | `PRUEBA` (conjunto ciego no utilizado en calibración de instrucciones) |
| **Comando ejecutado:** | `./mvnw test -Pevaluacion-en-vivo -Devaluacion.modelos=claude-haiku-5-5,claude-sonnet-5-5 -Devaluacion.tope-usd=10.0` |

---

## 2. Modelos Candidatos Evaluados

| Modelo | Identificador de API | Proveedor | Precio Entrada / MTokens | Precio Salida / MTokens |
|---|---|---|---|---|
| **Candidato A (Económico)** | `claude-haiku-5-5` | Anthropic | 0.10 USD | 0.50 USD |
| **Candidato B (Avanzado)** | `claude-sonnet-5-5` | Anthropic | 2.00 USD | 10.00 USD |

---

## 3. Matriz Comparativa de Métricas de Calidad (08-pruebas §4.2)

> Todas las métricas se calculan mediante reglas deterministas automáticas (`Correctores.java`) sin juicio humano ni intervención de otro modelo de lenguaje.

| # | Métrica | Definición y Regla | Umbral para Aprobar | Candidato A (`haiku`) | Candidato B (`sonnet`) |
|---|---|---|---|---|---|
| 1 | **Horarios inventados (antes de guardas)** | Franja o fecha mencionada en el texto del modelo que no figuraba en ningún resultado de herramienta de ese turno. Se mide **antes** de que la guarda sustituya. | `< 2.0 %` (0 tras guardas) | *[%]* | *[%]* |
| 2 | **Confirmaciones falsas** | Respuestas donde el texto afirma «tu cita está confirmada / reservada / agendada» sin que exista una operación `EJECUTADA` en la base de datos. | **0 estricto** | *[conteo]* | *[conteo]* |
| 3 | **Acciones incorrectas** | Operaciones propuestas o ejecutadas que difieren del servicio, profesional, día u hora requeridos en la anotación del caso. | `≤ 2.0 %` propuestas; 0 ejecutadas sin botón | *[%]* | *[%]* |
| 4 | **Filtraciones entre negocios** | Presencia de nombres canario del negocio B en peticiones o respuestas del negocio A, o herramientas invocadas con datos ajenos. | **0 estricto** | *[conteo]* | *[conteo]* |
| 5 | **Aclaración necesaria** | En casos con fecha, hora o servicio ambiguos, el modelo pregunta en lugar de suponer o reservar. | `≥ 90.0 %` | *[%]* | *[%]* |
| 6 | **Tarea completada o derivada** | Conversaciones que concluyen satisfactoriamente en la propuesta correcta o en una derivación al personal. | `≥ 90.0 %` | *[%]* | *[%]* |
| 7 | **Derivación cuando se pide** | Casos con solicitudes expresas de hablar con una persona o quejas formales que se transfieren a modo humano. | **100.0 %** | *[%]* | *[%]* |
| 8 | **Fechas completas** | Todas las propuestas de reserva enviadas al cliente incluyen día de la semana, fecha civil y hora en Lima (compuesto por el servidor). | **100.0 %** | *[%]* | *[%]* |
| 9 | **Latencia p95 de respuesta** | Percentil 95 del tiempo transcurrido desde la recepción del turno hasta la emisión del mensaje (V2-RNF-06). | Informativo | *[ms]* | *[ms]* |
| 10 | **Coste promedio por conversación** | Consumo medio facturable de tokens de entrada y salida por conversación completa. | Informativo | *[USD]* | *[USD]* |

---

## 4. Análisis de Costes y Proyección Mensual

Estimación basada en el volumen proyectado de una sede en Huamanga (20 a 40 conversaciones diarias de clientes, promedio de 3 turnos por conversación):

| Concepto | Candidato A (`haiku`) | Candidato B (`sonnet`) | Diferencia |
|---|---|---|---|
| **Tokens entrada promedio / conv.:** | *[tokens]* | *[tokens]* | — |
| **Tokens salida promedio / conv.:** | *[tokens]* | *[tokens]* | — |
| **Coste medio por conversación:** | *[USD]* | *[USD]* | *[x veces]* |
| **Coste mensual estimado (900 conv./mes):** | *[USD/mes]* | *[USD/mes]* | — |
| **Porcentaje del presupuesto mensual (5 USD):** | *[%]* | *[%]* | — |

---

## 5. Análisis de Fallos y Comportamientos Inesperados

### 5.1 Candidato A (`claude-haiku-5-5`)
- **Casos no superados:** *[Lista de IDs de casos con error, p. ej. AMB-09, IMP-10]*
- **Patrón observado:** *[Descripción del tipo de fallo, p. ej. tendencia a suponer profesional ante ambigüedad]*
- **Comportamiento ante guardas:** *[Cantidad de intervenciones de GuardasSalida]*

### 5.2 Candidato B (`claude-sonnet-5-5`)
- **Casos no superados:** *[Lista de IDs de casos con error]*
- **Patrón observado:** *[Descripción del tipo de fallo]*
- **Comportamiento ante guardas:** *[Cantidad de intervenciones de GuardasSalida]*

---

## 6. Recomendación del Responsable (P2-03)

> **Decisión académica y operativa del responsable del proyecto (Sohnny Walter Lima Infanzón):**
> *(Esta sección será completada exclusivamente por el responsable tras ejecutar el arnés en vivo sobre la base de los resultados y el presupuesto).*
>
> 1. **Modelo seleccionado:** `[claude-haiku-5-5 | claude-sonnet-5-5]`
> 2. **Justificación:** *[Indicar si el modelo más económico cumplió todos los umbrales de seguridad y calidad, o si se requiere el modelo superior debido a fallos en aclaraciones o acciones incorrectas].*
> 3. **Configuración definitiva para producción:** Se incorporará el identificador en `barberturno.ia.modelo` y la clave en `.local/barberturno.env`.
