-- V2-01: expansión compatible con las entidades de v1, sin cambiar sus identificadores.
CREATE TABLE negocio (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug varchar(40) NOT NULL,
    nombre varchar(100) NOT NULL,
    zona_horaria varchar(40) NOT NULL DEFAULT 'America/Lima',
    estado varchar(12) NOT NULL DEFAULT 'ACTIVO',
    anticipacion_cambio_cliente_min smallint NOT NULL DEFAULT 120,
    horizonte_dias smallint NOT NULL DEFAULT 30,
    limite_reservas_activas smallint NOT NULL DEFAULT 3,
    limite_barberos_activos smallint NOT NULL DEFAULT 10,
    confirmacion_manual boolean NOT NULL DEFAULT false,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT negocio_estado_valido CHECK (estado IN ('ACTIVO', 'SUSPENDIDO')),
    CONSTRAINT negocio_slug_formato CHECK (slug ~ '^[a-z0-9](-?[a-z0-9])*$'),
    CONSTRAINT negocio_id_rango CHECK (id < 2147483648)
);
CREATE UNIQUE INDEX negocio_slug_uk ON negocio (slug);
INSERT INTO negocio (slug, nombre, creado_en, actualizado_en)
VALUES ('${negocioUnicoSlug}', 'BarberTurno', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Sin contexto o después de COMMIT/ROLLBACK el GUC puede ser NULL o cadena vacía.
CREATE FUNCTION app_negocio_actual() RETURNS bigint
LANGUAGE sql STABLE
RETURN nullif(current_setting('app.negocio_id', true), '')::bigint;

-- Rellenar ANTES de activar FORCE: Flyway no dispone del contexto de una petición.
DO $$
DECLARE
    tabla text;
    negocio_unico bigint;
BEGIN
    SELECT id INTO STRICT negocio_unico FROM negocio WHERE slug = '${negocioUnicoSlug}';
    FOREACH tabla IN ARRAY ARRAY['servicio', 'barbero', 'jornada', 'bloqueo',
            'reserva', 'auditoria_reserva', 'notificacion'] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN negocio_id bigint', tabla);
        EXECUTE format('UPDATE %I SET negocio_id = $1', tabla) USING negocio_unico;
        EXECUTE format('ALTER TABLE %I ALTER COLUMN negocio_id SET NOT NULL,
            ALTER COLUMN negocio_id SET DEFAULT app_negocio_actual(),
            ADD CONSTRAINT %I FOREIGN KEY (negocio_id) REFERENCES negocio(id)',
            tabla, tabla || '_negocio_fk');
    END LOOP;
END
$$;

-- Padres con claves alternativas y relaciones que impiden mezclar ámbitos.
ALTER TABLE servicio ADD CONSTRAINT servicio_negocio_id_uk UNIQUE (negocio_id, id);
ALTER TABLE barbero ADD CONSTRAINT barbero_negocio_id_uk UNIQUE (negocio_id, id);
ALTER TABLE reserva ADD CONSTRAINT reserva_negocio_id_uk UNIQUE (negocio_id, id);
ALTER TABLE jornada DROP CONSTRAINT jornada_barbero_id_fkey,
    ADD CONSTRAINT jornada_barbero_fk FOREIGN KEY (negocio_id, barbero_id) REFERENCES barbero(negocio_id, id);
ALTER TABLE bloqueo DROP CONSTRAINT bloqueo_barbero_id_fkey,
    ADD CONSTRAINT bloqueo_barbero_fk FOREIGN KEY (negocio_id, barbero_id) REFERENCES barbero(negocio_id, id);
ALTER TABLE reserva DROP CONSTRAINT reserva_barbero_id_fkey, DROP CONSTRAINT reserva_servicio_id_fkey,
    ADD CONSTRAINT reserva_barbero_fk FOREIGN KEY (negocio_id, barbero_id) REFERENCES barbero(negocio_id, id),
    ADD CONSTRAINT reserva_servicio_fk FOREIGN KEY (negocio_id, servicio_id) REFERENCES servicio(negocio_id, id);
ALTER TABLE auditoria_reserva DROP CONSTRAINT auditoria_reserva_reserva_id_fkey,
    ADD CONSTRAINT auditoria_reserva_reserva_fk FOREIGN KEY (negocio_id, reserva_id) REFERENCES reserva(negocio_id, id);
ALTER TABLE notificacion DROP CONSTRAINT notificacion_reserva_id_fkey,
    ADD CONSTRAINT notificacion_reserva_fk FOREIGN KEY (negocio_id, reserva_id) REFERENCES reserva(negocio_id, id);
DROP INDEX servicio_nombre_uk;
CREATE UNIQUE INDEX servicio_nombre_uk ON servicio (negocio_id, lower(nombre));

-- La política obliga también al propietario y cubre SELECT, INSERT, UPDATE y DELETE.
DO $$
DECLARE tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['servicio', 'barbero', 'jornada', 'bloqueo',
            'reserva', 'auditoria_reserva', 'notificacion'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabla);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabla);
        EXECUTE format('CREATE POLICY %I ON %I USING (negocio_id = app_negocio_actual())
            WITH CHECK (negocio_id = app_negocio_actual())', tabla || '_negocio_rls', tabla);
    END LOOP;
END
$$;

-- El rol operativo no es dueño ni puede alterar el esquema, las políticas o las FK.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM "${rolAplicacion}";
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM "${rolAplicacion}";
REVOKE ALL ON FUNCTION app_negocio_actual() FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO "${rolAplicacion}";
GRANT SELECT ON negocio TO "${rolAplicacion}";
GRANT SELECT, INSERT, UPDATE, DELETE ON usuario, servicio, barbero, jornada, bloqueo,
    reserva, auditoria_reserva, notificacion TO "${rolAplicacion}";
-- Solo metadatos de migración: EsquemaIT verifica la versión aplicada con el rol real.
GRANT SELECT ON flyway_schema_history TO "${rolAplicacion}";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO "${rolAplicacion}";
GRANT EXECUTE ON FUNCTION app_negocio_actual() TO "${rolAplicacion}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${rolAplicacion}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO "${rolAplicacion}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO "${rolAplicacion}";
