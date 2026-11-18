-- V2-11: atención humana y mecanismos de privacidad (P2-06 pendiente).
ALTER TABLE conversacion
    ADD COLUMN derivada_en timestamptz,
    ADD COLUMN motivo_derivacion varchar(12),
    ADD COLUMN ultima_respuesta_personal_en timestamptz,
    ADD CONSTRAINT conversacion_motivo_derivacion_valido CHECK
        (motivo_derivacion IN ('PETICION', 'SIN_PROGRESO', 'LIMITE', 'HERRAMIENTA')),
    ADD CONSTRAINT conversacion_derivacion_coherente CHECK (
        (modo = 'HUMANO' AND cliente_id IS NOT NULL AND derivada_en IS NOT NULL
            AND motivo_derivacion IS NOT NULL)
        OR (modo = 'ASISTENTE' AND derivada_en IS NULL AND motivo_derivacion IS NULL
            AND ultima_respuesta_personal_en IS NULL));
GRANT UPDATE (modo, derivada_en, motivo_derivacion, ultima_respuesta_personal_en)
    ON conversacion TO "${rolAplicacion}";
CREATE INDEX conversacion_bandeja_ix ON conversacion (negocio_id, ultima_entrada_en DESC)
    WHERE modo = 'HUMANO';

ALTER TABLE mensaje_whatsapp
    ADD COLUMN enviado_por bigint,
    ADD CONSTRAINT mensaje_enviado_por_fk FOREIGN KEY (enviado_por) REFERENCES usuario(id),
    ADD CONSTRAINT mensaje_persona_coherente CHECK ((origen = 'PERSONA') = (enviado_por IS NOT NULL)),
    ADD CONSTRAINT mensaje_entrada_origen_coherente CHECK (direccion = 'SALIENTE' OR origen = 'CLIENTE'),
    DROP CONSTRAINT mensaje_error_valido,
    ADD CONSTRAINT mensaje_error_valido CHECK (error_codigo IN ('VENTANA_CERRADA', 'CANAL_DESCONECTADO',
        'NEGOCIO_NO_OPERATIVO', 'MENSAJE_CADUCADO', 'DESTINO_NO_VALIDO', 'PLANTILLA_RECHAZADA',
        'CREDENCIAL_RECHAZADA', 'CUENTA_RESTRINGIDA', 'SOLICITUD_RECHAZADA', 'REINTENTOS_AGOTADOS',
        'RESULTADO_DESCONOCIDO', 'FALLO_ENTREGA', 'MODO_HUMANO'));

ALTER TABLE notificacion ALTER COLUMN reserva_id DROP NOT NULL;
ALTER TABLE notificacion ADD COLUMN conversacion_id bigint,
    ADD CONSTRAINT notificacion_conversacion_fk FOREIGN KEY (negocio_id, conversacion_id)
        REFERENCES conversacion(negocio_id, id),
    ADD CONSTRAINT notificacion_destino_unico CHECK (num_nonnulls(reserva_id, conversacion_id) = 1);

GRANT UPDATE (contenido) ON mensaje_whatsapp TO "${rolAplicacion}";
CREATE FUNCTION validar_retirada_contenido_whatsapp() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.contenido IS DISTINCT FROM OLD.contenido AND (
        NEW.contenido IS DISTINCT FROM '{"retirado": true}'::jsonb
        OR OLD.estado = 'PENDIENTE' OR NEW.estado = 'PENDIENTE'
        OR NOT EXISTS (SELECT 1 FROM conversacion c
            WHERE c.negocio_id = NEW.negocio_id AND c.id = NEW.conversacion_id AND c.cliente_id IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'El contenido solo admite la retirada de mensajes terminados con ficha'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER mensaje_retirada_contenido BEFORE UPDATE OF contenido ON mensaje_whatsapp
    FOR EACH ROW EXECUTE FUNCTION validar_retirada_contenido_whatsapp();

ALTER TABLE cliente ADD COLUMN anonimizada_en timestamptz,
    DROP CONSTRAINT cliente_identificable,
    ADD CONSTRAINT cliente_identificable CHECK
        (usuario_id IS NOT NULL OR wa_id IS NOT NULL OR telefono IS NOT NULL OR anonimizada_en IS NOT NULL);
GRANT UPDATE (nombre, wa_id, telefono, anonimizada_en) ON cliente TO "${rolAplicacion}";

-- Se sustituyen las restrictivas: dos políticas se combinarían con AND, impidiendo anonimizar.
DROP POLICY conversacion_borrado_sin_ficha ON conversacion;
CREATE POLICY conversacion_borrado_sin_ficha ON conversacion AS RESTRICTIVE FOR DELETE
    USING (cliente_id IS NULL OR EXISTS (SELECT 1 FROM cliente f
        WHERE f.negocio_id = conversacion.negocio_id AND f.id = conversacion.cliente_id
            AND f.anonimizada_en IS NOT NULL));
DROP POLICY mensaje_borrado_sin_ficha ON mensaje_whatsapp;
CREATE POLICY mensaje_borrado_sin_ficha ON mensaje_whatsapp AS RESTRICTIVE FOR DELETE
    USING (estado <> 'PENDIENTE' AND EXISTS (
        SELECT 1 FROM conversacion c LEFT JOIN cliente f
            ON f.negocio_id = c.negocio_id AND f.id = c.cliente_id
        WHERE c.negocio_id = mensaje_whatsapp.negocio_id AND c.id = mensaje_whatsapp.conversacion_id
            AND ((c.cliente_id IS NULL AND mensaje_whatsapp.reserva_id IS NULL)
                OR f.anonimizada_en IS NOT NULL)));
GRANT DELETE ON operacion_pendiente TO "${rolAplicacion}";
CREATE POLICY operacion_borrado_terminada ON operacion_pendiente AS RESTRICTIVE FOR DELETE
    USING (estado <> 'VIGENTE' OR EXISTS (SELECT 1 FROM cliente f
        WHERE f.negocio_id = operacion_pendiente.negocio_id AND f.id = operacion_pendiente.cliente_id
            AND f.anonimizada_en IS NOT NULL));
