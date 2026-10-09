import AxeBuilder from '@axe-core/playwright';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { type Locator, type Page, type TestInfo } from '@playwright/test';
import { test, expect, sesionApi } from './fixture';
import type { Pagina } from '../src/app/core/modelos/pagina';
import type { ReservaDto } from '../src/app/core/modelos/reservas';

// Backend real salvo las respuestas concretas de vacío, carga, error y contraseña temporal.
// Las capturas no modifican las reservas de demostración.
async function entrar(page: Page, correo: string) {
  const api = await sesionApi(correo);
  try {
    await page.context().clearCookies();
    await page.context().addCookies((await api.storageState()).cookies);
  } finally {
    await api.dispose();
  }
}
function movil(page: Page) {
  return page.viewportSize()!.width === 360;
}
async function capturar(page: Page, info: TestInfo, nombre: string, cargando = false) {
  await expect(page.locator('main')).toBeVisible();
  if (!cargando) await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  await expect(page.locator('mat-snack-bar-container [aria-hidden="true"]')).toHaveCount(0);
  const viewport = page.viewportSize()!;
  const superpuesta =
    (await page.getByRole('dialog').count()) > 0 ||
    (await page.getByRole('menu').count()) > 0 ||
    (movil(page) && (await page.locator('mat-sidenav').isVisible()));
  // El shell móvil desplaza su contenido internamente; fullPage solo mide el documento.
  // Ampliar la altura permite capturar toda la pantalla sin alterar su ancho de 360 px.
  // Las superposiciones conservan el viewport original para mostrar su comportamiento real.
  if (movil(page) && !superpuesta) {
    const alto = await page
      .locator('mat-sidenav-content')
      .evaluate(
        (contenido) => contenido.scrollHeight + window.innerHeight - contenido.clientHeight,
      );
    await page.setViewportSize({ width: viewport.width, height: Math.max(viewport.height, alto) });
  }
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(
      Array.from(document.images).map((imagen) => imagen.decode().catch(() => undefined)),
    );
    window.scrollTo(0, 0);
    document.querySelector('mat-sidenav-content')?.scrollTo(0, 0);
  });
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
    nombre,
  ).toBe(true);
  const axe = await new AxeBuilder({ page }).analyze();
  expect(
    axe.violations.map(({ id, nodes }) => ({ id, elementos: nodes.map((n) => n.target) })),
    nombre,
  ).toEqual([]);
  const carpeta = resolve('../docs/pruebas/t-55/capturas', info.project.use.browserName!);
  mkdirSync(carpeta, { recursive: true });
  const png = await page.screenshot({
    path: resolve(carpeta, `${nombre}.png`),
    fullPage: !superpuesta,
  });
  const rutaAuditoria = resolve(carpeta, 'auditoria.json');
  const auditoria = existsSync(rutaAuditoria)
    ? JSON.parse(readFileSync(rutaAuditoria, 'utf8'))
    : {};
  auditoria[nombre] = {
    proyecto: info.project.name,
    viewport: page.viewportSize(),
    captura: { ancho: png.readUInt32BE(16), alto: png.readUInt32BE(20) },
    violacionesAxe: axe.violations.length,
    desplazamientoHorizontal: false,
    estadoSimulado: cargando ? 'carga' : undefined,
  };
  writeFileSync(rutaAuditoria, JSON.stringify(auditoria, null, 2) + '\n');
  await page.setViewportSize(viewport);
  await page.bringToFront();
}
async function focoVisible(control: Locator) {
  const actual = await control.evaluate(() => ({
    ventanaActiva: document.hasFocus(),
    etiqueta: document.activeElement?.getAttribute('aria-label'),
    elemento: document.activeElement?.tagName,
    id: document.activeElement?.id,
  }));
  await expect(control, JSON.stringify(actual)).toBeFocused();
  await expect(control).toHaveCSS('outline-style', 'solid');
  await expect(control).toHaveCSS('outline-width', '3px');
  await expect(control).toHaveCSS('outline-color', 'rgb(20, 92, 67)');
}
async function tabular(page: Page, desde: Locator, hasta: Locator) {
  await page.bringToFront();
  await desde.focus();
  await page.keyboard.press('Tab');
  await focoVisible(hasta);
}
async function elegirServicio(page: Page) {
  await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
}
async function avanzar(page: Page) {
  const accion = page.getByRole('button', { name: 'Elegir fecha y hora', exact: true });
  await accion.focus();
  await accion.press('Enter');
  await expect(page.locator('[data-paso="1"]')).toBeFocused();
  await expect(page.locator('[data-franja]')).not.toHaveCount(0);
  await expect
    .poll(() =>
      page
        .locator('mat-stepper')
        .evaluate((elemento) =>
          elemento
            .getAnimations({ subtree: true })
            .every((animacion) => animacion.playState !== 'running'),
        ),
    )
    .toBe(true);
}

test('propuesta: Reservar pasos 1, 2 y 3, anchos intermedios y tabulación', async ({
  page,
}, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/reservar');
  await elegirServicio(page);
  if (!movil(page)) {
    for (const width of [1200, 1280, 1360, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      const textos = page.locator('.profesional').first().locator('strong, small');
      expect(
        await textos.evaluateAll((elementos) =>
          elementos.every((e) => {
            const estilo = getComputedStyle(e);
            return (
              e.getBoundingClientRect().height <= 2 * parseFloat(estilo.lineHeight) + 1 &&
              e.scrollWidth <= e.clientWidth
            );
          }),
        ),
        `Sin preferencia a ${width} px`,
      ).toBe(true);
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
        ),
      ).toBe(true);
    }
  }
  await expect(page.locator('mat-step-header').first()).toBeVisible();
  await tabular(
    page,
    page.getByRole('radio', { name: 'Corte clásico', exact: true }),
    page.locator('[data-profesional="sin-preferencia"]'),
  );
  await capturar(page, info, movil(page) ? 'Reservar360Paso1' : 'Reservar1440Paso1');
  await avanzar(page);
  // Firefox también tabula el grupo desplazable; se verifica ese foco y el del primer día.
  if (movil(page) && info.project.use.browserName === 'firefox') {
    const dias = page.getByRole('group', { name: 'Días disponibles', exact: true });
    await tabular(page, page.locator('[data-paso="1"]'), dias);
    await tabular(page, dias, page.locator('[data-dia]').first());
  } else {
    await tabular(page, page.locator('[data-paso="1"]'), page.locator('[data-dia]').first());
  }
  const hora = page.locator('[data-franja]').first();
  await hora.focus();
  await hora.press('Enter');
  await expect(page.locator('[data-paso="2"]')).toBeFocused();
  await page.getByRole('button', { name: 'Cambiar fecha u hora', exact: true }).click();
  await expect(page.locator('[data-paso="1"]')).toBeFocused();
  await expect(hora).toHaveAttribute('aria-pressed', 'true');
  await capturar(page, info, movil(page) ? 'Main' : 'Reservar1440');
  const siguiente = page.getByRole('button', { name: 'Siguiente', exact: true });
  await siguiente.focus();
  await siguiente.press('Enter');
  await expect(page.locator('[data-paso="2"]')).toBeFocused();
  await capturar(page, info, movil(page) ? 'Reservar360Paso3' : 'Reservar1440Paso3');
});

test('propuesta: Mis citas, vacío y reprogramación de una cita real', async ({ page }, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/mis-citas');
  await expect(page.locator('app-reserva-tarjeta')).not.toHaveCount(0);
  const precios = await page.locator('app-reserva-tarjeta').allTextContents();
  expect(precios.every((texto) => /S[/]\s\d+\.\d{2}/.test(texto))).toBe(true);
  expect(precios.some((texto) => /S[/]\s{2,}/.test(texto))).toBe(false);
  await tabular(
    page,
    page.getByRole('radio', { name: 'Próximas', exact: true }),
    movil(page)
      ? page.getByRole('button', { name: 'Filtros por fecha y estado' })
      : page.getByLabel('Desde', { exact: true }),
  );
  await capturar(page, info, movil(page) ? 'MisCitas360' : 'MisCitas1440');
  await page.getByRole('link', { name: 'Reprogramar', exact: true }).first().click();
  await avanzar(page);
  await page.locator('[data-franja]').first().click();
  await expect(page.locator('[data-paso="2"]')).toBeFocused();
  await expect(page.locator('del')).toContainText('Horario anterior, se reemplaza:');
  await capturar(page, info, movil(page) ? 'Reprogramar360' : 'ReprogramarCliente1440');
  await page.route('**/api/reservas/mias?**', (ruta) =>
    ruta.fulfill({
      json: { contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 },
    }),
  );
  await page.goto('/mis-citas');
  await expect(
    page.getByRole('heading', { name: 'No tiene citas próximas', exact: true }),
  ).toBeVisible();
  await capturar(page, info, movil(page) ? 'MisCitas360Vacio' : 'MisCitas1440Vacio');
});

test('propuesta: error de horas conserva la selección y el aviso sobre la barra', async ({
  page,
}, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/reservar');
  await elegirServicio(page);
  await avanzar(page);
  await page.route('**/api/disponibilidad?**', (ruta) =>
    ruta.fulfill({
      status: 503,
      json: { detail: 'No se pudieron cargar las horas. Intente nuevamente.' },
    }),
  );
  await page.locator('[data-dia]').nth(1).click();
  await expect(page.getByRole('alert')).toContainText('Su servicio y profesional se conservan');
  const aviso = page.locator('.aviso-transitorio');
  await expect(aviso).toBeVisible();
  if (movil(page)) {
    await expect
      .poll(async () => {
        const panel = await aviso.boundingBox();
        const barra = await page.locator('.barra-reserva:visible').boundingBox();
        return !!panel && !!barra && panel.y + panel.height <= barra.y;
      })
      .toBe(true);
  }
  await capturar(page, info, movil(page) ? 'Reservar360Error' : 'Reservar1440Error');
});

test('propuesta: avisos del cliente y menú de cuenta solo con teclado', async ({ page }, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto(movil(page) ? '/mis-citas' : '/reservar');
  const abrir = page.getByRole('button', { name: /^Avisos: \d+ sin leer$/ });
  const { noLeidas } = await (await page.request.get('/api/notificaciones/conteo')).json();
  await expect(abrir).toHaveAccessibleName(`Avisos: ${noLeidas} sin leer`);
  await abrir.focus();
  await abrir.press('Enter');
  const dialogo = page.getByRole('dialog', { name: 'Avisos', exact: true });
  await expect(dialogo.getByRole('button', { name: 'Cerrar', exact: true })).toBeFocused();
  await expect(dialogo.locator('[aria-busy="true"]')).toHaveCount(0);
  await expect(dialogo.getByRole('heading', { name: 'Avisos', exact: true })).toHaveCount(1);
  expect(await dialogo.evaluate((e) => e.scrollWidth <= e.clientWidth)).toBe(true);
  await capturar(page, info, movil(page) ? 'Avisos360' : 'Avisos1440');
  await page.keyboard.press('Escape');
  await expect(dialogo).toHaveCount(0);
  await expect(abrir).toBeFocused();
  if (movil(page)) {
    const cuenta = page.getByRole('button', { name: 'Abrir cuenta' });
    await tabular(page, abrir, cuenta);
    for (const tecla of ['Enter', 'Space']) {
      await cuenta.press(tecla);
      const perfil = page.getByRole('menuitem', { name: 'Mi cuenta', exact: true });
      const salir = page.getByRole('menuitem', { name: 'Salir', exact: true });
      await expect(perfil).toBeFocused();
      await page.keyboard.press('ArrowDown');
      await focoVisible(salir);
      await page.keyboard.press('ArrowUp');
      await expect(perfil).toBeFocused();
      if (tecla === 'Enter') await capturar(page, info, 'Cuenta360');
      await page.keyboard.press('Escape');
      await expect(page.getByRole('menu')).toHaveCount(0);
      await focoVisible(cuenta);
    }
  }
});

test('propuesta: Agenda del barbero en Día, Semana y carga', async ({ page }, info) => {
  await entrar(page, 'carlos@ejemplo.test');
  await page.goto('/agenda');
  await expect(page.locator('app-agenda-cita')).not.toHaveCount(0);
  await expect(page.locator('[data-selector-barbero]')).toHaveCount(0);
  await tabular(
    page,
    page.getByRole('radio', { name: 'Día', exact: true }),
    page.getByRole('button', { name: 'Día anterior', exact: true }),
  );
  await capturar(page, info, movil(page) ? 'Agenda360' : 'AgendaBarbero1440');
  const abrirAvisos = page.getByRole('button', { name: /^Ver avisos: \d+ sin leer$/ });
  const { noLeidas } = await (await page.request.get('/api/notificaciones/conteo')).json();
  await expect(abrirAvisos).toHaveAccessibleName(`Ver avisos: ${noLeidas} sin leer`);
  await abrirAvisos.focus();
  await abrirAvisos.press('Enter');
  const avisos = page.getByRole('dialog', { name: 'Avisos', exact: true });
  await expect(avisos.getByRole('button', { name: 'Cerrar', exact: true })).toBeFocused();
  await expect(avisos.locator('[aria-busy="true"]')).toHaveCount(0);
  await expect(avisos.getByRole('heading', { name: 'Avisos', exact: true })).toHaveCount(1);
  expect(await avisos.evaluate((e) => e.scrollWidth <= e.clientWidth)).toBe(true);
  await capturar(page, info, movil(page) ? 'AvisosBarbero360' : 'AvisosBarbero1440');
  await page.keyboard.press('Escape');
  await expect(avisos).toHaveCount(0);
  await expect(abrirAvisos).toBeFocused();
  await page.getByRole('radio', { name: 'Semana', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Semana', exact: true })).toBeChecked();
  await expect(page.getByText(/^\s*Periodo:/)).toBeVisible();
  await capturar(page, info, movil(page) ? 'AgendaSemana360' : 'AgendaSemanaBarbero1440');
  await page.route('**/api/reservas?**', () => undefined);
  await page.goto('/agenda');
  await expect(page.getByText('Cargando agenda…', { exact: true })).toBeVisible();
  await capturar(page, info, movil(page) ? 'Agenda360Carga' : 'Agenda1440Carga', true);
});

test('propuesta: Agenda del administrador, Más acciones, reprogramar y cajón', async ({
  page,
}, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/agenda');
  await expect(page.locator('[data-selector-barbero]')).toBeVisible();
  await expect(page.locator('app-agenda-cita')).not.toHaveCount(0);
  await expect(page.getByRole('button', { name: /^(Ver avisos|Avisos):/ })).toHaveCount(0);
  await tabular(
    page,
    page.getByRole('radio', { name: 'Día', exact: true }),
    page.getByRole('combobox', { name: 'Barbero', exact: true }),
  );
  await capturar(page, info, movil(page) ? 'AgendaAdmin360' : 'Agenda1440');
  const reservas: Pagina<ReservaDto> = await (
    await page.request.get('/api/reservas?desde=2026-09-28&hasta=2026-09-28')
  ).json();
  const reserva = reservas.contenido.find((r) => r.permisos.reprogramar)!;
  expect(reserva).toBeDefined();
  const cita = page.locator('app-agenda-cita').filter({ hasText: reserva.codigo });
  const mas = cita.getByRole('button', { name: /^Más acciones para / });
  await mas.focus();
  await mas.press('Enter');
  await expect(page.getByRole('menuitem').first()).toBeFocused();
  await capturar(page, info, movil(page) ? 'MasAcciones360' : 'MasAcciones1440');
  await page.keyboard.press('ArrowDown');
  await focoVisible(page.getByRole('menuitem').nth(1));
  await page.keyboard.press('Escape');
  await expect(page.getByRole('menu')).toHaveCount(0);
  await focoVisible(mas);
  if (movil(page)) await mas.press('Enter');
  const reprogramar = movil(page)
    ? page.getByRole('menuitem', { name: 'Reprogramar', exact: true })
    : cita.getByRole('button', { name: 'Reprogramar', exact: true });
  await reprogramar.focus();
  await reprogramar.press('Enter');
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByLabel('Fecha de Lima', { exact: true })).toBeFocused();
  const consulta = page.waitForResponse((respuesta) => {
    const url = new URL(respuesta.url());
    return url.pathname === '/api/disponibilidad' && url.searchParams.get('fecha') === '2026-09-30';
  });
  const fecha = dialogo.getByLabel('Fecha de Lima', { exact: true });
  await fecha.fill('2026-09-30');
  await fecha.press('Tab');
  expect((await consulta).ok()).toBe(true);
  await expect(dialogo.getByText('Consultando franjas…')).toHaveCount(0);
  const franja = dialogo.getByRole('combobox', { name: 'Franja disponible (Lima)', exact: true });
  await franja.focus();
  await focoVisible(franja);
  await franja.press('Enter');
  await expect(page.getByRole('option').first()).toBeVisible();
  await page.getByRole('option').first().click();
  await dialogo
    .getByLabel('Motivo obligatorio', { exact: true })
    .fill('El cliente ficticio pidió pasar la cita al miércoles por la mañana.');
  await expect(dialogo.getByText('Consultando franjas…')).toHaveCount(0);
  await expect(dialogo.getByRole('button', { name: 'Reprogramar', exact: true })).toBeEnabled();
  await capturar(page, info, movil(page) ? 'ReprogramarAgenda360' : 'Reprogramar1440');
  await page.keyboard.press('Escape');
  await expect(dialogo).toHaveCount(0);
  await focoVisible(movil(page) ? mas : reprogramar);
  await page.getByRole('radio', { name: 'Semana', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Semana', exact: true })).toBeChecked();
  await capturar(page, info, movil(page) ? 'AgendaSemanaAdmin360' : 'AgendaSemana1440');
  if (movil(page)) {
    const abrir = page.getByRole('button', { name: 'Abrir menú', exact: true });
    await abrir.focus();
    await abrir.press('Enter');
    const cerrar = page.getByRole('button', { name: 'Cerrar menú', exact: true });
    await expect(cerrar).toBeFocused();
    await expect(page.locator('mat-sidenav')).not.toHaveClass(/mat-drawer-animating/);
    await capturar(page, info, 'MenuAdmin360');
    await page.keyboard.press('Escape');
    await expect(page.locator('mat-sidenav')).toBeHidden();
    await expect(abrir).toBeFocused();
  }
});

test('propuesta: reserva asistida elige cliente desde el backend real', async ({ page }, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/reservar');
  await page
    .getByLabel('Buscar cliente por nombre o correo', { exact: true })
    .fill('cliente@ejemplo.test');
  await page.getByRole('radio', { name: /cliente@ejemplo.test/ }).check();
  await capturar(page, info, movil(page) ? 'Asistida360' : 'Asistida1440');
});

test('propuesta: contraseña temporal con respuesta de sesión simulada', async ({ page }, info) => {
  await entrar(page, 'carlos@ejemplo.test');
  const usuario = await (await page.request.get('/api/auth/sesion')).json();
  await page.route('**/api/auth/sesion', (ruta) =>
    ruta.fulfill({ json: { ...usuario, debeCambiarPassword: true } }),
  );
  await page.goto('/agenda');
  await expect(page).toHaveURL(/\/cambiar-password$/);
  await expect(
    page.getByText('Su contraseña es temporal. Cámbiela para continuar.', { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Salir', exact: true })).toBeVisible();
  await expect(page.locator('.barra-inferior')).toHaveCount(0);
  await capturar(page, info, movil(page) ? 'Restringido360' : 'Restringido1440');
});

test('foco al restaurar la selección que el visitante conserva para ingresar', async ({ page }) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.route('**/api/auth/sesion', (ruta) => ruta.fulfill({ status: 401, json: {} }));
  await page.goto('/reservar');
  await elegirServicio(page);
  await page.getByRole('radio', { name: /Carlos/ }).check();
  await avanzar(page);
  await page.locator('[data-franja]').first().click();
  await page.getByRole('button', { name: 'Confirmar reserva', exact: true }).click();
  await expect(page).toHaveURL(/\/ingresar\?/);
  await page.unroute('**/api/auth/sesion');
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
