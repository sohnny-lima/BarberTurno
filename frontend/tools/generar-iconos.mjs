import { chromium } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const frontendDir = resolve(__dirname, '..');

process.env['PLAYWRIGHT_BROWSERS_PATH'] ??= resolve(frontendDir, 'tmp/navegadores');

const iconosDir = resolve(frontendDir, 'public/iconos');
mkdirSync(iconosDir, { recursive: true });

const svgIcono = readFileSync(resolve(iconosDir, 'icono.svg'), 'utf8');
const svgMaskable = readFileSync(resolve(iconosDir, 'icono-maskable.svg'), 'utf8');

const browser = await chromium.launch();
const page = await browser.newPage();

/**
 * Rasteriza un SVG a PNG con las dimensiones especificadas usando Chromium.
 *
 * @param {string} svgContenido - Contenido del archivo SVG
 * @param {string} rutaDestino - Ruta absoluta del archivo PNG a generar
 * @param {number} ancho - Ancho en píxeles
 * @param {number} alto - Alto en píxeles
 * @param {boolean} fondoOpaco - Si debe forzar fondo opaco (#f6f8f7)
 */
async function rasterizar(svgContenido, rutaDestino, ancho, alto, fondoOpaco = true) {
  await page.setViewportSize({ width: ancho, height: alto });
  const html = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body {
    width: ${ancho}px;
    height: ${alto}px;
    overflow: hidden;
    background: ${fondoOpaco ? '#f6f8f7' : 'transparent'};
  }
  svg {
    width: 100%;
    height: 100%;
    display: block;
  }
</style>
</head>
<body>
${svgContenido}
</body>
</html>`;
  await page.setContent(html, { waitUntil: 'load' });
  await page.screenshot({
    path: rutaDestino,
    omitBackground: !fondoOpaco,
    animations: 'disabled',
  });
  console.log(`[OK] Generado: ${rutaDestino} (${ancho}x${alto})`);
}

// 1. Iconos estándar PWA (any)
await rasterizar(svgIcono, resolve(iconosDir, 'icono-192.png'), 192, 192, true);
await rasterizar(svgIcono, resolve(iconosDir, 'icono-512.png'), 512, 512, true);

// 2. Icono adaptable PWA (maskable) con fondo a sangre opaco
await rasterizar(svgMaskable, resolve(iconosDir, 'icono-maskable-512.png'), 512, 512, true);

// 3. Icono de pantalla de inicio Apple (180x180, fondo opaco obligatorio)
await rasterizar(svgIcono, resolve(iconosDir, 'apple-touch-icon.png'), 180, 180, true);

await browser.close();
console.log('Todos los iconos PNG se han generado correctamente.');
