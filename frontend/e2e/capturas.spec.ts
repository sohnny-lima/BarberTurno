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
async function capturar(
  page: Page,
  info: TestInfo,
  nombre: string,
  cargando = false,
  fase: 't-55' | 't-57' = 't-55',
  controlFoco?: Locator,
) {
  await expect(page.locator('main')).toBeVisible();
  const main = page.locator('main#contenido');
  await expect(main).toHaveAttribute('tabindex', '-1');
  await expect(main).toHaveCSS('outline-style', 'none');
  // Los diálogos ocultan el fondo con aria-hidden; comprobar los pasos expuestos al lector.
  const pasos = page.getByRole('tab').filter({ has: page.locator('.mat-step-icon') });
  for (let index = 0; index < (await pasos.count()); index++) {
    const paso = pasos.nth(index);
    const icono = paso.locator('.mat-step-icon');
    const estado = await icono.locator('.oculto').textContent();
    const completado = estado?.includes('completado');
    await expect(paso).toHaveAccessibleName(
      new RegExp('Paso ' + (index + 1) + ',.*(completado|actual|pendiente)'),
    );
    await expect(icono.locator('[aria-hidden="true"]')).toHaveText(
      completado ? '✓' : String(index + 1),
    );
    if (completado) {
      await expect(icono).toHaveCSS('background-color', 'rgb(227, 241, 234)');
      await expect(icono).toHaveCSS('color', 'rgb(20, 92, 67)');
    }
  }
  for (const servicio of await page.locator('app-agenda-cita .servicio').all()) {
    expect(await servicio.textContent()).not.toMatch(/\s+,/);
  }
  if (!cargando) await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  await expect(page.locator('mat-snack-bar-container [aria-hidden="true"]')).toHaveCount(0);
  const viewport = page.viewportSize()!;
  const superpuesta =
    (await page.getByRole('dialog').count()) > 0 ||
    (await page.getByRole('menu').count()) > 0 ||
    (movil(page) && (await page.locator('mat-sidenav').isVisible()));
  // El shell móvil desplaza su contenido internamente; fullPage solo mide el documento.
  // Ampliar la altura muestra toda la página; T-57 también lo hace en escritorio.
  // Las superposiciones conservan el viewport original para mostrar su comportamiento real.
  if ((movil(page) || fase === 't-57') && !superpuesta) {
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
  if (fase === 't-57') {
    // Incluye las ayudas y errores que aparecen al mover el foco dentro del diálogo.
    await expect
      .poll(() =>
        page
          .locator('body')
          .evaluate((elemento) =>
            elemento
              .getAnimations({ subtree: true })
              .every((animacion) => animacion.playState !== 'running'),
          ),
      )
      .toBe(true);
  }
  const axe = await new AxeBuilder({ page }).analyze();
  expect(
    axe.violations.map(({ id, nodes }) => ({ id, elementos: nodes.map((n) => n.target) })),
    nombre,
  ).toEqual([]);
  if (controlFoco) {
    await focoVisible(controlFoco);
    await expect(controlFoco).toHaveCSS('outline-offset', '2px');
  }
  const carpeta = resolve('../docs/pruebas', fase, 'capturas', info.project.use.browserName!);
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
  if (movil(page))
    await expect(page.locator('.resumen-corto')).toHaveText('Elija una hora para continuar.');
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
  if (movil(page)) {
    const fecha = await page.locator('[data-dia][aria-pressed="true"]').getAttribute('data-dia');
    const dia = new Intl.DateTimeFormat('es-PE', {
      timeZone: 'America/Lima',
      weekday: 'long',
      day: 'numeric',
    }).format(new Date(fecha + 'T12:00:00-05:00'));
    const intervalo = (await hora.getAttribute('aria-label'))!.replace('–', ' a ');
    await expect(page.locator('.resumen-corto')).toHaveText(
      dia + ', ' + intervalo + ' · Corte clásico',
    );
  }
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

// Fase 2: comparte la auditoría de T-55 y conserva sus casos y carpeta de salida.
async function capturarFase2(
  page: Page,
  info: TestInfo,
  nombre: string,
  control: Locator,
  estado: 'real' | 'carga' | 'vacio' | 'error' | 'password-obligatorio' = 'real',
) {
  await page.bringToFront();
  await expect(control).toBeEnabled();
  // Espera transiciones y autofoco, incluidos los ancestros del diálogo.
  await expect
    .poll(() =>
      control.evaluate((elemento) => {
        let actual: Element | null = elemento;
        while (actual) {
          if (
            parseFloat(getComputedStyle(actual).opacity) < 1 ||
            actual.getAnimations().some((animacion) => animacion.playState === 'running')
          )
            return false;
          actual = actual.parentElement;
        }
        return true;
      }),
    )
    .toBe(true);
  // Tab activa el teclado; enfocar de nuevo evita depender del regreso desde la barra del navegador.
  await control.focus();
  await page.keyboard.press('Tab');
  await page.bringToFront();
  await control.focus();
  await focoVisible(control);
  await expect(control).toHaveCSS('outline-offset', '2px');
  const ancho = page.viewportSize()!.width;
  const clave = `${nombre}${ancho}`;
  await capturar(page, info, clave, estado === 'carga', 't-57', control);
  const ruta = resolve(
    '../docs/pruebas/t-57/capturas',
    info.project.use.browserName!,
    'auditoria.json',
  );
  const auditoria = JSON.parse(readFileSync(ruta, 'utf8'));
  auditoria[clave] = {
    ...auditoria[clave],
    estadoSimulado: estado === 'real' ? undefined : estado,
    camposOutline: true,
    versionNavegador: page.context().browser()!.version(),
    focoVisible: { borde: '3px solid #145C43', desfase: '2px' },
  };
  writeFileSync(ruta, JSON.stringify(auditoria, null, 2) + '\n');
}

async function listaFase2(page: Page, nombre: string, cantidadMinima = 1) {
  const lista = page.getByRole(page.viewportSize()!.width >= 1200 ? 'table' : 'list', {
    name: nombre,
    exact: true,
  });
  await expect(lista).toBeVisible();
  await expect
    .poll(() => lista.locator('tbody tr, [role="listitem"]').count())
    .toBeGreaterThanOrEqual(cantidadMinima);
  return lista;
}

test('fase 2: ingresar, registro y privacidad', async ({ page }, info) => {
  await page.goto('/ingresar');
  await capturarFase2(
    page,
    info,
    'Ingresar',
    page.getByRole('button', { name: 'Ingresar', exact: true }),
  );
  await page.goto('/registro');
  await capturarFase2(
    page,
    info,
    'Registro',
    page.getByRole('button', { name: 'Crear cuenta', exact: true }),
  );
  await page.goto('/privacidad');
  await capturarFase2(
    page,
    info,
    'Privacidad',
    page.getByRole('link', { name: 'Volver al registro', exact: true }),
  );
});

test('fase 2: Mi cuenta y contraseña voluntaria y obligatoria', async ({ page }, info) => {
  await entrar(page, 'cliente@ejemplo.test');
  await page.goto('/perfil');
  await capturarFase2(
    page,
    info,
    'MiCuenta',
    page.getByRole('button', { name: 'Guardar perfil', exact: true }),
  );
  await page.goto('/cambiar-password');
  await expect(
    page.getByText('Su contraseña es temporal. Cámbiela para continuar.', { exact: true }),
  ).toHaveCount(0);
  await capturarFase2(
    page,
    info,
    'PasswordVoluntario',
    page.getByRole('button', { name: 'Guardar contraseña', exact: true }),
  );
  const usuario = await (await page.request.get('/api/auth/sesion')).json();
  await page.route('**/api/auth/sesion', (ruta) =>
    ruta.fulfill({ json: { ...usuario, debeCambiarPassword: true } }),
  );
  await page.goto('/perfil');
  await expect(page).toHaveURL(/\/cambiar-password$/);
  await expect(
    page.getByText('Su contraseña es temporal. Cámbiela para continuar.', { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Salir', exact: true })).toBeVisible();
  await capturarFase2(
    page,
    info,
    'PasswordObligatorio',
    page.getByRole('button', { name: 'Guardar contraseña', exact: true }),
    'password-obligatorio',
  );
});

test('fase 2: servicios, tarjetas intermedias y diálogo', async ({ page }, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/admin/servicios');
  await listaFase2(page, 'Servicios');
  await capturarFase2(
    page,
    info,
    'Servicios',
    page.getByRole('button', { name: 'Nuevo servicio', exact: true }),
  );
  if (!movil(page)) {
    await page.setViewportSize({ width: 1024, height: 900 });
    await listaFase2(page, 'Servicios');
    await capturarFase2(
      page,
      info,
      'Servicios',
      page.getByRole('button', { name: 'Nuevo servicio', exact: true }),
    );
    await page.setViewportSize({ width: 1440, height: 900 });
  }
  await page.getByRole('button', { name: 'Nuevo servicio', exact: true }).click();
  const dialogo = page.getByRole('dialog', { name: 'Nuevo servicio', exact: true });
  // Ejemplo válido sin guardar: el autofoco no deja un Nombre obligatorio vacío al tabular.
  await dialogo.getByLabel('Nombre', { exact: true }).fill('Servicio de muestra');
  await capturarFase2(
    page,
    info,
    'DialogoServicio',
    dialogo.getByRole('button', { name: 'Cancelar', exact: true }),
  );
});

test('fase 2: barberos, alta real ficticia y contraseña temporal', async ({ page }, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/admin/barberos');
  await listaFase2(page, 'Barberos');
  await capturarFase2(
    page,
    info,
    'Barberos',
    page.getByRole('button', { name: 'Nuevo barbero', exact: true }),
  );
  await page.getByRole('button', { name: 'Nuevo barbero', exact: true }).click();
  const alta = page.getByRole('dialog', { name: 'Nuevo barbero', exact: true });
  await capturarFase2(
    page,
    info,
    'DialogoBarbero',
    alta.getByRole('button', { name: 'Cancelar', exact: true }),
  );
  if (movil(page)) {
    const nota = alta.locator('#aviso-password-temporal');
    await nota.scrollIntoViewIfNeeded();
    expect(
      await nota.evaluate((elemento) => {
        const nota = elemento.getBoundingClientRect();
        const contenido = elemento.closest('mat-dialog-content')!.getBoundingClientRect();
        return nota.top >= contenido.top && nota.bottom <= contenido.bottom;
      }),
    ).toBe(true);
    await capturarFase2(
      page,
      info,
      'DialogoBarberoNota',
      alta.getByRole('button', { name: 'Cancelar', exact: true }),
    );
  }
  await alta.getByLabel('Nombre', { exact: true }).fill('Profesional de captura');
  await alta.getByLabel('Correo', { exact: true }).fill('captura-temporal@ejemplo.test');
  await alta.getByLabel('Especialidad', { exact: true }).fill('Datos ficticios de prueba');
  // Credencial de demostración ficticia, solo válida en la base efímera de esta suite.
  await alta.getByLabel('Contraseña temporal', { exact: true }).fill('Temporal1Demo');
  await alta.getByRole('button', { name: 'Guardar barbero', exact: true }).click();
  const temporal = page.getByRole('dialog', { name: 'Contraseña temporal', exact: true });
  await expect(temporal.getByText('Temporal1Demo', { exact: true })).toBeVisible();
  await capturarFase2(
    page,
    info,
    'DialogoPasswordTemporal',
    temporal.getByRole('button', { name: 'Cerrar', exact: true }),
  );
  await page.keyboard.press('Escape');
  await expect(temporal).toBeVisible();
  await temporal.getByRole('button', { name: 'Cerrar', exact: true }).click();
});

test('fase 2: horarios sin selección y con Carlos; fecha completa de 1200 a 1440', async ({
  page,
}, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/admin/horarios');
  await expect(
    page.getByText('Elija un barbero para ver su semana y sus bloqueos.', { exact: true }),
  ).toBeVisible();
  const selector = page.getByRole('combobox', { name: 'Barbero', exact: true });
  await capturarFase2(page, info, 'HorariosSinBarbero', selector);
  await selector.click();
  await page.getByRole('option', { name: 'Carlos', exact: true }).click();
  const guardar = page.getByRole('button', { name: 'Guardar semana', exact: true });
  await expect(guardar).toBeEnabled();
  await capturarFase2(page, info, 'HorariosCarlos', guardar);
  if (!movil(page)) {
    for (const width of [1200, 1280, 1360, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      const fecha = page.getByLabel('Fecha en Lima', { exact: true });
      await expect(fecha).toHaveCSS('font-size', '16px');
      const campo = fecha.locator('..').locator('..');
      expect(await fecha.evaluate((e) => e.clientWidth)).toBeGreaterThanOrEqual(180);
      expect(await campo.evaluate((e) => e.scrollWidth <= e.clientWidth)).toBe(true);
      const etiqueta = page.locator('mat-form-field').filter({ has: fecha }).locator('mat-label');
      expect(
        await etiqueta.evaluate((e) => {
          const label = e.getBoundingClientRect();
          const campo = e.closest('mat-form-field')!.getBoundingClientRect();
          return label.width > 0 && label.left >= campo.left && label.right <= campo.right;
        }),
      ).toBe(true);
      await expect(fecha).toHaveValue('2026-09-28');
      if (width === 1200 || width === 1440) {
        await fecha.scrollIntoViewIfNeeded();
        await page.screenshot({
          path: resolve(`tmp/t-57-5/${info.project.use.browserName}-fecha-${width}.png`),
        });
      }
    }
    await page.setViewportSize({ width: 1024, height: 900 });
    await capturarFase2(page, info, 'HorariosCarlos', guardar);
    // Recargar devuelve el estado sin selección sin modificar la jornada.
    await page.goto('/admin/horarios');
    await expect(
      page.getByText('Elija un barbero para ver su semana y sus bloqueos.', { exact: true }),
    ).toBeVisible();
    await capturarFase2(page, info, 'HorariosSinBarbero', selector);
  }
});

async function comprobarCifras(page: Page) {
  const posiciones = await page
    .locator('app-reporte-cifras .estadistica')
    .evaluateAll((elementos) =>
      elementos.map((e) => {
        const { x, y, width, height } = e.getBoundingClientRect();
        return { x, y, width, height };
      }),
    );
  expect(posiciones).toHaveLength(7);
  expect(
    await page
      .locator('app-reporte-cifras .estadistica')
      .evaluateAll((elementos) => elementos.every((e) => e.scrollWidth <= e.clientWidth)),
  ).toBe(true);
  const [total, ...estados] = posiciones;
  if (page.viewportSize()!.width < 768) {
    expect(total.width).toBeGreaterThan(estados[0].width * 2);
    expect(new Set(estados.map((e) => e.y)).size).toBe(3);
    expect(total.y + total.height).toBeLessThan(estados[0].y);
  } else {
    expect(total.x).toBeLessThan(estados[0].x);
    expect(total.y).toBe(estados[0].y);
    expect(total.height).toBeGreaterThan(estados[0].height * 2);
    expect(new Set(estados.map((e) => e.y)).size).toBe(2);
    expect(new Set(estados.map((e) => e.x)).size).toBe(3);
  }
}

test('fase 2: reportes con cifras equilibradas e historial real', async ({ page }, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/admin/reportes');
  await expect(page.locator('app-reporte-cifras')).toBeVisible();
  await comprobarCifras(page);
  await capturarFase2(
    page,
    info,
    'Reportes',
    page.getByRole('button', { name: 'Aplicar', exact: true }),
  );
  if (!movil(page)) {
    await page.setViewportSize({ width: 768, height: 900 });
    await comprobarCifras(page);
  }
});

test('fase 2: usuarios, filtros compactos, acciones y confirmación de riesgo', async ({
  page,
}, info) => {
  await entrar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/admin/usuarios');
  await listaFase2(page, 'Usuarios y acciones de acceso');
  const buscar = page.getByRole('button', { name: 'Buscar', exact: true });
  if (!movil(page)) {
    const rellenos = await page.locator('form.filtros').evaluate((e) => {
      const estilo = getComputedStyle(e);
      const campos = e.querySelector('mat-form-field')!.getBoundingClientRect();
      return {
        superior: campos.top - e.getBoundingClientRect().top,
        inferior: e.getBoundingClientRect().bottom - campos.bottom,
        padding: parseFloat(estilo.paddingTop),
      };
    });
    expect(Math.abs(rellenos.superior - rellenos.inferior)).toBeLessThanOrEqual(2);
  }
  await capturarFase2(page, info, 'Usuarios', buscar);
  const anchos = movil(page) ? [360] : [768, 1024, 1199];
  for (const width of anchos) {
    await page.setViewportSize({ width, height: width === 360 ? 800 : 900 });
    const lista = await listaFase2(page, 'Usuarios y acciones de acceso');
    const acciones = lista.getByRole('listitem').first().locator('.acciones-fila button');
    const primero = (await acciones.nth(0).boundingBox())!;
    const segundo = (await acciones.nth(1).boundingBox())!;
    if (width === 360) {
      expect(primero.width).toBe(segundo.width);
      expect(primero.x).toBe(segundo.x);
      expect(segundo.y).toBeGreaterThanOrEqual(primero.y + primero.height);
    } else if (
      primero.width + segundo.width + 8 <=
      (await lista.getByRole('listitem').first().locator('.acciones-fila').boundingBox())!.width
    ) {
      expect(primero.y).toBe(segundo.y);
    }
    if (width === 1024) await capturarFase2(page, info, 'Usuarios', buscar);
  }
  await page.setViewportSize(info.project.use.viewport!);
  await listaFase2(page, 'Usuarios y acciones de acceso');
  await page.getByRole('button', { name: 'Desactivar Carlos', exact: true }).click();
  const dialogo = page.getByRole('dialog', { name: 'Desactivar Carlos', exact: true });
  const desactivar = dialogo.getByRole('button', { name: 'Desactivar', exact: true });
  await expect(desactivar).toHaveCSS('color', 'rgb(154, 38, 29)');
  await capturarFase2(page, info, 'DialogoRiesgo', desactivar);
  await dialogo.getByRole('button', { name: 'Cancelar', exact: true }).click();
});

for (const estado of ['carga', 'vacio', 'error'] as const) {
  test(`fase 2: barberos en estado ${estado} simulado`, async ({ page }, info) => {
    await entrar(page, 'admin-e2e@ejemplo.test');
    await page.route('**/api/barberos?**', (ruta) => {
      if (estado === 'carga') return;
      return ruta.fulfill(estado === 'vacio' ? { json: [] } : { status: 503, json: {} });
    });
    await page.goto('/admin/barberos');
    const mensaje =
      estado === 'carga'
        ? 'Cargando barberos…'
        : estado === 'vacio'
          ? 'No hay barberos para mostrar.'
          : 'No pudimos completar la solicitud. Intente nuevamente.';
    await expect(page.locator('main').getByText(mensaje, { exact: true })).toBeVisible();
    const control =
      estado === 'carga'
        ? movil(page)
          ? page.getByRole('button', { name: 'Abrir cuenta', exact: true })
          : page.getByRole('link', { name: 'Barberos', exact: true })
        : page.getByRole('button', {
            name: estado === 'error' ? 'Reintentar' : 'Actualizar',
            exact: true,
          });
    await capturarFase2(
      page,
      info,
      `Barberos${estado[0].toUpperCase()}${estado.slice(1)}`,
      control,
      estado,
    );
  });

  test(`fase 2: reportes en estado ${estado} simulado`, async ({ page }, info) => {
    await entrar(page, 'admin-e2e@ejemplo.test');
    const vacio = {
      total: 0,
      porEstado: {
        PENDIENTE: 0,
        CONFIRMADA: 0,
        EN_ATENCION: 0,
        COMPLETADA: 0,
        NO_ASISTIO: 0,
        CANCELADA: 0,
      },
      porServicio: [],
      porBarbero: [],
    };
    await page.route('**/api/reportes/resumen?**', (ruta) => {
      if (estado === 'carga') return;
      return ruta.fulfill(estado === 'vacio' ? { json: vacio } : { status: 503, json: {} });
    });
    if (estado === 'vacio')
      await page.route('**/api/reservas?**', (ruta) =>
        ruta.fulfill({
          json: { contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 },
        }),
      );
    await page.goto('/admin/reportes');
    const mensaje =
      estado === 'carga'
        ? 'Consultando reportes e historial…'
        : estado === 'vacio'
          ? 'No hay reservas en esta página con los filtros aplicados.'
          : 'No pudimos completar la solicitud. Intente nuevamente.';
    await expect(page.locator('main').getByText(mensaje, { exact: true })).toBeVisible();
    await capturarFase2(
      page,
      info,
      `Reportes${estado[0].toUpperCase()}${estado.slice(1)}`,
      page.getByRole('button', {
        name: estado === 'error' ? 'Reintentar' : 'Aplicar',
        exact: true,
      }),
      estado,
    );
  });
}
