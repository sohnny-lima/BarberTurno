import { randomBytes } from 'node:crypto';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, unlinkSync } from 'node:fs';
import { resolve } from 'node:path';
import { createConnection } from 'node:net';
import { BASE_E2E, sql, vaciar, validarBase, comprobarSinSesiones } from './base-datos';

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
  validarBase();
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
    if (!process.env['BT_DB_PASSWORD'] || !process.env['BT_FLYWAY_PASSWORD'])
      throw new Error('Faltan las credenciales de aplicación o de limpieza en el entorno local.');
    process.env['BT_DB_USER'] ??= 'barberturno_v2_app';
    process.env['BT_FLYWAY_USER'] ??= 'barberturno_v2';
    validarBase();
    await puertoLibre(8081);
    comprobarSinSesiones();
    const rol =
      sql(`SELECT current_database() = '${BASE_E2E}' AND current_user = 'barberturno_v2_app'
      AND NOT rolsuper AND NOT rolbypassrls FROM pg_roles WHERE rolname = current_user`);
    if (rol.status !== 0 || rol.stdout.trim() !== 't') throw new Error('Rol real E2E inválido.');
    process.env['BT_E2E_DB'] = BASE_E2E;
    process.env['BT_DB_URL'] = `jdbc:postgresql://localhost:5433/${process.env['BT_E2E_DB']}`;
    // Cada ejecución usa una clave ficticia distinta y efímera para todas las cuentas demo.
    process.env['BT_DEMO_PASSWORD'] = `Demo1${randomBytes(18).toString('hex')}`;
    const alias = randomBytes(8).toString('hex');
    process.env['BT_ADMIN_CORREO'] = `admin-${alias}@ejemplo.test`;
    process.env['BT_ADMIN_NOMBRE'] = `Administrador E2E ${alias}`;
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
