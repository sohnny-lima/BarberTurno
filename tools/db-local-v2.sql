-- Preparación local de BarberTurno v2 (V2-01, V2-DA-20).
-- NO se ejecuta directamente: lo lanza tools/preparar-entorno-v2.ps1 con psql 18 como postgres
-- en el puerto 5433, y recibe por variables de entorno solo los VERIFICADORES SCRAM de las
-- contraseñas (calculados en el equipo; las contraseñas nunca llegan al servidor).
-- Crea únicamente objetos nuevos de v2. No modifica roles, bases ni datos de v1.
\set ON_ERROR_STOP on
\set ECHO none
\set QUIET on
\connect postgres

\getenv ver_propietario BT_V2_SCRAM_PROPIETARIO
\getenv ver_app BT_V2_SCRAM_APP
\if :{?ver_propietario}
\else
    DO $$ BEGIN RAISE EXCEPTION 'Ejecute tools/preparar-entorno-v2.ps1; este archivo no se usa directamente.'; END $$;
\endif
\if :{?ver_app}
\else
    DO $$ BEGIN RAISE EXCEPTION 'Ejecute tools/preparar-entorno-v2.ps1; este archivo no se usa directamente.'; END $$;
\endif

SELECT :'ver_propietario' ~ '^SCRAM-SHA-256\$4096:[A-Za-z0-9+/]+={0,2}\$[A-Za-z0-9+/]+={0,2}:[A-Za-z0-9+/]+={0,2}$'
   AND :'ver_app' ~ '^SCRAM-SHA-256\$4096:[A-Za-z0-9+/]+={0,2}\$[A-Za-z0-9+/]+={0,2}:[A-Za-z0-9+/]+={0,2}$'
   AND :'ver_propietario' <> :'ver_app' AS verificadores_validos
\gset
\if :verificadores_validos
\else
    DO $$ BEGIN RAISE EXCEPTION 'Los verificadores SCRAM recibidos no son válidos.'; END $$;
\endif

-- Comprobar el destino y los objetos existentes antes de cualquier escritura.
DO $$
DECLARE
    rol text;
BEGIN
    IF current_setting('server_version_num')::integer / 10000 <> 18
       OR current_setting('port')::integer <> 5433 THEN
        RAISE EXCEPTION 'Se requiere PostgreSQL 18 en el puerto 5433.';
    END IF;

    FOREACH rol IN ARRAY ARRAY['barberturno_v2', 'barberturno_v2_app'] LOOP
        IF EXISTS (
            SELECT 1 FROM pg_roles
            WHERE rolname = rol
              AND (NOT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
        ) THEN
            RAISE EXCEPTION 'El rol % ya existe con privilegios que no corresponden; revíselo a mano.', rol;
        END IF;
        IF EXISTS (
            SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.member WHERE r.rolname = rol
        ) THEN
            RAISE EXCEPTION 'El rol % es miembro de otros roles; revíselo a mano.', rol;
        END IF;
    END LOOP;

    -- El rol de la aplicación no puede ser dueño de ninguna base (V2-DA-20).
    IF EXISTS (
        SELECT 1 FROM pg_database d JOIN pg_roles r ON r.oid = d.datdba WHERE r.rolname = 'barberturno_v2_app'
    ) THEN
        RAISE EXCEPTION 'barberturno_v2_app es dueño de alguna base; revíselo a mano.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_database
        WHERE datname IN ('barberturno_v2', 'barberturno_v2_test', 'barberturno_v2_migracion')
          AND pg_get_userbyid(datdba) <> 'barberturno_v2'
    ) THEN
        RAISE EXCEPTION 'Una base de v2 existente tiene otro dueño; no se cambiará su propietario.';
    END IF;
END
$$;

-- Roles nuevos (solo si faltan). Sin herencia ni privilegios especiales.
SELECT format('CREATE ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', nombre)
FROM (VALUES ('barberturno_v2'), ('barberturno_v2_app')) AS roles(nombre)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = nombre)
ORDER BY nombre
\gexec

-- Contraseñas: se guardan los verificadores tal cual (PostgreSQL reconoce el formato SCRAM).
ALTER ROLE barberturno_v2 PASSWORD :'ver_propietario';
ALTER ROLE barberturno_v2_app PASSWORD :'ver_app';

-- CREATE DATABASE no admite un bloque transaccional; generar solo lo que falte.
SELECT format('CREATE DATABASE %I OWNER barberturno_v2', nombre)
FROM (VALUES ('barberturno_v2'), ('barberturno_v2_test'), ('barberturno_v2_migracion')) AS bases(nombre)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = nombre)
ORDER BY nombre
\gexec

-- Solo los dos roles de v2 pueden conectarse (el rol de v1 y PUBLIC, no).
REVOKE ALL ON DATABASE barberturno_v2, barberturno_v2_test, barberturno_v2_migracion FROM PUBLIC;
GRANT CONNECT ON DATABASE barberturno_v2, barberturno_v2_test, barberturno_v2_migracion TO barberturno_v2_app;
-- Los permisos sobre el esquema y las tablas los concede la migración V100 (versionados, V2-DA-20).

\unset ver_propietario
\unset ver_app
\set QUIET off
SELECT r.rolname AS rol,
       r.rolcanlogin AS login,
       r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls AS privilegios_especiales,
       (SELECT count(*) FROM pg_database d WHERE d.datdba = r.oid) AS bases_propias
FROM pg_roles r
WHERE r.rolname IN ('barberturno_v2', 'barberturno_v2_app')
ORDER BY r.rolname;

SELECT datname AS base, pg_get_userbyid(datdba) AS propietario, datacl AS permisos
FROM pg_database
WHERE datname IN ('barberturno_v2', 'barberturno_v2_test', 'barberturno_v2_migracion')
ORDER BY datname;
