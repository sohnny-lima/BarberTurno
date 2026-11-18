import { chromium } from '@playwright/test';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
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
 * @param {string | undefined} rutaDestino - Ruta absoluta del PNG; omitir para devolverlo en memoria
 * @param {number} ancho - Ancho en píxeles
 * @param {number} alto - Alto en píxeles
 * @param {boolean} fondoOpaco - Si debe forzar fondo opaco (#f6f8f7)
 * @returns {Promise<Buffer>} PNG rasterizado para guardarlo o incluirlo en el favicon
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
  const png = await page.screenshot({
    type: 'png',
    path: rutaDestino,
    omitBackground: !fondoOpaco,
    animations: 'disabled',
  });
  console.log(`[OK] Generado: ${rutaDestino ?? 'imagen para favicon.ico'} (${ancho}x${alto})`);
  return png;
}

try {
  // 1. Iconos estándar PWA (any)
  await rasterizar(svgIcono, resolve(iconosDir, 'icono-192.png'), 192, 192, true);
  await rasterizar(svgIcono, resolve(iconosDir, 'icono-512.png'), 512, 512, true);

  // 2. Icono adaptable PWA (maskable) con fondo a sangre opaco
  await rasterizar(svgMaskable, resolve(iconosDir, 'icono-maskable-512.png'), 512, 512, true);

  // 3. Icono de pantalla de inicio Apple (180x180, fondo opaco obligatorio)
  await rasterizar(svgIcono, resolve(iconosDir, 'apple-touch-icon.png'), 180, 180, true);

  // 4. Contenedor ICO: cabecera de 6 bytes, directorio de 16 por imagen y PNG de 32 bits.
  const tamanos = [16, 32, 48];
  const cabecera = Buffer.alloc(6 + 16 * tamanos.length);
  cabecera.writeUInt16LE(1, 2); // Tipo 1: icono.
  cabecera.writeUInt16LE(tamanos.length, 4);
  const imagenes = [];
  let desplazamiento = cabecera.length;
  for (const [indice, tamano] of tamanos.entries()) {
    const png = await rasterizar(svgIcono, undefined, tamano, tamano, true);
    const entrada = 6 + indice * 16;
    cabecera.writeUInt8(tamano, entrada);
    cabecera.writeUInt8(tamano, entrada + 1);
    cabecera.writeUInt16LE(1, entrada + 4); // Un plano.
    cabecera.writeUInt16LE(32, entrada + 6);
    cabecera.writeUInt32LE(png.length, entrada + 8);
    cabecera.writeUInt32LE(desplazamiento, entrada + 12);
    imagenes.push(png);
    desplazamiento += png.length;
  }
  writeFileSync(resolve(frontendDir, 'public/favicon.ico'), Buffer.concat([cabecera, ...imagenes]));
  console.log('[OK] Generado: public/favicon.ico (16x16, 32x32 y 48x48)');
} finally {
  await browser.close();
}
console.log('Todos los iconos PNG y el favicon ICO se han generado correctamente.');
