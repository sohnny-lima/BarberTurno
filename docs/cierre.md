# BarberTurno · Registro único de cierre

Estado verificable del cierre del proyecto. Se actualiza en cada hito; el detalle está en las evidencias enlazadas. Última actualización: **18/10/2026**.

## 1. Terminado y acreditado
| Elemento | Evidencia | Estado |
|---|---|---|
| Plan T-01…T-57 (T-53 no abierta por decisión del responsable) | `docs/tareas.md` (tabla de estado y notas de cierre) | Hecho y revisado |
| Fase 1 visual (T-48 → T-55) y ayuda de contraseña (T-56) | `docs/pruebas/integracion-t56.md`, `docs/pruebas/t-55/` | Integrado en `main` (`17e4081`) |
| **Fase 2 visual (T-57):** acceso, cuenta, servicios, barberos, horarios, reportes, usuarios y diálogos | `docs/diseno/t-57.md`, `docs/pruebas/t-57.md`, `docs/pruebas/t-57/capturas/` (98 PNG, axe 0, guía en `indice.md`) | Integrado en `main` (merge `bb3d644`; código verificado en `720f022`). Gemini g034 y g036 (APROBADO) |
| Verificación de la versión publicada (checkpoint del coordinador, 17–18/10/2026) | `docs/tareas.md`, nota «T-57 · Cierre del coordinador» | `mvnw clean verify` 1548/1548; lint y formato; Vitest 442/442 en Lima y Madrid; build inicial 496,39 kB sin elevar presupuestos; E2E 48/48 (Chromium y Firefox, 360 y 1440 px); capturas 88/88 |
| **Publicación** | `origin/main` = `d2f3107` (código) | CI **39099141093** sobre `d2f3107`: **success** (18/10/2026, API pública de GitHub). Los commits posteriores son solo documentación |
| 18 de 19 casos de aceptación con evidencia técnica (CP-01…CP-19 salvo CP-11) | `docs/pruebas/aceptacion.md` | Acreditado |
| RNF-01 (carga), RNF-03 (salvo HTTPS real), RNF-04, RNF-05, RNF-08, RNF-09 (ensayo local), RNF-10, RNF-11, RNF-12, RNF-13, RNF-14 | `docs/pruebas/aceptacion.md` §RNF | Acreditado con las salvedades indicadas allí |
| CP-11 preparado sobre la versión final | `docs/pruebas/cp11-sesiones.md`, `docs/pruebas/usabilidad-plantilla.md` | Jar final fijado (SHA-256), comandos P1–P5 con copias independientes, consentimiento, instrucciones, fichas y distribución; ensayo con «RELOJ COMPARTIDO OK». **0/5 sesiones** |

## 2. Trabajo pendiente para el cierre
| Trabajo | Quién | Estado |
|---|---|---|
| **CP-11:** cinco sesiones con personas (P-07) | Responsable (facilitador) | Materiales listos; 0/5. No se sustituye por E2E ni cuenta el piloto |
| **P-03:** alojamiento, HTTPS, monitor, copias, restauración y reversión | Claude preparó · Gemini contrastó (g035) · **responsable aprueba** | Propuesta, coste de la configuración concreta (§8: ≈ 12,41 USD/mes del crédito, 0 de bolsillo; ≈ 8 meses estimados, condicionados al consumo) y guía de verificación (§9) en `docs/p-03-alojamiento.md`. Azure for Students **activada** por el responsable el 18/10/2026 (100 USD, gasto 0, vence el 18/10/2027). Suscripción confirmada en Cloud Shell (Enabled, AzureForStudents_2018-01-01, límite de gasto On; regiones permitidas westus, westus2, canadacentral, brazilsouth y chilecentral; B1MS, B1 Linux y Java 21 disponibles). Configuración final única en §11: **West US 2**, App Service Linux B1 + PostgreSQL 18 B1MS, ≈ 12,41 USD/mes del crédito, ≈ 8,1 meses estimados, condicionados al consumo. **Pendiente:** comprobar *Servicios gratuitos* y aprobar. Ningún recurso creado |
| Despliegue, ensayo de restauración y monitor | Tras la aprobación de P-03 | Pendiente |
| **RNF-02:** 99 % mensual, excluido el mantenimiento | Tras el despliegue | Método definido en `docs/p-03-alojamiento.md` §7; **no cumplido ni medible** hasta cerrar un mes de observación con evidencias |
| RNF-07: matriz con las versiones comerciales de Chrome, Edge y Firefox | Claude/Codex | Parcial (Playwright Chromium y Firefox). Se completará sobre la versión desplegada |
| `listen_addresses` de PostgreSQL y limpieza local | Claude preparó · responsable decide | Propuesta lista, nada aplicado ni borrado; la limpieza, tras el despliegue (ver §3) |

## 3. Intervención que necesito del responsable
| Decisión o acción | Detalle |
|---|---|
| CP-11 | Conseguir 5 participantes y ejecutar las sesiones con `docs/pruebas/cp11-sesiones.md`. Antes, fijar el **plazo de conservación** y el **canal de retirada** de datos que se leen en el consentimiento. |
| P-03 | **Paso actual:** comprobar en *Servicios gratuitos* el beneficio de PostgreSQL (§11, «Verificación del beneficio gratuito») y aprobar la configuración única de §11 (West US 2). Después: creación, secretos introducidos por el responsable, despliegue, HTTPS y monitor. |
| PostgreSQL local | Aprobar el cambio a `listen_addresses = 'localhost'` en las instancias 17 y 18 (hoy escuchan en todas las interfaces aunque `pg_hba` solo admite conexiones locales; ninguna aplicación necesita acceso remoto). Requiere consola de administrador y reinicio del servicio. |
| Limpieza | **Aplazada hasta después del despliegue** (decisión del responsable, 18/10/2026); se conservan todas las bases y respaldos. Propuesta por elemento: caché npm de C: (3,5 GB), restos de `frontend/tmp` (≈ 1,4 GB, conservando los navegadores), worktrees `BarberTurno-diag`, bases de revisión `t48/t54/t55`, verificaciones y ensayo de CP-11. Se conservan `barberturno_demo_t32` y `antes-de-18.6-sin-clean.sql`. |

## 4. Mejoras futuras fuera del cierre
- P-01 validación de reglas y precios con la barbería, P-06 política de inasistencias, P-08 informe del APF1 (decisiones del negocio o del docente).
- T-53 (precarga de componentes diferidos en `app.routes.spec`): no abierta; solo si el timeout se reproduce en ejecución aislada.
- Autorregistro de barberos y fotografías de profesionales: fuera de alcance por decisión del responsable.
- Monitor con comprobación cada minuto (plan de pago) y nonce de CSP en lugar de `style-src 'unsafe-inline'`.
