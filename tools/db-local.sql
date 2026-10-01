-- Preparación local de T-01. Ejecutar con psql como postgres en el puerto 5433.
-- No modifica tablas, datos, propietarios ni contraseñas que ya existan.
\set ON_ERROR_STOP on
\set ECHO none
\connect postgres

-- Comprobar el destino y los objetos existentes antes de realizar escrituras.
DO $$
BEGIN
    IF current_setting('server_version_num')::integer / 10000 <> 18
       OR current_setting('port')::integer <> 5433 THEN
        RAISE EXCEPTION 'Se requiere PostgreSQL 18 en el puerto 5433.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = 'barberturno'
          AND (NOT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'El rol barberturno existente no cumple el perfil de aplicación; revíselo sin alterar sus permisos automáticamente.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_database
        WHERE datname IN ('barberturno', 'barberturno_test')
          AND pg_get_userbyid(datdba) <> 'barberturno'
    ) THEN
        RAISE EXCEPTION 'Una base existente tiene otro dueño; no se cambiará su propietario ni se borrarán sus datos.';
    END IF;
END
$$;

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'barberturno') AS rol_nuevo
\gset

SELECT NOT EXISTS (
    SELECT 1 FROM pg_authid WHERE rolname = 'barberturno' AND rolpassword IS NOT NULL
) AS necesita_password
\gset

\if :rol_nuevo
    CREATE ROLE barberturno LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\endif

\if :necesita_password
    -- psql solicita la contraseña dos veces sin mostrarla ni escribirla en el SQL.
    SET password_encryption = 'scram-sha-256';
    \password barberturno
\else
    \echo El rol barberturno ya tiene contraseña; se conservan su contraseña y sus permisos.
\endif

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_authid WHERE rolname = 'barberturno' AND rolpassword IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'El rol barberturno necesita una contraseña no vacía; vuelva a ejecutar el script.';
    END IF;
END
$$;

-- CREATE DATABASE no admite un bloque transaccional; generar solo lo que falte.
SELECT format('CREATE DATABASE %I OWNER barberturno', nombre)
FROM (VALUES ('barberturno'), ('barberturno_test')) AS bases(nombre)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = nombre)
ORDER BY nombre
\gexec

SELECT datname AS base, pg_get_userbyid(datdba) AS propietario
FROM pg_database
WHERE datname IN ('barberturno', 'barberturno_test')
ORDER BY datname;
