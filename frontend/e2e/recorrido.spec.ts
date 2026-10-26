import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { type Locator, type Page, type Response, type TestInfo } from '@playwright/test';
import { test, expect, escribir, sesionApi } from './fixture';
import { sql } from './base-datos';
import type { BarberoDto, ServicioDto } from '../src/app/core/modelos/catalogo';
import type { NotificacionDto } from '../src/app/core/modelos/notificaciones';
import type { Pagina } from '../src/app/core/modelos/pagina';
import type { ReservaDto } from '../src/app/core/modelos/reservas';
import type { UsuarioAdminDto } from '../src/app/core/modelos/usuarios';

async function capturarNucleo(page: Page, testInfo: TestInfo, nombre: string) {
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  // Esperar los estados finales de Material evita capturar un cajón o diálogo a medio abrir.
  await page.evaluate(async () => {
    await Promise.all(
      document
        .getAnimations()
        .filter((animacion) => Number.isFinite(animacion.effect?.getComputedTiming().endTime))
        .map((animacion) => animacion.finished.catch(() => undefined)),
    );
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({
    path: `../docs/pruebas/v2-07/capturas/v2-07-1/${testInfo.project.name}-${nombre}.png`,
    fullPage: true,
    animations: 'disabled',
  });
}

async function abrirContexto(page: Page) {
  const menu = page.getByRole('button', { name: 'Abrir menú', exact: true });
  const cuenta = page.getByRole('button', { name: 'Abrir cuenta', exact: true });
  if (await menu.isVisible()) await menu.click();
  else if (await cuenta.isVisible()) await cuenta.click();
  return page
    .getByRole('region', { name: 'Barbería actual', exact: true })
    .filter({ visible: true });
}

let atencion: string;
let cercana: string;
let pendiente: string;
let cuentaRegistrada: number;

// El guion modifica la demo de forma secuencial y reportes verifica su resultado final.
test.describe.configure({ mode: 'serial' });

async function revisar(page: Page, testInfo: TestInfo, nombre: string) {
  await expect(page.locator('main')).toBeVisible();
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  // Material mueve el contenido al área live tras anunciarlo; analizar el DOM estable.
  await expect(page.locator('mat-snack-bar-container [aria-hidden="true"]')).toHaveCount(0);
  expect(
    await page
      .locator('mat-form-field')
      .evaluateAll((campos) =>
        campos.every((campo) => campo.classList.contains('mat-form-field-appearance-outline')),
      ),
  ).toBe(true);
  const resultados = await new AxeBuilder({ page }).analyze();
  const relevantes = resultados.violations.map(({ id, impact, nodes }) => ({
    id,
    impact,
    elementos: nodes.map((n) => n.target),
  }));
  await testInfo.attach(`axe-${nombre}`, {
    body: JSON.stringify(relevantes, null, 2),
    contentType: 'application/json',
  });
  expect(relevantes, nombre).toEqual([]);
  if (
    nombre.startsWith('reservar-') ||
    nombre.startsWith('asistida-') ||
    nombre.startsWith('mis-citas') ||
    nombre === 'avisos-cliente' ||
    nombre.startsWith('agenda-') ||
    nombre.startsWith('horarios') ||
    nombre === 'reportes' ||
    nombre === 'usuarios'
  ) {
    await testInfo.attach(nombre, {
      body: await page.screenshot({
        fullPage: nombre.startsWith('horarios') || nombre === 'reportes' || nombre === 'usuarios',
        ...(['reportes', 'usuarios'].includes(nombre) || nombre.startsWith('horarios')
          ? { path: testInfo.outputPath(nombre + '.png') }
          : {}),
      }),
      contentType: 'image/png',
    });
  }
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
    nombre,
  ).toBe(true);
}

function citaAgenda(page: Page, codigo: string) {
  return page
    .getByRole('article', { name: codigo, exact: true })
    .or(page.getByRole('row', { name: codigo, exact: true }));
}

async function accionAgenda(page: Page, cita: Locator, nombre: string) {
  await expect(page.locator('app-agenda .linea-tiempo')).toHaveAttribute('aria-busy', 'false');
  await expect(cita).toBeVisible();
  const principal = cita.getByRole('button', { name: nombre, exact: true });
  if (await principal.isVisible()) await principal.click();
  else {
    await cita.getByRole('button', { name: /^Más acciones para / }).click();
    await page.getByRole('menuitem', { name: nombre, exact: true }).click();
  }
}

async function ingresar(page: Page, correo: string, elegirInicial = true) {
  await page.goto('/ingresar');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  // Evita que la clave efímera aparezca en los pasos del informe HTML.
  await page.getByLabel('Contraseña', { exact: true }).evaluate((elemento, valor) => {
    (elemento as HTMLInputElement).value = valor;
    elemento.dispatchEvent(new Event('input', { bubbles: true }));
  }, process.env['BT_DEMO_PASSWORD']!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  // Esperar el destino evita abrir el menú mientras NavigationEnd lo está cerrando.
  await expect(page).not.toHaveURL(/\/ingresar(?:\?|$)/);
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  if (elegirInicial) {
    // Estas dos cuentas del seed tienen dos relaciones; las demás entran directamente.
    if (['cliente@ejemplo.test', 'multirol@ejemplo.test'].includes(correo))
      await page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true }).click();
    await expect(page).toHaveURL(/\/n\/barberturno\//);
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  }
  if (elegirInicial) await comprobarSalida(page);
}

/** En móvil los avisos del cliente se consultan desde la campana; en escritorio, en su columna. */
async function comprobarAvisoCliente(page: Page, mensaje: string | RegExp) {
  const movil = (page.viewportSize()?.width ?? 1440) <= 767;
  if (movil) {
    await expect(page.locator('app-mis-citas app-avisos-panel')).toHaveCount(0);
    await page.getByRole('button', { name: /^Avisos: \d+ sin leer$/ }).click();
  } else {
    await expect(page.getByRole('button', { name: /^Avisos: \d+ sin leer$/ })).toHaveCount(0);
  }
  await expect(page.locator('app-avisos-panel')).toContainText(mensaje);
  if (movil) {
    const dialogo = page.getByRole('dialog', { name: 'Avisos', exact: true });
    await dialogo.getByRole('button', { name: 'Cerrar', exact: true }).click();
    await expect(dialogo).toHaveCount(0);
    await expect(page.getByRole('button', { name: /^Avisos: \d+ sin leer$/ })).toBeFocused();
  }
}

async function comprobarSalida(page: Page) {
  const cajon = page.getByRole('button', { name: 'Abrir menú', exact: true });
  const cuenta = page.getByRole('button', { name: 'Abrir cuenta', exact: true });
  const abrir = (await cajon.isVisible()) ? cajon : cuenta;
  if (await abrir.isVisible()) {
    await abrir.focus();
    await abrir.click();
  }
  const salir = controlSalir(page);
  await expect(salir).toBeVisible();
  if (await abrir.isVisible()) {
    if (await cajon.isVisible()) {
      await expect(page.locator('mat-sidenav')).not.toHaveClass(/mat-drawer-animating/);
      await expect(page.getByRole('button', { name: 'Cerrar menú', exact: true })).toBeVisible();
    } else {
      await expect(page.getByRole('menuitem', { name: 'Mi cuenta', exact: true })).toBeVisible();
    }
    // Escape debe partir de un control del cajón, donde Material escucha el teclado.
    await salir.focus();
    await expect(salir).toBeFocused();
    await page.keyboard.press('Escape');
    // El cajón devuelve el foco al terminar su animación; no anticipar el siguiente Enter.
    await expect(page.locator('mat-sidenav')).toBeHidden();
    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(abrir).toBeFocused();
  }
}

function controlSalir(page: Page) {
  return page
    .getByRole('button', { name: 'Salir', exact: true })
    .or(page.getByRole('menuitem', { name: 'Salir', exact: true }));
}

async function seleccionar(page: Page, etiqueta: string, opcion: string) {
  if (etiqueta === 'Profesional') {
    await page.getByRole('radio', { name: new RegExp(opcion) }).check();
    return;
  }
  await page.getByRole('combobox', { name: etiqueta, exact: true }).click();
  await page.getByRole('option', { name: opcion, exact: true }).click();
}

/** Compara el token enviado por el navegador sin imprimir su valor en informes ni aserciones. */
async function huellaCsrf(respuesta: Response) {
  const token = await respuesta.request().headerValue('X-XSRF-TOKEN');
  expect(Boolean(token)).toBe(true);
  return createHash('sha256')
    .update(token ?? '')
    .digest('hex')
    .slice(0, 12);
}

async function fechaFranja(page: Page, fecha: string, hora: string) {
  // NativeDateAdapter usa los campos civiles locales del calendario, incluso en Madrid.
  const entrada = page.getByLabel('Otra fecha, hasta 30 días', { exact: true });
  const [ano, mes, dia] = fecha.split('-');
  await entrada.fill(`${mes}/${dia}/${ano}`);
  await entrada.press('Tab');
  await expect(page.locator('[data-fecha-consultada]')).toHaveAttribute(
    'data-fecha-consultada',
    fecha,
  );
  // La fecha se presenta antes de que termine la consulta de sus horas.
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  await page.getByRole('button', { name: new RegExp(`^${hora}–`) }).click();
  await expect(page.locator('.resumen')).toContainText(hora);
}

async function prepararReserva(page: Page, fecha: string, hora: string) {
  await page.goto('/n/barberturno/reservar');
  await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
  await seleccionar(page, 'Profesional', 'Carlos');
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await fechaFranja(page, fecha, hora);
}

test.beforeAll(async ({ servidor }) => {
  expect(servidor).toContain('8081');
  const api = await sesionApi('multirol@ejemplo.test');
  try {
    const servicios = await (await api.get('/api/n/barberturno/servicios')).json();
    const barberos = await (await api.get('/api/n/barberturno/barberos')).json();
    const clientes = await (
      await api.get('/api/n/barberturno/usuarios?rol=CLIENTE&tamano=100')
    ).json();
    const carlos = barberos.find((b: { nombre: string }) => b.nombre === 'Carlos');
    const barba = servicios.find((s: { nombre: string }) => s.nombre === 'Barba');
    const ana = clientes.contenido.find((u: { correo: string }) => u.correo === 'ana@ejemplo.test');
    const reservas = await (
      await api.get('/api/n/barberturno/reservas?desde=2026-09-28&hasta=2026-10-01')
    ).json();
    cercana = reservas.contenido.find((r: { inicio: string }) =>
      r.inicio.startsWith('2026-09-28T10:00'),
    ).codigo;
    pendiente = reservas.contenido.find((r: { estado: string }) => r.estado === 'PENDIENTE').codigo;
    atencion = (
      await (
        await escribir(api, '/api/n/barberturno/reservas', {
          clienteId: ana.id,
          barberoId: carlos.id,
          servicioId: barba.id,
          inicio: '2026-09-28T09:10:00-05:00',
        })
      ).json()
    ).codigo;
    // K1 ya ocupa 16–17 en la demo. Retirarlo por API permite ensayar el alta del guion.
    const bloqueos = await (
      await api.get(
        `/api/n/barberturno/barberos/${carlos.id}/bloqueos?desde=2026-10-01&hasta=2026-10-01`,
      )
    ).json();
    const estado = await api.storageState();
    const token = estado.cookies.find((c) => c.name === 'XSRF-TOKEN')!.value;
    for (const bloqueo of bloqueos) {
      expect(
        (
          await api.delete(`/api/n/barberturno/bloqueos/${bloqueo.id}`, {
            headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
          })
        ).status(),
      ).toBe(204);
    }
  } finally {
    await api.dispose();
  }
});

test('01 · cliente reserva en tres pasos, ve el aviso, reprograma y cancela', async ({
  page,
}, testInfo) => {
  await page.goto('/ingresar');
  await revisar(page, testInfo, 'ingresar');
  await page.getByLabel('Correo', { exact: true }).focus();
  await page.keyboard.press('Tab');
  await expect(page.getByLabel('Contraseña', { exact: true })).toBeFocused();
  expect(
    await page
      .getByLabel('Contraseña', { exact: true })
      .evaluate((e) => getComputedStyle(e).outlineStyle),
  ).not.toBe('none');
  await ingresar(page, 'cliente@ejemplo.test');
  const inicio = Date.now();
  await page.goto('/n/barberturno/reservar');
  await expect(page.locator('mat-step-header')).toHaveCount(3);
  await expect(page.locator('mat-step-header').first()).toBeVisible();
  if ((page.viewportSize()?.width ?? 1440) <= 767) {
    expect(
      await page
        .locator('mat-step-header .mat-step-text-label')
        .evaluateAll((etiquetas) => etiquetas.every((e) => e.scrollWidth <= e.clientWidth)),
    ).toBe(true);
  }
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
  if ((page.viewportSize()?.width ?? 1440) <= 767) {
    await expect(page.locator('h1')).toHaveCSS('clip-path', 'inset(50%)');
    await expect(page.locator('.titulo-pagina')).toHaveText('Reservar un turno BarberTurno');
    await expect(page.locator('.titulo-pagina')).not.toHaveAttribute('role', 'heading');
  }
  await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
  await seleccionar(page, 'Profesional', 'Carlos');
  await revisar(page, testInfo, 'reservar-servicio');
  const avanzar = page.getByRole('button', { name: 'Elegir fecha y hora' });
  await avanzar.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('[data-paso="1"]')).toBeFocused();
  await page.route(
    '**/api/n/barberturno/disponibilidad?**',
    (ruta) =>
      ruta.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ detail: 'Revise su conexión.' }),
      }),
    { times: 1 },
  );
  const fallida = page.waitForResponse(
    (r) => r.url().includes('/api/n/barberturno/disponibilidad?') && r.status() === 503,
  );
  const entrada = page.getByLabel('Otra fecha, hasta 30 días', { exact: true });
  await entrada.fill('10/01/2026');
  await entrada.press('Tab');
  const consultaFallida = await fallida;
  await expect(page.getByRole('alert')).toContainText('Su servicio y profesional se conservan');
  await expect(page.getByRole('button', { name: 'Siguiente', exact: true })).toBeDisabled();
  // El interceptor común anuncia también el error por snackbar; se cierra para revisar la alerta.
  await page
    .locator('mat-snack-bar-container')
    .getByRole('button', { name: 'Cerrar', exact: true })
    .click();
  await expect(page.locator('mat-snack-bar-container')).toHaveCount(0);
  await revisar(page, testInfo, 'reservar-error');
  const reintento = page.waitForResponse(
    (r) => r.url().includes('/api/n/barberturno/disponibilidad?') && r.status() === 200,
  );
  await page.getByRole('button', { name: 'Reintentar', exact: true }).click();
  expect((await reintento).url()).toBe(consultaFallida.url());
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(
    page.getByRole('heading', { name: 'jueves, 1 de octubre', exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: /^11:00–11:30/ })).toBeVisible();
  await revisar(page, testInfo, 'reservar-franja');
  const hora = page.getByRole('button', { name: /^11:00–11:30/ });
  await hora.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('[data-paso="2"]')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Confirmar reserva', exact: true })).toBeFocused();
  await expect(page.locator('.resumen .tique-hora')).toHaveText('11:00');
  await expect(page.locator('.resumen .tique-banda')).toContainText(
    'Hasta las 11:30, hora de Lima',
  );
  await revisar(page, testInfo, 'reservar-confirmacion');
  // Leer el cuerpo real antes de entregarlo a la SPA y permitir su navegación.
  let creada!: ReservaDto;
  await page.route(
    '**/api/n/barberturno/reservas',
    async (ruta) => {
      expect(ruta.request().method()).toBe('POST');
      const cabeceras = await ruta.request().allHeaders();
      const cookieCsrf = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(cabeceras['cookie'] ?? '')?.[1];
      expect(!!cookieCsrf && decodeURIComponent(cookieCsrf) === cabeceras['x-xsrf-token']).toBe(
        true,
      );
      expect((cabeceras['cookie'] ?? '').includes('BT_SESION=')).toBe(true);
      const respuesta = await ruta.fetch({ maxRetries: 0, maxRedirects: 0 });
      creada = (await respuesta.json()) as ReservaDto;
      await ruta.fulfill({ response: respuesta });
    },
    { times: 1 },
  );
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/n/barberturno/reservas') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Confirmar reserva', exact: true }).click();
  expect((await respuesta).status()).toBe(201);
  expect(creada).toBeDefined();
  await expect(page).toHaveURL(/\/mis-citas/);
  expect(Date.now() - inicio).toBeLessThan(180_000);
  await testInfo.attach('usabilidad-automatizada', {
    body: JSON.stringify({ pasos: 3, limite: 5, duracionMs: Date.now() - inicio }),
    contentType: 'application/json',
  });
  let tarjeta = page.locator('app-reserva-tarjeta').filter({ hasText: creada.codigo });
  await expect(tarjeta).toContainText('11:00');
  await comprobarAvisoCliente(page, creada.codigo);
  await revisar(page, testInfo, 'mis-citas');
  await tarjeta.getByRole('link', { name: 'Reprogramar' }).click();
  if ((page.viewportSize()?.width ?? 1440) <= 767) {
    const avanzarReprogramacion = page.getByRole('button', { name: 'Elegir fecha y hora' });
    await avanzarReprogramacion.scrollIntoViewIfNeeded();
    const areaAccion = await avanzarReprogramacion.boundingBox();
    const areaBarra = await page.locator('.barra-inferior').boundingBox();
    await testInfo.attach('reprogramar-barra-inferior', {
      body: await page.screenshot(),
      contentType: 'image/png',
    });
    expect(areaAccion).not.toBeNull();
    expect(areaBarra).not.toBeNull();
    expect(
      areaAccion!.y + areaAccion!.height,
      JSON.stringify({ areaAccion, areaBarra }),
    ).toBeLessThanOrEqual(areaBarra!.y);
  }
  await page.getByRole('button', { name: 'Elegir fecha y hora' }).click();
  await fechaFranja(page, '2026-10-01', '12:00');
  await expect(page.locator('del')).toContainText('Horario anterior, se reemplaza:');
  await expect(page.locator('del')).toContainText('11:00');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Reprogramar cita');
  await page.getByRole('button', { name: 'Confirmar reprogramación' }).click();
  await expect(page).toHaveURL(/\/mis-citas/);
  tarjeta = page.locator('app-reserva-tarjeta').filter({ hasText: creada.codigo });
  await expect(tarjeta).toContainText('12:00');
  await tarjeta.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(
    await page
      .getByRole('dialog')
      .locator('mat-form-field')
      .evaluateAll(
        (campos) =>
          campos.length > 0 &&
          campos.every((campo) => campo.classList.contains('mat-form-field-appearance-outline')),
      ),
  ).toBe(true);
  const cancelacion = page.waitForResponse((r) =>
    r.url().endsWith(`/api/n/barberturno/reservas/${creada.id}/cancelacion`),
  );
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /Cancelar cita|Confirmar cancelación/ })
    .click();
  const respuestaCancelacion = await cancelacion;
  if (respuestaCancelacion.status() !== 200) {
    const cabeceras = await respuestaCancelacion.request().allHeaders();
    const cookie = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(cabeceras['cookie'] ?? '')?.[1];
    await testInfo.attach('diagnostico-cancelacion', {
      body: JSON.stringify({
        status: respuestaCancelacion.status(),
        csrfPresente: !!cabeceras['x-xsrf-token'],
        cookieCsrfPresente: !!cookie,
        csrfCoincide: !!cookie && decodeURIComponent(cookie) === cabeceras['x-xsrf-token'],
        sesionPresente: (cabeceras['cookie'] ?? '').includes('BT_SESION='),
      }),
      contentType: 'application/json',
    });
  }
  expect(respuestaCancelacion.status()).toBe(200);
  await expect(tarjeta).toContainText('Cancelada');
  await expect(tarjeta.getByRole('button', { name: 'Cancelar', exact: true })).toHaveCount(0);
  await comprobarAvisoCliente(page, /cancelad/i);
});

test('02 · BT-104 conserva las horas de Lima y oculta las acciones a menos de dos horas', async ({
  page,
}) => {
  await ingresar(page, 'cliente@ejemplo.test');
  await page.goto('/n/barberturno/mis-citas');
  const tarjeta = page.locator('app-reserva-tarjeta').filter({ hasText: cercana });
  await expect(tarjeta).toContainText('10:00');
  await expect(tarjeta).toContainText(
    'Esta cita no permite cambios por la web. Comuníquese con la barbería.',
  );
  const citas: Pagina<ReservaDto> = await (
    await page.request.get('/api/n/barberturno/reservas/mias?tamano=100')
  ).json();
  const restringida = citas.contenido.find((cita) => cita.codigo === cercana)!;
  expect(restringida.permisos.cancelar).toBe(false);
  expect(restringida.permisos.reprogramar).toBe(false);
  await expect(tarjeta.getByRole('link', { name: 'Reprogramar' })).toHaveCount(0);
  await expect(tarjeta.getByRole('button', { name: 'Cancelar' })).toHaveCount(0);
});

test('03 · Carlos inicia y completa la atención y confirma BT-100 en Semana', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'carlos@ejemplo.test');
  await page.goto('/n/barberturno/agenda');
  await page.getByLabel('Fecha de Lima', { exact: true }).fill('2026-09-28');
  await page.getByRole('button', { name: 'Actualizar agenda' }).click();
  const fila = citaAgenda(page, atencion);
  await expect(fila).toContainText('09:10');
  await revisar(page, testInfo, 'agenda-dia');
  for (const accion of ['Iniciar atención', 'Completar']) {
    await accionAgenda(page, fila, accion);
    const transicion = page.waitForResponse(
      (r) => r.url().endsWith('/transiciones') && r.request().method() === 'POST',
    );
    await page.getByRole('dialog').getByRole('button', { name: accion, exact: true }).click();
    const respuestaTransicion = await transicion;
    if (respuestaTransicion.status() !== 200) {
      const cabeceras = await respuestaTransicion.request().allHeaders();
      const cookie = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(cabeceras['cookie'] ?? '')?.[1];
      await testInfo.attach('diagnostico-transicion', {
        body: JSON.stringify({
          accion,
          status: respuestaTransicion.status(),
          csrfPresente: !!cabeceras['x-xsrf-token'],
          cookieCsrfPresente: !!cookie,
          csrfCoincide: !!cookie && decodeURIComponent(cookie) === cabeceras['x-xsrf-token'],
          sesionPresente: (cabeceras['cookie'] ?? '').includes('BT_SESION='),
        }),
        contentType: 'application/json',
      });
    }
    expect(respuestaTransicion.status()).toBe(200);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(fila).toContainText(accion === 'Iniciar atención' ? 'En atención' : 'Completada');
  }
  await expect(fila).toContainText('Completada');
  await page.getByRole('radio', { name: 'Semana', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Semana', exact: true })).toBeChecked();
  const solicitud = citaAgenda(page, pendiente);
  await expect(solicitud).toContainText('Pendiente');
  await accionAgenda(page, solicitud, 'Confirmar');
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar', exact: true }).click();
  await expect(solicitud).toContainText('Confirmada');
  if (page.viewportSize()!.width >= 1200) {
    const cabeceras = solicitud.locator('..').getByRole('columnheader');
    const celdas = solicitud.getByRole('cell');
    await expect(celdas).toHaveCount(6);
    for (let indice = 0; indice < 6; indice++) {
      const cabecera = await cabeceras.nth(indice).boundingBox();
      const celda = await celdas.nth(indice).boundingBox();
      expect(cabecera).not.toBeNull();
      expect(celda).not.toBeNull();
      expect(Math.abs(cabecera!.x - celda!.x)).toBeLessThan(1);
    }
  }
  await revisar(page, testInfo, 'agenda-semana');
});

test('04 · administrador edita servicio y jornada, prueba bloqueos y concilia reportes', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'multirol@ejemplo.test');
  await page.goto('/n/barberturno/agenda');
  await page.getByLabel('Fecha de Lima', { exact: true }).fill('2026-09-28');
  await page.getByRole('button', { name: 'Actualizar agenda', exact: true }).click();
  const cita = citaAgenda(page, cercana);
  const mas = cita.getByRole('button', { name: `Más acciones para ${cercana}`, exact: true });
  for (const tecla of ['Enter', 'Space']) {
    await mas.focus();
    await mas.press(tecla);
    const items = page.getByRole('menuitem');
    await expect(items.first()).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await expect(items.nth(1)).toBeFocused();
    await page.keyboard.press('ArrowUp');
    await expect(items.first()).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(mas).toBeFocused();
  }
  await accionAgenda(page, cita, 'Reprogramar');
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog').getByLabel('Fecha de Lima', { exact: true })).toBeFocused();
  await expect(
    page.getByRole('dialog').getByLabel('Motivo obligatorio', { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('dialog').getByRole('button', { name: 'Reprogramar', exact: true }),
  ).toBeDisabled();
  await revisar(page, testInfo, 'agenda-reprogramar');
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Conservar cita', exact: true })
    .click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await accionAgenda(page, cita, 'Ver cambios');
  const auditoria = page.getByRole('dialog', { name: `Cambios de ${cercana}`, exact: true });
  await expect(auditoria).toContainText('Actor:');
  await expect(auditoria.getByRole('button', { name: 'Cerrar', exact: true })).toBeFocused();
  await revisar(page, testInfo, 'agenda-auditoria');
  await page.getByRole('dialog').getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.goto('/n/barberturno/admin/servicios');
  await expect(
    page.getByRole('button', { name: 'Editar servicio Corte clásico', exact: true }),
  ).toBeVisible();
  const viewportCatalogo = page.viewportSize()!;
  for (const width of [1024, 1199, 1200]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(page.getByRole('table', { name: 'Servicios', exact: true })).toHaveCount(
      width >= 1200 ? 1 : 0,
    );
    await expect(page.getByRole('list', { name: 'Servicios', exact: true })).toHaveCount(
      width >= 1200 ? 0 : 1,
    );
    await expect(
      page.getByRole('button', { name: 'Editar servicio Corte clásico', exact: true }),
    ).toHaveCount(1);
    await expect(
      page.getByRole('button', { name: 'Desactivar Corte clásico', exact: true }),
    ).toHaveCount(1);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
      ),
    ).toBe(true);
  }
  await page.setViewportSize(viewportCatalogo);
  await revisar(page, testInfo, 'servicios');
  await page.getByRole('button', { name: 'Desactivar Corte clásico', exact: true }).click();
  await expect(
    page.getByRole('dialog').getByRole('button', { name: 'Desactivar', exact: true }),
  ).toHaveClass(/riesgo/);
  const confirmacionEstado = page.getByRole('dialog');
  await expect(
    confirmacionEstado.getByRole('button', { name: 'Cancelar', exact: true }),
  ).toBeFocused();
  // Axe debe leer los colores finales, después de la transición pública de apertura.
  await confirmacionEstado.evaluate(async (dialogo) => {
    await Promise.all(
      dialogo
        .getAnimations({ subtree: true })
        .map((animacion) => animacion.finished.catch(() => undefined)),
    );
  });
  await revisar(page, testInfo, 'servicio-confirmacion');
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await page.getByRole('button', { name: 'Editar servicio Corte clásico', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(
    await page
      .getByRole('dialog')
      .locator('mat-form-field')
      .evaluateAll(
        (campos) =>
          campos.length > 0 &&
          campos.every((campo) => campo.classList.contains('mat-form-field-appearance-outline')),
      ),
  ).toBe(true);
  // Material enfoca Nombre al terminar de abrir; esperar evita que intercepte el relleno.
  await expect(page.getByLabel('Nombre', { exact: true })).toBeFocused();
  await page.getByLabel('Descripción', { exact: true }).fill('Corte y acabado de demostración E2E');
  await expect(page.getByLabel('Nombre', { exact: true })).toHaveValue('Corte clásico');
  await expect(page.getByLabel('Descripción', { exact: true })).toHaveValue(
    'Corte y acabado de demostración E2E',
  );
  await page.getByRole('button', { name: 'Guardar servicio', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const catalogo =
    (page.viewportSize()?.width ?? 1440) >= 1200
      ? page.getByRole('table', { name: 'Servicios', exact: true })
      : page.getByRole('list', { name: 'Servicios', exact: true });
  await expect(catalogo).toContainText('Corte y acabado de demostración E2E');
  await page.goto('/n/barberturno/admin/barberos');
  await expect(
    page.getByRole('button', { name: 'Editar barbero Carlos', exact: true }),
  ).toHaveCount(1);
  await revisar(page, testInfo, 'barberos');
  await page.getByRole('button', { name: 'Nuevo barbero', exact: true }).click();
  const altaBarbero = page.getByRole('dialog');
  await expect(
    altaBarbero.getByRole('combobox', { name: 'Tipo de alta', exact: true }),
  ).toBeFocused();
  const campoTipo = altaBarbero
    .locator('mat-form-field')
    .filter({ has: page.locator('mat-label', { hasText: 'Tipo de alta' }) });
  const contenidoBarbero = altaBarbero.locator('mat-dialog-content');
  const etiquetaTipo = await campoTipo.locator('mat-label').boundingBox();
  const limiteContenido = await contenidoBarbero.boundingBox();
  expect(etiquetaTipo!.y).toBeGreaterThanOrEqual(limiteContenido!.y);
  const generar = altaBarbero.getByRole('button', { name: 'Generar', exact: true });
  await generar.click();
  await expect(altaBarbero.getByLabel('Contraseña temporal', { exact: true })).not.toHaveValue('');
  const notaTemporal = altaBarbero.getByText(
    'Tras crear la cuenta se mostrará una sola vez para comunicarla en persona.',
    { exact: true },
  );
  await notaTemporal.scrollIntoViewIfNeeded();
  const notaCaja = await notaTemporal.boundingBox();
  const contenidoCaja = await contenidoBarbero.boundingBox();
  expect(notaCaja!.y).toBeGreaterThanOrEqual(contenidoCaja!.y);
  expect(notaCaja!.y + notaCaja!.height).toBeLessThanOrEqual(
    contenidoCaja!.y + contenidoCaja!.height + 1,
  );
  const pieBarbero = await altaBarbero.locator('mat-dialog-actions').boundingBox();
  expect(pieBarbero!.y + pieBarbero!.height).toBeLessThanOrEqual(page.viewportSize()!.height - 16);
  await revisar(page, testInfo, 'barbero-dialogo');
  await altaBarbero.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await page.goto('/n/barberturno/admin/horarios');
  await expect(
    page.getByText('Elija un barbero para ver su semana y sus bloqueos.', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('app-horarios .disposicion')).toHaveCount(0);
  await revisar(page, testInfo, 'horarios-sin-barbero');
  await seleccionar(page, 'Barbero', 'Carlos');
  await expect(page.getByLabel('Lunes · Fin 2', { exact: true })).toBeVisible();
  const viewportHorarios = page.viewportSize()!;
  for (const width of [360, 1024, 1199, 1200, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    const disposicion = page.locator('app-horarios .disposicion');
    if (width >= 1200) await expect(disposicion).toHaveClass(/dos-columnas/);
    else await expect(disposicion).not.toHaveClass(/dos-columnas/);
    // Leer todas las cajas en el mismo frame: el navegador puede ajustar el scroll al redimensionar.
    const { semana, bloqueos, alta } = await page.locator('app-horarios').evaluate((pagina) => {
      const caja = (id: string) => {
        const { x, y, width, height } = pagina
          .querySelector('[aria-labelledby="' + id + '"]')!
          .getBoundingClientRect();
        return { x, y, width, height };
      };
      return {
        semana: caja('titulo-semana'),
        bloqueos: caja('titulo-bloqueos'),
        alta: caja('titulo-alta'),
      };
    });
    if (width >= 1200) {
      expect(bloqueos!.x).toBeGreaterThan(semana!.x + semana!.width);
      expect(Math.abs(bloqueos!.y - semana!.y)).toBeLessThan(1);
      expect(semana!.width / bloqueos!.width).toBeCloseTo(2, 1);
    } else {
      expect(bloqueos!.y).toBeGreaterThan(semana!.y + semana!.height);
      expect(bloqueos!.x).toBe(semana!.x);
    }
    expect(alta!.y).toBeGreaterThan(bloqueos!.y + bloqueos!.height);
    if (width === 1200) {
      await page.screenshot({ path: testInfo.outputPath('horarios-1200.png'), fullPage: true });
    }
    const rango = await page.locator('.rango').evaluate((e) => {
      const campos = Array.from(e.querySelectorAll('input'));
      const boton = e.querySelector('button')!.getBoundingClientRect();
      const cajas = campos.map((c) => c.getBoundingClientRect());
      return {
        fuentes: campos.map((c) => getComputedStyle(c).fontSize),
        fin: Math.max(...cajas.map((c) => c.bottom)),
        botonY: boton.y,
        anchoBoton: boton.width,
        ancho: e.getBoundingClientRect().width,
      };
    });
    expect(rango.fuentes).toEqual(['16px', '16px']);
    expect(rango.botonY).toBeGreaterThan(rango.fin);
    expect(rango.anchoBoton).toBeCloseTo(rango.ancho, 0);
    expect(await page.getByLabel('Lunes · Fin 2', { exact: true }).inputValue()).toBe('18:00');
    await expect(
      page.getByRole('button', { name: 'Quitar intervalo 2 del Lunes', exact: true }),
    ).toHaveCount(1);
    const camposLunes = await page
      .locator('.dia')
      .first()
      .locator('input')
      .evaluateAll((campos) => {
        const [inicio, fin] = campos.map((campo) => campo.getBoundingClientRect());
        return {
          inicio: { x: inicio.x, y: inicio.y, ancho: inicio.width },
          fin: { x: fin.x, y: fin.y, ancho: fin.width },
        };
      });
    expect(camposLunes.inicio.y).toBe(camposLunes.fin.y);
    expect(camposLunes.fin.x).toBeGreaterThan(camposLunes.inicio.x);
    expect(camposLunes.inicio.ancho).toBeCloseTo(camposLunes.fin.ancho, 0);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
      ),
    ).toBe(true);
  }
  await page.setViewportSize(viewportHorarios);
  await page.getByLabel('Lunes · Fin 2', { exact: true }).focus();
  await expect(page.getByLabel('Lunes · Fin 2', { exact: true })).toBeFocused();
  expect(
    await page
      .getByLabel('Lunes · Fin 2', { exact: true })
      .evaluate((campo) => getComputedStyle(campo).outlineStyle),
  ).toBe('solid');
  await revisar(page, testInfo, 'horarios');
  await page.getByLabel('Lunes · Fin 2', { exact: true }).fill('18:10');
  await page.getByRole('button', { name: 'Guardar semana' }).click();
  await expect(page.getByRole('status').filter({ hasText: /guardad/i })).toBeVisible();
  await page.getByLabel('Fecha en Lima', { exact: true }).fill('2026-10-01');
  await page.getByLabel('Hora de inicio', { exact: true }).fill('10:10');
  await page.getByLabel('Hora de fin', { exact: true }).fill('10:20');
  await page.getByLabel('Motivo', { exact: true }).fill('Bloqueo de demostración E2E');
  await page.getByRole('button', { name: 'Agregar bloqueo' }).click();
  await expect(page.getByRole('alert')).toContainText('Reprograme o cancele primero');
  await page.getByLabel('Hora de inicio', { exact: true }).fill('16:00');
  await page.getByLabel('Hora de fin', { exact: true }).fill('17:00');
  await page.getByRole('button', { name: 'Agregar bloqueo' }).click();
  await expect(page.getByRole('status').filter({ hasText: /creado|agregado/i })).toBeVisible();
  await revisar(page, testInfo, 'horarios-bloqueo-creado');
  await page.goto('/n/barberturno/admin/reportes');
  await page.getByLabel('Desde', { exact: true }).fill('2026-09-27');
  await page.getByLabel('Hasta', { exact: true }).fill('2026-10-01');
  await page.getByRole('button', { name: 'Aplicar', exact: true }).click();
  await expect(
    page.getByText('7 reservas con los filtros aplicados.', { exact: true }),
  ).toBeVisible();
  await expect(page.getByText('Los estados suman el total.', { exact: true })).toBeVisible();
  const cifras = page.locator('.estadistica strong');
  await expect(cifras).toHaveText(['7', '0', '4', '0', '2', '0', '1']);
  expect(
    await page
      .locator('[aria-labelledby="por-servicio"] progress')
      .evaluateAll((barras) =>
        barras.map((b) => (b as HTMLProgressElement).value).sort((a, b) => a - b),
      ),
  ).toEqual([3, 4]);
  expect(
    await page
      .locator('[aria-labelledby="por-profesional"] progress')
      .evaluateAll((barras) =>
        barras.map((b) => (b as HTMLProgressElement).value).sort((a, b) => a - b),
      ),
  ).toEqual([1, 6]);
  const historial = page.locator('app-reporte-historial');
  if (page.viewportSize()!.width >= 1200) {
    await expect(historial.getByRole('table')).toHaveCount(1);
    await expect(historial.locator('tbody tr')).toHaveCount(7);
    await expect(historial.getByRole('listitem')).toHaveCount(0);
  } else {
    await expect(historial.getByRole('list', { name: 'Historial operativo' })).toBeVisible();
    await expect(historial.getByRole('listitem')).toHaveCount(7);
    await expect(historial.getByRole('table')).toHaveCount(0);
  }
  const columnasCifras = await page
    .locator('.estadisticas')
    .evaluate((e) => getComputedStyle(e).gridTemplateColumns.split(' ').length);
  if (page.viewportSize()!.width === 360) expect(columnasCifras).toBe(2);
  await revisar(page, testInfo, 'reportes');

  // La descarga conserva el periodo/profesional aplicados aunque se edite el formulario.
  await page.getByRole('combobox', { name: 'Profesional', exact: true }).click();
  await page.getByRole('option', { name: 'Carlos', exact: true }).click();
  const resumenFiltrado = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/n/barberturno/reportes/resumen' &&
      new URL(r.url()).searchParams.has('barberoId'),
  );
  await page.getByRole('button', { name: 'Aplicar', exact: true }).click();
  const aplicado = new URL((await resumenFiltrado).url());
  await expect(page.locator('app-reportes [aria-busy="true"]')).toHaveCount(0);
  await page.getByLabel('Desde', { exact: true }).fill('2027-01-01');
  const respuestaExcel = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/n/barberturno/reportes/resumen.xlsx',
  );
  const descargado = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel', exact: true }).click();
  const respuestaArchivo = await respuestaExcel;
  expect(respuestaArchivo.status()).toBe(200);
  expect(respuestaArchivo.headers()['content-type']).toBe(
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  );
  expect(respuestaArchivo.headers()['cache-control']).toBe('no-store');
  const consultaExcel = new URL(respuestaArchivo.url());
  expect([...consultaExcel.searchParams.entries()].sort()).toEqual(
    [...aplicado.searchParams.entries()].sort(),
  );
  const descarga = await descargado;
  expect(descarga.suggestedFilename()).toBe('reporte-barberturno-2026-09-27_2026-10-01.xlsx');
  expect(await descarga.failure()).toBeNull();
  const bytes = await readFile((await descarga.path())!);
  expect([...bytes.subarray(0, 4)]).toEqual([0x50, 0x4b, 0x03, 0x04]);
  expect(bytes.includes(Buffer.from('xl/workbook.xml'))).toBe(true);
  expect(bytes.includes(Buffer.from('xl/worksheets/sheet1.xml'))).toBe(true);
  await expect(page.getByRole('button', { name: 'Descargar Excel', exact: true })).toBeEnabled();

  // Solo lectura: estas cuentas siguen disponibles para los casos siguientes.
  await page.goto('/n/barberturno/admin/usuarios');
  await expect(page.locator('app-usuarios app-lista-adaptable')).toBeVisible();
  await page.getByRole('combobox', { name: 'Rol', exact: true }).click();
  await page.getByRole('option', { name: 'Barbero', exact: true }).click();
  const consultaUsuarios = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/n/barberturno/usuarios' &&
      new URL(r.url()).searchParams.get('rol') === 'BARBERO',
  );
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  const respuestaUsuarios = await consultaUsuarios;
  expect(respuestaUsuarios.status()).toBe(200);
  const usuarios = (await respuestaUsuarios.json()) as Pagina<UsuarioAdminDto>;
  expect(usuarios.contenido.length).toBeGreaterThan(0);
  const escritorioUsuarios = page.viewportSize()!.width >= 1200;
  const listaUsuarios = page.locator('app-usuarios app-lista-adaptable');
  await expect(listaUsuarios.getByRole('table')).toHaveCount(escritorioUsuarios ? 1 : 0);
  await expect(listaUsuarios.getByRole('list')).toHaveCount(escritorioUsuarios ? 0 : 1);
  const fichasUsuarios = listaUsuarios.locator(
    escritorioUsuarios ? 'tbody tr' : '[role="listitem"]',
  );
  await expect(fichasUsuarios).toHaveCount(usuarios.contenido.length);
  for (const [i, usuario] of usuarios.contenido.entries()) {
    expect(usuario.rol).toBe('BARBERO');
    const ficha = fichasUsuarios.nth(i);
    await expect(ficha).toContainText(usuario.nombre);
    await expect(ficha).toContainText(usuario.correo);
    const correoVisible = ficha.getByText(usuario.correo, { exact: true });
    await expect(correoVisible).toHaveCSS('font-size', '14px');
    await expect(correoVisible).toHaveCSS('line-height', '20px');
    await expect(ficha).toContainText('Barbero');
    await expect(ficha).toContainText(usuario.activo ? 'Activo' : 'Inactivo');
    await expect(ficha).toContainText(
      usuario.debeCambiarPassword ? 'Cambio obligatorio' : 'Personal',
    );
    await expect(
      ficha.getByRole('button', {
        name: 'Restablecer contraseña de ' + usuario.nombre,
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      ficha.getByRole('button', {
        name: (usuario.activo ? 'Desactivar ' : 'Activar ') + usuario.nombre,
        exact: true,
      }),
    ).toBeVisible();
  }
  await revisar(page, testInfo, 'usuarios');
});

test('05 · cliente redirigido de administración y protegido después de logout', async ({
  page,
}) => {
  await ingresar(page, 'cliente@ejemplo.test');
  await page.goto('/n/barberturno/admin/servicios');
  await expect(page).toHaveURL(/\/sin-acceso/);
  await expect(page.getByText('No tiene acceso a esta sección.', { exact: true })).toBeVisible();
  await salirConRespuesta(page);
  await page.goto('/n/barberturno/mis-citas');
  await expect(page).toHaveURL(/\/ingresar/);
});

test('06 · dos contextos disputan la misma franja y el perdedor recarga la disponibilidad', async ({
  browser,
}, testInfo) => {
  const contextos = await Promise.all(
    ['ana', 'luis'].map(() =>
      browser.newContext({
        baseURL: 'http://localhost:8081',
        timezoneId: 'Europe/Madrid',
        locale: 'es-PE',
        viewport: testInfo.project.use.viewport,
      }),
    ),
  );
  try {
    const paginas = await Promise.all(contextos.map((c) => c.newPage()));
    for (const [i, pagina] of paginas.entries()) {
      await pagina.clock.setFixedTime(new Date('2026-09-28T09:00:00-05:00'));
      await ingresar(pagina, `${i === 0 ? 'ana' : 'luis'}@ejemplo.test`);
      await prepararReserva(pagina, '2026-10-02', '11:00');
    }
    const respuestas = paginas.map((p) =>
      p.waitForResponse(
        (r) => r.url().endsWith('/api/n/barberturno/reservas') && r.request().method() === 'POST',
      ),
    );
    await Promise.all(
      paginas.map((p) => p.getByRole('button', { name: 'Confirmar reserva', exact: true }).click()),
    );
    const estados = await Promise.all(respuestas.map(async (r) => (await r).status()));
    expect([...estados].sort()).toEqual([201, 409]);
    const perdedor = paginas[estados.indexOf(409)];
    await expect(perdedor.getByRole('alert')).toContainText(/franja.*(disponible|ocupada)/i);
    await expect(perdedor.getByRole('region', { name: 'Horas disponibles' })).toBeVisible();
    await expect(perdedor.getByRole('button', { name: /^11:00–/ })).toHaveCount(0);
    await expect(perdedor.getByRole('button', { name: /^11:30–/ })).toBeVisible();
    // CP-04: el fin del ganador es también un comienzo disponible y reservable.
    await perdedor.getByRole('button', { name: /^11:30–12:00/ }).click();
    const contigua = perdedor.waitForResponse(
      (r) => r.url().endsWith('/api/n/barberturno/reservas') && r.request().method() === 'POST',
    );
    await perdedor.getByRole('button', { name: 'Confirmar reserva', exact: true }).click();
    expect((await contigua).status()).toBe(201);
  } finally {
    await Promise.all(contextos.map((c) => c.close()));
  }
});

test('07 · registro con privacidad y edición persistente del perfil (CP-01)', async ({
  page,
}, testInfo) => {
  await page.goto('/registro');
  const fotoRegistro = page.locator('app-composicion-acceso img');
  if ((page.viewportSize()?.width ?? 1440) >= 1024) {
    await expect(fotoRegistro).toBeVisible();
    expect((await fotoRegistro.boundingBox())!.height).toBeLessThanOrEqual(640);
  } else await expect(fotoRegistro).toBeHidden();
  const correo = `registro-${testInfo.project.name}@ejemplo.test`;
  await page.getByLabel('Nombre', { exact: true }).fill('Cliente E2E de registro');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  await page.getByLabel('Teléfono', { exact: true }).fill('999111222');
  for (const etiqueta of ['Contraseña', 'Confirmar contraseña']) {
    await page.getByLabel(etiqueta, { exact: true }).evaluate((elemento, valor) => {
      (elemento as HTMLInputElement).value = valor;
      elemento.dispatchEvent(new Event('input', { bubbles: true }));
    }, process.env['BT_DEMO_PASSWORD']!);
  }
  await page.getByRole('checkbox', { name: 'Acepto el aviso de privacidad' }).check();
  const registro = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/auth/registro' && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  const registrada = await registro;
  expect(registrada.status()).toBe(201);
  cuentaRegistrada = (await registrada.json()).id;
  await expect(page).not.toHaveURL(/\/registro(?:\?|$)/);
  await comprobarSalida(page);
  await page.goto('/perfil');
  await expect(page.getByLabel('Correo (solo lectura)', { exact: true })).toHaveValue(correo);
  await page.getByLabel('Nombre', { exact: true }).fill('Cliente E2E actualizado');
  await page.getByRole('button', { name: 'Guardar perfil', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Perfil guardado.' })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Nombre', { exact: true })).toHaveValue('Cliente E2E actualizado');
  const cuenta = page.locator('app-perfil .pagina-cuenta');
  const seguridad = page.getByRole('link', { name: 'Cambiar contraseña', exact: true });
  const parrafoSeguridad = page.getByText(
    'Cambie la contraseña con la que ingresa a BarberTurno.',
    { exact: true },
  );
  await expect(seguridad).toHaveAttribute('mat-stroked-button', '');
  expect((await seguridad.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  expect((await seguridad.boundingBox())!.x).toBe((await parrafoSeguridad.boundingBox())!.x);
  if ((page.viewportSize()?.width ?? 1440) >= 768) {
    const marco = page.locator('main');
    expect((await cuenta.boundingBox())!.x).toBe(
      await marco.evaluate(
        (e) => e.getBoundingClientRect().x + parseFloat(getComputedStyle(e).paddingLeft),
      ),
    );
    expect((await cuenta.boundingBox())!.width).toBeLessThanOrEqual(720);
  }
  await revisar(page, testInfo, 'perfil');
  await seguridad.click();
  await expect(page).toHaveURL(/\/cambiar-password$/);
  const cambio = page.locator('app-cambiar-password .pagina-cuenta');
  expect((await cambio.boundingBox())!.x).toBe(
    await page
      .locator('main')
      .evaluate((e) => e.getBoundingClientRect().x + parseFloat(getComputedStyle(e).paddingLeft)),
  );
  expect((await cambio.boundingBox())!.width).toBeLessThanOrEqual(720);
  await revisar(page, testInfo, 'cambiar-password');
});

async function salirConRespuesta(page: Page) {
  const menu = page.getByRole('button', { name: 'Abrir menú', exact: true });
  if (await menu.isVisible()) await menu.click();
  else {
    const cuenta = page.getByRole('button', { name: 'Abrir cuenta', exact: true });
    if (await cuenta.isVisible()) await cuenta.click();
  }
  const respuesta = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/auth/logout' && r.request().method() === 'POST',
  );
  await controlSalir(page).click();
  expect(
    (await respuesta).status(),
    'Logout conserva la protección CSRF y debe responder 204',
  ).toBe(204);
  await expect(page).toHaveURL(/\/ingresar/);
}

test('08 · Carlos lee solo sus avisos desde la cabecera y el administrador no tiene acceso', async ({
  page,
}, testInfo) => {
  // La atención del caso 03 genera COMPLETAR solo para Ana (RN-15).
  await ingresar(page, 'ana@ejemplo.test');
  await page.goto('/n/barberturno/mis-citas');
  await expect(page.getByRole('button', { name: /^Ver avisos/ })).toHaveCount(0);
  const avisosAna: Pagina<NotificacionDto> = await (
    await page.request.get('/api/n/barberturno/notificaciones?tamano=100')
  ).json();
  const exclusivoCliente = avisosAna.contenido.find(
    (a) => a.tipo === 'COMPLETAR' && a.mensaje.startsWith(`Reserva ${atencion}:`),
  )!;
  expect(exclusivoCliente).toBeDefined();
  if ((page.viewportSize()?.width ?? 1440) > 767) {
    await expect(page.locator('app-avisos-panel')).toContainText(exclusivoCliente.mensaje);
    await expect(page.getByRole('button', { name: /^Avisos: \d+ sin leer$/ })).toHaveCount(0);
    // La cabecera ofrece el diálogo en el resto de pantallas de escritorio.
    await page.goto('/n/barberturno/reservar');
  } else {
    await expect(page.locator('app-mis-citas app-avisos-panel')).toHaveCount(0);
  }
  const campanaCliente = page.getByRole('button', { name: /^Avisos: \d+ sin leer$/ });
  await campanaCliente.click();
  const avisosCliente = page.getByRole('dialog', { name: 'Avisos', exact: true });
  await expect(avisosCliente).toBeVisible();
  await expect(avisosCliente.getByRole('button', { name: 'Cerrar', exact: true })).toBeFocused();
  await expect(avisosCliente).toContainText(exclusivoCliente.mensaje);
  await expect(avisosCliente.locator('app-avisos-panel')).toContainText(exclusivoCliente.mensaje);
  await expect(avisosCliente.locator('section.incrustado')).toHaveCount(1);
  const avisoCliente = avisosCliente.locator('li').filter({ hasText: exclusivoCliente.mensaje });
  const lecturaCliente = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/api/n/barberturno/notificaciones/${exclusivoCliente.id}/lectura`) &&
      r.request().method() === 'POST',
  );
  await avisoCliente.getByRole('button', { name: /^Marcar como leído/ }).click();
  expect((await lecturaCliente).status()).toBe(204);
  await expect(avisoCliente).toContainText('Leído');
  await expect(avisoCliente.getByRole('button', { name: /^Marcar como leído/ })).toHaveCount(0);
  await revisar(page, testInfo, 'avisos-cliente');
  await avisosCliente.getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(avisosCliente).toHaveCount(0);
  await expect(campanaCliente).toBeFocused();
  if ((page.viewportSize()?.width ?? 1440) > 767) await page.goto('/n/barberturno/mis-citas');
  await expect(page).toHaveURL(/\/mis-citas$/);
  await salirConRespuesta(page);

  await ingresar(page, 'cliente@ejemplo.test');
  await expect(page.getByRole('button', { name: /^Ver avisos/ })).toHaveCount(0);
  const servicios: ServicioDto[] = await (
    await page.request.get('/api/n/barberturno/servicios')
  ).json();
  const barberos: BarberoDto[] = await (
    await page.request.get('/api/n/barberturno/barberos')
  ).json();
  expect(servicios.map((s) => s.nombre)).toContain('Corte clásico');
  expect(barberos.map((b) => b.nombre)).toContain('Carlos');
  const creada: ReservaDto = await (
    await escribir(page.request, '/api/n/barberturno/reservas', {
      servicioId: servicios.find((s) => s.nombre === 'Corte clásico')!.id,
      barberoId: barberos.find((b) => b.nombre === 'Carlos')!.id,
      inicio: '2026-10-02T15:00:00-05:00',
    })
  ).json();
  // El autoservicio ya confirma por defecto (RN-21); no requiere otra transición.
  expect(creada.estado).toBe('CONFIRMADA');
  const cliente: Pagina<NotificacionDto> = await (
    await page.request.get('/api/n/barberturno/notificaciones?tamano=100')
  ).json();
  expect(cliente.contenido.some((a) => a.reservaId === creada.id && a.tipo === 'CREAR')).toBe(true);
  await page.goto('/n/barberturno/mis-citas');
  await comprobarAvisoCliente(page, `Reserva ${creada.codigo} creada`);
  await salirConRespuesta(page);

  await ingresar(page, 'carlos@ejemplo.test');
  const propios: Pagina<NotificacionDto> = await (
    await page.request.get('/api/n/barberturno/notificaciones?tamano=100')
  ).json();
  expect(propios.contenido.some((a) => a.reservaId === creada.id && a.tipo === 'CREAR')).toBe(true);
  expect(propios.contenido.some((a) => a.mensaje === exclusivoCliente.mensaje)).toBe(false);
  const idsCliente = new Set([...avisosAna.contenido, ...cliente.contenido].map((a) => a.id));
  expect(propios.contenido.filter((a) => idsCliente.has(a.id))).toEqual([]);
  const { noLeidas } = await (
    await page.request.get('/api/n/barberturno/notificaciones/conteo')
  ).json();
  expect(noLeidas).toBeGreaterThan(0);
  const contador = page.locator('[data-contador-avisos]');
  await expect(contador).toHaveText(`Avisos sin leer: ${noLeidas}`);
  const abrir = page.getByRole('button', { name: `Ver avisos: ${noLeidas} sin leer`, exact: true });
  await abrir.focus();
  await expect(abrir).toBeFocused();
  const listado = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/n/barberturno/notificaciones',
  );
  await abrir.press('Enter');
  const respuestaListado = await listado;
  expect(respuestaListado.status()).toBe(200);
  // La petición del panel no filtra identidades: el servidor selecciona los propios.
  expect([...new URL(respuestaListado.url()).searchParams.keys()].sort()).toEqual([
    'pagina',
    'soloNoLeidas',
    'tamano',
  ]);
  const visibles: Pagina<NotificacionDto> = await respuestaListado.json();
  const dialogo = page.getByRole('dialog', { name: 'Avisos', exact: true });
  await expect(dialogo).toBeVisible();
  await expect(dialogo.getByRole('heading', { name: 'Avisos', exact: true })).toHaveCount(1);
  await expect(dialogo.locator('[aria-busy="true"]')).toHaveCount(0);
  await expect(dialogo.locator('app-avisos-panel li')).toHaveCount(visibles.contenido.length);
  for (const aviso of visibles.contenido) {
    expect(propios.contenido.some((a) => a.id === aviso.id)).toBe(true);
    await expect(dialogo).toContainText(aviso.mensaje);
  }
  await expect(dialogo).not.toContainText(exclusivoCliente.mensaje);
  // El foco inicial llega al terminar la apertura; axe debe leer los colores definitivos.
  await expect(dialogo.getByRole('button', { name: 'Cerrar', exact: true })).toBeFocused();
  const resultados = await new AxeBuilder({ page }).analyze();
  await testInfo.attach('axe-avisos-barbero', {
    body: JSON.stringify(
      resultados.violations.map(({ id, impact, nodes }) => ({
        id,
        impact,
        elementos: nodes.map((n) => n.target),
      })),
      null,
      2,
    ),
    contentType: 'application/json',
  });
  expect(resultados.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await dialogo.evaluate((e) => e.scrollWidth <= e.clientWidth)).toBe(true);
  await testInfo.attach(`avisos-barbero-${testInfo.project.name}`, {
    body: await page.screenshot({
      path: `test-results/avisos-barbero-${testInfo.project.name}.png`,
    }),
    contentType: 'image/png',
  });
  const lectura = page.waitForResponse((r) =>
    r
      .url()
      .endsWith(
        `/api/n/barberturno/notificaciones/${
          propios.contenido.find((a) => a.reservaId === creada.id && a.tipo === 'CREAR')!.id
        }/lectura`,
      ),
  );
  await dialogo
    .getByRole('button', { name: `Marcar como leído el aviso de ${creada.codigo}`, exact: true })
    .click();
  expect((await lectura).status()).toBe(204);
  await expect(contador).toHaveText(`Avisos sin leer: ${noLeidas - 1}`);
  await expect(
    dialogo.locator('li').filter({ hasText: `Reserva ${creada.codigo} creada` }),
  ).toContainText('Leído');
  await dialogo.getByRole('button', { name: 'Cerrar', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(dialogo).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^Ver avisos:/ })).toBeFocused();
  await salirConRespuesta(page);
  await ingresar(page, 'multirol@ejemplo.test');
  await expect(page.getByRole('button', { name: /^Ver avisos/ })).toHaveCount(0);
});

test('09 · salir y volver a entrar sin recargar renueva el token CSRF', async ({ page }) => {
  const primerLogin = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST',
  );
  await ingresar(page, 'cliente@ejemplo.test');
  const primeraRespuesta = await primerLogin;
  expect(primeraRespuesta.status()).toBe(200);
  const primeraHuella = await huellaCsrf(primeraRespuesta);
  await salirConRespuesta(page);
  if ((page.viewportSize()?.width ?? 1440) <= 767) {
    // La navegación debe cerrar el cajón y llevar el foco a la pantalla destino.
    await expect(page.locator('mat-sidenav')).toBeHidden();
  }
  await expect(page.locator('main')).toBeFocused();
  // Desde aquí no hay goto ni reload: el formulario es el que abrió la SPA al salir.
  await page.getByLabel('Correo', { exact: true }).fill('cliente@ejemplo.test');
  await page.getByLabel('Contraseña', { exact: true }).evaluate((elemento, valor) => {
    (elemento as HTMLInputElement).value = valor;
    elemento.dispatchEvent(new Event('input', { bubbles: true }));
  }, process.env['BT_DEMO_PASSWORD']!);
  const segundoLogin = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  const segundaRespuesta = await segundoLogin;
  expect(segundaRespuesta.status()).toBe(200);
  expect(await huellaCsrf(segundaRespuesta)).not.toBe(primeraHuella);
  await expect(page).not.toHaveURL(/\/ingresar(?:\?|$)/);
  await expect(page.locator('main')).toBeFocused();
  await expect
    .poll(
      () =>
        page.evaluate(() => {
          const activo = document.activeElement;
          return (
            !!activo?.isConnected && activo !== document.body && activo !== document.documentElement
          );
        }),
      { message: 'El reingreso sin recargar conserva un destino de foco válido.' },
    )
    .toBe(true);
  await comprobarSalida(page);
});

test('10 · administrador reserva para el cliente elegido y conserva su identidad en los cuatro pasos', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'multirol@ejemplo.test');
  await page.goto('/n/barberturno/reservar');
  await expect(page.locator('mat-step-header')).toHaveCount(4);
  await expect(page.locator('mat-step-header').first()).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Reserva asistida');
  const elegirServicio = page.getByRole('button', { name: 'Elegir servicio', exact: true });
  await expect(elegirServicio).toBeDisabled();
  await page
    .getByLabel('Buscar cliente por nombre o correo', { exact: true })
    .fill('Cliente E2E actualizado');
  const cliente = page.getByRole('radio', { name: /Cliente E2E actualizado/ });
  await cliente.check();
  await expect(cliente).toBeChecked();
  await revisar(page, testInfo, 'asistida-cliente');
  if ((page.viewportSize()?.width ?? 1440) > 767) {
    await expect(
      page.getByRole('complementary', { name: 'Reserva en curso', exact: true }),
    ).toContainText('Cliente E2E actualizado');
  }
  await elegirServicio.click();
  await expect(page.locator('[data-paso="1"]')).toBeFocused();
  await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
  await seleccionar(page, 'Profesional', 'Carlos');
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await fechaFranja(page, '2026-10-03', '11:00');
  await expect(page.locator('.resumen')).toContainText('Cliente E2E actualizado');
  await expect(
    page.getByRole('complementary', { name: 'Reserva en curso', exact: true }),
  ).toHaveCount(0);
  await revisar(page, testInfo, 'asistida-confirmacion');
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/n/barberturno/reservas') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Confirmar reserva', exact: true }).click();
  const creada = await respuesta;
  expect(creada.status()).toBe(201);
  expect(creada.request().postDataJSON().clienteId).toBe(cuentaRegistrada);
  const reserva = await creada.json();
  // El perfil global cambió en 07; la ficha local conserva el nombre de su alta (V2-RN-16).
  expect(reserva.cliente.nombre).toBe('Cliente E2E de registro');
  // ReservaDto conserva el id de cuenta, no expone el id interno de ficha (V2-DA-29).
  expect(reserva.cliente.id).toBe(cuentaRegistrada);
  await expect(page).toHaveURL(/\/agenda/);
  await page.getByLabel('Fecha de Lima', { exact: true }).fill('2026-10-03');
  await page.getByRole('button', { name: 'Actualizar agenda', exact: true }).click();
  await expect(citaAgenda(page, reserva.codigo)).toContainText('Cliente E2E de registro');
});

test('11 · Mis citas conserva filtros al reintentar y presenta vacío e historial sin paginador innecesario', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'cliente@ejemplo.test');
  await page.goto('/n/barberturno/mis-citas');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Mis citas');
  const movil = (page.viewportSize()?.width ?? 1440) <= 767;
  const abrir = page.getByRole('button', { name: 'Filtros por fecha y estado', exact: true });
  if (movil) {
    await expect(page.locator('.titulo-pagina')).toHaveText('Mis citas BarberTurno');
    await expect(abrir).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByLabel('Desde', { exact: true })).toBeHidden();
    await abrir.click();
    await expect(abrir).toHaveAttribute('aria-expanded', 'true');
  }
  await expect(page.getByLabel('Desde', { exact: true })).toBeVisible();
  await seleccionar(page, 'Estado', 'No asistió');
  let urlConsulta = '';
  await page.route('**/api/n/barberturno/reservas/mias?**', async (ruta) => {
    urlConsulta = ruta.request().url();
    await ruta.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        detail: 'No se pudo consultar. Intente nuevamente.',
        codigo: 'RECURSO_OCUPADO',
      }),
    });
    await page.unroute('**/api/n/barberturno/reservas/mias?**');
  });
  await page.getByRole('button', { name: 'Filtrar', exact: true }).click();
  const alerta = page.locator('.alerta');
  await expect(alerta).toContainText('No se pudieron cargar las citas.');
  await expect(page.locator('.vacio')).toHaveCount(0);
  const cerrarAviso = page
    .locator('mat-snack-bar-container')
    .getByRole('button', { name: 'Cerrar', exact: true });
  if (await cerrarAviso.isVisible()) await cerrarAviso.click();
  await revisar(page, testInfo, 'mis-citas-error');
  const consulta = page.waitForResponse((r) =>
    r.url().includes('/api/n/barberturno/reservas/mias?'),
  );
  await alerta.getByRole('button', { name: 'Reintentar', exact: true }).click();
  expect((await consulta).url()).toBe(urlConsulta);
  await expect(
    page.getByRole('heading', { name: 'No tiene citas próximas', exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: 'Reservar un turno', exact: true })).toBeVisible();
  await expect(page.locator('.vacio img')).toHaveAttribute('alt', '');
  await expect(page.locator('.vacio img')).toHaveAttribute('loading', 'lazy');
  await expect(page.locator('mat-paginator[aria-label="Páginas de citas"]')).toHaveCount(0);
  if (movil) {
    await abrir.click();
    await expect(abrir).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByLabel('Desde', { exact: true })).toBeHidden();
  }
  await revisar(page, testInfo, 'mis-citas-vacio');
  const historial = page.getByRole('radio', { name: 'Historial', exact: true });
  await historial.click();
  await expect(historial).toBeChecked();
  await expect(
    page.getByRole('heading', { name: 'No hay citas con estos filtros.', exact: true }),
  ).toBeVisible();
  await expect(page.locator('article.tique')).toHaveCount(0);
});

test('12 · Agenda conserva filtros al reintentar, agrupa días vacíos y cambia a tabla desde 1200 px', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'multirol@ejemplo.test');
  await page.goto('/n/barberturno/agenda');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Agenda');
  await seleccionar(page, 'Barbero', 'Carlos');
  await page.getByLabel('Fecha de Lima', { exact: true }).fill('2026-09-28');
  await page.getByRole('button', { name: 'Actualizar agenda', exact: true }).click();
  await expect(citaAgenda(page, cercana)).toBeVisible();
  const original = page.viewportSize()!;
  for (const width of [768, 1199, 1200]) {
    await page.setViewportSize({ width, height: 900 });
    const cita = page.locator(`app-agenda-cita[aria-label="${cercana}"]`);
    await expect(cita).toHaveAttribute('role', width >= 1200 ? 'row' : 'article');
    await expect(page.getByRole('table')).toHaveCount(width >= 1200 ? 1 : 0);
    const horario = await cita.locator('.intervalo strong').boundingBox();
    const cliente = await cita.locator('.cliente strong').boundingBox();
    expect(horario).not.toBeNull();
    expect(cliente).not.toBeNull();
    expect(horario!.x + horario!.width).toBeLessThan(cliente!.x);
    const telefono = cita.getByRole('link', { name: /^\d{3} \d{3} \d{3}$/ });
    await expect(telefono).toBeVisible();
    const numero = (await telefono.textContent())!.trim();
    await expect(telefono).toHaveAttribute('href', 'tel:' + numero.replaceAll(' ', ''));
    const areaTelefono = await telefono.boundingBox();
    expect(areaTelefono).not.toBeNull();
    expect(areaTelefono!.height).toBeGreaterThanOrEqual(44);
    await revisar(page, testInfo, `agenda-${width}`);
  }
  await page.setViewportSize(original);
  let soltar!: () => void;
  const espera = new Promise<void>((resolver) => {
    soltar = resolver;
  });
  let primera = true;
  let urlFallida = '';
  await page.route('**/api/n/barberturno/reservas?*', async (ruta) => {
    if (primera) {
      primera = false;
      urlFallida = ruta.request().url();
      await espera;
      await ruta.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ detail: 'Agenda temporalmente no disponible.' }),
      });
    } else await ruta.continue();
  });
  const fallida = page.waitForResponse(
    (r) => r.url().includes('/api/n/barberturno/reservas?') && r.status() === 503,
  );
  await page.getByRole('button', { name: 'Actualizar agenda', exact: true }).click();
  await expect(page.locator('app-reserva-esqueleto')).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Cargando agenda…' })).toHaveCount(1);
  soltar();
  await fallida;
  const alerta = page
    .getByRole('alert')
    .filter({ hasText: 'No pudimos completar la solicitud. Intente nuevamente.' });
  await expect(alerta).toBeVisible();
  await page
    .locator('mat-snack-bar-container')
    .getByRole('button', { name: 'Cerrar', exact: true })
    .click();
  await expect(page.locator('mat-snack-bar-container')).toHaveCount(0);
  await revisar(page, testInfo, 'agenda-error');
  const reintento = page.waitForResponse(
    (r) => r.url().includes('/api/n/barberturno/reservas?') && r.status() === 200,
  );
  await alerta.getByRole('button', { name: 'Reintentar', exact: true }).click();
  expect((await reintento).url()).toBe(urlFallida);
  await expect(citaAgenda(page, cercana)).toBeVisible();
  await expect(page.getByLabel('Barbero', { exact: true })).toContainText('Carlos');
  await page.getByRole('radio', { name: 'Semana', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Semana', exact: true })).toBeChecked();
  await expect(page.locator('.dias-vacios').first()).toBeVisible();
  await expect(page.locator('.dias-vacios').first()).toHaveAttribute(
    'aria-label',
    /^Agenda del \d{4}-\d{2}-\d{2} al \d{4}-\d{2}-\d{2}$/,
  );
  await revisar(page, testInfo, 'agenda-semana-admin');
});

test('13 · selector y cambio con respuesta pendiente cancelan el ámbito anterior', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'multirol@ejemplo.test', false);
  await expect(
    page.getByRole('list', { name: 'Barberías', exact: true }).getByRole('listitem'),
  ).toHaveCount(2);
  await capturarNucleo(page, testInfo, 'selector');
  await page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);
  const contexto = await abrirContexto(page);
  await expect(contexto).toContainText('Propietario');
  await capturarNucleo(page, testInfo, page.viewportSize()!.width === 360 ? 'cajon' : 'contexto');
  if (page.viewportSize()!.width === 360) await page.keyboard.press('Escape');
  await seleccionar(page, 'Barbero', 'Carlos');
  let soltar!: () => void;
  const espera = new Promise<void>((resolver) => {
    soltar = resolver;
  });
  let retenida!: () => void;
  const preparada = new Promise<void>((resolver) => {
    retenida = resolver;
  });
  let cancelada = false;
  let terminada!: () => void;
  const entregada = new Promise<void>((resolver) => {
    terminada = resolver;
  });
  await page.route(
    '**/api/n/barberturno/reservas?*',
    async (ruta) => {
      const solicitud = ruta.request();
      page.on('requestfailed', (fallida) => {
        if (fallida === solicitud) cancelada = true;
      });
      const respuesta = await ruta.fetch();
      retenida();
      await espera;
      try {
        await ruta.fulfill({ response: respuesta });
      } catch {
        /* El navegador puede haber abortado la petición al destruir la vista. */
      } finally {
        terminada();
      }
    },
    { times: 1 },
  );
  const posteriores: string[] = [];
  let cambiado = false;
  page.on('request', (r) => {
    if (cambiado && new URL(r.url()).pathname.startsWith('/api/n/'))
      posteriores.push(new URL(r.url()).pathname);
  });
  await page.getByRole('button', { name: 'Actualizar agenda', exact: true }).click();
  try {
    await preparada;
    const bloque = await abrirContexto(page);
    await bloque.getByRole('link', { name: 'Cambiar de barbería', exact: true }).click();
    await expect(page).toHaveURL(/\/negocios$/);
    cambiado = true;
    await page.getByRole('button', { name: 'Entrar en Barbería Sur', exact: true }).click();
    await expect(page).toHaveURL(/\/n\/sur\/agenda$/);
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  } finally {
    soltar();
  }
  await entregada;
  await expect.poll(() => cancelada).toBe(true);
  expect(posteriores.length).toBeGreaterThan(0);
  expect(posteriores.every((ruta) => ruta.startsWith('/api/n/sur/'))).toBe(true);
  await expect(page.locator('main')).not.toContainText('Ana');
  await expect(page.locator('main')).not.toContainText(cercana);
  const nuevo = await abrirContexto(page);
  await expect(nuevo).toContainText('Barbería Sur');
  await expect(nuevo).toContainText('Barbero');
  await expect(nuevo).not.toContainText('Propietario');
});

test('14 · personal y cliente elige papel, reserva como cliente y vuelve al personal', async ({
  page,
}, testInfo) => {
  const peticiones: { ruta: string; papel: string | null; metodo: string }[] = [];
  page.on('request', (r) => {
    if (new URL(r.url()).pathname.startsWith('/api/'))
      peticiones.push({
        ruta: new URL(r.url()).pathname,
        papel: r.headers()['x-bt-papel'] ?? null,
        metodo: r.method(),
      });
  });
  await ingresar(page, 'personal-cliente@ejemplo.test', false);
  const dialogo = page.getByRole('dialog');
  await expect(dialogo).toContainText('¿Cómo quiere entrar en BarberTurno?');
  await capturarNucleo(page, testInfo, 'papel');
  await dialogo.getByRole('radio', { name: /^Como cliente/ }).check();
  await dialogo.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/barberturno\/reservar\?papel=cliente$/);
  await expect(page.locator('mat-step-header')).toHaveCount(3);
  await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
  await seleccionar(page, 'Profesional', 'Carlos');
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await fechaFranja(page, '2026-10-05', '11:00');
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/n/barberturno/reservas') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Confirmar reserva', exact: true }).click();
  const creada = await respuesta;
  expect(creada.status()).toBe(201);
  expect(creada.request().postDataJSON()).not.toHaveProperty('clienteId');
  const reserva = await creada.json();
  expect(reserva.estado).toBe('CONFIRMADA');
  await expect(page).toHaveURL(/\/mis-citas\?papel=cliente$/);
  await expect(
    page.locator('app-reserva-tarjeta').filter({ hasText: reserva.codigo }),
  ).toBeVisible();
  expect(
    peticiones.filter((r) => !r.ruta.startsWith('/api/n/')).every((r) => r.papel === null),
  ).toBe(true);
  const delCliente = peticiones.filter((r) => r.ruta.startsWith('/api/n/'));
  expect(delCliente.length).toBeGreaterThan(0);
  expect(delCliente.every((r) => r.papel === 'CLIENTE')).toBe(true);
  const bloque = await abrirContexto(page);
  await expect(bloque).toContainText('Cliente');
  await bloque.getByRole('button', { name: 'Cambiar papel', exact: true }).click();
  await expect(dialogo).toBeVisible();
  peticiones.length = 0;
  await dialogo.getByRole('radio', { name: /^Como personal/ }).check();
  await dialogo.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  expect(peticiones.filter((r) => r.ruta.startsWith('/api/n/')).length).toBeGreaterThan(0);
  expect(peticiones.every((r) => r.papel === null)).toBe(true);
  await expect(page.locator('main')).not.toContainText(reserva.codigo);
  await expect(await abrirContexto(page)).toContainText('Barbero');
});

test('15 · URL ajena, inexistente y antigua no muestran datos del negocio anterior', async ({
  page,
}) => {
  await ingresar(page, 'personal-cliente@ejemplo.test', false);
  await page.getByRole('dialog').getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);
  const ajena = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/n/sur/sesion');
  await page.goto('/n/sur/agenda');
  expect((await ajena).status()).toBe(200);
  await expect(page.getByText('No tiene acceso a esta sección.', { exact: true })).toBeVisible();
  await expect(page.locator('app-agenda')).toHaveCount(0);
  await page.goto('/n/no-existe/agenda');
  await expect(
    page.getByText('No encontramos esa barbería. Revise la dirección.', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('app-agenda')).toHaveCount(0);
  const deNegocio: string[] = [];
  page.on('request', (r) => {
    if (new URL(r.url()).pathname.startsWith('/api/n/')) deNegocio.push(r.url());
  });
  await page.goto('/agenda');
  // Una sola barbería abre el diálogo de papel desde el selector, sin un ámbito implícito.
  await expect(page).toHaveURL(/\/negocios$/);
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(deNegocio).toEqual([]);
});

test('16 · logout y reingreso vacían el negocio y el papel anterior', async ({ page }) => {
  await ingresar(page, 'multirol@ejemplo.test', false);
  await page.getByRole('button', { name: 'Entrar en Barbería Sur', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/sur\/agenda$/);
  await salirConRespuesta(page);
  await expect(page.locator('app-barberia-actual section')).toHaveCount(0);
  const rutas: string[] = [];
  page.on('request', (r) => {
    const ruta = new URL(r.url()).pathname;
    if (ruta.startsWith('/api/n/')) rutas.push(ruta);
  });
  await ingresar(page, 'cliente@ejemplo.test', false);
  await expect(page).toHaveURL(/\/negocios$/);
  await expect(
    page.getByRole('list', { name: 'Barberías', exact: true }).getByRole('listitem'),
  ).toHaveCount(2);
  await expect(page.locator('app-barberia-actual section')).toHaveCount(0);
  expect(rutas).toEqual([]);
  await page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/barberturno\/reservar\?papel=cliente$/);
  await expect(page.locator('mat-step-header')).toHaveCount(3);
  await expect(page.getByRole('radio', { name: 'Corte clásico', exact: true })).not.toBeChecked();
  expect(rutas.length).toBeGreaterThan(0);
  expect(rutas.every((r) => r.startsWith('/api/n/barberturno/'))).toBe(true);
  await salirConRespuesta(page);
});

async function capturarGestion(page: Page, info: TestInfo, nombre: string, subtarea = 'v2-07-2') {
  await page.bringToFront();
  await expect(page.locator('main')).toBeVisible();
  await expect(
    page.locator(
      'main > app-negocio-vista, main > app-aceptacion, main > app-negocios, main > app-plataforma',
    ),
  ).toBeVisible();
  await expect(page.locator('main app-estado-contenido .esqueletos')).toHaveCount(0);
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  const dialogo = page.getByRole('dialog');
  if (await dialogo.isVisible()) {
    await dialogo.locator('input, button, a[href]').filter({ visible: true }).first().focus();
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => !!document.activeElement?.closest('[role="dialog"]'))).toBe(
      true,
    );
  } else {
    await page.locator('main').focus();
    await page.keyboard.press('Tab');
  }
  expect(await page.evaluate(() => document.activeElement?.matches(':focus-visible'))).toBe(true);
  await page.evaluate(
    () =>
      new Promise<void>((resolver) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolver())),
      ),
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({
    path: `../docs/pruebas/v2-07/capturas/${subtarea}/${info.project.name}-${nombre}.png`,
    fullPage: true,
    animations: 'disabled',
  });
}

test('17 · equipo e invitaciones: otra cuenta, aceptación, usada, caducada y revocada', async ({
  page,
}, info) => {
  await ingresar(page, 'encargado@ejemplo.test', false);
  await page.goto('/n/sur/equipo');
  await expect(page.locator('main')).toContainText('Usted');
  await capturarGestion(page, info, 'equipo');
  // El servidor conserva la protección del último propietario y el diálogo muestra su detalle.
  await page.getByRole('button', { name: 'Salir de la barbería', exact: true }).click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Salir de la barbería', exact: true })
    .click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText(/propietario/i);
  await page.getByRole('dialog').getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await test.step(
    'Cerrar la sesión del propietario de Sur',
    async () => {
      await salirConRespuesta(page);
    },
    { timeout: 20_000 },
  );
  await ingresar(page, 'multirol@ejemplo.test');
  await page.goto('/n/barberturno/equipo');
  await capturarGestion(page, info, 'equipo-inicial');
  await page.goto('/n/barberturno/invitaciones');
  await page.getByLabel('Correo', { exact: true }).fill('cliente@ejemplo.test');
  await seleccionar(page, 'Rol', 'Barbero');
  const creada = page.waitForResponse(
    (r) => r.url().endsWith('/api/n/barberturno/invitaciones') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Crear invitación', exact: true }).click();
  expect((await creada).status()).toBe(201);
  const emitida = await (await creada).json();
  await expect(page.locator('app-enlace-invitacion')).toContainText('se muestra una sola vez');
  await capturarGestion(page, info, 'invitaciones-enlace');
  await page.reload();
  await expect(page.locator('app-invitaciones')).toBeVisible();
  await expect(page.locator('app-enlace-invitacion')).toHaveCount(0);
  await page.goto(emitida.enlaceInvitacion.replace('/api/invitaciones/', '/invitacion/'));
  const ajena = page.waitForResponse((r) => r.url().endsWith('/aceptacion'));
  await page.getByRole('button', { name: 'Aceptar invitación', exact: true }).click();
  expect((await ajena).status()).toBe(403);
  await expect(page.locator('main')).toContainText('Esta invitación es para otra cuenta.');
  await salirConRespuesta(page);
  await page.goto(emitida.enlaceInvitacion.replace('/api/invitaciones/', '/invitacion/'));
  await expect(page.locator('main')).toContainText('Ingrese o cree una cuenta');
  await capturarGestion(page, info, 'aceptacion-sin-sesion');
  await page.getByRole('link', { name: 'Ingresar', exact: true }).click();
  await page.getByLabel('Correo', { exact: true }).fill('cliente@ejemplo.test');
  await page.getByLabel('Contraseña', { exact: true }).evaluate((el, valor) => {
    (el as HTMLInputElement).value = valor;
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }, process.env['BT_DEMO_PASSWORD']!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.locator('app-aceptacion')).toBeVisible();
  await capturarGestion(page, info, 'aceptacion-con-sesion');
  await page.getByRole('button', { name: 'Aceptar invitación', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);
  await page.goto(emitida.enlaceInvitacion.replace('/api/invitaciones/', '/invitacion/'));
  await expect(page.locator('main')).toContainText('Esta invitación ya se usó.');
  await capturarGestion(page, info, 'invitacion-usada');
  const gestor = await sesionApi('multirol@ejemplo.test');
  try {
    const vencida = await (
      await escribir(gestor, '/api/n/barberturno/invitaciones', {
        correo: 'caducada@ejemplo.test',
        rol: 'BARBERO',
      })
    ).json();
    // Excepción del encargo: adelantar la caducidad con el rol de aplicación y un id de esta prueba.
    expect(Number.isSafeInteger(vencida.invitacion.id)).toBe(true);
    const ajuste = sql(
      `UPDATE invitacion SET expira_en = '2026-09-27T09:00:00-05:00' WHERE id = ${vencida.invitacion.id} AND estado = 'PENDIENTE' RETURNING id`,
    );
    expect(ajuste.status).toBe(0);
    expect(ajuste.stdout).toContain(String(vencida.invitacion.id));
    await page.goto(vencida.enlaceInvitacion.replace('/api/invitaciones/', '/invitacion/'));
    await expect(page.locator('main')).toContainText('Esta invitación caducó.');
    await capturarGestion(page, info, 'invitacion-caducada');
    const revocada = await (
      await escribir(gestor, '/api/n/barberturno/invitaciones', {
        correo: 'revocada@ejemplo.test',
        rol: 'BARBERO',
      })
    ).json();
    const cookies = (await gestor.storageState()).cookies;
    expect(
      (
        await gestor.delete(`/api/n/barberturno/invitaciones/${revocada.invitacion.id}`, {
          headers: {
            'X-XSRF-TOKEN': decodeURIComponent(cookies.find((c) => c.name === 'XSRF-TOKEN')!.value),
          },
        })
      ).status(),
    ).toBe(200);
    await page.goto(revocada.enlaceInvitacion.replace('/api/invitaciones/', '/invitacion/'));
    await expect(page.locator('main')).toContainText('Esta invitación ya no es válida.');
    await capturarGestion(page, info, 'invitacion-revocada');
  } finally {
    await gestor.dispose();
  }
});

test('18 · revocación durante la sesión vuelve al selector aunque conserva ficha cliente', async ({
  page,
}, info) => {
  await ingresar(page, 'cliente@ejemplo.test', false);
  await page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);
  const gestor = await sesionApi('multirol@ejemplo.test');
  try {
    const equipo = await (await gestor.get('/api/n/barberturno/equipo')).json();
    const miembro = equipo.find((f: { correo: string }) => f.correo === 'cliente@ejemplo.test');
    expect(miembro).toBeDefined();
    await escribir(gestor, `/api/n/barberturno/equipo/${miembro.id}/revocacion`, null);
    await page.getByRole('button', { name: 'Actualizar agenda', exact: true }).click();
    await expect(page).toHaveURL(/\/negocios$/);
    await expect(page.locator('main')).toContainText('Ya no tiene acceso a BarberTurno.');
    await capturarGestion(page, info, 'acceso-revocado');
    await page.goto('/n/barberturno/equipo');
    await expect(page.locator('main')).toContainText('No tiene acceso a esta sección.');
  } finally {
    await gestor.dispose();
  }
});

test('19 · configuración real aplica el horizonte en la siguiente petición del cliente', async ({
  page,
}, info) => {
  await ingresar(page, 'multirol@ejemplo.test');
  await page.goto('/n/barberturno/configuracion');
  await expect(page.getByLabel('Horizonte en días', { exact: true })).toHaveValue('30');
  await expect(page.locator('main')).toContainText('Límite técnico: Entre 1 y 32767.');
  await page.getByLabel('Horizonte en días', { exact: true }).fill('12');
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/configuracion') && r.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Guardar configuración', exact: true }).click();
  expect((await respuesta).status()).toBe(200);
  await expect(page.locator('main')).toContainText('Configuración guardada.');
  await capturarGestion(page, info, 'configuracion');
  await salirConRespuesta(page);
  await ingresar(page, 'cliente@ejemplo.test');
  await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await expect(page.getByLabel('Otra fecha, hasta 12 días', { exact: true })).toBeVisible();
  await page.getByLabel('Otra fecha, hasta 12 días', { exact: true }).fill('11/10/2026');
  await page.getByLabel('Otra fecha, hasta 12 días', { exact: true }).press('Tab');
  await expect(page.locator('main')).toContainText(
    'Elija una fecha válida dentro de los próximos 12 días.',
  );
  const gestor = await sesionApi('multirol@ejemplo.test');
  try {
    const datos = await (await gestor.get('/api/n/barberturno/configuracion')).json();
    const cookies = (await gestor.storageState()).cookies;
    const guardado = await gestor.put('/api/n/barberturno/configuracion', {
      data: { ...datos, horizonteDias: 30 },
      headers: {
        'X-XSRF-TOKEN': decodeURIComponent(cookies.find((c) => c.name === 'XSRF-TOKEN')!.value),
      },
    });
    expect(guardado.status()).toBe(200);
  } finally {
    await gestor.dispose();
  }
});

test('20 · plataforma exige rol global, crea barbería y gestiona su invitación inicial una vez', async ({
  page,
}, info) => {
  for (const correo of ['multirol@ejemplo.test', 'encargado@ejemplo.test']) {
    await ingresar(page, correo, false);
    await page.goto('/plataforma');
    await expect(page.locator('main')).toContainText('No tiene acceso a esta sección.');
    await expect(page.locator('app-plataforma')).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: 'Plataforma', exact: true })).toHaveCount(0);
    // Login permite cambiar de identidad sin conservar la autoridad de la anterior.
    await salirConRespuesta(page);
  }
  await ingresar(page, 'plataforma@ejemplo.test', false);
  await expect(page.locator('main')).toContainText('Todavía no pertenece a ninguna barbería.');
  await page.locator('main').getByRole('link', { name: 'Plataforma', exact: true }).click();
  await expect(page).toHaveURL(/\/plataforma\/negocios$/);
  await expect(page.locator('main')).toContainText('Barberías de la plataforma');
  await expect(page.locator('app-barberia-actual section')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Agenda', exact: true })).toHaveCount(0);
  await capturarGestion(page, info, 'plataforma', 'v2-07-3');
  await page.getByRole('button', { name: 'Nueva barbería', exact: true }).click();
  const dialogo = page.getByRole('dialog');
  await dialogo.getByLabel('Nombre', { exact: true }).fill('Barbería Prueba');
  await dialogo.getByLabel('Dirección web', { exact: true }).fill('prueba-plataforma');
  await dialogo
    .getByLabel('Correo del propietario', { exact: true })
    .fill('propietario-inicial@ejemplo.test');
  await capturarGestion(page, info, 'alta', 'v2-07-3');
  const alta = page.waitForResponse(
    (r) => r.url().endsWith('/api/plataforma/negocios') && r.request().method() === 'POST',
  );
  await dialogo.getByRole('button', { name: 'Crear barbería', exact: true }).click();
  const respuesta = await alta;
  expect(respuesta.status()).toBe(201);
  const creada = await respuesta.json();
  await expect(dialogo).toHaveCount(0);
  await expect(page.locator('app-enlace-invitacion')).toContainText('una sola vez');
  await expect(page.locator('app-enlace-invitacion')).toContainText('/invitacion/');
  await capturarGestion(page, info, 'alta-enlace', 'v2-07-3');
  expect((await (await page.request.get(creada.enlaceInvitacion)).json()).estado).toBe('PENDIENTE');
  await page.reload();
  await expect(page.locator('app-plataforma')).toBeVisible();
  await expect(page.locator('app-enlace-invitacion')).toHaveCount(0);
  await page
    .getByRole('button', { name: 'Reenviar invitación de Barbería Prueba', exact: true })
    .click();
  const reenviada = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/api/plataforma/negocios/${creada.id}/invitacion-propietario`) &&
      r.request().method() === 'POST',
  );
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Reenviar invitación', exact: true })
    .click();
  const nueva = await (await reenviada).json();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect((await (await page.request.get(creada.enlaceInvitacion)).json()).estado).toBe('REVOCADA');
  await expect(page.locator('app-enlace-invitacion')).toContainText('hora de Lima');
  await capturarGestion(page, info, 'invitacion-reenviada', 'v2-07-3');
  await page
    .getByRole('button', { name: 'Revocar invitación de Barbería Prueba', exact: true })
    .click();
  const revocada = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/api/plataforma/negocios/${creada.id}/invitacion-propietario`) &&
      r.request().method() === 'DELETE',
  );
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Revocar invitación', exact: true })
    .click();
  expect((await revocada).status()).toBe(200);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect((await (await page.request.get(nueva.enlaceInvitacion)).json()).estado).toBe('REVOCADA');
  await page.locator('main').getByRole('link', { name: 'Ir a mis barberías', exact: true }).click();
  await expect(page).toHaveURL(/\/negocios$/);
  await expect(page.locator('main')).toContainText('Todavía no pertenece a ninguna barbería.');
});

test('21 · suspensión desde plataforma: público no disponible, personal consulta y no escribe; reactivación', async ({
  page,
  browser,
}, info) => {
  const opciones = {
    baseURL: 'http://localhost:8081',
    viewport: page.viewportSize()!,
    timezoneId: 'Europe/Madrid',
    locale: 'es-PE',
  };
  const contextoPersonal = await browser.newContext(opciones);
  const contextoPublico = await browser.newContext(opciones);
  try {
    const personal = await contextoPersonal.newPage();
    const publico = await contextoPublico.newPage();
    await personal.clock.setFixedTime(new Date('2026-09-28T09:00:00-05:00'));
    await publico.clock.setFixedTime(new Date('2026-09-28T09:00:00-05:00'));
    await ingresar(personal, 'multirol@ejemplo.test');
    await personal.goto('/n/barberturno/configuracion');
    await expect(personal.getByLabel('Nombre', { exact: true })).toHaveValue('BarberTurno');
    await ingresar(page, 'plataforma@ejemplo.test', false);
    await page.goto('/plataforma/negocios');
    await page.getByRole('button', { name: 'Suspender BarberTurno', exact: true }).click();
    const suspension = page.waitForResponse(
      (r) => r.url().endsWith('/estado') && r.request().method() === 'PATCH',
    );
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Suspender barbería', exact: true })
      .click();
    expect((await suspension).status()).toBe(200);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(
      page.getByRole('button', { name: 'Reactivar BarberTurno', exact: true }),
    ).toBeVisible();
    await capturarGestion(page, info, 'suspendida-plataforma', 'v2-07-3');
    await personal.reload();
    await expect(personal.locator('main')).toContainText(
      'Barbería suspendida: puede consultar, pero no hacer cambios.',
    );
    await expect(personal.getByLabel('Nombre', { exact: true })).toHaveValue('BarberTurno');
    const escritura = personal.waitForResponse(
      (r) => r.url().endsWith('/configuracion') && r.request().method() === 'PUT',
    );
    await personal.getByRole('button', { name: 'Guardar configuración', exact: true }).click();
    const rechazada = await escritura;
    expect(rechazada.status()).toBe(409);
    expect((await rechazada.json()).codigo).toBe('NEGOCIO_SUSPENDIDO');
    await expect(personal.locator('main [role="alert"]')).toContainText(/suspendid/i);
    await capturarGestion(personal, info, 'suspendida-personal', 'v2-07-3');
    const publica = publico.waitForResponse((r) => r.url().endsWith('/api/n/barberturno/negocio'));
    await publico.goto('/n/barberturno/reservar');
    expect((await publica).status()).toBe(404);
    await expect(publico.locator('main')).toContainText(
      'Esta barbería no está disponible por ahora.',
    );
    await expect(publico.locator('app-reservar')).toHaveCount(0);
    await capturarGestion(publico, info, 'suspendida-publica', 'v2-07-3');
    await page.getByRole('button', { name: 'Reactivar BarberTurno', exact: true }).click();
    const reactivacion = page.waitForResponse(
      (r) => r.url().endsWith('/estado') && r.request().method() === 'PATCH',
    );
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Reactivar barbería', exact: true })
      .click();
    expect((await reactivacion).status()).toBe(200);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await publico.reload();
    await expect(publico.getByRole('radio', { name: 'Corte clásico', exact: true })).toBeVisible();
    await personal.reload();
    await expect(personal.getByLabel('Nombre', { exact: true })).toHaveValue('BarberTurno');
    await expect(personal.locator('main')).not.toContainText('Barbería suspendida:');
  } finally {
    await contextoPersonal.close();
    await contextoPublico.close();
  }
});
