# BarberTurno · Registro único de cierre

Estado verificable del cierre del proyecto. Se actualiza en cada hito; el detalle está en las evidencias enlazadas. Última actualización: **18/10/2026**.

## 1. Terminado y acreditado
| Elemento | Evidencia | Estado |
|---|---|---|
| Plan T-01…T-56 (T-53 no abierta por decisión del responsable) | `docs/tareas.md` (tabla de estado y notas de cierre) | Hecho y revisado |
| Fase 1 visual (T-48 → T-55) y ayuda de contraseña (T-56) | `docs/pruebas/integracion-t56.md`, `docs/pruebas/t-55/` | Integrado en `main` (`17e4081`) |
| Publicación | `origin/main` = `f7dc336` | CI `37895739087` (`17e4081`) y `37896565925` (`f7dc336`): success (comprobado en la API pública el 17/10/2026) |
| 18 de 19 casos de aceptación con evidencia técnica (CP-01…CP-19 salvo CP-11) | `docs/pruebas/aceptacion.md` | Acreditado |
| RNF-01 (carga), RNF-03 (salvo HTTPS real), RNF-04, RNF-05, RNF-08, RNF-09 (ensayo local), RNF-10, RNF-11, RNF-12, RNF-13, RNF-14 | `docs/pruebas/aceptacion.md` §RNF | Acreditado con las salvedades indicadas allí |
| **Fase 2 visual (T-57)**: acceso, cuenta, servicios, barberos, horarios, reportes, usuarios y diálogos | `docs/diseno/t-57.md`, `docs/pruebas/t-57.md`, capturas `docs/pruebas/t-57/capturas/` (98 PNG, axe 0) | Hecho y revisado (Gemini g036 APROBADO); integración en `main` en curso |
| Datos y scripts de CP-11 verificados (plantilla con BT-100 y BT-103, reloj compartido) | `docs/pruebas/usabilidad-plantilla.md` (`3684999`) | Preparado; 0/5 sesiones |

## 2. Trabajo necesario para el cierre (en curso o independiente)
| Trabajo | Responsable | Estado |
|---|---|---|
| Integrar T-57 en `main`, push y comprobar la CI | Claude (autorizado) | Tras T-57.5 y revisión de Gemini |
| CP-11 sobre la versión final: jar, scripts, reloj y materiales de P1…P5 | Claude prepara | Tras integrar T-57 |
| P-03 · comparación de alojamiento, recomendación y material de despliegue (HTTPS, salud, monitor, copias, restauración y reversión) | Claude investiga · Gemini contrasta | Pendiente de iniciar |
| RNF-02 · método de evaluación (99 % mensual excluido mantenimiento) | Claude | Se define con P-03; no se declara cumplido sin periodo ni evidencias |
| RNF-07 · matriz con navegadores comerciales (Chrome, Edge, Firefox) | Claude/Codex | Parcial (Playwright Chromium y Firefox) |
| `listen_addresses` de PostgreSQL y limpieza de archivos, bases de revisión y worktrees | Claude prepara · responsable aprueba | Por preparar (sin borrar nada) |

## 3. Intervención que necesito del responsable
| Decisión o acción | Cuándo |
|---|---|
| Reclutar y ejecutar CP-11 con 5 participantes (el piloto propio no cuenta) | Cuando estén listos los materiales (§2) |
| Aprobar servicio de alojamiento, coste y configuración; introducir los secretos directamente en el proveedor | Tras la comparación de P-03 |
| Autorizar explícitamente cualquier borrado de bases o respaldos (se conservan `barberturno_demo_t32` y `antes-de-18.6-sin-clean.sql`) | Tras la propuesta de limpieza |
| Aplicar, si procede, el cambio de `listen_addresses` (requiere reiniciar el servicio) | Tras la propuesta |

## 4. Mejoras futuras fuera del cierre
- P-01 validación de reglas y precios con la barbería, P-06 política de inasistencias, P-08 informe del APF1 (decisiones del negocio o del docente).
- T-53 (precarga de componentes diferidos en `app.routes.spec`): no abierta; solo si el timeout se reproduce en ejecución aislada.
- Autorregistro de barberos y fotografías de profesionales: fuera de alcance por decisión del responsable.
