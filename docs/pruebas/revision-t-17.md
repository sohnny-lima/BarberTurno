# Revisión técnica de T-17 — Administración de servicios y barberos en el frontend (P05, P06)

- **Fecha:** 03/10/2026 · **Revisor:** Claude Code · **Encargos:** 021 (cortado por el límite de uso de la cuenta, trabajo conservado en un *stash*) y 021b (continuación, `--approve-for-me`, código 0, 36 min).
- **Commits:** `4e2ff06` (implementación), `75017df` (pruebas y README), `426ebab` (finales de línea), `59257c2` (cierre documental), merge `6546f2f`, registro `232d701`.
- **Resultado:** **Aprobada con observaciones.** Una observación menor de experiencia de usuario pasa a T-42; se activa la alerta temprana de RA-02 (O-2).

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | 33 archivos: `frontend/**`, `docs/pruebas/t-17.md`, `docs/tareas.md` y `README.md`. **Backend sin cambios** (`git diff 1628834 HEAD -- backend` vacío), sin cambios en APF2, CI ni documentos de diseño. `git diff --check` limpio. *Stash* recuperado y vacío. |
| V-02 | `frontend/.gitattributes` | Se retira la regla `*.ps1 eol=lf` de T-12, que contradecía la raíz (`*.ps1 eol=crlf`). Correcto: en el índice siguen en LF y se extraen en CRLF; el script de T-12 se normaliza en la próxima extracción, sin efecto funcional. |
| V-03 | Contratos (arquitectura §6.3) | `ServiciosApi` y `BarberosApi` con URLs relativas; `CrearBarberoDto` como unión de los dos cuerpos (`{nombre, correo, telefono?, especialidad, passwordTemporal}` o `{usuarioId, especialidad}`); `PUT` sin correo; `PATCH /estado` de barberos tipado como `{barbero, reservasFuturasVigentes}` y el de servicios como `ServicioDto`. Coinciden con el documento. |
| V-04 | Sin reglas de negocio en el cliente | Los validadores se declaran como espejo de experiencia de usuario (duración 10–180 y múltiplo de 10, precio 0–999999,99 con 2 decimales, nombre sin espacios dobles, contraseña con RN-25 y ≤ 72 bytes UTF-8). Los 409 `NOMBRE_DUPLICADO`/`CORREO_DUPLICADO` y los 400 `errores[]` se colocan en su campo; el 422 `LIMITE_BARBEROS_ACTIVOS` llega con el `detail` del servidor. El aviso "Tiene N reservas futuras" usa el dato del servidor. |
| V-05 | Contraseña temporal | `crypto.getRandomValues` con muestreo por rechazo (sin sesgo de módulo), 16 caracteres ASCII con al menos una letra y un dígito y mezcla de Fisher-Yates; no se guarda ni se registra. Se muestra una sola vez **después** de cerrar el diálogo de alta, con "Copiar" y el aviso de entrega en persona. Ver O-1. |
| V-06 | Rutas y guard | `/admin/servicios` y `/admin/barberos` *lazy* con `authGuard` + `rolGuard(['ADMIN'])`; el resto de páginas provisionales intactas. Prueba de que un CLIENTE no accede. |
| V-07 | Cadena del frontend (Node 24.21.0, fnm) | `npm ci` sin avisos y 0 vulnerabilidades; `lint` y `format:check` limpios; **Vitest 16 archivos, 115/115**; `build` 472,87 kB iniciales (presupuesto 500 kB). |
| V-08 | Backend | Árbol idéntico al revisado en T-16 (`clean verify` 896/896, 0 avisos de Javadoc); Codex lo repitió con el mismo resultado. Sin Javadoc nuevo (no hay Java nuevo). |
| V-09 | Recorrido de extremo a extremo | Codex: login del administrador inicial por el proxy con `BT_ADMIN_*` solo en el entorno del proceso; alta, listado, desactivación y reactivación; **12 rechazos 403 de CSRF** (6 rutas de escritura × sin token y token incorrecto); revisión con Edge a 1280 y 360 px. Limpieza por id del servicio 4, el barbero 4 y los usuarios 9 y 10. **El revisor comprobó la base de desarrollo: 0 usuarios, 0 barberos y 0 servicios**; puertos 8080/4200 libres y sin procesos propios (el `java` y el `codex` vivos son las extensiones de VS Code). |
| V-10 | % de Java (RA-02) | Medición del revisor: **54,68 % sin pruebas** y **67,42 % con pruebas**. Los scripts `.mjs`/`.ps1` de verificación no se cuentan, así que la bajada (61,37 % en T-12) se debe al código real de las pantallas. Ver O-2. |

## Observaciones
| # | Observación | Acción |
|---|---|---|
| O-1 | El diálogo de la contraseña temporal se puede cerrar con Esc o con un clic fuera. Como se muestra una sola vez, un cierre accidental obliga a restablecerla (T-31). | **T-42**: `disableClose: true` en ese diálogo (solo se cierra con "Cerrar") y una prueba. Se agrupa con el encargo de T-18. |
| O-2 | **Alerta temprana RA-02 activada** (umbral del 55 % fijado en la revisión de T-05, O-2): 54,68 % sin pruebas. Sigue por encima del 50 %, y la cifra principal (con pruebas) es del 67,42 %. | **Valoración del arquitecto:** T-19…T-25 son siete tareas de backend que añaden Java antes de la siguiente fase de pantallas (T-26…T-28), así que se espera una subida. Medidas: (1) desde T-18, los encargos de frontend exigen reutilizar los componentes compartidos (`confirmar-estado-dialogo`, `mostrarErrores`, `catalogo.scss`) y no recalcular en TS nada que ya entregue el servidor (DA-15); (2) medición con `--escribir` al cerrar T-18, T-25 y T-28; (3) **umbral de actuación del 52 %** sin pruebas tras T-28: si se cruza, el arquitecto propondrá trasladar lógica de presentación al servidor (por ejemplo, textos y agregados de reportes) con su DA. |
| O-3 | Dos pruebas antiguas (`app.spec.ts` y `app.routes.spec.ts`) agotaron los 5 s de Vitest mientras arrancaban los servidores en la misma máquina; sin esa carga pasan (Codex 10,4 s; revisor 27 s). | Sin acción ahora (no se amplió ningún tiempo). Vigilar en el primer run real de la CI (P-04) y en T-34; si se repite, ajustar el tiempo de esas pruebas y anotarlo. |

## Lecciones para los encargos
- El corte por el límite de uso a mitad de un encargo se resolvió sin pérdidas con *stash* + encargo de continuación que describe el estado real. Se mantiene como procedimiento.
- Codex detectó en las capturas dos problemas que la comprobación automática no veía (la ayuda que tapaba "Generar" y la separación de campos que Material sobrescribía). Conviene seguir pidiendo la revisión visual de las capturas además de las aserciones.
