# AGENTS.md — Instrucciones comunes para los agentes de BarberTurno

Este archivo lo leen **Codex** (directamente) y **Claude Code** (lo importa `CLAUDE.md`). Es la referencia común sobre responsabilidades, autonomía y convenciones del proyecto.

## 1. Proyecto
BarberTurno es un sistema web de reservas y turnos para una barbería de una sede en Huamanga, Perú. Es el proyecto académico del curso *Integrador I: Sistemas Software* (UTP), responsable Sohnny Walter Lima Infanzón. La arquitectura es la alternativa **A1** del APF2: SPA **Angular 22** + API **Spring Boot 4.1 / Java 21** + **PostgreSQL 18**. Toda la lógica de negocio vive en Java.

## 2. Mapa de documentos (leer antes de trabajar)
| Documento | Contenido | Quién lo mantiene |
|---|---|---|
| `docs/apf2/` | Informe APF2, prototipo HTML, mockups draw.io, corrida manual, anexo Java. **Material original de referencia: SOLO LECTURA. No modificar, mover ni reformatear.** | Nadie (congelado) |
| `docs/requisitos.md` | RF, RNF, reglas de negocio (RN), restricciones académicas (RA), mejoras (MJ), contradicciones, supuestos, pendientes, trazabilidad y CP | Claude Code |
| `docs/arquitectura.md` | Stack y versiones, módulos, modelo de datos (DDL), contratos de API, seguridad, concurrencia, pruebas, decisiones (DA) | Claude Code |
| `docs/tareas.md` | Plan de tareas T-01…T-37, su estado y las notas de cierre | Claude Code (plan) · Codex (estado y notas) |
| `docs/diseno/` | Especificaciones de diseño visual aprobadas (T-48: `t-48.md` e imágenes de referencia) | Claude Code |
| `docs/pruebas/` | Evidencias: aceptación, cobertura, carga, recuperación y % Java | Codex |
| `README.md` | Arranque rápido | Codex |

Si dos documentos se contradicen, el orden de precedencia es: **requisitos.md > arquitectura.md > tareas.md > código**. La contradicción se anota en las notas de cierre de `tareas.md` para que el arquitecto la corrija.

## 3. Roles y responsabilidades
| Rol | Responsabilidades | No hace |
|---|---|---|
| **Responsable del proyecto (estudiante)** | Decide sobre el alcance académico, el negocio, el alojamiento, el remoto de Git y las entregas. Valida con el docente y el negocio. | — |
| **Claude Code — arquitecto, coordinador y responsable técnico** | Analiza, mejora la idea, mantiene los requisitos, la arquitectura, el modelo de datos y el plan; prepara tareas y decide qué agente interviene; revisa técnicamente y aprueba; resuelve bloqueos de diseño; mantiene la coherencia documental. Puede implementar cambios pequeños y localizados o de coordinación cuando delegar cueste más (autorizado por el responsable el 03/10/2026). | No es el implementador principal: las implementaciones normales o extensas van a Codex. |
| **Codex — implementación y pruebas** | Implementador principal: implementa las tareas de `docs/tareas.md` en orden, escribe y ejecuta las pruebas, mantiene el README y las evidencias, y actualiza el estado y las notas de cierre. | No cambia requisitos, contratos de API, el modelo de datos ni las reglas de negocio por su cuenta (ver §4). |
| **Gemini (Antigravity CLI) — revisión independiente** | Revisa en solo lectura los cambios de una tarea (diff-first) cuando el coordinador lo decide, sobre todo en tareas críticas (seguridad, permisos, persistencia, concurrencia, fechas, reservas); segunda opinión y análisis de riesgos; respaldo de implementación si Codex no tiene cuota y el coordinador lo asigna. | No modifica archivos como revisor ni corrige lo que revisa: sus hallazgos vuelven al implementador. Nunca trabaja a la vez que otro implementador sobre la misma tarea. |

**Un implementador activo por tarea.** El coordinador asigna quién implementa; los demás solo revisan. Detalle del flujo en `CLAUDE.md`, "Coordinación de agentes".

## 4. Autonomía
**Ambos agentes pueden decidir sin preguntar** las cuestiones habituales: nombres internos, estructura de clases privadas, refactorizaciones locales, mensajes de UI, estilos, pruebas adicionales, versiones de *parche* de las dependencias y pequeñas mejoras de experiencia de usuario coherentes con los mockups. Hay que registrarlas: Codex en las *Notas de cierre* de `tareas.md`; Claude en los registros MJ o DA.

**Claude Code** además puede, sin pedir confirmación: aplicar mejoras de funcionalidad, experiencia de usuario, reglas, seguridad o datos que respeten el APF2 y sean abarcables por una persona. Para ello registra la mejora (MJ-xx/DA-xx), actualiza los tres documentos y crea o ajusta las tareas.

**Codex debe parar y marcar la tarea como `Bloqueada (motivo)`** cuando:
- haya que cambiar un contrato de API (§6 de arquitectura), el DDL (§5), una regla RN o un criterio de aceptación;
- una versión de una dependencia indicada no funcione y la alternativa implique un cambio *menor* o *mayor* de versión;
- una prueba exigida no se pueda escribir o ejecutar en el entorno (p. ej., falta Node 24 o PostgreSQL);
- detecte un problema de seguridad o integridad en el diseño.

**Ningún agente, sin permiso explícito del responsable:** hace push a un remoto, publica, borra datos o historial de Git, cambia la tecnología base (RA-01), modifica `docs/apf2/`, ni introduce servicios de pago o externos.

## 5. Flujo de trabajo de una tarea (Codex)
> **Modo freeze desde el 05/10/2026** (decisión del responsable, tras completar T-01…T-47 y el primer CI verde): solo se admiten correcciones de bugs reproducibles, problemas de CI, requisitos pendientes o defectos necesarios para el cierre. Nada de funcionalidades nuevas ni refactorizaciones sin ese motivo.
> **Excepción autorizada el 05/10/2026:** T-48 (interfaz con Tailwind, MJ-19, DA-24), limitada a la capa visual del frontend; el freeze se mantiene para lógica, backend y funcionalidades. T-48 se revisa con el responsable antes de integrarse en `main`.

1. Elegir la primera tarea `Pendiente` cuyas dependencias estén en `Hecha`. Marcarla `En curso`.
2. Releer la tarea, las RF/RN/CP que cita y las secciones de la arquitectura a las que enlaza.
3. Crear la rama `tarea/T-XX-descripcion-corta` desde `main`.
4. Implementar en pasos pequeños; primero las pruebas de la regla cuando sea dominio puro.
5. Ejecutar la verificación completa (§7). No desactivar ni debilitar pruebas para que pasen.
6. Commit(s) con Conventional Commits en español, referenciando la tarea.
7. Actualizar `docs/tareas.md` (estado `Hecha`, fecha, hash del commit, notas de cierre) y el README si cambió el arranque.
8. Integrar en `main` localmente (`git merge --no-ff`) cuando se cumpla la Definición de Hecho. **No hacer push** sin autorización.
9. Informar: qué se hizo, pruebas ejecutadas y su resultado (con la salida si algo falló), desviaciones y la siguiente tarea.

## 6. Convenciones
### Idioma
- Documentación, comentarios, Javadoc, mensajes de commit, mensajes de UI y de error: **español**.
- Código: el dominio en español (`Reserva`, `Barbero`, `inicio`, `fin`), con los sufijos técnicos habituales en inglés (`ReservaService`, `ReservaRepository`, `ReservaController`, `Dto`). Paquetes según la arquitectura §4.1.
- La UI usa "usted" neutro o tuteo cordial, de forma consistente con el prototipo ("Reservar un turno", "Mis citas").

### Java (backend)
- Java 21, Spring Boot 4.1.1. Paquete raíz `pe.barberturno`. Módulos por funcionalidad, con estructura plana + subpaquete `dto`.
- Inyección por constructor; DTO como `record` con Bean Validation; **sin Lombok ni MapStruct**.
- `@Transactional` solo en los servicios. Las entidades cambian de estado mediante métodos de dominio, no con setters sueltos.
- Fechas: `Instant` en persistencia; conversión a Lima solo con `TiempoNegocio`; **nunca** `LocalDateTime.now()` ni `Instant.now()`: usar el `Clock` inyectado.
- Errores: lanzar `NegocioException(ErrorCodigo, mensaje)`; nunca devolver trazas al cliente.
- Javadoc **útil** en todas las clases, métodos y constructores públicos o protegidos (`@author`, `@version`, `@param`, `@return`, `@throws`): propósito, regla aplicada y significado de los parámetros, sin repetir el nombre del método. `verify` ejecuta `javadoc` con `doclint` y falla ante cualquier aviso (DA-20). HTML: `mvnw.cmd javadoc:javadoc` → `backend/target/reports/apidocs/index.html`.
- Nombres de pruebas: `*Test` (unitarias) y `*IT` (integración con PostgreSQL). Métodos con nombre descriptivo en español (`cancelar_conMenosDeDosHoras_rechaza`).

### TypeScript / Angular (frontend)
- Angular 22, componentes *standalone*, signals, `ChangeDetectionStrategy.OnPush`, rutas *lazy* por funcionalidad, Angular Material.
- Un servicio de API por recurso en `core/api`; interfaces de modelo espejo de los DTO en `core/modelos`.
- **Ninguna regla de negocio en el frontend:** los permisos de acción salen de `ReservaDto.permisos`; las validaciones de formulario son solo de experiencia de usuario.
- Fechas siempre con el pipe `fechaLima` o con `Intl.DateTimeFormat` y `timeZone: 'America/Lima'`.
- Accesibilidad: `label` en todos los campos, foco visible, botones con texto o `aria-label`.

### SQL
- Migraciones Flyway `V<n>__descripcion.sql`, nunca editar una migración ya integrada en `main`: crear una nueva.
- `snake_case`, nombres en español, restricciones con nombre explícito (`tabla_proposito`).

### API
- Seguir exactamente la arquitectura §6 (rutas, DTO, códigos de error). Instantes ISO-8601 con desfase; fechas `yyyy-MM-dd`.

### Git
- Rama principal `main`. Ramas `tarea/T-XX-...`. Commits pequeños con Conventional Commits:
  `feat(reservas): …`, `fix(auth): …`, `test(concurrencia): …`, `docs: …`, `chore(ci): …`, `refactor(scheduling): …`; con la referencia `[T-XX]` al final del asunto.
- Nunca subir `.env*`, secretos, `target/`, `node_modules/` ni `dist/`.
- No crear etiquetas (tags) ni ramas que no sean `tarea/T-XX-…` sin que se pidan. Tras el `merge --no-ff`, se permite un único commit `docs` en `main` para anotar el hash del merge en `tareas.md` y en la evidencia.
- Finales de línea LF (ver `.gitattributes`); UTF-8 en todo.

## 7. Comandos y entorno
| Acción | Comando |
|---|---|
| Preparar la BD local (una vez) | `psql -p 5433 -U postgres -f tools/db-local.sql` |
| Backend: pruebas completas | `cd backend && ./mvnw verify` (Windows: `mvnw.cmd verify`) |
| Backend: arrancar | `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (con demo: `dev,demo`) |
| Frontend: instalar / arrancar | `cd frontend && npm ci && npm start` (proxy `/api` → `:8080`) |
| Frontend: calidad | `npm run lint && npm test -- --watch=false && npm run build` |
| E2E | `cd frontend && npx playwright test` |
| % Java | `node tools/medir-java.mjs` |

Entorno de referencia (comprobado el 01/10/2026 en la revisión de T-01):
- Windows 11. JDK 21 (Temurin 21.0.8) en `C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot`. Hay otros JDK instalados (17, 22, 24): fijar `JAVA_HOME` a la 21 antes de usar `mvnw`.
- **PostgreSQL 18.6** en el puerto **5433** (servicio `postgresql-x64-18`; actualizado desde 18.0 el 04/10/2026, T-38). Hay un PostgreSQL 17 en el 5432 que **no** se usa, y el `psql` del PATH es el cliente 17: usar `C:\Program Files\PostgreSQL\18\bin\psql.exe`.- Git 2.45. `.git` pertenece a la cuenta `CodexSandboxOffline`; Git funciona porque `safe.directory` incluye solo esta ruta (configuración global del usuario).
- **Node (revisado en T-02):** el del sistema es 20.19.0 (nvm de Herd); nvm tiene además 24.0.2 y 22.22.0, y fnm 1.38.1 tiene 22.16.0. **Ninguno cumple** Angular 22.2 (`^22.22.3 || ^24.15.0 || >=26`). Para el frontend se usa **Node 24.21.0 con fnm** (DA-18): `fnm install 24.21.0` y, en cada sesión de PowerShell, `fnm env --use-on-cd --version-file-strategy recursive --shell powershell | Out-String | Invoke-Expression` seguido de `fnm use`. La estrategia `recursive` hace falta para que fnm encuentre el `.node-version` de la raíz desde `frontend/` (hallazgo de T-03). Scripts de instalación de npm: ver DA-19. No cambiar el Node global ni usar `nvm use`. Ejecutable: `C:\Users\limas\AppData\Local\Microsoft\WinGet\Packages\Schniz.fnm_Microsoft.Winget.Source_8wekyb3d8bbwe\fnm.exe`.
- Maven no está instalado: usar siempre el wrapper. Docker no está instalado: las pruebas usan el PostgreSQL local (DA-10).
- Generar proyectos (Spring Initializr) y descargar dependencias (Maven Central, npm) requiere red. Si el entorno aislado no la tiene, pedir la escalación o marcar la tarea como `Bloqueada`; nunca escribir de memoria las coordenadas de las dependencias.

Variables de entorno: ver arquitectura §9 (`BT_DB_URL`, `BT_DB_USER`, `BT_DB_PASSWORD`, `BT_JWT_SECRET`, `BT_ADMIN_*`, `BT_COOKIE_SECURE`). Los secretos locales están en `.local/barberturno.env` (DA-17) y la conexión administrativa usa `.local/pgpass.conf` mediante `PGPASSFILE`. **Nunca mostrar, copiar a la salida ni versionar su contenido**; para comprobar el archivo, listar solo los nombres de las claves.

## 8. Seguridad y datos
- La autorización se impone **siempre en el servidor** (rol + propiedad + barbero asignado). Una reserva ajena → 404.
- Nunca registrar en los logs contraseñas, JWT, cookies ni datos personales completos.
- Todos los datos de prueba y de demostración son ficticios (dominio `@ejemplo.test`). No usar datos de personas reales.
- Las escrituras que afectan la disponibilidad siguen el protocolo de bloqueos de la arquitectura §8 **sin excepciones**.

## 9. Registro de cambios
- Mejoras funcionales → `docs/requisitos.md` §5 (MJ-xx). Decisiones técnicas → `docs/arquitectura.md` §13 (DA-xx).
- Desviaciones de implementación → `docs/tareas.md`, *Notas de cierre*.
- Cada cambio relevante indica la fecha, el motivo y los documentos o tareas afectados, y deja los tres documentos coherentes entre sí.
