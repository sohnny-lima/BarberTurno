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

## 7. Integración, despliegue y limpieza
(Se completa al cerrar.)
