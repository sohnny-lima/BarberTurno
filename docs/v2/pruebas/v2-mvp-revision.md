# Rama local de revisión del MVP (V2-07 … V2-13) · Evidencia e instrucciones

Fecha: 04–05/11/2026. **Punto de control final: `761eb33`** (§2.1). Rama **`tarea/V2-MVP-revision`** (worktree `D:\Proyectos\BarberTurno-v2-mvp`), desde la rama conjunta `ddd3fce` (V2-07…V2-10 ya validadas). **No está integrada en `v2` ni publicada**; `main`, Azure, las bases de v1 y los entregables académicos no se tocaron.

## 1. Qué contiene
| Tarea | Estado | Evidencia |
|---|---|---|
| V2-07 … V2-10 | Hechas y validadas antes (sin cambios aquí) | `v2-07.md` … `v2-10.md`, validación conjunta |
| V2-11 · derivación a una persona, bandeja web, avisos de derivación, mecanismos técnicos de P2-06 (retención por fases, anonimización de una ficha, entrantes mínimos) | Hecha en local | `v2-11.md`, `v2-11-backend.md` |
| V2-12 · conversación con IA (turnos fuera de la entrega, herramientas con alias, **salida estructurada sin texto del proveedor (V2-DA-86)**, límites y costes, contexto mínimo, adaptador de Anthropic) | Hecha en local, **sin clave** | `v2-12.md`, `v2-12-backend.md`, `v2-12-salida-estructurada.md` |
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

### 2.1 Punto de control final · `761eb33` (05/11/2026, 10:32–11:46)
`761eb33` = unión de V2-12 `8247e91` (V2-DA-86) en `0067b9a` + formato de Prettier en tres archivos de V2-11. Todo ejecutado sobre ese commit, con el cerrojo serializado:

| Qué | Resultado |
|---|---|
| `mvnw verify` completo | **3791/0/0/0**, BUILD SUCCESS, JaCoCo y Javadoc (`doclint`) correctos |
| Frontend `npm run lint` | correcto |
| Frontend `npm run format:check` | correcto (en `3a397f0` fallaba en 3 archivos de V2-11 → `761eb33`) |
| Frontend unitarias (Vitest) | **607/607** (53 archivos) |
| Frontend *build* (dentro del E2E) | 499,56 kB iniciales, sin avisos de presupuesto |
| E2E completo (`recorrido` + `v2-11-bandeja`, chromium y firefox, 1440 y 360) | **88/88** en 15,6 min, sin repeticiones |
| Jar con *frontend* | 87 recursos estáticos |
| Entorno de revisión `-Verificar` | rc=0 (cuentas, bandeja dentro y fuera de la ventana, profesional 403, sondas) |

**Relación entre los puntos anteriores** (`d7bca85` ⊂ `373a6ee` ⊂ `1d9c307` ⊂ `3a397f0` ⊂ `761eb33`, todos antecesores):
- `d7bca85` (corrección de `TurnosIAIT` «vacia»): cubierto por el `verify` de `373a6ee` (3608/0).
- `373a6ee..1d9c307` (136/137/g101): cubierto por `verify` 3662/0 y E2E 88/88 en `1d9c307`.
- `1d9c307..3a397f0`: **solo documentación** (`v2-mvp-revision.md`); allí se ejecutó el *frontend* (lint correcto, formato con 3 fallos, unitarias 607/607). Los encargos 131/132 de Codex solo habían ejecutado lint, *build* y la prueba de la bandeja.
- `3a397f0..761eb33`: V2-DA-86 completo y el formato; cubierto por el punto final de esta tabla.

### 2.2 V2-DA-86 · cierre de las confirmaciones falsas
El texto libre del proveedor **no llega al cliente por ningún camino** (outbox, vista previa, bandeja, registros). El modelo solo elige herramientas y la terminal `responder_al_cliente` (intención y alias de listas cerradas). El servidor valida y degrada contra sus datos, y `RespuestasAsistente` compone el mensaje con los menús de V2-10. La única afirmación de una operación sigue siendo `TextosConversacion.resultado`, tras ejecutar con la confirmación del cliente y el *nonce*, con el código real (sin una segunda implementación de las reglas de reserva). Detalle, autores, revisores, resultados y **prueba de mutación** (volver a encolar el texto del proveedor: `SalidaClienteIT` 31/31 y guiones 22/35 fallan): `v2-12.md`, sección V2-DA-86. Contrato: `17-…` §13. Datos al proveedor: `15-…` §1.4 (borrador, sin aprobación ni afirmación de cumplimiento).

**No ejecutado:** la lectura de `origen` y del JSON completo en el arnés en vivo (`EscenarioEvaluacion`) compila y está revisada (Codex 142), pero no se ha ejecutado de extremo a extremo (necesita la clave de P2-03). La métrica «Confirmaciones falsas» no es una garantía semántica; la garantía es de arquitectura (no hay camino), con la mutación como prueba de sensibilidad.

### 2.3 Accesibilidad: requiere revisión manual (no aprobada automáticamente)
axe no da infracciones en las capturas, pero devuelve resultados **incompletos** (no determinables) que debe mirar una persona:
| Regla | Dónde | Qué comprobar a mano |
|---|---|---|
| `aria-hidden-focus` | Anclas `.cdk-focus-trap-anchor` de los diálogos (avisos, cambio de papel) y `app-root` con un diálogo abierto | Que el Tab no salga del diálogo ni caiga en elementos ocultos |
| `color-contrast` (fondo superpuesto) | Agenda (selector Día/Semana, selector, campos), configuración (campos) | Contraste del texto sobre el fondo real |
| `color-contrast` (superposición parcial) | Lista de avisos (1440), título del diálogo de papel (360), pista del alta (360) | Ídem |
| `skip-link` | Cajón móvil (360) · `.saltar` | Que el enlace «Saltar al contenido» se vea al recibir el foco |

### Revisiones cruzadas de V2-11…V2-13 (nadie aprobó su propio bloque)
| Revisión | Revisor | Sobre | Resultado |
|---|---|---|---|
| 129 | Codex (solo lectura) | G11, GI, CI, guiones | Rechazados (15 defectos) → V2-DA-84, Codex 131, Claude `8123429`, Gemini g097 |
| g098 · g099 | Gemini Argon | Codex 131 · 132 | Guardas con defectos → V2-DA-85, Codex 132 y 135; bandeja sin hallazgos en g099 |
| 133 | Codex (solo lectura) | g097, `8123429` | Arnés, correctores y saneador rechazados → Claude `53326bd`, Codex 134 |
| g100 | Gemini Argon | Codex 134 | Salvaguardas resueltas; 4 defectos de medición → Claude `416e6c2` |
| 136 | Codex (solo lectura) | `53326bd`, `416e6c2`, `d7bca85` | Saneador y correctores rechazados → Codex 137/137b; diagrama → Claude `bfce0d6` |
| g101 | Gemini Argon | Codex 137/137b | **Aprobado** sin hallazgos |
| 138 · g102 | Codex · Gemini Argon (solo lectura) | Contrato V2-DA-86 (Claude) | 8 + 3 propuestas aceptadas antes de programar |
| 140 | Codex (solo lectura) | RD-1 (Gemini g103), RD-2 (g104), EV (Claude) | RD-1 aprobado; RD-2 1 observación → g106; EV rechazado → `5b1c9f4` |
| g105b | Gemini 3.8 Flash (Argon sin cuota) | SE (Codex 139) | **Aprobado** |
| 142 · 143 | Codex (solo lectura) | Codex 141, g106/g107, EV | Hallazgos corregidos (g107, `3ee3faf`); 143 **aprobado** |

~~Riesgo residual aceptado (V2-DA-85): paráfrasis libres de una confirmación pueden pasar las guardas.~~ **Cerrado por V2-DA-86** (§2.2): ninguna frase del proveedor llega al cliente; las guardas quedan solo como indicador de la evaluación.

## 3. Cómo revisarla
1. **Arrancar el entorno** (PowerShell 7, desde `D:\Proyectos\BarberTurno-v2-mvp`; requiere el jar con el *frontend*, que deja el último E2E en `backend\target`):
   ```powershell
   pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1
   ```
   Pide una clave ficticia (oculta) para las cuentas de demostración, prepara `barberturno_v2_test` con dos barberías ficticias y abre dos ventanas (escritorio y móvil 360 × 640). La revisión termina al cerrarlas; la base se vacía al salir. Si el jar no tiene el *frontend*: `cd frontend; npm run build; cd ..\backend; .\mvnw.cmd -Pcon-frontend "-Dexec.skip=true" -DskipTests package`.
2. **Recorridos:** `frontend/tools/revision/LEEME.md` §2 (cuentas, cambio de barbería y de papel, avisos y, nuevo, **§2.4 bandeja de WhatsApp**: aviso de derivación → conversación, responder dentro de la ventana, botón deshabilitado fuera de ella, «Devolver al asistente», el profesional y el cliente sin la entrada «WhatsApp»).
3. **Lo que no se ve en pantalla:** la IA (V2-12) está desactivada sin clave; el texto libre recibe los menús guiados de V2-10. Su comportamiento se comprueba con las pruebas (`SalidaClienteIT`, `TurnosIAIT`, `ConversacionGuionesIT`, `ModeloAnthropicTest`). Accesibilidad pendiente de mirar a mano: §2.3. **No hay conexión con Meta:** las respuestas de la bandeja quedan en la outbox (`PENDIENTE`).
4. **Código:** `git log --oneline --first-parent ddd3fce..tarea/V2-MVP-revision` lista las uniones; cada bloque tiene su rama `tarea/V2-11-*` / `tarea/V2-12-*` con autor y revisor en su evidencia.

## 4. Lo que requiere al responsable
- **P2-05 (Meta):** número, token y plantillas; hasta entonces todo va contra el servidor Graph falso.
- **P2-06:** decisiones D1…D7 (incluidas D5 anonimización y D6 entrantes mínimos) y los plazos; el aviso de privacidad debe mencionar al proveedor de IA.
- **P2-03:** clave, proveedor y modelo de IA; presupuesto de la evaluación en vivo (propuesta ≤ 10 USD).
- **V2-13:** revisión del conjunto de 120 conversaciones (BORRADOR), de su partición y de las 7 preguntas de `v2-13-conjunto-convenciones.md` (entre ellas qué significa «tarde» frente al horario real; la propuesta B no está adoptada).
- Revisión manual de esta rama y decisión de integrarla en `v2`.
