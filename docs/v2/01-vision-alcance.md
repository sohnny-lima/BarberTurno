# BarberTurno v2 · 01 · Visión, alcance inicial y funciones aplazadas

> Estado: **propuesta para aprobación** · 21/10/2026 · Autor: Claude Code (arquitecto) · Rama `tarea/V2-00-especificacion`
> Nada de este documento está implementado. La versión académica (v1, `main`, código `3bcd5d0`) no cambia.

## 1. Visión
BarberTurno v2 es una **plataforma para varias barberías independientes**. Cada barbería (*negocio*) gestiona sus servicios, profesionales, horarios, clientes y reservas con los datos separados de los demás negocios. Sus clientes reservan **desde la web o desde WhatsApp**, en lenguaje natural, con **las mismas reglas de disponibilidad, integridad y permisos** que hoy protege v1 (arquitectura v1 §8).

Principio rector: **la IA interpreta y conversa; el backend decide y ejecuta.** Ninguna operación se da por hecha en el chat hasta que el servidor la ha confirmado en la base de datos.

## 2. Actores
| Actor | Ámbito | Descripción |
|---|---|---|
| Administrador de la plataforma | Plataforma | Da de alta negocios e invita a su propietario. Suspende o reactiva negocios. **No ve** clientes, reservas ni conversaciones de los negocios en el MVP. |
| Propietario | Negocio | Responsable del negocio. Todo lo del encargado, más: gestionar el equipo (incluidos otros propietarios), conectar WhatsApp y la configuración del negocio. Siempre hay al menos uno activo. |
| Encargado | Negocio | Opera el negocio: catálogo, profesionales, horarios, bloqueos, reservas de cualquier cliente, reportes, bandeja de WhatsApp e invitaciones de barberos y encargados. |
| Barbero | Negocio | Su agenda, sus horarios y bloqueos (lectura), sus avisos y las transiciones de sus citas. |
| Cliente | Negocio | Ficha del cliente **dentro de cada negocio**. Reserva, consulta, reprograma y cancela sus citas por la web (con cuenta) o por WhatsApp (identificado por su número). |
| Persona usuaria | Plataforma | Cuenta global (correo y contraseña). Una misma persona puede ser propietaria de un negocio, barbera en otro y cliente en un tercero. |
| Meta (WhatsApp Cloud API) | Externo | Entrega los mensajes entrantes por *webhook* y envía los salientes. |
| Proveedor de IA | Externo | Interpreta mensajes con herramientas definidas por el backend. Sin acceso a la base de datos. |

## 3. Objetivos medibles del MVP
1. **Aislamiento:** 0 filtraciones entre negocios en la batería de pruebas de aislamiento (base, API y conversación).
2. **Integridad:** las pruebas de concurrencia de v1 (CP-03) siguen dando exactamente 1 éxito con N solicitudes simultáneas, también cuando una llega por WhatsApp y otra por la web.
3. **Conversación:** en la evaluación de conversaciones, 0 horarios inventados, 0 confirmaciones falsas, 0 filtraciones entre negocios y ≥ 90 % de tareas completadas o derivadas correctamente (`08-pruebas-migracion-reversion.md` §4).
4. **Coste:** coste variable (WhatsApp + IA) por barbería piloto por debajo del umbral que fije el responsable; la estimación inicial está en `09-costes.md`.
5. **Continuidad:** v1 y su evidencia académica intactas (`main`, Azure en `d2f3107`, `docs/apf2/`, `entregables/`).

## 4. Alcance inicial (MVP)
El MVP se construye en cuatro fases. Cada fase deja un sistema funcionando y revisable (detalle y criterios en `10-backlog.md`).

| Fase | Contenido | Resultado verificable |
|---|---|---|
| **0 · Fundación multinegocio** | Entorno aislado; negocio por defecto; `negocio_id` + RLS; membresías y rol de plataforma; ficha de cliente por negocio; adaptación de catálogo, agenda, reservas, avisos, auditoría y reportes; alta de negocio, invitaciones y protección del último propietario; selector de negocio y página pública por negocio | Dos negocios de prueba operan a la vez sin verse; v1 funciona igual como «negocio por defecto» |
| **1 · WhatsApp guiado (sin IA)** | Canal por negocio, *webhook* firmado e idempotente, cola de procesamiento, envío con reintentos y estados, menús interactivos (servicios, disponibilidad, reservar con confirmación por botón, mis citas, cancelar, reprogramar), consentimiento, derivación a una persona y bandeja mínima | Una reserva completa por WhatsApp con el número de prueba de Meta, con las mismas reglas que la web |
| **2 · Conversación con IA** | Puerto de modelo con adaptador, herramientas de solo lectura y de *propuesta*, guardas de salida, límites de uso, evaluación de conversaciones (determinista en `verify` y en vivo bajo demanda) | «¿Carlos tiene algo mañana después de las cinco para un corte?» termina en una propuesta con fecha completa y confirmación explícita |
| **3 · Piloto** | Recordatorios con plantillas *utility* y opt-in; incorporación de números de otros negocios (Embedded Signup como Tech Provider); entorno de *staging* y plan de corte (sin ejecutarlo) | 1–3 barberías piloto, si el responsable lo autoriza y Meta aprueba la app |

**Fuera de la Fase 3** no hay despliegue de v2 en producción ni migración de los datos de v1: ambas cosas requieren una decisión explícita del responsable.

## 5. Funciones aplazadas (no se hacen en el MVP)
| Función | Motivo del aplazamiento |
|---|---|
| **Sucursales** (varias sedes de un mismo negocio) | Un negocio = una sede, como v1. La sucursal es un nivel más (horarios, personal y agenda por sede) que ninguna barbería piloto ha pedido. El modelo deja sitio: una sucursal sería una entidad hija de `negocio` con su propia FK en barbero y reserva (`07-decisiones.md` V2-DA-05). |
| Autorregistro público de barberías | Riesgo de abuso y de suplantación; el alta la hace el administrador de la plataforma tras validar al negocio. |
| Pagos, señales, suscripciones o facturación de la plataforma | Fuera de alcance por indicación del responsable. El precio sigue siendo referencial (RN-23). |
| Recuperación de contraseña por correo | Requiere un proveedor de correo (servicio externo): decisión pendiente P2-04. En el MVP: el propietario o encargado restablece a su equipo (como v1), la plataforma a los propietarios, y el cliente vinculado a WhatsApp recibe un enlace de un solo uso por ese canal. |
| Mensajes de marketing por WhatsApp | Coste por mensaje más alto, opt-in específico y riesgo para la calidad del número. Solo mensajes de servicio y *utility*. |
| Coexistencia con la app WhatsApp Business del negocio | Útil para el piloto, pero exige ser Tech Provider y no se puede probar con el número de prueba. Se evalúa en la Fase 3 como alternativa a la bandeja web. |
| Acceso de soporte de la plataforma a los datos de un negocio | Requiere consentimiento del negocio, auditoría y un rol de lectura temporal. No es necesario para operar. |
| Fusión de fichas de cliente (web ↔ WhatsApp ↔ creada por el personal) | El MVP vincula solo por pruebas seguras (enlace de un solo uso por WhatsApp). La fusión manual por el personal se aplaza. |
| Zonas horarias distintas de Lima | La columna existe (`negocio.zona_horaria`) pero el MVP la fija en `America/Lima` (todas las barberías objetivo están en Perú). |
| Numeración de reservas por negocio | Se conserva `BT-<id>` global; numerar por negocio añadiría un bloqueo de contador al protocolo ①②③. |
| n8n, Activepieces u otros orquestadores | Sin ventaja concreta para el MVP (`07-decisiones.md` V2-DA-11). |
| Microservicios, colas externas (Kafka, RabbitMQ) | El monolito con cola en PostgreSQL (`FOR UPDATE SKIP LOCKED`) basta para el volumen previsto. |
| Aplicación móvil nativa, multidioma, valoraciones, fotos de profesionales | Sin relación con el objetivo de v2. |

## 6. Supuestos (a validar)
| ID | Supuesto | Cómo se valida |
|---|---|---|
| S2-01 | Volumen por barbería piloto: ≤ 10 profesionales, ≤ 60 reservas/día, ≤ 300 conversaciones de WhatsApp/mes. | Datos del piloto. |
| S2-02 | Las barberías piloto aceptan que el cliente reserve por WhatsApp con un número de la barbería conectado a la plataforma. | Entrevista con el negocio (heredado de P-01). |
| S2-03 | Todas las barberías del MVP operan en `America/Lima` y cobran en soles. | Alta de cada negocio. |
| S2-04 | Cada barbería paga sus propios mensajes de WhatsApp a Meta (método de pago en su cuenta de mensajería); la plataforma solo paga la IA y el alojamiento. | Modelo de Tech Provider (`09-costes.md` §2). |
| S2-05 | La API de Meta permite el uso de IA en un asistente de reservas de un negocio concreto (no es un chatbot de propósito general). | Términos de la WhatsApp Business Solution vigentes (`09-costes.md` §5). |

## 7. Decisiones pendientes del responsable (fuera de la autonomía del arquitecto)
| ID | Decisión | Recomendación | Bloquea |
|---|---|---|---|
| P2-01 | Aprobar la estrategia de repositorio: rama `v2` + worktree en este repositorio (`07-decisiones.md` V2-DA-01). Crear la rama de integración `v2` y, opcionalmente, la etiqueta `v1.0-apf3` sobre `3bcd5d0`. | Sí a ambas | V2-01 |
| P2-02 | El repositorio es **público**. ¿v2 (código comercial y estrategia del producto) puede seguir siendo público? Si no, ese es el único motivo que justificaría un repositorio privado nuevo. | Decidir antes de subir la rama `v2` | Push de `v2` |
| P2-03 | Proveedor y modelo de IA. Recomendación: interfaz neutra y primer adaptador de Anthropic (SDK oficial de Java). El modelo se elige con la evaluación de la Fase 2 (`09-costes.md` §3). | Decidir tras V2-13 | V2-12 (solo la clave) |
| P2-04 | Proveedor de correo para invitaciones y recuperación (y su coste). | Aplazar; el MVP funciona sin correo | — |
| P2-05 | Cuenta de Meta: portafolio comercial de la plataforma, app de Meta y, para la Fase 3, verificación del negocio (puede exigir documentos de una entidad, p. ej. RUC). | Crear app y número de prueba al llegar a V2-08 | V2-08, V2-16 |
| P2-06 | Aviso de privacidad y consentimiento de v2 (Ley 29733 y su reglamento, D. S. 016-2024-JUS): roles de responsable y encargado del tratamiento entre la plataforma y cada barbería, transferencia de datos a Meta y al proveedor de IA. | Revisión del responsable (y del docente o asesor) antes del piloto | V2-10 (texto), V2-15 |
| P2-07 | Barberías piloto y su autorización por escrito. | — | Fase 3 |
| P2-08 | Alojamiento de *staging* de v2 (coste aparte del crédito de Azure for Students que usa v1). | Decidir en la Fase 3 | V2-17 |
| P2-09 | Levantar el freeze **solo en la rama `v2`** (en `main` sigue vigente). | Sí | V2-01 |
