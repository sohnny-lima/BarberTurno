-- V2-09: conversación y outbox con aislamiento forzado y escrituras acotadas.
CREATE TABLE conversacion (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    wa_id varchar(20) NOT NULL,
    cliente_id bigint,
    modo varchar(10) NOT NULL DEFAULT 'ASISTENTE',
    estado jsonb NOT NULL DEFAULT '{}',
    ultima_entrada_en timestamptz,
    version integer NOT NULL DEFAULT 0,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT conversacion_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT conversacion_negocio_id_uk UNIQUE (negocio_id, id),
    CONSTRAINT conversacion_cliente_fk FOREIGN KEY (negocio_id, cliente_id) REFERENCES cliente(negocio_id, id),
    CONSTRAINT conversacion_wa_uk UNIQUE (negocio_id, wa_id),
    CONSTRAINT conversacion_cliente_uk UNIQUE (negocio_id, cliente_id),
    CONSTRAINT conversacion_wa_formato CHECK (wa_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT conversacion_modo_valido CHECK (modo IN ('ASISTENTE', 'HUMANO'))
);
REVOKE ALL ON conversacion FROM "${rolAplicacion}";
GRANT SELECT, INSERT ON conversacion TO "${rolAplicacion}";
GRANT UPDATE (ultima_entrada_en, actualizado_en) ON conversacion TO "${rolAplicacion}";
ALTER TABLE conversacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversacion FORCE ROW LEVEL SECURITY;
CREATE POLICY conversacion_aislamiento ON conversacion
    USING (negocio_id = app_negocio_actual()) WITH CHECK (negocio_id = app_negocio_actual());

CREATE TABLE mensaje_whatsapp (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    conversacion_id bigint NOT NULL,
    reserva_id bigint,
    direccion varchar(8) NOT NULL,
    origen varchar(10) NOT NULL,
    tipo varchar(12) NOT NULL,
    contenido jsonb NOT NULL,
    wamid varchar(128),
    estado varchar(10) NOT NULL,
    intentos smallint NOT NULL DEFAULT 0,
    inciertos smallint NOT NULL DEFAULT 0,
    proximo_intento timestamptz,
    error_codigo varchar(24),
    codigo_meta integer,
    enviado_como varchar(12),
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT mensaje_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT mensaje_conversacion_fk FOREIGN KEY (negocio_id, conversacion_id) REFERENCES conversacion(negocio_id, id),
    CONSTRAINT mensaje_reserva_fk FOREIGN KEY (negocio_id, reserva_id) REFERENCES reserva(negocio_id, id),
    CONSTRAINT mensaje_direccion_valida CHECK (direccion IN ('ENTRANTE', 'SALIENTE')),
    CONSTRAINT mensaje_origen_valido CHECK (origen IN ('CLIENTE', 'ASISTENTE', 'SISTEMA', 'PERSONA')),
    CONSTRAINT mensaje_tipo_valido CHECK (tipo IN ('TEXTO', 'INTERACTIVO', 'PLANTILLA')),
    CONSTRAINT mensaje_estado_valido CHECK (estado IN ('RECIBIDO', 'PENDIENTE', 'ENVIADO', 'ENTREGADO', 'LEIDO', 'FALLIDO')),
    CONSTRAINT mensaje_enviado_como_valido CHECK (enviado_como IN ('TEXTO', 'INTERACTIVO', 'PLANTILLA')),
    CONSTRAINT mensaje_error_valido CHECK (error_codigo IN ('VENTANA_CERRADA', 'CANAL_DESCONECTADO',
        'NEGOCIO_NO_OPERATIVO', 'MENSAJE_CADUCADO', 'DESTINO_NO_VALIDO', 'PLANTILLA_RECHAZADA',
        'CREDENCIAL_RECHAZADA', 'CUENTA_RESTRINGIDA', 'SOLICITUD_RECHAZADA', 'REINTENTOS_AGOTADOS',
        'RESULTADO_DESCONOCIDO', 'FALLO_ENTREGA')),
    CONSTRAINT mensaje_intentos_validos CHECK (intentos >= 0 AND inciertos >= 0),
    CONSTRAINT mensaje_error_coherente CHECK ((estado = 'FALLIDO') = (error_codigo IS NOT NULL)),
    CONSTRAINT mensaje_pendiente_programado CHECK (estado <> 'PENDIENTE' OR proximo_intento IS NOT NULL),
    CONSTRAINT mensaje_envio_coherente CHECK (estado NOT IN ('ENVIADO', 'ENTREGADO', 'LEIDO')
        OR (wamid IS NOT NULL AND enviado_como IS NOT NULL)),
    CONSTRAINT mensaje_entrada_coherente CHECK ((direccion = 'ENTRANTE') = (estado = 'RECIBIDO'))
);
REVOKE ALL ON mensaje_whatsapp FROM "${rolAplicacion}";
GRANT SELECT, INSERT ON mensaje_whatsapp TO "${rolAplicacion}";
GRANT UPDATE (wamid, estado, intentos, inciertos, proximo_intento, error_codigo, codigo_meta,
    enviado_como, actualizado_en) ON mensaje_whatsapp TO "${rolAplicacion}";
ALTER TABLE mensaje_whatsapp ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensaje_whatsapp FORCE ROW LEVEL SECURITY;
CREATE POLICY mensaje_whatsapp_aislamiento ON mensaje_whatsapp
    USING (negocio_id = app_negocio_actual()) WITH CHECK (negocio_id = app_negocio_actual());
CREATE UNIQUE INDEX mensaje_wamid_uk ON mensaje_whatsapp(wamid) WHERE wamid IS NOT NULL;
CREATE INDEX mensaje_salida_cola_ix ON mensaje_whatsapp(negocio_id, proximo_intento, id) WHERE estado = 'PENDIENTE';
CREATE INDEX mensaje_conversacion_pendiente_ix ON mensaje_whatsapp(negocio_id, conversacion_id, id) WHERE estado = 'PENDIENTE';

ALTER TABLE evento_whatsapp_entrante ALTER COLUMN clave_idempotencia TYPE varchar(160);
