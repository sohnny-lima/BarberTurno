import { defineConfig } from '@playwright/test';
import base from './playwright.config';

export default defineConfig({
  ...base,
  testMatch: '**/v2-07-capturas.spec.ts',
  reporter: [['list']],
});
