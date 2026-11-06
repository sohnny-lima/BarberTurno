# Evidencia · V2-DEMO: demo de pruebas de BarberTurno v2 en Azure (06/11/2026)

Contrato y decisiones: [`../19-v2-demo-azure.md`](../19-v2-demo-azure.md). Scripts: `tools/demo-azure/` (rama `tarea/V2-DEMO-provision`).

## 1. Qué está publicado
| Elemento | Valor |
|---|---|
| URL | **https://barberturno-v2-demo-202611.azurewebsites.net** |
| Artefacto | `barberturno-0.0.1-SNAPSHOT.jar`, SHA-256 `39133f582d7aa299a4c934cb4f828574e781d06431c00d19a2208e836e4a3828`, compilado el 05/11/2026 22:35 desde `115eec7`. El código de la aplicación (`backend/`, `frontend/src/`) es idéntico al de `2f76afb` (V2-18); `115eec7` solo cambia el servidor de pruebas e2e |
| Despliegue | `az webapp deploy --type jar` (OneDeploy, estado 4 = correcto), 06/11/2026 09:39 UTC |
| Perfil | `prod` (sin `demo`, sin reloj fijo); Meta e IA en `false`; texto de privacidad «BORRADOR (demo; pendiente de P2-06)…» |
| Datos | Ficticios (`*@ejemplo.test`), dos barberías: `barberturno` («BarberTurno») y `sur` («Barbería Sur»), cargados una vez desde el equipo del coordinador con el mismo jar en `dev,demo` (19 migraciones, hasta V117) |

## 2. Recursos usados (todos sin coste nuevo)
| Recurso | Nombre | Notas |
|---|---|---|
| Grupo | `rg-barberturno-v2-demo` (westus) | Etiquetas `proyecto=barberturno`, `entorno=demo-v2`, `datos=ficticios` |
| Plan | `plan-barberturno-v2-demo` | Linux **F1** (gratuito: 1 GB, 60 min de CPU al día, sin *Always On*) |
| App | `barberturno-v2-demo-202611` | Java 21, HTTPS only, TLS ≥ 1.2, FTP desactivado, *health check* `/actuator/health`, `WEBSITES_CONTAINER_START_TIME_LIMIT=600`, registro del contenedor en el sistema de archivos |
| Base | `barberturno_v2_demo` en el servidor existente `pg-barberturno-202610` | Roles `bt_v2_demo` (dueño, Flyway) y `bt_v2_demo_app` (ejecución, NOBYPASSRLS); `CONNECT` revocado a `PUBLIC`; esquema `public` cedido a `bt_v2_demo` |
| Cortafuegos | 43 reglas `demo-v2-*` | IP de salida posibles de la app; las 14 reglas de v1, intactas |

## 3. Comprobaciones
| CP | Resultado |
|---|---|
| D-1 | `/actuator/health` → 200 `UP`. HTTP → 301 a HTTPS. HSTS, CSP, `X-Frame-Options: DENY`, `nosniff` y `Referrer-Policy`. `/actuator`, `/actuator/env`, `/actuator/info`, `/v3/api-docs`, `/swagger-ui.html` → 401. `/api/demo` y `/api/pruebas/vaciar` → 404 |
| D-2 | Pruebas de aceptación 02: XSRF `Secure`/`SameSite=Strict`, sesión `HttpOnly`/`Secure`, POST sin token → 403 (360 y 1440) |
| D-3, D-4 | Pruebas de aceptación de Gemini contra la URL publicada: ver §4 |
| D-5 | `assetlinks.json` → 200 con `pe.barberturno.prueba.twa` y la huella `E8:16:33:…:BE:CF`. La APK de prueba (versionCode 2) está firmada con esa misma huella (`apksigner`) y su `assetStatements` apunta al hostname de la demo. **En el emulador (Android 16, Chrome 141) la TWA se abrió sin barra de dirección**: actividad `TranslucentCustomTabActivity`, árbol de la interfaz sin `url_bar`/`location_bar` y barra de estado en el color del tema ([captura](v2-demo-twa-emulador.png)) |
| D-6 | v1 sin cambios: las 10 variables de `barberturno-web-202610` tienen la misma huella antes y después; última modificación 01/11/2026; salud 200. v1 se conecta con `barberturno_app` (no `btadmin`). El servidor siguió en `Ready` antes y después del restablecimiento de `btadmin` |

## 4. Pruebas de aceptación (Playwright, Chromium 360×800 y 1440×900)
Autor: Gemini (g114 → g115 → g116). Ejecutor: Claude. Revisor: Codex.
- Pase 1 (g114): 360 → 01–10 correctas, 11 falla (Background Sync desactivado en Chromium sin ventana); 1440 → 01 correcta, 02 agota el tiempo (`networkidle`, máquina cargada por el emulador). Defectos de las pruebas.
- Pase 2 (g115): 01 y 02 correctas en ambos; 03 falla por una carrera en `iniciarSesion` (`isVisible()` sin espera ante el selector de barberías). Defecto de la prueba.
- Pase 3 (g116): 01–04 correctas en ambos; 05 falla porque la prueba pulsaba «Siguiente» (`isVisible()`), y la app avanza sola al paso 3 al elegir franja. No se creó ninguna reserva. Defecto de la prueba.
- **Pase 4 (g118): 22/22 correctas** (01–11 en 360×800 y 1440×900, 3,4 min). Cubre:
  - salud y superficie pública;
  - cookies y CSRF;
  - acceso;
  - selector entre las dos barberías;
  - reserva, Mis citas y cancelación;
  - agenda;
  - avisos con Atrás;
  - bandeja;
  - PWA instalable (CDP);
  - Cache Storage sin `/api` ni datos de la cuenta, y Atrás tras cerrar sesión;
  - sin conexión, con la escritura rechazada y ningún envío automático al reconectar.
- Revisión de Codex (149): 9 hallazgos sobre las pruebas (2 altos: la 05 podía dejar una reserva si fallaba una aserción y la 11 no veía peticiones del *service worker*; 7 medios: comprobaciones débiles en 04, 06, 08, 09 y 10, y una bifurcación en CP-8). Corregidos por Gemini (g120).
- Pase 5 (g120): la 04 reescrita falla. Para el propietario, `/reservar` es la «Reserva asistida», y en 360 el menú lateral tapaba el selector. Defecto de la prueba, corregido en g121.
- **Pase 6 (g121): 22/22 correctas** (3,9 min) con las pruebas reforzadas. `movil.spec.ts` (local, con la base de pruebas y su cerrojo): 20/20.
- Verificaciones de Codex:
  - 150b: 6 de 9 hallazgos resueltos; pendientes la limpieza de la 05 y de la 11 y un dato propio de Sur en la 04, corregidos en g122.
  - 151: 04 y 05 subsanadas; la 11 aún podía dejar una reserva sin cancelar en una ruta de error, corregido en g123 y revisado por Claude.
- **Pase 7 (g122) y pase 8 (g123): 22/22 correctas** (3,8 min cada uno).
- Ninguno de los fallos de los pases 1–5 fue de la aplicación.

## 5. Probar la TWA en un teléfono Android
1. En el equipo, el archivo es `D:\Android\barberturno-twa-demo\app-release-signed.apk`, firmado con la clave **de prueba**. Pásalo al teléfono por USB, Drive o correo propio.
2. En el teléfono, ábrelo y permite «Instalar apps desconocidas» para la app con la que lo abres. Play Protect avisará de que es una app desconocida: «Instalar de todos modos».
   - Alternativa con depuración USB: `D:\Android\Sdk\platform-tools\adb.exe install D:\Android\barberturno-twa-demo\app-release-signed.apk`.
3. El teléfono necesita **Chrome actualizado** como navegador compatible.
4. Abre «BarberTurno». El primer arranque de la demo puede tardar hasta 1–2 minutos (plan F1 dormido).
   - **Resultado correcto:** pantalla completa, sin barra de dirección.
   - **Si aparece una barra con la URL:** Chrome no pudo validar Digital Asset Links. Cierra la app, comprueba que https://barberturno-v2-demo-202611.azurewebsites.net/.well-known/assetlinks.json abre en el teléfono, y en Ajustes → Apps → BarberTurno → Almacenamiento borra los datos y vuelve a abrir.
5. Cuentas ficticias: `cliente@ejemplo.test`, `multirol@ejemplo.test`, `carlos@ejemplo.test` y `encargado@ejemplo.test`. Su contraseña común está en `D:\Proyectos\BarberTurno\.local\demo-azure\demo-cuentas.secreto`, fuera de Git. La cuenta de plataforma es `plataforma-demo@ejemplo.test` (`admin-plataforma.secreto`).
6. Desinstalar: Ajustes → Apps → BarberTurno → Desinstalar.

## 6. Límites conocidos
- **F1:** 60 minutos de CPU al día y descarga tras unos 20 minutos sin tráfico. El primer acceso tras la pausa tarda en arrancar. Si se agota la CPU del día, la app responde 403 hasta el día siguiente (UTC).
- **Cortafuegos:** las IP de salida se comparten con otras apps de Azure. El aislamiento lo dan la autenticación SCRAM, `CONNECT` revocado y la RLS.
- TalkBack no se aprueba con estas pruebas. P2-03, P2-05 y P2-06 siguen pendientes.
