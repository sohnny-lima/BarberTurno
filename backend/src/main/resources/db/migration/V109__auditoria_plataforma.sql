-- Operaciones globales de V2-06.2; solo identificadores, nunca credenciales ni datos personales.
CREATE TABLE auditoria_plataforma (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id bigint NOT NULL,
    accion varchar(32) NOT NULL,
    negocio_id bigint,
    objetivo_id bigint,
    creado_en timestamptz NOT NULL,
    CONSTRAINT auditoria_plataforma_actor_fk FOREIGN KEY (actor_id) REFERENCES usuario(id),
    CONSTRAINT auditoria_plataforma_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT auditoria_plataforma_objetivo_fk FOREIGN KEY (objetivo_id) REFERENCES usuario(id),
    CONSTRAINT auditoria_plataforma_accion_valida CHECK (accion IN
        ('CREAR_NEGOCIO', 'LISTAR_NEGOCIOS', 'SUSPENDER_NEGOCIO', 'REACTIVAR_NEGOCIO', 'RESTABLECER_PASSWORD')),
    CONSTRAINT auditoria_plataforma_destino_coherente CHECK (
        (accion = 'LISTAR_NEGOCIOS' AND negocio_id IS NULL AND objetivo_id IS NULL) OR
        (accion IN ('CREAR_NEGOCIO', 'SUSPENDER_NEGOCIO', 'REACTIVAR_NEGOCIO') AND negocio_id IS NOT NULL AND objetivo_id IS NULL) OR
        (accion = 'RESTABLECER_PASSWORD' AND negocio_id IS NULL AND objetivo_id IS NOT NULL))
);
CREATE INDEX auditoria_plataforma_negocio_ix ON auditoria_plataforma (negocio_id, creado_en);
-- V2-DA-20: la aplicación recibe INSERT de negocio al llegar a V2-06, sin propiedad ni privilegios de DDL.
GRANT INSERT, UPDATE ON negocio TO "${rolAplicacion}";
-- El registro es acumulativo; no se permiten cambios ni borrados por la aplicación.
REVOKE UPDATE, DELETE ON auditoria_plataforma FROM "${rolAplicacion}";
GRANT SELECT, INSERT ON auditoria_plataforma TO "${rolAplicacion}";
