import { test as base, expect, request, type APIRequestContext } from '@playwright/test';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { resolve } from 'node:path';
import { vaciar } from './base-datos';

export const test = base.extend<object, { servidor: string }>({
  servidor: [
    async ({ browserName }, use) => {
      expect(['chromium', 'firefox']).toContain(browserName);
      vaciar();
      const java = resolve(process.env['JAVA_HOME']!, 'bin/java.exe');
      const jar = resolve('../backend/target/barberturno-0.0.1-SNAPSHOT.jar');
      const proceso = spawn(
        java,
        [
          '-jar',
          jar,
          '--spring.profiles.active=dev,demo',
          '--server.port=18034',
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
        const limite = Date.now() + 90_000;
        while (true) {
          if (terminado) throw new Error('El jar E2E terminó antes de estar listo.');
          try {
            const respuesta = await fetch('http://localhost:18034/actuator/health', {
              headers: { Accept: 'application/json' },
            });
            if (respuesta.ok) {
              const catalogo = await fetch('http://localhost:18034/api/barberos');
              // health responde mientras los ApplicationRunner siguen cargando.
              if (catalogo.ok && (await catalogo.json()).length === 2) break;
            }
          } catch {
            /* El puerto aún no está listo. */
          }
          if (Date.now() > limite) throw new Error('El jar E2E no estuvo listo en 90 s.');
          await new Promise((continuar) => setTimeout(continuar, 300));
        }
        await use('http://localhost:18034');
      } finally {
        if (!terminado) {
          const salida = once(proceso, 'exit');
          proceso.kill();
          await salida;
        }
        vaciar();
      }
    },
    { scope: 'worker', timeout: 120_000 },
  ],
  page: async ({ page, servidor }, use) => {
    expect(servidor).toBe('http://localhost:18034');
    // Solo Date: animaciones, temporizadores y peticiones continúan funcionando.
    await page.clock.setFixedTime(new Date('2026-09-28T09:00:00-05:00'));
    await use(page);
  },
});
export { expect };

export async function sesionApi(correo: string) {
  const api = await request.newContext({ baseURL: 'http://localhost:18034' });
  await api.get('/api/auth/sesion');
  await escribir(api, '/api/auth/login', { correo, password: process.env['BT_DEMO_PASSWORD'] });
  await api.get('/api/auth/sesion');
  return api;
}

export async function escribir(api: APIRequestContext, ruta: string, data: unknown) {
  const estado = await api.storageState();
  const token = estado.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  const respuesta = await api.post(ruta, {
    data,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token ?? '') },
  });
  if (!respuesta.ok())
    throw new Error(`La preparación API ${ruta} devolvió ${respuesta.status()}.`);
  return respuesta;
}
