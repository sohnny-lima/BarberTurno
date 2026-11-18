import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

process.env['PLAYWRIGHT_BROWSERS_PATH'] ??= resolve('tmp/navegadores');

export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/pwa/**/*.spec.ts'],
  globalSetup: process.env['BT_PWA_SIN_BD'] === '1' ? undefined : './e2e/pwa/preparacion.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4301',
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: {
    command: 'node e2e/pwa/servidor.mjs',
    url: 'http://localhost:4301',
    reuseExistingServer: false,
  },
  projects: [360, 1440].map((ancho) => ({
    name: `chromium-${ancho}`,
    use: { browserName: 'chromium', viewport: { width: ancho, height: ancho === 360 ? 800 : 900 } },
  })),
});
