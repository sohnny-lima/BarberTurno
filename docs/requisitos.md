# BarberTurno — Especificación de requisitos

> Versión 1.0 · 01/10/2026 · Responsable técnico: Claude Code (arquitecto) · Implementación: Codex
> Documentos relacionados: [arquitectura.md](arquitectura.md) · [tareas.md](tareas.md) · [../AGENTS.md](../AGENTS.md)

## 0. Cómo leer este documento

- **Fuente de verdad del diseño original:** `docs/apf2/` (no se modifica). Las referencias usan esta notación:
  - `[Inf p. N]` → `docs/apf2/APF2_BarberTurno_Informe.pdf`, página N.
  - `[Proto]` → `docs/apf2/BarberTurno_Prototipo.html` (comportamiento simulado en JavaScript).
  - `[Drawio:Hoja]` → `docs/apf2/BarberTurno_APF2_Mockups_y_Diagramas.drawio`, pestaña indicada (A1-P01…A1-P08, Canvas, Proceso, Clases, Datos, Gantt).
  - `[Corrida:Hoja]` → `docs/apf2/BarberTurno_Corrida_Manual.xlsx` (hojas Verificacion, Datos_base, Inicial, Final, Trazabilidad).
  - `[Java]` → `docs/apf2/java/ReservaService.java` y su prueba. `[LEEME]` → `docs/apf2/LEEME.txt`.
- **Identificadores:** se conservan RF-01…RF-14, RNF-01…RNF-10 y CP-01…CP-12 del APF2 [Inf p. 7–9, 31]. Lo nuevo continúa la numeración (RF-15+, RNF-11+, CP-13+) y se marca como **mejora (MJ-xx)**.
- **Prioridad (MoSCoW):** M = obligatorio para el MVP · S = debería estar · C = podría estar si sobra tiempo.

---

## 1. Contexto y alcance

### 1.1 Problema y objetivo
Una barbería de una sede en Huamanga coordina citas por mensajes, llamadas y notas dispersas. Eso produce dobles asignaciones, confirmaciones lentas y cambios sin trazabilidad [Inf p. 4]. Objetivo general: un sistema web responsivo que centralice reservas y turnos, permita consultar la disponibilidad, gestionar cambios y controlar la agenda diaria con trazabilidad [Inf p. 6].

### 1.2 Actores
| Actor | Responsabilidad [Inf p. 6, 29] |
|---|---|
| Cliente | Se registra, consulta disponibilidad, reserva, reprograma y cancela **sus** citas dentro de la política y consulta su historial y sus avisos. |
| Barbero | Consulta **su** agenda diaria y semanal, confirma pendientes y registra el inicio, el fin o la inasistencia de **sus** atenciones. |
| Administrador | Mantiene el catálogo, el personal, las jornadas y los bloqueos. Consulta la agenda global y los reportes. Aplica excepciones con motivo. |
| Sistema | Calcula franjas, impide solapamientos y registra auditoría y avisos internos. |

### 1.3 Alcance del MVP [Inf p. 6]
| Incluido | Fuera de alcance |
|---|---|
| Registro, acceso por roles y perfil | App móvil nativa |
| Catálogo, profesionales, jornadas, descansos y bloqueos | Pagos en línea, caja, inventario y facturación electrónica |
| Disponibilidad, reserva, reprogramación y cancelación | Varias sedes |
| Agenda, estados, historial, reportes y avisos internos | WhatsApp, SMS, correo saliente y marketing automatizado |

Límites: **una sede**, **hasta 10 profesionales activos**, una reserva = **un cliente + un servicio + un barbero**, hora de negocio **America/Lima**. Precios y duraciones de las pantallas son datos de demostración [Inf p. 6]. Todos los barberos activos ofrecen todo el catálogo activo; la especialidad es descriptiva [Inf p. 35].

### 1.4 Restricciones académicas
| ID | Restricción | Origen |
|---|---|---|
| RA-01 | Alternativa seleccionada A1: SPA **Angular** + API **Spring Boot (Java)** + **PostgreSQL**. A2 (Spring MVC + Thymeleaf) queda como contingencia. | [Inf p. 3, 10, 23] |
| RA-02 | El backend se implementa en **Java** (A1). *Reclasificada el 04/10/2026 por el responsable:* el porcentaje de Java (A1 estimaba 60 %) es solo una **métrica informativa** de seguimiento, sin umbral; no bloquea tareas ni justifica mover lógica entre capas, que se reparte por responsabilidad (la lógica de negocio sigue en el servidor por diseño, DA-15). Medición: LOC Java propias ÷ LOC totales, con y sin pruebas, excluyendo dependencias, generados, comentarios, líneas vacías y documentación. | [Inf p. 10] |
| RA-03 | El porcentaje no se demuestra con mockups ni por el framework: se mide en el repositorio con una herramienta reproducible (tarea T-05). | [Inf p. 10] |
| RA-04 | Trazabilidad RF → pantalla → prueba. Se conservan los IDs del APF1/APF2. | [Inf p. 7, 30] |
| RA-05 | Documentación de clases con Javadoc (`@author`, `@version`, `@param`, `@return`, `@throws`) al menos en las clases de dominio y servicios. | [Inf p. 37] |
| RA-06 | Plan de aceptación CP-01…CP-12 con registro de fecha, versión, entradas, resultado y evidencia. | [Inf p. 31] |
| RA-07 | Línea base planificada: 17/08 → 20/12/2026; aprox. 420 h, trabajo individual. El APF2 se entrega el 01/10/2026 a las 22:30. | [Inf p. 23–24], [Drawio:Gantt] |
| RA-08 | Los datos del negocio son hipótesis académicas y aún no los ha validado un representante del negocio. No se presentan como resultados reales. | [Inf p. 3], [LEEME] |

---

## 2. Requisitos funcionales

### 2.1 Requisitos del APF2 (precisados)
Cada RF conserva el comportamiento del APF2 y añade **criterios verificables** que salen del prototipo, la corrida y las mejoras de la sección 5.

| ID | Requisito | Criterios de aceptación | Pant. | Prio | Origen |
|---|---|---|---|---|---|
| **RF-01** Registro | Crear una cuenta de cliente con nombre, correo, teléfono y contraseña. | Con datos válidos se crea un usuario `CLIENTE` y se abre la sesión. Se rechazan el correo repetido (sin distinguir mayúsculas), los campos incompletos, un teléfono que no tenga 9 dígitos y una contraseña débil (RN-25). Es obligatorio aceptar el aviso de privacidad (MJ-12). | P01 | M | [Inf p. 7], [Proto] |
| **RF-02** Autenticación | Iniciar y cerrar sesión y restringir acciones por rol. | El servidor rechaza con 401/403/404 toda operación no autorizada, aunque la interfaz oculte el botón. El error de credenciales es genérico. Tras 5 intentos fallidos la cuenta se bloquea 15 min (MJ-11). | P01 | M | [Inf p. 6–7] |
| **RF-03** Perfil | Actualizar datos personales no sensibles. | El usuario edita nombre y teléfono. El **correo no es editable** porque es el identificador de acceso (ver C-06). El cambio persiste y se refleja al volver. | P01 | M | [Inf p. 7] |
| **RF-04** Servicios | Crear, editar y desactivar servicios (nombre, descripción, precio y duración). | Nombre único. Duración de 10–180 min en múltiplos de 10. Precio ≥ 0 con 2 decimales. Solo los activos aparecen para nuevas reservas. Desactivar no borra ni altera las reservas existentes. | P05 | M | [Inf p. 7, 13], [Proto] |
| **RF-05** Barberos | Administrar profesionales, especialidad y estado. | Al crear un barbero se crea su cuenta de personal con contraseña temporal (MJ-09). Hay como máximo 10 barberos activos (RN-19). Desactivar oculta al barbero de nuevas reservas, bloquea su acceso y conserva el historial. La respuesta informa cuántas reservas futuras vigentes tiene para que el administrador las gestione. | P06 | M | [Inf p. 6–7, 13] |
| **RF-06** Horarios | Configurar jornada, descanso y bloqueos por profesional. | La jornada semanal se define como intervalos por día (el descanso es el hueco entre dos intervalos). Se rechazan intervalos inválidos o solapados y los cambios que dejen fuera de jornada una reserva futura que ocupa franja. Un bloqueo que cruce una reserva que ocupa franja se rechaza. | P07 | M | [Inf p. 7, 14], [Drawio:Datos] |
| **RF-07** Disponibilidad | Calcular franjas según fecha, servicio y barbero. | No aparecen franjas pasadas, ocupadas, bloqueadas, fuera de jornada ni fuera del horizonte de reserva. La rejilla es de 10 min y la franja dura lo que el servicio. Se puede consultar sin sesión (MJ-07). | P02 | M | [Inf p. 7], [Proto] |
| **RF-08** Reserva | Confirmar servicio, barbero, fecha y hora disponibles. | Se guarda **una sola** reserva aunque lleguen solicitudes concurrentes. Se guardan `precio_ref` y `duracion_ref`, una auditoría y un aviso interno en la misma transacción. Por defecto queda `CONFIRMADA` (ver RN-21). Si hay conflicto se responde 409 y la interfaz ofrece otras franjas. | P02, P03 | M | [Inf p. 8, 25, 28] |
| **RF-09** Reprogramación | Cambiar fecha, hora y, opcionalmente, barbero de una reserva vigente según la política. | Solo aplica a reservas `CONFIRMADA`. Se conservan servicio, precio y duración de referencia. Es atómica: si la nueva franja falla, la reserva original queda intacta. El cliente puede hacerlo hasta 2 h antes. El administrador requiere motivo. | P03, P02 | M | [Inf p. 8, 25, 28], [Proto] |
| **RF-10** Cancelación | Cancelar la reserva y registrar el motivo cuando corresponda. | Aplica a `PENDIENTE` o `CONFIRMADA`. El cliente puede cancelar hasta 2 h antes y el motivo es opcional. El administrador puede hacerlo antes del inicio y el motivo es obligatorio. Se libera la franja, se conserva la fila, se audita y se notifica. | P03 | M | [Inf p. 8], [Proto] |
| **RF-11** Agenda | Consultar la agenda diaria y semanal (lunes a domingo) por profesional y fecha. | Muestra cliente, servicio, intervalo y estado. El barbero ve solo la suya y el administrador ve todas o filtra. | P04 | M | [Inf p. 8], [Proto] |
| **RF-12** Atención | Actualizar el estado de una atención. | Solo se aceptan las transiciones de RN-10 permitidas para el rol (RN-11) y el momento (RN-12). Repetir una acción ya aplicada o enviar una versión desactualizada devuelve 409. | P04 | M | [Inf p. 8, 25] |
| **RF-13** Historial | Consultar reservas propias u operativas con filtros (fechas, estado, barbero, servicio). | El cliente solo ve las suyas. El barbero, las asignadas a él. El administrador, todas. Hay paginación. Una reserva ajena responde 404 sin revelar datos. | P03, P08 | M | [Inf p. 8, 31] |
| **RF-14** Reportes | Contar reservas por estado, servicio, profesional y periodo. | Se filtra por fecha de inicio de la reserva (Lima) y el rango es de 366 días como máximo. La suma por estado = suma por servicio = suma por profesional = total. Los datos coinciden con el historial filtrado. | P08 | M | [Inf p. 8, 31] |

### 2.2 Requisitos nuevos (mejoras)
| ID | Requisito | Criterios de aceptación | Pant. | Prio | Mejora |
|---|---|---|---|---|---|
| **RF-15** Contraseña | Cambiar la propia contraseña. Cambio obligatorio si es temporal. | Se exige la contraseña actual. Tras el cambio se invalidan las sesiones anteriores (`token_version`). Con una contraseña temporal solo se permite cambiarla y cerrar sesión. | P01 | M | MJ-09 |
| **RF-16** Avisos internos | Consultar avisos propios, ver el contador de no leídos y marcarlos como leídos. | Cada usuario ve solo los suyos. El contador se actualiza al navegar y cada 60 s. | P03, cabecera | M | MJ-05 |
| **RF-17** Auditoría | Consultar el historial de cambios de una reserva. | Lo ven el administrador y el barbero asignado. Muestra actor, fecha, acción, valores anterior y nuevo, y motivo. | P04 | M | MJ-05 |
| **RF-18** Reserva asistida | El administrador crea una reserva en nombre de un cliente registrado (llamada telefónica o conciliación del registro manual [Inf p. 4]). | Aplica las mismas validaciones y concurrencia que RF-08. La auditoría registra como actor al administrador. | P02 (modo admin) | S | MJ-10 |
| **RF-19** Restablecer acceso | El administrador busca clientes o personal y restablece su contraseña con una temporal. | La contraseña temporal se muestra una sola vez. Se exige cambiarla en el siguiente ingreso (RF-15). Las sesiones previas se invalidan. | P06 / gestión de usuarios | S | MJ-09 |
| **RF-20** Bloqueo para todo el equipo | Crear el mismo bloqueo (p. ej., feriado) para varios barberos en una operación. | La operación es atómica: si algún barbero tiene una reserva en conflicto no se crea ninguno y se informa cuáles fallan. | P07 | C | MJ-14 |
| **RF-21** Sin preferencia de barbero | Consultar la disponibilidad de todos los barberos activos para un servicio y una fecha. | Cada franja indica qué barberos la tienen libre. Al confirmar se envía un barbero concreto. | P02 | C | MJ-15 |

---

## 3. Requisitos no funcionales

La tabla de la p. 9 del PDF se extrae con las columnas desalineadas. La correspondencia ID ↔ meta se reconstruyó con la tabla de componentes de la p. 28 (supuesto S-01).

| ID | Meta | Verificación | Prio | Origen |
|---|---|---|---|---|
| **RNF-01** Rendimiento | 95 % de consultas de disponibilidad en ≤ 2 s con 50 usuarios concurrentes. | Prueba de carga (T-35) con los datos de demostración ampliados. | M | [Inf p. 9] |
| **RNF-02** Disponibilidad | 99 % mensual, excluido el mantenimiento. | Endpoint de salud + monitor externo tras el despliegue. Medición posterior. | S | [Inf p. 9] |
| **RNF-03** Seguridad de credenciales | BCrypt, JWT y HTTPS en producción. | BCrypt (coste 12), JWT firmado HS256 en cookie `HttpOnly; Secure; SameSite=Strict` con expiración de 8 h y TLS en producción. Revisión en T-33. | M | [Inf p. 9] |
| **RNF-04** Autorización | Autorización en cada operación protegida: rol, propiedad de la reserva y barbero asignado. | Matriz de pruebas rol × endpoint (T-10, T-21) + CP-02. | M | [Inf p. 9] |
| **RNF-05** Integridad | Reservas y cambios sin solapamientos, incluso con concurrencia. | Bloqueo por barbero + restricción de exclusión GiST. Prueba concurrente CP-03 (T-20). | M | [Inf p. 9, 28] |
| **RNF-06** Usabilidad | Reservar en ≤ 3 min y ≤ 5 pasos principales. | Asistente de 3 pasos. Prueba con 5 participantes (CP-11). | M | [Inf p. 9] |
| **RNF-07** Compatibilidad | Las dos últimas versiones de Chrome, Edge y Firefox. | Matriz de compatibilidad + E2E en Chromium y Firefox (T-34). | M | [Inf p. 9] |
| **RNF-08** Adaptabilidad | Interfaz usable de 360 a 1440 px. | E2E con viewport de 360 px y 1440 px (T-34). | M | [Inf p. 9] |
| **RNF-09** Recuperación | Copia diaria, RPO 24 h, RTO 4 h. | Scripts `pg_dump`/`pg_restore` + ensayo cronometrado (T-36). | M | [Inf p. 9] |
| **RNF-10** Mantenibilidad | Cobertura unitaria ≥ 70 % en el dominio crítico. | JaCoCo con umbral sobre los paquetes de dominio y servicios de reservas y agenda. El build falla si no se alcanza (T-04, T-09). | M | [Inf p. 9] |
| **RNF-11** Accesibilidad *(MJ-13)* | Pautas básicas de WCAG 2.2 AA: etiquetas, foco visible, navegación por teclado y contraste ≥ 4.5:1. | Revisión con axe en E2E (T-34). | S | Mejora |
| **RNF-12** Datos personales *(MJ-12)* | Ley 29733 (Perú): aviso y consentimiento al registrarse, minimización de datos y nunca exponer datos de otros clientes. | Pruebas de autorización y revisión de DTO (sin hash ni datos ajenos). | M | Mejora |
| **RNF-13** Zona horaria *(MJ-04)* | Toda fecha de negocio se interpreta y muestra en America/Lima (UTC−5, sin horario de verano), sea cual sea la zona del navegador o del servidor. | Pruebas con `Clock` fijo y navegador en otra zona (E2E). | M | Mejora |
| **RNF-14** Observabilidad *(MJ-16)* | Logs estructurados sin contraseñas, tokens ni datos personales completos. Endpoint de salud. | Revisión de código + prueba de `/actuator/health`. | S | Mejora |

---

## 4. Reglas de negocio

Fuentes principales: [Inf p. 9, 25, 28, 35–36], [Proto], [Java], [Corrida]. "Ocupa franja" = estado distinto de `CANCELADA` (la misma condición que usa la restricción de exclusión [Inf p. 28]).

| ID | Regla | Origen |
|---|---|---|
| RN-01 | Los intervalos son semiabiertos `[inicio, fin)`. `fin = inicio + duración`. Dos citas contiguas (10:00–10:30 y 10:30–11:00) no se solapan. | [Inf p. 9], [Java], CP-04 |
| RN-02 | Cada reserva tiene exactamente un cliente, un servicio y un barbero. | [Inf p. 6, 35] |
| RN-03 | Un barbero no puede tener dos reservas que ocupan franja y se solapan. | [Inf p. 9, 28] |
| RN-04 | Un cliente no puede tener dos reservas que ocupan franja y se solapan, aunque sean con barberos distintos. | **MJ-06** |
| RN-05 | Una franja reservable debe: estar dentro de un intervalo de jornada del día; no cruzar bloqueos ni reservas; empezar después del instante actual; estar alineada a la rejilla de 10 min desde el inicio del intervalo; y empezar como máximo 30 días después de hoy (horizonte, parametrizable). | [Inf p. 7], [Proto], **MJ-08** |
| RN-06 | Solo servicios y barberos **activos** sirven para crear una reserva; en una reprogramación, el **barbero de destino** debe estar activo y el servicio se conserva aunque se haya desactivado después (RN-09, RN-13). *(Redacción aclarada el 04/10/2026 por el hallazgo de T-23: la frase anterior podía leerse como si el servicio también fuera "destino".)* | [Inf p. 7, 31] |
| RN-07 | El cliente puede reprogramar o cancelar si faltan **≥ 2 h** para el inicio (el límite exacto está permitido; 1 h 59 min no). | [Inf p. 9], [Java], CP-06 |
| RN-08 | El administrador puede reprogramar o cancelar fuera de la política solo **antes del inicio** de la cita y con **motivo obligatorio** (≥ 5 caracteres). Se audita como excepción. *Diferencia con el anexo del APF2: su caso 6 permitía al administrador modificar justo en el instante de inicio; T-09 aplica esta regla (estrictamente antes), que prevalece.* | [Inf p. 9, 33], [Proto] |
| RN-09 | Solo se reprograman reservas `CONFIRMADA`. Pueden cambiar fecha, hora y barbero; el servicio, `precio_ref` y `duracion_ref` se conservan. Si la nueva franja falla, la reserva original no cambia. | [Inf p. 8, 25, 28, 36] |
| RN-10 | Estados: `PENDIENTE`, `CONFIRMADA`, `EN_ATENCION`, `COMPLETADA`, `CANCELADA`, `NO_ASISTIO`. Transiciones: PENDIENTE→CONFIRMADA\|CANCELADA; CONFIRMADA→EN_ATENCION\|CANCELADA\|NO_ASISTIO; EN_ATENCION→COMPLETADA. Los estados terminales son COMPLETADA, CANCELADA y NO_ASISTIO. | [Inf p. 25] |
| RN-11 | Permisos de transición: confirmar, iniciar, completar y marcar no asistió → barbero asignado o administrador. Cancelar → cliente propietario (RN-07) o administrador (RN-08). El barbero **no** cancela. | [Inf p. 25, 29], [Proto] |
| RN-12 | Momento de las transiciones: `EN_ATENCION` desde 15 min antes del inicio; `NO_ASISTIO` desde la hora de inicio; `COMPLETADA` en cualquier momento después de `EN_ATENCION`. | **MJ-17** |
| RN-13 | `precio_ref` y `duracion_ref` se copian del servicio al crear la reserva y no cambian aunque cambie el catálogo. | [Inf p. 9, 35–36] |
| RN-14 | Cancelar libera la franja y conserva la fila histórica. | [Inf p. 8, 36] |
| RN-15 | Todo cambio de una reserva (creación, reprogramación, cancelación o cambio de estado) registra una auditoría (actor, fecha, acción, valores anterior y nuevo, motivo, si fue una excepción) y avisos internos **en la misma transacción**. Destinatarios: siempre el cliente; además, el barbero asignado (o los barberos anterior y nuevo) en creación, reprogramación, cancelación y confirmación, salvo que sea el propio actor. | [Inf p. 8–9, 28], [Corrida:Trazabilidad], **MJ-05** |
| RN-16 | No se borran físicamente usuarios, servicios ni barberos: se desactivan. Los bloqueos sí se pueden eliminar. | [Inf p. 27], [Proto] |
| RN-17 | Jornada: intervalos `[hora_inicio, hora_fin)` por día ISO (lunes = 1 … domingo = 7), sin solaparse entre sí en el mismo día. Un cambio de jornada no puede dejar fuera de jornada una reserva **futura** que ocupa franja. | [Corrida:Datos_base], [Inf p. 7, 31] |
| RN-18 | Bloqueo: `inicio < fin`, no puede empezar en el pasado y no cruza reservas que ocupan franja. | [Inf p. 7, 14], [LEEME] |
| RN-19 | Como máximo **10 barberos activos** y una sola sede. | [Inf p. 6] |
| RN-20 | Un cliente tiene como máximo **3 reservas futuras que ocupan franja** (parametrizable). El administrador no está sujeto a este límite en la reserva asistida. | **MJ-08** |
| RN-21 | La reserva por autoservicio queda `CONFIRMADA`. Si se activa el parámetro `confirmacion-manual`, las del cliente quedan `PENDIENTE`, ocupan franja y las confirma el barbero o el administrador. Las del administrador siempre quedan `CONFIRMADA`. | [Inf p. 25], **C-05** |
| RN-22 | Los reportes agrupan por la fecha de **inicio** de la reserva en Lima, rango `[desde, hasta]` inclusivo. Suma por estado = total. | [Inf p. 8, 31] |
| RN-23 | El precio es referencial y el pago es presencial, fuera del sistema. | [Inf p. 5] |
| RN-24 | El correo es único sin distinguir mayúsculas (se guarda en minúsculas). | [Inf p. 7], [Drawio:Datos] |
| RN-25 | Contraseña de 8 a 72 caracteres y como máximo 72 bytes en UTF-8 (límite de BCrypt; precisado en T-09), con al menos una letra y un dígito. 5 intentos fallidos seguidos bloquean el acceso durante 15 min. | **MJ-11** |
| RN-26 | El personal (barbero o administrador) no se registra solo: lo crea el administrador. Un administrador puede tener también un perfil de barbero (dueño que atiende). | [Inf p. 11], **MJ-09** |

Parámetros configurables (prefijo `barberturno.reservas.*`): `anticipacion-cambio-cliente=2h`, `horizonte-dias=30`, `rejilla-min=10`, `max-activas-por-cliente=3`, `confirmacion-manual=false`, `tolerancia-inicio-min=15`.

---

## 5. Registro de mejoras aplicadas

Cada mejora queda incorporada en los requisitos, en la arquitectura y en las tareas. **Para añadir una mejora** se crea la fila MJ-xx aquí, se enlazan los RF, RNF y RN afectados, se actualizan [arquitectura.md](arquitectura.md) y [tareas.md](tareas.md) y se anota la fecha.

| ID | Fecha | Mejora | Motivo | Afecta |
|---|---|---|---|---|
| MJ-01 | 01/10/2026 | La reprogramación puede cambiar de barbero, no solo la fecha y la hora. | El prototipo ya lo permite y la p. 28 prevé "dos profesionales en una misma operación". Se formaliza con un orden de bloqueo para evitar interbloqueos. | RF-09, RN-09, T-23 |
| MJ-02 | 01/10/2026 | Jornada como varios intervalos por día (modelo de la corrida) en lugar de jornada + descanso (prototipo). | Permite partir el día, descansos múltiples y días libres con un solo modelo, sin columnas especiales. | RF-06, RN-17, T-15 |
| MJ-03 | 01/10/2026 | Se fija la definición de "ocupa franja" (estado ≠ CANCELADA) y se usa en disponibilidad, bloqueos, jornadas y la restricción de exclusión. | El prototipo usaba criterios distintos para bloqueos y jornadas (C-08). | RN-03/17/18 |
| MJ-04 | 01/10/2026 | Las fechas se guardan como instantes (`timestamptz`) y se presentan siempre en America/Lima con un `Clock` inyectable. | Evita errores por la zona del navegador o del servidor y permite pruebas deterministas (escenario 28/09/2026 09:00). | RNF-13, T-08 |
| MJ-05 | 01/10/2026 | Se formalizan RF-16 (avisos) y RF-17 (auditoría), y se define a quién llegan los avisos. | El APF2 los mencionaba dentro de RF-08…RF-12 sin criterios propios ni destinatarios claros. | RF-16/17, RN-15 |
| MJ-06 | 01/10/2026 | Se impide que un cliente tenga reservas solapadas (también en la base de datos). | Evita reservas simultáneas con dos barberos, un abuso que bloquea agenda. Cuesta una restricción más. | RN-04, T-06, T-20 |
| MJ-07 | 01/10/2026 | Catálogo, barberos activos y disponibilidad se pueden consultar sin sesión; para confirmar sí hace falta. | Mejora la conversión: el cliente ve horarios antes de registrarse. No expone datos personales. | RF-04, RF-05, RF-07 |
| MJ-08 | 01/10/2026 | Horizonte de reserva de 30 días y máximo de 3 reservas futuras por cliente. | Evita que la agenda se acapare y acota el cálculo de disponibilidad (RNF-01). | RN-05, RN-20 |
| MJ-09 | 01/10/2026 | Cuentas del personal con contraseña temporal, cambio obligatorio y restablecimiento por el administrador. | El APF2 dice que el administrador crea al personal [Inf p. 11] pero no cómo recibe su acceso. Sin correo saliente es el mecanismo más simple. | RF-05, RF-15, RF-19 |
| MJ-10 | 01/10/2026 | Reserva asistida por el administrador. | Cubre las reservas por teléfono o en el local y la conciliación del registro manual en caso de caída, previstas en el plan de continuidad [Inf p. 4]. | RF-18 |
| MJ-11 | 01/10/2026 | Política de contraseñas (≥ 8) y bloqueo temporal por intentos fallidos. | Mitiga la fuerza bruta. El prototipo exigía solo 6 caracteres. | RN-25, RNF-03 |
| MJ-12 | 01/10/2026 | Aviso y consentimiento de datos personales (Ley 29733). | Obligación legal en el Perú para un registro de clientes con teléfono y correo. | RF-01, RNF-12 |
| MJ-13 | 01/10/2026 | Accesibilidad básica WCAG 2.2 AA. | Mejora la usabilidad para todos y es verificable con herramientas automáticas. | RNF-11 |
| MJ-14 | 01/10/2026 | Bloqueo para todo el equipo (feriados). | Ahorra trabajo repetido al administrador. Reutiliza la lógica de bloqueos. *Podría* (C). | RF-20 |
| MJ-15 | 01/10/2026 | Disponibilidad "sin preferencia de barbero". | Es común que el cliente no tenga preferencia. *Podría* (C). | RF-21 |
| MJ-16 | 01/10/2026 | Logs sin datos sensibles y endpoint de salud. | Da soporte a RNF-02 y RNF-12. | RNF-14 |
| MJ-17 | 01/10/2026 | Ventanas de tiempo para iniciar la atención o marcar no asistió. | Evita marcar una inasistencia antes de la hora o iniciar con días de antelación (calidad de datos). | RN-12 |
| MJ-18 | 01/10/2026 | Errores con formato estándar RFC 9457 (Problem Details) y códigos de negocio estables. | La interfaz puede reaccionar a cada caso (p. ej., recargar franjas ante `FRANJA_NO_DISPONIBLE`). | arquitectura §6 |

---

## 6. Contradicciones detectadas y resolución

| ID | Contradicción o vacío | Fuentes | Resolución |
|---|---|---|---|
| C-01 | En el PDF, la tabla de RNF muestra los IDs separados de sus metas. | [Inf p. 9] vs [Inf p. 28] | Se reconstruye el orden RNF-01…RNF-10 según la tabla de componentes (S-01). |
| C-02 | Jornada: el prototipo usa 1 intervalo + descanso; el modelo E-R y la corrida usan varios intervalos por día. | [Proto] vs [Drawio:Datos], [Corrida:Datos_base] | Se adopta el modelo E-R (MJ-02). |
| C-03 | Numeración del día: el prototipo usa domingo = 0; la corrida, lunes = 1. | [Proto] vs [Corrida:Datos_base] | ISO 8601: lunes = 1 … domingo = 7. |
| C-04 | RF-09 dice "fecha u hora", pero el prototipo y la arquitectura permiten cambiar de barbero. | [Inf p. 8] vs [Proto], [Inf p. 28] | Se permite el cambio de barbero (MJ-01). |
| C-05 | Existe el estado `PENDIENTE` y el prototipo lo muestra, pero ningún flujo lo crea ("el autoservicio confirma inmediatamente"). | [Inf p. 25], [Proto] | Parámetro `confirmacion-manual` (por defecto `false`). Se conserva el estado y su flujo (RN-21). |
| C-06 | El prototipo deja editar el correo; RF-03 habla de datos "no sensibles". | [Proto] vs [Inf p. 7] | El correo no se edita (es la credencial). Si hace falta cambiarlo, lo hace el administrador (fuera del MVP). |
| C-07 | Contraseña mínima de 6 en el prototipo; el informe no dice nada. | [Proto] | 8–72 con letra y dígito (MJ-11). |
| C-08 | Para validar un bloqueo, el prototipo excluye solo las canceladas; para validar una jornada, solo considera pendiente, confirmada y en atención. | [Proto] | Criterio único "ocupa franja" (MJ-03). Las jornadas solo se validan contra reservas futuras. |
| C-09 | Avisos: el prototipo solo avisa al cliente; el modelo tiene `usuario_id` genérico. | [Proto] vs [Drawio:Datos] | Destinatarios definidos en RN-15. |
| C-10 | El administrador puede cambiar cualquier cita en el prototipo, incluso las ya iniciadas o pasadas. | [Proto] | Solo antes del inicio (RN-08). Las pasadas se resuelven con transiciones de estado. |
| C-11 | La corrida avisa al cliente incluso de su propia acción; el prototipo muestra "Notificación: BT-101 confirmada". | [Corrida:Trazabilidad], [Drawio:A1-P03] | Se mantiene: el cliente siempre recibe aviso (confirmación interna). Al barbero no se le avisa de sus propias acciones. |
| C-12 | Los mockups P06 muestran "especialidad", pero todos los barberos ofrecen todo el catálogo. | [Drawio:A1-P06] vs [Inf p. 35] | La especialidad es un texto descriptivo. No hay relación barbero-servicio en el MVP. |
| C-13 | El anexo Java usa JDK 17; se decide usar Java 21 (ver arquitectura). | [Java], [LEEME] | Es compatible: la clase del anexo se porta sin cambios de API. |
| C-14 | RF-16 pide que **cada usuario** consulte y marque sus avisos (pantalla «P03, cabecera») y CP-18 que el barbero «solo vea los suyos», pero la interfaz solo ofrece el panel de avisos en Mis citas (P03, solo CLIENTE): el BARBERO recibe avisos (RN-15) y ve el contador sin poder leerlos. | RF-16, CP-18, RN-15 vs arquitectura §4.2 y T-27 (detectado en T-48; análisis g018, T-50, 06/10/2026) | **Requisito pendiente para BARBERO** (decisión del responsable, 06/10/2026): lee y marca sus avisos desde el contador de la cabecera reutilizando el panel existente (T-51, DA-25). El ADMIN no recibe avisos por RN-15: no se añade nada para él. |

## 7. Supuestos

| ID | Supuesto |
|---|---|
| S-01 | La correspondencia RNF-ID ↔ meta es la de §3 (reconstruida con [Inf p. 28]). |
| S-02 | Lima no aplica horario de verano. UTC−5 fijo. |
| S-03 | Hay una sola sede y un solo administrador habitual, aunque el sistema admite varios administradores. |
| S-04 | El teléfono se valida como 9 dígitos sin prefijo internacional. |
| S-05 | No hay envío de correos. Las contraseñas temporales se comunican en persona. |
| S-06 | El volumen esperado es de decenas de reservas al día: una sola instancia de backend basta. |
| S-07 | La reserva por autoservicio no requiere aprobación (`confirmacion-manual=false`). |

## 8. Datos y decisiones pendientes (fuera de mi autonomía)

| ID | Pendiente | Quién decide | Impacto si no se resuelve |
|---|---|---|---|
| P-01 | Validar reglas, precios, jornadas y el límite de 2 h con un representante de la barbería [Inf p. 3, 24]. | Estudiante / negocio | Se usan los valores académicos actuales. |
| P-02 | ~~Confirmar con el docente el método de conteo del % Java (RA-02).~~ **Resuelta el 04/10/2026 por el responsable:** el % de Java no es un requisito de aprobación; el requisito real (backend en Java) ya se cumple. RA-02 queda como métrica informativa (con T-18 integrada, 57,50 % sin pruebas y 72,74 % con pruebas). | Estudiante | — |
| P-03 | Proveedor de alojamiento, dominio y certificado TLS para producción (RNF-02, RNF-03). | Estudiante | T-33 deja todo listo para cualquier host con Java 21 + PostgreSQL. |
| P-04 | ~~Crear el repositorio remoto (GitHub) para activar la CI.~~ **Resuelta el 05/10/2026:** repositorio público https://github.com/sohnny-lima/BarberTurno; primer CI verde en la ejecución 37314699514 (ver `docs/pruebas/ci-primer-run.md`). | Estudiante | — |
| P-05 | Fechas reales de los siguientes avances y de la entrega final (la línea base propone el 20/12/2026). | Docente | Se usa el Gantt del APF2. |
| P-06 | Política ante inasistencias (¿penalización?). | Negocio | Solo se registra `NO_ASISTIO`. |
| P-07 | Participantes para la prueba de usabilidad (5 personas, CP-11). | Estudiante | CP-11 queda sin evidencia. |
| P-08 | `docs/apf1/` está vacía. Aportar el informe del APF1 (citado en [Inf p. 34], ref. [1]) para contrastar el Lean Canvas, la estimación de 420 h y el SRS original. | Estudiante | Este análisis se basa solo en el APF2, que declara conservar los IDs del APF1. |

---

## 9. Matriz de trazabilidad

| Req. | Pantalla A1 | API principal (ver arquitectura §6) | Tareas | Casos de prueba |
|---|---|---|---|---|
| RF-01 | P01 | `POST /api/auth/registro` | T-10, T-12 | CP-01 |
| RF-02 | P01 | `POST /api/auth/login`, `/logout`, `GET /api/auth/sesion` | T-10, T-12 | CP-02, CP-14 |
| RF-03 | P01 | `GET/PUT /api/perfil` | T-11, T-12 | CP-01 |
| RF-04 | P05 | `/api/servicios` | T-13, T-17 | CP-08 |
| RF-05 | P06 | `/api/barberos` | T-14, T-17 | CP-08 |
| RF-06 | P07 | `/api/barberos/{id}/jornadas`, `/bloqueos` | T-15, T-16, T-18 | CP-07, CP-16 |
| RF-07 | P02 | `GET /api/disponibilidad` | T-19, T-26 | CP-04, CP-07 |
| RF-08 | P02, P03 | `POST /api/reservas` | T-20, T-26 | CP-03, CP-04, CP-13, CP-15 |
| RF-09 | P03, P02 | `POST /api/reservas/{id}/reprogramacion` | T-23, T-26, T-27 | CP-05 |
| RF-10 | P03 | `POST /api/reservas/{id}/cancelacion` | T-22, T-27 | CP-06 |
| RF-11 | P04 | `GET /api/reservas` (agenda) | T-21, T-28 | CP-02 |
| RF-12 | P04 | `POST /api/reservas/{id}/transiciones` | T-24, T-28 | CP-09 |
| RF-13 | P03, P08 | `GET /api/reservas/mias`, `GET /api/reservas` | T-21, T-27, T-30 | CP-02, CP-10 |
| RF-14 | P08 | `GET /api/reportes/resumen` | T-29, T-30 | CP-10 |
| RF-15 | P01 | `PUT /api/auth/password` | T-11, T-12 | CP-17 |
| RF-16 | P03 | `/api/notificaciones` | T-25, T-27 | CP-18 |
| RF-17 | P04 | `GET /api/reservas/{id}/auditoria` | T-25, T-28 | CP-09 |
| RF-18 | P02 | `POST /api/reservas` con `clienteId` | T-31 | CP-19 |
| RF-19 | P06 | `/api/usuarios` | T-31 | CP-17 |
| RF-20 | P07 | `POST /api/bloqueos/lote` | T-16 (opcional) | CP-07 |
| RF-21 | P02 | `GET /api/disponibilidad` sin `barberoId` | T-19 (opcional) | — |
| RNF-01 | — | disponibilidad | T-35 | CP-12 |
| RNF-02 | — | operación (`/actuator/health`) | T-33, T-36 | — (medición tras el despliegue, P-03) |
| RNF-03/04/12 | — | seguridad | T-10, T-21, T-33 | CP-02, CP-12 |
| RNF-05 | — | reservas | T-06, T-20, T-23 | CP-03 |
| RNF-06/07/08/11 | todas | — | T-34 | CP-11 |
| RNF-09 | — | — | T-36 | CP-12 |
| RNF-10 | — | — | T-04, T-09 | CP-12 |
| RNF-13 | todas | tiempo (`TiempoNegocio`, `core/tiempo`) | T-08, T-18, T-26, T-28, T-34 | E2E T-34 (navegador en Europe/Madrid) |
| RNF-14 | — | observabilidad | T-08, T-33 | CP-12 |

### 9.1 Casos de prueba de aceptación
CP-01…CP-12 se conservan del APF2 [Inf p. 31]. Se añaden casos para las mejoras:

| Caso | Acción | Resultado esperado |
|---|---|---|
| CP-13 | El mismo cliente reserva a la misma hora con dos barberos distintos. | La segunda reserva se rechaza con `CLIENTE_CON_RESERVA_SOLAPADA`. |
| CP-14 | 5 intentos de acceso fallidos y luego la contraseña correcta. | Acceso bloqueado 15 min con un mensaje genérico; después se desbloquea. |
| CP-15 | Un cliente con 3 reservas futuras intenta una cuarta. | 422 `LIMITE_RESERVAS_ACTIVAS`. |
| CP-16 | Jornada con intervalos solapados o con inicio ≥ fin. | 400 `JORNADA_INVALIDA`; la jornada anterior se conserva. |
| CP-17 | Cambio o restablecimiento de contraseña con una sesión abierta en otro navegador. | La sesión anterior recibe 401 en la siguiente petición. |
| CP-18 | Crear, cancelar y confirmar una reserva. | El cliente y el barbero reciben los avisos según RN-15 y solo ven los suyos. |
| CP-19 | El administrador reserva para un cliente en una franja ocupada y luego en una libre. | Primero 409; después 201, con el administrador como actor en la auditoría. |
