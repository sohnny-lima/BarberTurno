import { defineConfig } from 'vitest/config';

// En este equipo los forks tardan más que el límite de arranque de Vitest.
// Se conserva el aislamiento y todas las pruebas; solo cambia el transporte del worker.
export default defineConfig({ test: { pool: 'threads', maxWorkers: 1 } });
