import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

process.env['PLAYWRIGHT_BROWSERS_PATH'] ??= resolve('tmp/navegadores');

const baseURL = process.env['BT_DEMO_URL'];
if (!baseURL || baseURL.trim() === '') {
  throw new Error('La variable de entorno BT_DEMO_URL es obligatoria para las pruebas de aceptación de la demo.');
}

const password = process.env['BT_DEMO_PASSWORD'];
if (!password || password.trim() === '') {
  throw new Error('La variable de entorno BT_DEMO_PASSWORD es obligatoria.');
}

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/demo/**/*.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  use: {
    baseURL,
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    serviceWorkers: 'allow',
  },
  projects: [
    {
      name: 'chromium-360',
      use: {
        browserName: 'chromium',
        viewport: { width: 360, height: 800 },
      },
    },
    {
      name: 'chromium-1440',
      use: {
        browserName: 'chromium',
        viewport: { width: 1440, height: 900 },
      },
    },
  ],
});
