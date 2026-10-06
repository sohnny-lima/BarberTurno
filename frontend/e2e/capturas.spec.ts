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
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
  const axe = await new AxeBuilder({ page }).analyze();
  expect(
    axe.violations.map(({ id, nodes }) => ({ id, elementos: nodes.map((n) => n.target) })),
  ).toEqual([]);
  const carpeta = resolve('../docs/pruebas/t-48/capturas');
  mkdirSync(carpeta, { recursive: true });
  await page.evaluate(() => {
    (document.activeElement as HTMLElement)?.blur();
    window.scrollTo(0, 0);
  });
  await page.screenshot({
    path: resolve(carpeta, `${info.project.name}-${nombre}.png`),
    fullPage: true,
  });
}

test('capturas reales de Reservar, Mis citas y Agenda', async ({ page }, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/reservar');
  await expect(page.locator('[data-servicio]')).not.toHaveCount(0);
  await capturar(page, info, 'reservar-paso1');
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
  await capturar(page, info, 'agenda-admin');
  await page.getByRole('radio', { name: 'Semana', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Semana', exact: true })).toBeChecked();
  await expect(page.locator('.linea-tiempo section')).toHaveCount(7);
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
    if (estado === 'error') await expect(page.locator('main [role="alert"]')).toBeVisible();
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
      await capturar(page, info, `${pantalla.nombre}-${estado}`, estado === 'carga');
      await page.unroute(pantalla.api);
    }
  }
});
