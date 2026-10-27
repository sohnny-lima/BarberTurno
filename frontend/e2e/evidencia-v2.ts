import AxeBuilder from '@axe-core/playwright';
import { expect, type Page, type TestInfo } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

/** Comprueba la disposición real antes de que fullPage pueda cambiar el área de captura. */
async function comprobarDisposicion(page: Page, ancho: number) {
  expect(await page.evaluate(() => innerWidth)).toBe(ancho);
  expect(await page.evaluate(() => matchMedia('(max-width: 767.98px)').matches)).toBe(ancho < 768);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
  if (ancho >= 768) {
    await expect(page.locator('.barra-inferior')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Abrir menú', exact: true })).toHaveCount(0);
    await expect
      .poll(
        async () => {
          const lateral = page.locator('mat-sidenav');
          if (!(await lateral.isVisible())) return true;
          const riel = await lateral.boundingBox();
          const contenido = await page.locator('mat-sidenav-content').boundingBox();
          return !!riel && !!contenido && contenido.x >= riel.x + riel.width - 1;
        },
        { message: 'El menú lateral no debe superponerse al contenido de escritorio.' },
      )
      .toBe(true);
  }
}

/** Fuentes, imágenes y animaciones finitas terminadas: axe y la captura miden el estado estable. */
async function esperarEstable(page: Page) {
  await page.evaluate(async () => {
    // Dos fotogramas: una transición recién provocada ya figura en getAnimations().
    await new Promise((listo) => requestAnimationFrame(() => requestAnimationFrame(listo)));
    await document.fonts.ready;
    await Promise.all(
      Array.from(document.images).map((imagen) => imagen.decode().catch(() => undefined)),
    );
    await Promise.all(
      document
        .getAnimations()
        .filter((animacion) => Number.isFinite(animacion.effect?.getComputedTiming().endTime))
        .map((animacion) => animacion.finished.catch(() => undefined)),
    );
  });
}

/** Captura y ficha de QA sin cookies, valores de formulario ni capacidades en los metadatos. */
export async function capturarV2(page: Page, info: TestInfo, nombre: string) {
  await page.bringToFront();
  // Sin hover accidental: al ampliar la altura el puntero quedaría sobre otro control.
  await page.mouse.move(0, 0);
  await expect(page.locator('main')).toBeVisible();
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  await expect(page.locator('main app-estado-contenido .esqueletos')).toHaveCount(0);
  // Los avisos transitorios de otros escenarios terminan antes de fotografiar el estado estable.
  // La ausencia inmediata del snackbar de suspensión se exige aparte en el recorrido 21.
  await expect(page.locator('mat-snack-bar-container')).toHaveCount(0);
  await esperarEstable(page);
  const original = page.viewportSize()!;
  await comprobarDisposicion(page, original.width);
  const paneles = page.locator('.cdk-overlay-pane').filter({ visible: true });
  const cajon = original.width < 768 && (await page.locator('mat-sidenav').isVisible());
  const superpuesta = (await paneles.count()) > 0 || cajon;
  try {
    if (!superpuesta) {
      // Ampliación explícita y sincronizada: evita el cambio implícito de viewport de fullPage.
      // Incluye el scroll interno que utiliza el shell móvil.
      for (let intento = 0; intento < 3; intento++) {
        const alto = await page.evaluate(() => {
          const contenido = document.querySelector('mat-sidenav-content')!;
          return Math.ceil(
            Math.max(
              document.documentElement.scrollHeight,
              contenido.scrollHeight + innerHeight - contenido.clientHeight,
            ),
          );
        });
        if (alto <= page.viewportSize()!.height) break;
        await page.setViewportSize({ width: original.width, height: alto });
        await page.evaluate(
          () => new Promise<void>((terminar) => requestAnimationFrame(() => terminar())),
        );
      }
    }
    await comprobarDisposicion(page, original.width);
    expect(await page.locator('.saltar').evaluate((el) => el.matches(':focus-visible'))).toBe(
      false,
    );
    await page.evaluate(() => {
      window.scrollTo(0, 0);
      document.querySelector('mat-sidenav-content')?.scrollTo(0, 0);
      document.querySelector('.mat-drawer-inner-container')?.scrollTo(0, 0);
    });
    await page.evaluate(
      () =>
        new Promise<void>((terminar) =>
          requestAnimationFrame(() => requestAnimationFrame(() => terminar())),
        ),
    );
    await comprobarDisposicion(page, original.width);
    expect(await page.evaluate(() => scrollY)).toBe(0);
    expect(
      await page.locator('.saltar').evaluate((el) => el.getBoundingClientRect().bottom),
    ).toBeLessThanOrEqual(0);
    await esperarEstable(page);
    const axe = await new AxeBuilder({ page }).analyze();
    const relevantes = axe.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(
      relevantes.map((v) => ({
        id: v.id,
        impacto: v.impact,
        nodos: v.nodes.map((n) => n.target.join(' ') + ' · ' + n.failureSummary),
      })),
      nombre,
    ).toEqual([]);
    const navegador = info.project.use.browserName ?? info.project.name.split('-')[0];
    const carpeta = resolve('../docs/pruebas/v2-07/capturas', navegador);
    mkdirSync(carpeta, { recursive: true });
    const archivo = `${original.width}-${nombre}`;
    await page.screenshot({
      path: resolve(carpeta, archivo + '.png'),
      fullPage: true,
      animations: 'disabled',
    });
    await comprobarDisposicion(page, original.width);
    // El foco con teclado se comprueba después de axe y de la captura: salir de un campo con
    // Shift+Tab lo marca como tocado y mostraría un error que la persona no ha provocado.
    const raiz =
      (await paneles.count()) > 0
        ? paneles.first()
        : cajon
          ? page.locator('mat-sidenav')
          : page.locator('main');
    let control = raiz
      .locator('button:enabled, [role="button"]:not([aria-disabled="true"])')
      .filter({ visible: true })
      .first();
    if (!(await control.count()))
      control = raiz
        .locator('a[href], input:enabled:not([type="hidden"])')
        .filter({ visible: true })
        .first();
    if (!(await control.count()))
      control = page.locator('a.enlace').filter({ visible: true }).first();
    // Una pantalla en solo lectura (V2-DA-49) a 360 px puede no tener controles activos en el
    // contenido: el foco se comprueba entonces en la cabecera («Abrir menú»), con la misma exigencia.
    if (!(await control.count()))
      control = page
        .getByRole('banner')
        .locator('button:enabled')
        .filter({ visible: true })
        .first();
    await expect(control).toBeVisible();
    await control.focus();
    await page.keyboard.press('Shift+Tab');
    await page.keyboard.press('Tab');
    await expect(control).toBeFocused();
    if (await page.getByRole('dialog').isVisible()) {
      expect(await page.evaluate(() => !!document.activeElement?.closest('[role="dialog"]'))).toBe(
        true,
      );
    }
    const foco = await control.evaluate((elemento) => {
      const estilo = getComputedStyle(elemento);
      return {
        etiqueta: elemento.tagName,
        clase: elemento.className,
        visible: elemento.matches(':focus-visible'),
        estilo: estilo.outlineStyle,
        ancho: estilo.outlineWidth,
        desfase: estilo.outlineOffset,
      };
    });
    const descripcion = `${nombre}: ${foco.etiqueta}.${foco.clase}`;
    expect(foco.visible, descripcion).toBe(true);
    expect(foco.estilo, descripcion).not.toBe('none');
    expect(parseFloat(foco.ancho), descripcion).toBeGreaterThanOrEqual(3);
    writeFileSync(
      resolve(carpeta, archivo + '.json'),
      JSON.stringify(
        {
          navegador,
          version: page.context().browser()!.version(),
          viewport: original,
          captura: page.viewportSize(),
          superpuesta,
          foco,
          ruta: new URL(page.url()).pathname.replace(/^\/invitacion\/.+/, '/invitacion/:token'),
          // Resultado completo: la prueba falla solo con serias o críticas (CG-6); el resto se
          // registra con sus nodos, y los incompletos son los que axe no pudo decidir solo.
          axe: {
            version: axe.testEngine.version,
            reglas: {
              superadas: axe.passes.length,
              violadas: axe.violations.length,
              incompletas: axe.incomplete.length,
              inaplicables: axe.inapplicable.length,
            },
            seriasOCriticas: relevantes.length,
            total: axe.violations.length,
            otras: axe.violations.map((v) => ({
              id: v.id,
              impacto: v.impact,
              nodos: v.nodes.map((n) => n.target.join(' ')),
            })),
            incompletas: axe.incomplete.map((v) => ({
              id: v.id,
              impacto: v.impact,
              nodos: v.nodes.length,
            })),
          },
          disposicion: 'sin desbordamiento; escritorio sin barra móvil ni superposición del riel',
        },
        null,
        2,
      ) + '\n',
      'utf8',
    );
  } finally {
    if (page.viewportSize()!.height !== original.height) await page.setViewportSize(original);
  }
}
