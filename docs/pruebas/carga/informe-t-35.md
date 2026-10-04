# T-35 · Informe de carga RNF-01

**Resultado: aprobado.** El 04/10/2026 se mantuvieron 50 usuarios concurrentes durante 300 s con Gatling. Disponibilidad: **p95 11 ms ≤ 2000 ms**, **0 % de errores**. Global HTTP: **29/19.768 = 0,146702 % < 1 %**. Se conservaron todos los rechazos inesperados como KO; no se ajustaron umbrales, consultas o índices.

## Entorno y versión

- CPU Intel Core i7-8750H @ 2,20 GHz, 6 núcleos / 12 procesadores lógicos; RAM instalada 19,85 GiB.
- Windows 11 Home Single Language, versión 10.0.26200. Backend, generador de tráfico y PostgreSQL en el mismo equipo, por localhost HTTP:8080.
- Temurin 21.0.8+9, Maven 3.9.16 (wrapper backend), PostgreSQL **18.0** en 5433; actualización T-38 no iniciada.
- Gatling 3.16.0 y gatling-maven-plugin 4.21.12, últimas releases verificadas en Maven Central el 04/10/2026. Sin Docker, cloud ni servicios de pago.
- Backend sin cambios sobre `d2ba926531526a5c78f2317520fd3fe704694448`; commit de simulación ejecutada: **`e464b20905f9f6d59eb74da9d55c6e4cca43c970`**.
- Informe local `disponibilidadsimulation-20261004113620910`: inicio 06:36:20 Lima (11:36:20 UTC), 300 s de inyección más drenaje de los usuarios iniciados; Gatling Maven terminó 06:41:23, `Total time: 05:07 min`. Metadatos previos a la inyección en `20261004-063550`.

## Datos y modelo de tráfico

Base nueva y exclusiva `barberturno_perf`, Flyway real del jar con perfil dev; no perfil demo ni reloj fijo. Fecha de agenda 05/10/2026–03/11/2026. Semilla 35, 26 días laborables; 200 clientes ficticios, 10 barberos, 2 servicios, 120 jornadas (dos intervalos/día de lunes a sábado), 1560 reservas asistidas por ADMIN ficticio, 1560 auditorías y 3120 avisos. **41,67 %** de ocupación: 200 min reservados sobre 480 por barbero/día laborable. Reservas asistidas exentas de RN-20 según decisión explícita del arquitecto; el generador preserva restricciones y bloqueo ⓪ → ① → ②. Los cien clientes de autoservicio empiezan sin reservas. BCrypt coste 12, política del servidor sin cambios, `ANALYZE` después de importar.

Carga cerrada `constantConcurrentUsers(50).during(300)`; un recorrido por usuario y pausa final 1 s, con sustitución hasta el final de la ventana. Fechas y servicios aleatorios, con/sin preferencia de barbero. Mezcla configurada 90 % consulta / 10 % login y reserva, aplicada a **recorridos**, no a peticiones HTTP: observados 12.979 recorridos de consulta y 1401 de creación (**9,74 %**); todo recorrido de creación consulta disponibilidad y obtiene cookie/token reales. Lectura del XSRF de la última respuesta inmediatamente antes del POST.

## Percentiles y rendimiento

Valores en ms; tasa media Gatling, incluido el drenaje final. Percentiles de todas las respuestas HTTP de la operación; la tabla separa OK/KO. Los p95 globales incluyen login BCrypt y no sustituyen el p95 de disponibilidad.

| Operación | Peticiones | OK | KO | peticiones/s | p50 | p75 | p95 | p99 | Máximo | Media |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Global | 19768 | 19739 | 29 | 65,67 | 4 | 6 | 263 | 288 | 743 | 25 |
| Disponibilidad | 14380 | 14380 | 0 | 47,77 | 4 | 5 | **11** | 20 | 314 | 6 |
| XSRF inicial | 1401 | 1401 | 0 | 4,65 | 2 | 3 | 5 | 8 | 234 | 3 |
| Login cliente | 1401 | 1401 | 0 | 4,65 | 269 | 279 | 308 | 410 | 743 | 275 |
| XSRF con sesión | 1401 | 1401 | 0 | 4,65 | 2 | 2 | 4 | 9 | 38 | 2 |
| Crear reserva | 1185 | 1156 | 29 | 3,94 | 6 | 9 | 19 | 78 | 297 | 10 |

## Rechazos y aserciones

- 300 reservas creadas con 201 real.
- 856 respuestas **422 + `LIMITE_RESERVAS_ACTIVAS`**, OK esperado.
- 0 respuestas **409 + `FRANJA_NO_DISPONIBLE`**, OK esperado. La clasificación exacta de ambos rechazos se cubre también con pruebas unitarias; no se afirma que el conflicto se produjo en esta ejecución.
- 216 recorridos de creación sin franja (fechas sin jornada, incluidos domingos): omiten POST, registrados explícitamente.
- 29 POST con código distinto de los dos aceptados: **KO**, sin ocultarlos ni asumir el código. El log agrupa la validación como `jsonPath($.codigo).find.rechazo esperado exacto ... Código de rechazo inesperado`; no conserva el código concreto. KO de POST: 2,447 %; el umbral exigido se aplica a disponibilidad y al global HTTP (**0,146702 %**). Se documenta esta diferencia para no presentar todas las reservas como exitosas. Un diagnóstico futuro puede conservar los códigos inesperados por separado, sin ampliar la lista de OK.

```text
Disponibilidad: 95th percentile ... <= 2000.0 : true (actual : 11.0)
Disponibilidad: percentage of failed events ... < 1.0 : true (actual : 0.0)
Global: percentage of failed events ... < 1.0 : true (actual : 0.14670174018615945)
Crear reserva: count of successful events ... > 0.0 : true (actual : 1156.0)
[INFO] BUILD SUCCESS
```

El hook final exige además al menos un 201 real (300 observados). La saturación del cupo de los cien clientes provoca muchos 422 al avanzar la prueba: mide RNF-01 con los rechazos permitidos por el plan, no certifica rendimiento de escrituras exitosas sostenidas ni capacidad de un despliegue remoto.

## Ensayo, reproducción y limpieza

Ensayo previo corregido: 5 usuarios/20 s, 146 HTTP, 0 KO, 92 consultas de disponibilidad con p95 38 ms y 12 creaciones reales. [Agregados del ensayo](ensayo-breve.json); HEAD `4fcebac`, con las correcciones luego confirmadas en `e464b20` presentes en el árbol de trabajo. [Agregados oficiales](carga-50-usuarios.json), sin cookies, cuerpos o datos de cuentas. Se guarda resumen Markdown/JSON; HTML y log binario permanecen ignorados en `perf/target/gatling/`.

```powershell
.\perf\ejecutar-carga.ps1
.\perf\resumir-carga.ps1 -Ejecucion perf/target/ejecuciones/20261004-063550 -Informe perf/target/gatling/disponibilidadsimulation-20261004113620910 -Salida docs/pruebas/carga/carga-50-usuarios.json
```

Ambas ejecuciones exitosas y los ensayos fallidos ejecutaron `DROP DATABASE` después de detener únicamente su backend. Comprobación final: **0** bases llamadas `barberturno_perf`, **0** Java/Maven/Gatling del encargo y **0** escuchas 8080/4200; procesos Java preexistentes del IDE conservados. No se usó ni vació otra base. [Evidencia completa, pruebas e incidencias](../t-35.md).
