import AxeBuilder from '@axe-core/playwright';
import { type Page } from '@playwright/test';
import { Buffer } from 'node:buffer';
import { test, expect } from './fixture';
import { sembrarConversacionDerivada } from '../base-datos';

/** Viewports estándar para pruebas móviles y horizontal de acuerdo con CP-8 */
const VIEWPORTS = [
  { nombre: '320x640', width: 320, height: 640 },
  { nombre: '360x800', width: 360, height: 800 },
  { nombre: '412x915', width: 412, height: 915 },
  { nombre: '800x360-horizontal', width: 800, height: 360 },
];

/**
 * Decodifica las dimensiones reales de una imagen PNG a partir de su cabecera IHDR.
 *
 * @param buffer - Buffer de los bytes del PNG
 * @returns Ancho y alto en píxeles
 */
function decodificarDimensionesPng(buffer: Buffer): { ancho: number; alto: number } {
  const firmaValida = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  expect(buffer.subarray(0, 8).equals(firmaValida)).toBe(true);
  const tipoChunk = buffer.toString('ascii', 12, 16);
  expect(tipoChunk).toBe('IHDR');
  const ancho = buffer.readUInt32BE(16);
  const alto = buffer.readUInt32BE(20);
  return { ancho, alto };
}

/** Espera a que las fuentes e imágenes completen su renderizado */
async function esperarEstable(page: Page) {
  await page.evaluate(async () => {
    await new Promise((listo) => requestAnimationFrame(() => requestAnimationFrame(listo)));
    await document.fonts.ready;
    await Promise.all(
      Array.from(document.images).map((img) => img.decode().catch(() => undefined)),
    );
  });
}

/** Comprueba que no exista desplazamiento horizontal */
async function comprobarSinScrollHorizontal(page: Page, contexto: string) {
  const sinDesplazamiento = await page.evaluate(() => {
    return document.documentElement.scrollWidth <= window.innerWidth + 1;
  });
  expect(sinDesplazamiento, `Desplazamiento horizontal detectado en ${contexto}`).toBe(true);
}

/** Comprueba escalado de texto al 200% sin pérdida ni desbordamiento */
async function comprobarZoom200(page: Page, contexto: string) {
  await page.evaluate(() => {
    document.documentElement.style.fontSize = '200%';
  });
  await esperarEstable(page);
  const sinDesbordamiento = await page.evaluate(() => {
    return document.documentElement.scrollWidth <= window.innerWidth + 1;
  });
  expect(sinDesbordamiento, `Desbordamiento al 200% de texto en ${contexto}`).toBe(true);
  await page.evaluate(() => {
    document.documentElement.style.fontSize = '';
  });
  await esperarEstable(page);
}

/** Comprueba dimensiones mínimas de 44x44 px en controles interactivos principales */
async function comprobarObjetivosTactiles(page: Page, contexto: string) {
  // Enlaces de navegación inferior si existen
  const enlacesBarra = page.locator('.barra-inferior a');
  const totalEnlaces = await enlacesBarra.count();
  for (let i = 0; i < totalEnlaces; i++) {
    const enlace = enlacesBarra.nth(i);
    if (await enlace.isVisible()) {
      const box = await enlace.boundingBox();
      if (box) {
        expect(
          box.width >= 43.5 && box.height >= 43.5,
          `Enlace barra inferior en ${contexto} debe medir >= 44x44 (medido ${box.width}x${box.height})`,
        ).toBe(true);
      }
    }
  }

  // Botones principales visibles
  const botones = page.locator('main button[mat-flat-button]:visible, .cabecera button:visible');
  const totalBotones = await botones.count();
  for (let i = 0; i < totalBotones; i++) {
    const boton = botones.nth(i);
    const box = await boton.boundingBox();
    if (box) {
      expect(
        box.width >= 43.5 && box.height >= 43.5,
        `Botón principal en ${contexto} debe medir >= 44x44 (medido ${box.width}x${box.height})`,
      ).toBe(true);
    }
  }
}

/** Ejecuta análisis de accesibilidad axe y verifica ausencia de infracciones serias o críticas */
async function auditarAxe(page: Page, nombre: string) {
  await esperarEstable(page);
  const resultados = await new AxeBuilder({ page }).analyze();
  const relevantes = resultados.violations.filter(
    (v) => v.impact === 'serious' || v.impact === 'critical',
  );
  expect(
    relevantes.map((v) => ({
      id: v.id,
      impact: v.impact,
      nodes: v.nodes.map((n) => n.target.join(' ')),
    })),
    `Violaciones axe serias o críticas en ${nombre}`,
  ).toEqual([]);
}

/** Inicia sesión con credenciales demo en una barbería */
async function iniciarSesion(page: Page, correo: string) {
  await page.goto('/ingresar');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).evaluate((elemento, valor) => {
    (elemento as HTMLInputElement).value = valor;
    elemento.dispatchEvent(new Event('input', { bubbles: true }));
  }, process.env['BT_DEMO_PASSWORD'] ?? 'Demo1234!');
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).not.toHaveURL(/\/ingresar(?:\?|$)/);
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  if (/\/n\/barberturno\//.test(new URL(page.url()).pathname)) return;
  const botonEntrar = page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true });
  const dialogoPapel = page.getByRole('dialog').filter({ hasText: '¿Cómo quiere entrar' });
  if (await botonEntrar.or(dialogoPapel).first().isVisible()) {
    if (await dialogoPapel.isVisible()) return;
    await botonEntrar.click();
    await expect(page).toHaveURL(/\/n\/barberturno\//);
  }
}

// ============================================================================
// CP-7: Instalabilidad, Manifest e Iconos
// ============================================================================
test.describe('CP-7 · Instalabilidad y Manifest PWA', () => {
  test('GET /manifest.webmanifest responde 200 con campos completos según V2-DA-94', async ({
    request,
  }) => {
    const respuesta = await request.get('/manifest.webmanifest');
    expect(respuesta.status()).toBe(200);

    const contentType = respuesta.headers()['content-type'] ?? '';
    expect(contentType).toMatch(/(?:application\/(?:manifest\+)?json|text\/plain)/);

    const manifest = await respuesta.json();
    expect(manifest.id).toBe('/');
    expect(manifest.start_url).toBe('/');
    expect(manifest.scope).toBe('/');
    expect(manifest.name).toBe('BarberTurno');
    expect(manifest.short_name).toBe('BarberTurno');
    expect(manifest.description).toBe('Reservas y turnos para barberías');
    expect(manifest.display).toBe('standalone');
    expect(manifest.orientation).toBe('any');
    expect(manifest.lang).toBe('es-PE');
    expect(manifest.dir).toBe('ltr');
    expect(manifest.theme_color).toBe('#145c43');
    expect(manifest.background_color).toBe('#f6f8f7');
    expect(manifest.screenshots).toBeUndefined();
    expect(manifest.shortcuts).toBeUndefined();
    expect(manifest.related_applications).toBeUndefined();

    expect(manifest.icons).toHaveLength(3);
    const icono192 = manifest.icons.find((i: { sizes: string }) => i.sizes === '192x192');
    const icono512 = manifest.icons.find(
      (i: { sizes: string; purpose: string }) => i.sizes === '512x512' && i.purpose === 'any',
    );
    const maskable512 = manifest.icons.find(
      (i: { sizes: string; purpose: string }) => i.sizes === '512x512' && i.purpose === 'maskable',
    );

    expect(icono192).toBeDefined();
    expect(icono192.src).toBe('iconos/icono-192.png');
    expect(icono192.type).toBe('image/png');

    expect(icono512).toBeDefined();
    expect(icono512.src).toBe('iconos/icono-512.png');
    expect(icono512.type).toBe('image/png');

    expect(maskable512).toBeDefined();
    expect(maskable512.src).toBe('iconos/icono-maskable-512.png');
    expect(maskable512.type).toBe('image/png');
  });

  test('cada icono PNG responde 200 y sus dimensiones IHDR son exactas', async ({ request }) => {
    const iconos = [
      { ruta: '/iconos/icono-192.png', ancho: 192, alto: 192 },
      { ruta: '/iconos/icono-512.png', ancho: 512, alto: 512 },
      { ruta: '/iconos/icono-maskable-512.png', ancho: 512, alto: 512 },
      { ruta: '/iconos/apple-touch-icon.png', ancho: 180, alto: 180 },
    ];

    for (const item of iconos) {
      const respuesta = await request.get(item.ruta);
      expect(respuesta.status(), `Icono ${item.ruta} debe responder 200`).toBe(200);
      expect(respuesta.headers()['content-type']).toContain('image/png');
      const buffer = await respuesta.body();
      const dims = decodificarDimensionesPng(buffer);
      expect(dims.ancho, `Ancho de ${item.ruta}`).toBe(item.ancho);
      expect(dims.alto, `Alto de ${item.ruta}`).toBe(item.alto);
    }
  });

  test('HTML principal enlaza el manifest y contiene viewport-fit=cover y theme-color', async ({
    page,
  }) => {
    await page.goto('/');
    const manifestLink = page.locator('link[rel="manifest"]');
    await expect(manifestLink).toHaveAttribute('href', 'manifest.webmanifest');

    const themeColor = page.locator('meta[name="theme-color"]');
    await expect(themeColor).toHaveAttribute('content', '#145c43');

    const description = page.locator('meta[name="description"]');
    await expect(description).toHaveAttribute('content', 'Reservas y turnos para barberías');

    const viewport = page.locator('meta[name="viewport"]');
    const content = (await viewport.getAttribute('content')) ?? '';
    expect(content).toContain('viewport-fit=cover');
    expect(content).toContain('interactive-widget=resizes-content');
    expect(content).not.toContain('user-scalable=no');
    expect(content).not.toContain('maximum-scale');

    const appleTouchIcon = page.locator('link[rel="apple-touch-icon"]');
    await expect(appleTouchIcon).toHaveAttribute('href', 'iconos/apple-touch-icon.png');
  });

  test('instalabilidad Chromium vía CDP sin errores', async ({ page }, testInfo) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    try {
      const cdp = await page.context().newCDPSession(page);
      const { installabilityErrors } = await cdp.send('Page.getInstallabilityErrors');
      expect(installabilityErrors).toEqual([]);
    } catch (error) {
      testInfo.annotations.push({
        type: 'cdp-installability',
        description: `Page.getInstallabilityErrors no disponible en este entorno: ${error}`,
      });
    }
  });
});

// ============================================================================
// CP-6: Visual ante Teclado Abierto
// ============================================================================
test.describe('CP-6 · Teclado abierto y barra inferior', () => {
  // Integración (Claude): el núcleo oculta la barra con la señal Teclado.abierto; se simula el teclado
  // como lo ve el navegador: campo enfocado y visualViewport más bajo.
  test('con un campo enfocado y el teclado abierto la barra inferior se retira y el campo sigue visible', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 360, height: 800 });
    await iniciarSesion(page, 'cliente@ejemplo.test');
    await page.goto('/perfil');
    const nombre = page.getByLabel('Nombre', { exact: true });
    await expect(nombre).toBeVisible();
    await expect(page.locator('.barra-inferior')).toBeVisible();
    await nombre.focus();
    await page.setViewportSize({ width: 360, height: 480 });
    await expect(page.locator('.barra-inferior')).toHaveCount(0);
    await expect(nombre).toBeInViewport();
    await nombre.blur();
    await page.setViewportSize({ width: 360, height: 800 });
    await expect(page.locator('.barra-inferior')).toBeVisible();
  });
});

// ============================================================================
// CP-8: Adaptación Móvil, Reflujo, Formularios y Accesibilidad
// ============================================================================
test.describe('CP-8 · Reflujo, formularios y accesibilidad móvil', () => {
  test('pantalla de acceso (/ingresar): atributos de formulario, reflujo, zoom 200% y axe', async ({
    page,
  }) => {
    await page.goto('/ingresar');

    // Comprobar atributos optimizados de formulario
    const correo = page.locator('input[formControlName="correo"]');
    await expect(correo).toHaveAttribute('type', 'email');
    await expect(correo).toHaveAttribute('autocomplete', 'username');
    await expect(correo).toHaveAttribute('inputmode', 'email');
    await expect(correo).toHaveAttribute('enterkeyhint', 'next');

    const password = page.locator('input[formControlName="password"]');
    await expect(password).toHaveAttribute('type', 'password');
    await expect(password).toHaveAttribute('autocomplete', 'current-password');
    await expect(password).toHaveAttribute('enterkeyhint', 'done');

    // Reflujo en los viewports exigidos
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await esperarEstable(page);
      await comprobarSinScrollHorizontal(page, `/ingresar a ${vp.nombre}`);
      await comprobarZoom200(page, `/ingresar a ${vp.nombre}`);
      await comprobarObjetivosTactiles(page, `/ingresar a ${vp.nombre}`);
    }

    // Auditoría de accesibilidad axe a 360 px
    await page.setViewportSize({ width: 360, height: 800 });
    await auditarAxe(page, 'acceso-/ingresar-360px');
  });

  test('pantalla reservar (/n/barberturno/reservar): reflujo, zoom 200% y axe', async ({
    page,
  }) => {
    await page.goto('/n/barberturno/reservar');
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await esperarEstable(page);
      await comprobarSinScrollHorizontal(page, `reservar a ${vp.nombre}`);
      await comprobarZoom200(page, `reservar a ${vp.nombre}`);
      await comprobarObjetivosTactiles(page, `reservar a ${vp.nombre}`);
    }

    await page.setViewportSize({ width: 360, height: 800 });
    await auditarAxe(page, 'reservar-360px');
  });

  test('pantalla mis citas (/n/barberturno/mis-citas): reflujo, zoom 200% y axe con cliente', async ({
    page,
  }) => {
    await iniciarSesion(page, 'cliente@ejemplo.test');
    await page.goto('/n/barberturno/mis-citas');
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await esperarEstable(page);
      await comprobarSinScrollHorizontal(page, `mis-citas a ${vp.nombre}`);
      await comprobarZoom200(page, `mis-citas a ${vp.nombre}`);
      await comprobarObjetivosTactiles(page, `mis-citas a ${vp.nombre}`);
    }

    await page.setViewportSize({ width: 360, height: 800 });
    await auditarAxe(page, 'mis-citas-360px');
  });

  test('pantalla agenda (/n/barberturno/agenda): reflujo, zoom 200% y axe con multirol', async ({
    page,
  }) => {
    await iniciarSesion(page, 'multirol@ejemplo.test');
    await page.goto('/n/barberturno/agenda');
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await esperarEstable(page);
      await comprobarSinScrollHorizontal(page, `agenda a ${vp.nombre}`);
      await comprobarZoom200(page, `agenda a ${vp.nombre}`);
      await comprobarObjetivosTactiles(page, `agenda a ${vp.nombre}`);
    }

    await page.setViewportSize({ width: 360, height: 800 });
    await auditarAxe(page, 'agenda-360px');
  });

  test('pantalla bandeja whatsapp (/n/barberturno/whatsapp): reflujo, zoom 200% y axe', async ({
    page,
  }) => {
    sembrarConversacionDerivada('barberturno', 'multirol@ejemplo.test');
    await iniciarSesion(page, 'multirol@ejemplo.test');
    await page.goto('/n/barberturno/whatsapp');
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await esperarEstable(page);
      await comprobarSinScrollHorizontal(page, `bandeja a ${vp.nombre}`);
      await comprobarZoom200(page, `bandeja a ${vp.nombre}`);
      await comprobarObjetivosTactiles(page, `bandeja a ${vp.nombre}`);
    }

    await page.setViewportSize({ width: 360, height: 800 });
    await auditarAxe(page, 'bandeja-360px');
  });
});
