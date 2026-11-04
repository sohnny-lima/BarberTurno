# Entorno de revisión conjunta (V2-07…V2-10)

Script y sondas versionados para revisar en local la rama conjunta de BarberTurno v2 con el _backend_ real (`dev,demo`) y el _frontend_ empaquetado sobre la base desechable **`barberturno_v2_test`** en el puerto **`8081`**. Nunca usa `barberturno_v2` ni las bases de v1, y nunca imprime valores de `.local/barberturno.env`.

## 1. Uso

Desde cualquier carpeta en PowerShell 7 (`pwsh`):

```powershell
# 1. Revisión manual interactiva (pide una clave ficticia oculta que cumpla RN-25 y abre dos ventanas)
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1

# 2. Verificación automática sin ventanas interactivas (clave aleatoria en memoria + sonda axe)
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Verificar

# 3. Verificación con sondas adicionales de foco y salto al contenido
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Verificar -Sondas axe.mjs,foco.mjs,salto.mjs

# 4. Recuperación explícita tras un cierre abrupto o un vaciado fallido
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Recuperar

# 5. Modo de espera sin ventanas (exclusivo para probar el cierre abrupto y el Job Object)
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Mantener
```

Parámetro opcional `-Entorno`: ruta del archivo con las variables `BT_*` (por defecto, `D:\Proyectos\BarberTurno-v2\.local\barberturno.env`). Las variables se cargan únicamente en el proceso actual y se eliminan al salir.

## 2. Ventanas y recorridos a mano para el responsable

En el modo interactivo, `frontend/tools/revision/ventanas.mjs` abre dos ventanas de Chromium con contextos independientes (`es-PE`, `America/Lima`):

- **Escritorio:** ventana real maximizada (`viewport: null`), de modo que todo el contenido se alcanza desplazando incluso con escala del sistema al 125 %.
- **Móvil:** _viewport_ emulado de **`360 × 640`**.

La sesión permanece abierta hasta que se cierran ambas ventanas.

### Cuentas ficticias de demostración

Todas las cuentas comparten la contraseña introducida al iniciar el script:

| Cuenta                          | Barberías y papeles                                                                                          |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `multirol@ejemplo.test`         | **Propietario** en `BarberTurno` (`/n/barberturno`) · **Barbero** (profesional) en `Barbería Sur` (`/n/sur`) |
| `personal-cliente@ejemplo.test` | **Barbero** (profesional) y **Cliente** en `BarberTurno`                                                     |
| `encargado@ejemplo.test`        | **Encargado** en `BarberTurno` · **Propietario** en `Barbería Sur`                                           |
| `cliente@ejemplo.test`          | **Cliente** en `BarberTurno` y en `Barbería Sur`                                                             |
| `plataforma@ejemplo.test`       | **Administrador de la plataforma** (`/plataforma/negocios`), sin membresías en barberías                     |

### Recorridos recomendados

1. **Cambio de barbería (`multirol@ejemplo.test` o `encargado@ejemplo.test`):**
   - Ingrese con `multirol@ejemplo.test` y elija **BarberTurno** en `/negocios` (entra como **Propietario**).
   - En el bloque **«Barbería actual»** del menú lateral (o en móvil abriendo el menú `☰`), pulse **«Cambiar de barbería»** para volver al selector `/negocios` y entre en **Barbería Sur** (pasa a actuar como **Barbero**, el menú se reduce a _Agenda_ y _Mi cuenta_ y se descarta el estado de la barbería anterior).
2. **Cambio de papel (`personal-cliente@ejemplo.test`):**
   - Ingrese con `personal-cliente@ejemplo.test`. En el diálogo _«¿Cómo quiere entrar en BarberTurno?»_, elija **«Como personal (Barbero)»** y pulse **«Entrar»**.
   - En el bloque **«Barbería actual»** (menú lateral en escritorio, o menú **«Abrir cuenta»** en móvil), pulse **«Cambiar papel»**, seleccione **«Como cliente»** y confirme. La ruta pasa a llevar `?papel=cliente`, la navegación muestra _Reservar_, _Mis citas_ y _Mi cuenta_, y las peticiones envían `X-BT-Papel: CLIENTE`.
3. **Avisos de cliente, profesional, encargado y propietario:**
   - **Cliente (`cliente@ejemplo.test` o `personal-cliente@ejemplo.test` con papel Cliente):** en `/n/barberturno/reservar` pulse la campana de la cabecera (`Avisos: … sin leer`) para abrir el diálogo, o entre en _Mis citas_ para usar el panel de avisos; pruebe _«Marcar como leído»_ y _«Marcar todos como leídos»_. Salga a `/negocios` con _«Cambiar de barbería»_ y vuelva a entrar para comprobar que el contador se vuelve a consultar (V2-07.6d).
   - **Profesional (`personal-cliente@ejemplo.test` como Barbero, o `carlos@ejemplo.test`):** en `/n/barberturno/agenda` pulse la campana (`Ver avisos: … sin leer`), revise el aviso de reserva asignada y marque su lectura.
   - **Encargado (`encargado@ejemplo.test` en `BarberTurno`):** en `/n/barberturno/agenda` (o _Equipo_ / _Invitaciones_) pulse la campana (`Avisos: 1 sin leer`) y revise el aviso interno de fallo de entrega por WhatsApp (V2-DA-61, con el código de reserva y sin datos personales del cliente).
   - **Propietario (`multirol@ejemplo.test` en `BarberTurno`):** en `/n/barberturno/agenda` (o _Configuración_) pulse la campana (`Avisos: 1 sin leer`), revise el aviso interno de fallo de WhatsApp, cambie a **Barbería Sur** (`0 sin leer`) y regrese a **BarberTurno** para comprobar que el contador se actualiza por barbería.

## 3. Por qué no se promete que el `finally` se ejecute siempre

En PowerShell, el bloque `finally` se ejecuta cuando el script termina por su flujo normal (al cerrar las dos ventanas de revisión, al finalizar `-Verificar`, ante una excepción de PowerShell o al pulsar `Ctrl+C` si la consola sigue activa).

Sin embargo, **un `finally` no se ejecuta si la consola muere de forma abrupta**:

- si se cierra la ventana de la terminal directamente con el botón `X` y el anfitrión finaliza `pwsh.exe` antes de que termine la limpieza (fallo real ocurrido en la revisión de V2-07);
- si el proceso `pwsh.exe` recibe `Stop-Process -Force` / `taskkill /F` (`TerminateProcess`);
- ante un cierre forzoso de sesión o caída del proceso anfitrión.

Cuando eso ocurre, el proceso de PowerShell desaparece en el acto sin ejecutar ni una sola línea del `finally`. Por eso el diseño separa la **terminación inmediata de procesos hijos** (mediante un _Job Object_ del sistema operativo al que se asigna la propia consola antes de crear ningún hijo) de la **limpieza diferida de estado y base de datos** (mediante archivo de sesión con identificador único, cerrojo y recuperación).

## 4. Garantías y límites del _Job Object_ de Windows

`revision-conjunta.ps1` crea un objeto de trabajo de Windows (`CreateJobObjectW`) configurado con la bandera `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` y le asigna **el propio proceso de la consola (`[System.Diagnostics.Process]::GetCurrentProcess().Handle`) antes de crear ningún proceso hijo**. Si la asignación de la consola al trabajo falla (por ejemplo, porque el proceso anfitrión pertenezca a un trabajo que prohíba anidar), el script aborta con un mensaje claro antes de iniciar procesos ni tocar la base.

Al estar la consola dentro del trabajo desde antes de cualquier `Start-Process`, todo proceso hijo nace ya dentro del _Job Object_ sin ventana entre creación y asignación:

- el proceso del _backend_ (`java -jar …`);
- el proceso de las ventanas de revisión (`node …/ventanas.mjs`) y sus navegadores hijos de Playwright;
- cada proceso de sonda ejecutado en `-Verificar` (`axe.mjs`, `foco.mjs`, `salto.mjs`) y sus navegadores hijos.

### Qué garantiza el _Job Object_

- Si el proceso de la consola (`pwsh.exe`) termina de forma abrupta, el núcleo de Windows cierra el último identificador abierto del _Job Object_ y **termina inmediatamente el _backend_, las sondas y los navegadores**, liberando el puerto `8081` sin dejar procesos huérfanos.
- Con `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`, el cierre normal del proceso de la consola al terminar el script también cierra el identificador del trabajo y termina cualquier proceso hijo que quedara en él, lo que es coherente porque los hijos ya se detienen explícitamente en el bloque `finally` antes de vaciar la base y salir.

### Qué NO garantiza el _Job Object_ (límites explícitos)

1. **No vacía la base de datos ni borra archivos en disco:** el núcleo de Windows solo termina procesos; no ejecuta `vaciar()` sobre `barberturno_v2_test`, ni borra `.local/revision-conjunta.sesion.json`, ni retira `D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock`. Esa limpieza la realiza la recuperación.
2. **Procesos no registrados aún si la consola muere antes de escribir el PID:** si la consola muere en el instante posterior a `Start-Process` antes de que llegue a actualizarse `.local/revision-conjunta.sesion.json`, es el _Job Object_ (y no la recuperación por PID) quien garantiza la muerte inmediata del proceso hijo. La recuperación posterior vacía la base y limpia el cerrojo y la sesión.

## 5. Cerrojo, archivo de sesión y recuperación

- **Cerrojo de la base (`D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock`):** se toma con `[IO.FileMode]::CreateNew` antes de arrancar procesos con el formato `pid=<PID> idSesion=<GUID> worktree=<ruta> inicio=<ISO-8601>` durante toda la sesión, para que ninguna suite de pruebas use `barberturno_v2_test` simultáneamente.
- **Cerrojo de recuperación (`.local/revision-conjunta.recuperacion.lock`):** serializa las ejecuciones concurrentes de `Recuperar-Sesion` (creado con `CreateNew` y liberado en su propio `finally`).
- **Archivo de sesión (`.local/revision-conjunta.sesion.json`):** ignorado por Git; se escribe antes de arrancar procesos y se actualiza inmediatamente después de crear cada hijo (antes de esperar a que el _backend_ arranque o a que terminen las sondas/ventanas). Registra:
  - `idSesion` (GUID único de la sesión), `pidConsola`, `jar`, `base`, `puerto`, `inicio` (`hora`), `worktree` y `limpiezaPendiente`;
  - la identidad de cada proceso activo (`backend`, `ventanas`, `sonda`), compuesta por su `pid`, **hora de creación** (`Process.StartTime` en UTC ISO-8601), **ejecutable** con ruta absoluta y **línea de órdenes completa** con rutas absolutas.
- **Cierre normal y vaciado fallido:**
  - Antes de vaciar y antes de borrar la sesión o el cerrojo en el `finally`, el script vuelve a leer `.local/revision-conjunta.sesion.json` y comprueba que `idSesion` sigue siendo el suyo.
  - Si `Vaciar-Base` falla (por ejemplo, porque hay una conexión externa abierta a `barberturno_v2_test` que la guarda de `vaciar()` rechaza), el script **conserva** el archivo de sesión marcando `limpiezaPendiente = true` y **conserva** el cerrojo `bd-v2-test.lock`, e informa en consola de cómo ejecutar `-Recuperar`.
  - La sesión y el cerrojo solo se borran tras confirmar el vaciado y comprobar que `idSesion` y el `pid` del cerrojo siguen siendo los propios.
- **Recuperación (automática al arrancar si existe `.local/revision-conjunta.sesion.json`, o manual con `-Recuperar`):**
  1. Se serializa mediante `.local/revision-conjunta.recuperacion.lock` (`CreateNew`). Si otra recuperación concurrente ya procesó la sesión mientras se esperaba el cerrojo, sale sin tocar nada.
  2. Si el cerrojo de la base (`bd-v2-test.lock`) pertenece a otro proceso vivo, **aborta sin tocar nada** (no detiene procesos, no vacía la base y no borra el cerrojo ni la sesión).
  3. Si alguno de los procesos registrados en la sesión (`backend`, `ventanas`, `sonda`) sigue vivo, lo detiene **únicamente** si coinciden **los tres** datos de su identidad: hora de creación (`StartTime`), ruta absoluta del ejecutable y línea de órdenes completa con rutas absolutas (sin comodines por nombre de archivo).
  4. Vuelve a leer `.local/revision-conjunta.sesion.json` para comprobar que `idSesion` no ha cambiado y vacía `barberturno_v2_test` mediante `vaciar()` de `frontend/e2e/base-datos.ts`. Si el vaciado falla, conserva la sesión con `limpiezaPendiente = true` y el cerrojo, e informa de cómo reintentar `-Recuperar`.
  5. Tras confirmar el vaciado, vuelve a leer `.local/revision-conjunta.sesion.json` para comprobar que `idSesion` sigue siendo el mismo, borra el cerrojo **solo** si su `pid` coincide con `pidConsola` de esa sesión y dicho proceso ya no existe, y borra `.local/revision-conjunta.sesion.json`.

## 6. Pruebas de verificación del entorno (P1…P7)

Pruebas que ejecuta el coordinador para validar el entorno:

- **P1:** `-Verificar` → código 0; sin archivo de sesión ni cerrojo al terminar; base vacía (1 negocio, 0 filas en tablas limpiadas); ningún proceso escuchando en el puerto `8081`.
- **P2:** consola con `-Mantener` → se mata la consola con `Stop-Process -Force` → en ≤ 10 s el _backend_ ya no existe (terminado por el _Job Object_), y quedan en disco el archivo de sesión y el cerrojo.
- **P3:** `-Recuperar` → base vacía, cerrojo y archivo de sesión borrados, con el resumen de lo recuperado en salida estándar.
- **P4:** `-Verificar` de nuevo (reapertura) → código 0.
- **P5:** con el cerrojo tomado por otro proceso vivo, tanto `-Recuperar` como el arranque normal abortan sin tocar procesos, base, cerrojo ni sesión.
- **P6:** dos `-Recuperar` lanzados a la vez → se serializan mediante `.local/revision-conjunta.recuperacion.lock`; uno recupera la sesión y el otro espera o sale sin tocar nada.
- **P7:** un vaciado que falla (por ejemplo, con una sesión de base abierta en `barberturno_v2_test` que la guarda de `vaciar()` rechaza) → la sesión se conserva con `limpiezaPendiente = true` junto con el cerrojo, e invocando `-Recuperar` una vez cerrada la conexión externa se completa la limpieza.
