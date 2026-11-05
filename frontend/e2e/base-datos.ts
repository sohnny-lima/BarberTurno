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

export interface DatosSiembraDerivacion {
  conversacionId: number;
  notificacionId: number;
  clienteId: number;
}

/**
 * Siembra por SQL una conversación con ficha en modo HUMANO, un mensaje entrante reciente
 * y un aviso DERIVACION para el encargado (V2-11 CB-14).
 */
/** 15 min antes del reloj fijo del jar E2E (fixture.ts, 2026-09-28T09:00-05:00): dentro de la ventana de 24 h. */
const INSTANTE_SIEMBRA = "TIMESTAMPTZ '2026-09-28T08:45:00-05:00'";

export function sembrarConversacionDerivada(
  slugNegocio = 'barberturno',
  correoEncargado = 'encargado@ejemplo.test',
): DatosSiembraDerivacion {
  validarBase();
  const sqlSiembra = `
    DO $$
    DECLARE
      v_negocio_id bigint;
      v_usuario_id bigint;
      v_cliente_id bigint;
      v_conversacion_id bigint;
    BEGIN
      SELECT id INTO v_negocio_id FROM negocio WHERE slug = '${slugNegocio}';
      IF v_negocio_id IS NULL THEN
        RAISE EXCEPTION 'Negocio % no encontrado', '${slugNegocio}';
      END IF;

      SELECT id INTO v_usuario_id FROM usuario WHERE correo = '${correoEncargado}';
      IF v_usuario_id IS NULL THEN
        SELECT id INTO v_usuario_id FROM usuario WHERE correo = 'multirol@ejemplo.test';
      END IF;
      IF v_usuario_id IS NULL THEN
        SELECT id INTO v_usuario_id FROM usuario LIMIT 1;
      END IF;

      INSERT INTO membresia (negocio_id, usuario_id, rol, estado, creado_en, actualizado_en)
      VALUES (v_negocio_id, v_usuario_id, 'ENCARGADO', 'ACTIVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      ON CONFLICT (negocio_id, usuario_id) DO UPDATE SET rol = 'ENCARGADO', estado = 'ACTIVA';

      PERFORM set_config('app.negocio_id', v_negocio_id::text, false);

      -- Canal activo: sin canal la bandeja da NEGOCIO_NO_OPERATIVO y no deja responder (los datos demo no lo crean). SQL en ASCII: psql recibe el texto en la pagina de codigos de la consola.
      INSERT INTO canal_whatsapp (negocio_id, waba_id, phone_number_id, numero_visible, estado, creado_en, actualizado_en)
      VALUES (v_negocio_id, '100000000000001', '100000000000002', '+51900000000', 'ACTIVO', ${INSTANTE_SIEMBRA}, ${INSTANTE_SIEMBRA})
      ON CONFLICT (negocio_id) DO UPDATE SET estado = 'ACTIVO', actualizado_en = ${INSTANTE_SIEMBRA};

      INSERT INTO cliente (negocio_id, nombre, wa_id, telefono, activo, privacidad_aceptada_en, creado_en, actualizado_en)
      VALUES (v_negocio_id, 'Cliente WhatsApp E2E', '51999111222', '999111222', true, ${INSTANTE_SIEMBRA}, ${INSTANTE_SIEMBRA}, ${INSTANTE_SIEMBRA})
      ON CONFLICT (negocio_id, wa_id) WHERE wa_id IS NOT NULL DO UPDATE SET nombre = EXCLUDED.nombre
      RETURNING id INTO v_cliente_id;

      INSERT INTO conversacion (negocio_id, wa_id, cliente_id, modo, derivada_en, motivo_derivacion, ultima_entrada_en, creado_en, actualizado_en)
      VALUES (v_negocio_id, '51999111222', v_cliente_id, 'HUMANO', ${INSTANTE_SIEMBRA}, 'PETICION', ${INSTANTE_SIEMBRA}, ${INSTANTE_SIEMBRA}, ${INSTANTE_SIEMBRA})
      ON CONFLICT (negocio_id, wa_id) DO UPDATE SET modo = 'HUMANO', cliente_id = v_cliente_id, derivada_en = ${INSTANTE_SIEMBRA}, motivo_derivacion = 'PETICION', ultima_entrada_en = ${INSTANTE_SIEMBRA}
      RETURNING id INTO v_conversacion_id;

      INSERT INTO mensaje_whatsapp (negocio_id, conversacion_id, direccion, origen, tipo, contenido, wamid, estado, creado_en, actualizado_en)
      VALUES (v_negocio_id, v_conversacion_id, 'ENTRANTE', 'CLIENTE', 'TEXTO', '{"texto": "Hola, necesito hablar con un asesor"}'::jsonb, 'wamid.e2e.' || floor(random() * 1000000)::text, 'RECIBIDO', ${INSTANTE_SIEMBRA}, ${INSTANTE_SIEMBRA});

      INSERT INTO notificacion (negocio_id, usuario_id, conversacion_id, tipo, mensaje, leida, creado_en)
      VALUES (v_negocio_id, v_usuario_id, v_conversacion_id, 'DERIVACION', U&'Cliente WhatsApp E2E solicita atenci\\00F3n', false, ${INSTANTE_SIEMBRA});
    END $$;
    SELECT set_config('app.negocio_id', (SELECT id::text FROM negocio WHERE slug = '${slugNegocio}'), false);
    SELECT c.id || '|' || n.id || '|' || c.cliente_id
    FROM conversacion c
    JOIN notificacion n ON n.conversacion_id = c.id
    WHERE c.wa_id = '51999111222'
    ORDER BY n.id DESC LIMIT 1;
  `;

  const res = sql(sqlSiembra);
  if (res.status !== 0) {
    throw new Error('Fallo al sembrar conversación derivada: ' + (res.stderr || res.stdout));
  }
  const lineas = res.stdout.trim().split(/\r?\n/).filter(Boolean);
  const ultima = lineas[lineas.length - 1];
  const [conversacionId, notificacionId, clienteId] = ultima.split('|').map(Number);
  return { conversacionId, notificacionId, clienteId };
}
