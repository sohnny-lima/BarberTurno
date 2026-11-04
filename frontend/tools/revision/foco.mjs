// Sonda de revisión: contención del foco en diálogos y en el menú móvil, y activación del salto
// al contenido con el menú abierto. Usa la clave ficticia del proceso sin mostrarla.
import { fileURLToPath } from 'node:url';
import { chromium, firefox } from '@playwright/test';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= fileURLToPath(
  new URL('../../tmp/navegadores', import.meta.url),
);

const base = 'http://localhost:8081';
async function sesion(navegador, correo, viewport) {
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
/** Recorre con Tab y Shift+Tab y anota dónde queda el foco en cada pulsación. */
async function recorrer(pagina, ambito, pulsaciones) {
  const fuera = [];
  // Material sitúa el foco inicial al terminar la animación de apertura.
  const inicio = Date.now();
  const entro = await pagina
    .waitForFunction((s) => !!document.activeElement?.closest(s), ambito, { timeout: 3000 })
    .then(
      () => true,
      () => false,
    );
  fuera.push(
    entro ? `foco inicial dentro en ${Date.now() - inicio} ms` : 'FOCO INICIAL FUERA tras 3 s',
  );
  for (const tecla of ['Tab', 'Shift+Tab']) {
    for (let i = 0; i < pulsaciones; i++) {
      await pagina.keyboard.press(tecla);
      const foco = await pagina.evaluate((selector) => {
        const a = document.activeElement;
        return {
          dentro: !!a?.closest(selector),
          ancla: !!a?.classList.contains('cdk-focus-trap-anchor'),
          saltar: !!a?.classList.contains('saltar'),
          descripcion: a ? a.tagName + '.' + a.className.toString().slice(0, 40) : 'ninguno',
        };
      }, ambito);
      if (!foco.dentro || foco.ancla || foco.saltar)
        fuera.push(`${tecla}#${i + 1}: ${foco.descripcion}`);
    }
  }
  return fuera;
}
for (const [nombre, tipo] of [
  ['chromium', chromium],
  ['firefox', firefox],
]) {
  const navegador = await tipo.launch();
  // Diálogo de elección de papel y diálogo de alta de barbería, a 1440 y 360.
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 360, height: 800 },
  ]) {
    const doble = await sesion(navegador, 'personal-cliente@ejemplo.test', viewport);
    await doble.goto(`${base}/negocios`);
    await doble
      .getByRole('button', { name: 'Entrar en BarberTurno' })
      .click({ timeout: 3000 })
      .catch(() => {});
    await doble.getByRole('dialog').waitFor();
    console.log(
      `${nombre}-${viewport.width} papel: fuera del diálogo ${JSON.stringify(await recorrer(doble, '[role="dialog"]', 12))}`,
    );
    const plataforma = await sesion(navegador, 'plataforma@ejemplo.test', viewport);
    await plataforma.goto(`${base}/plataforma/negocios`);
    await plataforma.getByRole('button', { name: 'Nueva barbería' }).click();
    await plataforma.getByRole('dialog').waitFor();
    console.log(
      `${nombre}-${viewport.width} alta: fuera del diálogo ${JSON.stringify(await recorrer(plataforma, '[role="dialog"]', 14))}`,
    );
  }
  // Menú móvil abierto a 360: contención y salto al contenido.
  const dueno = await sesion(navegador, 'multirol@ejemplo.test', { width: 360, height: 800 });
  await dueno.goto(`${base}/n/barberturno/agenda`);
  await dueno.getByRole('button', { name: 'Abrir menú' }).click();
  await dueno.locator('mat-sidenav').waitFor({ state: 'visible' });
  await dueno.waitForTimeout(500);
  console.log(
    `${nombre}-360 cajón: fuera del menú ${JSON.stringify(await recorrer(dueno, 'mat-sidenav', 18))}`,
  );
  // Un lector de pantalla en modo exploración puede activar el enlace aunque Tab no llegue a él.
  await dueno.locator('.saltar').evaluate((enlace) => enlace.click());
  await dueno.waitForTimeout(600);
  const estado = await dueno.evaluate(() => {
    const main = document.querySelector('main');
    const caja = main.getBoundingClientRect();
    const x = Math.min(innerWidth - 1, caja.left + 40);
    const y = Math.min(innerHeight - 1, Math.max(caja.top, 0) + 40);
    const encima = document.elementFromPoint(x, y);
    return {
      menuAbierto: getComputedStyle(document.querySelector('mat-sidenav')).visibility !== 'hidden',
      focoEnMain: document.activeElement === main,
      mainVisibleEnSuPunto: !!encima?.closest('main'),
      elementoEncima: encima ? encima.tagName + '.' + encima.className.toString().slice(0, 40) : null,
    };
  });
  console.log(`${nombre}-360 salto con el menú abierto: ${JSON.stringify(estado)}`);
  await navegador.close();
}
