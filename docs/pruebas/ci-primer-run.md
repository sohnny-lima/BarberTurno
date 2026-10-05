# Primera integración continua real (P-04)

- **Fecha:** 05/10/2026 · **Responsable de la verificación:** Claude Code.
- **Repositorio:** https://github.com/sohnny-lima/BarberTurno (público), remoto `origin`, rama `main` con seguimiento de `origin/main`. El responsable creó el repositorio e hizo el primer push de `4c44309`.
- **Workflow:** `.github/workflows/ci.yml` (T-04), en cada `push` y `pull_request`.

## Ejecuciones
| # | Run | Commit | Resultado | Backend | Frontend | Notas |
|---|---|---|---|---|---|---|
| 1 | [37308126689](https://github.com/sohnny-lima/BarberTurno/actions/runs/37308126689) | `4c44309` | **Fallo** | fallo en "Verificar el backend con PostgreSQL real" (405 s) | éxito (46 s) | Los logs de Actions exigen autenticación y `gh` no está instalado: solo era visible "exit code 1". |
| 2 | [37312152089](https://github.com/sohnny-lima/BarberTurno/actions/runs/37312152089) | `6df34b2` | **Fallo** | fallo (333 s) | éxito (30 s) | Nuevo paso de diagnóstico: anotaciones públicas con las líneas `[ERROR]` → 9 fallos solo en `SecurityConfigIT`. |
| 3 | [37313113748](https://github.com/sohnny-lima/BarberTurno/actions/runs/37313113748) | `59d647a` | **Fallo** | fallo | éxito | Anotaciones con el mensaje de cada fallo y el orden real de las clases: `GET /api/auth/sesion` sin cookie `XSRF-TOKEN` y sesión HTTP creada en el health; `SpaForwardIT` se ejecutaba antes que `SecurityConfigIT`. |
| 4 | [37314699514](https://github.com/sohnny-lima/BarberTurno/actions/runs/37314699514) | `d6f5c50` | **Éxito** | éxito (377 s) | éxito (36 s) | Primer CI verde. |

## Pasos comprobados en la ejecución verde (#4)
| Trabajo | Pasos | Resultado |
|---|---|---|
| `backend` (ubuntu-latest, servicio `postgres:18`) | Inicializar contenedores (PostgreSQL 18), descargar el repositorio, preparar **Java 21** (Temurin) con caché de Maven, **`./mvnw -B -ntp verify`** con PostgreSQL real (pruebas, JaCoCo y Javadoc estricto), publicar cobertura y resultados | Todos correctos (el paso de diagnóstico se omitió por no haber errores). |
| `frontend` (ubuntu-latest) | Preparar **Node** desde `.node-version` con caché de npm, **`npm ci`**, **lint**, **format:check**, **pruebas** (Vitest) y **build de producción** | Todos correctos. |

## Causa del fallo y corrección
- **Causa (defecto de las pruebas, no del producto):** `SpaForwardIT` (T-33) usaba el postprocesador `csrf()` de spring-security-test, que sustituye de forma permanente el repositorio CSRF del `CsrfFilter` del contexto Spring compartido por un `TestCsrfTokenRepository` basado en sesión. Las clases que comparten ese contexto y se ejecutan después (`SecurityConfigIT`) dejaban de recibir la cookie `XSRF-TOKEN` y veían una sesión HTTP. En Windows, Surefire ejecuta las clases en orden alfabético (`common.security` antes que `common.web`) y el defecto quedaba oculto; en Linux, el orden del sistema de archivos lo destapó.
- **Reproducción local:** `-Dtest=SpaForwardIT,SecurityConfigIT -Dsurefire.runOrder=reversealphabetical` → los mismos 9 fallos. Antes se descartaron, con un `verify` completo en una copia limpia (1535/1535 en ambos casos), la zona horaria UTC, la configuración regional en inglés y un PostgreSQL que imita la imagen de la CI (superusuario, `en-US`, UTC).
- **Corrección (`d6f5c50`):** `SpaForwardIT` usa el CSRF real (cookie `XSRF-TOKEN` de `GET /api/auth/sesion` y cabecera `X-XSRF-TOKEN`), sin alterar el contexto. Ninguna otra prueba usa `csrf()`. Verificado en orden directo e inverso con las cuatro clases del contexto compartido: 73/73.
- **Mejora de la CI conservada (`6df34b2`, `59d647a`):** si `verify` falla, el workflow publica como anotaciones (visibles sin autenticación) el mensaje de cada prueba fallida, las líneas `[ERROR]` de Maven y el orden de ejecución de las clases. Validado con actionlint 1.7.12.

## Aviso del proveedor
GitHub anuncia que `ubuntu-latest` pasará a Ubuntu 26 a partir del 19/10/2026. No requiere acción ahora; si la imagen nueva cambiara el comportamiento, las anotaciones de diagnóstico lo mostrarán.
