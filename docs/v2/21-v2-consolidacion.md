# V2-CONS · Consolidación de la V2 en `main` y versión estable en producción

**Fecha:** 18/11/2026 · **Autorización:** encargo del responsable del 18/11/2026 (integrar la V2 y las correcciones en `main`, publicar, alinear Azure con `main`, preparar la firma de distribución de Android sin publicar en Play, sin recursos nuevos ni datos borrados). **Rama:** `tarea/V2-CONS-consolidacion`. **Coordinación:** Claude (coordinador), Codex (encargo 161, frontend), Gemini (g136, auditoría de cobertura). Copilot no participó: su CLI no está instalado en el equipo (requiere instalación e inicio de sesión del responsable).

## 1. Punto de partida verificado
| Elemento | Estado |
|---|---|
| `main` / `origin/main` | `efd52aa` (V1 + APF3). Es **antecesor** de `origin/v2`: la integración no tiene conflictos. |
| `origin/v2` | `b7f663b` (merge del PR #1, V2-TW, 13/11/2026, CI verde). |
| Producción | `barberturno-web-202610` ejecutaba `3737d04` (⊂ `origin/v2`; solo difieren CI, documentación y una prueba) contra `barberturno_v2_demo`, Flyway 118. |
| Trabajo pendiente fuera de `v2` | `tarea/V2-DIAG-advertencias-java` ⊃ `tarea/V2-DEMO-azure` ⊃ `tarea/V2-DEMO-provision` y `tarea/V2-DEMO-aceptacion` (scripts de la demo, TWA, aceptación E2E y diagnóstico Java). Integrado en `97bf5b1` sin conflictos y sin código de producción. |
| Base V1 | `barberturno_prod` no se toca (punto de reversión de V1). |

## 2. Decisiones
### V2-DA-110 · `main` es la rama estable con la V2
`main` avanza a la V2 consolidada mediante un merge `--no-ff` revisado y con CI verde. La V1 entregada sigue identificada por `efd52aa`/`3bcd5d0` en la historia de `main` y su base `barberturno_prod` queda intacta. `v2` se conserva como rama de trabajo y se iguala a `main` tras la integración.

### V2-DA-111 · El 404 del vínculo profesional es silencioso
`GET|POST|DELETE /api/n/{slug}/whatsapp/vinculo-profesional` responde 404 cuando WhatsApp está apagado en el despliegue (V2-DA-109) o cuando quien abre la agenda no tiene perfil profesional activo en ese negocio. Es un estado esperado: el panel se oculta y el error se marca como presentado, de modo que el interceptor global no muestra «No se encontró el perfil profesional». Los demás errores se muestran junto a la acción, sin snackbar duplicado. **Causa del aviso observado por el responsable al entrar como barbero en Sur:** producción tiene `BARBERTURNO_WHATSAPP_PROCESADOR_HABILITADO=false`; el aviso aparecía para cualquier profesional, no por un defecto de datos de Sur.

### V2-DA-112 · Paquete y clave de distribución de Android
La app Android (TWA) de distribución usa el paquete **`pe.barberturno.app`** (el de prueba `pe.barberturno.prueba.twa` lleva «prueba» en un identificador que en Play no se puede cambiar). Se firma con una clave propia (PKCS12, RSA 4096, validez 10 000 días) guardada fuera de Git en `.local/android/distribucion/`, con acceso restringido al usuario; la contraseña es aleatoria, se guarda junto a la clave y las herramientas la leen de una variable de entorno. `android/generar.ps1 -Clave distribucion|prueba` genera la APK y el AAB firmados. Producción publica en `/.well-known/assetlinks.json` el paquete y la huella de distribución (`BT_TWA_PAQUETE`, `BT_TWA_HUELLAS`); la APK de prueba deja de validarse (se abriría con barra de dirección) y debe desinstalarse. Si se publica en Play con *Play App Signing*, habrá que **añadir** la huella de la clave de firma de Google a `BT_TWA_HUELLAS` (admite varias).

### V2-DA-113 · Textos de marca multinegocio
El pie y el aviso de privacidad dejan de hablar de «una sede en Huamanga»: el producto es una plataforma para varias barberías. Web, PWA y APK usan el nombre «BarberTurno» y las tijeras verdes (también en `favicon.ico`, que era el icono genérico de Angular).

## 3. Cambios
| Commit | Autor | Contenido |
|---|---|---|
| `97bf5b1` | Claude | Merge del trabajo pendiente de la demo, TWA, aceptación y diagnóstico Java. |
| `02fb81b` | Codex 161 | 404 silencioso del vínculo profesional (V2-DA-111) y sus pruebas. |
| `0daa443` | Codex 161 | Pie multinegocio, `favicon.ico` de tijeras (16/32/48 px) y descripción del manifest. |
| `b824461` | Codex 161 | Formato Prettier de tres archivos de la demo (el CI lo exige). |
| `a44519c` | Codex 161 | Evidencia `pruebas/v2-cons-161.md`. |
| `757865a`, `3417551`, `bd7993b` | Claude | Aviso de privacidad multinegocio, `android/` de distribución, este documento, matriz de roles (§4) y registro de cierre. |
| (siguiente) | Claude | Correcciones de la revisión de Codex 162 a `android/generar.ps1`: carpeta de claves restringida antes de crear secretos y ACL exclusiva exigida también al reutilizarlos; `-Clave prueba` adapta su propia copia del manifiesto; verificación del AAB con `jarsigner -verify -strict` contra el almacén y el alias. |

## 4. Revisión de permisos y aislamiento (g136 + verificación del coordinador)
Gemini construyó la matriz comportamiento → pruebas → brecha a partir de un extracto (reglas HTTP, mapeos de controladores y nombres de 1 455 pruebas de backend y de las de frontend). Áreas: permisos por rol, aislamiento entre barberías (RLS, 404 ajeno), ciclo de reserva y concurrencia, avisos, actualización de la agenda y usuario multirol. **Veredicto: sin brechas altas.** Verificación de sus hallazgos en el código:

| # | Hallazgo de Gemini | Verificación | Resultado |
|---|---|---|---|
| 1 | Sin prueba de 403 a ENCARGADO en `GET /api/configuracion` | `ConfiguracionNegocioService.consultar` exige propietario; `ConfiguracionNegocioIT.soloPropietario_…` recorre `gestorLocal` (ENCARGADO), barbero, cliente y plataforma con GET y PUT → 403 | Falso positivo |
| 2 | Sin prueba de 403 a ENCARGADO en `PATCH /api/equipo/{id}/rol` | `EquipoService.cambiarRol` exige PROPIETARIO; `EquipoIT` línea 84 lo comprueba con `gestorLocal` | Falso positivo |
| 3 | `05-roles-permisos.md` §2.2 asigna el canal de WhatsApp al propietario, pero el código lo restringe a `ADMIN_PLATAFORMA` | V2-DA-56 (28/10/2026) decidió el alta manual por la plataforma; la matriz no se actualizó | Corregido en `05` (documental) |
| 4 | `AnonimizacionFichasController` no recibe el actor | `AnonimizacionFichas.anonimizar` exige propietario con el contexto revalidado; probado en `BandejaWhatsAppIT` | Estilo; sin cambio (freeze) |

## 5. Flujo comprobado en un Android real (responsable, 18/11/2026)
**Comprobado manualmente por el responsable** con la APK de prueba (`pe.barberturno.prueba.twa`, host de producción) y la PWA de producción: creó la cita **BT-118** en **Barbería Sur** desde la APK; la cita apareció en la PWA; la confirmó; el celular recibió el aviso y la cita mostró el estado **Confirmada**. Cubre creación, visibilidad entre canales, confirmación, aviso en la app y actualización del estado. La reprogramación y la cancelación en el teléfono no forman parte de esta comprobación (cubiertas por `ReprogramacionIT`, `CancelacionIT` y E2E).

## 6. Producción: auditoría (solo lectura, 18/11/2026)
| Aspecto | Estado | Acción |
|---|---|---|
| HTTPS | `httpsOnly`, TLS ≥ 1.2 (sitio y SCM), FTPS obligatorio, credenciales básicas de publicación desactivadas, `BT_COOKIE_SECURE=true` | — |
| Salud | *Health check* en `/actuator/health`, Always On, `WEBSITE_HEALTHCHECK_MAXPINGFAILURES=10` | — |
| Registros | Estaban apagados. **Activado** el registro del contenedor en el sistema de archivos al desplegar (3 días, 100 MB; sin coste adicional) | — |
| Base de datos | PostgreSQL 18 B1ms, TLS obligatorio, copia automática **7 días** (restauración a un instante), sin redundancia geográfica ni alta disponibilidad, crecimiento automático apagado (32 GB) | Ampliar retención o geo: decisión de coste del responsable |
| Cortafuegos | 57 reglas por IP (salidas de App Service y del responsable), ninguna abierta a todo Internet | — |
| Secretos | En la configuración de App Service (JWT, BD, Flyway, administrador); fuera de Git | Rotar las contraseñas de las cuentas de prueba si se comparten |
| Migraciones | Flyway 118; V2-CONS no añade migraciones | Reversión = JAR anterior |
| Dominio | El nombre corto `barberturno-web-202610.azurewebsites.net` **no existe** (NXDOMAIN); la URL válida es `https://barberturno-web-202610-bvbacecef3hpcga6.westus-01.azurewebsites.net`. El repositorio ya usa la larga | — |

## 7. Datos de demostración y uso real
Producción sirve la base `barberturno_v2_demo`: todas sus cuentas son ficticias (`@ejemplo.test`) y sus barberías (`barberturno`, `sur`) son de demostración. Para un uso real faltan (decisiones del responsable, sin cambios en esta tarea):
1. Alta de la barbería y de las cuentas reales (propietario, personal) y retirada o desactivación de las cuentas de prueba; no se borran datos en esta tarea.
2. Aviso de privacidad real (responsable del tratamiento, finalidades, plazos) y enlace `BARBERTURNO_WHATSAPP_CONVERSACION_PRIVACIDAD_ENLACE` (hoy `https://barberturno.ejemplo.test/privacidad`, de relleno).
3. WhatsApp (Twilio o Meta) e IA: apagados; requieren cuentas, plantillas y costes.
4. Dominio propio y certificado (opcional); hoy, `*.azurewebsites.net`.
5. Publicación en Play: cuenta de desarrollador, ficha, política de privacidad y huella de *Play App Signing* en `BT_TWA_HUELLAS`.
6. Copia de seguridad de la clave de distribución fuera del equipo (si se pierde, no se puede actualizar la app instalada).

## 8. Pruebas, CI, integración y despliegue
### 8.1 Pruebas
| Prueba | Dónde | Resultado |
|---|---|---|
| Frontend: lint, Prettier, unitarias, build | Codex 161 y Claude (tras `757865a`) | Correcto; **652/652** (56 archivos); build 509,09 kB |
| E2E de regresión (`recorrido` + `v2-11-bandeja`, Chromium y Firefox, 1440 y 360 px) contra `barberturno_v2_test` | Claude, JAR de `bd7993b` | **88/88** (13,7 min) |
| E2E de la PWA (`playwright.pwa.config.ts`) | Claude | **29/30**: falla CP-5 en `chromium-360` (§8.2) |
| Backend `verify` completo (PostgreSQL real, JaCoCo, Javadoc) | CI de GitHub sobre `bd7993b` (ejecuciones 44251192675 y 44251217734) | **4013 pruebas, 0 fallos, 0 errores**, BUILD SUCCESS; frontend de CI correcto |
| Revisión independiente | Codex 162 (cambios de Claude) | 3 hallazgos en `android/generar.ps1` (1 alta, 2 medias), corregidos |
| APK y AAB de distribución con el generador corregido | Claude | Firmados; `apksigner verify` y `jarsigner -verify -strict` correctos; huella `3F:D9:3D:82:…:6E:23:83` |

### 8.2 Defecto previo encontrado: CP-5 (Atrás con el cajón)
`e2e/pwa/nucleo.spec.ts` CP-5 falla **7 de 10** veces en `chromium-360` con `--repeat-each=10`, **igual en `v2` sin cambios** (`c22dc32`, 7/10): no lo introduce V2-CONS y ya está en producción. Tras cerrar los avisos con Atrás y abrir el menú lateral, otro Atrás navega a `/negocios` en lugar de cerrar el menú. Diagnóstico de la causa: §8.3. No bloquea la versión (no empeora con ella), pero queda como pendiente real.

### 8.3 Diagnóstico de CP-5 (Codex 163, solo lectura)
Angular Material 22.2.1 emite `openedStart` del cajón con un `setTimeout`: el cajón ya se ve abierto, pero `Shell.cambioCajon()` aún no ha registrado su entrada en el historial (`HistorialSuperposiciones.abrir()`). Un Atrás en esa ventana recorre el historial real hasta la ruta anterior (`/negocios`) porque no hay entrada del cajón que consumir. Playwright pulsa Atrás de inmediato; para una persona la ventana es de milisegundos, así que el impacto real es muy bajo. **Corrección propuesta (no aplicada; pendiente):** registrar la entrada en el mismo clic del botón, antes de `menu.toggle()`, y hacer idempotente el registro para que el `openedStart` diferido no lo duplique; prueba unitaria determinista en `shell.spec.ts` con temporizadores controlados. Riesgo bajo a moderado (cierre por Atrás, botón, Escape, fondo y navegación desde el cajón).

### 8.4 Integración y despliegue
- **Integración:** PR #2 con CI verde en `dd4e3ce` (backend **4013/0**, 18,7 min; frontend correcto) → merge `--no-ff` **`7729d3f`** en `main` (árbol idéntico al de `dd4e3ce`); `v2` avanzada a `7729d3f`.
- **Respaldo previo (18/11, 01:4x):** volcado lógico del esquema y de los datos de cada negocio (`--enable-row-security`, RLS forzada) de `barberturno_v2_demo`, guardado fuera de Git; ajustes completos de App Service guardados; JAR anterior `3737d04` (SHA-256 `02727FD1…`) disponible. Copias automáticas de Azure: 7 días.
- **Despliegue:** JAR de `7729d3f` con frontend (SHA-256 `E4163DA2…4BA2F0`); ajustes `BT_TWA_PAQUETE=pe.barberturno.app` y `BT_TWA_HUELLAS` = huella de distribución.
- **Incidencia (≈29 min sin servicio, 02:23–02:52 hora de Lima):** `az webapp deploy` devolvió 504 de la pasarela de Kudu; el reinicio arrancó con un `app.jar` inconsistente (`ClassNotFoundException: org.springframework.boot.SpringApplication`) y la plataforma detuvo el contenedor; el despliegue de Kudu terminó en error (estado 3). Se comprobó que el JAR local estaba íntegro, se subió con `PUT /api/vfs/site/wwwroot/app.jar` de Kudu (204, tamaño exacto, 130 s) y se reinició: salud **UP** a las 02:52. Sin pérdida de datos. **Lección:** para este JAR (~140 MB) en B1, subir por la API de archivos de Kudu y reiniciar, en lugar de `az webapp deploy` síncrono.
- **Verificación posterior:** todas las comprobaciones críticas pasan (salud, HTTPS, PWA, inicio de sesión con cookie `Secure`, agenda, bandeja, 403 al cliente, 404 del vínculo con WhatsApp apagado, webhook de Twilio no expuesto, CSRF, pie nuevo, `favicon` de la marca y `assetlinks` de distribución). Datos: Flyway 118, 10 cuentas, 18 reservas en `barberturno`, **BT-118 CONFIRMADA** en `sur`. V1 `barberturno_prod`: Flyway 1, intacta.
- **Vuelta atrás preparada:** restaurar los ajustes de la TWA anteriores y el JAR `3737d04` (sin migraciones que revertir); a la V1, con su procedimiento del 11/11/2026. La regla temporal del cortafuegos usada para el respaldo se retiró.

## 9. Cierre
| Comprobado | Cómo |
|---|---|
| `main` = V2 estable, publicada | `7729d3f` en `origin/main`, CI verde en su árbol |
| Producción = `main` | Verificación HTTPS completa tras el despliegue |
| Aviso «No se encontró el perfil profesional» corregido | Prueba unitaria (404 sin snackbar); 404 del vínculo en producción confirmado como estado esperado |
| Pie y privacidad multinegocio; favicon de la marca | Producción sirve el bundle y el favicon nuevos |
| Permisos y aislamiento | g136 + verificación del coordinador; 4013 pruebas de backend; E2E 88/88 |
| Flujo BT-118 en Android real | Comprobado por el responsable; la base confirma BT-118 CONFIRMADA en Barbería Sur |
| Asociación de dominio para `pe.barberturno.app` | `/.well-known/assetlinks.json` en producción con el paquete y la huella de la clave de distribución |

| Pendiente de prueba manual o de decisión | Motivo |
|---|---|
| Instalar la **APK de distribución** en el teléfono y comprobar pantalla completa (sin barra de dirección) | La validación de Digital Asset Links solo se ve en el dispositivo; desinstalar antes la APK de prueba (`pe.barberturno.prueba.twa`, ahora sin asociación) |
| Reprogramar y cancelar desde el teléfono | Cubiertas por pruebas automáticas, no probadas a mano en el Android |
| CP-5 (Atrás inmediato con el cajón) | Defecto previo de impacto muy bajo; corrección propuesta en §8.3 |
| Copia de la clave de distribución fuera del equipo | Si se pierde, no se puede actualizar la app |
| Uso real (§7), retención de copias o redundancia geográfica, Play Store | Decisiones y costes del responsable |
| Copilot | No instalado en el equipo; requiere instalación e inicio de sesión del responsable |
