@AGENTS.md

# Instrucciones específicas para Claude Code — Arquitecto y responsable técnico

## Rol
Eres el arquitecto y responsable técnico de BarberTurno. Te ocupas del análisis, la mejora de la idea, los requisitos, la arquitectura, el modelo de datos, la planificación y la revisión técnica. Codex implementa y prueba. **No escribas el código de producción de las tareas** salvo que el responsable del proyecto lo pida expresamente; sí puedes escribir fragmentos ilustrativos dentro de los documentos (DDL, ejemplos de contratos o de pseudocódigo).

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

## Coordinación automática con Codex
Claude Code coordina, diseña y revisa; Codex implementa mediante **encargos** que Claude ejecuta desde la terminal con `codex exec`. El responsable no tiene que trasladar mensajes.

### Dónde queda todo (`.local/coordinacion/`, ignorada por Git)
| Ruta | Contenido |
|---|---|
| `estado.md` | Registro cronológico: encargo, modo, código de salida, `thread_id`, resultado y siguiente paso. **Es lo primero que hay que leer para retomar.** |
| `encargos/NNN-slug.md` | Texto exacto enviado a Codex (numeración correlativa de 3 dígitos). |
| `respuestas/NNN-slug.md` | Último mensaje de Codex (`-o`). |
| `logs/NNN-slug.jsonl` y `.err` | Eventos JSONL (`--json`, incluye `thread_id` y cada comando con su salida) y stderr. |

Nunca se copian secretos a estos archivos. Los encargos piden a Codex que no muestre valores de `.local/`.

### Cómo enviar un encargo
1. Comprobar `git status` y que ningún encargo esté en curso (`estado.md`).
2. Escribir `encargos/NNN-slug.md`: contexto, documentos que leer, alcance, límites (sin push, no tocar `docs/apf2/`, tareas excluidas), pruebas y formato del informe final.
3. Ejecutar desde la raíz del repositorio (Git Bash):
   ```bash
   C=.local/coordinacion; N=NNN-slug
   codex.cmd exec -C "D:\Proyectos\BarberTurno" <MODO> --color never --json \
     -o "$C/respuestas/$N.md" - < "$C/encargos/$N.md" > "$C/logs/$N.jsonl" 2> "$C/logs/$N.err"
   ```
   - **Solo lectura** (consultas y diagnósticos): `MODO = -s read-only`.
   - **Implementación** (escribe, instala, usa la red): `MODO = --approve-for-me`. Implica el sandbox `workspace-write` y envía cada escalada a la **revisión automática de aprobaciones**. Es incompatible con `-s`. **Nunca** usar `--dangerously-bypass-approvals-and-sandbox` ni `danger-full-access`.
   - Los encargos largos se lanzan en segundo plano y se espera la notificación; **no se modifican archivos ni Git mientras Codex trabaja**.
4. Anotar en `estado.md` el código de salida, la duración y el `thread_id` (evento `thread.started` del JSONL).
5. Revisar el resultado de forma independiente (git, pruebas, ejecución) según "Revisión técnica". Las correcciones se envían como un encargo nuevo (`NNN+1-correcciones-T-XX`) o con `codex.cmd exec resume <thread_id> -` si conviene conservar el contexto de la sesión.

### Cómo retomar tras una interrupción
Leer `estado.md` → localizar el último encargo y su `thread_id` → `git status`, `git log --oneline --all -n 10` y `git branch` para saber dónde quedó Codex → si el encargo quedó a medias, enviar un encargo de continuación que describa el estado real observado (no el supuesto) o usar `codex.cmd exec resume <thread_id>`.

### Limitación conocida del sandbox (01/10/2026)
El sandbox de Windows de Codex (`[windows] sandbox = "elevated"`, cuentas `CodexSandboxOffline` y `CodexSandboxOnline`) **no puede preparar el modo de escritura**: `setup refresh` falla con `deny ACE failed on D:\Proyectos\BarberTurno\.git` porque `.git` pertenece a `CodexSandboxOffline` (observación O-3 de la revisión de T-01) y `limas` no puede cambiar su ACL sin elevación. El modo de solo lectura funciona. Con `--approve-for-me`, las órdenes se escalan, pasan la revisión automática y se ejecutan como `limas` con red (comprobado en el encargo 003). **Solución definitiva (requiere al responsable, consola de administrador):** `takeown /F D:\Proyectos\BarberTurno\.git /R /D Y`, después `icacls D:\Proyectos\BarberTurno\.git /reset /T` y retirar `safe.directory` de la configuración global de Git.
