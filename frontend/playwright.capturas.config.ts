import { defineConfig } from '@playwright/test';
import base from './playwright.config';
export default defineConfig({
  ...base,
  testMatch: '**/capturas.spec.ts',
  reporter: [['list']],
});
