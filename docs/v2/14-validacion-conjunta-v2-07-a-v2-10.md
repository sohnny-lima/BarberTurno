# BarberTurno v2 · 14 · Validación conjunta local de V2-07 a V2-10 y corrección de H-116

> **Autorizado por el responsable el 04/11/2026:**
> - corregir H-116-01/02 (V2-09);
> - reunir **temporalmente** V2-07 y la línea de V2-10 (con V2-08, V2-09 y la corrección) en una rama de revisión;
> - validar el conjunto en local.
>
> **No autoriza** integrar en `v2`, publicar, enviar mensajes reales ni aprobar V2-07: la comprobación funcional de V2-07 por el responsable sigue pendiente. V2-11 no empieza.
>
> Ramas originales intactas: V2-07 `3ded886`, V2-08 `233dfbf`, V2-09 `d4bc401`, V2-10 `59f50cf`, `v2` `ccdbb29`, `main` `efd52aa`.

## 1. Ramas y worktrees
| Rama | Worktree | Base | Para qué | Responsable |
|---|---|---|---|---|
| `tarea/V2-09-whatsapp-salida-presupuesto` | `D:\Proyectos\BarberTurno-v2-09-presupuesto` | `d4bc401` + contrato `9aca981` | corrección de H-116 (V2-DA-70) | Codex (encargo 122) |
| `tarea/V2-10-conversacion-guiada` | `D:\Proyectos\BarberTurno-v2-10` | `59f50cf` | recibe la corrección por **unión** (sin copiarla) | Claude |
| `tarea/V2-07-10-validacion-conjunta` | `D:\Proyectos\BarberTurno-v2-conjunta` | línea de V2-10 + V2-07 (`58c52ea`) | rama de revisión: unión, conflictos, tipos, validación | Claude |
| `tarea/V2-07-10-validacion-conjunta-gemini` | `D:\Proyectos\BarberTurno-v2-conjunta-gemini` | `58c52ea` | entorno de revisión, limpieza, avisos y pruebas de integración | Gemini |

Orden de las uniones:
1. presupuesto → línea de V2-10;
2. línea de V2-10 → rama conjunta;
3. rama de Gemini → rama conjunta.

La corrección entra en la rama conjunta **una sola vez**, por la línea de V2-10.

## 2. Responsables por archivo
| Archivos | Responsable | Revisa |
|---|---|---|
| `whatsapp/{EnviadorMensajesWhatsApp,ColaSalidaWhatsApp,MensajeWhatsApp,SalidaWhatsAppPropiedades}.java`, `application.yml` (`max-inciertos`), sus pruebas en `whatsapp/`, `12-v2-09-whatsapp-salida.md` y `pruebas/v2-09.md` (rama del presupuesto) | **Codex** | Gemini Argon (*diff-first*) y Claude |
| Contrato V2-DA-70; uniones; conflictos de `00-README`, `07-decisiones` y `10-backlog`; `ResolutorNegocio`/`SecurityConfig`/`ErrorCodigo`/YAML tras la unión; `frontend/src/app/core/modelos/reservas.ts` y sus *fixtures* (`shared/reserva-prueba.ts`, `reserva.store.spec.ts`, `reservar.spec.ts`); este documento y las evidencias | **Claude** | Codex (solo lectura) |
| `frontend/e2e/base-datos.ts` (`vaciar()`); `frontend/src/app/layout/shell.{html,ts,scss,spec.ts}` (avisos); entorno de revisión versionado (`frontend/tools/revision-conjunta.ps1`, `frontend/tools/revision/*.mjs`); pruebas de integración nuevas de la validación conjunta | **Gemini** | Codex (solo lectura) y Claude |
| Ejecución de todas las pruebas de Gemini, del `verify` completo, de las suites del *frontend* y de los E2E | **Claude** (Gemini no puede ejecutar órdenes) | — |

Migraciones: **ninguna nueva**. No se edita ninguna migración aplicada.

## 3. Resoluciones de la unión (`58c52ea`)
- **Conflictos de texto** (solo documentos):
  - `07-decisiones.md`: V2-DA-42…51 (V2-07), seguidas de V2-DA-52…69 (V2-08…10), sin números solapados;
  - `00-README.md`: filas de V2-08 y de incidencias de v1;
  - `10-backlog.md`: V2-06 integrada y V2-07 de su rama; V2-08…10 de la línea de V2-10; las notas de continuación de V2-07 se conservan.
- **Código** (unido sin conflictos textuales, revisado a mano):
  - `ResolutorNegocio.esGlobal` conserva el *webhook* de WhatsApp (V2-08), el alta de canales bajo `/api/plataforma` y las capacidades de invitación (V2-07); sin alias de rutas antiguas (V2-07.5);
  - `SecurityConfig`, `ErrorCodigo` y los YAML combinan los dos lados.
- **Tipos del *frontend*** (V2-DA-69): `ReservaDto.cliente` pasa a `{ id: number | null; fichaId: number; nombre; telefono? }`. `id` sigue siendo la cuenta web (nulo en fichas de WhatsApp sin cuenta). El *frontend* solo usa nombre y teléfono, y la única comprobación de `cliente.id` (E2E de registro) sigue siendo válida.

## 4. Validación prevista (sobre el commit combinado que se identifique)
- **Backend:** `verify` completo (JaCoCo y Javadoc).
- **Frontend:** lint, formato, unitarias y *build*.
- **E2E:** recorridos de V2-07, con cambio de barbería y de papel, y avisos (contador, lista y lectura) para cliente, profesional, encargado y propietario.
- **Conversación guiada local:** escenarios de V2-10 (reserva, consulta, reprogramación, cancelación, aislamiento, suspensión, concurrencia y confirmaciones antiguas o repetidas), con eventos insertados y la outbox local; **sin Meta**.
- **Entorno de revisión:** cierre normal de ventanas, cierre abrupto de la consola, recuperación y reapertura.
- **Regresiones de los defectos de producción de V2-10:** cada uno con su prueba (§5).

## 5. Defectos de producción hallados en V2-10 y su regresión
Tabla completa en [`pruebas/v2-conjunta.md`](pruebas/v2-conjunta.md) §5: cada defecto con la regresión que falla sin su corrección.

## 6. Decisiones pendientes del responsable
Ver `15-decisiones-pendientes-p2-05-p2-06.md`.
