// Renderiza páginas de un PDF a PNG con pdf.js dentro de Chromium. Uso: node pdf-a-png.mjs <pdf> <dirSalida> [desde] [hasta] [escala]
import { createRequire } from 'module';
import fs from 'fs'; import path from 'path'; import http from 'http';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');
const [pdf, out, desde = '1', hasta = '999', escala = '1.3'] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const base = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1')), 'node_modules', 'pdfjs-dist', 'legacy', 'build');
const servidor = http.createServer((req, res) => {
  if (req.url === '/doc.pdf') { res.setHeader('Content-Type', 'application/pdf'); return res.end(fs.readFileSync(pdf)); }
  const f = path.join(base, path.basename(req.url));
  if (req.url.endsWith(".mjs") && fs.existsSync(f)) { res.setHeader('Content-Type', 'text/javascript'); return res.end(fs.readFileSync(f)); }
  res.setHeader('Content-Type', 'text/html');
  res.end('<html><body style="margin:0;background:#888"><canvas id="c"></canvas></body></html>');
}).listen(0);
const puerto = servidor.address().port;
const b = await chromium.launch(); const p = await b.newPage();
await p.goto(`http://127.0.0.1:${puerto}/`);
const total = await p.evaluate(async () => {
  const pdfjs = await import('/pdf.mjs');
  pdfjs.GlobalWorkerOptions.workerSrc = '/pdf.worker.mjs';
  window.docPdf = await pdfjs.getDocument('/doc.pdf').promise;
  return window.docPdf.numPages;
});
console.log('paginas', total);
for (let i = +desde; i <= Math.min(+hasta, total); i++) {
  const dim = await p.evaluate(async ({ i, escala }) => {
    const pg = await window.docPdf.getPage(i); const vp = pg.getViewport({ scale: escala });
    const c = document.getElementById('c'); c.width = vp.width; c.height = vp.height;
    await pg.render({ canvasContext: c.getContext('2d'), viewport: vp }).promise; return [vp.width, vp.height];
  }, { i, escala: +escala });
  await p.setViewportSize({ width: Math.ceil(dim[0]), height: Math.ceil(dim[1]) });
  await p.locator('#c').screenshot({ path: path.join(out, `p${String(i).padStart(2, '0')}.png`) });
}
await b.close(); servidor.close();
