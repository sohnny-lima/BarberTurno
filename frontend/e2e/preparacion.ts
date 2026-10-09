import { randomBytes } from 'node:crypto';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, unlinkSync } from 'node:fs';
import { resolve } from 'node:path';
import { createConnection } from 'node:net';
import { sql, vaciar } from './base-datos';

async function puertoLibre(puerto: number) {
  const ocupado = await new Promise<boolean>((terminar) => {
    const socket = createConnection({ port: puerto, host: '127.0.0.1' });
    socket.once('connect', () => {
      socket.destroy();
      terminar(true);
    });
    socket.once('error', () => terminar(false));
  });
  if (ocupado) throw new Error(`Puerto ${puerto} ocupado: detenga la aplicación antes de los E2E.`);
}

/** Importa secretos únicamente al entorno; no escribe estados de sesión ni credenciales. */
export default async function preparar() {
  mkdirSync(resolve('tmp'), { recursive: true });
  const ruta = resolve('tmp/e2e.lock');
  const bloqueo = openSync(ruta, 'wx');
  let preparada = false;
  const limpiar = () => {
    try {
      if (preparada) vaciar();
    } finally {
      closeSync(bloqueo);
      unlinkSync(ruta);
    }
  };
  try {
    const archivo = resolve('../.local/barberturno.env');
    if (existsSync(archivo)) {
      for (const linea of readFileSync(archivo, 'utf8').split(/\r?\n/)) {
        const entrada = /^([A-Z][A-Z_]+)=(.*)$/.exec(linea);
        if (entrada && !process.env[entrada[1]]) process.env[entrada[1]] = entrada[2];
      }
    }
    if (!process.env['BT_DB_PASSWORD'])
      throw new Error('Falta BT_DB_PASSWORD en el entorno o archivo local.');
    await puertoLibre(8080);
    await puertoLibre(18034);
    const conexiones = sql(
      "SELECT count(*) FROM pg_stat_activity WHERE datname = 'barberturno_test' AND pid <> pg_backend_pid()",
      'barberturno_test',
    );
    if (conexiones.status !== 0 || conexiones.stdout.trim() !== '0')
      throw new Error(
        'Hay conexiones de otra suite PostgreSQL o no se pudo comprobar su ausencia.',
      );
    process.env['BT_E2E_DB'] = 'barberturno_test';
    process.env['BT_DB_URL'] = `jdbc:postgresql://localhost:5433/${process.env['BT_E2E_DB']}`;
    // Cada ejecución usa una clave ficticia distinta y efímera para todas las cuentas demo.
    process.env['BT_DEMO_PASSWORD'] = `Demo1${randomBytes(18).toString('hex')}`;
    process.env['BT_ADMIN_CORREO'] = 'admin-e2e@ejemplo.test';
    process.env['BT_ADMIN_NOMBRE'] = 'Administrador E2E';
    process.env['BT_ADMIN_PASSWORD'] = process.env['BT_DEMO_PASSWORD'];
    process.env['BT_JWT_SECRET'] = randomBytes(48).toString('base64');
    process.env['BT_COOKIE_SECURE'] = 'false';
    vaciar();
    preparada = true;
    console.log(
      `Base E2E: ${process.env['BT_E2E_DB']}; limpieza antes y después de cada proyecto.`,
    );
    return limpiar;
  } catch (error) {
    limpiar();
    throw error;
  }
}
