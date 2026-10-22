# BarberTurno · Resumen de continuidad para iniciar la versión 2

Punto de partida para una sesión nueva. Estado al **21/10/2026** (cierre del APF3). El detalle verificable está en `docs/cierre.md`; el plan y las decisiones, en `docs/tareas.md`, `docs/requisitos.md` (MJ) y `docs/arquitectura.md` (DA).

## 1. Qué existe (v1, académica)
- **Producto:** sistema de reservas de una barbería de **una sede**, hasta 10 barberos activos y tres roles (CLIENTE, BARBERO, ADMIN). RF-01…RF-21 implementados; exportación del reporte a Excel (T-58).
- **Arquitectura (A1):** SPA Angular 22 + API Spring Boot 4.1.1 / Java 21 + PostgreSQL 18, monolito modular en un único jar. Paquetes `auth`, `users`, `catalog`, `scheduling`, `reservations`, `audit`, `notifications`, `reporting`, `common`, `demo`.
- **Integridad:** bloqueos ordenados ① cliente → ② barberos → ③ reserva, revalidación dentro de la transacción y `EXCLUDE USING gist` (arquitectura §8). Es la parte que no se debe romper en ninguna evolución.
- **Seguridad:** JWT en cookie HttpOnly, CSRF obligatorio en toda escritura (`DEFAULT_CSRF_MATCHER`), autorización por rol y por propiedad (404 ante recursos ajenos), BCrypt 12, bloqueo tras 5 intentos.
- **Calidad:** `mvnw verify` 1578 pruebas (JaCoCo ≥ 70 % en el dominio, Javadoc con doclint), Vitest 450, E2E 48 (Chromium y Firefox, 360 y 1440 px), CI en GitHub Actions.
- **Despliegue:** Azure for Students (West US): App Service Linux B1 + PostgreSQL Flexible 18.6 B1ms, HTTPS, health check y UptimeRobot. **Ejecuta `d2f3107`** (sin T-58).

## 2. Versiones de referencia
| Elemento | Commit |
|---|---|
| Código de la entrega académica (APF3) | `3bcd5d0` (merge de T-58 en `main`) |
| Código desplegado en Azure | `d2f3107` |
| Materiales del APF3 y APF2 final publicados | ver `docs/cierre.md` §0 |

## 3. Pendientes heredados de v1 (no cerrar en falso)
1. **CP-11:** cinco sesiones de usabilidad con personas (0/5; materiales en `docs/pruebas/cp11-sesiones.md`).
2. **RNF-02:** disponibilidad del 99 % en noviembre de 2026 (cálculo el 01/12/2026, `docs/p-03-alojamiento.md` §12.7).
3. **Pruebas operativas en producción:** PM-1…PM-8 (§12.5), ensayo de restauración en Azure (§12.6) y protecciones (bloqueo de la base, avisos, copia lógica semanal).
4. **Redespliegue de T-58** en Azure: decisión pendiente del responsable.
5. **RNF-07:** matriz de navegadores comerciales.
6. **Negocio:** validar reglas, precios y reportes con una barbería real (P-01, P-06).
7. **Participación de Java:** 28,78 % del producto (41,56 % con pruebas) frente al 60 % estimado en el APF2; métrica informativa (RA-02). No se maquilla.

## 4. Ideas para v2 (propuestas, **no** implementadas ni comprometidas)
- Varias barberías en una plataforma (multitenencia) y nuevos roles de propietario o plataforma.
- Reservas por WhatsApp y asistencia con IA.
- Notificaciones externas (correo o SMS), pagos o señal de reserva.

Cada una cambia supuestos de v1 (una sede, avisos internos, sin pagos) y exige nuevos requisitos (MJ/RF), decisiones de arquitectura (DA) y un plan propio antes de tocar código.

## 5. Cómo empezar la sesión de v2
1. Leer `AGENTS.md`, `CLAUDE.md`, este archivo, `docs/cierre.md` y `.local/coordinacion/estado.md` (local).
2. Confirmar con el responsable el **alcance de v2** y si se levanta el **freeze** de v1 (AGENTS.md §5). Decidir si v2 vive en `main` o en una rama o repositorio aparte, para no alterar la versión académica entregada (`3bcd5d0`).
3. Antes de diseñar la multitenencia, revisar el impacto en el protocolo de bloqueos, las restricciones `EXCLUDE`, la autorización por propiedad y los reportes.
4. Mantener el flujo de agentes: Claude coordina y revisa, Codex implementa, Gemini revisa en solo lectura las tareas críticas.
