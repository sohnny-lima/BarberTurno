import { expect, test, errors, type Request, type Response } from '@playwright/test';
import { abrirContexto, cerrarContexto, cerrarSesion, iniciarSesion } from './ayudantes';

test.describe.configure({ mode: 'serial' });

test.describe('Aceptación Demo v2 en Azure', () => {
  test('01 · Superficie pública sin sesión: salud, seguridad, OpenAPI desactivado y manifiestos PWA/TWA', async ({
    request,
  }) => {
    // 1. GET /actuator/health -> 200 y status: UP
    const salud = await request.get('/actuator/health');
    expect(salud.status(), 'GET /actuator/health debe responder 200').toBe(200);
    const cuerpoSalud = await salud.json();
    expect(cuerpoSalud.status, 'Estado de salud debe ser UP').toBe('UP');

    // 2. GET / con cabeceras de seguridad: HSTS, CSP, X-Frame-Options: DENY, X-Content-Type-Options: nosniff
    const raiz = await request.get('/');
    expect(raiz.status(), 'GET / debe responder 200').toBe(200);
    const cabeceras = raiz.headers();
    expect(
      cabeceras['strict-transport-security'],
      'Strict-Transport-Security (HSTS) debe estar presente',
    ).toBeDefined();
    expect(
      cabeceras['content-security-policy'],
      'Content-Security-Policy (CSP) debe estar presente',
    ).toBeDefined();
    expect(cabeceras['x-frame-options']?.toUpperCase(), 'X-Frame-Options debe ser DENY').toBe(
      'DENY',
    );
    expect(
      cabeceras['x-content-type-options']?.toLowerCase(),
      'X-Content-Type-Options debe ser nosniff',
    ).toBe('nosniff');

    // 3. /v3/api-docs, /swagger-ui.html, /actuator/env, /actuator/info, /actuator/metrics no devuelven 200 con contenido
    const rutasOcultas = [
      '/v3/api-docs',
      '/swagger-ui.html',
      '/actuator/env',
      '/actuator/info',
      '/actuator/metrics',
    ];
    for (const ruta of rutasOcultas) {
      const resp = await request.get(ruta);
      expect(
        [401, 403, 404].includes(resp.status()) || resp.status() !== 200,
        `Ruta no pública ${ruta} no debe devolver 200 con contenido (obtenido ${resp.status()})`,
      ).toBe(true);
    }

    // 4. /manifest.webmanifest -> 200 con Content-Type application/manifest+json
    const manifestResp = await request.get('/manifest.webmanifest');
    expect(manifestResp.status(), '/manifest.webmanifest debe responder 200').toBe(200);
    const ctManifest = manifestResp.headers()['content-type'] ?? '';
    expect(
      ctManifest.startsWith('application/manifest+json'),
      `Content-Type de /manifest.webmanifest debe empezar por application/manifest+json (obtenido "${ctManifest}")`,
    ).toBe(true);

    // 5. /.well-known/assetlinks.json -> 200 JSON con relation, namespace, package_name y huella SHA-256
    const assetlinksResp = await request.get('/.well-known/assetlinks.json');
    expect(assetlinksResp.status(), '/.well-known/assetlinks.json debe responder 200').toBe(200);
    const assetlinks = await assetlinksResp.json();
    expect(Array.isArray(assetlinks), 'assetlinks.json debe ser un array JSON').toBe(true);
    expect(assetlinks.length).toBeGreaterThan(0);
    const declaracion = assetlinks[0];
    expect(declaracion.relation).toContain('delegate_permission/common.handle_all_urls');
    expect(declaracion.target.namespace).toBe('android_app');
    expect(typeof declaracion.target.package_name).toBe('string');
    expect(declaracion.target.package_name.length).toBeGreaterThan(0);
    expect(Array.isArray(declaracion.target.sha256_cert_fingerprints)).toBe(true);
    expect(declaracion.target.sha256_cert_fingerprints.length).toBeGreaterThan(0);
    const patronHuella = /^([0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}$/;
    expect(
      declaracion.target.sha256_cert_fingerprints.some((h: string) => patronHuella.test(h)),
      'assetlinks debe contener al menos una huella SHA-256 de 32 pares hex',
    ).toBe(true);

    // 6. /ngsw.json sin dataGroups con elementos y sin ninguna URL que empiece por /api
    const ngswResp = await request.get('/ngsw.json');
    expect(ngswResp.status(), '/ngsw.json debe responder 200').toBe(200);
    const ngsw = await ngswResp.json();
    expect(ngsw.dataGroups ?? []).toHaveLength(0);
    if (Array.isArray(ngsw.assetGroups)) {
      for (const grupo of ngsw.assetGroups) {
        if (Array.isArray(grupo.urls)) {
          for (const url of grupo.urls) {
            expect(url.startsWith('/api'), `URL cacheada no debe empezar por /api: ${url}`).toBe(
              false,
            );
          }
        }
      }
    }
  });

  test('02 · Cookies y CSRF: atributos de seguridad en cookies y rechazo 403 ante POST sin token CSRF', async ({
    page,
  }) => {
    await page.goto('/ingresar');
    await expect(page.getByRole('button', { name: 'Ingresar', exact: true })).toBeVisible();

    // Comprobar cookie XSRF-TOKEN con espera explícita
    await expect
      .poll(
        async () => {
          const cookies = await page.context().cookies();
          return cookies.find((c) => c.name === 'XSRF-TOKEN');
        },
        { message: 'La cookie XSRF-TOKEN debe estar presente', timeout: 15_000 },
      )
      .toBeDefined();

    const cookiesIniciales = await page.context().cookies();
    const cookieXsrf = cookiesIniciales.find((c) => c.name === 'XSRF-TOKEN');
    expect(cookieXsrf, 'Cookie XSRF-TOKEN debe estar definida').toBeDefined();
    expect(cookieXsrf!.secure, 'Cookie XSRF-TOKEN debe ser Secure').toBe(true);
    expect(cookieXsrf!.sameSite, 'Cookie XSRF-TOKEN debe ser SameSite=Strict').toBe('Strict');

    // Iniciar sesión y comprobar cookie BT_SESION
    await iniciarSesion(page, 'cliente@ejemplo.test', 'barberturno');
    const cookiesAutenticado = await page.context().cookies();
    const cookieSesion = cookiesAutenticado.find((c) => c.name === 'BT_SESION');
    expect(cookieSesion, 'Cookie BT_SESION debe existir tras login').toBeDefined();
    expect(cookieSesion!.httpOnly, 'Cookie BT_SESION debe ser HttpOnly').toBe(true);
    expect(cookieSesion!.secure, 'Cookie BT_SESION debe ser Secure').toBe(true);

    // Un POST autenticado sin cabecera X-XSRF-TOKEN debe devolver 403 Forbidden
    const peticionSinCsrf = await page.request.post('/api/auth/logout', {
      headers: {
        'Content-Type': 'application/json',
      },
    });
    expect(peticionSinCsrf.status(), 'POST sin cabecera X-XSRF-TOKEN debe responder 403').toBe(403);

    await cerrarSesion(page);
  });

  test('03 · Acceso del cliente por la pantalla «Ingresar» y cierre de sesión', async ({
    page,
  }) => {
    await iniciarSesion(page, 'cliente@ejemplo.test', 'barberturno');
    await expect(page).toHaveURL(/\/n\/barberturno\//);
    await expect(page.locator('main')).toBeVisible();

    await cerrarSesion(page);
    await expect(page).toHaveURL(/\/ingresar(?:\?|$)/);
    await expect(page.getByRole('button', { name: 'Ingresar', exact: true })).toBeVisible();
  });

  test('04 · Selector de barbería: alternancia entre barberías sin datos residuales', async ({
    page,
  }) => {
    // multirol@ejemplo.test tiene membresía en ambas barberías (Propietario en barberturno, Barbero en sur)
    await iniciarSesion(page, 'multirol@ejemplo.test', null);
    await expect(page).toHaveURL(/\/negocios$/);

    const listaBarberias = page.getByRole('list', { name: 'Barberías', exact: true });
    await expect(listaBarberias.getByRole('listitem')).toHaveCount(2);

    // 1. Entrar en BarberTurno
    await page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true }).click();
    await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    const contexto1 = await abrirContexto(page);
    await expect(contexto1).toContainText('BarberTurno');
    await expect(contexto1).toContainText('Propietario');
    await cerrarContexto(page);

    // En BarberTurno (rol Propietario/ADMIN): selector de barberos visible con Carlos y Miguel
    const selectorBarbero1 = page.locator('[data-selector-barbero]');
    await expect(selectorBarbero1).toBeVisible();
    await selectorBarbero1.locator('mat-select').click();
    await expect(page.getByRole('option', { name: 'Carlos' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Miguel' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Personal de dos barberías' })).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('option', { name: 'Carlos' })).toBeHidden();

    // Contenido distintivo de catálogo: como Propietario, accede a /admin/servicios con Barba y Corte clásico
    await page.goto('/n/barberturno/admin/servicios');
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
    await expect(page.locator('main')).toContainText('Barba');
    await expect(page.locator('main')).toContainText('Corte clásico');

    // 2. Cambiar a Barbería Sur
    const contextoCambio = await abrirContexto(page);
    await contextoCambio.getByRole('link', { name: 'Cambiar de barbería', exact: true }).click();
    await expect(page).toHaveURL(/\/negocios$/);

    await page.getByRole('button', { name: 'Entrar en Barbería Sur', exact: true }).click();
    await expect(page).toHaveURL(/\/n\/sur\/agenda$/);
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    const contexto2 = await abrirContexto(page);
    await expect(contexto2).toContainText('Barbería Sur');
    await expect(contexto2).toContainText('Barbero');
    await expect(contexto2).not.toContainText('Propietario');
    await cerrarContexto(page);

    // En Barbería Sur: Multirol es Barbero.
    // 2.1 Verificar que la agenda carga correctamente, sin errores, con título y estado propio
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
    await expect(page.locator('main .alerta, main [role="alert"]')).toHaveCount(0);
    await expect(page.locator('app-reserva-esqueleto')).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Agenda');
    await expect(page.locator('.subtitulo')).toHaveText('Todas las horas corresponden a Lima.');
    await expect(page.locator('app-agenda .linea-tiempo')).toBeVisible();
    await expect(page.getByText('Sin citas para este día.')).toBeVisible();
    await expect(page.locator('.resumen-conteos')).toContainText('0 citas');

    // El selector de barberos no existe (no es admin) y no hay residuos de BarberTurno
    await expect(page.locator('[data-selector-barbero]')).toHaveCount(0);
    await expect(page.locator('main')).not.toContainText('Carlos');
    await expect(page.locator('main')).not.toContainText('Miguel');
    await expect(page.locator('main')).not.toContainText('Barba');
    await expect(page.locator('main')).not.toContainText('BarberTurno');

    // 2.2 Verificar datos propios de Sur en la vista de cliente/reserva (/n/sur/reservar):
    // «Corte clásico» como único servicio y «Personal de dos barberías» como profesional exclusivo de Sur
    await page.goto('/n/sur/reservar');
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
    await expect(page.locator('main .alerta, main [role="alert"]')).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Reservar un turno');

    // Servicio propio de Sur: Corte clásico (Barba no existe en Sur)
    await expect(page.getByRole('radio', { name: 'Corte clásico', exact: true })).toBeVisible();
    await expect(page.getByRole('radio', { name: 'Barba', exact: true })).toHaveCount(0);

    // Profesional propio de Sur: Personal de dos barberías (Carlos y Miguel no pertenecen a Sur)
    await expect(
      page.locator('label.profesional').filter({ hasText: 'Personal de dos barberías' }),
    ).toBeVisible();
    await expect(page.locator('label.profesional').filter({ hasText: 'Carlos' })).toHaveCount(0);
    await expect(page.locator('label.profesional').filter({ hasText: 'Miguel' })).toHaveCount(0);
    await expect(page.locator('main')).not.toContainText('BarberTurno');

    await cerrarSesion(page);
  });

  test('05 · Reserva (cliente): reserva de turno con margen, verificación en Mis citas y cancelación', async ({
    page,
  }) => {
    let reservaCreada: { id: number; codigo: string; version: number } | null = null;
    let cancelada = false;
    let errorCuerpo: unknown = null;
    let falloCuerpo = false;
    const erroresLimpieza: Error[] = [];

    try {
      await iniciarSesion(page, 'cliente@ejemplo.test', 'barberturno');
      await page.goto('/n/barberturno/reservar');
      await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
      await expect(page.locator('mat-step-header').first()).toBeVisible();

      // Paso 1: Servicio y profesional
      await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
      await page.getByRole('button', { name: 'Elegir fecha y hora' }).click();

      // Paso 2: Fecha y hora (elegir un día con margen suficiente, p. ej. 3 días adelante, para poder cancelar)
      const botonesDias = page.locator('.dias button.dia');
      await expect(botonesDias.first()).toBeVisible();
      const totalDias = await botonesDias.count();
      const indiceDia = totalDias >= 4 ? 3 : totalDias - 1;
      const botonDiaElegido = botonesDias.nth(indiceDia);
      await botonDiaElegido.click();

      // Esperar a que el día quede seleccionado y la rejilla de franjas corresponda a ese día
      await expect(botonDiaElegido).toHaveAttribute('aria-pressed', 'true');
      const fechaDia = await botonDiaElegido.getAttribute('data-dia');
      if (fechaDia) {
        await expect(page.locator('.horas')).toHaveAttribute('data-fecha-consultada', fechaDia);
      }
      await expect(page.locator('.horas[aria-busy="true"]')).toHaveCount(0);

      const franjas = page.locator('.rejilla-horas button.franja');
      await expect(franjas.first()).toBeVisible();
      await franjas.first().click();

      // Paso 3: Confirmación
      const botonConfirmar = page.getByRole('button', { name: 'Confirmar reserva', exact: true });
      const siguienteHabilitado = page
        .getByRole('button', { name: 'Siguiente', exact: true })
        .and(page.locator(':enabled'));

      await expect(botonConfirmar.or(siguienteHabilitado)).toBeVisible();
      if (!(await botonConfirmar.isVisible())) {
        await siguienteHabilitado.click();
      }
      await expect(botonConfirmar).toBeVisible();

      const respuestaReserva = page.waitForResponse(
        (r) =>
          r.url().includes('/api/n/barberturno/reservas') &&
          r.request().method() === 'POST' &&
          !r.url().endsWith('/cancelacion'),
      );

      await botonConfirmar.click();
      const resReserva = await respuestaReserva;
      expect(resReserva.status(), 'La reserva debe crearse con código 201').toBe(201);
      reservaCreada = await resReserva.json();
      expect(reservaCreada?.codigo).toBeDefined();

      // Debe navegar a Mis citas y mostrar la reserva creada
      await expect(page).toHaveURL(/\/n\/barberturno\/mis-citas/);
      const tarjeta = page
        .locator('app-reserva-tarjeta')
        .filter({ hasText: reservaCreada!.codigo });
      // Recorrer las páginas de la UI evita depender de cuántas canceladas dejaron otros pases.
      // Se conservan todos los estados para comprobar «Cancelada» en la misma tarjeta después.
      const listaCitas = page.locator('app-mis-citas .lista-citas');
      await expect(listaCitas).toHaveAttribute('aria-busy', 'false');
      while (!(await tarjeta.isVisible())) {
        const siguiente = listaCitas.getByRole('button', { name: 'Página siguiente', exact: true });
        await expect(
          siguiente,
          `La reserva ${reservaCreada!.codigo} debe aparecer antes de agotar las páginas de Mis citas`,
        ).toBeEnabled();
        await siguiente.click();
        await expect(listaCitas).toHaveAttribute('aria-busy', 'false');
      }
      await expect(tarjeta).toBeVisible();

      // Cancelar la reserva creada para dejar la demo en su estado original
      const botonCancelar = tarjeta.getByRole('button', { name: 'Cancelar', exact: true });
      await botonCancelar.click();

      const dialogo = page.getByRole('dialog');
      await expect(dialogo).toBeVisible();

      const respuestaCancelacion = page.waitForResponse(
        (r) =>
          r.url().includes(`/api/n/barberturno/reservas/${reservaCreada!.id}/cancelacion`) &&
          r.request().method() === 'POST',
      );

      await dialogo.getByRole('button', { name: /Cancelar cita|Confirmar cancelación/ }).click();
      const resCancel = await respuestaCancelacion;
      expect(resCancel.status(), 'La cancelación debe responder 200').toBe(200);
      cancelada = true;

      await expect(tarjeta).toContainText('Cancelada');
    } catch (err) {
      errorCuerpo = err;
      falloCuerpo = true;
    } finally {
      // 1. Cancelación garantizada si no se completó por UI
      if (reservaCreada && !cancelada) {
        try {
          let cookies = await page.context().cookies();
          let cookieXsrf = cookies.find((c) => c.name === 'XSRF-TOKEN');
          if (!cookieXsrf) {
            await page.request.get('/api/auth/sesion');
            cookies = await page.context().cookies();
            cookieXsrf = cookies.find((c) => c.name === 'XSRF-TOKEN');
          }
          if (!cookieXsrf) {
            erroresLimpieza.push(
              new Error('No se pudo obtener la cookie XSRF-TOKEN para cancelar la reserva'),
            );
          } else {
            const resCancel = await page.request.post(
              `/api/n/barberturno/reservas/${reservaCreada.id}/cancelacion`,
              {
                headers: {
                  'X-XSRF-TOKEN': cookieXsrf.value,
                  'Content-Type': 'application/json',
                },
                data: {
                  version: reservaCreada.version ?? 0,
                  motivo: 'Limpieza de reserva tras fallo en prueba',
                },
              },
            );
            if (resCancel.status() !== 200) {
              erroresLimpieza.push(
                new Error(
                  `La cancelación de la reserva ${reservaCreada.id} en limpieza devolvió ${resCancel.status()} (se esperaba 200)`,
                ),
              );
            } else {
              cancelada = true;
            }
          }
        } catch (errCancel) {
          erroresLimpieza.push(
            errCancel instanceof Error ? errCancel : new Error(String(errCancel)),
          );
        }
      }

      // 2. Cierre de sesión garantizado con 204
      try {
        const dialogo = page.getByRole('dialog');
        if (await dialogo.isVisible()) {
          await page.keyboard.press('Escape');
        }
        await cerrarSesion(page);
      } catch (errLogout) {
        erroresLimpieza.push(errLogout instanceof Error ? errLogout : new Error(String(errLogout)));
      }
    }

    // Registrar fallos de limpieza sin ocultar el error original de la prueba
    if (erroresLimpieza.length > 0) {
      for (const errLimpieza of erroresLimpieza) {
        test.info().errors.push({
          message: `Fallo en limpieza: ${errLimpieza.message}`,
        });
      }
    }

    if (falloCuerpo) {
      if (erroresLimpieza.length > 0 && errorCuerpo instanceof Error) {
        errorCuerpo.message += `\n\nAdicionalmente falló la limpieza:\n${erroresLimpieza.map((e) => `- ${e.message}`).join('\n')}`;
      }
      throw errorCuerpo;
    }

    if (erroresLimpieza.length > 0) {
      throw new Error(
        `Fallos durante la limpieza de la prueba 05:\n${erroresLimpieza.map((e) => `- ${e.message}`).join('\n')}`,
      );
    }
  });

  test('06 · Agenda del barbero o encargado: visualización de turnos programados del día', async ({
    page,
  }) => {
    await iniciarSesion(page, 'carlos@ejemplo.test', 'barberturno');
    await page.goto('/n/barberturno/agenda');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Agenda');
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    const lineaTiempo = page.locator('app-agenda .linea-tiempo');
    await expect(lineaTiempo).toBeVisible();
    await expect(lineaTiempo).not.toHaveAttribute('aria-busy', 'true');
    await expect(page.locator('app-reserva-esqueleto')).toHaveCount(0);

    // Tras terminar la carga, exige una cita real o el mensaje explícito de estado vacío («Sin citas para este día.»)
    const citaOEstadoVacio = page
      .locator('app-agenda-cita')
      .first()
      .or(page.getByText('Sin citas para este día.'))
      .or(page.locator('.dias-vacios'));
    await expect(citaOEstadoVacio).toBeVisible();

    await cerrarSesion(page);
  });

  test('07 · Avisos (notificaciones): apertura del panel y cierre con botón Atrás sin alterar la ruta', async ({
    page,
  }) => {
    await iniciarSesion(page, 'cliente@ejemplo.test', 'barberturno');
    await page.goto('/n/barberturno/reservar');
    const urlAntes = page.url();

    const botonCampana = page
      .getByRole('button', { name: /^Avisos: \d+ sin leer$/ })
      .or(page.getByRole('button', { name: /^Ver avisos/ }));
    await expect(botonCampana).toBeVisible();
    await botonCampana.click();

    const dialogo = page.getByRole('dialog', { name: 'Avisos', exact: true });
    await expect(dialogo).toBeVisible();
    await expect(dialogo.locator('app-avisos-panel')).toBeVisible();

    const filasAvisos = dialogo.locator('app-avisos-panel li');
    await expect(filasAvisos.first()).toBeVisible();

    // Atrás del navegador cierra el diálogo sin cambiar de ruta
    await page.goBack();
    await expect(dialogo).toHaveCount(0);
    expect(page.url()).toBe(urlAntes);

    await cerrarSesion(page);
  });

  test('08 · Bandeja (encargado): carga correcta de la bandeja web de WhatsApp sin errores', async ({
    page,
  }) => {
    await iniciarSesion(page, 'encargado@ejemplo.test', 'barberturno');

    // Espera la respuesta de la lista (/api/... de la bandeja)
    const respuestaLista = page.waitForResponse(
      (r) => r.url().includes('/whatsapp/conversaciones') && r.request().method() === 'GET',
    );
    await page.goto('/n/barberturno/whatsapp');
    const res = await respuestaLista;
    expect(res.status(), 'La lista de conversaciones debe responder 200').toBe(200);

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Bandeja de WhatsApp');
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
    await expect(page.locator('app-estado-contenido[tipo="carga"]')).toHaveCount(0);

    // Exige conversaciones o el estado vacío definitivo («No hay conversaciones derivadas»), sin mensaje de error
    const conversacionesOEstadoVacio = page
      .locator('.panel-lista .item-fila')
      .first()
      .or(page.getByText('No hay conversaciones derivadas'));
    await expect(conversacionesOEstadoVacio).toBeVisible();
    await expect(page.locator('.alerta-error, [role="alert"]')).toHaveCount(0);

    await cerrarSesion(page);
  });

  test('09 · PWA instalable: verificación de instalabilidad Chromium vía CDP sin errores', async ({
    page,
    browserName,
  }) => {
    test.skip(
      browserName !== 'chromium',
      'La auditoría CDP de instalabilidad solo aplica a Chromium',
    );

    await page.goto('/');
    await expect(page.locator('main')).toBeVisible();
    await page.evaluate(async () => {
      if ('serviceWorker' in navigator) {
        await navigator.serviceWorker.ready;
      }
    });

    // En Chromium cualquier error de CDP debe hacer fallar la prueba directamente
    const cdp = await page.context().newCDPSession(page);
    const resultado = await cdp.send('Page.getInstallabilityErrors');
    expect(resultado.installabilityErrors).toEqual([]);
  });

  test('10 · Caché sin datos privados: ausencia de URLs /api y datos confidenciales en Cache Storage, e inmunidad ante Atrás tras logout', async ({
    page,
  }) => {
    const correo = 'cliente@ejemplo.test';

    // 1. Completar la carga privada de «Agenda» con usuario barbero esperando su respuesta /api
    await iniciarSesion(page, 'carlos@ejemplo.test', 'barberturno');
    const respuestaAgenda = page.waitForResponse(
      (r) =>
        r.url().includes('/api/n/barberturno/reservas') &&
        !r.url().includes('/mias') &&
        r.request().method() === 'GET',
    );
    await page.goto('/n/barberturno/agenda');
    const resAgenda = await respuestaAgenda;
    expect(resAgenda.status(), 'GET /reservas (agenda) debe responder 200').toBe(200);
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
    await cerrarSesion(page);

    // 2. Completar la carga privada de «Mis citas» y «Reservar» con usuario cliente esperando sus respuestas /api
    await iniciarSesion(page, correo, 'barberturno');
    const respuestaMisCitas = page.waitForResponse(
      (r) => r.url().includes('/api/n/barberturno/reservas/mias') && r.request().method() === 'GET',
    );
    await page.goto('/n/barberturno/mis-citas');
    const resMisCitas = await respuestaMisCitas;
    expect(resMisCitas.status(), 'GET /reservas/mias debe responder 200').toBe(200);
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    const respuestaServicios = page.waitForResponse(
      (r) => r.url().includes('/api/n/barberturno/servicios') && r.request().method() === 'GET',
    );
    await page.goto('/n/barberturno/reservar');
    const resServicios = await respuestaServicios;
    expect(resServicios.status(), 'GET /servicios debe responder 200').toBe(200);
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
    await expect(page.locator('mat-step-header').first()).toBeVisible();

    // 3. Esperar a que el service worker esté registrado y controle la página
    await expect
      .poll(
        async () =>
          page.evaluate(() => 'serviceWorker' in navigator && !!navigator.serviceWorker.controller),
        {
          message: 'El service worker debe estar registrado y controlar la página',
          timeout: 15_000,
        },
      )
      .toBe(true);

    // 4. Examinar Cache Storage con expect.poll reactivo: exige entradas públicas cacheadas y prohíbe /api/
    const inspeccionarCaches = async (correoCliente: string) => {
      return page.evaluate(async (correoCliente) => {
        const nombres = await caches.keys();
        const apis: string[] = [];
        const sospechosos: string[] = [];
        const publicas: string[] = [];

        for (const nombre of nombres) {
          const cache = await caches.open(nombre);
          const peticiones = await cache.keys();
          for (const peticion of peticiones) {
            const url = peticion.url;
            if (url.includes('/api/')) {
              apis.push(url);
            }
            if (
              url.includes('.js') ||
              url.includes('.html') ||
              url.includes('.css') ||
              url.endsWith('/') ||
              url.includes('/manifest')
            ) {
              publicas.push(url);
            }
            const resp = await cache.match(peticion);
            if (resp) {
              // Tratar como no inspeccionables SOLO las respuestas opacas (cross-origin);
              // cualquier otro error debe propagarse
              if (resp.type === 'opaque') {
                continue;
              }
              const ct = resp.headers.get('content-type') || '';
              if (ct.includes('text') || ct.includes('json') || ct.includes('javascript')) {
                const texto = await resp.clone().text();
                if (texto.includes(correoCliente) || texto.includes('Cliente de demostración')) {
                  sospechosos.push(`Caché ${nombre}: ${peticion.url}`);
                }
              }
            }
          }
        }
        return {
          urlsApi: apis,
          contenidosConDatosPrivados: sospechosos,
          totalPublicas: publicas.length,
        };
      }, correoCliente);
    };

    // Exigir que haya entradas públicas cacheadas (no Cache Storage vacío)
    await expect
      .poll(
        async () => (await inspeccionarCaches(correo)).totalPublicas,
        'Cache Storage debe contener recursos públicos cacheados (p. ej. index.html o archivos .js)',
      )
      .toBeGreaterThan(0);

    await expect
      .poll(
        async () => (await inspeccionarCaches(correo)).urlsApi,
        'Cache Storage no debe contener URLs de /api/',
      )
      .toEqual([]);

    await expect
      .poll(
        async () => (await inspeccionarCaches(correo)).contenidosConDatosPrivados,
        'Cache Storage no debe contener datos personales del cliente',
      )
      .toEqual([]);

    // 5. Salir y pulsar Atrás no debe mostrar datos previos
    await cerrarSesion(page);
    await page.goBack();

    await expect(page).toHaveURL(/\/ingresar(?:\?|$)/);
    await expect(page.locator('main')).not.toContainText(correo);
    await expect(page.locator('app-mis-citas')).toHaveCount(0);
  });

  test('11 · Sin conexión: banner de conectividad, rechazo de escritura y ausencia de envíos encolados al reconectar', async ({
    page,
    context,
  }) => {
    const reservasInesperadas: { id: number; version: number }[] = [];
    const escriturasDetectadas: Request[] = [];
    const peticionesNoGet: string[] = [];
    const promesasRespuestas: Promise<void>[] = [];
    const idsReservasPrevias = new Set<number>();
    let instantaneaInicialValida = false;
    let listenersRegistrados = false;

    const interceptor = (req: Request) => {
      if (req.url().includes('/api/') && req.method() !== 'GET') {
        peticionesNoGet.push(`${req.method()} ${req.url()}`);
        escriturasDetectadas.push(req);
      }
    };

    const responseInterceptor = (res: Response) => {
      if (
        res.url().includes('/api/n/barberturno/reservas') &&
        res.request().method() === 'POST' &&
        !res.url().endsWith('/cancelacion') &&
        res.status() === 201
      ) {
        const tarea = (async () => {
          try {
            const cuerpo = await res.json();
            if (cuerpo?.id && !reservasInesperadas.some((r) => r.id === cuerpo.id)) {
              reservasInesperadas.push({
                id: cuerpo.id,
                version: typeof cuerpo.version === 'number' ? cuerpo.version : 0,
              });
            }
          } catch {
            // Ignorar fallo de parseo JSON
          }
        })();
        promesasRespuestas.push(tarea);
      }
    };

    let errorCuerpo: unknown = null;
    let falloCuerpo = false;
    const erroresLimpieza: Error[] = [];

    try {
      await iniciarSesion(page, 'cliente@ejemplo.test', 'barberturno');

      // Guardar reservas previas antes de desconectar para comparar en limpieza si fuera necesario
      try {
        const respMiasInicial = await page.request.get(
          '/api/n/barberturno/reservas/mias?tamano=100',
        );
        if (!respMiasInicial.ok()) {
          throw new Error(
            `La consulta inicial de reservas devolvió HTTP ${respMiasInicial.status()}`,
          );
        }
        const cuerpoMias = await respMiasInicial.json();
        if (!Array.isArray(cuerpoMias?.contenido)) {
          throw new Error('La consulta inicial de reservas no devolvió una lista válida');
        }
        for (const r of cuerpoMias.contenido) {
          if (typeof r?.id !== 'number') {
            throw new Error('La consulta inicial de reservas devolvió un identificador inválido');
          }
          idsReservasPrevias.add(r.id);
        }
        instantaneaInicialValida = true;
      } catch (error) {
        throw new Error(
          'No se pudo obtener una instantánea inicial válida de las reservas; se aborta la prueba antes de desconectar.',
          { cause: error },
        );
      }

      await page.goto('/n/barberturno/reservar');
      await expect(page.locator('main')).toBeVisible();
      await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
      await expect(page.locator('mat-step-header').first()).toBeVisible();

      // Paso 1: Elegir servicio
      await page.getByRole('radio', { name: 'Corte clásico', exact: true }).check();
      await page.getByRole('button', { name: 'Elegir fecha y hora' }).click();

      // Paso 2: Elegir fecha y franja
      const botonesDias = page.locator('.dias button.dia');
      await expect(botonesDias.first()).toBeVisible();
      const totalDias = await botonesDias.count();
      const indiceDia = totalDias >= 4 ? 3 : totalDias - 1;
      const botonDiaElegido = botonesDias.nth(indiceDia);
      await botonDiaElegido.click();

      // Esperar a que el día quede seleccionado y la rejilla de franjas corresponda a ese día
      await expect(botonDiaElegido).toHaveAttribute('aria-pressed', 'true');
      const fechaDia = await botonDiaElegido.getAttribute('data-dia');
      if (fechaDia) {
        await expect(page.locator('.horas')).toHaveAttribute('data-fecha-consultada', fechaDia);
      }
      await expect(page.locator('.horas[aria-busy="true"]')).toHaveCount(0);

      const franjas = page.locator('.rejilla-horas button.franja');
      await expect(franjas.first()).toBeVisible();
      await franjas.first().click();

      // Paso 3: Confirmación de reserva
      const botonConfirmar = page.getByRole('button', { name: 'Confirmar reserva', exact: true });
      const siguienteHabilitado = page
        .getByRole('button', { name: 'Siguiente', exact: true })
        .and(page.locator(':enabled'));

      await expect(botonConfirmar.or(siguienteHabilitado)).toBeVisible();
      if (!(await botonConfirmar.isVisible())) {
        await siguienteHabilitado.click();
      }
      await expect(botonConfirmar).toBeVisible();

      // Desconectar
      await context.setOffline(true);

      const aviso = page.locator('app-avisos-pwa');
      await expect(aviso).toBeVisible();
      await expect(aviso).toContainText('Sin conexión.');

      // Intentar una escritura real en modo sin conexión
      await botonConfirmar.click();

      // Comprobar que la UI informa del fallo en la solicitud
      const alertaFallo = page.locator('.mensaje[role="alert"], [role="alert"]').first();
      await expect(alertaFallo).toBeVisible();
      await expect(alertaFallo).toContainText(
        /No se pudo completar la solicitud|No pudimos completar la solicitud/,
      );
      await expect(botonConfirmar).not.toContainText('Enviando…');

      // Comprobar que no hay Background Sync pendiente.
      // Si SyncManager existe y getTags() lanza «Background Sync is disabled.», se trata como sin etiquetas.
      // Cualquier otro error debe propagarse.
      const etiquetasSync = await page.evaluate(async () => {
        if ('serviceWorker' in navigator && 'SyncManager' in window) {
          try {
            const reg = await navigator.serviceWorker.ready;
            // @ts-expect-error SyncManager interface en navegadores compatibles
            const tags = await reg.sync?.getTags();
            return tags ?? [];
          } catch (error: unknown) {
            const mensaje = error instanceof Error ? error.message : String(error);
            if (mensaje.includes('Background Sync is disabled')) {
              return [];
            }
            throw error;
          }
        }
        return [];
      });
      expect(etiquetasSync).toEqual([]);

      // Escuchar en el BrowserContext (que en Chromium incluye peticiones del service worker)
      // desde ANTES de reconectar
      context.on('request', interceptor);
      context.on('response', responseInterceptor);
      listenersRegistrados = true;

      await context.setOffline(false);
      await expect(page.locator('app-avisos-pwa')).not.toContainText('Sin conexión.');
      await expect(page.locator('app-avisos-pwa')).toContainText('Conexión restablecida.');

      let tiempoAgotado = false;
      let errorReenvio: Error | null = null;

      // Espera negativa explícita: ventana de 5 s durante la cual NO debe emitirse
      // ninguna petición de escritura a /api/ en el BrowserContext. Debe agotarse el tiempo y lanzar TimeoutError.
      try {
        const peticionInesperada = await context.waitForEvent('request', {
          predicate: (req) => req.url().includes('/api/') && req.method() !== 'GET',
          timeout: 5000,
        });
        errorReenvio = new Error(
          `Se detectó un reenvío automático inesperado a /api/ en el BrowserContext: ${peticionInesperada.method()} ${peticionInesperada.url()}`,
        );
      } catch (error: unknown) {
        if (
          error instanceof errors.TimeoutError ||
          (error instanceof Error && error.name === 'TimeoutError') ||
          (typeof error === 'object' &&
            error !== null &&
            'name' in error &&
            (error as { name: string }).name === 'TimeoutError')
        ) {
          tiempoAgotado = true;
        } else {
          throw error;
        }
      }

      expect(
        tiempoAgotado,
        'La ventana de 5 s en BrowserContext debió agotar el tiempo sin que se produjeran peticiones de escritura',
      ).toBe(true);

      if (errorReenvio) {
        throw errorReenvio;
      }

      // Exigir que no se haya reintentado ninguna escritura encolada de manera automática
      expect(
        peticionesNoGet,
        'No debe reenviarse ninguna petición de escritura a /api/ automáticamente al reconectar',
      ).toEqual([]);
    } catch (err) {
      errorCuerpo = err;
      falloCuerpo = true;
    } finally {
      try {
        await context.setOffline(false);
      } catch (errConexion) {
        erroresLimpieza.push(
          errConexion instanceof Error ? errConexion : new Error(String(errConexion)),
        );
      }

      // 1. Recolección de las escrituras detectadas: esperar su respuesta con un plazo mayor (30 s)
      // Manteniendo los listeners activos para capturar cualquier 201 en vuelo
      let huboEscrituraSinRespuesta = false;
      const escriturasAEsperar = [...escriturasDetectadas];

      for (const req of escriturasAEsperar) {
        try {
          const res = await Promise.race([
            req.response(),
            new Promise<null>((resolve) => setTimeout(() => resolve(null), 30000)),
          ]);
          if (res) {
            if (
              res.url().includes('/api/n/barberturno/reservas') &&
              req.method() === 'POST' &&
              !res.url().endsWith('/cancelacion') &&
              res.status() === 201
            ) {
              try {
                const cuerpo = await res.json();
                if (cuerpo?.id && !reservasInesperadas.some((r) => r.id === cuerpo.id)) {
                  reservasInesperadas.push({
                    id: cuerpo.id,
                    version: typeof cuerpo.version === 'number' ? cuerpo.version : 0,
                  });
                }
              } catch {
                // Ignorar error al parsear JSON
              }
            }
          } else {
            // Venció el plazo de 30 s sin respuesta
            huboEscrituraSinRespuesta = true;
          }
        } catch {
          huboEscrituraSinRespuesta = true;
        }
      }

      // 2. Si venció el plazo sin respuesta para alguna escritura, consultar reservas activas por API
      // y cancelar cualquiera creada durante la prueba comparando con las que había antes de desconectar
      if (instantaneaInicialValida && huboEscrituraSinRespuesta) {
        try {
          const respMias = await page.request.get('/api/n/barberturno/reservas/mias?tamano=100');
          if (!respMias.ok()) {
            erroresLimpieza.push(
              new Error(
                `La consulta de reservas en limpieza devolvió ${respMias.status()} (se esperaba 200)`,
              ),
            );
          } else {
            const cuerpoMias = await respMias.json();
            const contenido = Array.isArray(cuerpoMias?.contenido) ? cuerpoMias.contenido : [];
            for (const r of contenido) {
              if (
                typeof r?.id === 'number' &&
                !idsReservasPrevias.has(r.id) &&
                r.estado !== 'CANCELADA' &&
                !reservasInesperadas.some((item) => item.id === r.id)
              ) {
                reservasInesperadas.push({
                  id: r.id,
                  version: typeof r.version === 'number' ? r.version : 0,
                });
              }
            }
          }
        } catch (errMias) {
          erroresLimpieza.push(errMias instanceof Error ? errMias : new Error(String(errMias)));
        }
      }

      await Promise.allSettled(promesasRespuestas);

      // 3. Cancelar cualquier reserva inesperada capturada y verificar su respuesta HTTP 200
      for (const reserva of reservasInesperadas) {
        try {
          let cookies = await context.cookies();
          let cookieXsrf = cookies.find((c) => c.name === 'XSRF-TOKEN');
          if (!cookieXsrf) {
            await page.request.get('/api/auth/sesion');
            cookies = await context.cookies();
            cookieXsrf = cookies.find((c) => c.name === 'XSRF-TOKEN');
          }
          if (!cookieXsrf) {
            erroresLimpieza.push(
              new Error(
                `No se pudo obtener la cookie XSRF-TOKEN para cancelar la reserva inesperada ${reserva.id}`,
              ),
            );
          } else {
            const resCancel = await page.request.post(
              `/api/n/barberturno/reservas/${reserva.id}/cancelacion`,
              {
                headers: {
                  'X-XSRF-TOKEN': cookieXsrf.value,
                  'Content-Type': 'application/json',
                },
                data: {
                  version: reserva.version,
                  motivo: 'Limpieza de reserva inesperada en prueba offline',
                },
              },
            );
            if (resCancel.status() !== 200) {
              erroresLimpieza.push(
                new Error(
                  `La cancelación de la reserva inesperada ${reserva.id} devolvió ${resCancel.status()} (se esperaba 200)`,
                ),
              );
            }
          }
        } catch (errCancel) {
          erroresLimpieza.push(
            errCancel instanceof Error ? errCancel : new Error(String(errCancel)),
          );
        }
      }

      // 4. Retirar listeners de peticiones y respuestas antes de cerrar sesión
      if (listenersRegistrados) {
        context.off('request', interceptor);
        context.off('response', responseInterceptor);
      }

      // 5. Cierre de sesión garantizado con 204
      try {
        const dialogo = page.getByRole('dialog');
        if (await dialogo.isVisible()) {
          await page.keyboard.press('Escape');
        }
        await cerrarSesion(page);
      } catch (errLogout) {
        erroresLimpieza.push(errLogout instanceof Error ? errLogout : new Error(String(errLogout)));
      }
    }

    // Reportar errores de limpieza sin ocultar el error del cuerpo de la prueba
    if (erroresLimpieza.length > 0) {
      for (const errLimpieza of erroresLimpieza) {
        test.info().errors.push({
          message: `Fallo en limpieza: ${errLimpieza.message}`,
        });
      }
    }

    if (falloCuerpo) {
      if (erroresLimpieza.length > 0 && errorCuerpo instanceof Error) {
        errorCuerpo.message += `\n\nAdicionalmente falló la limpieza:\n${erroresLimpieza.map((e) => `- ${e.message}`).join('\n')}`;
      }
      throw errorCuerpo;
    }

    if (erroresLimpieza.length > 0) {
      throw new Error(
        `Fallos durante la limpieza de la prueba 11:\n${erroresLimpieza.map((e) => `- ${e.message}`).join('\n')}`,
      );
    }
  });
});
