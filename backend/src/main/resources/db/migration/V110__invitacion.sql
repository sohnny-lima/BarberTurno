-- V2-DA-39: una invitación acredita una membresía, nunca reasigna una agenda.
CREATE TABLE invitacion (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL REFERENCES negocio(id),
    correo varchar(254) NOT NULL,
    rol varchar(12) NOT NULL,
    token_hash bytea NOT NULL,
    estado varchar(10) NOT NULL DEFAULT 'PENDIENTE',
    expira_en timestamptz NOT NULL,
    invitada_por bigint NOT NULL REFERENCES usuario(id),
    aceptada_por bigint REFERENCES usuario(id),
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT invitacion_correo_minusculas CHECK (correo = lower(correo)),
    CONSTRAINT invitacion_rol_valido CHECK (rol IN ('PROPIETARIO', 'ENCARGADO', 'BARBERO')),
    CONSTRAINT invitacion_estado_valido CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'REVOCADA'))
);
CREATE UNIQUE INDEX invitacion_token_uk ON invitacion (token_hash);
CREATE UNIQUE INDEX invitacion_pendiente_uk ON invitacion (negocio_id, correo) WHERE estado = 'PENDIENTE';
-- Plataforma sin RLS, superficie mínima en InvitacionRepository. Sin DELETE ni DDL.
REVOKE DELETE ON invitacion FROM "${rolAplicacion}";
GRANT SELECT, INSERT, UPDATE ON invitacion TO "${rolAplicacion}";

-- La identidad de un perfil es inmutable también para SQL directo del rol de aplicación.
CREATE FUNCTION impedir_reasignacion_barbero() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
BEGIN
    IF NEW.usuario_id IS DISTINCT FROM OLD.usuario_id THEN
        RAISE EXCEPTION 'La cuenta de un perfil profesional es inmutable.'
            USING ERRCODE = '23514', CONSTRAINT = 'barbero_usuario_inmutable';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION impedir_reasignacion_barbero() FROM PUBLIC;
REVOKE ALL ON FUNCTION impedir_reasignacion_barbero() FROM "${rolAplicacion}";
CREATE TRIGGER barbero_usuario_inmutable BEFORE UPDATE OF usuario_id ON barbero
    FOR EACH ROW EXECUTE FUNCTION impedir_reasignacion_barbero();
