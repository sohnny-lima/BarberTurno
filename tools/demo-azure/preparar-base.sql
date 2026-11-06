-- Solo mediante preparar-base.ps1. No contiene ni recibe contraseñas en claro.
\set ON_ERROR_STOP on
\set ECHO none
\set QUIET on
SELECT :'accion' = 'preparar' AS preparar, :'accion' = 'limpiar' AS limpiar, :'accion' = 'informe' AS informe \gset
\if :preparar
\ir validar-base.sql
\getenv ver_propietario BT_DEMO_SCRAM_PROPIETARIO
\getenv ver_app BT_DEMO_SCRAM_APP
SELECT :'ver_propietario' ~ '^SCRAM-SHA-256\$4096:[A-Za-z0-9+/]+={0,2}\$[A-Za-z0-9+/]+={0,2}:[A-Za-z0-9+/]+={0,2}$'
   AND :'ver_app' ~ '^SCRAM-SHA-256\$4096:[A-Za-z0-9+/]+={0,2}\$[A-Za-z0-9+/]+={0,2}:[A-Za-z0-9+/]+={0,2}$'
   AND :'ver_propietario' <> :'ver_app' AS verificadores_validos \gset
\if :verificadores_validos
\else
DO $$ BEGIN RAISE EXCEPTION 'Verificadores SCRAM inválidos.'; END $$;
\endif
SELECT format('CREATE ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', nombre)
FROM (VALUES ('bt_v2_demo'), ('bt_v2_demo_app')) r(nombre)
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = nombre) \gexec
ALTER ROLE bt_v2_demo PASSWORD :'ver_propietario';
ALTER ROLE bt_v2_demo_app PASSWORD :'ver_app';
-- PG16+: concesión propia separada de la automática, solo SET, sin ADMIN/INHERIT.
GRANT bt_v2_demo TO btadmin WITH ADMIN FALSE, INHERIT FALSE, SET TRUE GRANTED BY btadmin;
SELECT 'CREATE DATABASE barberturno_v2_demo OWNER bt_v2_demo'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'barberturno_v2_demo') \gexec
SET ROLE bt_v2_demo;
REVOKE ALL ON DATABASE barberturno_v2_demo FROM PUBLIC;
GRANT CONNECT ON DATABASE barberturno_v2_demo TO bt_v2_demo_app;
-- Necesario para crear la extensión como admin tras revocar CONNECT de PUBLIC.
GRANT CONNECT ON DATABASE barberturno_v2_demo TO btadmin;
RESET ROLE;
-- La conexión vuelve como btadmin, no como el dueño que ejecuta Flyway.
\connect barberturno_v2_demo
CREATE EXTENSION IF NOT EXISTS btree_gist;
\endif

\if :limpiar
SELECT 'SET ROLE bt_v2_demo; REVOKE CONNECT ON DATABASE barberturno_v2_demo FROM btadmin; RESET ROLE;'
WHERE EXISTS (SELECT FROM pg_database WHERE datname = 'barberturno_v2_demo' AND pg_get_userbyid(datdba) = 'bt_v2_demo')
  AND EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.roleid
    WHERE r.rolname = 'bt_v2_demo' AND m.member = (SELECT oid FROM pg_roles WHERE rolname = 'btadmin')
      AND m.grantor = m.member AND m.set_option) \gexec
-- No revocar la concesión automática del bootstrap: btadmin no puede eliminarla.
SELECT 'REVOKE bt_v2_demo FROM btadmin GRANTED BY btadmin'
WHERE EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.roleid
    WHERE r.rolname = 'bt_v2_demo' AND m.member = (SELECT oid FROM pg_roles WHERE rolname = 'btadmin')
      AND m.grantor = m.member) \gexec
\endif

\if :informe
\ir validar-base.sql
\set QUIET off
SELECT r.rolname AS rol, r.rolcanlogin AS login,
       r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls AS privilegios_especiales,
       (SELECT count(*) FROM pg_database d WHERE d.datdba = r.oid) AS bases_propias
FROM pg_roles r WHERE rolname IN ('bt_v2_demo', 'bt_v2_demo_app') ORDER BY rolname;
SELECT datname AS base, pg_get_userbyid(datdba) AS propietario, datacl AS permisos
FROM pg_database WHERE datname = 'barberturno_v2_demo';
SELECT NOT EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.roleid
    WHERE r.rolname IN ('bt_v2_demo', 'bt_v2_demo_app') AND (m.set_option OR m.inherit_option)) AS sin_set_ni_herencia_temporal;
\endif
