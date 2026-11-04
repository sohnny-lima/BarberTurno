// Sonda de revisión: posición del contenido tras activar el salto al contenido (menú abierto y cerrado).
import { fileURLToPath } from 'node:url';
import { chromium, firefox } from '@playwright/test';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= fileURLToPath(
  new URL('../../tmp/navegadores', import.meta.url),
);

const base = 'http://localhost:8081';
for (const [nombre, tipo] of [
  ['chromium', chromium],
  ['firefox', firefox],
]) {
  const navegador = await tipo.launch();
  for (const viewport of [
    { width: 360, height: 800 },
    { width: 1440, height: 900 },
  ]) {
    for (const conMenu of viewport.width === 360 ? [true, false] : [false]) {
      const contexto = await navegador.newContext({
        viewport,
        locale: 'es-PE',
        timezoneId: 'America/Lima',
      });
      const p = await contexto.newPage();
      await p.goto(base + '/ingresar');
      await p.getByLabel('Correo', { exact: true }).fill('multirol@ejemplo.test');
      await p.getByLabel('Contraseña', { exact: true }).fill(process.env.BT_DEMO_PASSWORD);
      await p.getByRole('button', { name: 'Ingresar', exact: true }).click();
      await p.waitForURL((url) => !url.pathname.startsWith('/ingresar'));
      await p.goto(base + '/n/barberturno/agenda');
      await p.locator('main h1').waitFor();
      if (conMenu) {
        await p.getByRole('button', { name: 'Abrir menú' }).click();
        await p.locator('mat-sidenav').waitFor({ state: 'visible' });
        const linea = await p.evaluate(async () => {
          document.querySelector('.saltar').click();
          const t0 = performance.now();
          const marcas = [];
          while (performance.now() - t0 < 1200) {
            await new Promise((r) => setTimeout(r, 50));
            const main = document.querySelector('main');
            const caja = main.getBoundingClientRect();
            const e = document.elementFromPoint(caja.left + 40, Math.max(caja.top, 0) + 40);
            const d = e
              ? main.contains(e)
                ? 'main'
                : e.tagName + '.' + String(e.className).slice(0, 25)
              : 'nada';
            const foco = document.activeElement === main ? 'F' : '-';
            if (!marcas.length || marcas.at(-1).d !== d || marcas.at(-1).foco !== foco)
              marcas.push({ ms: Math.round(performance.now() - t0), d, foco });
          }
          return marcas;
        });
        console.log(`  línea de tiempo: ${JSON.stringify(linea)}`);
      } else {
        await p.locator('.saltar').focus();
        await p.keyboard.press('Enter');
      }
      await p.waitForTimeout(800);
      const r = await p.evaluate(() => {
        const main = document.querySelector('main');
        const h1 = main.querySelector('h1');
        const cab = document.querySelector('.cabecera');
        const caja = (e) =>
          e
            ? Math.round(e.getBoundingClientRect().top) +
              '..' +
              Math.round(e.getBoundingClientRect().bottom)
            : null;
        const hr = h1.getBoundingClientRect();
        const encima = document.elementFromPoint(hr.left + 5, hr.top + hr.height / 2);
        return {
          url: location.pathname,
          foco: document.activeElement === main,
          main: caja(main),
          cabecera: caja(cab),
          h1: caja(h1),
          h1Visible: !!encima && h1.contains(encima),
          encimaDelH1: encima ? encima.tagName + '.' + String(encima.className).slice(0, 30) : null,
          desplazamiento: document.querySelector('mat-sidenav-content')?.scrollTop ?? null,
          ventana: scrollY,
        };
      });
      console.log(`${nombre}-${viewport.width}${conMenu ? ' menú abierto' : ' teclado'}: ${JSON.stringify(r)}`);
      await contexto.close();
    }
  }
  await navegador.close();
}
