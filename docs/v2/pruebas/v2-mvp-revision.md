# Rama local de revisión del MVP (V2-07 … V2-13) · Evidencia e instrucciones

Fecha: 04–05/11/2026. Rama **`tarea/V2-MVP-revision`** (worktree `D:\Proyectos\BarberTurno-v2-mvp`), desde la rama conjunta `ddd3fce` (V2-07…V2-10 ya validadas). **No está integrada en `v2` ni publicada**; `main`, Azure, las bases de v1 y los entregables académicos no se tocaron.

## 1. Qué contiene
| Tarea | Estado | Evidencia |
|---|---|---|
| V2-07 … V2-10 | Hechas y validadas antes (sin cambios aquí) | `v2-07.md` … `v2-10.md`, validación conjunta |
| V2-11 · derivación a una persona, bandeja web, avisos de derivación, mecanismos técnicos de P2-06 (retención por fases, anonimización de una ficha, entrantes mínimos) | Hecha en local | `v2-11.md`, `v2-11-backend.md` |
| V2-12 · conversación con IA (turnos fuera de la entrega, herramientas con alias, guardas, límites y costes, contexto mínimo, adaptador de Anthropic) | Hecha en local, **sin clave** | `v2-12.md`, `v2-12-backend.md` |
| V2-13 · evaluación determinista en `verify`, arnés en vivo, conjunto de 120, plantilla del informe | Hecha la parte local; arnés **sin ejecutar** | `v2-13.md` |
| Entorno de revisión manual con la bandeja (g094) | Actualizado | `frontend/tools/revision/LEEME.md` |

Las propuestas de P2-06 (plazos 30/90/30 días, anonimización como D5, entrantes mínimos como D6) solo existen en `dev` y `test`, marcadas **BORRADOR P2-06**. En `prod` las purgas nuevas están desactivadas. Nada está aprobado.

## 2. Resultados ejecutados (cerrojo serializado sobre `barberturno_v2_test`)
| Qué | Commit | Resultado |
|---|---|---|
| `verify` completo con V2-07…V2-11 | `d97e55c` | 3061/0/0/0, JaCoCo y Javadoc correctos |
| `verify` completo con V2-12/13 | `9a79259^` (antes del ajuste) | 3275 pruebas, 1 fallo (`DerivacionV116IT`, permisos de V117) → corregido en `ec576e2`; clases de esquema 45/0 |
| `verify` completo (punto intermedio) | `6f875a9` | 3275/0/0/0, JaCoCo y Javadoc correctos (05/11 00:3x) |
| `verify` completo tras las revisiones 129…g100 | `005c19a` | 3608 pruebas, 1 fallo reproducible (`TurnosIAIT`, caso `vacia`: expectativa anterior a V2-DA-85) → corregido en `d7bca85` (40/0) |
| `verify` completo | `373a6ee` | 3608/0/0/0 |
| **`verify` completo final** (con 136/137/g101) | **`1d9c307`** | **3662/0/0/0**, JaCoCo y Javadoc correctos (05/11 06:5x) |
| Frontend: lint / *build* | `9a79259` | correcto / 499,56 kB sin avisos de presupuesto |
| Frontend: unitarias | `9a79259` | 588/589; la que falla es `app.spec` por *timeout* de 5 s con la máquina cargada, 1/1 aislada |
| E2E `recorrido.spec.ts` (84) | `9a79259` | 79 correctas, 1 *timeout* en firefox-360 (prueba 17) y 4 sin ejecutar detrás; repeticiones: 17 y 20 correctas en los cuatro proyectos (intermitencia por tiempo de firefox-360 tras las capturas axe) |
| E2E `v2-11-bandeja.spec.ts` (nunca se había ejecutado) | `6f875a9` | 4/4 tras corregir su siembra y sus pasos |
| E2E completo (punto intermedio) | `6f875a9` | 88/88 en 16,3 min |
| E2E completo | `005c19a` | 86 correctas, 1 *timeout* (prueba 20, firefox-1440) y 1 sin ejecutar detrás; 20 y 21 repetidas: 2/2 |
| **E2E completo final** (`recorrido` + `v2-11-bandeja`, chromium y firefox, 1440 y 360) | **`1d9c307`** | **88/88** en 15,2 min, sin repeticiones |
| Entorno de revisión `-Verificar` | `985e136` | rc=0: cinco cuentas, bandeja (dentro y fuera de la ventana), profesional 403, sondas |
| **Entorno de revisión `-Verificar` final** | **`1d9c307`** | rc=0, mismas comprobaciones; axe lista elementos **no determinables** (contraste sobre superposiciones, anclas de foco de los diálogos) para revisión manual |

### Revisiones cruzadas de V2-11…V2-13 (nadie aprobó su propio bloque)
| Revisión | Revisor | Sobre | Resultado |
|---|---|---|---|
| 129 | Codex (solo lectura) | G11, GI, CI, guiones | Rechazados (15 defectos) → V2-DA-84, Codex 131, Claude `8123429`, Gemini g097 |
| g098 · g099 | Gemini Argon | Codex 131 · 132 | Guardas con defectos → V2-DA-85, Codex 132 y 135; bandeja sin hallazgos en g099 |
| 133 | Codex (solo lectura) | g097, `8123429` | Arnés, correctores y saneador rechazados → Claude `53326bd`, Codex 134 |
| g100 | Gemini Argon | Codex 134 | Salvaguardas resueltas; 4 defectos de medición → Claude `416e6c2` |
| 136 | Codex (solo lectura) | `53326bd`, `416e6c2`, `d7bca85` | Saneador y correctores rechazados → Codex 137/137b; diagrama → Claude `bfce0d6` |
| g101 | Gemini Argon | Codex 137/137b | **Aprobado** sin hallazgos |

Riesgo residual aceptado (V2-DA-85): paráfrasis libres de una confirmación pueden pasar las guardas; las mide «Confirmaciones falsas» y ninguna operación se ejecuta sin el botón de confirmar del servidor.

## 3. Cómo revisarla
1. **Arrancar el entorno** (PowerShell 7, desde `D:\Proyectos\BarberTurno-v2-mvp`; requiere el jar con el *frontend*, que deja el último E2E en `backend\target`):
   ```powershell
   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1
   ```
   Pide una clave ficticia (oculta) para las cuentas de demostración, prepara `barberturno_v2_test` con dos barberías ficticias y abre dos ventanas (escritorio y móvil 360 × 640). La revisión termina al cerrarlas; la base se vacía al salir. Si el jar no tiene el *frontend*: `cd frontend; npm run build; cd ..\backend; .\mvnw.cmd -Pcon-frontend "-Dexec.skip=true" -DskipTests package`.
2. **Recorridos:** `frontend/tools/revision/LEEME.md` §2 (cuentas, cambio de barbería y de papel, avisos y, nuevo, **§2.4 bandeja de WhatsApp**: aviso de derivación → conversación, responder dentro de la ventana, botón deshabilitado fuera de ella, «Devolver al asistente», el profesional y el cliente sin la entrada «WhatsApp»).
3. **Lo que no se ve en pantalla:** la IA (V2-12) está desactivada sin clave; el texto libre recibe los menús guiados de V2-10. Su comportamiento se comprueba con las pruebas (`TurnosIAIT`, `ConversacionGuionesIT`, `ModeloAnthropicTest`). **No hay conexión con Meta:** las respuestas de la bandeja quedan en la outbox (`PENDIENTE`).
4. **Código:** `git log --oneline --first-parent ddd3fce..tarea/V2-MVP-revision` lista las uniones; cada bloque tiene su rama `tarea/V2-11-*` / `tarea/V2-12-*` con autor y revisor en su evidencia.

## 4. Lo que requiere al responsable
- **P2-05 (Meta):** número, token y plantillas; hasta entonces todo va contra el servidor Graph falso.
- **P2-06:** decisiones D1…D7 (incluidas D5 anonimización y D6 entrantes mínimos) y los plazos; el aviso de privacidad debe mencionar al proveedor de IA.
- **P2-03:** clave, proveedor y modelo de IA; presupuesto de la evaluación en vivo (propuesta ≤ 10 USD).
- **V2-13:** revisión del conjunto de 120 conversaciones (BORRADOR) y de su partición de prueba.
- Revisión manual de esta rama y decisión de integrarla en `v2`.
