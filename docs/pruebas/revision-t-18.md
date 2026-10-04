# Revisión técnica de T-18 y T-42 — Horarios y bloqueos en el frontend (P07) · cierre de la contraseña temporal

- **Fecha:** 03/10/2026 · **Revisor:** Claude Code · **Encargo:** 022 (Codex, `--approve-for-me`, código 0, 40 min). Clasificación: NORMAL (pantalla sobre una API ya revisada); revisión de Gemini no requerida.
- **Commits:** T-42 `276dd96`, merge `37d29c6`. T-18 en la rama `tarea/T-18-horarios-bloqueos`: `a6e3395`, `f3e53a4`, `338dd5e` (**sin integrar**). Registro `893f8dc`.
- **Resultado:** **T-42 Aprobada.** **T-18 Aprobada técnicamente; integración en espera de la decisión sobre RA-02 (P-02).**

## Verificado por el revisor
| # | Comprobación | Resultado |
|---|---|---|
| V-01 | Alcance | T-42: 2 líneas (`disableClose: true` y su aserción). T-18: 20 archivos en `frontend/**`, `docs/pruebas/{t-18,medicion-java}.md`, `docs/tareas.md` y `README.md`. Backend sin cambios. `git diff --check` limpio. |
| V-02 | Contratos (§6.3) | `HorariosApi`: `GET/PUT /api/barberos/{id}/jornadas`, `GET/POST /api/barberos/{id}/bloqueos`, `POST /api/bloqueos/lote`, `DELETE /api/bloqueos/{id}`. El 409 lee `reservas`, que el backend envía como lista (`JornadaService`, bloqueo individual) o como mapa por barbero (lote, `BloqueoService`); coincide con el catálogo de errores. |
| V-03 | Fechas en Lima | `instanteLima(fecha, hora)` construye `yyyy-MM-ddTHH:mm:00-05:00` sin pasar por la zona del navegador; `fechaHoyLima` usa `Intl.DateTimeFormat` con `America/Lima`, probada con un instante en que UTC ya cambió de día. Correcto: Perú no tiene horario de verano. |
| V-04 | Reglas en el cliente | La validación del editor (inicio < fin, sin solapes en el mismo día) es solo de experiencia de usuario; el servidor decide (`JORNADA_INVALIDA` por índice, `CONFLICTO_CON_RESERVAS`). "Copiar el lunes" sustituye martes–sábado y conserva el domingo. Reutiliza `confirmar-estado-dialogo`, `mostrarErrores` y los estilos del catálogo, como pedía el encargo. |
| V-05 | Pruebas del frontend (revisor, rama T-18, Node 24.21.0) | **20 archivos, 146/146** con la zona del proceso por defecto y **146/146 con `TZ=Europe/Madrid`**. Codex: lint, formato y build (473,38 kB) en verde; backend `verify` 896/896 sin cambios. |
| V-06 | Recorrido de extremo a extremo (Codex) | Jornadas, bloqueo individual, lote y borrado; **8 rechazos 403 de CSRF**; Edge a 1280 y 360 px con el navegador en Madrid mostrando horas de Lima. Limpieza por id; bases limpias, puertos libres. El 409 real de jornada sigue cubierto por `JornadaIT` (aún no hay API pública de reservas). |

## Observaciones
| # | Observación | Acción |
|---|---|---|
| O-1 | **RA-02 por debajo del 50 % sin pruebas:** 48,32 % (Java 2767 · TS 1832 · HTML 737 · SCSS 281 · SQL 109 LOC de producto). Con pruebas, 61,28 %. Sin contar HTML ni SCSS, 58,8 %. Proyección al cierre con el plan actual: unas +2200 LOC de Java (T-19…T-25, T-29, T-31) frente a unas +2400 de frontend (T-26…T-28, T-30, T-31), es decir, **≈ 48 % sin pruebas** y ≈ 65 % con pruebas. | **Decisión del responsable (alcance académico, P-02).** Opciones en el informe del coordinador. Mientras tanto, T-18 queda en su rama (sin conflicto con el backend) y se continúa con las tareas de backend, que suben el porcentaje. Antes de T-26 hace falta la decisión. |
| O-2 | Los dos *timeouts* antiguos de T-17 (O-3) reaparecieron con la máquina cargada; el comando estándar pasó en la repetición. | Sigue en vigilancia (CI y T-34). |
