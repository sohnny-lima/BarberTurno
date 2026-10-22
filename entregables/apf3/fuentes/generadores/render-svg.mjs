import { createRequire } from 'module';
import fs from 'fs'; import path from 'path';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');
const [dir, out] = process.argv.slice(2); fs.mkdirSync(out, { recursive: true });
const b = await chromium.launch(); const p = await b.newPage({ deviceScaleFactor: 2 });
for (const f of fs.readdirSync(dir).filter(f => f.endsWith('.svg'))) {
  const svg = fs.readFileSync(path.join(dir, f), 'utf8');
  const w = +svg.match(/width="(\d+)"/)[1], h = +svg.match(/height="(\d+)"/)[1];
  await p.setViewportSize({ width: w, height: h });
  await p.setContent(`<html><body style="margin:0">${svg}</body></html>`);
  await p.screenshot({ path: path.join(out, f.replace('.svg', '.png')), clip: { x: 0, y: 0, width: w, height: h } });
  console.log('ok', f);
}
await b.close();
