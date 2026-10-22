// Hojas de contacto: node contactos.mjs <dirPng> <dirSalida> [porHoja=6] [columnas=3] [anchoCelda=600]
import { createRequire } from 'module';
import fs from 'fs'; import path from 'path';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');
const [dir, out, porHoja = '6', cols = '3', ancho = '600'] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const pngs = fs.readdirSync(dir).filter((f) => f.endsWith('.png')).sort();
const b = await chromium.launch(); const p = await b.newPage();
for (let i = 0; i < pngs.length; i += +porHoja) {
  const grupo = pngs.slice(i, i + +porHoja);
  const html = `<html><body style="margin:0;background:#666;display:grid;grid-template-columns:repeat(${cols},${ancho}px);gap:8px;padding:8px;width:max-content">` +
    grupo.map((f) => `<div style="background:#fff"><div style="font:14px sans-serif;background:#222;color:#fff;padding:2px 6px">${f}</div><img style="width:${ancho}px;display:block" src="data:image/png;base64,${fs.readFileSync(path.join(dir, f)).toString('base64')}"></div>`).join('') + '</body></html>';
  await p.setViewportSize({ width: 400, height: 400 });
  await p.setContent(html);
  await p.screenshot({ path: path.join(out, `hoja${String(i / +porHoja + 1).padStart(2, '0')}.png`), fullPage: true });
}
await b.close();
console.log('hojas', Math.ceil(pngs.length / +porHoja));
