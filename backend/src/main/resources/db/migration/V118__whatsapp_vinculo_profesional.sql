-- V2-DA-106: capacidades de un uso y vínculo local; ninguna identidad de otro negocio.
CREATE TABLE codigo_vinculo_whatsapp (
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    barbero_id bigint NOT NULL,
    codigo_hash char(64) NOT NULL,
    caduca_en timestamptz NOT NULL,
    usado_en timestamptz,
    creado_en timestamptz NOT NULL,
    CONSTRAINT codigo_vinculo_whatsapp_pk PRIMARY KEY (codigo_hash),
    CONSTRAINT codigo_vinculo_whatsapp_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT codigo_vinculo_whatsapp_barbero_fk FOREIGN KEY (negocio_id, barbero_id)
        REFERENCES barbero(negocio_id, id),
    CONSTRAINT codigo_vinculo_whatsapp_hash_valido CHECK (codigo_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT codigo_vinculo_whatsapp_caducidad_valida CHECK (caduca_en > creado_en),
    CONSTRAINT codigo_vinculo_whatsapp_uso_valido CHECK (usado_en >= creado_en)
);
CREATE UNIQUE INDEX codigo_vinculo_whatsapp_sin_usar_uk
    ON codigo_vinculo_whatsapp (negocio_id, barbero_id) WHERE usado_en IS NULL;
ALTER TABLE codigo_vinculo_whatsapp ENABLE ROW LEVEL SECURITY;
ALTER TABLE codigo_vinculo_whatsapp FORCE ROW LEVEL SECURITY;
CREATE POLICY codigo_vinculo_whatsapp_aislamiento ON codigo_vinculo_whatsapp
    USING (negocio_id = app_negocio_actual()) WITH CHECK (negocio_id = app_negocio_actual());
REVOKE ALL ON codigo_vinculo_whatsapp FROM "${rolAplicacion}";
GRANT SELECT, INSERT, DELETE ON codigo_vinculo_whatsapp TO "${rolAplicacion}";
GRANT UPDATE (usado_en) ON codigo_vinculo_whatsapp TO "${rolAplicacion}";

CREATE TABLE vinculo_whatsapp_profesional (
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    barbero_id bigint NOT NULL,
    wa_id varchar(20) NOT NULL,
    creado_en timestamptz NOT NULL,
    CONSTRAINT vinculo_whatsapp_profesional_pk PRIMARY KEY (negocio_id, barbero_id),
    CONSTRAINT vinculo_whatsapp_profesional_wa_uk UNIQUE (negocio_id, wa_id),
    CONSTRAINT vinculo_whatsapp_profesional_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT vinculo_whatsapp_profesional_barbero_fk FOREIGN KEY (negocio_id, barbero_id)
        REFERENCES barbero(negocio_id, id),
    CONSTRAINT vinculo_whatsapp_profesional_wa_formato CHECK (wa_id ~ '^[0-9]{1,20}$')
);
ALTER TABLE vinculo_whatsapp_profesional ENABLE ROW LEVEL SECURITY;
ALTER TABLE vinculo_whatsapp_profesional FORCE ROW LEVEL SECURITY;
CREATE POLICY vinculo_whatsapp_profesional_aislamiento ON vinculo_whatsapp_profesional
    USING (negocio_id = app_negocio_actual()) WITH CHECK (negocio_id = app_negocio_actual());
REVOKE ALL ON vinculo_whatsapp_profesional FROM "${rolAplicacion}";
GRANT SELECT, INSERT, DELETE ON vinculo_whatsapp_profesional TO "${rolAplicacion}";
