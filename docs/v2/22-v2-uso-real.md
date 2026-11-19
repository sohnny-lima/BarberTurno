# V2-REAL · Plataforma lista para uso real (sin demostración)

Autorizado por el responsable el 18/11/2026. Parte de `main` = `716d260` (V2-CONS). Rama `tarea/V2-REAL-uso-real`.

## 1. Alcance autorizado
- Corregir el fallo de Atrás con el menú lateral (CP-5, diagnosticado en V2-CONS §8.3).
- Quitar los textos académicos y las referencias geográficas de relleno.
- Retirar de producción los datos de demostración, con un respaldo previo. Se conservan los datos reales, la base V1 (`barberturno_prod`) y un administrador funcional.
- Desactivar la carga automática de ejemplos.
- Verificar el registro, los roles y el aislamiento entre barberías.
- Comprobar WhatsApp con Twilio. Los mensajes reales solo se envían al número que autorice el responsable.
- Sin contratar servicios ni publicar en Play Store.

Datos que dio el responsable (18/11/2026):
- **Responsable del tratamiento:** Sohnny Lima, contacto limasoin.sli@gmail.com. Aparecen en el aviso de privacidad.
- **Administrador:** se conserva la cuenta de plataforma actual con el nombre visible «Administración». La contraseña la cambia el propio responsable.
- **WhatsApp:** se usa el mismo teléfono de la prueba del 12/11.

## 2. Decisiones
### V2-DA-114 · Administrador de plataforma sin negocio inicial
- **Comportamiento:** si `barberturno.tenancy.negocio-inicial-slug` está vacío, el arranque crea o acredita solo la cuenta ADMIN_PLATAFORMA de `BT_ADMIN_*`, sin membresía ni negocio (`EjecutorEnNegocio.ejecutarSinNegocio`).
- **Con slug:** dev, test y E2E siguen igual.
- **Motivo:** sin este cambio, al borrar el negocio demo inicial la aplicación no arrancaría. Con él, las barberías reales se dan de alta desde la plataforma (alta del negocio e invitación al propietario).

### V2-DA-115 · Limpieza única de la demostración con un script versionado
- **Herramienta:** `tools/v2/limpiar-demo.sql`, que se ejecuta con `limpiar-demo.psql`. Es un único bloque `DO` con modo `ensayo` (termina en `ROLLBACK`) o `aplicar` (termina en `COMMIT`).
- **Ejecución:** con el rol propietario, sin superusuario y respetando la RLS forzada.
- **Protecciones:**
  - descubre todas las tablas con `negocio_id`;
  - aborta si algún negocio de la lista tiene referencias a un usuario que no sea `@ejemplo.test`;
  - solo borra identidades ficticias que hayan quedado sin referencias;
  - renombra al administrador.
- **Uso:** se ejecuta una sola vez en producción, con la aplicación detenida y después del respaldo.

### V2-DA-116 · El cajón se registra en el historial en el mismo clic
- **Causa:** en Material 22.2.1, `openedStart` se emite de forma diferida.
- **Cambio:** el botón «Abrir menú» llama a `cambioCajon(true)` antes de `toggle()`, y el registro es idempotente.
- **Efecto:** pulsar Atrás enseguida cierra el menú y ya no vuelve al selector de barberías.

### V2-DA-117 · Aviso de privacidad real
- **Contenido:** sustituye al aviso de ejemplo. Recoge al responsable y su contacto, los datos tratados y su finalidad, quién accede, el alojamiento (Microsoft Azure, Estados Unidos), la cookie técnica de sesión, la conservación y los derechos ARCO (Ley 29733).
- **Pie de página:** «BarberTurno · Reservas y turnos para barberías».
- **Asistente de IA:** ya no menciona Huamanga.

## 3. Reparto y revisiones
| Trabajo | Implementó | Revisión |
|---|---|---|
| Backend (V2-DA-114/115, texto de la IA, pruebas de perfiles) | Codex 164 (`f18bc9e`, `a437ab4`, `f0672c9`) | Coordinador y **Gemini g137: APROBADO** (tarea crítica) |
| Frontend (V2-DA-116/117) | Copilot c001 (`2230981`, sin ejecutar órdenes) | Coordinador: ejecutó las pruebas y comprobó que las dos pruebas nuevas fallan con el código anterior |

## 4. Carga automática de ejemplos
- **Producción:** no carga ejemplos.
  - Los cargadores `demo/*` llevan `@Profile("demo & !prod")`.
  - `ConfiguracionProduccion` impide arrancar `prod` junto con `dev`, `test` o `demo`.
  - Azure solo activa `prod`.
- **Prueba:** `PerfilesDemoTest` lo comprueba.
- **Dev y E2E:** cargan la demostración solo si se pide de forma explícita el perfil `demo`.

## 5. WhatsApp con Twilio
- **Canal disponible:** solo el **Sandbox** de Twilio, con un perfil `twilio` que producción no activa. En producción el procesador y la salida de WhatsApp están **apagados**.
- **Respuestas del bot:** el webhook valida la firma `X-Twilio-Signature`. Las respuestas llegan por TwiML dentro de la misma conversación: reservar, consultar, reprogramar y cancelar desde el chat.
- **Avisos proactivos:** el cliente **no** recibe aviso cuando la barbería confirma, reprograma o cancela desde la web.
  - El catálogo de plantillas existe (`PlantillaUtility`), pero no hay código que las envíe.
  - Pertenece al piloto V2-14…V2-17, sin autorizar.
  - Con el Sandbox y remitentes BSUID, además, Twilio rechaza los envíos REST (§10 y §13 del documento 20).
- **Conclusión:** **WhatsApp no está listo para producción.** Hace falta:
  1. un remitente de WhatsApp de pago (Twilio o Meta), lo que supone contratar un servicio;
  2. plantillas aprobadas;
  3. autorizar y hacer V2-14.

## 6. Pruebas antes de integrar
| Prueba | Resultado |
|---|---|
| Backend `verify` completo (Codex) | 4028 pruebas, 0 fallos, 0 errores y 0 omitidas; JaCoCo y Javadoc sin avisos |
| Frontend: lint, Prettier, unitarias y build (coordinador) | Correctos; **654/654** |
| E2E de la PWA | **30/30** |
| CP-5 repetida 10 veces en `chromium-360` | **10/10** (antes fallaba 7 de cada 10) |
| E2E de regresión | 86/88: el test 20 agotó el tiempo en `firefox-1440`. Repetición: ver §7 |

## 7. Integración, despliegue y limpieza (18/11/2026)
- **Integración.** El PR #3 se integró en `main` como `bec6816`, con la CI en verde (backend y frontend).
- **E2E test 20.** El test 20 (alta de barbería desde la plataforma) falló 2 de 12 veces en la rama y 0 de 12 en `main`. Las instantáneas muestran dos carreras del propio test:
  - en Firefox, el diálogo recupera el foco y el correo acaba escrito en «Nombre»;
  - en Chromium, el cuerpo del POST se pierde al cerrarse el diálogo, porque se recorre el historial.

  En ambos casos la barbería se crea bien. Queda como fragilidad de la E2E, que no está en la CI.
- **Respaldo previo.** Está en `.local/respaldo-v2-real-20261118/`, fuera de Git:
  - volcado del esquema;
  - datos por negocio con RLS;
  - ajustes de App Service.

  La auditoría dio 2 negocios demo y 10 cuentas `@ejemplo.test`, **0 cuentas reales**. Flyway V2 estaba en 118 y la V1 (`barberturno_prod`) intacta. Además quedan las copias automáticas de Azure de 7 días.
- **Ventana de mantenimiento.** De **10:18:52 a 10:26:04** (hora de Lima), con la aplicación detenida:
  1. **Limpieza:** primero en modo `ensayo` (`ROLLBACK`, verificado sin cambios) y después en `aplicar`. Resultado: 0 negocios y 1 usuario, el administrador con el nombre «Administración». Flyway sigue en 118.
  2. **JAR:** `bec6816` (el mismo artefacto probado en las E2E), subido por la API VFS de Kudu. Se evita `az webapp deploy`, que el 18/11 dio 504.
  3. **Ajustes:**
     - se elimina `BARBERTURNO_TENANCY_NEGOCIOINICIALSLUG`;
     - `BT_ADMIN_NOMBRE=Administración`;
     - el texto de privacidad de WhatsApp pasa a ser el definitivo y el enlace apunta a `/privacidad` de la aplicación.
- **Verificación posterior.** `verificar-v2-real.ps1` pasa todas las comprobaciones críticas:
  - salud y redirección de HTTP a HTTPS;
  - PWA (manifest, service worker y `ngsw.json` sin `/api`);
  - `assetlinks` de distribución;
  - ningún texto académico en los 77 JS;
  - el aviso real, comprobado además renderizando `/privacidad`;
  - 401 y 403 sin sesión ni CSRF;
  - el webhook de Twilio no está expuesto;
  - el administrador entra y es ADMIN_PLATAFORMA;
  - 0 barberías;
  - las cuentas y los negocios demo dan 401 y 404.

  La regla temporal del cortafuegos se retiró después.
- **APK** (PR #4, `f2af695`). Bubblewrap 1.27 lee `appVersion`, no `appVersionName`, y la APK salía sin versión. Se regeneró con la clave de distribución: versión 1.0.0 y la misma huella `3F:D9:…:23:83`. La APK anterior queda en `D:Androidarberturno-twa-dist-anterior-20261118`.

## 8. WhatsApp: prueba real en local con Twilio Sandbox (responsable, 18/11/2026)
**Entorno:** la aplicación en este equipo con los perfiles `dev,demo,twilio` y la base `barberturno_v2_test`, expuesta solo en `/api/twilio/*` mediante ngrok y un proxy. **No es Azure.**

**Funcionó:**
- reserva BT-105 y su reprogramación;
- reserva BT-106 y su cancelación, comprobadas en la base: CANCELADA;
- consentimiento con el texto definitivo;
- rechazo de las firmas inválidas (401);
- el túnel no expone otras rutas (404).

**Defectos comprobados con la base y los registros.** Ningún MessageSid se procesó dos veces y no hubo reintentos de Twilio. Evidencias fuera de Git, en `.local/coordinacion/evidencias-whatsapp-20261118/`.
1. **Triple respuesta a un solo «menu».** La conversación estaba en modo HUMANO y, al volver, el código enviaba el aviso de retorno dos veces (texto e interactivo) más el menú de bienvenida.
2. **Selecciones rápidas aplicadas a otro menú.** Varios «1» seguidos se interpretaban contra el menú guardado al procesarse, no contra el que veía el cliente, y así se llegó a una reserva no deseada.
3. **RN-07 sin explicación.** BT-105, a menos de 2 h, solo ofrecía «1. Menú».
4. **«del 1 al 1».** Ese texto aparecía cuando el menú tenía una sola opción.

**Mejoras pedidas por el responsable:**
- datos completos en los resúmenes;
- acciones sobre la cita recién creada;
- «Sí, cancelar» / «Mantener cita»;
- barbería identificada;
- palabras clave coherentes;
- derivación veraz.

Las implementó Codex en 165/165b/165c (`b110b2f`, `60d1ba1`, `df2122d`, `1083596`). El coordinador ajustó las pruebas (`af20b6c`).

**Revisiones:**
- **Gemini g138:** dos hallazgos válidos, uno alto y otro medio, más tres menores. Se devolvieron a Codex.
- **Gemini g139b:** los da todos por corregidos. Su único hallazgo nuevo es un falso positivo, porque leyó `main` en lugar de la rama.

`verify` completo: **4083/4083**, sin fallos (`docs/pruebas/v2-real-165.md`).

**Integración y validación (19/11/2026):**
- [PR #5](https://github.com/sohnny-lima/BarberTurno/pull/5): CI en verde, con el backend en 10 min.
- Integrado en `main` con el merge `740ee71`.
- Desplegado en Azure: `verificar-v2-real.ps1` pasa las 20 comprobaciones críticas.
- **El responsable validó con su teléfono los mensajes corregidos** en el entorno local, con Twilio Sandbox, sin incidencias: retorno del modo humano, ráfagas, resúmenes, cancelación, RN-07 y palabras clave.

**Alcance de la validación:** se hizo en el entorno local con el Sandbox. No se probó contra Azure, donde WhatsApp sigue apagado (§9).

### V2-DA-118 · «ayuda», «menú» y «volver» llevan al menú principal
«ayuda» deja de derivar a una persona: el responsable pidió que «menu», «menú», «volver» y «ayuda» sean coherentes. Para hablar con el equipo siguen «hablar con alguien», «persona», «humano», «asesor», «agente» y el botón del menú. Si hay una propuesta abierta, se descarta con aviso.

### V2-DA-119 · Una selección solo vale para el menú que el cliente pudo ver
- **Cuándo se publica el menú de Twilio:** cuando su TwiML se entrega de verdad, no al encolarlo.
- **Selecciones que no se ejecutan:** las que llegan con la respuesta anterior aún pendiente, o dentro de una ventana mínima de reacción tras publicar el menú. En su lugar se envía «Recibí varios mensajes seguidos; para evitar errores, elige de nuevo:» seguido del menú vigente.
- **Reintentos:** un reintento con el mismo MessageSid devuelve la misma traducción.
- **Parámetros:**
  - la marca de respuesta pendiente caduca a los 20 s y se libera al entregarse cualquier respuesta, también una sin opciones;
  - la ventana de reacción es `barberturno.whatsapp.twilio.reaccion-minima`, de 1,5 s por defecto (entre 0,5 y 5 s).

### V2-DA-120 · BCrypt de coste 4 solo en el perfil de pruebas
- **Cambio:** `barberturno.seguridad.bcrypt-coste` vale 12 por defecto y está fijado a 12 de forma explícita en `prod` (RNF-03), con una prueba que lo comprueba. El perfil `test` usa 4.
- **Motivo, medido:** 513 ms frente a 2 ms por cada cifrado y comprobación. La suite pasa de 43 a 28 min en las mismas clases.
- **Efecto en los datos:** ninguno. Cada hash guarda su propio coste, así que las contraseñas existentes siguen siendo válidas.

## 9. Estado de WhatsApp para producción
- **Producción (Azure):** WhatsApp está **apagado** (procesador y salida en `false`) y sin el perfil `twilio`. No se ha probado contra Azure.
- **Para probarlo en Azure con el Sandbox** (decisión del responsable):
  1. dar de alta una barbería real con servicios y personal;
  2. cargar las credenciales del Sandbox en App Service y añadir el perfil `twilio`;
  3. activar el procesador y la salida;
  4. apuntar el webhook del Sandbox a la URL de Azure en la consola de Twilio.

  Un resultado correcto seguiría sin confirmar el uso en producción, porque depende del Sandbox: la membresía caduca a las 72 h y los remitentes BSUID rechazan los envíos REST.
- **Para producción real hace falta:**
  1. un remitente de WhatsApp de pago, de Twilio o de Meta (contratación);
  2. plantillas aprobadas;
  3. los avisos proactivos al confirmar, reprogramar o cancelar desde la web, que corresponden a V2-14 y no están autorizados.
