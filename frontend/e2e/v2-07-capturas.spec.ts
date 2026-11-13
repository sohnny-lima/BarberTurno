import { type Page } from '@playwright/test';
import { test, expect, sesionApi } from './fixture';
import { capturarV2 } from './evidencia-v2';

/** Identidades ficticias reales del seed; nunca se guarda storageState en disco. */
async function entrar(page: Page, correo: string) {
  const api = await sesionApi(correo);
  try {
    await page.context().clearCookies();
    await page.context().addCookies((await api.storageState()).cookies);
  } finally {
    await api.dispose();
  }
}

test('visual · selector, entrada única y estados de acceso por URL', async ({ page }, info) => {
  await entrar(page, 'multirol@ejemplo.test');
  await page.goto('/negocios');
  await expect(
    page.getByRole('list', { name: 'Barberías', exact: true }).getByRole('listitem'),
  ).toHaveCount(2);
  await expect(page.locator('.usuario div span, .identidad-menu span')).toHaveCount(0);
  await capturarV2(page, info, 'selector-varias');

  await entrar(page, 'carlos@ejemplo.test');
  await page.goto('/negocios');
  await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);
  await expect(page.locator('app-agenda')).toBeVisible();
  // El diseño aprobado entra directamente cuando solo hay una relación: no inventar otra pantalla.
  await capturarV2(page, info, 'selector-una-entrada-directa');

  await entrar(page, 'plataforma@ejemplo.test');
  await page.goto('/negocios');
  await expect(page.locator('main')).toContainText('Todavía no pertenece a ninguna barbería.');
  await capturarV2(page, info, 'selector-ninguna');

  await entrar(page, 'multirol@ejemplo.test');
  await page.goto('/n/sur/configuracion');
  await expect(page.locator('main')).toContainText('No tiene acceso a esta sección.');
  await expect(page.locator('app-configuracion')).toHaveCount(0);
  await capturarV2(page, info, 'sin-acceso');

  await page.goto('/n/no-existe/reservar');
  await expect(page.locator('main')).toContainText('No encontramos esa barbería.');
  await expect(page.locator('app-reservar')).toHaveCount(0);
  await capturarV2(page, info, 'barberia-inexistente');

  const cookie = (await page.context().cookies()).find((c) => c.name === 'BT_SESION')!;
  await page.context().addCookies([{ ...cookie, value: 'sesion-invalida-de-la-prueba' }]);
  await page.goto('/n/barberturno/agenda');
  await expect(page).toHaveURL(/\/ingresar\?returnUrl=/);
  await expect(page.getByLabel('Correo', { exact: true })).toBeVisible();
  await expect(page.locator('app-agenda')).toHaveCount(0);
  await capturarV2(page, info, 'sesion-caducada-ingresar');
});

test('visual · pantallas existentes canónicas y tarjetas de equipo y plataforma a 1024', async ({
  page,
}, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/n/barberturno/reservar');
  await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
  await capturarV2(page, info, 'reservar');
  await page.goto('/n/barberturno/mis-citas');
  await expect(page.locator('app-mis-citas')).toBeVisible();
  await capturarV2(page, info, 'mis-citas');

  await entrar(page, 'multirol@ejemplo.test');
  for (const [seccion, componente, nombre] of [
    ['agenda', 'app-agenda', 'agenda'],
    ['reservar', 'app-reservar', 'reserva-asistida'],
    ['admin/servicios', 'app-servicios', 'servicios'],
    ['admin/barberos', 'app-barberos', 'barberos'],
    ['admin/usuarios', 'app-usuarios', 'usuarios'],
  ]) {
    await page.goto('/n/barberturno/' + seccion);
    await expect(page.locator(componente)).toBeVisible();
    await capturarV2(page, info, nombre);
  }
  await page.goto('/n/barberturno/admin/horarios');
  await page.getByRole('combobox', { name: 'Barbero', exact: true }).click();
  await page.getByRole('option', { name: 'Carlos', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Jornada semanal', exact: true })).toBeVisible();
  await capturarV2(page, info, 'horarios');

  await page.goto('/n/barberturno/admin/reportes');
  await page.getByLabel('Desde', { exact: true }).fill('2026-09-28');
  await page.getByLabel('Hasta', { exact: true }).fill('2026-10-05');
  await page.getByRole('button', { name: 'Aplicar', exact: true }).click();
  await capturarV2(page, info, 'reportes');

  await page.goto('/n/barberturno/configuracion');
  await page.getByLabel('Límite de barberos activos', { exact: true }).fill('1');
  const cupo = page.waitForResponse(
    (r) => r.url().endsWith('/configuracion') && r.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Guardar configuración', exact: true }).click();
  const rechazo = await cupo;
  expect(rechazo.status()).toBe(409);
  expect((await rechazo.json()).codigo).toBe('CONFLICTO');
  await expect(page.locator('main [role="alert"]')).toContainText(
    'El límite no puede ser inferior al número de barberos activos.',
  );
  await capturarV2(page, info, 'configuracion-cupo');

  if (page.viewportSize()!.width === 1440) {
    const original = page.viewportSize()!;
    await page.setViewportSize({ width: 1024, height: 900 });
    try {
      await page.goto('/n/barberturno/equipo');
      const personas = page
        .getByRole('list', { name: 'Equipo', exact: true })
        .getByRole('listitem');
      await expect(personas.nth(1)).toBeVisible();
      await expect(personas.first()).toContainText('Rol:');
      await expect(personas.first()).not.toContainText('Motivo:');
      expect((await personas.nth(1).boundingBox())!.x).toBeGreaterThan(
        (await personas.first().boundingBox())!.x,
      );
      await capturarV2(page, info, 'equipo');

      await entrar(page, 'plataforma@ejemplo.test');
      await page.goto('/plataforma/negocios');
      const barberias = page
        .getByRole('list', { name: 'Barberías de la plataforma', exact: true })
        .getByRole('listitem');
      await expect(barberias.nth(1)).toBeVisible();
      expect((await barberias.nth(1).boundingBox())!.x).toBeGreaterThan(
        (await barberias.first().boundingBox())!.x,
      );
      await capturarV2(page, info, 'plataforma');
    } finally {
      await page.setViewportSize(original);
    }
  }
});
