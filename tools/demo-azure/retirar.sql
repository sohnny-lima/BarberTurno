-- Solo mediante retirar.ps1. No se usan FORCE, CASCADE ni REASSIGN/DROP OWNED.
\set ON_ERROR_STOP on
\set ECHO none
\set QUIET on
\ir validar-base.sql
-- Abortamos también ante permisos/dependencias fuera de la demo: no se intentarán corregir.
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_shdepend s JOIN pg_roles r ON r.oid = s.refobjid
        WHERE s.refclassid = 'pg_authid'::regclass AND r.rolname IN ('bt_v2_demo', 'bt_v2_demo_app')
          AND NOT ((s.dbid <> 0 AND s.dbid = coalesce((SELECT oid FROM pg_database WHERE datname = 'barberturno_v2_demo'), 0))
            OR (s.classid = 'pg_database'::regclass AND s.objid = coalesce((SELECT oid FROM pg_database WHERE datname = 'barberturno_v2_demo'), 0))))
    THEN RAISE EXCEPTION 'Hay dependencias fuera de la base demo; no se retirará.'; END IF;
END $$;
SELECT :'accion' = 'retirar' AS retirar \gset
\if :retirar
-- La app ya debe estar detenida. No se terminan sesiones de otras bases.
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_stat_activity WHERE datname = 'barberturno_v2_demo')
    THEN RAISE EXCEPTION 'Quedan conexiones a la base demo. Espere al cierre de la app y reintente.'; END IF;
END $$;
SELECT 'GRANT bt_v2_demo TO btadmin WITH ADMIN FALSE, INHERIT FALSE, SET TRUE GRANTED BY btadmin; SET ROLE bt_v2_demo;'
WHERE EXISTS (SELECT FROM pg_database WHERE datname = 'barberturno_v2_demo') \gexec
DROP DATABASE IF EXISTS barberturno_v2_demo;
RESET ROLE;
SELECT 'REVOKE bt_v2_demo FROM btadmin GRANTED BY btadmin'
WHERE EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.roleid
    WHERE r.rolname = 'bt_v2_demo' AND m.member = (SELECT oid FROM pg_roles WHERE rolname = 'btadmin')
      AND m.grantor = m.member) \gexec
DROP ROLE IF EXISTS bt_v2_demo_app;
DROP ROLE IF EXISTS bt_v2_demo;
\set QUIET off
SELECT NOT EXISTS (SELECT FROM pg_database WHERE datname = 'barberturno_v2_demo') AS base_retirada,
       NOT EXISTS (SELECT FROM pg_roles WHERE rolname IN ('bt_v2_demo', 'bt_v2_demo_app')) AS roles_retirados;
\endif
