import { test, expect, type Page } from '@playwright/test';

export async function instalar(page: Page) {
  await page.evaluate(async () => {
    await navigator.serviceWorker.register('/ngsw-worker.js');
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
}

test('CP-3: dos versiones reales del shell, aceptación y formulario modificado', async ({
  page,
  request,
}) => {
  await request.post('/__pwa/version?valor=1');
  await page.goto('/ingresar');
  await instalar(page);
  await expect(page.locator('meta[name="version-prueba-pwa"]')).toHaveAttribute('content', '1');
  await page.getByLabel('Correo', { exact: true }).fill('modificado@ejemplo.test');
  await expect(page.locator('form.ng-dirty')).toHaveCount(1);
  await request.post('/__pwa/version?valor=2');
  // Protocolo oficial del worker: comprueba otro manifiesto, sin invocar servicios Angular.
  await page.evaluate(() =>
    navigator.serviceWorker.controller!.postMessage({ action: 'CHECK_FOR_UPDATES', nonce: 123456 }),
  );
  await expect(page.getByText('Hay una versión nueva.', { exact: false })).toBeVisible();
  await expect(page.locator('meta[name="version-prueba-pwa"]')).toHaveAttribute('content', '1');
  page.once('dialog', (dialogo) => dialogo.dismiss());
  await page.getByRole('button', { name: 'Actualizar', exact: true }).click();
  await expect(page.getByLabel('Correo', { exact: true })).toHaveValue('modificado@ejemplo.test');
  await expect(page.locator('meta[name="version-prueba-pwa"]')).toHaveAttribute('content', '1');
  page.once('dialog', (dialogo) => dialogo.accept());
  await page.getByRole('button', { name: 'Actualizar', exact: true }).click();
  await expect(page.locator('meta[name="version-prueba-pwa"]')).toHaveAttribute('content', '2');
});
