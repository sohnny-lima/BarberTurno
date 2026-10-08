import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { type Page, type Response, type TestInfo } from '@playwright/test';
import { test, expect, escribir, sesionApi } from './fixture';
import type { BarberoDto, ServicioDto } from '../src/app/core/modelos/catalogo';
import type { NotificacionDto } from '../src/app/core/modelos/notificaciones';
import type { Pagina } from '../src/app/core/modelos/pagina';
import type { ReservaDto } from '../src/app/core/modelos/reservas';

let atencion: string;
let cercana: string;
let pendiente: string;

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
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
    nombre,
  ).toBe(true);
}

async function ingresar(page: Page, correo: string) {
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
  await comprobarSalida(page);
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
  const entrada = page.getByLabel('Otra fecha (Lima)', { exact: true });
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
  await page.goto('/reservar');
  await page.getByRole('button', { name: /Corte clásico/ }).click();
  await seleccionar(page, 'Profesional', 'Carlos');
  await page.getByRole('button', { name: 'Elegir fecha y hora', exact: true }).click();
  await fechaFranja(page, fecha, hora);
}

test.beforeAll(async ({ servidor }) => {
  expect(servidor).toContain('18034');
  const api = await sesionApi('admin-e2e@ejemplo.test');
  try {
    const servicios = await (await api.get('/api/servicios')).json();
    const barberos = await (await api.get('/api/barberos')).json();
    const clientes = await (await api.get('/api/usuarios?rol=CLIENTE&tamano=100')).json();
    const carlos = barberos.find((b: { nombre: string }) => b.nombre === 'Carlos');
    const barba = servicios.find((s: { nombre: string }) => s.nombre === 'Barba');
    const ana = clientes.contenido.find((u: { correo: string }) => u.correo === 'ana@ejemplo.test');
    const reservas = await (
      await api.get('/api/reservas?desde=2026-09-28&hasta=2026-10-01')
    ).json();
    cercana = reservas.contenido.find((r: { inicio: string }) =>
      r.inicio.startsWith('2026-09-28T10:00'),
    ).codigo;
    pendiente = reservas.contenido.find((r: { estado: string }) => r.estado === 'PENDIENTE').codigo;
    atencion = (
      await (
        await escribir(api, '/api/reservas', {
          clienteId: ana.id,
          barberoId: carlos.id,
          servicioId: barba.id,
          inicio: '2026-09-28T09:10:00-05:00',
        })
      ).json()
    ).codigo;
    // K1 ya ocupa 16–17 en la demo. Retirarlo por API permite ensayar el alta del guion.
    const bloqueos = await (
      await api.get(`/api/barberos/${carlos.id}/bloqueos?desde=2026-10-01&hasta=2026-10-01`)
    ).json();
    const estado = await api.storageState();
    const token = estado.cookies.find((c) => c.name === 'XSRF-TOKEN')!.value;
    for (const bloqueo of bloqueos) {
      expect(
        (
          await api.delete(`/api/bloqueos/${bloqueo.id}`, {
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
  await page.goto('/reservar');
  await expect(page.locator('mat-step-header')).toHaveCount(3);
  await page.getByRole('button', { name: /Corte clásico/ }).click();
  await seleccionar(page, 'Profesional', 'Carlos');
  await revisar(page, testInfo, 'reservar-servicio');
  const avanzar = page.getByRole('button', { name: 'Elegir fecha y hora' });
  await avanzar.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('[data-paso="1"]')).toBeFocused();
  const entrada = page.getByLabel('Otra fecha (Lima)', { exact: true });
  await entrada.fill('10/01/2026');
  await entrada.press('Tab');
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
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/reservas') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Confirmar reserva', exact: true }).click();
  const creada = await (await respuesta).json();
  await expect(page).toHaveURL(/\/mis-citas/);
  expect(Date.now() - inicio).toBeLessThan(180_000);
  await testInfo.attach('usabilidad-automatizada', {
    body: JSON.stringify({ pasos: 3, limite: 5, duracionMs: Date.now() - inicio }),
    contentType: 'application/json',
  });
  let tarjeta = page.locator('app-reserva-tarjeta').filter({ hasText: creada.codigo });
  await expect(tarjeta).toContainText('11:00');
  await expect(page.locator('app-avisos-panel')).toContainText(creada.codigo);
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
    r.url().endsWith(`/api/reservas/${creada.id}/cancelacion`),
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
  await expect(page.locator('app-avisos-panel')).toContainText(/cancelad/i);
});

test('02 · BT-104 conserva las horas de Lima y oculta las acciones a menos de dos horas', async ({
  page,
}) => {
  await ingresar(page, 'cliente@ejemplo.test');
  await page.goto('/mis-citas');
  const tarjeta = page.locator('app-reserva-tarjeta').filter({ hasText: cercana });
  await expect(tarjeta).toContainText('10:00');
  await expect(tarjeta).toContainText('Faltan menos de 2 horas');
  await expect(tarjeta.getByRole('link', { name: 'Reprogramar' })).toHaveCount(0);
  await expect(tarjeta.getByRole('button', { name: 'Cancelar' })).toHaveCount(0);
});

test('03 · Carlos inicia y completa la atención y confirma BT-100 en Semana', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'carlos@ejemplo.test');
  await page.goto('/agenda');
  await page.getByLabel('Fecha de Lima', { exact: true }).fill('2026-09-28');
  await page.getByRole('button', { name: 'Actualizar agenda' }).click();
  const fila = page.getByRole('article', { name: atencion, exact: true });
  await expect(fila).toContainText('09:10');
  await revisar(page, testInfo, 'agenda-dia');
  for (const accion of ['Iniciar atención', 'Completar']) {
    await fila.getByRole('button', { name: accion, exact: true }).click();
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
  }
  await expect(fila).toContainText('Completada');
  await page.getByRole('radio', { name: 'Semana', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Semana', exact: true })).toBeChecked();
  const solicitud = page.getByRole('article', { name: pendiente, exact: true });
  await expect(solicitud).toContainText('Pendiente');
  await solicitud.getByRole('button', { name: 'Confirmar', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar', exact: true }).click();
  await expect(solicitud).toContainText('Confirmada');
  await revisar(page, testInfo, 'agenda-semana');
});

test('04 · administrador edita servicio y jornada, prueba bloqueos y concilia reportes', async ({
  page,
}, testInfo) => {
  await ingresar(page, 'admin-e2e@ejemplo.test');
  await page.goto('/admin/servicios');
  await expect(
    page.getByRole('button', { name: 'Editar servicio Corte clásico', exact: true }),
  ).toBeVisible();
  await revisar(page, testInfo, 'servicios');
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
  await expect(page.getByRole('table')).toContainText('Corte y acabado de demostración E2E');
  await page.goto('/admin/horarios');
  await seleccionar(page, 'Barbero', 'Carlos');
  await expect(page.getByLabel('Lunes · Fin 2', { exact: true })).toBeVisible();
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
  await page.goto('/admin/reportes');
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
  await expect(page.locator('tbody tr')).toHaveCount(7);
  await revisar(page, testInfo, 'reportes');
});

test('05 · cliente redirigido de administración y protegido después de logout', async ({
  page,
}) => {
  await ingresar(page, 'cliente@ejemplo.test');
  await page.goto('/admin/servicios');
  await expect(page).toHaveURL(/\/reservar/);
  await salirConRespuesta(page);
  await page.goto('/mis-citas');
  await expect(page).toHaveURL(/\/ingresar/);
});

test('06 · dos contextos disputan la misma franja y el perdedor recarga la disponibilidad', async ({
  browser,
}, testInfo) => {
  const contextos = await Promise.all(
    ['ana', 'luis'].map(() =>
      browser.newContext({
        baseURL: 'http://localhost:18034',
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
        (r) => r.url().endsWith('/api/reservas') && r.request().method() === 'POST',
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
      (r) => r.url().endsWith('/api/reservas') && r.request().method() === 'POST',
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
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page).not.toHaveURL(/\/registro(?:\?|$)/);
  await comprobarSalida(page);
  await page.goto('/perfil');
  await expect(page.getByLabel('Correo (solo lectura)', { exact: true })).toHaveValue(correo);
  await page.getByLabel('Nombre', { exact: true }).fill('Cliente E2E actualizado');
  await page.getByRole('button', { name: 'Guardar perfil', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Perfil guardado.' })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Nombre', { exact: true })).toHaveValue('Cliente E2E actualizado');
  await revisar(page, testInfo, 'perfil');
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
  await page.goto('/mis-citas');
  await expect(page.getByRole('button', { name: /^Ver avisos/ })).toHaveCount(0);
  const avisosAna: Pagina<NotificacionDto> = await (
    await page.request.get('/api/notificaciones?tamano=100')
  ).json();
  const exclusivoCliente = avisosAna.contenido.find(
    (a) => a.tipo === 'COMPLETAR' && a.mensaje.startsWith(`Reserva ${atencion}:`),
  )!;
  expect(exclusivoCliente).toBeDefined();
  await expect(page.locator('app-avisos-panel')).toContainText(exclusivoCliente.mensaje);
  const campanaCliente = page.getByRole('button', { name: /^Avisos: \d+ sin leer$/ });
  await campanaCliente.click();
  const avisosCliente = page.getByRole('dialog', { name: 'Avisos', exact: true });
  await expect(avisosCliente).toBeVisible();
  await expect(avisosCliente.getByRole('button', { name: 'Cerrar', exact: true })).toBeFocused();
  await expect(avisosCliente).toContainText(exclusivoCliente.mensaje);
  await expect(avisosCliente.locator('section.incrustado')).toHaveCount(1);
  await avisosCliente.getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(avisosCliente).toHaveCount(0);
  await expect(campanaCliente).toBeFocused();
  await expect(page).toHaveURL(/\/mis-citas$/);
  await salirConRespuesta(page);

  await ingresar(page, 'cliente@ejemplo.test');
  await expect(page.getByRole('button', { name: /^Ver avisos/ })).toHaveCount(0);
  const servicios: ServicioDto[] = await (await page.request.get('/api/servicios')).json();
  const barberos: BarberoDto[] = await (await page.request.get('/api/barberos')).json();
  expect(servicios.map((s) => s.nombre)).toContain('Corte clásico');
  expect(barberos.map((b) => b.nombre)).toContain('Carlos');
  const creada: ReservaDto = await (
    await escribir(page.request, '/api/reservas', {
      servicioId: servicios.find((s) => s.nombre === 'Corte clásico')!.id,
      barberoId: barberos.find((b) => b.nombre === 'Carlos')!.id,
      inicio: '2026-10-02T15:00:00-05:00',
    })
  ).json();
  // El autoservicio ya confirma por defecto (RN-21); no requiere otra transición.
  expect(creada.estado).toBe('CONFIRMADA');
  const cliente: Pagina<NotificacionDto> = await (
    await page.request.get('/api/notificaciones?tamano=100')
  ).json();
  expect(cliente.contenido.some((a) => a.reservaId === creada.id && a.tipo === 'CREAR')).toBe(true);
  await page.goto('/mis-citas');
  await expect(page.locator('app-avisos-panel')).toContainText(`Reserva ${creada.codigo} creada`);
  await salirConRespuesta(page);

  await ingresar(page, 'carlos@ejemplo.test');
  const propios: Pagina<NotificacionDto> = await (
    await page.request.get('/api/notificaciones?tamano=100')
  ).json();
  expect(propios.contenido.some((a) => a.reservaId === creada.id && a.tipo === 'CREAR')).toBe(true);
  expect(propios.contenido.some((a) => a.mensaje === exclusivoCliente.mensaje)).toBe(false);
  const idsCliente = new Set([...avisosAna.contenido, ...cliente.contenido].map((a) => a.id));
  expect(propios.contenido.filter((a) => idsCliente.has(a.id))).toEqual([]);
  const { noLeidas } = await (await page.request.get('/api/notificaciones/conteo')).json();
  expect(noLeidas).toBeGreaterThan(0);
  const contador = page.locator('[data-contador-avisos]');
  await expect(contador).toHaveText(`Avisos sin leer: ${noLeidas}`);
  const abrir = page.getByRole('button', { name: `Ver avisos: ${noLeidas} sin leer`, exact: true });
  await abrir.focus();
  await expect(abrir).toBeFocused();
  const listado = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/notificaciones');
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
        `/api/notificaciones/${
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
  await ingresar(page, 'admin-e2e@ejemplo.test');
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
    await expect(page.locator('main')).toBeFocused();
  }
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
  await comprobarSalida(page);
});
