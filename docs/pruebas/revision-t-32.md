# Revisión técnica de T-32 — Datos de demostración y corrida manual automatizada

- **Fecha:** 04/10/2026 · **Revisor:** Claude Code · **Encargo:** 036 (Codex, `--approve-for-me`, código 0, 38 min). Clasificación: NORMAL. Revisión de Gemini no requerida (la corrección de RN-15 incluida es de dos líneas, evidente y con su prueba).
- **Commits:** `48bfae2`, `7cbe4ef` (corrección RN-15), `94da026`, `bb2adc6`, merge `1e62954`, registro `5964019`.
- **Resultado:** **Aprobada.** La corrida automatizada destapó un defecto real de T-23, ya corregido.

## Verificado
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Demo | `DatosDemoRunner`/`DatosDemoService` (`@Profile("demo")`), atómica e idempotente por marcador; contraseña solo de `BT_DEMO_PASSWORD` (si falta: WARN y no se carga nada); escenario del prototipo con BT-100…BT-104, servicios, jornadas y K1; correos `@ejemplo.test`. |
| V-02 | Reloj fijo | `ClockConfig`: `barberturno.reloj-fijo` solo en `demo`/`test`; en `prod` el arranque falla (también en `ConfiguracionProduccion`, que además impide combinar `prod` con `dev`, `test` o `demo`). |
| V-03 | Corrida (`CorridaManualIT`) | 8 pasos por la API con sesiones reales y el `Clock` avanzado al 01/10; conciliación: 3 reservas (cancelada, completada, confirmada), `precio_ref` S/ 25 tras subir S1 a S/ 30, 8 auditorías y 8 avisos a clientes + 5 a Carlos (RN-15, documentados aparte); los rechazos no dejan cambios. |
| V-04 | **Defecto corregido (heredado de T-23)** | Al reprogramar, el cliente que actuaba no recibía su aviso, y RN-15 dice "siempre el cliente" (la excepción del actor es solo para los barberos). `7cbe4ef` lo corrige y ajusta `ReprogramacionIT`. Adenda en la revisión de T-23. |
| V-05 | Verificación (Codex) | `clean verify` **1407/1407** local y en worktree sin `.local/`; JaCoCo cumplido; 0 avisos de Javadoc. Dos arranques reales `dev,demo` seguidos sobre `barberturno_demo_t32`: sin duplicados; disponibilidad del 01/10 de Carlos con 31 franjas, 10:00 ocupada y K1 excluido. |
| V-06 | Bases de datos (revisor) | `barberturno` (desarrollo) vacía. `barberturno_demo_t32` creada por Codex y conservada: solo el escenario ficticio (6 usuarios `@ejemplo.test`, 5 reservas); útil para la sustentación; el responsable puede borrarla. |
| V-07 | RA-02 | 63,58 % de Java sin pruebas (sin T-18); 77,23 % con pruebas. |

## Observaciones
| # | Observación | Acción |
|---|---|---|
| O-1 | El agotamiento de conexiones reapareció al arrancar la demo mientras corría `verify`. | Regla para los encargos: no arrancar la aplicación mientras corre la suite (ya aplicado por Codex). |
| O-2 | Codex encontró una vez clases compiladas con "Unresolved compilation problems" (huella del compilador de Eclipse): la extensión Java de VS Code compila en `backend/target`. `clean verify` lo resuelve. | Riesgo de entorno anotado; las verificaciones válidas son las de `clean verify`. |
