-- Limpieza excepcional V2-REAL. Ejecutar con la aplicación detenida y respaldo validado.
-- Una sola sentencia DO, en autocommit (sin BEGIN externo), compatible con psql y JDBC.
-- Parámetros de sesión: bt.limpieza_slugs (array JSON), bt.limpieza_admin,
-- bt.limpieza_nombre y bt.limpieza_modo (ensayo|aplicar). Ver limpiar-demo.psql.
-- Nunca desactiva RLS/disparadores ni utiliza privilegios de superusuario.
DO $limpieza$
DECLARE
    slugs text[];
    destinos bigint[];
    conservado bigint;
    nombre_nuevo text := current_setting('bt.limpieza_nombre', true);
    modo text := current_setting('bt.limpieza_modo', true);
    ambito bigint;
    tabla record;
    referencia record;
    pendientes oid[];
    candidatas bigint[];
    elegida oid;
    cantidad bigint;
    antes bigint;
    hay_reales boolean;
BEGIN
    PERFORM set_config('search_path', 'pg_catalog, public', true);
    PERFORM set_config('lock_timeout', '10s', true);
    IF modo IS NULL OR modo NOT IN ('ensayo', 'aplicar')
            OR nombre_nuevo IS NULL OR length(btrim(nombre_nuevo)) NOT BETWEEN 1 AND 100 THEN
        RAISE EXCEPTION 'Configuración de limpieza incompleta o inválida';
    END IF;
    SELECT array_agg(DISTINCT s) INTO slugs
        FROM jsonb_array_elements_text(current_setting('bt.limpieza_slugs')::jsonb) AS lista(s);
    IF coalesce(cardinality(slugs), 0) = 0 OR array_position(slugs, NULL) IS NOT NULL THEN
        RAISE EXCEPTION 'La lista de negocios debe ser explícita y no vacía';
    END IF;

    -- Impide altas/referencias concurrentes durante validación y borrado; no modifica otros negocios.
    LOCK TABLE public.negocio, public.usuario IN SHARE ROW EXCLUSIVE MODE;
    FOR tabla IN
        SELECT DISTINCT c.oid, n.nspname, c.relname FROM pg_class c
        JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind='r' AND (
            EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid=c.oid
                AND a.attname='negocio_id' AND NOT a.attisdropped)
            OR EXISTS (SELECT 1 FROM pg_constraint f WHERE f.conrelid=c.oid
                AND f.contype='f' AND f.confrelid='public.usuario'::regclass))
        ORDER BY c.oid
    LOOP
        EXECUTE format('LOCK TABLE %I.%I IN SHARE ROW EXCLUSIVE MODE', tabla.nspname, tabla.relname);
    END LOOP;
    SELECT array_agg(id ORDER BY id) INTO destinos FROM public.negocio WHERE slug=ANY(slugs);
    IF cardinality(destinos) IS DISTINCT FROM cardinality(slugs) THEN
        RAISE EXCEPTION 'Algún negocio solicitado no existe; no se realiza la limpieza';
    END IF;
    SELECT id INTO conservado FROM public.usuario
        WHERE correo=lower(btrim(current_setting('bt.limpieza_admin', true)))
            AND activo AND rol_plataforma='ADMIN_PLATAFORMA';
    IF conservado IS NULL THEN
        RAISE EXCEPTION 'Se requiere una cuenta administradora de plataforma activa';
    END IF;

    -- Revisa todas las FK de identidad, incluidas ficha, personal, creador de reserva y auditorías.
    FOREACH ambito IN ARRAY destinos LOOP
        PERFORM set_config('app.negocio_id', ambito::text, true);
        FOR referencia IN
            SELECT c.relname, a.attname FROM pg_constraint f
            JOIN pg_class c ON c.oid=f.conrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace
            JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=f.conkey[1]
            WHERE f.contype='f' AND f.confrelid='public.usuario'::regclass
                AND n.nspname='public' AND cardinality(f.conkey)=1
                AND EXISTS (SELECT 1 FROM pg_attribute b WHERE b.attrelid=c.oid
                    AND b.attname='negocio_id' AND NOT b.attisdropped)
        LOOP
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I t JOIN public.usuario u
                ON u.id=t.%I WHERE t.negocio_id=$1 AND u.correo NOT LIKE ''%%@ejemplo.test'')',
                referencia.relname, referencia.attname) INTO hay_reales USING ambito;
            IF hay_reales THEN
                RAISE EXCEPTION 'Un negocio solicitado tiene referencias a usuarios reales; limpieza cancelada';
            END IF;
        END LOOP;
    END LOOP;

    FOREACH ambito IN ARRAY destinos LOOP
        PERFORM set_config('app.negocio_id', ambito::text, true);
        SELECT array_agg(c.oid ORDER BY c.oid) INTO pendientes
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            JOIN pg_attribute a ON a.attrelid=c.oid AND a.attname='negocio_id' AND NOT a.attisdropped
            WHERE n.nspname='public' AND c.relkind='r';
        -- Conteos anteriores a cualquier modificación, incluidos cambios de estado para retención.
        FOR tabla IN SELECT relname FROM pg_class WHERE oid=ANY(pendientes) ORDER BY relname LOOP
            EXECUTE format('SELECT count(*) FROM public.%I WHERE negocio_id=$1', tabla.relname)
                INTO cantidad USING ambito;
            RAISE NOTICE 'Antes tabla=% filas=%', tabla.relname, cantidad;
        END LOOP;
        -- Las políticas restrictivas de retención también se aplican al propietario con FORCE.
        -- Habilita el borrado de la demo por las condiciones existentes, sin alterar políticas.
        UPDATE public.cliente SET anonimizada_en=CURRENT_TIMESTAMP WHERE negocio_id=ambito;
        UPDATE public.mensaje_whatsapp SET estado='FALLIDO', error_codigo='MENSAJE_CADUCADO',
            actualizado_en=CURRENT_TIMESTAMP WHERE negocio_id=ambito AND estado='PENDIENTE';
        UPDATE public.turno_ia SET estado='DESCARTADO', texto=NULL, reclamado_hasta=NULL,
            actualizado_en=CURRENT_TIMESTAMP WHERE negocio_id=ambito AND estado='PENDIENTE';

        -- Orden topológico de FK: primero hijos, después padres. Un ciclo aborta toda la operación.
        WHILE cardinality(pendientes) > 0 LOOP
            SELECT p INTO elegida FROM unnest(pendientes) p
                WHERE NOT EXISTS (SELECT 1 FROM pg_constraint f WHERE f.contype='f'
                    AND f.confrelid=p AND f.conrelid=ANY(pendientes) AND f.conrelid<>p)
                ORDER BY p LIMIT 1;
            IF elegida IS NULL THEN
                RAISE EXCEPTION 'Dependencias cíclicas no soportadas; limpieza cancelada';
            END IF;
            SELECT relname INTO tabla FROM pg_class WHERE oid=elegida;
            EXECUTE format('DELETE FROM public.%I WHERE negocio_id=$1', tabla.relname) USING ambito;
            EXECUTE format('SELECT count(*) FROM public.%I WHERE negocio_id=$1', tabla.relname)
                INTO cantidad USING ambito;
            RAISE NOTICE 'Después tabla=% filas=%', tabla.relname, cantidad;
            IF cantidad <> 0 THEN
                RAISE EXCEPTION 'RLS impide completar el borrado; limpieza cancelada';
            END IF;
            pendientes := array_remove(pendientes, elegida);
        END LOOP;
    END LOOP;
    SELECT count(*) INTO antes FROM public.negocio WHERE id=ANY(destinos);
    DELETE FROM public.negocio WHERE id=ANY(destinos);
    RAISE NOTICE 'Antes tabla=negocio filas=%', antes;
    RAISE NOTICE 'Después tabla=negocio filas=0';

    -- Solo se eliminan identidades ficticias huérfanas. Leer cada ámbito evita falsos huérfanos por RLS.
    SELECT array_agg(id) INTO candidatas FROM public.usuario
        WHERE correo LIKE '%@ejemplo.test' AND id<>conservado;
    FOR ambito IN SELECT id FROM public.negocio UNION ALL SELECT NULL::bigint LOOP
        PERFORM set_config('app.negocio_id', coalesce(ambito::text, ''), true);
        FOR referencia IN
            SELECT n.nspname, c.relname, a.attname FROM pg_constraint f
            JOIN pg_class c ON c.oid=f.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
            JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=f.conkey[1]
            WHERE f.contype='f' AND f.confrelid='public.usuario'::regclass AND cardinality(f.conkey)=1
        LOOP
            EXECUTE format('SELECT ARRAY(SELECT unnest($1::bigint[]) EXCEPT
                SELECT %I FROM %I.%I)', referencia.attname, referencia.nspname, referencia.relname)
                INTO candidatas USING candidatas;
        END LOOP;
    END LOOP;
    SELECT count(*) INTO antes FROM public.usuario WHERE id=ANY(candidatas);
    DELETE FROM public.usuario WHERE id=ANY(candidatas);
    RAISE NOTICE 'Antes tabla=usuario filas=%', antes;
    RAISE NOTICE 'Después tabla=usuario filas=0';
    UPDATE public.usuario SET nombre=btrim(nombre_nuevo), actualizado_en=CURRENT_TIMESTAMP WHERE id=conservado;
    PERFORM set_config('app.negocio_id', '', true);
    IF modo='ensayo' THEN
        RAISE NOTICE 'Ensayo terminado: ROLLBACK';
        ROLLBACK;
    ELSE
        RAISE NOTICE 'Limpieza terminada: COMMIT';
        COMMIT;
    END IF;
END
$limpieza$;
