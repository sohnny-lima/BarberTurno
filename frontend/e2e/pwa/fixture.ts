// Variante PWA del arnés existente: más margen de arranque para este equipo, mismas guardas y limpieza.
import { test as base, expect } from '@playwright/test';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { resolve } from 'node:path';
import { vaciar } from '../base-datos';

/** Tras un fallo, el jar del worker anterior puede tardar en soltar sus conexiones: se reintenta el vaciado. */
async function vaciarConEspera() {
  for (let intento = 1; ; intento++) {
    try {
      vaciar();
      return;
    } catch (error) {
      if (intento >= 20) throw error;
      await new Promise((continuar) => setTimeout(continuar, 500));
    }
  }
}

export const test = base.extend<object, { servidor: string }>({
  servidor: [
    async ({ browserName }, use) => {
      expect(['chromium', 'firefox']).toContain(browserName);
      await vaciarConEspera();
      const java = resolve(process.env['JAVA_HOME']!, 'bin/java.exe');
      const jar = resolve('../backend/target/barberturno-0.0.1-SNAPSHOT.jar');
      const proceso = spawn(
        java,
        [
          '-jar',
          jar,
          '--spring.profiles.active=dev,demo',
          '--server.port=8081',
          '--barberturno.reloj-fijo=2026-09-28T09:00:00-05:00',
          '--logging.level.pe.barberturno=INFO',
        ],
        {
          cwd: resolve('..'),
          env: process.env,
          stdio: 'ignore',
          windowsHide: true,
        },
      );
      let terminado = false;
      proceso.once('exit', () => {
        terminado = true;
      });
      try {
        const limite = Date.now() + 180_000;
        let salud = 0,
          estadoCatalogo = 0,
          cantidad = -1;
        while (true) {
          if (terminado) throw new Error('El jar E2E terminó antes de estar listo.');
          try {
            const respuesta = await fetch('http://localhost:8081/actuator/health', {
              headers: { Accept: 'application/json' },
              signal: AbortSignal.timeout(5000),
            });
            salud = respuesta.status;
            if (respuesta.ok) {
              const catalogo = await fetch('http://localhost:8081/api/n/sur/barberos', {
                signal: AbortSignal.timeout(5000),
              });
              // health responde mientras los ApplicationRunner siguen cargando.
              estadoCatalogo = catalogo.status;
              cantidad = catalogo.ok ? (await catalogo.json()).length : -1;
              if (cantidad === 1) break;
            }
          } catch {
            /* El puerto aún no está listo. */
          }
          if (Date.now() > limite)
            throw new Error(
              `El jar E2E no estuvo listo en 180 s (salud=${salud}, catálogo=${estadoCatalogo}, cantidad=${cantidad}).`,
            );
          await new Promise((continuar) => setTimeout(continuar, 300));
        }
        await use('http://localhost:8081');
      } finally {
        if (!terminado) {
          const salida = once(proceso, 'exit');
          proceso.kill();
          await salida;
        }
        vaciar();
      }
    },
    { scope: 'worker', timeout: 210_000 },
  ],
  page: async ({ page, servidor }, use) => {
    expect(servidor).toBe('http://localhost:8081');
    // Solo Date: animaciones, temporizadores y peticiones continúan funcionando.
    await page.clock.setFixedTime(new Date('2026-09-28T09:00:00-05:00'));
    await use(page);
  },
});
export { expect };
