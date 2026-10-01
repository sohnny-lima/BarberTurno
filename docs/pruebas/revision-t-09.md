# Revisión técnica de T-09 — Reglas de dominio puras y umbral de JaCoCo

- **Fecha:** 01/10/2026 · **Revisor:** Claude Code · **Encargo:** 013 (`--approve-for-me`, código 0, 24 min).
- **Commits:** `0827b44`, `9e2302d`, `65561d5`, `7d7a8f9`, merge `f7b90cf`, registro `309c6a9`.
- **Resultado:** **Aprobada.**

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | Clases puras (`ReglasTemporales`, `PoliticaTransiciones`, `EstadoReserva`, `CalculadoraFranjas`, `PoliticaPassword`), `ParametrosReserva` + `ConfiguracionReservas`, la invariante de `Reserva`, la regla de JaCoCo en el pom y las pruebas. Sin cambios en frontend, CI, APF2 ni documentos de diseño. |
| V-02 | Pureza | Ninguna de las cinco clases importa `org.springframework` ni `jakarta.persistence`. |
| V-03 | Reglas | `puedeModificar`: el cliente necesita `inicio − ahora ≥ anticipación` (límite exacto permitido); el admin necesita `ahora < inicio` y un motivo con ≥ 5 caracteres no blancos (cuenta puntos de código Unicode). `Reserva.cambiarEstado` lanza `IllegalStateException` si `EstadoReserva.puedePasarA` lo impide (añadido a petición de la revisión de T-07). |
| V-04 | **`mvnw.cmd verify` (JDK 21)** | **518 pruebas**, 0 fallos/errores/omitidas; `jacoco:check (check-dominio)`: "All coverage checks have been met" con un mínimo de 0,70 en `reservations*` y `scheduling*`. |
| V-05 | Cobertura de las clases puras (`jacoco.csv`) | `ReglasTemporales` 19/19, `PoliticaTransiciones` 19/19, `EstadoReserva` 10/10, `CalculadoraFranjas` 33/33 y `PoliticaPassword` 9/9: **100 %**. Codex comprobó que el umbral falla si se exige 1,00 en `scheduling` (94,95 %): el control negativo funciona. |
| V-06 | Escenario de la corrida | La calculadora da 31 franjas el 01/10 con la jornada partida, la reserva de 10:00 y el bloqueo de 16:00. Las contiguas se permiten. Las candidatas de la rejilla coinciden con `esFranjaValida`. |

## Hallazgos aplicados en este commit de revisión
- Arquitectura §4.1: `scheduling` también depende de la clase pura `ReglasTemporales`.
- Requisitos RN-25: además del límite de caracteres, máximo de 72 bytes en UTF-8 (BCrypt).
- Requisitos RN-08: se documenta la diferencia con el caso 6 del anexo del APF2 (el administrador justo en el inicio). Prevalece RN-08: estrictamente antes del inicio.
