-- V2-08: entrada de WhatsApp (11-v2-08-whatsapp-entrada.md §3; V2-DA-52…56).
-- Tablas de plataforma sin RLS (04-arquitectura-datos.md §2): sus repositorios exponen una superficie mínima (V2-DA-26).

-- Un número por negocio y un negocio por número (V2-RN-07). Sin token con el número de prueba (V2-DA-14).
CREATE TABLE canal_whatsapp (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL,
    waba_id varchar(32) NOT NULL,
    phone_number_id varchar(32) NOT NULL,
    numero_visible varchar(21) NOT NULL,
    token_cifrado bytea,
    estado varchar(12) NOT NULL DEFAULT 'ACTIVO',
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT canal_whatsapp_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT canal_whatsapp_negocio_uk UNIQUE (negocio_id),
    CONSTRAINT canal_whatsapp_estado_valido CHECK (estado IN ('ACTIVO', 'DESCONECTADO')),
    CONSTRAINT canal_whatsapp_waba_formato CHECK (waba_id ~ '^[0-9]{1,32}$'),
    CONSTRAINT canal_whatsapp_numero_formato CHECK (phone_number_id ~ '^[0-9]{1,32}$'),
    CONSTRAINT canal_whatsapp_visible_formato CHECK (numero_visible ~ '^\+?[0-9]{6,20}$')
);
CREATE UNIQUE INDEX canal_whatsapp_numero_uk ON canal_whatsapp (phone_number_id);

-- Bandeja de entrada idempotente: el webhook inserta sin negocio y el procesador lo resuelve (V2-DA-52).
CREATE TABLE evento_whatsapp_entrante (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_idempotencia varchar(128) NOT NULL,
    tipo varchar(8) NOT NULL,
    phone_number_id varchar(32) NOT NULL,
    negocio_id bigint,
    cuerpo jsonb NOT NULL,
    estado varchar(12) NOT NULL DEFAULT 'PENDIENTE',
    motivo varchar(24),
    intentos smallint NOT NULL DEFAULT 0,
    proximo_intento timestamptz NOT NULL,
    recibido_en timestamptz NOT NULL,
    procesado_en timestamptz,
    CONSTRAINT evento_whatsapp_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT evento_whatsapp_tipo_valido CHECK (tipo IN ('MENSAJE', 'ESTADO')),
    CONSTRAINT evento_whatsapp_estado_valido CHECK (estado IN ('PENDIENTE', 'PROCESADO', 'DESCARTADO', 'ERROR')),
    CONSTRAINT evento_whatsapp_motivo_valido CHECK (motivo IS NULL OR motivo IN ('NUMERO_NO_REGISTRADO',
        'CANAL_DESCONECTADO', 'NEGOCIO_NO_OPERATIVO', 'REMITENTE_INVALIDO', 'FALLO_PROCESAMIENTO')),
    CONSTRAINT evento_whatsapp_motivo_coherente CHECK (
        (estado IN ('PENDIENTE', 'PROCESADO') AND motivo IS NULL)
        OR (estado IN ('DESCARTADO', 'ERROR') AND motivo IS NOT NULL)),
    CONSTRAINT evento_whatsapp_intentos_validos CHECK (intentos >= 0)
);
CREATE UNIQUE INDEX evento_whatsapp_clave_uk ON evento_whatsapp_entrante (clave_idempotencia);
CREATE INDEX evento_whatsapp_cola_ix ON evento_whatsapp_entrante (proximo_intento, id) WHERE estado = 'PENDIENTE';

-- Remitente verificado por WhatsApp: único por negocio; nunca se deduce del teléfono (V2-RN-09, V2-DA-54).
ALTER TABLE cliente ADD COLUMN wa_id varchar(20);
ALTER TABLE cliente ADD CONSTRAINT cliente_wa_formato CHECK (wa_id ~ '^[0-9]{1,20}$');
CREATE UNIQUE INDEX cliente_wa_uk ON cliente (negocio_id, wa_id) WHERE wa_id IS NOT NULL;
ALTER TABLE cliente DROP CONSTRAINT cliente_identificable,
    ADD CONSTRAINT cliente_identificable CHECK (usuario_id IS NOT NULL OR wa_id IS NOT NULL OR telefono IS NOT NULL);

-- Alta y desconexión del canal por la plataforma (V2-DA-56).
ALTER TABLE auditoria_plataforma
    DROP CONSTRAINT auditoria_plataforma_accion_valida,
    DROP CONSTRAINT auditoria_plataforma_destino_coherente,
    ADD CONSTRAINT auditoria_plataforma_accion_valida CHECK (accion IN
        ('CREAR_NEGOCIO', 'LISTAR_NEGOCIOS', 'SUSPENDER_NEGOCIO', 'REACTIVAR_NEGOCIO',
         'RESTABLECER_PASSWORD', 'REENVIAR_INVITACION_PROPIETARIO', 'REVOCAR_INVITACION_PROPIETARIO',
         'REGISTRAR_CANAL_WHATSAPP', 'DESCONECTAR_CANAL_WHATSAPP')),
    ADD CONSTRAINT auditoria_plataforma_destino_coherente CHECK (
        (accion = 'LISTAR_NEGOCIOS' AND negocio_id IS NULL AND objetivo_id IS NULL) OR
        (accion IN ('CREAR_NEGOCIO', 'SUSPENDER_NEGOCIO', 'REACTIVAR_NEGOCIO',
                   'REENVIAR_INVITACION_PROPIETARIO', 'REVOCAR_INVITACION_PROPIETARIO',
                   'REGISTRAR_CANAL_WHATSAPP', 'DESCONECTAR_CANAL_WHATSAPP')
         AND negocio_id IS NOT NULL AND objetivo_id IS NULL) OR
        (accion = 'RESTABLECER_PASSWORD' AND negocio_id IS NULL AND objetivo_id IS NOT NULL));

-- La aplicación no es propietaria: solo las columnas que cambian; sin DELETE en la cola.
REVOKE ALL ON canal_whatsapp, evento_whatsapp_entrante FROM "${rolAplicacion}";
GRANT SELECT, INSERT ON canal_whatsapp TO "${rolAplicacion}";
GRANT UPDATE (waba_id, phone_number_id, numero_visible, estado, actualizado_en) ON canal_whatsapp TO "${rolAplicacion}";
GRANT SELECT, INSERT ON evento_whatsapp_entrante TO "${rolAplicacion}";
GRANT UPDATE (negocio_id, estado, motivo, intentos, proximo_intento, procesado_en)
    ON evento_whatsapp_entrante TO "${rolAplicacion}";
