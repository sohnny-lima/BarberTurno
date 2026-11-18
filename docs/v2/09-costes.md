# BarberTurno v2 · 09 · Estimación de costes

> 21/10/2026. Cifras en USD. Las tarifas cambian (Meta las revisa el primer día de cada trimestre): **revisar las fuentes de §7 antes de comprometer un presupuesto**. Los escenarios usan los supuestos S2-01…S2-04 (`01-vision-alcance.md` §6). Ningún coste de esta tabla se contrata sin la aprobación del responsable.

## 1. Resumen
| Concepto | Quién paga | Desarrollo (fases 0–2) | Piloto, por barbería y mes |
|---|---|---|---|
| Alojamiento de desarrollo | Plataforma | 0 (PostgreSQL y JDK locales) | — |
| Mensajes de WhatsApp | **Cada barbería** (método de pago en su cuenta de Meta) | 0 (número de prueba) | 3,5 – 28 (§2) |
| IA | Plataforma | ≤ 10 en total para la evaluación (presupuesto propuesto) | 1 – 39 según el modelo (§3) |
| *Staging* de v2 | Plataforma | 0 (no hay) | ≈ 13 – 28 al mes, para todo el piloto (§4, decisión P2-08) |
| Número de teléfono para WhatsApp | Cada barbería | 0 | Una línea que no esté en la app de WhatsApp (o coexistencia) |
| Meta: app, Tech Provider, verificación | — | 0 | 0 (no hay cuota de plataforma) |

## 2. WhatsApp (modelo por mensaje de Meta)
**Reglas vigentes (página oficial de precios, consultada el 21/10/2026):**
- Desde el 01/07/2025 se cobra **por mensaje entregado**, según la categoría y el prefijo del destinatario; los mensajes del cliente al negocio no se cobran.
- Categorías con plantilla: *marketing*, *utility*, *authentication*. Sin plantilla, solo dentro de la ventana de 24 h: *service* (y *Meta Business Agent*, por tokens, que no se usa).
- **Desde el 01/10/2026:** cada número tiene **1000 mensajes de servicio gratis al mes** (sin acumulación) y los siguientes se cobran; también se cobran las plantillas *utility* enviadas dentro de una ventana abierta (antes eran gratuitas). Perú (+51) es un mercado con tarifa propia y sus tarifas *utility* y *authentication* **subieron** el 01/10/2026; Perú se puede facturar en soles.
- Paga la cuenta de mensajería que envía (`messaging_account_id`), con su método de pago; en el modelo de Tech Provider es la de cada barbería.

**Tarifas de Perú usadas (USD por mensaje, vigentes desde el 01/10/2026):** *marketing* 0,0808 · *utility* 0,0345 · *authentication* 0,0345 · *service* (tras los 1000 gratis) 0,0345. Fuente: tarjeta de tarifas publicada por un BSP (Gallabox), que podría incluir su recargo; otra fuente secundaria da 0,03 para *utility*. **La tabla oficial de Meta por país no se pudo leer en texto: confirmar en la calculadora oficial antes del piloto.**

**Escenarios por barbería y mes** (supuesto de diseño: ≤ 5 mensajes salientes por conversación, agrupando información en listas y botones; un recordatorio *utility* por reserva con opt-in):
| Escenario | Conversaciones | Mensajes de servicio | Cobrados (> 1000) | Recordatorios | Coste |
|---|---|---|---|---|---|
| Bajo | 100 | 500 | 0 | 100 × 0,0345 | **≈ 3,45** |
| Medio (S2-01) | 300 | 1500 | 500 × 0,0345 = 17,25 | 300 × 0,0345 = 10,35 | **≈ 27,60** |
| Alto | 600 | 3000 | 2000 × 0,0345 = 69,00 | 600 × 0,0345 = 20,70 | **≈ 89,70** |

Palancas: agrupar mensajes (cada mensaje de más cuesta en el escenario medio); recordatorios solo con opt-in y solo uno por cita; sin *marketing* en el MVP. Las conversaciones iniciadas desde un anuncio *Click to WhatsApp* abren una ventana gratuita de hasta 7 días (no se usa en el MVP).

## 3. IA (por tokens; paga la plataforma)
**Supuestos por conversación con IA:** 6 llamadas al modelo (3 turnos con ida y vuelta de herramientas); entrada de ≈ 5500 tokens por llamada (instrucciones y herramientas ≈ 3000 + catálogo ≈ 800 estables y cacheables; estado e historial ≈ 1700); 70 % de la entrada leída de caché; una escritura de caché del prefijo estable (≈ 3800 tokens) por conversación; salida de ≈ 550 tokens por llamada (incluido el razonamiento con esfuerzo bajo). Total: ≈ 33 000 tokens de entrada y ≈ 3300 de salida.

**Precios (API de Anthropic, tabla de modelos vigente; caché: lectura ≈ 0,1× la entrada —0,05× en Opus 5.5—, escritura de 5 min 1,25×):**
| Modelo | Entrada / salida por millón | Lectura de caché | Coste por conversación | 300 conversaciones/mes |
|---|---|---|---|---|
| Claude Haiku 5.5 (`claude-haiku-5-5`) | 0,10 / 0,50 (prompts ≤ 100 000 tokens) | 0,01 | ≈ 0,0034 | **≈ 1,0** |
| Claude Sonnet 5.5 (`claude-sonnet-5-5`) | 2 / 10 | 0,20 | ≈ 0,067 | **≈ 20** |
| Claude Opus 5.5 (`claude-opus-5-5`) | 4 / 20 | 0,20 | ≈ 0,13 | **≈ 39** |

Margen de error: ± 50 % (longitud real de las conversaciones, esfuerzo, más escrituras de caché si el cliente tarda más de 5 min en responder). Los menús guiados no consumen IA. El modelo lo elige el responsable con la evaluación (`08-pruebas-migracion-reversion.md` §4.2): el criterio propuesto es **el más barato que cumpla todos los umbrales**; la diferencia de coste entre modelos (≈ 40×) hace que merezca la pena medirlo. Haiku 5.5 no tiene alternativa automática en el servidor si rechaza una petición: el orquestador pasa a menús.

**Evaluación en vivo:** 120 conversaciones × 2 rondas = 240 ejecuciones por modelo ≈ 0,8 (Haiku) + 16 (Sonnet) + 31 (Opus) ≈ 48 en el peor caso con los tres. Propuesta: una primera ronda con Haiku 5.5 y Sonnet 5.5 (≈ 0,4 + 8 ≈ 8,5), Opus 5.5 solo si ninguno cumple; **presupuesto máximo de 10 USD en la Fase 2 sin nueva aprobación**.

## 4. Alojamiento
- **Desarrollo:** local, coste 0. v2 no usa los recursos de Azure de v1 (V2-DA-17).
- ***Staging* o producción de v2 (P2-08, Fase 3):** referencia medida con v1: App Service Linux B1 en West US ≈ 13,14 USD/mes (`docs/cierre.md` §2). PostgreSQL Flexible B1ms: el beneficio gratuito de 12 meses ya lo usa v1, así que una segunda instancia se pagaría (estimación ≈ 13–15 USD/mes, **a confirmar en la calculadora de Azure**); alternativa más barata para *staging*: una base nueva en el mismo servidor PostgreSQL de v1 con roles separados (≈ 0 adicional, pero comparte recurso con la producción académica: no se recomienda hasta después del cálculo de RNF-02 del 01/12/2026).
- El *webhook* exige HTTPS público: en desarrollo, un túnel temporal (p. ej., Cloudflare Tunnel o ngrok en sus planes gratuitos; servicio externo que requiere autorización); en *staging*, el dominio de App Service.

## 5. Requisitos de Meta para incorporar otros negocios (comprobados el 21/10/2026)
| Requisito | Detalle | Coste |
|---|---|---|
| Portafolio comercial y app de Meta (tipo *Business*) con el caso de uso WhatsApp | De la plataforma (P2-05) | 0 |
| App Review con acceso avanzado | `whatsapp_business_management` y `whatsapp_business_messaging`; sin él no se pueden incorporar negocios | 0 (tiempo de revisión) |
| Modo *Live* y dominios permitidos | HTTPS y dominio del flujo en *Allowed domains* y *Valid OAuth redirect URIs* | — |
| Límite de incorporación | 10 negocios nuevos por semana (ventana móvil de 7 días) por defecto; 200 con verificación del negocio + App Review + Access Verification; más, solicitando ser *Meta Business Partner* | 0 |
| Embedded Signup | Usar **v4** (v2 y v3 retiradas el 15/10/2026) | 0 |
| Por cada barbería | Cuenta de Facebook o Meta Business, aceptar términos, portafolio y cuenta de WhatsApp, número verificado (no usado en la app, o coexistencia), nombre visible, **método de pago** en su cuenta de mensajería, límites de mensajería iniciales | Mensajes (§2) |
| Política de IA | Desde el 15/01/2026 los términos de la WhatsApp Business Solution prohíben los **chatbots de IA de propósito general** como función principal; los asistentes de un negocio para tareas concretas (atención, citas) siguen permitidos (S2-05). El asistente de v2 se limita a reservas y deriva lo demás | — |
| Opt-in | Antes de enviar mensajes proactivos: el cliente acepta recibir mensajes de ese negocio, con su nombre claramente indicado, y según la ley local; baja sencilla | — |

## 6. Costes no monetarios a considerar
- Tiempo de revisión de Meta (App Review y verificación) en la ruta crítica de la Fase 3.
- La verificación del negocio puede exigir documentación de una entidad legal (P2-05).
- Cumplimiento de la Ley 29733 y su reglamento (D. S. 016-2024-JUS, publicado el 30/11/2024): aviso de privacidad, roles de responsable y encargado y transferencias a Meta y al proveedor de IA (P2-06).

## 7. Fuentes (consultadas el 21/10/2026)
- Meta, *Pricing on the WhatsApp Business Platform*: https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing
- Meta, *Embedded Signup — Overview*: https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/overview/
- Meta, *Webhooks overview* (reintentos de hasta 7 días, duplicados, 3 MB, mTLS): https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/overview
- Meta, *Getting opt-in*: https://developers.facebook.com/documentation/business-messaging/whatsapp/getting-opt-in
- Gallabox, tarjeta de tarifas desde el 01/10/2026 (Perú): https://docs.gallabox.com/pricing-and-billing/whatsapp-pricing/rate-card.md
- respond.io, política de chatbots de IA de 2026 (fuente secundaria; confirmar en los términos de Meta): https://respond.io/blog/whatsapp-general-purpose-chatbots-ban
- Anthropic, precios y modelos: https://platform.claude.com/docs/en/about-claude/pricing · https://platform.claude.com/docs/en/about-claude/models/overview · caché: https://platform.claude.com/docs/en/build-with-claude/prompt-caching · SDK de Java: https://github.com/anthropics/anthropic-sdk-java
- n8n, preguntas frecuentes de la licencia: https://docs.n8n.io/n8n-community-license/community-license/license-faq · Activepieces, ediciones: https://activepieces.com/docs/about/editions
- Perú, D. S. 016-2024-JUS (Reglamento de la Ley 29733): https://www.gob.pe/institucion/anpd/normas-legales/6554453-16-2024-jus
- Coste de App Service medido en v1: `docs/cierre.md` §2 y `docs/p-03-alojamiento.md` §12.
