-- Solo consultas. Se incluye ANTES de cualquier escritura, también en la retirada.
DO $$
DECLARE nombre text;
BEGIN
    IF current_setting('server_version_num')::int / 10000 <> 18
       OR current_user <> 'btadmin' OR session_user <> 'btadmin'
       OR current_database() <> 'postgres'
       OR (SELECT rolsuper OR NOT rolcreatedb OR NOT rolcreaterole FROM pg_roles WHERE rolname = current_user)
    THEN RAISE EXCEPTION 'Se requiere PostgreSQL 18 y btadmin no superusuario con CREATEDB/CREATEROLE en postgres.';
    END IF;
    FOREACH nombre IN ARRAY ARRAY['bt_v2_demo', 'bt_v2_demo_app'] LOOP
        IF EXISTS (SELECT FROM pg_roles WHERE rolname = nombre AND
            (NOT rolcanlogin OR rolinherit OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))
        THEN RAISE EXCEPTION 'Rol demo con atributos inesperados: %.', nombre; END IF;
        IF EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.member WHERE r.rolname = nombre)
        THEN RAISE EXCEPTION 'Un rol demo pertenece a otros roles: %.', nombre; END IF;
        -- PG16+ conserva la concesión automática ADMIN al creador, sin SET ni INHERIT.
        -- No se permite ninguna otra pertenencia (ni siquiera una temporal de un intento interrumpido).
        IF EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.roleid
            WHERE r.rolname = nombre AND NOT (m.member = (SELECT oid FROM pg_roles WHERE rolname = current_user)
                AND m.grantor <> m.member AND m.admin_option AND NOT m.set_option AND NOT m.inherit_option))
        THEN RAISE EXCEPTION 'Membresía inesperada sobre un rol demo: %.', nombre; END IF;
    END LOOP;
    IF EXISTS (SELECT FROM pg_database WHERE datname = 'barberturno_v2_demo' AND pg_get_userbyid(datdba) <> 'bt_v2_demo')
    THEN RAISE EXCEPTION 'La base demo tiene otro propietario.'; END IF;
    IF EXISTS (SELECT FROM pg_database d CROSS JOIN LATERAL aclexplode(coalesce(d.datacl, acldefault('d', d.datdba))) a
        WHERE d.datname = 'barberturno_v2_demo' AND a.grantee <> 0 AND a.grantee <> d.datdba
          AND NOT (a.grantee = coalesce((SELECT oid FROM pg_roles WHERE rolname = 'bt_v2_demo_app'), 0)
              AND a.privilege_type = 'CONNECT' AND NOT a.is_grantable))
    THEN RAISE EXCEPTION 'La base demo tiene concesiones adicionales inesperadas.'; END IF;
    IF EXISTS (SELECT FROM pg_database WHERE pg_get_userbyid(datdba) = 'bt_v2_demo_app'
        OR (pg_get_userbyid(datdba) = 'bt_v2_demo' AND datname <> 'barberturno_v2_demo'))
    THEN RAISE EXCEPTION 'Los roles demo son dueños de bases no autorizadas.'; END IF;
    IF EXISTS (SELECT FROM pg_shdepend s JOIN pg_roles r ON r.oid = s.refobjid
        WHERE s.refclassid = 'pg_authid'::regclass AND s.deptype = 'o' AND r.rolname = 'bt_v2_demo_app')
    THEN RAISE EXCEPTION 'El rol de ejecución tiene objetos en propiedad.'; END IF;
    -- Incluye objetos de otras bases y objetos compartidos (p. ej., tablespaces).
    IF EXISTS (SELECT FROM pg_shdepend s JOIN pg_roles r ON r.oid = s.refobjid
        WHERE s.refclassid = 'pg_authid'::regclass AND s.deptype = 'o'
          AND r.rolname IN ('bt_v2_demo', 'bt_v2_demo_app')
          AND NOT ((s.dbid <> 0 AND s.dbid = coalesce((SELECT oid FROM pg_database WHERE datname = 'barberturno_v2_demo'), 0))
              OR (s.classid = 'pg_database'::regclass AND s.objid = coalesce((SELECT oid FROM pg_database WHERE datname = 'barberturno_v2_demo'), 0))))
    THEN RAISE EXCEPTION 'Un rol demo es dueño de objetos fuera de la base demo.'; END IF;
END $$;
