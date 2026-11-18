# V2-DEMO · Demo de pruebas de BarberTurno v2 en Azure

**Autorización del responsable (05/11/2026, noche):** publicar ya BarberTurno v2 como **demo de pruebas con datos ficticios** en Azure, con su aplicación y su base aisladas, dentro de los recursos disponibles y sin cargos nuevos. Hostname HTTPS de Azure, sin dominio ni certificado comprado. Se despliega el artefacto del commit verificado **sin integrar en `v2` ni publicar ramas**. Meta y la IA real, desactivadas; los textos de privacidad siguen marcados como borradores (P2-06). Quedan intactos `main`, `v2`, los entregables, la app académica de Azure y todas las bases de v1. No se intercambian *slots*, no se sobrescribe la app existente y no se migra su base.

Ramas (desde `2f76afb`, integración V2-18):
- **`tarea/V2-DEMO-azure`**: integración, contrato y TWA (Claude);
- **`tarea/V2-DEMO-provision`**: scripts de aprovisionamiento (Codex);
- **`tarea/V2-DEMO-aceptacion`**: pruebas de aceptación sobre la URL publicada (Gemini).

## 1. Inventario previo (solo lectura, 05/11/2026)
| Recurso | Estado observado |
|---|---|
| Suscripción | Azure for Students, `Enabled`, rol **Owner** de la cuenta del responsable |
| `rg-barberturno-prod` (westus) | App v1 `barberturno-web-202610` en el plan **B1** `plan-barberturno-prod` (1 sitio). PostgreSQL Flexible `pg-barberturno-202610` (18, B1ms, 32 GB sin crecimiento, copias 7 días, sin HA, solo autenticación por contraseña, `azure.extensions = BTREE_GIST`), base `barberturno_prod`, 14 reglas de cortafuegos |
| Carga del plan B1 (7 días) | Memoria **76 % de media, 93 % de pico**; CPU 15 % de media, picos del 99 % |

## 2. Decisiones
| ID | Decisión | Motivo |
|---|---|---|
| **V2-DA-97** | La demo va en un **plan Linux F1 (gratuito) propio**, `plan-barberturno-v2-demo`, dentro de su propio grupo `rg-barberturno-v2-demo`; **no** en el plan B1 compartido | El B1 ya va al 76–93 % de memoria: una segunda JVM pondría en riesgo la app académica durante la medición de RNF-02 de noviembre. Ampliar el plan es un cargo nuevo (requiere aprobación). F1 cuesta 0 a cambio de límites: 1 GB, **60 min de CPU al día**, sin *Always On* (la app se descarga tras ~20 min sin tráfico y el primer acceso tarda en arrancar) |
| **V2-DA-98** | Base **`barberturno_v2_demo`** en el servidor PostgreSQL existente (gratuito 12 meses), con un rol de migraciones dueño de la base (`bt_v2_demo`, solo Flyway) y uno de ejecución sin propiedad ni BYPASSRLS (`bt_v2_demo_app`). `CONNECT` revocado a `PUBLIC`; los roles de v1 no acceden | Un segundo servidor costaría ≈ 16 USD/mes. La RLS y el aislamiento entre barberías funcionan igual que en local (V2-DA-20) |
| **V2-DA-99** | Datos ficticios de **dos barberías** (`barberturno` y `sur`) cargados **una vez desde el equipo del coordinador** ejecutando el mismo jar con `dev,demo` contra la base de la demo. La app publicada solo usa `prod`, que rechaza `demo` y el reloj fijo | Así no se desactiva ninguna comprobación de producción y los datos se crean con el código de la aplicación (mismas reglas y RLS). Contraseña de las cuentas ficticias: aleatoria, en `.local/demo-azure/` (no en Git ni en el chat) |
| **V2-DA-100** | Meta e IA **apagadas de forma explícita** (`barberturno.ia.habilitada=false`, procesadores de WhatsApp desactivados; sin token ni clave). El texto de privacidad de producción se da por variable y empieza por «BORRADOR (demo; pendiente de P2-06)» | Requisito del responsable; P2-03/05/06 siguen pendientes |
| **V2-DA-101** | Acceso del servidor de base de datos: reglas de cortafuegos `demo-v2-*`, una por cada IP de salida **posible** de la app de la demo (`possibleOutboundIpAddresses`, 43); sin «permitir servicios de Azure». Administración con la contraseña de `btadmin`, que el responsable escribe **oculta** en una consola (`Read-Host -AsSecureString`): no se guarda en disco y solo llega al `psql` hijo por `PGPASSWORD`. **Sin** cambiar la autenticación del servidor. *Actualizada el 06/11/2026:* la contraseña conocida no era válida; el responsable autorizó restablecer **solo** la de `btadmin` (v1 usa `barberturno_app`, también para Flyway). **Límite (revisión de Codex 148):** las IP de salida de App Service se comparten con otras apps de la misma unidad de escalado; las reglas reducen la exposición pero **no** aíslan la demo por red. El aislamiento lo dan la autenticación SCRAM con claves aleatorias de 32 caracteres, `CONNECT` revocado a `PUBLIC` y la RLS. Las reglas de v1 tienen el mismo límite | Activar Entra en el servidor compartido podría reiniciarlo (no está documentado lo contrario) |
| **V2-DA-102** | TWA de prueba (`pe.barberturno.prueba.twa`, clave de PRUEBA fuera de Git) con host `barberturno-v2-demo-202611.azurewebsites.net`; `assetlinks.json` desde `BT_TWA_PAQUETE`/`BT_TWA_HUELLAS` | Primer hostname HTTPS real para validar Digital Asset Links |

## 3. Recursos
| Recurso | Nombre | Configuración | Coste |
|---|---|---|---|
| Grupo | `rg-barberturno-v2-demo` | westus; etiquetas `proyecto=barberturno`, `entorno=demo-v2`, `datos=ficticios` | 0 |
| Plan | `plan-barberturno-v2-demo` | Linux **F1** (Free) | 0 |
| App | `barberturno-v2-demo-202611` | Java SE 21; HTTPS only; FTP desactivado; TLS ≥ 1.2; HTTP/2; *Health check* `/actuator/health` | 0 |
| Base | `barberturno_v2_demo` en `pg-barberturno-202610` | Roles `bt_v2_demo` y `bt_v2_demo_app` | 0 (dentro de los 32 GB gratuitos) |
| Cortafuegos | `demo-v2-*` en `pg-barberturno-202610` | 43 reglas: IP de salida posibles de la app de la demo | 0 |

URL: **https://barberturno-v2-demo-202611.azurewebsites.net**

## 4. Reparto (un implementador por archivo; nadie aprueba lo suyo)
| Bloque | Autor | Ejecutor | Revisor |
|---|---|---|---|
| Revisión focalizada de lo posterior a Codex 145 | — | — | **Codex 146**: `3541587` aprobado; hallazgo 1 (`movil.spec.ts`, Gemini) y hallazgo 2 (`servidor.mjs`, Claude) |
| Corrección del hallazgo 2 (`115eec7`) | Claude | Claude | Gemini |
| Corrección del hallazgo 1 y pruebas de aceptación (`e2e/demo/**`, `playwright.demo.config.ts`) | Gemini (g114) | Claude | Codex |
| Scripts `tools/demo-azure/**` | Codex (147) | Claude (con la sesión de Azure del responsable) | Gemini |
| Recursos de Azure, despliegue, TWA y documentación | Claude | Claude | Codex o Gemini (lo que no escribieron) |

## 5. Comprobaciones de aceptación (sobre la URL publicada)
| CP | Qué |
|---|---|
| D-1 | Salud, cabeceras de seguridad, redirección HTTP→HTTPS, OpenAPI y *actuator* no públicos, ningún punto de prueba o vaciado |
| D-2 | Cookies `Secure`/`HttpOnly`/`SameSite`, CSRF (POST sin token → 403) |
| D-3 | Acceso, selector de barbería (dos barberías sin restos), reserva y cancelación, agenda, avisos (Atrás), bandeja |
| D-4 | PWA instalable; Cache Storage sin `/api` ni datos personales; sin conexión, sin escrituras encoladas |
| D-5 | `assetlinks.json` con el paquete y la huella de prueba; APK en el emulador: ¿se abre **sin barra de dirección**? (resultado real, sin suponerlo) |
| D-6 | La app v1 y su base sin cambios: mismo despliegue, mismas variables, `barberturno_prod` intacta |

## 6. Detener o retirar solo esta demo
- **Detener** (sin borrar nada): `az webapp stop -g rg-barberturno-v2-demo -n barberturno-v2-demo-202611`. **Reanudar:** `az webapp start …` con los mismos argumentos.
- **Retirar:** `tools/demo-azure/retirar.ps1 -Confirmar -Suscripcion <UUID>`, en una consola interactiva (pide oculta la contraseña de `btadmin`). Borra el grupo `rg-barberturno-v2-demo`, las reglas `demo-v2-*`, la base `barberturno_v2_demo` y los roles `bt_v2_demo`/`bt_v2_demo_app`, y nada más.

## 7. Incidencias del despliegue (06/11/2026) y su corrección
| # | Incidencia | Causa | Corrección |
|---|---|---|---|
| 1 | La app `prod` no arrancaría | `AdminInicialRunner` exige la barbería inicial | `BARBERTURNO_TENANCY_NEGOCIOINICIALSLUG=barberturno` en `configurar-app.ps1` (`1c8fbdb`). **Defecto de v2 a registrar:** la guía de producción de v2 debe exigir esta variable |
| 2 | La contraseña de `btadmin` fue rechazada | No era la vigente | Restablecida solo la de `btadmin` (autorizado); antes y después, el servidor en `Ready` y la configuración de v1 con la misma huella |
| 3 | Flyway: `permission denied for schema public` | En Azure el esquema `public` de una base nueva pertenece a `azure_pg_admin` | `ALTER SCHEMA public OWNER TO bt_v2_demo` en `preparar-base.sql` (`639f4a6`) |
| 4 | La siembra se marcó como fallida | El aviso de Flyway «btree_gist already exists… Error Code: 0» activaba el filtro de errores | Se ignora solo ese aviso (`2d4d14b`); los cargadores son idempotentes |
| 5 | La app no conectaba a PostgreSQL (`Connect timed out`) | Las 25 IP de salida actuales no bastan | Reglas para las 43 IP posibles (`9b93863`). La app se detuvo mientras tanto para no gastar CPU del F1 |
| 6 | Arranque en F1 > 230 s posible | Límite por defecto de App Service | `WEBSITES_CONTAINER_START_TIME_LIMIT=600` en la app de la demo |
