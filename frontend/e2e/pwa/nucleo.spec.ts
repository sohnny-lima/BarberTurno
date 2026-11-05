import { type Page } from '@playwright/test';
import { test, expect } from './fixture';

async function ingresar(page: Page, correo: string) {
  await page.goto('/ingresar');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).evaluate((campo, valor) => {
    (campo as HTMLInputElement).value = valor;
    campo.dispatchEvent(new Event('input', { bubbles: true }));
  }, process.env['BT_DEMO_PASSWORD']!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).not.toHaveURL(/\/ingresar/);
  const entrar = page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true });
  await expect
    .poll(async () => (await entrar.isVisible()) || /\/n\/barberturno\//.test(page.url()))
    .toBe(true);
  if (await entrar.isVisible()) await entrar.click();
  await expect(page).toHaveURL(/\/n\/barberturno\//);
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
}

async function salir(page: Page) {
  const cuenta = page.getByRole('button', { name: 'Abrir cuenta', exact: true });
  const menu = page.getByRole('button', { name: 'Abrir menú', exact: true });
  if (await cuenta.isVisible()) await cuenta.click();
  else if (await menu.isVisible()) await menu.click();
  await page
    .getByRole('button', { name: 'Salir', exact: true })
    .or(page.getByRole('menuitem', { name: 'Salir', exact: true }))
    .filter({ visible: true })
    .click();
  await expect(page).toHaveURL(/\/ingresar/);
  await expect(page.getByRole('heading', { name: 'Iniciar sesión', exact: true })).toBeVisible();
  await expect(page.locator('app-agenda, app-mis-citas, app-perfil')).toHaveCount(0);
}

async function instalar(page: Page) {
  await page.evaluate(async () => {
    await navigator.serviceWorker.register('/ngsw-worker.js');
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
}

async function cachePublica(page: Page, privados: string[]) {
  const entradas = await page.evaluate(async () => {
    const datos: { url: string; texto: string }[] = [];
    for (const nombre of await caches.keys()) {
      const cache = await caches.open(nombre);
      for (const peticion of await cache.keys()) {
        const respuesta = await cache.match(peticion);
        datos.push({ url: peticion.url, texto: await respuesta!.text() });
      }
    }
    return datos;
  });
  expect(entradas.length).toBeGreaterThan(0);
  for (const entrada of entradas) {
    expect(new URL(entrada.url).pathname).not.toMatch(/^\/api(?:\/|$)/);
    for (const dato of privados) {
      expect(entrada.url).not.toContain(dato);
      expect(entrada.texto).not.toContain(dato);
    }
  }
}

test('CP-1: caché pública con cliente, profesional y encargado', async ({ page, request }) => {
  const manifiesto = await (await request.get('/ngsw.json')).json();
  expect(manifiesto.dataGroups ?? []).toEqual([]);
  for (const grupo of manifiesto.assetGroups) {
    expect(grupo.patterns).toEqual([]);
    expect(grupo.urls.some((url: string) => /^\/api(?:\/|$)/.test(url))).toBe(false);
  }
  const privados: string[] = [];
  for (const correo of ['cliente@ejemplo.test', 'carlos@ejemplo.test', 'encargado@ejemplo.test']) {
    await ingresar(page, correo);
    await instalar(page);
    const sesion = await (await page.request.get('/api/auth/sesion')).json();
    expect(sesion.correo).toBe(correo);
    privados.push(correo, sesion.nombre);
    const ruta = correo.startsWith('cliente') ? 'mis-citas' : 'agenda';
    await page.goto('/n/barberturno/' + ruta);
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
    await cachePublica(page, privados);
    await salir(page);
  }
});

test('CP-2: arranque sin conexión y escritura sin reenvío al recuperar red', async ({
  page,
  context,
}) => {
  await ingresar(page, 'cliente@ejemplo.test');
  await instalar(page);
  await page.goto('/perfil');
  await expect(page.getByLabel('Nombre', { exact: true })).toBeVisible();
  await context.setOffline(true);
  await expect(page.getByText(/^Sin conexión\./)).toBeVisible();
  await page.getByLabel('Nombre', { exact: true }).fill('Cambio ficticio sin conexión');
  const escrituras: string[] = [];
  page.on('request', (peticion) => {
    if (!['GET', 'HEAD', 'OPTIONS'].includes(peticion.method())) escrituras.push(peticion.url());
  });
  await page.getByRole('button', { name: /Guardar/ }).click();
  await expect(
    page.getByText(/No se pudo confirmar el cambio por falta de conexión/).first(),
  ).toBeVisible();
  expect(escrituras).toHaveLength(1);
  await context.setOffline(false);
  await expect(page.getByText(/Conexión restablecida/)).toBeVisible();
  await page.waitForTimeout(1500);
  expect(escrituras).toHaveLength(1);
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByText(/^Sin conexión\./)).toBeVisible();
  await context.setOffline(false);
});

test('CP-4: dos cuentas y dos barberías sin datos anteriores incluso con Atrás', async ({
  page,
}) => {
  await ingresar(page, 'multirol@ejemplo.test');
  await instalar(page);
  const anterior = await (await page.request.get('/api/auth/sesion')).json();
  await page.goto('/negocios');
  await page.getByRole('button', { name: 'Entrar en Barbería Sur', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/sur\//);
  await page.evaluate(() => {
    sessionStorage.setItem('barberturno.prueba-cuenta', 'anterior');
    localStorage.setItem('barberturno.prueba-cuenta', 'anterior');
  });
  await salir(page);
  await page.goBack();
  await expect(page.locator('body')).not.toContainText(anterior.nombre);
  await ingresar(page, 'cliente@ejemplo.test');
  await expect(page).toHaveURL(/\/n\/barberturno\//);
  await expect(page.locator('body')).not.toContainText(anterior.nombre);
  expect(
    await page.evaluate(() =>
      [sessionStorage, localStorage].every(
        (almacen) => almacen.getItem('barberturno.prueba-cuenta') === null,
      ),
    ),
  ).toBe(true);
  await page.goBack();
  await expect(page.locator('body')).not.toContainText(anterior.nombre);
  await cachePublica(page, [anterior.nombre, anterior.correo]);
});

test('CP-5: Atrás cierra diálogo, cajón y menú sin cambiar de ruta', async ({ page }) => {
  await ingresar(page, 'encargado@ejemplo.test');
  const ruta = page.url();
  const campana = page.getByRole('button', { name: /^Avisos:/ });
  await campana.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.goBack();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(page.url()).toBe(ruta);
  if ((page.viewportSize()?.width ?? 0) < 768) {
    await page.getByRole('button', { name: 'Abrir menú', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Cerrar menú', exact: true })).toBeVisible();
    await page.goBack();
    await expect(page.getByRole('button', { name: 'Cerrar menú', exact: true })).not.toBeVisible();
    expect(page.url()).toBe(ruta);
    await page.getByRole('button', { name: 'Abrir cuenta', exact: true }).click();
    await expect(page.getByRole('menu')).toBeVisible();
    await page.goBack();
    await expect(page.getByRole('menu')).toHaveCount(0);
    expect(page.url()).toBe(ruta);
  }
  // Tras cerrar por botón no debe quedar una parada artificial antes de la ruta anterior.
  await page.goto('/perfil');
  await page.goto('/n/barberturno/agenda');
  await campana.click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.goBack();
  await expect(page).toHaveURL(/\/perfil$/);
});
