-- V2-10: confirmación explícita por ficha, con aislamiento y retención acotada.
CREATE TABLE operacion_pendiente (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    conversacion_id bigint NOT NULL,
    cliente_id bigint NOT NULL,
    tipo varchar(14) NOT NULL,
    datos jsonb NOT NULL,
    nonce bytea NOT NULL,
    estado varchar(10) NOT NULL DEFAULT 'VIGENTE',
    expira_en timestamptz NOT NULL,
    resultado jsonb,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT operacion_conversacion_fk FOREIGN KEY (negocio_id, conversacion_id)
        REFERENCES conversacion(negocio_id, id),
    CONSTRAINT operacion_cliente_fk FOREIGN KEY (negocio_id, cliente_id) REFERENCES cliente(negocio_id, id),
    CONSTRAINT operacion_tipo_valido CHECK (tipo IN ('CREAR', 'REPROGRAMAR', 'CANCELAR')),
    CONSTRAINT operacion_estado_valido CHECK (estado IN ('VIGENTE', 'EJECUTADA', 'CADUCADA', 'RECHAZADA', 'ANULADA')),
    CONSTRAINT operacion_nonce_longitud CHECK (octet_length(nonce) = 16),
    CONSTRAINT operacion_resultado_coherente CHECK ((estado IN ('EJECUTADA', 'RECHAZADA')) = (resultado IS NOT NULL))
);
CREATE UNIQUE INDEX operacion_vigente_uk ON operacion_pendiente(conversacion_id) WHERE estado = 'VIGENTE';
ALTER TABLE operacion_pendiente ENABLE ROW LEVEL SECURITY;
ALTER TABLE operacion_pendiente FORCE ROW LEVEL SECURITY;
CREATE POLICY operacion_pendiente_aislamiento ON operacion_pendiente
    USING (negocio_id = app_negocio_actual()) WITH CHECK (negocio_id = app_negocio_actual());
REVOKE ALL ON operacion_pendiente FROM "${rolAplicacion}";
GRANT SELECT, INSERT ON operacion_pendiente TO "${rolAplicacion}";
GRANT UPDATE (estado, resultado, actualizado_en) ON operacion_pendiente TO "${rolAplicacion}";

GRANT UPDATE (cliente_id, estado, version) ON conversacion TO "${rolAplicacion}";
CREATE FUNCTION validar_ficha_conversacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.cliente_id IS NOT NULL AND NEW.cliente_id IS DISTINCT FROM OLD.cliente_id THEN
        RAISE EXCEPTION 'La ficha de una conversación es inmutable' USING ERRCODE = '23514';
    END IF;
    IF NEW.cliente_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM cliente c WHERE c.negocio_id = NEW.negocio_id
            AND c.id = NEW.cliente_id AND c.wa_id = NEW.wa_id
    ) THEN
        RAISE EXCEPTION 'La ficha no corresponde al remitente del negocio' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER conversacion_ficha_inmutable BEFORE INSERT OR UPDATE ON conversacion
    FOR EACH ROW EXECUTE FUNCTION validar_ficha_conversacion();

GRANT DELETE ON conversacion, mensaje_whatsapp TO "${rolAplicacion}";
CREATE POLICY conversacion_borrado_sin_ficha ON conversacion AS RESTRICTIVE FOR DELETE
    USING (cliente_id IS NULL);
CREATE POLICY mensaje_borrado_sin_ficha ON mensaje_whatsapp AS RESTRICTIVE FOR DELETE
    USING (estado <> 'PENDIENTE' AND reserva_id IS NULL AND EXISTS (
        SELECT 1 FROM conversacion c WHERE c.negocio_id = mensaje_whatsapp.negocio_id
            AND c.id = mensaje_whatsapp.conversacion_id AND c.cliente_id IS NULL
    ));

ALTER TABLE reserva ALTER COLUMN creada_por DROP NOT NULL;
ALTER TABLE reserva ADD COLUMN canal varchar(10) NOT NULL DEFAULT 'WEB',
    ADD CONSTRAINT reserva_canal_valido CHECK (canal IN ('WEB', 'WHATSAPP')),
    ADD CONSTRAINT reserva_creador_coherente CHECK (creada_por IS NOT NULL OR canal = 'WHATSAPP');
ALTER TABLE auditoria_reserva ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE auditoria_reserva ADD COLUMN actor_cliente_id bigint,
    ADD CONSTRAINT auditoria_actor_cliente_fk FOREIGN KEY (negocio_id, actor_cliente_id) REFERENCES cliente(negocio_id, id),
    ADD CONSTRAINT auditoria_actor_unico CHECK (num_nonnulls(actor_id, actor_cliente_id) = 1);
GRANT UPDATE (cuerpo) ON evento_whatsapp_entrante TO "${rolAplicacion}";
