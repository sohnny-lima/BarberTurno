// Capturas de solo lectura del despliegue público (GET sin sesión). No envía formularios.
import { createRequire } from 'module';
import fs from 'fs';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');
const OUT = process.argv[2];
fs.mkdirSync(OUT, { recursive: true });
const BASE = 'https://barberturno-web-202610-bvbacecef3hpcga6.westus-01.azurewebsites.net';
const browser = await chromium.launch();
const ctx = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima' });
const page = await ctx.newPage();
const registro = [];
for (const [nombre, ruta, w, h] of [
  ['az01-ingresar-1440', '/ingresar', 1440, 900],
  ['az02-ingresar-360', '/ingresar', 360, 800],
  ['az03-health', '/actuator/health', 900, 300],
]) {
  await page.setViewportSize({ width: w, height: h });
  const r = await page.goto(BASE + ruta, { waitUntil: 'networkidle', timeout: 90000 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: `${OUT}/${nombre}.png` });
  const cab = r.headers();
  registro.push({ nombre, url: BASE + ruta, estado: r.status(), fecha: new Date().toISOString(),
    hsts: cab['strict-transport-security'], csp: (cab['content-security-policy'] || '').slice(0, 120),
    nosniff: cab['x-content-type-options'] });
  console.log('ok', nombre, r.status());
}
fs.writeFileSync(`${OUT}/azure-capturas.json`, JSON.stringify(registro, null, 2));
await browser.close();
