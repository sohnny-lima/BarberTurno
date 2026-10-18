# CP-11 · Plantilla de usabilidad con cinco participantes

Estado: **pendiente de ejecución por el responsable (P-07)**. Guía de sesiones, comandos, consentimiento, instrucciones y fichas sobre la versión final: [cp11-sesiones.md](cp11-sesiones.md). Preparada el 04/10/2026 para T-37. No hay observaciones humanas registradas. El E2E automático acredita controles y recorridos, pero no sustituye esta sesión.

## Preparación y consentimiento

Registrar fecha, commit exacto (`git rev-parse HEAD`), navegador/versión, dispositivo, viewport (360 px o escritorio hasta 1440 px), conexión y reloj fijo de negocio `2026-09-28T09:00:00-05:00`. Usar `dev,demo`, cuentas ficticias `@ejemplo.test` y una base de demostración preparada por el responsable. Las contraseñas se proporcionan fuera de esta ficha y no se registran.

Texto que se lee a cada participante: «Evaluamos la aplicación, no sus habilidades. Su participación es voluntaria; puede detenerse o retirarse en cualquier momento. Solo anotaremos un código P1…P5, tiempos, pasos, errores y comentarios sobre la interfaz. No necesitamos su nombre, correo real ni contraseña. No grabaremos audio, vídeo ni pantalla. Los resultados agregados se usarán en la sustentación académica. ¿Acepta participar?» Registrar aceptación sí/no y fecha. Si no acepta, no iniciar ni guardar observaciones y buscar otra persona. El responsable comunica cómo solicitar la retirada de los datos y fija el plazo de conservación antes de empezar. Cualquier grabación requeriría consentimiento separado y no forma parte de este guion.

Dar una explicación neutral: «Reserve un turno como lo haría normalmente; piense en voz alta si desea». No enseñar el asistente antes de medir. Mantener la misma instrucción y punto de partida para las cinco personas. Distribuir 360 px y escritorio entre participantes, registrar la distribución; si se repite en otro tamaño, anotar por separado el aprendizaje.

## Guion de tareas

| Tarea | Instrucción al participante | Criterio de finalización |
|---|---|---|
| U1 · Reserva principal | Con la sesión de cliente ya abierta, reserve Corte clásico con Carlos el 01/10/2026 en una franja libre; revise los datos y confirme. | Mis citas muestra la reserva y el aviso, con la hora de Lima esperada. |
| U2 · Reprogramación | Cambie esa cita a otra franja libre del mismo día. | Mis citas muestra el mismo código y la nueva hora. |
| U3 · Cancelación | Cancele la cita que acaba de cambiar. | Estado Cancelada, aviso y acciones retiradas. |
| U4 · Política | Busque su cita de Barba con Carlos del 28/09/2026 a las 10:00 y explique qué puede hacer a las 09:00. | Identifica la restricción de menos de dos horas y el mensaje de ayuda. |
| U5 · Orientación | Vuelva a Reservar y luego a Mis citas usando el menú. | Encuentra ambas pantallas sin perder controles ni desplazamiento global. |

Restablecer solo las fixtures propias mediante las operaciones autorizadas o preparar cuentas independientes entre sesiones. No borrar una base existente. Si la franja de U1 ya está ocupada, el facilitador prepara otra libre antes de iniciar; anotar la entrada exacta.

### Datos de partida (solo para el facilitador; 10/10/2026)

Cada participante usa una copia nueva (`barberturno_cp11_p<n>`) de la plantilla `barberturno_cp11_base`, cargada con los datos de demostración. La aplicación arranca con los perfiles `dev,demo`, porque `ClockConfig` solo aplica el reloj fijo con `demo` o `test`. Los scripts de preparación están en `.local/coordinacion/cp11/`.

**Comprobado sin cuentas** (copia aislada, 10/10/2026):
- Reloj efectivo: 28/09/2026 entre las 09:00 y las 09:10 de Lima. El 27/09 no tiene franjas, y Barba del 28/09 empieza a las 09:10.
- Profesionales activos: Carlos y Miguel. Servicios: Corte clásico (30 min) y Barba (20 min).
- Jornadas de Carlos: de lunes a sábado, 09:00–13:00 y 14:00–18:00.
- U1: el 01/10 hay 31 inicios libres de Corte con Carlos. Faltan los de 09:40 a 10:20 y los de 15:40 a 16:50.

**Comprobado con las cuentas** (`verificar-cp11.ps1`, copia `barberturno_cp11_verif_20261010_111137`, commit `f7dc336`, resultado «VERIFICACIÓN CP-11 OK»):
- `cliente@ejemplo.test` («Cliente de demostración», CLIENTE) y `carlos@ejemplo.test` (BARBERO, vinculado a Carlos) acceden sin cambio obligatorio de contraseña.
- Citas del cliente al empezar:

| Código | Servicio | Profesional | Fecha y hora (Lima) | Estado | A las 09:00 del 28/09 | Papel en el guion |
|---|---|---|---|---|---|---|
| **BT-100** | Corte clásico | Carlos | 01/10/2026 10:00–10:30 | CONFIRMADA | Puede cancelar y reprogramar | Ocupa las 10:00 del 01/10: U1 debe elegir otra franja |
| **BT-103** | Barba | Carlos | 28/09/2026 10:00–10:20 | CONFIRMADA | No puede cancelar ni reprogramar (faltan menos de 2 h) | Cita de U4 |
| BT-102 | Corte clásico | Miguel | 27/09/2026 10:00–10:30 | COMPLETADA | Sin acciones | Historial |

- Ningún inicio libre de Corte con Carlos el 01/10 se solapa con BT-100 (10:00–10:30).
- El segundo hueco de U1 (de 15:40 a 16:50) corresponde, según la carga, al bloqueo «Trámite» de 16:00 a 17:00. Es un bloqueo, no una reserva, y no se verifica por la API pública.

**Códigos:** el código visible es `BT-<id>`, con identidad desde 100, y no coincide con la referencia del APF2 que la carga guarda en la auditoría. Por ejemplo, BT-103 corresponde a la BT-104 del APF2. La instrucción de U4 al participante no menciona el código.

**Límites del cliente:** RN-04 impide reservas propias solapadas. Con RN-20, la reserva de U1 deja al cliente en el máximo de 3 reservas futuras que ocupan franja; una reserva adicional hecha por error haría fallar los intentos siguientes. Anotar cualquier reserva extra como error.

## Métricas y reglas de registro

Cronometrar U1 desde que se presenta la instrucción con `/reservar` lista hasta que aparece la reserva en Mis citas; incluir dudas y correcciones. Medir también U2…U5 por separado. Registrar tiempo en segundos, pasos principales (pantallas/etapas: servicio, franja, confirmación; ingreso previo fuera de U1), interacciones totales opcionales, errores y finalización sí/no. Un error es una acción que produce un resultado no deseado o obliga a corregir; anotar su efecto, sin culpar a la persona. Registrar ayudas del facilitador y distinguir finalización autónoma de asistida. Al superar 180 s en U1 conservar el tiempo real, incluso si continúa; una tarea abandonada queda como no finalizada. RNF-06 exige U1 ≤ 180 s y ≤ 5 pasos principales; no sustituir datos faltantes por cero. Informar los cinco resultados individuales y cuántos cumplen ambas metas; cualquier incumplimiento se comunica al arquitecto. CP-11 también exige comprobar que los controles sean utilizables en ambos tamaños.

| Participante | Fecha / commit | Consentimiento | Navegador / viewport | U1 entrada (fecha, servicio, barbero, franja) | U1 segundos | U1 pasos | U1 errores / descripción | Finalizó / autónoma o asistida | U2…U5 tiempos, errores y finalización | Controles / comentarios | SUS opcional |
|---|---|---|---|---|---:|---:|---|---|---|---|---|
| P1 | Pendiente | Pendiente | Pendiente | Pendiente | — | — | — | — | — | — | — |
| P2 | Pendiente | Pendiente | Pendiente | Pendiente | — | — | — | — | — | — | — |
| P3 | Pendiente | Pendiente | Pendiente | Pendiente | — | — | — | — | — | — | — |
| P4 | Pendiente | Pendiente | Pendiente | Pendiente | — | — | — | — | — | — | — |
| P5 | Pendiente | Pendiente | Pendiente | Pendiente | — | — | — | — | — | — | — |

SUS es opcional: si el responsable dispone del cuestionario estándar de diez ítems, recoger respuestas 1–5 después de las tareas. Puntuación = 2,5 × (suma de respuesta−1 en ítems impares + 5−respuesta en pares), escala 0–100. No inventar respuestas ni usar SUS como sustituto de los tiempos/pasos o como criterio nuevo de aprobación. Si no se aplica, registrar «no aplicado».

## Cierre de la sesión

Fecha / facilitador: pendiente. Participantes que cumplen ambas metas U1: —/5. Finalizaciones autónomas: —/5. Controles utilizables a 360 px y escritorio: pendiente. Resumen de errores, ayudas y mejoras propuestas: pendiente. Adjuntar esta ficha anonimizada a [aceptacion.md](aceptacion.md), con commit probado y resultado observado por participante; no cerrar CP-11 hasta completar las cinco sesiones.
