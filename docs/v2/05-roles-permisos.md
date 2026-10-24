# BarberTurno v2 · 05 · Roles y matriz de permisos

> Estado: propuesta · 21/10/2026. Sustituye, en v2, a la matriz de v1 (arquitectura §7.2), que sigue vigente en `main`.

## 1. Dos niveles de rol
| Nivel | Dónde vive | Valores | Cómo se obtiene |
|---|---|---|---|
| **Rol de plataforma** | `usuario.rol_plataforma` | `NINGUNO` (por defecto), `ADMIN_PLATAFORMA` | Solo por configuración inicial (`BT_ADMIN_*`, como `AdminInicialRunner`) o por otro administrador de la plataforma. Nunca por registro. |
| **Rol en un negocio** | `membresia (negocio_id, usuario_id, rol, estado)` | `PROPIETARIO`, `ENCARGADO`, `BARBERO` | Solo aceptando una **invitación** válida para ese negocio y ese correo, o en el alta del negocio. |
| **Cliente de un negocio** | `cliente (negocio_id, …, usuario_id?, wa_id?)` | — (no es un rol de personal) | Registro público en la página del negocio, primer mensaje de WhatsApp al número del negocio, o alta por el personal. **Nunca concede permisos de personal.** |

Reglas:
- **R2-01.** Una persona tiene **como máximo una membresía por negocio** (`UNIQUE (negocio_id, usuario_id)`). Puede tener membresías en varios negocios y fichas de cliente en varios negocios. Ser personal y cliente del mismo negocio está permitido (el barbero que se corta el pelo en su barbería), con dos registros separados.
- **R2-02.** El rol de plataforma **no concede** permisos dentro de los negocios. Si el administrador de la plataforma trabaja además en una barbería, necesita su membresía como cualquiera.
- **R2-03.** El negocio activo de una petición sale **siempre de la ruta** (`/api/n/{slug}/…`) y se autoriza en el servidor con la membresía o la ficha de cliente del actor en ese negocio. El `slug` o el `negocioId` que envía el navegador **solo identifican**; nunca autorizan. Negocio inexistente → **404**; actor autenticado sin rol en un negocio existente, en una operación protegida → **403** (el negocio es público por su página); recurso concreto de otro negocio → **404**, igual que las reservas ajenas en v1 (precisado por V2-DA-34, 24/10/2026).
- **R2-04.** Un negocio `SUSPENDIDO` no acepta reservas ni mensajes de WhatsApp nuevos; su personal conserva el acceso de lectura y el administrador de la plataforma puede reactivarlo.
- **R2-05.** Siempre queda **al menos un PROPIETARIO activo** por negocio (V2-RN-06). Las operaciones que cambian propietarios toman `pg_advisory_xact_lock(313131, negocio_id)` antes de contar (generaliza DA-23).
- **R2-06.** La pertenencia se revalida en **cada petición** junto con `activo` y `token_version` (la misma consulta que hoy hace `UsuarioJwtConverter`, ampliada). Revocar una membresía tiene efecto inmediato sin tocar el JWT.
- **R2-07.** Un negocio no cambia la identidad global de una persona que también pertenece a otros negocios (V2-RN-16): solo revoca su membresía, desactiva su perfil o su ficha y edita el nombre visible local.
- **R2-08.** El encargado gestiona solo a los barberos: no invita, revoca, cambia de rol ni restablece a otros encargados ni a propietarios (hallazgo g038-3).

## 2. Matriz de permisos
Leyenda: ✔ permitido · **P** solo lo propio (la ficha de cliente del actor) · **A** solo lo asignado al barbero (`barbero.usuario_id = actor`) · **N** solo dentro del negocio de la ruta · — denegado (403 por rol; 404 si es un recurso concreto ajeno o de otro negocio).

### 2.1 Plataforma
| Operación | Público | Usuario autenticado | ADMIN_PLATAFORMA |
|---|---|---|---|
| Registro de cuenta, login, logout, sesión, cambiar contraseña, perfil | ✔ (registro y login) | ✔ | ✔ |
| Listar mis negocios (membresías y fichas) | — | ✔ (los suyos) | ✔ (los suyos) |
| Aceptar una invitación (token válido + correo coincidente) | — | ✔ | ✔ |
| Crear negocio + invitación al propietario | — | — | ✔ |
| Listar negocios, suspender y reactivar | — | — | ✔ |
| Reenviar o revocar la invitación pendiente del propietario de un negocio | — | — | ✔ |
| Restablecer la contraseña de cualquier cuenta (propietarios y personas con relación con varios negocios, V2-RN-16) | — | — | ✔ (se audita) |
| Ver clientes, reservas, conversaciones o reportes de un negocio | — | — | **—** (MVP) |

### 2.2 Dentro de un negocio (`/api/n/{slug}/…`)
| Operación | Público | CLIENTE | BARBERO | ENCARGADO | PROPIETARIO |
|---|---|---|---|---|---|
| Ver la página pública: servicios y profesionales activos, disponibilidad | ✔ | ✔ | ✔ | ✔ | ✔ |
| Darse de alta como cliente del negocio (crea la ficha, nunca una membresía) | — (requiere cuenta) | ✔ | ✔ | ✔ | ✔ |
| Crear reserva para sí | — | P | — | — | — |
| Crear reserva para un cliente (reserva asistida, RF-18) | — | — | — | N | N |
| Mis reservas | — | P | — | — | — |
| Agenda y listado operativo | — | — | A | N | N |
| Ver detalle de una reserva | — | P | A | N | N |
| Reprogramar, cancelar | — | P (≥ 2 h, RN-07) | — | N (motivo, antes del inicio, RN-08) | N (ídem) |
| Confirmar, iniciar, completar, no asistió | — | — | A | N | N |
| Auditoría de una reserva | — | — | A | N | N |
| Servicios, profesionales, jornadas y bloqueos (escritura) | — | — | — | N | N |
| Ver jornadas y bloqueos de un profesional | — | — | A | N | N |
| Clientes: buscar, ver ficha, crear ficha; restablecer el acceso web del cliente solo si su cuenta no tiene relación con otros negocios (V2-RN-16) | — | — | — | N | N |
| Reportes y su exportación `.xlsx` | — | — | — | N | N |
| Avisos propios | — | P | P | P | P |
| Bandeja de WhatsApp: ver conversaciones derivadas, responder, devolver al asistente | — | — | — | N | N |
| Invitar o revocar BARBERO; desactivar el perfil de profesional (no la cuenta global); restablecer su contraseña solo si su cuenta no tiene relación con otros negocios (V2-RN-16) | — | — | — | N | N |
| Invitar, revocar o restablecer a un ENCARGADO (con la misma condición de V2-RN-16) | — | — | — | — | N |
| Invitar o revocar PROPIETARIO; cambiar roles (p. ej., ENCARGADO ↔ PROPIETARIO) | — | — | — | — | N (no puede dejar 0 propietarios) |
| Conectar, reconectar o desconectar el número de WhatsApp; ver su estado | — | — | — | — (solo ver estado) | N |
| Configuración del negocio (nombre, parámetros de reserva, horario de atención humana) | — | — | — | — | N |
| Salir del negocio (renunciar a la propia membresía) | — | — | ✔ | ✔ | ✔ (si queda otro propietario) |

### 2.3 Canal WhatsApp (el actor es la ficha de cliente identificada por `wa_id` en el negocio dueño del número)
| Operación | Cliente por WhatsApp |
|---|---|
| Consultar servicios, profesionales y disponibilidad del negocio | ✔ |
| Crear reserva para sí | P, solo tras **confirmación explícita** (botón con el identificador de la operación pendiente) |
| Consultar sus reservas | P |
| Reprogramar o cancelar | P (mismas reglas que la web: RN-07, RN-09), con confirmación explícita |
| Pedir una persona | ✔ |
| Cualquier otra operación (personal, reportes, otros clientes, otros negocios) | — (no existe herramienta para ello) |

## 3. Cómo se implementa (resumen; detalle en `04-arquitectura-datos.md` §4)
1. `SecurityFilterChain`: autenticación (JWT en cookie, CSRF, como v1) y reglas **gruesas** por prefijo de ruta (`/api/plataforma/**` exige `ADMIN_PLATAFORMA`; `/api/n/**` exige autenticación salvo las lecturas públicas).
2. `ResolutorNegocio` (filtro **antes** de la autenticación, tras el cortafuegos; V2-DA-33/36): rechaza rutas `/api/**` codificadas (400), resuelve el `slug` de la ruta (404 si no existe) y fija el negocio de la petición. Después, la autenticación revalida la membresía y la ficha del actor **en ese negocio** y construye el rol efectivo; sin rol en una operación protegida → 403 (V2-DA-34); recurso de otro negocio → 404. *(Actualizado el 24/10/2026.)*
3. Transacción: al empezar, `set_config('app.negocio_id', negocioId, true)`; **RLS** filtra y valida cada fila del negocio (segunda barrera).
4. Servicios: comprobaciones por rol del negocio y por propiedad o asignación (evolución de `ReservaAutorizacion`).
5. `ReservaDto.permisos` lo sigue calculando el servidor con el rol del negocio (DA-15).

## 4. Correspondencia con v1
| v1 | v2 |
|---|---|
| `ADMIN` | `PROPIETARIO` del negocio por defecto (y `ADMIN_PLATAFORMA` el administrador inicial `BT_ADMIN_*`) |
| `BARBERO` | membresía `BARBERO` + perfil `barbero` |
| `CLIENTE` | ficha `cliente` del negocio por defecto vinculada a su cuenta |
| ADMIN con perfil de barbero | `PROPIETARIO` + `barbero.usuario_id` = la misma persona (hereda A sobre su agenda) |
| RN-26 (el personal no se registra solo) | Se mantiene y se refuerza: solo por invitación (V2-RN-04) |
