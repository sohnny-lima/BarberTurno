import AxeBuilder from '@axe-core/playwright';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { type Page, type TestInfo } from '@playwright/test';
import { test, expect, sesionApi } from './fixture';

async function entrar(page: Page, correo: string) {
  const api = await sesionApi(correo);
  try {
    await page.context().clearCookies();
    await page.context().addCookies((await api.storageState()).cookies);
  } finally {
    await api.dispose();
  }
}

async function capturar(page: Page, info: TestInfo, nombre: string, cargando = false) {
  await expect(page.locator('main')).toBeVisible();
  if (!cargando) await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  // Material mueve el aviso al área live al terminar su anuncio inicial.
  await expect(page.locator('mat-snack-bar-container [aria-hidden="true"]')).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
  expect(
    await page
      .locator('mat-form-field')
      .evaluateAll((campos) =>
        campos.every((campo) => campo.classList.contains('mat-form-field-appearance-outline')),
      ),
  ).toBe(true);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
  const axe = await new AxeBuilder({ page }).analyze();
  expect(
    axe.violations.map(({ id, nodes }) => ({ id, elementos: nodes.map((n) => n.target) })),
  ).toEqual([]);
  const carpeta = resolve('../docs/pruebas/t-48/capturas');
  const guardar = info.project.use.browserName === 'chromium';
  if (guardar) mkdirSync(carpeta, { recursive: true });
  await page.evaluate(() => {
    window.scrollTo(0, 0);
  });
  const captura = await page.screenshot({
    path: guardar ? resolve(carpeta, `${info.project.name}-${nombre}.png`) : undefined,
    fullPage: true,
  });
  if (!guardar) await info.attach(nombre, { body: captura, contentType: 'image/png' });
}

test('capturas reales de Reservar, Mis citas y Agenda', async ({ page }, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/reservar');
  await expect(page.locator('[data-servicio]')).not.toHaveCount(0);
  await capturar(page, info, 'reservar-paso1');
  if (info.project.use.viewport?.width === 360) {
    const servicio = await page.locator('[data-servicio]').first().boundingBox();
    const titulo = await page.getByRole('heading', { level: 1 }).boundingBox();
    expect(servicio!.x).toBe(titulo!.x);
  }
  await page.getByRole('button', { name: /Corte clásico/ }).click();
  await page.getByRole('radio', { name: /Carlos/ }).check();
  await expect(page.locator('aside[aria-label="Su cita"]')).toContainText('Carlos');
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await expect(page.locator('[data-franja]')).not.toHaveCount(0);
  await capturar(page, info, 'reservar-paso2');
  await page.locator('[data-franja]').first().click();
  await expect(page.locator('[data-confirmar]')).toBeVisible();
  await capturar(page, info, 'reservar-paso3');
  await page.goto('/mis-citas');
  await expect(page.locator('app-reserva-tarjeta')).not.toHaveCount(0);
  await capturar(page, info, 'mis-citas');
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/agenda');
  await expect(page.locator('[data-selector-barbero]')).toBeVisible();
  await expect(page.locator('article.fila')).not.toHaveCount(0);
  await expect(page.getByText(/^\s*Periodo:/)).toHaveCount(0);
  if (info.project.use.viewport?.width === 360) {
    const hoy = await page.getByRole('button', { name: 'Hoy', exact: true }).boundingBox();
    const actualizar = await page
      .getByRole('button', { name: 'Actualizar agenda', exact: true })
      .boundingBox();
    expect(hoy!.y).toBe(actualizar!.y);
    await expect(page.locator('.cabecera [data-contador-avisos]')).toHaveText('Avisos sin leer: 0');
    await expect(page.locator('.cabecera .contador-personal')).toHaveCount(0);
  }
  await capturar(page, info, 'agenda-admin');
  await page.getByRole('radio', { name: 'Semana', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Semana', exact: true })).toBeChecked();
  await expect(page.locator('.linea-tiempo section')).toHaveCount(7);
  await expect(page.getByText(/^\s*Periodo:/)).toBeVisible();
  await capturar(page, info, 'agenda-semana');
});

test('legibilidad de Barberos y Usuarios sin rediseñar la fase 2', async ({ page }, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  for (const pantalla of ['barberos', 'usuarios']) {
    await page.goto(`/admin/${pantalla}`);
    await expect(page.locator('main tbody tr')).not.toHaveCount(0);
    await capturar(page, info, `fase2-${pantalla}`);
  }
});

test('estados de carga, vacío y error con API simulada', async ({ page }, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  for (const estado of ['carga', 'vacio', 'error']) {
    await page.route('**/api/reservas/mias?**', async (ruta) => {
      if (estado === 'carga') return;
      await ruta.fulfill({
        status: estado === 'error' ? 503 : 200,
        contentType: 'application/json',
        body: JSON.stringify(
          estado === 'error'
            ? { detail: 'No se pudieron cargar las citas. Intente nuevamente.' }
            : { contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 },
        ),
      });
    });
    await page.goto('/mis-citas');
    if (estado === 'carga') await expect(page.getByText('Cargando citas…')).toBeVisible();
    if (estado === 'vacio')
      await expect(
        page.getByRole('status').filter({ hasText: 'No tiene citas próximas' }),
      ).toBeVisible();
    if (estado === 'error') {
      await expect(page.locator('main [role="alert"]')).toBeVisible();
      await expect(page.locator('mat-paginator[aria-label="Páginas de citas"]')).toHaveCount(0);
    }
    await capturar(page, info, `mis-citas-${estado}`, estado === 'carga');
    await page.unroute('**/api/reservas/mias?**');
  }
});

test('estados de Reservar y Agenda con API simulada', async ({ page }, info) => {
  for (const pantalla of [
    {
      nombre: 'reservar',
      correo: 'cliente@ejemplo.test',
      ruta: '/reservar',
      api: '**/api/servicios?**',
      vacio: [],
      carga: 'Cargando servicios y profesionales…',
      textoVacio: 'No hay servicios activos disponibles.',
    },
    {
      nombre: 'agenda',
      correo: 'admin-e2e@ejemplo.test',
      ruta: '/agenda',
      api: '**/api/reservas?**',
      vacio: { contenido: [], pagina: 0, totalPaginas: 1 },
      carga: 'Cargando agenda…',
      textoVacio: 'Sin citas para este día.',
    },
  ]) {
    await entrar(page, pantalla.correo);
    if (pantalla.nombre === 'agenda') {
      await page.route('**/api/notificaciones/conteo', async (ruta) => {
        await ruta.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ noLeidas: 12 }),
        });
      });
    }
    for (const estado of ['carga', 'vacio', 'error']) {
      await page.route(pantalla.api, async (ruta) => {
        if (estado === 'carga') return;
        await ruta.fulfill({
          status: estado === 'error' ? 503 : 200,
          contentType: 'application/json',
          body: JSON.stringify(
            estado === 'error'
              ? { detail: 'No se pudieron cargar los datos. Intente nuevamente.' }
              : pantalla.vacio,
          ),
        });
      });
      await page.goto(pantalla.ruta);
      if (estado === 'carga') await expect(page.getByText(pantalla.carga)).toBeVisible();
      if (estado === 'vacio')
        await expect(
          page.getByRole('status').filter({ hasText: pantalla.textoVacio }),
        ).toBeVisible();
      if (estado === 'error') await expect(page.locator('main [role="alert"]')).toBeVisible();
      if (pantalla.nombre === 'agenda' && info.project.use.viewport?.width === 360) {
        await expect(page.locator('.cabecera [data-contador-avisos]')).toHaveText(
          'Avisos sin leer: 12',
        );
        const contador = await page.locator('.contador-personal').boundingBox();
        expect(contador!.height).toBeLessThanOrEqual(32);
      }
      await capturar(page, info, `${pantalla.nombre}-${estado}`, estado === 'carga');
      await page.unroute(pantalla.api);
    }
  }
});

test('error de horas con aviso transitorio por encima de la barra móvil', async ({
  page,
}, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/reservar');
  await page.getByRole('button', { name: /Corte clásico/ }).click();
  await expect(page.locator('[data-franja]')).not.toHaveCount(0);
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await page.route('**/api/disponibilidad?**', async (ruta) => {
    await ruta.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ detail: 'No se pudieron cargar las horas. Intente nuevamente.' }),
    });
  });
  await page.locator('[data-dia]').nth(1).click();
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  await expect(page.locator('main [role="alert"]')).toContainText(
    'No se pudieron cargar las horas',
  );
  const aviso = page.locator('.aviso-transitorio');
  await expect(aviso).toHaveCount(1);
  await expect(aviso).toBeVisible();
  if (info.project.use.viewport?.width === 360) {
    await expect
      .poll(async () => {
        const panel = await aviso.boundingBox();
        const barra = await page.locator('.barra-reserva').boundingBox();
        return !!panel && !!barra && panel.y + panel.height <= barra.y;
      })
      .toBe(true);
  }
  await capturar(page, info, 'reservar-2-error');
});

test('foco al restaurar la selección que el visitante conserva para ingresar', async ({ page }) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.route('**/api/auth/sesion', async (ruta) => {
    await ruta.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
  });
  await page.goto('/reservar');
  await page.getByRole('button', { name: /Corte clásico/ }).click();
  await page.getByRole('radio', { name: /Carlos/ }).check();
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await page.locator('[data-franja]').first().click();
  await page.getByRole('button', { name: 'Confirmar reserva', exact: true }).click();
  await expect(page).toHaveURL(/\/ingresar\?/);
  await page.unroute('**/api/auth/sesion');
  // La sesión ficticia real sigue disponible; al volver se revalida la selección conservada.
  await page.goto('/reservar');
  await expect(page.locator('[data-paso="2"]')).toBeFocused();
  await expect(page.locator('[data-confirmar]')).toBeEnabled();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});
