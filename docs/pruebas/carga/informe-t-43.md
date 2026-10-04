# T-43 · Clasificación de rechazos inesperados de reserva

**Resultado: aprobado.** Carga oficial del 04/10/2026: 50 usuarios concurrentes durante 300 s. Disponibilidad: **p95 11 ms ≤ 2000 ms**, **0 errores de 14.363 consultas**. Global: **23/19.766 = 0,116361 % < 1 %**. Los **23 rechazos inesperados** fueron **409 `CLIENTE_CON_RESERVA_SOLAPADA`**; todos siguen siendo KO. **Cero 500 y cero 503** observados en los POST. Ninguna aserción ni respuesta aceptada modificada.

## Entorno, datos y reproducción

- Base local `main`: `1e5b5fa33bf7e99f98688c928531173801097fa4`. Fuentes de la carga ejecutada: `1af96404c926d7b663637b40be7c37c0c00940d5`. Backend sin cambios de T-43; jar reconstruido por `verify` antes de la carga.
- Intel Core i7-8750H @ 2,20 GHz, 6 núcleos / 12 procesadores lógicos, RAM 19,85 GiB; Windows 11 Home Single Language 10.0.26200. Backend, Gatling y PostgreSQL en el mismo equipo por localhost.
- Temurin 21.0.8+9, Maven 3.9.16 mediante wrapper; PostgreSQL **18.0**, puerto 5433; Gatling 3.16.0 y plugin 4.21.12. Sin cambios de versiones ni inicio de T-38.
- Base exclusiva y nueva `barberturno_perf`, perfil `dev` y Flyway real, sin demo ni reloj fijo. La inspección previa confirmó **cero conexiones ajenas de aplicación**; `verify` y la carga se ejecutaron consecutivamente.
- Generador de T-35 intacto, semilla **35**, misma agenda 05/10/2026–03/11/2026: 200 clientes ficticios, 10 barberos, 2 servicios, 120 jornadas lunes–sábado, 26 días laborables, 1560 reservas asistidas, 1560 auditorías y 3120 avisos; **41,67 % de ocupación**. Cien clientes inicialmente libres para autoservicio. BCrypt coste 12 y protocolo de bloqueos conservados.
- Modelo de tráfico de T-35 intacto: `constantConcurrentUsers(50).during(300)`, mezcla 90/10 por recorridos, semilla `35 + userId` para seleccionar fecha/servicio/barbero/cliente/franja, una franja recién consultada, cookies/CSRF reales y pausa final 1 s. Observados 12.960 recorridos de consulta y 1403 de creación (9,768 %).
- Ejecución `perf/target/ejecuciones/20261004-073051`; informe `disponibilidadsimulation-20261004123122160`: inicio 07:31:22 Lima / 12:31:22 UTC, final Maven 07:36:25 Lima; 300 s más drenaje, Maven `Total time: 05:08 min`.

```powershell
.\perf\ejecutar-carga.ps1
.\perf\resumir-carga.ps1 -Ejecucion perf/target/ejecuciones/20261004-073051 -Informe perf/target/gatling/disponibilidadsimulation-20261004123122160 -Salida docs/pruebas/carga/carga-t-43.json
```

[Resumen JSON oficial](carga-t-43.json). Incluye los agregados originales y el desglose nuevo; `erroresPorcentaje` de las tablas HTML está redondeado por Gatling (global 0,12 % y POST 1,93 %). Los porcentajes exactos de este informe se calculan con los conteos.

## Métricas

Percentiles de todas las respuestas de la operación, en ms; tasa media incluida la fase de drenaje. El p95 global incorpora BCrypt y corresponde a una operación distinta del p95 de disponibilidad.

| Operación | Peticiones | OK | KO | peticiones/s | p50 | p75 | p95 | p99 | Máximo | Media |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Global | 19766 | 19743 | 23 | 65,67 | 4 | 6 | 264 | 299 | 742 | 25 |
| Disponibilidad | 14363 | 14363 | 0 | 47,72 | 4 | 5 | **11** | 32 | 742 | 6 |
| XSRF inicial | 1403 | 1403 | 0 | 4,66 | 2 | 3 | 5 | 12 | 447 | 3 |
| Login cliente | 1403 | 1403 | 0 | 4,66 | 272 | 287 | 323 | 438 | 681 | 281 |
| XSRF con sesión | 1403 | 1403 | 0 | 4,66 | 2 | 2 | 5 | 12 | 47 | 2 |
| Crear reserva | 1194 | 1171 | 23 | 3,97 | 6 | 9 | 21 | 159 | 325 | 11 |

## Desglose y clasificación

| Estado HTTP | Código | Cantidad | Clasificación Gatling | Significado |
|---|---|---:|---|---|
| 201 | — | 300 | OK | Reservas realmente creadas |
| 422 | `LIMITE_RESERVAS_ACTIVAS` | 871 | OK permitido | Rechazo previsto por RN-20 |
| 409 | `FRANJA_NO_DISPONIBLE` | 0 | OK permitido | Conflicto de barbero autorizado por el modelo |
| **409** | **`CLIENTE_CON_RESERVA_SOLAPADA`** | **23** | **KO inesperado** | Rechazo legítimo por RN-04 / CP-13 |
| 500 | Cualquier código / sin cuerpo | 0 | KO si apareciera | No observado |
| 503 | Cualquier código / sin cuerpo | 0 | KO si apareciera | No observado |
| Otros estados o códigos inesperados | Incluido `sin cuerpo` | 0 | KO si aparecieran | No observado |

El hook contabilizó exactamente **23**, con una sola pareja estado/código. Coinciden con **todos los 23 KO de Crear reserva y del global**. Los 1403 recorridos de creación se concilian: **209 sin franja + 300 creadas + 871 límites + 23 solapes = 1403**; los POST: **300 + 871 + 23 = 1194**. KO entre POST: **1,926298 %**; las aserciones originales aplican el umbral < 1 % a disponibilidad y al global HTTP, y ambas pasan. No se presenta la tasa global como tasa de éxito de creación.

### Por qué ocurre el solape del cliente

La consulta de disponibilidad devuelve las franjas libres del **barbero**, de acuerdo con arquitectura §6.3 y §8.2. No recibe un cliente para descontar sus citas. La simulación reutiliza al azar cien clientes de autoservicio; que estén libres inicialmente no impide que acumulen citas a lo largo del ensayo. En un recorrido posterior puede elegir para el mismo cliente otra franja que cruza una cita suya con un barbero distinto. Dos sesiones simultáneas también pueden compartir cliente. La creación toma los bloqueos vinculantes y verifica RN-04; el rechazo preserva la integridad.

Además, `ReservaService.crear` comprueba el solape del cliente **antes** del límite RN-20. Por ello un cliente ya saturado puede devolver este 409 cuando el nuevo intervalo se cruza con una cita suya, en vez del 422 permitido por el modelo. La captura demuestra el código recibido; este mecanismo explica por qué la simulación puede provocarlo, sin afirmar qué historial individual originó cada petición (no se conservan datos de cuentas ni cuerpos).

**Propuesta al arquitecto, no aplicada:** si una futura variante busca medir reservas válidas, la simulación puede consultar `GET /api/reservas/mias` y seleccionar franjas que no se crucen con las citas del cliente. La concurrencia de sesiones del mismo cliente seguiría requiriendo una estrategia explícita de asignación o coordinación. Esto añade peticiones y modifica la selección y la contención, por lo que debe autorizarse como otro modelo de carga y documentarse por separado. Cambiar únicamente las reservas iniciales del generador no evita el solape surgido después de las creaciones; aumentar clientes también altera la saturación RN-20 y el conjunto de datos. No se propone ampliar la lista de respuestas aceptadas.

La revisión de T-35 partía de **29 KO sin código conservado**. Esta repetición produjo **23** y los clasifica todos; la misma semilla del generador y de las elecciones por usuario no fija la mezcla aleatoria de Gatling ni el orden de ejecución concurrente. **No es posible reconstruir retrospectivamente los códigos de los 29 de T-35.** La corrida nueva aporta evidencia del mecanismo de negocio, sin asignar a esos 29 un código que no se registró.

### Concurrencia y RNF-05

No aparece ningún POST 500 ni 503. Inspección de `backend.log` y `backend-error.log` de la ejecución: **cero coincidencias** de `ERROR`, `55P03`, `40P01`, `lock_timeout` o `deadlock`; el log de error no aporta incidentes. No se observa un hallazgo de concurrencia que requiera una propuesta de cambio del backend. El 409 RN-04 es una protección de integridad. Esta carga no sustituye las pruebas de concurrencia CP-03 de T-20 ni demuestra ausencia universal de interbloqueos.

## Aserciones y limpieza

```text
Disponibilidad: 95th percentile ... <= 2000.0 : true (actual : 11.0)
Disponibilidad: percentage of failed events ... < 1.0 : true (actual : 0.0)
Global: percentage of failed events ... < 1.0 : true (actual : 0.11636142871597693)
Crear reserva: count of successful events ... > 0.0 : true (actual : 1171.0)
[INFO] BUILD SUCCESS
Gatling: BUILD SUCCESS. Informes locales en perf/target/gatling/.
Limpieza: backend detenido y barberturno_perf eliminada.
```

El hook mantiene la exigencia adicional de al menos un **201 real**: hubo 300. `DROP DATABASE` final correcto. Verificación administrativa posterior: **0 bases barberturno_perf y 0 conexiones de aplicación**; **0 escuchas 8080/4200 y 0 procesos Java propios**, conservando únicamente el Java preexistente del IDE (PID 46188). Sin worktrees nuevos. Solo se creó y eliminó la base autorizada de carga.

[Evidencia de implementación, pruebas, incidencias, commits e integración](../t-43.md).
