import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

// Caché local: permite ejecutar en equipos cuyo disco del sistema tenga poco espacio.
process.env['PLAYWRIGHT_BROWSERS_PATH'] ??= resolve('tmp/navegadores');

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/recorrido.spec.ts',
  globalSetup: './e2e/preparacion.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { outputFolder: '../docs/pruebas/t-52/e2e', open: 'never' }]],
  use: {
    baseURL: 'http://localhost:18034',
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
