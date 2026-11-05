-- V2-12: invalidación de turnos, cola aislada y contabilidad conservadora de IA.
ALTER TABLE conversacion
    ADD COLUMN revision bigint NOT NULL DEFAULT 0,
    ADD COLUMN minuto_entradas timestamptz,
    ADD COLUMN entradas_minuto smallint NOT NULL DEFAULT 0,
    ADD CONSTRAINT conversacion_entradas_no_negativas CHECK (entradas_minuto >= 0);
GRANT UPDATE (minuto_entradas, entradas_minuto) ON conversacion TO "${rolAplicacion}";
CREATE FUNCTION incrementar_revision_conversacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.revision := OLD.revision + 1;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION incrementar_revision_conversacion() FROM PUBLIC;
REVOKE ALL ON FUNCTION incrementar_revision_conversacion() FROM "${rolAplicacion}";
CREATE TRIGGER conversacion_revision BEFORE UPDATE ON conversacion
    FOR EACH ROW EXECUTE FUNCTION incrementar_revision_conversacion();

ALTER TABLE negocio
    ADD COLUMN ia_habilitada boolean NOT NULL DEFAULT false,
    ADD COLUMN ia_presupuesto_mensual_usd numeric(8,2),
    ADD COLUMN ia_llamadas_dia_conversacion integer,
    ADD COLUMN ia_entradas_minuto integer,
    ADD COLUMN ia_aviso_limite_en date,
    ADD CONSTRAINT negocio_ia_presupuesto_positivo CHECK (ia_presupuesto_mensual_usd > 0),
    ADD CONSTRAINT negocio_ia_llamadas_positivas CHECK (ia_llamadas_dia_conversacion > 0),
    ADD CONSTRAINT negocio_ia_entradas_positivas CHECK (ia_entradas_minuto > 0);
GRANT UPDATE (ia_habilitada, ia_presupuesto_mensual_usd, ia_llamadas_dia_conversacion,
    ia_entradas_minuto, ia_aviso_limite_en) ON negocio TO "${rolAplicacion}";

CREATE TABLE turno_ia (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    conversacion_id bigint NOT NULL,
    revision bigint NOT NULL,
    texto varchar(2000),
    estado varchar(10) NOT NULL DEFAULT 'PENDIENTE',
    motivo varchar(20),
    intentos smallint NOT NULL DEFAULT 0,
    reclamado_hasta timestamptz,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT turno_ia_negocio_id_uk UNIQUE (negocio_id, id),
    CONSTRAINT turno_ia_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT turno_ia_conversacion_fk FOREIGN KEY (negocio_id, conversacion_id)
        REFERENCES conversacion(negocio_id, id),
    CONSTRAINT turno_ia_estado_valido CHECK (estado IN ('PENDIENTE','HECHO','DESCARTADO','FALLIDO')),
    CONSTRAINT turno_ia_texto_coherente CHECK ((estado = 'PENDIENTE') = (texto IS NOT NULL)),
    CONSTRAINT turno_ia_intentos_validos CHECK (intentos BETWEEN 0 AND 3),
    CONSTRAINT turno_ia_revision_valida CHECK (revision >= 0)
);
CREATE UNIQUE INDEX turno_ia_pendiente_uk ON turno_ia (conversacion_id) WHERE estado='PENDIENTE';
CREATE INDEX turno_ia_cola_ix ON turno_ia (negocio_id, reclamado_hasta) WHERE estado='PENDIENTE';
CREATE INDEX turno_ia_conversacion_ix ON turno_ia (negocio_id, conversacion_id);
CREATE INDEX turno_ia_terminados_ix ON turno_ia (negocio_id, actualizado_en) WHERE estado <> 'PENDIENTE';
ALTER TABLE turno_ia ENABLE ROW LEVEL SECURITY;
ALTER TABLE turno_ia FORCE ROW LEVEL SECURITY;
CREATE POLICY turno_ia_aislamiento ON turno_ia
    USING (negocio_id=app_negocio_actual()) WITH CHECK (negocio_id=app_negocio_actual());
CREATE POLICY turno_ia_borrado_terminado ON turno_ia AS RESTRICTIVE FOR DELETE
    USING (estado <> 'PENDIENTE' OR EXISTS (
        SELECT 1 FROM conversacion c JOIN cliente f ON f.negocio_id=c.negocio_id AND f.id=c.cliente_id
        WHERE c.negocio_id=turno_ia.negocio_id AND c.id=turno_ia.conversacion_id AND f.anonimizada_en IS NOT NULL));
REVOKE ALL ON turno_ia FROM "${rolAplicacion}";
GRANT SELECT, INSERT, DELETE ON turno_ia TO "${rolAplicacion}";
GRANT UPDATE (texto, estado, motivo, intentos, reclamado_hasta, actualizado_en) ON turno_ia TO "${rolAplicacion}";
GRANT USAGE, SELECT ON SEQUENCE turno_ia_id_seq TO "${rolAplicacion}";

CREATE TABLE llamada_ia (
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    turno_id bigint NOT NULL,
    numero smallint NOT NULL,
    conversacion_id bigint NOT NULL,
    dia date NOT NULL,
    modelo varchar(100) NOT NULL,
    entrada bigint NOT NULL DEFAULT 0,
    salida bigint NOT NULL DEFAULT 0,
    cache_leida bigint NOT NULL DEFAULT 0,
    cache_escrita bigint NOT NULL DEFAULT 0,
    coste_reservado_micro_usd bigint NOT NULL,
    coste_micro_usd bigint,
    estado varchar(10) NOT NULL DEFAULT 'RESERVADA',
    CONSTRAINT llamada_ia_turno_numero_uk UNIQUE (turno_id, numero),
    CONSTRAINT llamada_ia_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT llamada_ia_turno_fk FOREIGN KEY (negocio_id, turno_id) REFERENCES turno_ia(negocio_id, id),
    CONSTRAINT llamada_ia_conversacion_fk FOREIGN KEY (negocio_id, conversacion_id)
        REFERENCES conversacion(negocio_id, id),
    CONSTRAINT llamada_ia_numero_positivo CHECK (numero > 0),
    CONSTRAINT llamada_ia_tokens_validos CHECK (entrada >= 0 AND salida >= 0 AND cache_leida >= 0 AND cache_escrita >= 0),
    CONSTRAINT llamada_ia_costes_validos CHECK (coste_reservado_micro_usd >= 0 AND coste_micro_usd >= 0),
    CONSTRAINT llamada_ia_estado_valido CHECK (estado IN ('RESERVADA','LIQUIDADA')),
    CONSTRAINT llamada_ia_coste_coherente CHECK ((estado='LIQUIDADA') = (coste_micro_usd IS NOT NULL))
);
CREATE INDEX llamada_ia_conversacion_dia_ix ON llamada_ia (negocio_id, conversacion_id, dia);
ALTER TABLE llamada_ia ENABLE ROW LEVEL SECURITY;
ALTER TABLE llamada_ia FORCE ROW LEVEL SECURITY;
CREATE POLICY llamada_ia_aislamiento ON llamada_ia
    USING (negocio_id=app_negocio_actual()) WITH CHECK (negocio_id=app_negocio_actual());
REVOKE ALL ON llamada_ia FROM "${rolAplicacion}";
GRANT SELECT, INSERT, DELETE ON llamada_ia TO "${rolAplicacion}";
GRANT UPDATE (entrada, salida, cache_leida, cache_escrita, coste_micro_usd, estado)
    ON llamada_ia TO "${rolAplicacion}";

-- El agregado incluye las reservas aún desconocidas desde ANTES del HTTP. Liquidar sustituye
-- ese importe por el conocido; purgar las filas de detalle nunca vuelve a sumar su coste.
CREATE TABLE uso_ia (
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    dia date NOT NULL,
    llamadas bigint NOT NULL DEFAULT 0,
    entrada bigint NOT NULL DEFAULT 0,
    salida bigint NOT NULL DEFAULT 0,
    cache_leida bigint NOT NULL DEFAULT 0,
    cache_escrita bigint NOT NULL DEFAULT 0,
    coste_micro_usd bigint NOT NULL DEFAULT 0,
    CONSTRAINT uso_ia_pk PRIMARY KEY (negocio_id, dia),
    CONSTRAINT uso_ia_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT uso_ia_no_negativo CHECK (llamadas >= 0 AND entrada >= 0 AND salida >= 0
        AND cache_leida >= 0 AND cache_escrita >= 0 AND coste_micro_usd >= 0)
);
ALTER TABLE uso_ia ENABLE ROW LEVEL SECURITY;
ALTER TABLE uso_ia FORCE ROW LEVEL SECURITY;
CREATE POLICY uso_ia_aislamiento ON uso_ia
    USING (negocio_id=app_negocio_actual()) WITH CHECK (negocio_id=app_negocio_actual());
REVOKE ALL ON uso_ia FROM "${rolAplicacion}";
GRANT SELECT, INSERT ON uso_ia TO "${rolAplicacion}";
GRANT UPDATE (llamadas, entrada, salida, cache_leida, cache_escrita, coste_micro_usd)
    ON uso_ia TO "${rolAplicacion}";
