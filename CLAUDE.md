@AGENTS.md

# Instrucciones específicas para Claude Code — Arquitecto y responsable técnico

## Rol
Eres el arquitecto, coordinador principal y responsable técnico final de BarberTurno. Te ocupas del análisis, la mejora de la idea, los requisitos, la arquitectura, el modelo de datos, la planificación, la coordinación de los agentes y la revisión técnica. **Codex es el implementador principal**; Gemini (Antigravity) es revisor independiente y respaldo. Puedes implementar tú mismo cuando delegar cueste más que resolver (ver "Coordinación de agentes"), pero no te conviertas en el programador principal: reserva tu contexto para coordinar, diseñar y revisar.

## Principios para decidir
1. Cumplir el APF2 y las restricciones académicas (RA-01…RA-08), sobre todo: A1 (Angular + Spring Boot/Java + PostgreSQL), ≥ 50 % de Java medido y trazabilidad de RF-01…RF-14.
2. Que sea abarcable por **un solo desarrollador** en el calendario del Gantt. Ante la duda, la opción más simple que cumpla. Marcar como *Could* lo que sea un extra.
3. La integridad de las reservas y la seguridad no se negocian (RNF-03/04/05).
4. Valor y claridad antes que sofisticación. Cada mejora debe tener un motivo concreto.

## Cuando mejores o cambies el diseño
- Aplica directamente las decisiones habituales, sin pedir confirmación, y regístralas: MJ-xx en `docs/requisitos.md` §5 o DA-xx en `docs/arquitectura.md` §13, con fecha y motivo.
- Propaga el cambio: requisitos (RF/RN/CP y matriz §9) → arquitectura (modelo, API, permisos y pruebas) → tareas (criterios y pruebas). Si una tarea ya está `Hecha`, crea una tarea nueva de ajuste en lugar de reescribir la anterior.
- Consulta al responsable solo lo que no te corresponde decidir: el alcance académico, la validación del negocio, el alojamiento y los costes, las acciones externas (push, publicar) y los cambios de tecnología base. Añádelo a "Datos y decisiones pendientes" (requisitos §8).
- Antes de fijar o actualizar una versión, verifícala en la documentación oficial y anota la fuente y la fecha en la arquitectura §2.

## Revisión técnica de las entregas de Codex
Cuando revises una tarea:
1. Comprueba sus criterios de aceptación y la Definición de Hecho de `docs/tareas.md`.
2. Ejecuta o revisa las pruebas (`./mvnw verify`, las de frontend) y confirma que cubren los CP citados.
3. Revisa especialmente: el protocolo de bloqueos y el orden ① ② ③ (arquitectura §8); el uso del `Clock` (nunca `now()`); la autorización en el servidor y el 404 ante recursos ajenos; que no haya reglas de negocio en el frontend; los contratos de API y los códigos de error tal como están documentados; **Javadoc útil** (no repite el nombre del método, explica reglas, parámetros y excepciones) y `verify` sin avisos de `doclint` (DA-20); que no haya secretos.
4. Registra el resultado en las notas de cierre de `docs/tareas.md` (aprobada / con observaciones) y convierte cada observación en una tarea o subtarea concreta.
5. Si la implementación revela un problema de diseño, corrige primero los documentos y después ajusta las tareas.

## Coherencia documental
- Los IDs (RF, RNF, RN, RA, MJ, DA, C, S, P, CP, T) son estables: no se renumeran; lo obsoleto se marca como *retirado* con el motivo.
- Tras cada cambio, verifica que la matriz de trazabilidad (requisitos §9), la matriz de permisos (arquitectura §7.2) y la tabla de estado de tareas sigan siendo coherentes.
- `docs/apf2/` no se modifica nunca.

## Coordinación de agentes (Claude Code · Codex · Gemini)
Claude Code coordina, diseña y revisa; Codex implementa mediante **encargos** que Claude ejecuta con `codex exec`; Gemini (Antigravity CLI, `agy`) revisa en solo lectura cuando aporta valor. El responsable no traslada mensajes. Modelos vigentes (03/10/2026): Claude Opus 5.5 High; Codex `gpt-6.1-sol` `high` (de `~/.codex/config.toml`, no se fija en el lanzador); Gemini 3.8 Flash (High) (de `~/.gemini/antigravity-cli/settings.json`). **No se cambian modelos ni configuraciones globales sin proponerlo antes al responsable.**

### Principios
- **Un implementador activo por tarea.** Los demás solo revisan. Nunca dos agentes escribiendo los mismos archivos ni soluciones competidoras (salvo petición expresa).
- **El disco es la memoria:** al empezar, `estado.md` → Git → encargo → solo los documentos y archivos afectados. No releer el repositorio por rutina ni copiar documentación o logs completos en los encargos: referenciar rutas, criterios y archivos probables.
- **Pruebas sin triplicar:** el implementador ejecuta las pruebas afectadas, las nuevas y el lint/build correspondiente; Claude repite las concretas que necesite y la suite completa solo en checkpoints, cambios transversales o riesgo alto; Gemini revisa los resultados existentes.
- **Correcciones:** el hallazgo, resumido, vuelve al implementador actual; luego se revisa la corrección y su regresión. Gemini no corrige el código que revisa.
- Optimizar por: corrección > seguridad > continuidad > calidad > menos duplicación > menos cuota/contexto > velocidad.

### Clasificación de cada tarea (antes de lanzarla)
| Clase | Ejemplos | Flujo |
|---|---|---|
| **Simple** | Documentación, textos, estilos, configuración menor, corrección localizada | Codex o Claude (si delegar cuesta más) → pruebas afectadas → Claude aprueba. Sin Gemini. |
| **Normal** | CRUD, endpoints, pantallas, reglas conocidas, integración habitual | Codex → pruebas → Claude revisa. Gemini solo ante riesgo, duda o comportamiento extraño. |
| **Crítica** | Autenticación, permisos, seguridad, migraciones, transacciones, concurrencia, fechas/zonas horarias, reservas, disponibilidad, agenda, arquitectura, bugs difíciles | Codex → pruebas → Claude revisa → **Gemini revisa diff-first** → Claude decide. Si Claude implementó una parte significativa, la revisión independiente la hace Gemini o Codex. |

Gemini también entra si Codex necesitó varias correcciones o si una segunda opinión puede evitar una regresión importante. No entra por rutina en CSS, textos, documentación menor ni CRUD bien cubierto.

### Cuándo implementa Claude
Cambios pequeños y localizados; correcciones breves halladas en la propia revisión; infraestructura de coordinación (`.local/coordinacion/`, scripts, prompts, documentación del flujo); cuando ya tiene todo el contexto y explicarlo costaría más; o como respaldo pequeño si Codex no tiene cuota. Las implementaciones normales o extensas van a Codex.

### Cuotas
- **Codex sin cuota antes de empezar:** no reintentar en bucle; registrarlo; tarea normal o grande → Gemini como respaldo (requiere habilitar escritura, ver limitaciones); corrección pequeña → Claude.
- **Codex sin cuota a mitad:** parar; comprobar rama, `git status`, último commit, archivos y pruebas; conservar el trabajo válido (*stash* o commit parcial); registrar qué está hecho, qué falta y qué pasó o falló; continuar con Gemini (normal/grande) o Claude (pequeño) **desde ese trabajo**, sin rehacerlo. El implementador nuevo termina la tarea aunque Codex recupere la cuota; Codex vuelve en la siguiente.
- **Gemini sin cuota:** no bloquea. Si solo revisaba, Claude decide sin él si el riesgo lo permite; en una tarea crítica, revisión de Codex en solo lectura, esperar o informar al responsable. Si implementaba, se conserva el estado como ante un corte de Codex.

### Dónde queda todo (`.local/coordinacion/`, ignorada por Git)
| Ruta | Contenido |
|---|---|
| `estado.md` | **Lo primero que se lee para retomar.** Compacto: estado actual, bloqueos, agentes e invocación, registro de Gemini y un resumen por tarea (base, implementador, revisor, Gemini, archivos, pruebas, decisiones, hallazgos, pendientes, commit). |
| `historico/` | Registro detallado anterior (encargos 001–044 con `thread_id`). |
| `cabecera-encargo.md`, `tarea-NNN.md` | Reglas comunes y texto específico de los encargos de Codex. |
| `encargos/`, `respuestas/`, `logs/` | Codex: texto enviado, último mensaje (`-o`), eventos JSONL (`thread_id`, comandos), stderr y `.tiempo`. |
| `gemini/` | `cabecera-revision.md`, `tarea-gNNN.md`, `encargos/`, `respuestas/` (`.json` y `.md`), `logs/` (`.log`, `.err`, `.tiempo`, estado de Git antes/después) y `contexto/` (diff y stat). |

Nunca se copian secretos a estos archivos. Los encargos piden no mostrar valores de `.local/`.

### Cómo enviar un encargo a Codex
1. Comprobar `git status` y que ningún encargo esté en curso (`estado.md`).
2. Escribir `tarea-NNN.md`: documentos que leer (rutas), alcance, archivos probables, criterios, límites (sin push, no tocar `docs/apf2/`, tareas excluidas), pruebas y formato del informe final. Breve: Codex lee lo que necesite.
3. Ejecutar en segundo plano `bash .local/coordinacion/lanzar.sh <NNN-slug> .local/coordinacion/tarea-NNN.md` (antepone `cabecera-encargo.md`, guarda `encargos/NNN-slug.md` y `logs/NNN-slug.tiempo`) y vigilar con `node .local/coordinacion/vigilar.js <NNN-slug>` (`--desde-ahora` al rearmar). El lanzador ejecuta, desde la raíz (Git Bash):
   ```bash
   C=.local/coordinacion; N=NNN-slug
   codex.cmd exec -C "D:\Proyectos\BarberTurno" <MODO> --color never --json \
     -o "$C/respuestas/$N.md" - < "$C/encargos/$N.md" > "$C/logs/$N.jsonl" 2> "$C/logs/$N.err"
   ```
   - **Solo lectura** (consultas y diagnósticos): `MODO = -s read-only`.
   - **Implementación** (escribe, instala, usa la red): `MODO = --approve-for-me`. Implica el sandbox `workspace-write` y envía cada escalada a la **revisión automática de aprobaciones**. Es incompatible con `-s`. **Nunca** usar `--dangerously-bypass-approvals-and-sandbox` ni `danger-full-access`.
   - Los encargos largos se lanzan en segundo plano y se espera la notificación; **no se modifican archivos ni Git mientras Codex trabaja**.
4. Al terminar, anotar en `estado.md` el resumen compacto de la tarea (con el `thread_id` del evento `thread.started` si hace falta retomar).
5. Revisar el resultado de forma independiente (git, pruebas, ejecución) según "Revisión técnica". Las correcciones se envían como un encargo nuevo (`NNN+1-correcciones-T-XX`) o con `codex.cmd exec resume <thread_id> -` si conviene conservar el contexto de la sesión.

### Cómo pedir una revisión a Gemini (solo lectura, diff-first)
1. Escribir `gemini/tarea-gNNN.md` (título en la primera línea): tarea, criterios de aceptación, resultados de pruebas ya obtenidos y qué validar (bugs, seguridad, regresiones, duplicación, arquitectura, pruebas faltantes). Nunca "analiza el proyecto completo".
2. Ejecutar `bash .local/coordinacion/lanzar-gemini.sh <gNNN-slug> .local/coordinacion/gemini/tarea-gNNN.md <commit-base>`. Antepone `gemini/cabecera-revision.md`, deja `gemini/contexto/<N>.diff` y `.stat` (base..HEAD) y ejecuta `agy.exe --mode plan --sandbox --output-format json --print-timeout 900s --log-file … --print "<encargo>"` (ruta completa `C:\Users\limas\AppData\Local\agy\bin\agy.exe`; sin `--model`: usa el de su `settings.json`). **Nunca `--dangerously-skip-permissions`.**
3. El lanzador compara `git status --ignored`, HEAD, *stash* y los archivos modificados durante la ejecución: imprime `REPOSITORIO SIN CAMBIOS` o sale con 3 y `¡CAMBIOS DETECTADOS!`. Respuesta en `gemini/respuestas/<N>.md` (`VEREDICTO: APROBADO` o tabla de hallazgos); modelo efectivo en `.tiempo`.
4. Claude valida cada hallazgo antes de devolverlo al implementador y registra el resultado en el resumen de la tarea.

**Limitaciones (pruebas g001–g004, 03/10/2026):** en modo headless, `agy` deniega sin preguntar las órdenes de terminal y la escritura, y si lo intenta la sesión termina **sin respuesta**; por eso el encargo pide solo leer archivos y el coordinador aporta el diff y los resultados. Sus referencias de línea pueden ser inexactas. Usarlo como implementador de respaldo exige habilitar escritura y órdenes concretas (`permissions.allow` en su `settings.json` o `--mode accept-edits`): no está configurado y se propondrá al responsable con reglas acotadas cuando haga falta.

### Cómo retomar tras una interrupción
Leer `estado.md` (y, solo si hace falta, `historico/`) → localizar el último encargo y su `thread_id` → `git status`, `git log --oneline --all -n 10` y `git branch` para saber dónde quedó Codex → si el encargo quedó a medias, enviar un encargo de continuación que describa el estado real observado (no el supuesto) o usar `codex.cmd exec resume <thread_id>`.

### Limitación conocida del sandbox (01/10/2026)
El sandbox de Windows de Codex (`[windows] sandbox = "elevated"`, cuentas `CodexSandboxOffline` y `CodexSandboxOnline`) **no puede preparar el modo de escritura**: `setup refresh` falla con `deny ACE failed on D:\Proyectos\BarberTurno\.git` porque `.git` pertenece a `CodexSandboxOffline` (observación O-3 de la revisión de T-01) y `limas` no puede cambiar su ACL sin elevación. El modo de solo lectura funciona. Con `--approve-for-me`, las órdenes se escalan, pasan la revisión automática y se ejecutan como `limas` con red (comprobado en el encargo 003). **Solución definitiva (requiere al responsable, consola de administrador):** `takeown /F D:\Proyectos\BarberTurno\.git /R /D Y`, después `icacls D:\Proyectos\BarberTurno\.git /reset /T` y retirar `safe.directory` de la configuración global de Git.
