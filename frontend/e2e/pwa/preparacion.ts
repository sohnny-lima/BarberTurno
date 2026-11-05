import { randomBytes } from 'node:crypto';
import { BASE_E2E, comprobarSinSesiones, sql, vaciar, validarBase } from '../base-datos';

/** El cerrojo externo entrega credenciales por entorno; este arnés nunca lee .local. */
export default async function preparar() {
  validarBase();
  if (!process.env['BT_DB_PASSWORD'] || !process.env['BT_FLYWAY_PASSWORD'])
    throw new Error('Faltan las credenciales E2E en el entorno del cerrojo.');
  comprobarSinSesiones();
  const rol = sql(
    `SELECT current_database() = '${BASE_E2E}' AND current_user = 'barberturno_v2_app' AND NOT rolsuper AND NOT rolbypassrls FROM pg_roles WHERE rolname = current_user`,
  );
  if (rol.status !== 0 || rol.stdout.trim() !== 't') throw new Error('Rol E2E inválido.');
  process.env['BT_E2E_DB'] = BASE_E2E;
  process.env['BT_DB_URL'] = `jdbc:postgresql://localhost:5433/${BASE_E2E}`;
  process.env['BT_DEMO_PASSWORD'] = `Demo1${randomBytes(18).toString('hex')}`;
  process.env['BT_ADMIN_CORREO'] = `admin-${randomBytes(8).toString('hex')}@ejemplo.test`;
  process.env['BT_ADMIN_NOMBRE'] = 'Administrador ficticio PWA';
  process.env['BT_ADMIN_PASSWORD'] = process.env['BT_DEMO_PASSWORD'];
  process.env['BT_JWT_SECRET'] = randomBytes(48).toString('base64');
  process.env['BT_COOKIE_SECURE'] = 'false';
  vaciar();
  return () => vaciar();
}
