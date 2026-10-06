import { spawnSync } from 'node:child_process';

const tablas =
  'notificacion, auditoria_reserva, reserva, bloqueo, jornada, barbero, servicio, usuario';

/** Solo admite barberturno_test; nunca crea bases ni usa la de desarrollo. */
export function sql(sentencia: string, base = process.env['BT_E2E_DB'] ?? 'barberturno_test') {
  if (base !== 'barberturno_test') throw new Error('Base E2E no autorizada.');
  return spawnSync(
    process.env['BT_E2E_PSQL'] ?? 'C:/Program Files/PostgreSQL/18/bin/psql.exe',
    [
      '-X',
      '-w',
      '-h',
      'localhost',
      '-p',
      '5433',
      '-U',
      process.env['BT_DB_USER'] ?? 'barberturno',
      '-d',
      base,
      '-v',
      'ON_ERROR_STOP=1',
      '-At',
      '-c',
      sentencia,
    ],
    { env: { ...process.env, PGPASSWORD: process.env['BT_DB_PASSWORD'] }, encoding: 'utf8' },
  );
}

export function vaciar() {
  const existe = sql("SELECT to_regclass('public.usuario') IS NOT NULL");
  if (existe.status !== 0) throw new Error('No se pudo consultar la base de prueba.');
  if (existe.stdout.trim() !== 't') return;
  const resultado = sql(`TRUNCATE ${tablas} RESTART IDENTITY CASCADE`);
  if (resultado.status !== 0) throw new Error('No se pudo vaciar la base de prueba autorizada.');
}
