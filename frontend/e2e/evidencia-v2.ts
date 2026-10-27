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

/**
 * Revisión de los nodos de contraste que axe deja «incompletos» (no puede decidir porque otro
 * elemento, p. ej. el contorno de un campo de Material, se superpone al texto). Calcula el
 * contraste con el color del texto y el fondo efectivo de sus antecesores, componiendo las
 * transparencias y la opacidad. Se registra en la evidencia; no hace fallar la captura.
 */
function medirContraste(selectores: string[]) {
  const lienzo = document.createElement('canvas').getContext('2d', { willReadFrequently: true })!;
  const rgba = (color: string): number[] => {
    lienzo.clearRect(0, 0, 1, 1);
    lienzo.fillStyle = 'rgba(0, 0, 0, 0)';
    lienzo.fillStyle = color;
    lienzo.fillRect(0, 0, 1, 1);
    const [r, g, b, a] = lienzo.getImageData(0, 0, 1, 1).data;
    return [r, g, b, a / 255];
  };
  const componer = (arriba: number[], abajo: number[]) =>
    [0, 1, 2].map((i) => arriba[i] * arriba[3] + abajo[i] * (1 - arriba[3])).concat(1);
  const luminancia = (c: number[]) =>
    c
      .slice(0, 3)
      .map((v) => v / 255)
      .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
      .reduce((suma, v, i) => suma + v * [0.2126, 0.7152, 0.0722][i], 0);
  const hex = (c: number[]) =>
    '#' +
    c
      .slice(0, 3)
      .map((v) => Math.round(v).toString(16).padStart(2, '0'))
      .join('');
  return selectores.map((selector) => {
    const el = document.querySelector(selector) as HTMLElement | null;
    if (!el) return { selector, error: 'no encontrado' };
    const capas: number[][] = [];
    let imagen = false;
    let opacidad = 1;
    for (let nodo: HTMLElement | null = el; nodo; nodo = nodo.parentElement) {
      const propio = getComputedStyle(nodo);
      opacidad *= Number(propio.opacity);
      if (propio.backgroundImage !== 'none') imagen = true;
      const capa = rgba(propio.backgroundColor);
      if (capa[3] > 0) capas.push(capa);
      if (capa[3] >= 1) break;
    }
    const fondo = capas
      .reverse()
      .reduce((abajo, arriba) => componer(arriba, abajo), [255, 255, 255, 1]);
    const estilo = getComputedStyle(el);
    const texto = rgba(estilo.color);
    texto[3] *= opacidad;
    const color = componer(texto, fondo);
    const [l1, l2] = [luminancia(color), luminancia(fondo)].sort((a, b) => b - a);
    const ratio = Math.round(((l1 + 0.05) / (l2 + 0.05)) * 100) / 100;
    const tamano = parseFloat(estilo.fontSize);
    const grande = tamano >= 24 || (tamano >= 18.66 && Number(estilo.fontWeight) >= 700);
    const exento = !!el.closest(
      ':disabled, [aria-disabled="true"], .mat-mdc-form-field-disabled, .mdc-text-field--disabled, .mat-mdc-button-disabled, .mdc-switch--disabled, .mat-button-toggle-disabled',
    );
    const componente = el.matches('input, textarea')
      ? 'Valor de campo'
      : el.closest('.mdc-floating-label, mat-label')
        ? 'Etiqueta de campo'
        : el.closest('mat-error')
          ? 'Error de campo'
          : el.closest('mat-hint, .mat-mdc-form-field-hint')
            ? 'Pista de campo'
            : el.closest(
                  '.mat-mdc-select-value, .mat-mdc-select-min-line, .mat-mdc-select-placeholder',
                )
              ? 'Valor de selector'
              : el.closest('.mat-button-toggle')
                ? 'Grupo conmutable'
                : el.closest('.mat-mdc-dialog-title')
                  ? 'Título de diálogo'
                  : el.closest('.mat-mdc-button-base')
                    ? 'Botón'
                    : el.tagName.toLowerCase() + '.' + (el.classList[0] ?? '');
    const estado = exento
      ? 'desactivado'
      : el.closest('.mat-form-field-invalid')
        ? 'con error'
        : el.closest('.mat-focused')
          ? 'con foco'
          : 'normal';
    return {
      selector,
      componente,
      estado,
      color: hex(color),
      fondo: hex(fondo),
      ratio,
      minimo: grande ? 3 : 4.5,
      exento,
      cumple: exento || ratio >= (grande ? 3 : 4.5),
      imagen,
    };
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
    const contrasteIncompleto = axe.incomplete.find((v) => v.id === 'color-contrast');
    const contrasteManual = contrasteIncompleto
      ? await page.evaluate(
          medirContraste,
          contrasteIncompleto.nodes.map((n) => n.target.join(' ')),
        )
      : [];
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
    // Contención: con un diálogo o el menú móvil abiertos, Tab y Shift+Tab nunca sacan el foco ni
    // lo dejan en las anclas de CDK o en el salto al contenido (aria-hidden-focus de axe). Los menús
    // de Material se cierran con Tab por diseño y no se recorren. El foco vuelve al control inicial.
    let contencion: { ambito: string; pulsaciones: number } | null = null;
    const ambito = (await page.getByRole('dialog').isVisible())
      ? '[role="dialog"]'
      : cajon
        ? 'mat-sidenav'
        : null;
    if (ambito) {
      const pulsaciones =
        (await page
          .locator(ambito)
          .first()
          .locator('a[href], button:enabled, input:enabled, textarea:enabled, [tabindex="0"]')
          .filter({ visible: true })
          .count()) + 2;
      const escapes: string[] = [];
      for (const tecla of ['Tab', 'Shift+Tab'])
        for (let i = 1; i <= pulsaciones; i++) {
          await page.keyboard.press(tecla);
          const fuera = await page.evaluate((selector) => {
            const activo = document.activeElement;
            const valido =
              !!activo?.closest(selector) &&
              !activo.classList.contains('cdk-focus-trap-anchor') &&
              !activo.classList.contains('saltar');
            return valido ? null : activo ? activo.tagName + '.' + activo.className : 'ninguno';
          }, ambito);
          if (fuera) escapes.push(tecla + ' ' + i + ': ' + fuera);
        }
      expect(escapes, nombre + ': el foco sale de ' + ambito).toEqual([]);
      await control.focus();
      contencion = { ambito, pulsaciones };
    }
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
          contencion,
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
              nodos: v.nodes.map((n) => n.target.join(' ')),
            })),
            contrasteManual,
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
