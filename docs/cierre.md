# BarberTurno · Registro único de cierre

Estado verificable del cierre del proyecto. Se actualiza en cada hito; el detalle está en las evidencias enlazadas. Última actualización: **19/10/2026**. Freeze vigente (AGENTS.md §5).

## 1. Terminado y acreditado
| Elemento | Evidencia | Estado |
|---|---|---|
| Plan T-01…T-57 (T-53 no abierta por decisión del responsable) | `docs/tareas.md` (tabla de estado y notas de cierre) | Hecho y revisado |
| Fase 1 visual (T-48 → T-55) y ayuda de contraseña (T-56) | `docs/pruebas/integracion-t56.md`, `docs/pruebas/t-55/` | Integrado en `main` (`17e4081`) |
| **Fase 2 visual (T-57):** acceso, cuenta, servicios, barberos, horarios, reportes, usuarios y diálogos | `docs/diseno/t-57.md`, `docs/pruebas/t-57.md`, `docs/pruebas/t-57/capturas/` (98 PNG, axe 0, guía en `indice.md`) | Integrado en `main` (merge `bb3d644`; código verificado en `720f022`). Gemini g034 y g036 (APROBADO). Terminada por decisión del responsable (18/10/2026): no se abren más tareas visuales |
| Verificación de la versión publicada (checkpoint del coordinador, 17–18/10/2026) | `docs/tareas.md`, nota «T-57 · Cierre del coordinador» | `mvnw clean verify` 1548/1548; lint y formato; Vitest 442/442 en Lima y Madrid; build inicial 496,39 kB sin elevar presupuestos; E2E 48/48 (Chromium y Firefox, 360 y 1440 px); capturas 88/88 |
| **Publicación del código** | `origin/main`: código en `d2f3107` | CI **39099141093** sobre `d2f3107`: **success**. Los commits posteriores son solo documentación |
| 18 de 19 casos de aceptación con evidencia técnica (CP-01…CP-19 salvo CP-11) | `docs/pruebas/aceptacion.md` | Acreditado (entorno local) |
| RNF-01 (carga), RNF-03 (salvo HTTPS real), RNF-04, RNF-05, RNF-08, RNF-09 (ensayo local), RNF-10, RNF-11, RNF-12, RNF-13, RNF-14 | `docs/pruebas/aceptacion.md` §RNF | Acreditado con las salvedades indicadas allí |
| **Despliegue en Azure for Students** (19/10/2026) | `docs/p-03-alojamiento.md` §12 | **West US**: App Service Linux B1 `barberturno-web-202610` (Java SE 21, *Always On*, *HTTPS Only*, *Health check* correcto) y PostgreSQL 18.6 B1ms `pg-barberturno-202610` (32 GB, copias de 7 días, rol de aplicación propio, `btree_gist`, TLS `verify-full`). Jar `barberturno-final.jar`, SHA-256 `aa4dcf8f…88bc2` = código de `d2f3107`; despliegue `b850dc93-92cc-4c3c-b66d-a004ec9f3b39` `RuntimeSuccessful` |
| **HTTPS y salud en producción (RNF-03, RNF-14)** | `docs/p-03-alojamiento.md` §12.3 | `/actuator/health` 200 UP; HTTP→HTTPS 301; HSTS, CSP y demás cabeceras en GET; cookie XSRF `Secure; SameSite=Strict`; Swagger no público. Acceso del administrador a Agenda (responsable) |
| Monitor externo | UptimeRobot `804331244` | Configurado (cada 5 min, alerta por correo de prueba recibida). No acredita caídas ni recuperaciones reales |
| CP-11 preparado sobre la versión final | `docs/pruebas/cp11-sesiones.md`, `docs/pruebas/usabilidad-plantilla.md` | Jar identificado, comandos P1–P5 con copias independientes, consentimiento, instrucciones, fichas y distribución; ensayo con «RELOJ COMPARTIDO OK». **0/5 sesiones** |

## 2. Trabajo pendiente para el cierre
| Trabajo | Quién | Estado |
|---|---|---|
| **Prueba mínima en producción** (PM-1…PM-8) | Responsable ejecuta · Claude guía y registra | PM-0 hecho. PM-1 (inventario de solo lectura) en curso; las escrituras, PM-2 en adelante, requieren autorización y se anotan en §12.5 |
| **Beneficio gratuito de PostgreSQL** | Responsable consulta · Claude registra | Revisar *Usage* del portal de patrocinios entre el 20/10 02:00 y el 22/10 02:00 de Lima: PostgreSQL con gasto 0; App Service ≈ 0,43 USD/día |
| Coste real de App Service | — | West US: ≈ **13,14 USD/mes del crédito**; ≈ 7,6 meses estimados, condicionados al consumo (hacia comienzos de junio de 2027) |
| Ensayo de restauración en Azure (RNF-09 en producción) | Claude preparó · **responsable autoriza** | Procedimiento y coste (< 0,10 USD si se hace en octubre y se elimina el mismo día) en §12.6. No ejecutado |
| Protecciones operativas: bloqueo `CanNotDelete` de la base, avisos de cambios por correo, copia lógica semanal con latido | Claude preparará · **responsable autoriza** | Sin evidencia de creación. Coste 0 (Azure) / gratuito (Healthchecks.io) |
| **RNF-02:** 99 % mensual, excluido el mantenimiento | Observación | Monitor desde ≈ 19/10/2026 02:15 de Lima (hora exacta por confirmar); periodo propuesto: **noviembre de 2026** completo en hora de Lima; octubre se conserva como observación previa. **No cumplido** hasta el cálculo del 01/12/2026 (§12.7) |
| **CP-11:** cinco sesiones con personas (P-07) | Responsable (facilitador) | 0/5. No se sustituye por E2E ni cuenta el piloto |
| RNF-07 (versiones comerciales de Chrome, Edge y Firefox) | Responsable/Claude | Parcial; PM-7 aporta Chrome y Firefox reales en producción |
| Mantenimiento local separado: `listen_addresses` y limpieza de C: | Claude preparó · responsable decide | Propuesta lista, nada aplicado; no se mezcla con las pruebas del despliegue |

## 3. Intervención que necesito del responsable
| Decisión o acción | Detalle |
|---|---|
| Prueba mínima | Ejecutar PM-1 ahora (solo lectura) y autorizar después las escrituras de PM-2 a PM-8 con los datos ficticios propuestos. |
| Coste de PostgreSQL | Consultar *Usage* en la ventana de 24–72 h indicada. |
| Ensayo de restauración y protecciones | Autorizar (o no) los recursos de §12.6 y las protecciones de coste 0. |
| RNF-02 | Confirmar la hora exacta de creación del monitor y fijar la zona horaria de UptimeRobot en Lima; el 01/12/2026 guardar los incidentes y la disponibilidad de noviembre. |
| CP-11 | Conseguir 5 participantes y fijar el **plazo de conservación** y el **canal de retirada** de datos del consentimiento. |
| Mantenimiento local | Decidir aparte `listen_addresses = 'localhost'` y la limpieza (se conservan `barberturno_demo_t32`, los respaldos y las bases locales). |

## 4. Mejoras futuras fuera del cierre
- P-01 validación de reglas y precios con la barbería, P-06 política de inasistencias, P-08 informe del APF1 (decisiones del negocio o del docente).
- T-53 (precarga de componentes diferidos en `app.routes.spec`): no abierta; solo si el timeout se reproduce en ejecución aislada.
- Autorregistro de barberos y fotografías de profesionales: fuera de alcance por decisión del responsable.
- Monitor con comprobación cada minuto (plan de pago) y nonce de CSP en lugar de `style-src 'unsafe-inline'`.
- `HEAD /` sin cabeceras de seguridad y `HEAD /reservar` 404 (GET correcto): observación menor sin efecto en navegadores.
