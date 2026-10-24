-- V2-DA-32: la identidad global puede trabajar en varios negocios.
ALTER TABLE barbero DROP CONSTRAINT barbero_usuario_id_key;
ALTER TABLE barbero ADD CONSTRAINT barbero_usuario_uk UNIQUE (negocio_id, usuario_id);
