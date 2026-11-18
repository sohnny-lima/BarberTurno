# Validación conjunta local de V2-07 a V2-10 y corrección de H-116

> **Estado (04/11/2026): validación conjunta completada en local. `verify` completo **2954/0/0/0** (JaCoCo y Javadoc) sobre `e44a4ff`; E2E 84/84 y capturas 8/8 sobre `f45a18d` (después solo cambian una prueba de *backend* y la documentación); entorno de revisión P1–P7 correctas.**
> - Rama de revisión `tarea/V2-07-10-validacion-conjunta`, commit para la revisión del responsable: **`el commit que contiene este documento (ver `estado.md` y el informe)`**.
> - **No integra en `v2` ni publica nada, y no supone la aprobación funcional de V2-07**, cuya comprobación manual por el responsable sigue pendiente.
> - Todo es **validación local**: sin conexión con Meta (P2-05), sin mensajes reales y con datos ficticios.
>
> Responsables por archivo y resoluciones: [`14-validacion-conjunta-v2-07-a-v2-10.md`](../14-validacion-conjunta-v2-07-a-v2-10.md). Decisiones pendientes: [`15-decisiones-pendientes-p2-05-p2-06.md`](../15-decisiones-pendientes-p2-05-p2-06.md).

## 1. Corrección de H-116-01/02 (V2-DA-70)
| Paso | Commit | Autor | Revisión |
|---|---|---|---|
| Contrato V2-DA-70 | `9aca981` | Claude | Codex (122) y Gemini (g080) lo leyeron; sin cambios |
| Implementación y pruebas (`PresupuestoEnvioWhatsAppIT`, 14 casos que observan las solicitudes que recibe `ServidorGraphFalso`) | `60e3468` | **Codex** (122) | **Gemini g080 (Argon High): APROBADO**; Claude: conforme |
| Especificación y evidencia de V2-09 | `2c5178e` | Codex | Claude |
| Propagación a la línea de V2-10 (unión, sin copiar) | `676fd31` | Claude | Codex 123: los cinco archivos coinciden byte a byte con la rama del presupuesto |
| Nota del *backlog* | `226465e` | Claude | — |

- **Ejecuciones:**
  - Codex, sobre su rama: batería focal **183/0/0** y Javadoc sin avisos;
  - Claude, **mutación**: con el enviador anterior (`d4bc401`) fallan **12 de las 14** pruebas nuevas (`mutacion-h116.txt`). Las 2 que pasan son regresiones de casos que ya funcionaban.
- **Solicitudes posibles por mensaje** (tabla de Codex): si el primer desconocido es la reserva *k*, como máximo **una** solicitud posterior y min(5, *k* + 1) en total; con aceptación a la primera, 1. La política **no garantiza la entrega ni la ausencia de duplicados**, y una caída antes del HTTP puede consumir el intento y el presupuesto.

## 2. Unión y resoluciones
| Commit | Qué | Autor |
|---|---|---|
| `58c52ea` | V2-07 (`3ded886`) + línea de V2-10 (`59f50cf`); conflictos solo en documentos | Claude |
| `4df3158` | `ReservaDto.cliente` `{ id: number\|null; fichaId }` (V2-DA-69) y documentos 14 y 15 | Claude |
| `a669f12` | Campana para todo miembro del negocio (incluido `ADMIN`) y `vaciar()` de los E2E con las tablas de V2-08/09/10 | Gemini (g078, Argon) |
| `27d5ba6`, `4358ab8`, `f4c4c7c` | Entorno de revisión versionado: *Job Object*, sesión, recuperación (y sus correcciones) | Gemini (g079, g082 Argon; g084 Flash) |
| `bbad1cd` | La corrección de H-116 entra **una sola vez**, por la línea de V2-10 | Claude |
| `bfe8309` | Rutas canónicas en dos pruebas de V2-08/V2-10 tras retirar el alias de V2-07 | Claude |
| `eaa287f` | E2E 08 (campana del personal) e IT `AvisosWhatsAppPersonalIT` | Gemini (g081, Argon) |
| `6002d0e` | `shell.scss` dentro del presupuesto de estilos | Gemini (g083, Flash) |
| `acc5594` | Formato de Prettier en tres archivos del entorno | Claude (`prettier --write`) |
| `f45a18d` | Agenda: la línea «Ahora» de un día sin citas no deja una fila fuera de una tabla (axe) | Claude |
| `a22b40b` | `ExencionCsrfWebhookTest` (H-124-01) | Claude |
| `e44a4ff` | Capturas, E2E y P1–P7 en la evidencia | Claude |

## 3. Ejecuciones sobre la combinación (cada una con su commit)
| Ejecución | Commit | Ejecutor | Resultado |
|---|---|---|---|
| `-DskipTests verify` (compilación y Javadoc) | `58c52ea` | Claude | rc=0 |
| Frontend: lint, formato, unitarias y *build* | `4df3158` | Claude | lint y formato correctos; 559/560 (`app.routes.spec` agotó una espera con la máquina cargada; sola, 16/16); *build* 499,36 kB |
| Frontend: lint, formato y unitarias | `c6f8855` | Claude | **577/577** |
| **`verify` completo** | **`bbad1cd`** | Claude (cerrojo) | **2939: 2937 correctas, 2 fallos**, ambos por rutas sin `/api/n/{slug}` en pruebas (alias retirado en V2-07) → `bfe8309` (`verify-conjunto-bbad1cd.txt`) |
| Focal: `ConversacionOperacionesIT`, `WebhookWhatsAppIT` y `AvisosWhatsAppPersonalIT` | `878eb3d` | Claude (cerrojo) | **29/29** (`focal-878eb3d.txt`) |
| Frontend: lint, formato, unitarias y *build* | `acc5594` | Claude | **577/577**; *build* 499,36 kB sin avisos de presupuesto |
| E2E completos (4 proyectos) | `acc5594` | Claude (cerrojo) | **84/84** (13,8 min) |
| Capturas y axe de V2-07 | `acc5594` | Claude (cerrojo) | **8/8** |
| Entorno de revisión, P1 (`-Verificar`) | `acc5594` → `f4c4c7c` | Claude | 1.º: fallo de codificación del SQL de avisos (g084); 2.º: axe crítico en la agenda (`f45a18d`) |
| Frontend: lint, formato y unitarias | `f45a18d` | Claude | **578/578** |
| E2E completos y capturas | **`f45a18d`** | Claude (cerrojo) | **84/84** (15,4 min) y **8/8** (`e2e-f45a18d.txt`) |
| `ExencionCsrfWebhookTest` | `a22b40b` | Claude | **12/12**; con el criterio ampliado a `startsWith`, 3 fallos (`mutacion-csrf.txt`) |
| Entorno de revisión P1–P7 | `a22b40b` (las herramientas no cambian después) | Claude | **todas correctas** (`revision-P1-P7.txt`, §4) |
| **`verify` completo final** | **`e44a4ff`** | Claude (cerrojo) | ****2954/0/0/0**, JaCoCo («All coverage checks have been met») y Javadoc sin avisos, BUILD SUCCESS, 39:17 min (`verify-conjunto-e44a4ff.txt`)** |

## 4. Entorno de revisión: cierre y recuperación (P1–P7)
- **Cierre normal:** al cerrar las ventanas, o al terminar `-Verificar`, el `finally` detiene el *backend*, vacía la base y borra la sesión y el cerrojo.
- **Cierre abrupto de la consola:** el `finally` **no** se ejecuta. Por eso:
  1. la consola entra en un *Job Object* con `KILL_ON_JOB_CLOSE` **antes** de crear ningún proceso, de modo que el sistema termina el *backend*, las ventanas y las sondas al morir la consola;
  2. la sesión (con identificador único, procesos con hora de creación, ejecutable y línea de órdenes) y el cerrojo quedan como marca;
  3. el siguiente arranque, o `-Recuperar`, vacía la base y retira las marcas.
- **No se promete:** que el `finally` se ejecute; que el *Job Object* limpie la base; ni nada si se mata a la vez el proceso que hace la recuperación.

| Prueba | Resultado |
|---|---|
| P1 · `-Verificar` | rc=0 en 154 s. Las cinco cuentas entran con sus papeles y avisos sin leer; axe sin violaciones; sin sesión ni cerrojo; puerto 8081 libre; base vaciada |
| P2 · `-Mantener` y `Stop-Process -Force` de la consola | El *backend* deja de existir a los **0,3 s**; quedan la sesión y el cerrojo |
| P3 · `-Recuperar` | rc=0: «backend ya no existía; base vaciada; cerrojo borrado; sesión borrada». Comprobación independiente: 1 negocio y 0 usuarios |
| P4 · `-Verificar` de nuevo (reapertura) | rc=0; sin restos |
| P5 · cerrojo de otro proceso vivo | `-Recuperar` y `-Verificar` abortan con un mensaje claro; cerrojo intacto; sin *backend* |
| P6 · dos `-Recuperar` a la vez | Una recupera; la otra sale «sin tocar nada» |
| P7 · vaciado rechazado (conexión abierta) | rc=1 con `limpiezaPendiente=true`; se conservan sesión y cerrojo; tras cerrar la conexión, `-Recuperar` completa (rc=0) |

## 5. Defectos de producción y su regresión
Cada regresión **falla con el código anterior a la corrección**. Es una mutación real, ejecutada por Claude; los resúmenes están en `mutacion-*.txt`.

| Defecto | Hallado por | Corrección | Regresión que lo detecta | Fallo sin la corrección |
|---|---|---|---|---|
| 119-1 · mostrar el menú no anulaba la operación vigente | Codex 119 | `c34d9e3` | `OrquestadorConversacionTest.textoNoReconocidoTrasUnaPropuesta…`; escenario 5 | sí (`1ff8fc4`) |
| 119-2 · [Aceptar] creaba la ficha sin un aviso presentado | Codex 119 | `c34d9e3` | `aceptarSinAvisoPresentado…`, `aceptarConFranjaValidaPeroSinElPaso…`; escenario 7 | sí |
| 119-3 · inicio no representable → reintentos hasta `ERROR` | Codex 119 | `c34d9e3`, `6620005` | `franjaConInicioNoRepresentable…`, `franjaCambio…`, `franjaConUnSegundoMas…`, `CodigoSeleccionTest.segundosEpochLimite…` | sí |
| 119-5 · el recorte partía emojis | Codex 119 | `6620005` | `MenusConversacionTest.recortar_caracteresSuplementarios…` | sí |
| 119-6 · «Ver más» hacia la página 10 | Codex 119 | `6620005` | `paginacion90_91_200…`, `profesionales_con89Y90…`, `todasLasPaginas…` | sí (`sp:10`) |
| 119-7 · *nonce* no canónico aceptado | Codex 119 | `6620005` | `CodigoSeleccionTest.nonceCanonicoYNoCanonico…` | sí (X ya lo rechazaba) |
| 120-1 · la repetición no anulaba otra vigente | Codex 120 | `dd2299f` | `repetirUnaEjecutadaConOtraVigente…` | sí (`c34d9e3`) |
| 120-2 · una pulsación inválida anulaba la vigente propia | Codex 120 | `dd2299f` | `confirmacionConNonceIncorrecto…`, `confirmacionSinFicha…`; escenario 7 | sí |
| H-116-01/02 · límite de desconocidos sin cubrir cierres perdidos ni el 429 posterior | Codex 116 | `60e3468` | `PresupuestoEnvioWhatsAppIT` (12 de 14 fallan) | sí (`d4bc401`) |
| Agenda (V2-07) · «Ahora» como `role="row"` fuera de una tabla (axe crítico) | entorno de revisión (sonda axe con reloj y datos reales) | `f45a18d` | `agenda.spec` «sin citas hoy en escritorio…» | sí (29/30) |

No son defectos de producción, pero también quedan cubiertos:
- **H-124-01:** cobertura de la exención de CSRF → `ExencionCsrfWebhookTest` (falla si se amplía a `startsWith`);
- **pruebas con rutas antiguas** (`bfe8309`);
- **entorno de revisión:** H-123-01…05 y la codificación del SQL, con P1–P7.

## 6. Revisiones cruzadas (nadie aprueba lo suyo)
| Revisión | Revisor (solo lectura) | Sobre | Resultado |
|---|---|---|---|
| g080 | Gemini Argon High | Corrección de H-116 (Codex) | APROBADO (estado de la sesión «ERROR», pero respuesta completa) |
| 123 | Codex | Unión, tipos (Claude); avisos, vaciado y entorno (Gemini) | Unión, tipos, avisos y vaciado conformes; entorno: H-123-01 (alta) y 02…05 (media) → g082 |
| 124 | Codex | Rutas de pruebas y agenda (Claude); `shell.scss` y codificación (Gemini); correcciones de H-123 | Agenda y SQL correctos; H-124-01 (media) → `a22b40b`. Los mecanismos de H-123-01…05 están en el código (líneas citadas); el encargo le impedía leer los identificadores en la coordinación, y Claude los relaciona: identidad (365–419), consola en el trabajo (604–614), sondas dentro del trabajo, conservación ante un vaciado fallido (508–520) y serialización con `idSesion` (439–477) |
| Claude | — | Corrección de H-116 (Codex) y cada ronda de Gemini | Conforme, con las rondas descritas |

## 7. Límites y pendientes
- **Sin integrar en `v2`, sin *push* ni despliegue.** `main`, Azure, las bases de v1 y los entregables académicos, intactos. Ramas originales intactas.
- **P2-05** (Meta) y **P2-06** (privacidad y plazos): pendientes; preparados en el documento 15.
- **V2-07:** sigue pendiente su comprobación funcional por el responsable.
- La capa del *frontend* no prueba la conversación de WhatsApp (no hay pantalla): sus flujos se validan con las IT y los escenarios de extremo a extremo del *backend*.

## 8. Entorno conjunto para la revisión manual (preparado, **sin abrir**)
- **Rama y worktree:** `tarea/V2-07-10-validacion-conjunta` en `D:\Proyectos\BarberTurno-v2-conjunta`; el commit es el que contiene esta sección.
- **Jar con el *frontend*** (reconstruido tras el `verify`, que deja un jar sin él): `backend\target\barberturno-0.0.1-SNAPSHOT.jar`.
- **Abrir** (pide de forma oculta la clave de demostración, abre una ventana de escritorio maximizada y otra de móvil a 360×640, y toma el cerrojo de `barberturno_v2_test` mientras dura):
  ```powershell
  Set-Location D:\Proyectos\BarberTurno-v2-conjunta
  pwsh -NoProfile -File frontend\tools\revision-conjunta.ps1
  ```
- **Cerrar:** cierra las ventanas; el script detiene el *backend*, vacía la base y libera el cerrojo. Si cierras la consola de golpe, el sistema termina el *backend* y las ventanas, y la limpieza se completa al abrir de nuevo o con `-Recuperar`.
- **Recorridos sugeridos:**
  - cambio de barbería y de papel con `multirol@ejemplo.test`, `encargado@ejemplo.test` y `personal-cliente@ejemplo.test`;
  - avisos (contador, lista y lectura) del propietario, el encargado, el profesional y el cliente;
  - agenda con la línea «Ahora».
- **La conversación de WhatsApp no tiene pantalla:** se valida con las pruebas del *backend* y no se abre con este entorno.
