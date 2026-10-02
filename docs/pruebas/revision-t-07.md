# Revisión técnica de T-07 — Entidades JPA y repositorios

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 011 (`--approve-for-me`, código 0, 27 min).
- **Commits:** `82f7c1b`, `f8050c9`, `7eea2c9`, merge `359d8a4`, registro `b75a40d`.
- **Resultado:** **Aprobada.**

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | 8 entidades, 3 enumerados y 8 repositorios en `users`, `catalog`, `scheduling`, `reservations`, `audit` y `notifications`, más pruebas y soporte. Sin cambios en migraciones, recursos, pom, frontend, CI, APF2 ni documentos de diseño. |
| V-02 | Convenciones | En `src/main` no hay `Instant.now()`, `LocalDateTime.now()`, `Clock.system` (salvo `ClockConfig`) ni Lombok; tampoco *setters* públicos. Los cambios pasan por métodos de dominio (`reprogramar`, `cambiarEstado`…). El correo se normaliza con `toLowerCase(Locale.ROOT)`. `@Version` en `Reserva`. `jsonb` como `Map` inmutable con `@JdbcTypeCode(SqlTypes.JSON)`. |
| V-03 | Reglas de la entidad | El constructor de `Reserva` copia `duracionRefMin` y `precioRef` del servicio y calcula `fin`. `reprogramar` recalcula `fin` con la duración de referencia, no con la del servicio actual (RN-09, RN-13). |
| V-04 | Consultas | `bloquearPorIds` con `PESSIMISTIC_WRITE` y `order by b.id`; `bloquearPorId` en usuario y reserva; solapamientos con el centinela `0L`, sin parámetros nulos en JPQL. |
| V-05 | **`mvnw.cmd verify` (JDK 21)** | **80 pruebas** (16 de `RepositoriosIT`), 0 fallos/errores/omitidas. SQL real del bloqueo: `… for no key update of u1_0`. |
| V-06 | Concurrencia y otras comprobaciones de Codex | B espera hasta el commit de A; `55P03` con un `lock_timeout` corto; versión obsoleta rechazada; identidad que vuelve a empezar en 100 tras `LimpiezaBaseDatos`; 80/80 también en un worktree sin `.local/`; tablas vacías al final. |

## Observaciones
| ID | Observación | Acción |
|---|---|---|
| O-1 | Hibernate usa `FOR NO KEY UPDATE`. Es válido para el protocolo y además mejor, porque no bloquea las comprobaciones de clave foránea. | Documentado en arquitectura §8 en el commit de esta revisión. |
| O-2 | `Reserva.cambiarEstado` todavía no valida la transición, que llega en T-09. | Añadido al encargo de T-09: la entidad rechazará las transiciones que `EstadoReserva` no permita, como última defensa. |

## Adenda (02/10/2026, revisión de T-16)
**Defecto no detectado en esta revisión:** el mapeo de `Jornada.horaInicio/horaFin` (`LocalTime` → `time`) se veía afectado por `hibernate.jdbc.time_zone=UTC`: la hora de Lima se guardaba desplazada +5 h. La lectura deshacía el desplazamiento, por eso las pruebas de ida y vuelta pasaban, pero un tramo que terminara a partir de las 19:00 de Lima incumplía `jornada_intervalo_valido`. Corregido en T-16 con `@JdbcTypeCode(SqlTypes.LOCAL_TIME)` y con pruebas que leen el valor físico (`hora_inicio::text`). **Lección:** en los mapeos de fecha y hora, comprobar el valor almacenado con JDBC, no solo la ida y vuelta por JPA.
