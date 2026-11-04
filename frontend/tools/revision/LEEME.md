# Entorno de revisión conjunta (V2-07…V2-10)

Script y sondas versionados para revisar en local la rama conjunta de BarberTurno v2 con el *backend* real (`dev,demo`) y el *frontend* empaquetado sobre la base desechable **`barberturno_v2_test`** en el puerto **`8081`**. Nunca usa `barberturno_v2` ni las bases de v1, y nunca imprime valores de `.local/barberturno.env`.

## 1. Uso

Desde cualquier carpeta en PowerShell 7 (`pwsh`):

```powershell
# 1. Revisión manual interactiva (pide una clave ficticia oculta que cumpla RN-25 y abre dos ventanas)
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1

# 2. Verificación automática sin ventanas interactivas (clave aleatoria en memoria + sonda axe)
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Verificar

# 3. Verificación con sondas adicionales de foco y salto al contenido
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Verificar -Sondas axe.mjs,foco.mjs,salto.mjs

# 4. Recuperación explícita tras un cierre abrupto de la consola
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Recuperar

# 5. Modo de espera sin ventanas (exclusivo para probar el cierre abrupto y el Job Object)
pwsh -NoProfile -File frontend/tools/revision-conjunta.ps1 -Mantener
```

Parámetro opcional `-Entorno`: ruta del archivo con las variables `BT_*` (por defecto, `D:\Proyectos\BarberTurno-v2\.local\barberturno.env`). Las variables se cargan únicamente en el proceso actual y se eliminan al salir.

## 2. Ventanas y recorridos a mano para el responsable

En el modo interactivo, `frontend/tools/revision/ventanas.mjs` abre dos ventanas de Chromium con contextos independientes (`es-PE`, `America/Lima`):

- **Escritorio:** ventana real maximizada (`viewport: null`), de modo que todo el contenido se alcanza desplazando incluso con escala del sistema al 125 %.
- **Móvil:** *viewport* emulado de **`360 × 640`**.

La sesión permanece abierta hasta que se cierran ambas ventanas.

### Cuentas ficticias de demostración

Todas las cuentas comparten la contraseña introducida al iniciar el script:

| Cuenta | Barberías y papeles |
|---|---|
| `multirol@ejemplo.test` | **Propietario** en `BarberTurno` (`/n/barberturno`) · **Barbero** (profesional) en `Barbería Sur` (`/n/sur`) |
| `personal-cliente@ejemplo.test` | **Barbero** (profesional) y **Cliente** en `BarberTurno` |
| `encargado@ejemplo.test` | **Encargado** en `BarberTurno` · **Propietario** en `Barbería Sur` |
| `cliente@ejemplo.test` | **Cliente** en `BarberTurno` y en `Barbería Sur` |
| `plataforma@ejemplo.test` | **Administrador de la plataforma** (`/plataforma/negocios`), sin membresías en barberías |

### Recorridos recomendados

1. **Cambio de barbería (`multirol@ejemplo.test` o `encargado@ejemplo.test`):**
   - Ingrese con `multirol@ejemplo.test` y elija **BarberTurno** en `/negocios` (entra como **Propietario**).
   - En el bloque **«Barbería actual»** del menú lateral (o en móvil abriendo el menú `☰`), pulse **«Cambiar de barbería»** para volver al selector `/negocios` y entre en **Barbería Sur** (pasa a actuar como **Barbero**, el menú se reduce a *Agenda* y *Mi cuenta* y se descarta el estado de la barbería anterior).
2. **Cambio de papel (`personal-cliente@ejemplo.test`):**
   - Ingrese con `personal-cliente@ejemplo.test`. En el diálogo *«¿Cómo quiere entrar en BarberTurno?»*, elija **«Como personal (Barbero)»** y pulse **«Entrar»**.
   - En el bloque **«Barbería actual»** (menú lateral en escritorio, o menú **«Abrir cuenta»** en móvil), pulse **«Cambiar papel»**, seleccione **«Como cliente»** y confirme. La ruta pasa a llevar `?papel=cliente`, la navegación muestra *Reservar*, *Mis citas* y *Mi cuenta*, y las peticiones envían `X-BT-Papel: CLIENTE`.
3. **Avisos de cliente, profesional, encargado y propietario:**
   - **Cliente (`cliente@ejemplo.test` o `personal-cliente@ejemplo.test` con papel Cliente):** en `/n/barberturno/reservar` pulse la campana de la cabecera (`Avisos: … sin leer`) para abrir el diálogo, o entre en *Mis citas* para usar el panel de avisos; pruebe *«Marcar como leído»* y *«Marcar todos como leídos»*. Salga a `/negocios` con *«Cambiar de barbería»* y vuelva a entrar para comprobar que el contador se vuelve a consultar (V2-07.6d).
   - **Profesional (`personal-cliente@ejemplo.test` como Barbero, o `carlos@ejemplo.test`):** en `/n/barberturno/agenda` pulse la campana (`Ver avisos: … sin leer`), revise el aviso de reserva asignada y marque su lectura.
   - **Encargado (`encargado@ejemplo.test` en `BarberTurno`):** en `/n/barberturno/agenda` (o *Equipo* / *Invitaciones*) pulse la campana (`Avisos: 1 sin leer`) y revise el aviso interno de fallo de entrega por WhatsApp (V2-DA-61, con el código de reserva y sin datos personales del cliente).
   - **Propietario (`multirol@ejemplo.test` en `BarberTurno`):** en `/n/barberturno/agenda` (o *Configuración*) pulse la campana (`Avisos: 1 sin leer`), revise el aviso interno de fallo de WhatsApp, cambie a **Barbería Sur** (`0 sin leer`) y regrese a **BarberTurno** para comprobar que el contador se actualiza por barbería.

## 3. Por qué no se promete que el `finally` se ejecute siempre

En PowerShell, el bloque `finally` se ejecuta cuando el script termina por su flujo normal (al cerrar las dos ventanas de revisión, al finalizar `-Verificar`, ante una excepción de PowerShell o al pulsar `Ctrl+C` si la consola sigue activa).

Sin embargo, **un `finally` no se ejecuta si la consola muere de forma abrupta**:
- si se cierra la ventana de la terminal directamente con el botón `X` y el anfitrión finaliza `pwsh.exe` antes de que termine la limpieza (fallo real ocurrido en la revisión de V2-07);
- si el proceso `pwsh.exe` recibe `Stop-Process -Force` / `taskkill /F` (`TerminateProcess`);
- ante un cierre forzoso de sesión o caída del proceso anfitrión.

Cuando eso ocurre, el proceso de PowerShell desaparece en el acto sin ejecutar ni una sola línea del `finally`. Por eso el diseño separa la **terminación inmediata de procesos hijos** (mediante un *Job Object* del sistema operativo) de la **limpieza diferida de estado y base de datos** (mediante archivo de sesión, cerrojo y recuperación).

## 4. Garantías y límites del *Job Object* de Windows

`revision-conjunta.ps1` crea un objeto de trabajo de Windows (`CreateJobObjectW`) configurado con la bandera `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` y le asigna:
- el proceso del *backend* (`java -jar …`);
- el proceso de las ventanas de revisión (`node …/ventanas.mjs`), cuyos procesos hijos (los navegadores Chromium lanzados por Playwright) heredan automáticamente el trabajo.

### Qué garantiza el *Job Object*
- Si el proceso de la consola (`pwsh.exe`) termina de forma abrupta, el núcleo de Windows cierra el último identificador abierto del *Job Object* y **termina inmediatamente el *backend* y los navegadores**, liberando el puerto `8081` sin dejar procesos huérfanos.

### Qué NO garantiza el *Job Object* (límites explícitos)
1. **No vacía la base de datos ni borra archivos en disco:** el núcleo de Windows solo termina procesos; no ejecuta `vaciar()` sobre `barberturno_v2_test`, ni borra `.local/revision-conjunta.sesion.json`, ni retira `D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock`. Esa limpieza la realiza la recuperación.
2. **Ventana de milisegundos al crear cada proceso:** en Windows, `Start-Process` crea el proceso antes de que `AssignProcessToJobObject` reciba su identificador. Existe una ventana de unos milisegundos entre ambas llamadas en la que, si la consola muriera exactamente en ese instante, el proceso hijo podría no haber entrado aún en el trabajo. El archivo de sesión y la recuperación cubren también ese caso residual.

## 5. Cerrojo, archivo de sesión y recuperación

- **Cerrojo (`D:\Proyectos\BarberTurno-v2\.local\bd-v2-test.lock`):** se toma antes de arrancar procesos con el formato `pid=<PID> worktree=<ruta> inicio=<ISO-8601>` durante toda la sesión, para que ninguna suite de pruebas use `barberturno_v2_test` simultáneamente.
- **Archivo de sesión (`.local/revision-conjunta.sesion.json`):** ignorado por Git; se escribe antes de arrancar procesos y registra `pidConsola`, `pidBackend`, `pidVentanas`, `jar`, `base`, `puerto` e `inicio`.
- **Recuperación (automática al arrancar si existe `.local/revision-conjunta.sesion.json`, o manual con `-Recuperar`):**
  1. Si el cerrojo pertenece a otro proceso vivo, **aborta sin tocar nada** (no detiene procesos, no vacía la base y no borra el cerrojo ni la sesión).
  2. Si los PIDs registrados en la sesión (`pidBackend`, `pidVentanas`) siguen vivos, los detiene **únicamente** si su línea de órdenes (`Win32_Process.CommandLine`) coincide con el jar de BarberTurno o con `ventanas.mjs`, nunca por nombre de proceso.
  3. Vacía `barberturno_v2_test` mediante `vaciar()` de `frontend/e2e/base-datos.ts`.
  4. Borra el cerrojo **solo** si su `pid` coincide con `pidConsola` de la sesión registrada y ese proceso ya no existe.
  5. Borra `.local/revision-conjunta.sesion.json` e informa en consola de todo lo recuperado.
