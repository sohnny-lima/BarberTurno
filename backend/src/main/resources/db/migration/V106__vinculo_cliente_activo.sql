-- V2-DA-35: proyección de plataforma, solo SELECT para la aplicación.
ALTER TABLE vinculo_cliente ADD COLUMN activo boolean;

-- FORCE obliga también al propietario: rellenar con el contexto de cada negocio.
DO $$
DECLARE ambito bigint;
BEGIN
    FOR ambito IN SELECT id FROM negocio LOOP
        PERFORM set_config('app.negocio_id', ambito::text, true);
        UPDATE vinculo_cliente v SET activo = c.activo
        FROM cliente c
        WHERE v.negocio_id = ambito AND c.negocio_id = v.negocio_id AND c.usuario_id = v.usuario_id;
    END LOOP;
    PERFORM set_config('app.negocio_id', '', true);
END
$$;
ALTER TABLE vinculo_cliente ALTER COLUMN activo SET NOT NULL;

-- CREATE OR REPLACE conserva propietario y permisos; no modifica V102 aplicada.
-- OLD/NEW proceden de filas autorizadas por RLS, sin leer fichas de otros negocios.
CREATE OR REPLACE FUNCTION mantener_vinculo_cliente() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP <> 'INSERT' AND OLD.usuario_id IS NOT NULL THEN
        DELETE FROM public.vinculo_cliente WHERE negocio_id = OLD.negocio_id AND usuario_id = OLD.usuario_id;
    END IF;
    IF TG_OP <> 'DELETE' AND NEW.usuario_id IS NOT NULL THEN
        INSERT INTO public.vinculo_cliente (negocio_id, usuario_id, activo)
        VALUES (NEW.negocio_id, NEW.usuario_id, NEW.activo);
    END IF;
    RETURN NULL;
END
$$;
