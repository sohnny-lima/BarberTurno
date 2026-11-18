import { expect, type Page } from '@playwright/test';

/**
 * Obtiene la contraseña demo desde la variable de entorno BT_DEMO_PASSWORD.
 * Si no está configurada, lanza un error claro. Nunca debe escribirse en un archivo,
 * en una captura, en un mensaje de error ni en un test.info().annotations.
 */
export function obtenerClaveDemo(): string {
  const clave = process.env['BT_DEMO_PASSWORD'];
  if (!clave || clave.trim() === '') {
    throw new Error('La variable de entorno BT_DEMO_PASSWORD es obligatoria.');
  }
  return clave;
}

/**
 * Localiza el control para cerrar sesión en el menú lateral, menú de cuenta o cabecera.
 */
export function controlSalir(page: Page) {
  return page
    .getByRole('button', { name: 'Salir', exact: true })
    .or(page.getByRole('menuitem', { name: 'Salir', exact: true }));
}

/**
 * Inicia sesión en la demo con el correo indicado y la clave demo configurada.
 * Si se especifica slugNegocio ('barberturno' o 'sur'), navega y selecciona dicha barbería.
 */
export async function iniciarSesion(
  page: Page,
  correo: string,
  slugNegocio: string | null = 'barberturno',
) {
  const clave = obtenerClaveDemo();
  await page.goto('/ingresar');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).evaluate((elemento, valor) => {
    (elemento as HTMLInputElement).value = valor;
    elemento.dispatchEvent(new Event('input', { bubbles: true }));
  }, clave);

  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).not.toHaveURL(/\/ingresar(?:\?|$)/);
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

  if (!slugNegocio) {
    await expect(page).toHaveURL(/\/negocios|\/n\//);
    return;
  }

  // Esperar con expect a que la URL sea /n/<slug>/ o /negocios
  const patronEsperado = new RegExp(`/n/${slugNegocio}/|/negocios(?:\\?|$)`);
  await expect(page).toHaveURL(patronEsperado);

  if (/\/negocios(?:\/|$)/.test(new URL(page.url()).pathname)) {
    const nombreNegocio =
      slugNegocio === 'sur'
        ? 'Barbería Sur'
        : slugNegocio === 'barberturno'
          ? 'BarberTurno'
          : slugNegocio;
    const botonEntrar = page.getByRole('button', {
      name: `Entrar en ${nombreNegocio}`,
      exact: true,
    });
    const dialogoPapel = page.getByRole('dialog').filter({ hasText: '¿Cómo quiere entrar' });

    // Esperar explícitamente a que el botón de entrar o el diálogo sea visible
    const elementoListo = botonEntrar.or(dialogoPapel).first();
    await expect(elementoListo).toBeVisible();

    if (!(await dialogoPapel.isVisible())) {
      await botonEntrar.click();
    }

    const patronDestino = new RegExp(`/n/${slugNegocio}/`);

    // Gestionar el diálogo «¿Cómo quiere entrar…» si aparece con espera explícita
    await expect
      .poll(
        async () => {
          if (patronDestino.test(page.url())) {
            return 'navegado';
          }
          if (await dialogoPapel.isVisible()) {
            return 'dialogo';
          }
          return 'esperando';
        },
        {
          message: `Debe navegar a /n/${slugNegocio}/ o mostrar el diálogo «¿Cómo quiere entrar…»`,
        },
      )
      .not.toBe('esperando');

    if (await dialogoPapel.isVisible()) {
      const botonEntrarDialogo = dialogoPapel.getByRole('button', { name: 'Entrar', exact: true });
      await expect(botonEntrarDialogo).toBeVisible();
      await botonEntrarDialogo.click();
    }
  }

  // Termina siempre exigiendo toHaveURL(/\/n\/<slug>\//)
  await expect(page).toHaveURL(new RegExp(`/n/${slugNegocio}/`));
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
}

/**
 * Cierra la sesión activa comprobando que se realice la petición POST a /api/auth/logout
 * con código 204 y retorno a /ingresar.
 */
export async function cerrarSesion(page: Page) {
  const dialogo = page.getByRole('dialog');
  if (await dialogo.isVisible()) {
    await page.keyboard.press('Escape');
    await expect(dialogo).toBeHidden();
  }

  const salir = controlSalir(page);
  const cuenta = page.getByRole('button', { name: 'Abrir cuenta', exact: true });
  const menu = page.getByRole('button', { name: 'Abrir menú', exact: true });

  // Espera determinista previa antes de decidir si abrir menú o cuenta
  const controlDisponible = salir.or(cuenta).or(menu).first();
  await expect(controlDisponible).toBeVisible();

  if (!(await salir.first().isVisible())) {
    const disparador = cuenta.or(menu).first();
    await expect(disparador).toBeVisible();
    await disparador.click();
  }

  await expect(salir.first()).toBeVisible();

  const logoutResponsePromise = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/auth/logout' && r.request().method() === 'POST',
  );

  await salir.first().click();
  const respuesta = await logoutResponsePromise;
  expect(respuesta.status(), 'Logout debe responder 204 con CSRF correcto').toBe(204);
  await expect(page).toHaveURL(/\/ingresar(?:\?|$)/);
}

/**
 * Abre el menú lateral o el menú de cuenta para acceder a la región de barbería actual.
 */
export async function abrirContexto(page: Page) {
  const regionDirecta = page.getByRole('region', { name: 'Barbería actual', exact: true });
  const menu = page.getByRole('button', { name: 'Abrir menú', exact: true });
  const cuenta = page.getByRole('button', { name: 'Abrir cuenta', exact: true });

  // Espera determinista previa
  const controlOpcion = regionDirecta.or(menu).or(cuenta).first();
  await expect(controlOpcion).toBeVisible();

  if (!(await regionDirecta.first().isVisible())) {
    const disparador = menu.or(cuenta).first();
    await expect(disparador).toBeVisible();
    await disparador.click();
  }

  const regionVisible = page
    .getByRole('region', { name: 'Barbería actual', exact: true })
    .filter({ visible: true });
  await expect(regionVisible).toBeVisible();
  return regionVisible;
}

/**
 * Cierra deterministamente el menú lateral o desplegable abierto para consultar el contexto.
 * En móvil con cajón (ADMIN), pulsa el botón «Cerrar menú» y espera a que el sidenav esté oculto.
 * En móvil con menú de cuenta (CLIENTE/BARBERO), cierra el panel mediante su backdrop o Escape.
 * En escritorio (1440), donde el sidenav es lateral permanente (mode="side"), no interfiere.
 */
export async function cerrarContexto(page: Page) {
  const botonCerrarMenu = page.getByRole('button', { name: 'Cerrar menú', exact: true });
  const menuCuenta = page.getByRole('menu');

  if (await botonCerrarMenu.isVisible()) {
    await botonCerrarMenu.click();
    await expect(botonCerrarMenu).toBeHidden();
    await expect(page.locator('#menu-principal')).toBeHidden();
    await expect(page.locator('.mat-drawer-backdrop.mat-drawer-shown')).toHaveCount(0);
  } else if (await menuCuenta.isVisible()) {
    const backdrop = page.locator('.cdk-overlay-backdrop').first();
    if (await backdrop.isVisible()) {
      await backdrop.click({ position: { x: 5, y: 5 }, force: true });
    } else {
      await page.keyboard.press('Escape');
    }
    await expect(menuCuenta).toBeHidden();
  }
}
