# Evidencia y Suite de Aceptación: Demo v2 en Azure + Correcciones tras Ejecución Real y Revisión Codex (g115/g116/g118/g120/g121/g122/g123)

- **Fecha:** 2026-11-06
- **Autor de pruebas:** Gemini (Antigravity CLI) — Responsable de pruebas
- **Coordinador:** Claude Code
- **Worktree:** `D:\Proyectos\BarberTurno-v2-demo-aceptacion`
- **Rama:** `tarea/V2-DEMO-aceptacion`
- **Entorno objetivo:** Demo publicada en Azure App Service (`https://barberturno-v2-demo-202611.azurewebsites.net`, Spring Boot 4.1.1 + SPA Angular 22 empaquetada con service worker PWA, perfil `prod` y seed de dos barberías `barberturno` y `sur`).

---

## 1. Archivos creados y modificados

| Archivo | Estado | Qué cubre / Criterio correspondiente |
|---|---|---|
| `frontend/playwright.demo.config.ts` | **Modificado** (g115) | Configuración de Playwright para la demo en Azure: `baseURL` desde `BT_DEMO_URL`, validación de `BT_DEMO_PASSWORD`, viewports Chromium 360×800 y 1440×900, `workers: 1`, `fullyParallel: false`, `retries: 0`, `serviceWorkers: 'allow'`, reporter `list`. Ajuste a `timeout: 180_000` (180 s) justificado por el arranque en frío de Azure App Service (plan F1 compartido). |
| `frontend/e2e/demo/ayudantes.ts` | **Modificado** (g116/g118/g121/g122) | Incorporación en **g121** de `cerrarContexto(page)` determinista y ajuste en **g122** de `cerrarSesion`: repliegue previo de cualquier diálogo o modal activo (`page.keyboard.press('Escape')`) para evitar que el *backdrop* de CDK intercepte la interacción con el control de cierre de sesión. |
| `frontend/e2e/demo/aceptacion.spec.ts` | **Modificado** (g116/g118/g120/g121/g122/g123) | Corrección en **g123** del último hallazgo de Codex (151) en la **prueba 11 (CP-11)**: traslado de la recolección y limpieza de escrituras detectadas al bloque `finally` para ejecutarse siempre antes de desregistrar listeners y cerrar sesión; ampliación del plazo de espera por respuesta a 30 s (`Promise.race([req.response(), timeout(30000)])`); guardado al inicio de la prueba de la lista de reservas existentes antes de desconectar y consulta fallback de reservas activas (`GET /api/n/barberturno/reservas/mias?tamano=100`) ante expiración del plazo para cancelar cualquier reserva creada durante la prueba; captura de errores de limpieza con el patrón de la prueba 05.<br>Correcciones previas (g122): prueba 05 con `try` independientes, 200/204 y XSRF; prueba 04 con agenda y catálogo propio de Sur sin contaminación. |
| `frontend/e2e/pwa/movil.spec.ts` | **Modificado** (g115/g120) | Corrección g120 (Hallazgo 9): eliminación de bifurcación con `isVisible()` en CP-8 «reservar», esperando y marcando directamente el radio «Corte clásico» sin *fallback*. Correcciones previas (g115): `comprobarZoom200` preservando `toBeInViewport({ ratio: 1 })` y `click({ trial: true })`, eliminación de `networkidle` en CDP y aserción estricta de Content-Type en el manifest. |
| `docs/v2/pruebas/v2-demo-aceptacion.md` | **Modificado** (g118/g120/g121/g122/g123) | Informe técnico actualizado con la subsanación del hallazgo final de Codex 151 (prueba 11). |

---

## 2. Variables de entorno necesarias y justificación de configuración

### Variables obligatorias

| Variable | Propósito | Reglas de seguridad |
|---|---|---|
| `BT_DEMO_URL` | URL base pública de la instancia de Azure App Service (`https://barberturno-v2-demo-202611.azurewebsites.net`). | Obligatoria. Si falta, la configuración falla inmediatamente con error descriptivo. |
| `BT_DEMO_PASSWORD` | Contraseña asignada a las cuentas ficticias de demostración creadas por `DatosDemoService` y `DatosDemoMultinegocioService`. | Obligatoria. **Nunca** se escribe en archivos versionados, capturas, mensajes de error ni anotaciones de Playwright. |

### Justificación técnica del timeout a 180 s en `playwright.demo.config.ts`

En la corrida inicial del 06/11/2026:
- En `chromium-360`, la prueba 02 tardó 2,0 minutos.
- En `chromium-1440`, la prueba 02 falló por `Test timeout of 120000ms exceeded while setting up "page"`, abortando la suite serial.

El plan F1 (nivel gratuito compartido) de Azure App Service experimenta suspensión de instancias y arranque en frío (*cold start*) pronunciado cuando entra la primera petición tras periodos de inactividad, sumado a la contención de recursos en el host de ejecución. Al eliminar `waitForLoadState('networkidle')` y pasar a esperas explícitas reactivas (`expect.poll` y localizadores de visibilidad), la duración nominal de las pruebas se reduce drásticamente (a pocos segundos una vez cargada la página). No obstante, elevar el `timeout` global a 180 s garantiza resiliencia ante arranques en frío extremos sin debilitar las aserciones funcionales ni de seguridad. Se mantienen intactos `workers: 1`, `fullyParallel: false`, `retries: 0` y la configuración `serial`.

---

## 3. Cobertura detallada y correcciones

### 3.1 Corrección g123 (Revisión Codex 151) · Prueba 11: Recolección y limpieza en finally, plazo de 30 s y consulta de reservas activas

- **Diagnóstico (Hallazgo 151 de Codex):** La espera de respuestas de las escrituras detectadas estaba dentro del bloque `try` de la prueba. Si fallaba cualquier aserción previa (como la del banner de reconexión), el flujo saltaba directamente al `finally` que retiraba los listeners de `request` y `response`. Asimismo, si una respuesta tardaba más de 5 s, se continuaba sin resolverla, quedando cualquier HTTP 201 posterior fuera de la limpieza en base de datos.
- **Solución implementada:**
  1. **Recolección y limpieza en el bloque `finally`:** La espera y resolución de las respuestas de todas las escrituras detectadas se trasladó íntegramente al bloque `finally`, garantizando que se ejecute **siempre** tanto en la salida normal como ante cualquier fallo de aserción del cuerpo de la prueba.
  2. **Preservación activa de los listeners:** Los listeners de `request` y `response` se mantienen registrados y activos durante toda la fase de recolección y cancelación, retirándose con `context.off` únicamente tras completar la resolución y la cancelación de reservas inesperadas.
  3. **Plazo extendido de espera a 30 s:** Para cada escritura detectada, se espera su respuesta mediante `Promise.race([req.response(), timeout(30000)])`.
  4. **Consulta de seguridad de reservas activas ante expiración de plazo:** Si expira el plazo de 30 s sin respuesta para alguna escritura (`huboEscrituraSinRespuesta = true`), no se da por resuelta; se realiza una consulta de seguridad `GET /api/n/barberturno/reservas/mias?tamano=100` y se identifican las reservas creadas durante la prueba comparando contra el conjunto `idsReservasPrevias` guardado al inicio de la prueba antes de la desconexión. Cualquier reserva activa no presente en la lista inicial se agrega a `reservasInesperadas`.
  5. **Cancelación garantizada con 200 y logout con 204:** Para cada reserva inesperada capturada se ejecuta `POST /api/n/barberturno/reservas/{id}/cancelacion` validando HTTP 200 (obteniendo el token XSRF vía `GET /api/auth/sesion` si no estuviera presente), y solo después de procesar todas las cancelaciones se desregistran los listeners y se procede al cierre de sesión exigiendo HTTP 204.
  6. **Gestión de fallos de limpieza:** Los posibles errores de la consulta de reservas, cancelación o logout se capturan en `erroresLimpieza`, se emiten a `test.info().errors` y se anexan a `errorCuerpo` sin ocultar el fallo original de la prueba.

### 3.2 Correcciones g122 (Revisión Codex 150b) · Tres hallazgos pendientes

Codex dio por resueltos 6 de sus 9 hallazgos previos y emitió tres observaciones precisas sobre el código de pruebas, las cuales han sido resueltas en su totalidad:

#### Hallazgo 1 (Alta) · Prueba 05: Limpieza con try independientes, confirmación 200/204 y recuperación de XSRF
- **Diagnóstico:** Anteriormente, el bloque `finally` omitía la cancelación si no encontraba la cookie `XSRF-TOKEN`, no comprobaba el código de respuesta HTTP devuelto por la API y silenciaba los errores de cancelación y de logout mediante bloques `catch` vacíos, permitiendo que la prueba aprobara con el cierre de sesión fallido.
- **Solución implementada:**
  1. Se ejecutan **siempre** ambas operaciones, cada una en su propio bloque `try`.
  2. Si la cookie `XSRF-TOKEN` no se encuentra disponible al entrar en la limpieza, se realiza una petición preliminar `GET /api/auth/sesion` para que el filtro CSRF emita la cookie de forma determinista antes de enviar la solicitud de cancelación.
  3. Se exige formalmente que la llamada de cancelación `POST /api/n/barberturno/reservas/{id}/cancelacion` responda con código HTTP **200** (según contrato de `ReservaController.cancelar`).
  4. Se exige formalmente que el cierre de sesión concluya con código HTTP **204** (según contrato de `AuthController.logout`) y retorno a `/ingresar`.
  5. Si ocurren errores durante la limpieza, estos se recopilan y se notifican a través de `test.info().errors`. Si la prueba principal ya había fallado, el error original se relanza enriquecido con el detalle de los fallos de limpieza para no ocultar la causa raíz. Si la prueba principal no falló pero la limpieza sí, se lanza una excepción explícita que hace fallar la prueba.

#### Hallazgo 2 (Alta) · Prueba 11: Espera de respuestas asíncronas antes de retirar listeners y cancelación garantizada
- **Diagnóstico:** Ante la detección de una petición de escritura espuria en reconexión, la aserción fallaba inmediatamente al observar el evento de la *petición* y procedía en el `finally` a desregistrar los listeners de `request` y `response`. Si la respuesta HTTP 201 llegaba instantes después, la reserva quedaba huérfana en la base de datos sin ser cancelada; asimismo, no se esperaba la resolución de `res.json()`.
- **Solución implementada:**
  1. Se registra cada petición de escritura detectada en un arreglo `escriturasDetectadas`.
  2. Si `waitForEvent('request', ...)` detecta una petición indebida, registra el error pero difiere su lanzamiento hasta haber recolectado y esperado las respuestas.
  3. **Antes** de retirar los listeners con `context.off()`, se recorren todas las peticiones detectadas y se espera de manera explícita su respuesta mediante `Promise.race([req.response(), timeout])` con un plazo acotado de 5 s.
  4. Para cualquier respuesta con código 201 en la ruta de reservas, se parsea asíncronamente el cuerpo y se almacenan tanto el identificador (`id`) como el número de versión (`version`). Se sincronizan todas las tareas pendientes con `Promise.allSettled`.
  5. Una vez retirados los listeners, si se detectaron peticiones de reenvío se falla la aserción.
  6. En el `finally` exterior, para cada reserva inesperada capturada se ejecuta de inmediato la cancelación vía API exigiendo respuesta HTTP 200 (obteniendo el token XSRF vía GET si fuera necesario), y **solo después** de verificar las cancelaciones se ejecuta el cierre de sesión exigiendo HTTP 204.

#### Hallazgo 3 (Media) · Prueba 04: Carga sin error de Sur y comprobación de datos propios de la sede
- **Diagnóstico:** Al cambiar a «Barbería Sur», la prueba únicamente comprobaba el contexto del encabezado y la ausencia de cadenas de BarberTurno; una agenda en blanco o con errores de carga aprobaría falsamente.
- **Solución implementada:**
  1. Se espera la carga completa de la vista `/n/sur/agenda`, verificando ausencia de `[aria-busy="true"]`, recuento 0 de alertas (`.alerta, [role="alert"]`) y ausencia de esqueletos (`app-reserva-esqueleto`).
  2. Se validan los datos propios de la agenda del barbero: título `<h1>Agenda</h1>`, subtítulo propio de personal `Todas las horas corresponden a Lima.` (que omite «Citas de todos los barberos.» al no ser administrador), línea de tiempo visible, estado vacío explícito `Sin citas para este día.` y resumen de conteo `0 citas.`.
  3. Se comprueba que no existe el selector de barberos (`[data-selector-barbero]` con recuento 0) ni residuos de la sede anterior (`Carlos`, `Miguel`, `Barba`, `BarberTurno`).
  4. Adicionalmente, se navega a la vista de cliente de Sur (`/n/sur/reservar`) y se exige la presencia de los **datos propios de Barbería Sur**: en catálogo, presencia de `Corte clásico` y ausencia de `Barba`; en profesionales, presencia del radio con el nombre local `Personal de dos barberías` y ausencia de los barberos de la sede anterior (`Carlos` y `Miguel`), confirmando el aislamiento riguroso entre barberías sin contaminación cruzada.

---

### 3.3 Correcciones g121 · Prueba 04: Cierre determinista de menú y contenido distintivo del personal

1. **Función auxiliar `cerrarContexto(page)` en `ayudantes.ts`:**
   - Detecta si el botón `Cerrar menú` está visible (renderizado en móvil por el componente `Shell` cuando el cajón está abierto: `@if (movil()) { <button mat-button class="cerrar-menu" (click)="menu.close()">Cerrar menú</button> }`).
   - Hace clic en `Cerrar menú` y espera de forma determinista que tanto el botón como el elemento `#menu-principal` pasen a estar ocultos (`toBeHidden()`), además de comprobar que el backdrop `.mat-drawer-backdrop.mat-drawer-shown` desaparezca (`toHaveCount(0)`).
   - Si lo que se desplegó fue un menú de cuenta (`mat-menu`), pulsa el backdrop del overlay o `Escape`, verificando `toBeHidden()`.
   - En resolución de escritorio (1440), donde el `mat-sidenav` opera en modo lateral fijo (`mode="side"`) y no se superpone al contenido principal, no realiza ninguna acción invasiva.
2. **Reescritura de la prueba 04 con pantallas que el rol sí ve:**
   - En `BarberTurno`: tras validar el contexto («BarberTurno», «Propietario»), se llama a `cerrarContexto(page)` para replegar el menú en móvil. Luego se valida el selector de barberos `[data-selector-barbero]` de `/agenda`, abriendo el `mat-select` y comprobando las opciones `Carlos` y `Miguel` (y la ausencia de `Personal de dos barberías`), cerrando el selector con `Escape`. A continuación, se valida el catálogo de servicios visitando `/n/barberturno/admin/servicios` (pantalla accesible para Propietario), comprobando la presencia de los servicios distintivos `Barba` y `Corte clásico`.

---

### 3.4 Correcciones g120 (Revisión Codex 149)

- **Prueba 09 (Media):** Restricción estricta de `test.skip` en CDP únicamente si `browserName !== 'chromium'`.
- **Prueba 10 (Media):** Tratamiento estricto de respuestas opacas con `if (resp.type === 'opaque') continue;`.
- **Prueba 10 (Media):** Validación de control activo del Service Worker, caché pública no vacía (`totalPublicas > 0`) y cargas privadas de `/agenda`, `/mis-citas` y `/reservar` sin rastro en caché.
- **Prueba 06 (Media):** Exigencia de citas reales (`app-agenda-cita`) o estado vacío explícito (`Sin citas para este día.`).
- **Prueba 08 (Media):** Espera obligatoria de respuesta 200 de `/whatsapp/conversaciones` y verificación de conversaciones o estado definitivo «No hay conversaciones derivadas».
- **`movil.spec.ts` CP-8 (Media):** Eliminación de bifurcación condicional con `isVisible()`, marcando directamente el radio `Corte clásico`.

---

## 4. Trazabilidad Criterio → Prueba

| Criterio | Prueba en `aceptacion.spec.ts` | Aspectos auditados |
|---|---|---|
| **CP-1** | `01 · Superficie pública sin sesión` | Health check UP, cabeceras HSTS/CSP/X-Frame-Options/nosniff, OpenAPI/Actuator desactivados, manifest PWA con MIME exacto, assetlinks TWA y ngsw.json sin /api. |
| **CP-2** | `02 · Cookies y CSRF` | Cookie `XSRF-TOKEN` con Secure y SameSite=Strict; cookie `BT_SESION` con HttpOnly y Secure; rechazo HTTP 403 Forbidden ante POST sin cabecera CSRF. |
| **CP-3** | `03 · Acceso e ingreso cliente` | Autenticación de cliente, resolución de selector multisede si aplica, y logout limpio con código 204. |
| **CP-4** | `04 · Selector de barbería` | Alternancia entre sedes con cierre determinista de menús en móvil/escritorio; en `barberturno` selector de barberos (Carlos/Miguel) y catálogo en admin (`Barba`/`Corte clásico`); en `sur` carga sin errores de agenda, título y subtítulo de barbero, estado vacío explícito `Sin citas para este día.`, catálogo exclusivo `Corte clásico` y profesional propio `Personal de dos barberías`, con ausencia estricta de residuos de la sede anterior. |
| **CP-5** | `05 · Reserva (cliente) y cancelación` | Flujo de reserva con franja horaria a 3 días vista, confirmación 201, visualización en Mis citas, cancelación por UI o API en `finally` con verificación obligatoria de HTTP 200 y logout con 204 en bloques independientes sin ocultar errores principales. |
| **CP-6** | `06 · Agenda del personal` | Carga de agenda con citas reales (`app-agenda-cita`) o mensaje explícito de estado vacío (`Sin citas para este día.`), sin falsos positivos por encabezado. |
| **CP-7** | `07 · Avisos y navegación Atrás` | Apertura de diálogo de avisos sin alterar URL y cierre automático mediante botón Atrás (`page.goBack()`). |
| **CP-8** | `08 · Bandeja de WhatsApp` | Espera de respuesta 200 de `/whatsapp/conversaciones`, verificación de conversaciones o estado vacío «No hay conversaciones derivadas», sin alertas de error. |
| **CP-9** | `09 · PWA instalable (CDP)` | Verificación estricta de ausencia de errores de instalabilidad vía CDP en Chromium, sin `skip` defensivo. |
| **CP-10** | `10 · Cache Storage higiénico` | Service worker controlando la página, presencia comprobada de entradas públicas cacheadas, cargas privadas de agenda y mis citas sin rastro en caché, y protección ante botón Atrás tras logout. |
| **CP-11** | `11 · Modo sin conexión y reconexión` | Banner offline, bloqueo y reporte visual de fallos en escrituras, inspección acotada de Background Sync, escucha en `BrowserContext` desde antes de reconectar, ventana negativa de 5 s, recolección y limpieza determinista de escrituras en `finally` antes de retirar listeners y cerrar sesión, espera extendida a 30 s por respuesta, consulta de seguridad de reservas activas (`GET /api/n/barberturno/reservas/mias?tamano=100`) ante expiración de plazo comparando contra la lista inicial de reservas previas a la desconexión, cancelación verificada con HTTP 200 de cualquier reserva espuria y logout con 204. |

---

## 5. Supuestos sobre el contrato y la implementación

1. **Datos de la demo en Azure:** Las cuentas de prueba (`cliente@ejemplo.test`, `carlos@ejemplo.test`, `multirol@ejemplo.test`, `encargado@ejemplo.test`) se encuentran sembradas con la clave provista en `BT_DEMO_PASSWORD`. En particular, `multirol@ejemplo.test` posee membresía como Propietario (`ADMIN`) en `barberturno` y como Barbero (`BARBERO`) en `sur`, contando en `sur` con el perfil activo de barbero bajo el nombre `Personal de dos barberías`.
2. **Reloj de Azure y regla de cancelación:** La demo en Azure opera en la zona `America/Lima` sobre el reloj real del sistema. Las pruebas de reserva dinámica seleccionan fechas con margen futuro (≥ 3 días) para garantizar que la regla de anticipación de cancelación de 2 horas se cumpla holgadamente.
3. **Contrato HTTP de cancelación y logout:**
   - Cancelación de reserva: `POST /api/n/{slug}/reservas/{id}/cancelacion` responde HTTP **200** con el `ReservaDto` actualizado.
   - Cierre de sesión: `POST /api/auth/logout` responde HTTP **204** No Content y renueva la cookie CSRF.
4. **Comportamiento sin conexión (V2-RN / V2-DA):** La aplicación web no realiza sincronización diferida en segundo plano ni reenvíos automáticos opacos de reservas creadas offline; la interfaz informa explícitamente del estado sin conexión y de la imposibilidad de completar la solicitud en el momento.

---

## 6. Defectos o contradicciones observadas

1. **Gestión de superposición modal en limpieza:** Cuando una prueba falla con un diálogo o backdrop de Angular Material / CDK activo, los eventos de puntero hacia botones de navegación o menús pueden ser bloqueados por el overlay. Por tanto, `cerrarSesion` y los bloques de limpieza deben replegar activamente cualquier modal abierto (`Escape`) antes de intentar la interacción con el control de cierre de sesión.
2. **Divergencia de interfaces por rol en la misma ruta:** La ruta `/reservar` presenta comportamientos radicalmente distintos según el rol del usuario: para clientes muestra el flujo de reserva con selección de servicios y profesionales; para personal con rol `ADMIN` presenta «Reserva asistida» con selección previa de cliente registrado. Toda prueba e2e debe evitar asumir la vista de cliente en cuentas con roles de gestión.

---

## 7. Lo que no se pudo cubrir y por qué

1. **Ejecución local interactiva:** Por política del entorno de trabajo no interactivo del agente sin acceso a `run_command`, la compilación y ejecución de las pruebas corresponde a Claude Code a través del ejecutor serializado.
2. **Verificación en dispositivo físico Android y TalkBack:** Corresponde a los criterios CP-10 y verificación manual de accesibilidad del responsable del proyecto.
3. **Webhooks en vivo de Meta WhatsApp:** El entorno demo opera con integración desacoplada (sin app de Meta verificada en producción), validando la superficie visual y el comportamiento del cliente web sin intercambio con servidores externos de Meta.
