import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

// Caché local: permite ejecutar en equipos cuyo disco del sistema tenga poco espacio.
process.env['PLAYWRIGHT_BROWSERS_PATH'] ??= resolve('tmp/navegadores');

export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/recorrido.spec.ts', '**/v2-11-bandeja.spec.ts'],
  globalSetup: './e2e/preparacion.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { outputFolder: 'tmp/e2e/informe', open: 'never' }]],
  use: {
    baseURL: 'http://localhost:8081',
    // V2-18: el build de producción registra un service worker y page.route no intercepta lo que pasa
    // por él. Esta batería prueba la aplicación, no la PWA (cubierta por playwright.pwa.config.ts).
    serviceWorkers: 'block',
    timezoneId: 'Europe/Madrid',
    locale: 'es-PE',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  projects: ['chromium', 'firefox'].flatMap((browserName) =>
    [
      { width: 1440, height: 900 },
      { width: 360, height: 800 },
    ].map((viewport) => ({
      name: `${browserName}-${viewport.width}`,
      use: { browserName: browserName as 'chromium' | 'firefox', viewport },
    })),
  ),
});
