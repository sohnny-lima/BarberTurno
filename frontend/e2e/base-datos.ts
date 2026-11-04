import { spawnSync } from 'node:child_process';

export const BASE_E2E = 'barberturno_v2_test';

/** Rechaza también las bases de desarrollo y cualquier destino de v1. */
export function validarBase(base = process.env['BT_E2E_DB'] ?? BASE_E2E) {
  if (base !== BASE_E2E) throw new Error('Base E2E no autorizada.');
  return base;
}

function ejecutar(sentencia: string, propietario: boolean, base: string) {
  validarBase(base);
  const usuario = propietario ? process.env['BT_FLYWAY_USER'] : process.env['BT_DB_USER'];
  if (usuario !== (propietario ? 'barberturno_v2' : 'barberturno_v2_app'))
    throw new Error('Rol E2E no autorizado.');
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
      usuario,
      '-d',
      base,
      '-v',
      'ON_ERROR_STOP=1',
      '-At',
      '-c',
      sentencia,
    ],
    {
      env: {
        ...process.env,
        PGPASSWORD: process.env[propietario ? 'BT_FLYWAY_PASSWORD' : 'BT_DB_PASSWORD'],
      },
      encoding: 'utf8',
      windowsHide: true,
    },
  );
}

/** Las consultas de comprobación usan siempre el rol de la aplicación. */
export function sql(sentencia: string, base = validarBase()) {
  return ejecutar(sentencia, false, base);
}

export function comprobarSinSesiones() {
  const resultado = sql(`SELECT count(*) FROM pg_stat_activity
    WHERE datname = '${BASE_E2E}' AND pid <> pg_backend_pid()`);
  if (resultado.status !== 0 || resultado.stdout.trim() !== '0')
    throw new Error('Hay conexiones de otra suite PostgreSQL o no se pudo comprobar su ausencia.');
}

/** Excepción V2-DA-20: propietario exclusivamente para limpiar, sin eludir RLS. */
export function vaciar() {
  validarBase();
  comprobarSinSesiones();
  const resultado = ejecutar(
    `BEGIN;
    DO $$ BEGIN
      IF current_database() <> '${BASE_E2E}' OR current_user <> 'barberturno_v2'
        OR NOT EXISTS (SELECT FROM negocio WHERE id = 1 AND slug = 'barberturno')
        OR EXISTS (SELECT FROM pg_stat_activity WHERE datname = current_database()
          AND pid <> pg_backend_pid()) THEN
        RAISE EXCEPTION 'Destino o sesiones de limpieza E2E no autorizados';
      END IF;
    END $$;
    TRUNCATE operacion_pendiente, mensaje_whatsapp, conversacion,
      evento_whatsapp_entrante, canal_whatsapp, notificacion, auditoria_reserva,
      reserva, bloqueo, jornada, barbero, servicio, vinculo_cliente, cliente,
      membresia, invitacion, auditoria_plataforma, usuario RESTART IDENTITY CASCADE;
    DELETE FROM negocio WHERE id <> 1;
    UPDATE negocio SET nombre = 'BarberTurno', estado = 'ACTIVO',
      anticipacion_cambio_cliente_min = 120, horizonte_dias = 30,
      limite_reservas_activas = 3, limite_barberos_activos = 10,
      confirmacion_manual = false, tolerancia_inicio_min = 15,
      actualizado_en = CURRENT_TIMESTAMP WHERE id = 1;
    COMMIT;`,
    true,
    validarBase(),
  );
  if (resultado.status !== 0) throw new Error('No se pudo vaciar la base v2 de prueba autorizada.');
}
