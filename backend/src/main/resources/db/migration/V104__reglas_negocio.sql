-- V2-04: reglas de reserva y datos profesionales propios de cada negocio.
ALTER TABLE negocio ADD COLUMN tolerancia_inicio_min smallint NOT NULL DEFAULT 15;
ALTER TABLE barbero ADD COLUMN nombre_visible varchar(100),
    ADD COLUMN telefono_contacto varchar(16);

-- FORCE también obliga al propietario: cada relleno se ejecuta con su contexto local.
DO $$
DECLARE
    negocio_actual bigint;
BEGIN
    FOR negocio_actual IN SELECT id FROM negocio ORDER BY id LOOP
        PERFORM set_config('app.negocio_id', negocio_actual::text, true);
        UPDATE barbero b SET nombre_visible = u.nombre, telefono_contacto = u.telefono
        FROM usuario u WHERE b.usuario_id = u.id;
    END LOOP;
    PERFORM set_config('app.negocio_id', '', true);
END
$$;

ALTER TABLE barbero ALTER COLUMN nombre_visible SET NOT NULL;
