// Capturas recortadas y legibles de GitHub (páginas públicas, sin sesión). Complementa capturas-github.mjs.
import { createRequire } from 'module';
import fs from 'fs';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');
const OUT = process.argv[2];
const REPO = 'https://github.com/sohnny-lima/BarberTurno';
const C = '3bcd5d0';
// [nombre, url, ancho, alto de la ventana, recorte {x,y,width,height} o null, desplazamiento a línea]
const paginas = [
  ['gh10-reservarepository', `${REPO}/blob/${C}/backend/src/main/java/pe/barberturno/reservations/ReservaRepository.java?plain=1#L80-L122`, 1500, 1000, { x: 330, y: 0, width: 1150, height: 1000 }],
  ['gh09-securityconfig', `${REPO}/blob/${C}/backend/src/main/java/pe/barberturno/common/security/SecurityConfig.java?plain=1#L99-L172`, 1500, 1100, { x: 330, y: 0, width: 1150, height: 1100 }],
  ['gh11-migracion-exclude', `${REPO}/blob/${C}/backend/src/main/resources/db/migration/V1__esquema_inicial.sql?plain=1#L66-L92`, 1500, 800, { x: 330, y: 0, width: 1150, height: 800 }],
  ['gh14-commit-t49-rojo', `${REPO}/commit/21f86ad`, 1440, 760, { x: 0, y: 170, width: 1440, height: 560 }],
  ['gh15-commit-t49-verde', `${REPO}/commit/b60cbdc`, 1440, 760, { x: 0, y: 170, width: 1440, height: 560 }],
  ['gh05-actions', `${REPO}/actions`, 1440, 1000, { x: 0, y: 170, width: 1440, height: 820 }],
  ['gh06-ci-d2f3107', `${REPO}/actions/runs/39099141093`, 1440, 760, { x: 0, y: 170, width: 1440, height: 520 }],
  ['gh16-ci-t58', `${REPO}/actions/runs/39716850704`, 1440, 760, { x: 0, y: 170, width: 1440, height: 520 }],
  ['gh17-merge-t58', `${REPO}/commit/3bcd5d0`, 1440, 760, { x: 0, y: 170, width: 1440, height: 450 }],
  ['gh18-reporteexcelservice', `${REPO}/blob/${C}/backend/src/main/java/pe/barberturno/reporting/ReporteExcelService.java?plain=1#L60-L105`, 1500, 1100, { x: 330, y: 0, width: 1150, height: 1100 }],
  ['gh04-merge-t57', `${REPO}/commit/bb3d644`, 1440, 760, { x: 0, y: 170, width: 1440, height: 450 }],
];
const browser = await chromium.launch();
const ctx = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima', deviceScaleFactor: 1.5 });
for (const [nombre, url, w, h, clip] of paginas) {
  const page = await ctx.newPage();
  await page.setViewportSize({ width: w, height: h });
  try { await page.goto(url, { waitUntil: 'networkidle', timeout: 90000 }); } catch (e) { console.log('aviso', nombre, e.message.split('\n')[0]); }
  await page.waitForTimeout(8000);
  await page.screenshot({ path: `${OUT}/${nombre}.png`, clip: clip || undefined });
  fs.appendFileSync(`${OUT}/indice-github.tsv`, `${nombre}\t${url}\t${new Date().toISOString()}\trecorte\n`);
  console.log('ok', nombre);
  await page.close();
}
await browser.close();
