// Sonda de revisión: violaciones y motivos de los resultados «incompletos» de axe.
// Usa la clave ficticia del proceso (BT_DEMO_PASSWORD) sin mostrarla.
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= fileURLToPath(
  new URL('../../tmp/navegadores', import.meta.url),
);

const base = 'http://localhost:8081';
const navegador = await chromium.launch();
const resumen = {};
const violaciones = [];

async function sesion(correo, viewport) {
  const contexto = await navegador.newContext({
    viewport,
    locale: 'es-PE',
    timezoneId: 'America/Lima',
  });
  const pagina = await contexto.newPage();
  await pagina.goto(base + '/ingresar');
  await pagina.getByLabel('Correo', { exact: true }).fill(correo);
  await pagina.getByLabel('Contraseña', { exact: true }).fill(process.env.BT_DEMO_PASSWORD);
  await pagina.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await pagina.waitForURL((url) => !url.pathname.startsWith('/ingresar'));
  return pagina;
}

async function analizar(pagina, nombre) {
  await pagina.waitForLoadState('networkidle');
  await pagina.waitForTimeout(400);
  const r = await new AxeBuilder({ page: pagina }).analyze();
  for (const v of r.violations) {
    console.log(`VIOLACIÓN ${nombre}: ${v.id} (${v.impact}) en ${v.nodes.length} nodo(s)`);
    if (v.impact === 'serious' || v.impact === 'critical') {
      violaciones.push(`${nombre}: ${v.id} (${v.impact}) en ${v.nodes.length} nodo(s)`);
    }
  }
  for (const v of r.incomplete) {
    for (const n of v.nodes) {
      const motivo = [...n.any, ...n.all, ...n.none].map((c) => c.message).join(' | ');
      const clave = `${v.id} (${v.impact}) · ${motivo}`;
      resumen[clave] ??= new Set();
      resumen[clave].add(`${nombre} → ${n.target.join(' ')}`);
    }
  }
}

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 360, height: 800 },
]) {
  const w = viewport.width;
  const dueno = await sesion('multirol@ejemplo.test', viewport);
  await analizar(dueno, `${w}-selector`);
  for (const ruta of [
    'agenda',
    'configuracion',
    'equipo',
    'invitaciones',
    'admin/horarios',
    'admin/usuarios',
    'admin/reportes',
    'reservar',
  ]) {
    await dueno.goto(`${base}/n/barberturno/${ruta}`);
    await analizar(dueno, `${w}-${ruta}`);
  }
  await dueno.locator('header.cabecera button.campana').click();
  await dueno.getByRole('dialog').waitFor();
  await analizar(dueno, `${w}-avisos`);
  await dueno.getByRole('dialog').getByRole('button', { name: 'Cerrar', exact: true }).click();
  await dueno.getByRole('dialog').waitFor({ state: 'hidden' });
  await dueno.waitForTimeout(300);
  if (w === 360) {
    await dueno.getByRole('button', { name: 'Abrir menú' }).click();
    await analizar(dueno, `${w}-cajon`);
  }
  const doble = await sesion('personal-cliente@ejemplo.test', viewport);
  await doble.goto(`${base}/negocios`);
  // Con una sola barbería puede entrar directamente y abrir la elección de papel.
  await doble
    .getByRole('button', { name: 'Entrar en BarberTurno' })
    .click({ timeout: 3000 })
    .catch(() => {});
  await doble
    .getByRole('dialog')
    .waitFor({ timeout: 5000 })
    .catch(() => {});
  await analizar(doble, `${w}-papel`);
  const plataforma = await sesion('plataforma@ejemplo.test', viewport);
  await plataforma.goto(`${base}/plataforma/negocios`);
  await plataforma.getByRole('button', { name: 'Nueva barbería' }).click();
  await plataforma.getByRole('dialog').waitFor();
  await analizar(plataforma, `${w}-alta`);
}
await navegador.close();
for (const [clave, sitios] of Object.entries(resumen).sort()) {
  console.log(`${clave}\n    ${sitios.size} nodos: ${[...sitios].slice(0, 6).join('; ')}`);
}
if (violaciones.length > 0) {
  console.error('Violaciones de axe detectadas:\n  ' + violaciones.join('\n  '));
  process.exitCode = 1;
}
