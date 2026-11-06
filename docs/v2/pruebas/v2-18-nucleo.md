# V2-18 · Bloque N · Encargo 144

Estado: **Bloqueada (verificación final no ejecutable: timeouts de workers y del ejecutor local)**, 05/11/2026. Rama exclusiva `tarea/V2-PWA-nucleo`, sin integración ni push. No se considera aceptación final de CP-1…CP-5.

Implementación: worker oficial Angular 22.2.1, recursos públicos exclusivamente, red sin cola de escrituras, actualización aceptada por el usuario, historial central de superposiciones, detección de teclado y limpieza entre identidades/barberías. Sin cambios de backend, contrato, permisos ni estilos del bloque M.

## Hallazgos del contrato

- Angular genera siempre `dataGroups: []` en `ngsw.json`, aun omitiendo la propiedad en `ngsw-config.json`. No se altera el generador oficial ni su salida: la configuración no declara grupos y la comprobación del generado exige cero grupos. El arquitecto debe precisar la redacción literal «no tiene dataGroups» como «no tiene grupos de datos». Esta diferencia no permite cachear la API.
- CP-3 usa dos variantes del build de producción: el servidor exclusivo de pruebas deriva la segunda en memoria cambiando `index.html`, su SHA-1 en `ngsw.json` y `appData`; no modifica archivos de producción ni sustituye el worker. La prueba verifica instalación, oferta, rechazo con formulario y aceptación efectiva.

## Verificación en curso

Node 24.21.0 por fnm, npm 11.19.0, Angular core/service-worker 22.2.1, builder 22.2.0, Vitest 5.0.3 y Playwright 1.63.0. No se ejecutó `npm install`, `verify` ni la suite E2E general. El arnés autorizado ejecuta internamente `package` con pruebas Java omitidas. Las pruebas web no acreditan emulador, teléfono, TWA ni TalkBack; instalación significa aquí worker instalado y controlando la página. Manifest e instalación standalone corresponden a M.

- Primer build: correcto, inicial **507,76 kB**, aviso de presupuesto de 500 kB; pendiente comparar sin registro SW para aplicar V2-DA-96.
- CP-3 sin base: `BT_PWA_SIN_BD=1 npx playwright test --config=playwright.pwa.config.ts --grep CP-3`: **2 passed (3.2m)**, Chromium 360/1440.
- Primera suite unitaria abortó por `Worker exited unexpectedly with signal SIGTERM during stopping state while running test file .../app.spec.ts`; también detectó una expectativa antigua sin `replaceUrl`, corregida conservando el destino y el returnUrl. Repetición con `VITEST_MAX_WORKERS=2` y `NG_BUILD_MAX_WORKERS=2`.
- Git requirió escalada por propiedad; fnm por acceso al directorio de enlaces. Sin cambios de ACL, propietario o configuración. Los primeros procesos Angular dentro del sandbox no progresaron y se cerraron por PID antes de repetir fuera.

## Seguimiento de la implementación y comprobaciones

| Módulo / archivos | Resultado implementado |
| --- | --- |
| `ngsw-config.json`, `angular.json`, `app.config.ts` y spec | Worker oficial de producción, registro al estabilizar o 30 s, assets públicos, exclusiones de API/actuator/well-known/OpenAPI/Swagger y extensiones. Sin cambios de XSRF. |
| `core/pwa/red`, `error-red`, `avisos-pwa` | Estado de red inicial/eventos, aviso fijo anunciado, recuperación de cinco segundos y mensaje de escritura sin cola ni reintento. Estilos propios menores de 4 kB. |
| `core/pwa/actualizaciones` | VERSION_READY e irrecuperable, aceptación, confirmación de dirty/dialog, activación/recarga y visibilidad con intervalo mínimo de 30 min. |
| `core/pwa/historial-superposiciones`, shell | MatDialog del flujo del shell, cajón móvil y MatMenu del contenedor CDK; entradas marcadas, captura de popstate, anidados y cierre por otros medios. Entradas bajo otra navegación se omiten al volver/avanzar. |
| `core/pwa/teclado`, shell | visualViewport, foco visible también dentro de diálogos, barra inferior oculta; conserva altura entre campos y distingue zoom/orientación. |
| `core/auth`, `core/negocio`, `core/notificaciones`, `reserva.store.ts` | Limpieza de identidad/negocio/avisos/reserva y todas las claves propias de ambos almacenes; nonce contra respuestas anteriores; cancelación de consultas. Logout/401 esperan a retirar el historial antes de reemplazar la URL. |
| shell | Limpia también snackbar, título y callback de Agenda de la cuenta anterior. |
| `playwright.pwa.config.ts`, `e2e/pwa/` | Producción, Chromium 360×800 y 1440×900, servidor local sin nuevas dependencias, proxy 8081, dos versiones reales de índice/manifiesto y casos CP-1…CP-5. Incluye el patrón solicitado para `movil.spec.ts`, reservado a M. |

Auditoría: los 14 clientes API raíz no almacenan respuestas; no hay `shareReplay`. Los estados de cuenta son sesión, negocio y avisos, más el store de reserva solicitado. `RutasNegocio` no conserva payload de cuenta. Fuera de core, `EntradaNegocio` conserva servicios y sus diálogos se cierran; `TituloPagina` conserva título/callback y se limpia desde el shell sin modificar su archivo. Los servicios PWA conservan estado público. Solo había dos claves propias, pero se limpia todo el prefijo `barberturno.*`, conservando otras aplicaciones.

La selección pública se conserva al ir a ingresar; cerrar/invalidar una identidad la borra. El 401 anónimo de consulta pública de contexto mantiene la semántica de V2-07; el de sesión activa limpia. Un 401 de una generación anterior no cierra la cuenta nueva.

### Ejecuciones

Los primeros ensayos fueron diagnósticos sobre el árbol de implementación de `29f3ed90bbd685af405207eb204672d9dfbd2f1e`, consolidado después en los commits citados. No se atribuyen árboles sin commit a una revisión final limpia.

| Verificación | Revisión / fuente | Resultado |
| --- | --- | --- |
| `npm run lint` y `npm run format:check` | `4969079` | Correctos, incluidos tipos de E2E; hay cambios de limpieza posteriores que requieren repetición final. |
| `npm test -- --watch=false` / dos forks | Árbol inicial, consolidado en `76c3391` / `948c183` | Expectativa antigua de replaceUrl corregida; otro intento ejecutó 499 casos correctos pero tuvo 4 errores de arranque de workers. No aprobado. |
| Un worker threads | Árbol consolidado en `e1d8e32` | Cero casos, timeout de arranque. Se descartó esa configuración. |
| PWA aislada con un fork | Árbol consolidado en `e1d8e32` | 15 pruebas correctas, un archivo. |
| Suite completa, un fork | `65adc38` | **54 archivos, 628 pruebas correctas**, 63,30 s. La corrección de la barrera de salida es posterior. |
| Reintento concurrente final | Base `f51d3da`, correcciones `4969079` | Cero casos: timeout del worker. No aprobado. |
| Reintento aislado por PowerShell | Base `f599c73` y cierres posteriores | Cancelado tras más de diez minutos en compilación sin llegar a casos. |
| E2E con cerrojo, `tmp/144-pwa-nucleo.log` en frontend | Build anterior a cierres `76c3391` / `948c183` | **2 passed, 8 failed (17.4m)**: Salir tenía rol menuitem y los siguientes arranques excedieron 90 s. |
| E2E con cerrojo y `--max-failures=1`, `tmp/144-pwa-nucleo-final.log` | Build 506,83 kB anterior a `3b8991e` | **1 passed, 1 failed, 8 did not run (3.2m)**: competencia entre logout y retirada del historial. |

Las versiones de CP-3 usan el worker real: el servidor cambia en memoria index.html, su SHA-1 en ngsw.json y appData. Rechazar conserva versión 1 y el formulario modificado; aceptar carga versión 2. No se inyecta estado de Angular ni se modifica dist.

### Salidas relevantes de los fallos

```text
Error: Worker exited unexpectedly with signal SIGTERM during stopping state
Caused by: Error: [vitest-pool-runner]: Timeout waiting for worker to respond
Test Files 50 passed (50)
Tests 499 passed (499)
Errors 4 errors
```

El mismo timeout apareció con threads y con un fork. La configuración auxiliar conserva aislamiento y todas las aserciones; no se considera verde un arranque que no ejecuta los casos.

```text
TS2345: ... url: string | null ... is not assignable ... url?: string
Type 'null' is not assignable to type 'string | undefined'.
```

`65adc38` conserva `url: error.url ?? undefined`; la suite posterior pasó 628 casos.

```text
locator.click: Test timeout of 120000ms exceeded.
waiting for getByRole('button', { name: 'Salir', exact: true }).visible()
Error: El jar E2E no estuvo listo en 90 s.
```

El selector acepta button/menuitem. El arnés PWA conserva guardas y limpieza del existente, amplía solo preparación a 180 s y limita cada fetch a cinco segundos. Si falla informa estados y cantidades, sin datos.

```text
Expected pattern: /\/ingresar/
Received string: "http://localhost:4301/n/barberturno/mis-citas"
Timeout: 20000ms
1 failed, 8 did not run, 1 passed (3.2m)
```

Tras logout se limpiaba la cabecera pero seguía montada la vista privada. `3b8991e` añade una barrera: cerrar superposiciones, retirar entradas y después entregar logout/navegar por 401. `f599c73` exige también el formulario de ingreso y ausencia de componentes privados. Las nuevas unitarias cubren espera, anidados y aperturas pendientes. **Estas correcciones necesitan la pasada final; no se declaran verificadas por la suite anterior.**

```text
Failed to create unified exec process:
timed out after 15000ms connecting runner pipe-in
```

El ejecutor falló incluso al crear un proceso de lectura. El intento directo desde cmd también quedó detenido antes de los casos y se cerró enviando Ctrl+C a su sesión (salida 1). Node --version sí confirmó 24.21.0. Un intento previo de cmd falló por entrecomillado del ejecutable; se corrigió sin tocar fuentes.

### Presupuesto y hallazgos

Comparaciones temporales restauradas en finally: base `29f3ed9` **499,58 kB**; fuente raíz posterior a `65adc38` sin registro SW **500,58 kB**; producción con SW anterior a `3b8991e` **506,83 kB**. Separar el mensaje del servicio Red y centralizar 401 redujo el crecimiento, pero quedaban **576 bytes** sobre 500 kB sin el registro SW. **No se aplica DA-96:** su condición «solo por service worker» no se cumple. Se conserva aviso 500 kB / error 1 MB; falta resolver el exceso y medir de nuevo las correcciones posteriores.

DA-88 literal: Angular siempre genera `dataGroups: []`. La configuración no declara la propiedad y las pruebas exigen cero grupos en el generado y ninguna API en assets/caché. El arquitecto debe precisar «sin grupos de datos»; N no modifica el contrato ni el generador oficial.

### Reproducción y cierre pendiente

Desde frontend, con Node 24.21.0 de fnm:

```powershell
npm run lint
npm run format:check
$env:VITEST_MAX_WORKERS='1'
$env:NG_BUILD_MAX_WORKERS='2'
npm test -- --watch=false --runner-config=e2e/pwa/vitest.config.mts
npm run build
```

Toda prueba con base se hizo exclusivamente mediante:

```powershell
pwsh -File D:\Proyectos\BarberTurno-v2\.local\e2e-v2-test.ps1 -Worktree D:\Proyectos\BarberTurno-v2-pwa-nucleo -Registro tmp\144-pwa-nucleo-final.log --config=playwright.pwa.config.ts --max-failures=1
```

CP-1, CP-2, CP-4 y CP-5 E2E siguen pendientes de una pasada correcta de la revisión final; CP-3 pasó en revisiones anteriores; CP-6 lógica tiene unitarias escritas. El bloqueo se registra aquí porque el encargo prohíbe modificar tareas/backlog. Los logs `tmp-pwa-*.log` son artefactos locales ignorados, sin secretos.

Commits de código/pruebas: `76c3391`, `948c183`, `e1d8e32`, `65adc38`, `f51d3da`, `4969079`, `3b8991e`, `f599c73`, `e8a4d54`, `8e3de92`. Merge: **ninguno**, por orden expresa.

Límites: solo archivos asignados y esta evidencia. Sin requisitos/arquitectura/backlog/tareas/AGENTS/CLAUDE/APF2, v1, entregables, Azure, public, index ni estilos/plantillas de M. Sin backend ni Javadoc nuevos. Sin lectura directa ni exposición de .local/coordinación; solo invocación del arnés autorizado. Sin push, remotos, tags, servicios externos, instalaciones, worktrees nuevos o cambios de ACL/propietario/configuración. Pendiente la comprobación final de procesos y el commit documental.
