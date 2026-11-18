-- V2-02: identidad global y pertenencia explícita; usuario.rol se conserva durante la expansión.
ALTER TABLE usuario ADD COLUMN rol_plataforma varchar(16) NOT NULL DEFAULT 'NINGUNO',
    ADD CONSTRAINT usuario_rol_plataforma_valido CHECK (rol_plataforma IN ('NINGUNO', 'ADMIN_PLATAFORMA'));

-- Tabla de plataforma SIN RLS (V2-DA-24); el repositorio limita toda consulta a un negocio.
CREATE TABLE membresia (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    negocio_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    rol varchar(12) NOT NULL,
    estado varchar(10) NOT NULL DEFAULT 'ACTIVA',
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT membresia_negocio_fk FOREIGN KEY (negocio_id) REFERENCES negocio(id),
    CONSTRAINT membresia_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT membresia_rol_valido CHECK (rol IN ('PROPIETARIO', 'ENCARGADO', 'BARBERO')),
    CONSTRAINT membresia_estado_valido CHECK (estado IN ('ACTIVA', 'REVOCADA')),
    CONSTRAINT membresia_unica_uk UNIQUE (negocio_id, usuario_id)
);

-- Las identidades inactivas conservan su pertenencia; la autorización exige también cuenta activa.
INSERT INTO membresia (negocio_id, usuario_id, rol, estado, creado_en, actualizado_en)
SELECT n.id, u.id, CASE u.rol WHEN 'ADMIN' THEN 'PROPIETARIO' ELSE 'BARBERO' END,
    'ACTIVA', u.creado_en, u.actualizado_en
FROM usuario u CROSS JOIN negocio n
WHERE n.slug = '${negocioUnicoSlug}' AND u.rol IN ('ADMIN', 'BARBERO');

-- Los privilegios por defecto de V100 conceden DML y USAGE/SELECT de la secuencia al rol de aplicación.
-- No se conceden TRUNCATE, REFERENCES, TRIGGER ni CREATE.
