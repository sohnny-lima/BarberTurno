import { defineConfig } from 'vitest/config';

// Un worker mantiene el aislamiento y reduce la contención al arrancar en Windows.
export default defineConfig({ test: { pool: 'forks', maxWorkers: 1 } });
