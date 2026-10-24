-- V2-03: ficha local independiente de la identidad global. Sin WhatsApp ni fichas sin cuenta por API.
CREATE TABLE cliente (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL DEFAULT app_negocio_actual(),
    usuario_id bigint,
    nombre varchar(100) NOT NULL,
    telefono varchar(16),
    privacidad_aceptada_en timestamptz,
    activo boolean NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT cliente_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT cliente_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT cliente_negocio_id_uk UNIQUE (negocio_id, id),
    CONSTRAINT cliente_usuario_uk UNIQUE (negocio_id, usuario_id),
    CONSTRAINT cliente_identificable CHECK (usuario_id IS NOT NULL OR telefono IS NOT NULL)
);

-- FORCE ya protege reserva: inspeccionar cada negocio con contexto, nunca eludir sus políticas.
DO $$
DECLARE ambito bigint;
BEGIN
    FOR ambito IN SELECT id FROM negocio LOOP
        PERFORM set_config('app.negocio_id', ambito::text, true);
        IF EXISTS (SELECT 1 FROM reserva r JOIN usuario u ON u.id = r.cliente_id WHERE u.rol <> 'CLIENTE') THEN
            RAISE EXCEPTION 'V102: hay reservas cuyo cliente no es una cuenta CLIENTE';
        END IF;
    END LOOP;
END
$$;

-- ANTES de activar RLS: conservar ids y todas las columnas de las tablas de v1.
INSERT INTO cliente (id, negocio_id, usuario_id, nombre, telefono, privacidad_aceptada_en,
        activo, creado_en, actualizado_en) OVERRIDING SYSTEM VALUE
SELECT u.id, n.id, u.id, u.nombre, u.telefono, u.privacidad_aceptada_en,
    u.activo, u.creado_en, u.actualizado_en
FROM usuario u CROSS JOIN negocio n
WHERE u.rol = 'CLIENTE' AND n.slug = '${negocioUnicoSlug}';
SELECT setval(pg_get_serial_sequence('cliente', 'id'), coalesce(max(id), 1), max(id) IS NOT NULL) FROM cliente;

-- Índice sin datos personales ni RLS; solo el disparador puede escribir por la aplicación.
CREATE TABLE vinculo_cliente (
    negocio_id bigint NOT NULL REFERENCES negocio(id),
    usuario_id bigint NOT NULL REFERENCES usuario(id),
    CONSTRAINT vinculo_cliente_pk PRIMARY KEY (negocio_id, usuario_id)
);
INSERT INTO vinculo_cliente (negocio_id, usuario_id)
SELECT negocio_id, usuario_id FROM cliente WHERE usuario_id IS NOT NULL;
REVOKE ALL ON vinculo_cliente FROM "${rolAplicacion}";
GRANT SELECT ON vinculo_cliente TO "${rolAplicacion}";

-- No consulta cliente: FORCE no se elude. OLD/NEW proceden de la fila autorizada por RLS.
CREATE FUNCTION mantener_vinculo_cliente() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP <> 'INSERT' AND OLD.usuario_id IS NOT NULL THEN
        DELETE FROM public.vinculo_cliente WHERE negocio_id = OLD.negocio_id AND usuario_id = OLD.usuario_id;
    END IF;
    IF TG_OP <> 'DELETE' AND NEW.usuario_id IS NOT NULL THEN
        INSERT INTO public.vinculo_cliente (negocio_id, usuario_id) VALUES (NEW.negocio_id, NEW.usuario_id);
    END IF;
    RETURN NULL;
END
$$;
REVOKE ALL ON FUNCTION mantener_vinculo_cliente() FROM PUBLIC, "${rolAplicacion}";
CREATE TRIGGER cliente_vinculo_trg AFTER INSERT OR UPDATE OR DELETE ON cliente
    FOR EACH ROW EXECUTE FUNCTION mantener_vinculo_cliente();

ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente FORCE ROW LEVEL SECURITY;
CREATE POLICY cliente_negocio_rls ON cliente USING (negocio_id = app_negocio_actual())
    WITH CHECK (negocio_id = app_negocio_actual());

ALTER TABLE reserva DROP CONSTRAINT reserva_cliente_id_fkey,
    ADD CONSTRAINT reserva_cliente_fk FOREIGN KEY (negocio_id, cliente_id) REFERENCES cliente(negocio_id, id);
-- reserva_sin_solape_cliente conserva definición y nombre: cliente_id ahora identifica la ficha.
-- DML de cliente y uso de su secuencia se heredan de los privilegios por defecto de V100.
