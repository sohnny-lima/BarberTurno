# BarberTurno v2 · Especificación y plan técnico

> **Estado: propuesta para aprobación del responsable** · 21/10/2026 · Rama `tarea/V2-00-especificacion` (worktree `D:\Proyectos\BarberTurno-v2`), creada desde `main` = `efd52aa`.
> Esta etapa **solo** analiza y especifica: no se ha modificado el producto, no se ha ejecutado ninguna migración y Azure sigue con `d2f3107`.

## 1. Qué es v2
Una plataforma para varias barberías independientes, con datos separados por negocio, roles de plataforma y de negocio, y reservas por la web o por WhatsApp con conversación natural, todo sobre las mismas reglas de disponibilidad e integridad de v1. Resumen de la visión y del MVP en `01-vision-alcance.md`.

## 2. Documentos
| # | Documento | Contenido |
|---|---|---|
| 01 | [Visión y alcance](01-vision-alcance.md) | Visión, actores, objetivos, MVP por fases, funciones aplazadas, supuestos y **decisiones pendientes del responsable (P2-01…P2-09)** |
| 02 | [Requisitos](02-requisitos.md) | Herencia de v1, V2-RF, V2-RN, V2-RNF y casos V2-CP |
| 03 | [Auditoría de impacto](03-auditoria-impacto.md) | Tablas, consultas, bloqueos, seguridad, frontend y pruebas de v1 afectados (auditoría de Codex 075) y respuesta a cada riesgo |
| 04 | [Arquitectura y modelo de datos](04-arquitectura-datos.md) | Módulos, DDL propuesto, RLS, contexto de negocio, concurrencia, WhatsApp, IA y entornos |
| 05 | [Roles y permisos](05-roles-permisos.md) | Rol de plataforma, membresías, fichas de cliente y matriz completa |
| 06 | [Flujos](06-flujos.md) | Alta de barbería, invitaciones, recuperación, varios negocios, conexión del número, WhatsApp de punta a punta, derivación y fallos |
| 07 | [Decisiones](07-decisiones.md) | V2-DA-01…18 con alternativas descartadas |
| 08 | [Pruebas, migración y reversión](08-pruebas-migracion-reversion.md) | Niveles de prueba, batería de aislamiento, evaluación de conversaciones, entorno aislado, migración futura, reversión y preservación de v1 |
| 09 | [Costes](09-costes.md) | WhatsApp, IA, alojamiento y requisitos de Meta, con fuentes y supuestos |
| 10 | [Backlog](10-backlog.md) | V2-01…V2-17 con dependencias y criterios, **recomendación de MVP** y **primera tarea (V2-01)** |

## 3. Reglas de trabajo en v2
- **Precedencia** (como v1): requisitos (`02`) > arquitectura (`04`, `05`, `07`) > backlog (`10`) > código. Lo que v2 no redefine se hereda de `docs/requisitos.md` y `docs/arquitectura.md`.
- **Ramas:** integración en `v2`; tareas en `tarea/V2-NN-…` desde `v2`; `main` es v1 académica en freeze y sus correcciones se integran hacia `v2` con `git merge main` (V2-DA-01).
- **Migraciones** de v2 en el rango `V100+` (`08-pruebas-migracion-reversion.md` §6.1).
- **No se tocan** desde v2: `docs/apf2/`, `entregables/`, las evidencias de v1, Azure ni las bases locales de v1.
- **Agentes:** Claude coordina, diseña y revisa; Codex implementa; Gemini revisa en solo lectura las tareas críticas (todas las de aislamiento, permisos, WhatsApp e IA). Un único implementador activo.

## 4. Revisiones de esta especificación
| Fecha | Revisor | Alcance | Resultado |
|---|---|---|---|
| 21/10/2026 | Codex (encargo 075, solo lectura) | Auditoría de impacto del código de v1 | Incorporada en `03`; hallazgo nuevo convertido en V2-RN-16 |
| 21/10/2026 | Gemini (g038, solo lectura) | Modelo de aislamiento, permisos y WhatsApp | *Pendiente* |
