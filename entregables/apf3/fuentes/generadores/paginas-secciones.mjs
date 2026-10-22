// Devuelve en JSON la página del PDF donde empieza cada sección numerada (busca el título en el texto de cada página).
import { createRequire } from 'module';
import http from 'http'; import fs from 'fs'; import path from 'path';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');
const pdf = process.argv[2];
const base = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1')), 'node_modules', 'pdfjs-dist', 'legacy', 'build');
const srv = http.createServer((q, r) => {
  if (q.url === '/doc.pdf') return r.end(fs.readFileSync(pdf));
  if (q.url.endsWith('.mjs')) { r.setHeader('Content-Type', 'text/javascript'); return r.end(fs.readFileSync(path.join(base, path.basename(q.url)))); }
  r.setHeader('Content-Type', 'text/html'); r.end('<html></html>');
}).listen(0);
const b = await chromium.launch(); const p = await b.newPage();
await p.goto(`http://127.0.0.1:${srv.address().port}/`);
const textos = await p.evaluate(async () => {
  const pdfjs = await import('/pdf.mjs'); pdfjs.GlobalWorkerOptions.workerSrc = '/pdf.worker.mjs';
  const d = await pdfjs.getDocument('/doc.pdf').promise; const out = [];
  for (let i = 1; i <= d.numPages; i++) { const t = await (await d.getPage(i)).getTextContent(); out.push(t.items.map((x) => x.str).join(' ')); }
  return out;
});
const titulos = {1:'1. Continuidad del proyecto',2:'2. Problema, objetivos',3:'3. Arquitectura implementada',4:'4. Diseño de la solución',5:'5. Seguridad aplicada',6:'6. Persistencia, integridad',7:'7. Recursos Java',8:'8. Interfaces gráficas',9:'9. Matriz requisito',10:'10. Control de versiones',11:'11. Pruebas y resultados',12:'12. Despliegue en Azure',13:'13. Estado de aceptación',14:'14. Autoría',15:'15. Propuesta de sustentación',16:'16. Conclusiones'};
const norm = (t) => t.replace(/\s+/g, '');
const secciones = {};
for (const [n, t] of Object.entries(titulos)) { const i = textos.findIndex((x, k) => k > 2 && norm(x).includes(norm(t))); secciones[n] = i >= 0 ? i + 1 : null; }
console.log(JSON.stringify({ paginas: textos.length, secciones }));
await b.close(); srv.close();
