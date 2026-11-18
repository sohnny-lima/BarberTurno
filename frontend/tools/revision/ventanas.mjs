// Revisión conjunta (V2-07…V2-11, rama de revisión del MVP): dos ventanas visibles con sesiones independientes.
// - Escritorio: ventana real maximizada (viewport: null). La página usa el tamaño real de la
//   ventana, así que todo el contenido se alcanza desplazando, también con la escala de Windows
//   al 125 % (una ventana emulada de 900 px de alto no cabía y ocultaba la parte inferior).
// - Móvil: viewport emulado de 360 x 640, que cabe en la ventana.
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= fileURLToPath(
  new URL('../../tmp/navegadores', import.meta.url),
);

const navegador = await chromium.launch({ headless: false, args: ['--start-maximized'] });
const paginas = [];
for (const viewport of [null, { width: 360, height: 640 }]) {
  const contexto = await navegador.newContext({
    viewport,
    locale: 'es-PE',
    timezoneId: 'America/Lima',
  });
  const pagina = await contexto.newPage();
  await pagina.goto('http://localhost:8081/ingresar');
  paginas.push(pagina);
}
const real = await paginas[0].evaluate(() => `${innerWidth} x ${innerHeight}`);
console.log(
  `Ventanas abiertas: escritorio maximizada (${real} px) y móvil 360 x 640. Ciérrelas para terminar la revisión.`,
);
console.log(
  'Recorridos a mano: cambio de barbería (multirol/encargado), cambio de papel (personal-cliente), avisos y bandeja de WhatsApp V2-11 (ver frontend/tools/revision/LEEME.md).',
);
console.log(
  'Bandeja de WhatsApp (V2-11): revise en /n/barberturno/whatsapp con encargado@ejemplo.test o multirol@ejemplo.test (entrada «WhatsApp», responder dentro de la ventana de 24 h, botón deshabilitado fuera de ventana, «Devolver al asistente», aviso de derivación en la campana). Sin conexión real con Meta: las respuestas quedan en la outbox (PENDIENTE) y no salen a ningún teléfono. Profesional: sin la entrada «WhatsApp».',
);
await Promise.all(paginas.map((pagina) => pagina.waitForEvent('close', { timeout: 0 })));
await navegador.close();
