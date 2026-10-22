// Capturas auténticas de páginas públicas de GitHub (solo lectura, sin sesión).
import { createRequire } from 'module';
import fs from 'fs';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');

const OUT = process.argv[2];
fs.mkdirSync(OUT, { recursive: true });
const REPO = 'https://github.com/sohnny-lima/BarberTurno';
const C = '3bcd5d0';
const paginas = [
  ['gh01-repositorio', `${REPO}`, 1440, 1100, false],
  ['gh02-commits-main', `${REPO}/commits/main/`, 1440, 1500, false],
  ['gh03-commits-octubre-04', `${REPO}/commits/main/?since=2026-10-04&until=2026-10-04`, 1440, 1500, false],
  ['gh04-merge-t57', `${REPO}/commit/bb3d644`, 1440, 1000, false],
  ['gh05-actions', `${REPO}/actions`, 1440, 1100, false],
  ['gh06-ci-d2f3107', `${REPO}/actions/runs/39099141093`, 1440, 1000, false],
  ['gh07-ci-primer-run', `${REPO}/actions/runs/37314699514`, 1440, 1000, false],
  ['gh08-arbol-backend', `${REPO}/tree/${C}/backend/src/main/java/pe/barberturno`, 1440, 1100, false],
  ['gh09-securityconfig', `${REPO}/blob/${C}/backend/src/main/java/pe/barberturno/common/security/SecurityConfig.java#L150-L190`, 1440, 1200, false],
  ['gh10-reservarepository', `${REPO}/blob/${C}/backend/src/main/java/pe/barberturno/reservations/ReservaRepository.java`, 1440, 1400, false],
  ['gh11-migracion-exclude', `${REPO}/blob/${C}/backend/src/main/resources/db/migration/V1__esquema_inicial.sql`, 1440, 1400, false],
  ['gh12-ci-yml', `${REPO}/blob/${C}/.github/workflows/ci.yml`, 1440, 1200, false],
  ['gh13-tareas-md', `${REPO}/blob/main/docs/tareas.md`, 1440, 1300, false],
  ['gh14-commit-t49-rojo', `${REPO}/commit/21f86ad`, 1440, 1100, false],
  ['gh15-commit-t49-verde', `${REPO}/commit/b60cbdc`, 1440, 1100, false],
];

const browser = await chromium.launch();
const ctx = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima', colorScheme: 'light' });
for (const [nombre, url, w, h] of paginas) {
  const page = await ctx.newPage();
  await page.setViewportSize({ width: w, height: h });
  try {
    await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
  } catch (e) { console.log('aviso', nombre, e.message.split('\n')[0]); }
  await page.waitForTimeout(9000);
  // Oculta banners de cookies/inicio de sesión si aparecen (no altera el contenido del repositorio).
  await page.addStyleTag({ content: '.js-cookie-consent-banner, cookie-consent-banner, .signup-prompt-bg{display:none!important}' }).catch(() => {});
  const fecha = new Date().toISOString();
  await page.screenshot({ path: `${OUT}/${nombre}.png`, fullPage: false });
  fs.appendFileSync(`${OUT}/indice-github.tsv`, `${nombre}\t${url}\t${fecha}\t${w}x${h}\n`);
  console.log('ok', nombre);
  await page.close();
}
await browser.close();
