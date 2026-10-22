import { createRequire } from 'module';
const require = createRequire('D:/Proyectos/BarberTurno/frontend/package.json');
process.env.PLAYWRIGHT_BROWSERS_PATH = 'D:/Proyectos/BarberTurno/frontend/tmp/navegadores';
const { chromium } = require('@playwright/test');
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 760, height: 90 }, deviceScaleFactor: 2 });
const r = await p.goto('https://barberturno-web-202610-bvbacecef3hpcga6.westus-01.azurewebsites.net/actuator/health');
await p.addStyleTag({ content: 'body{font-size:28px;margin:16px;font-family:Consolas,monospace}' });
await p.screenshot({ path: process.argv[2] }); console.log(r.status(), await p.textContent('body')); await b.close();
