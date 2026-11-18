-- V2-DA-37: hasta V107 la única baja funcional era la desactivación del perfil (V2-04).
ALTER TABLE membresia ADD COLUMN motivo_revocacion varchar(20);

UPDATE membresia SET motivo_revocacion = 'PERFIL_DESACTIVADO' WHERE estado = 'REVOCADA';

ALTER TABLE membresia
    ADD CONSTRAINT membresia_motivo_valido CHECK (motivo_revocacion IN
        ('PERFIL_DESACTIVADO', 'SALIDA', 'DECISION_GESTOR', 'PLATAFORMA')),
    ADD CONSTRAINT membresia_motivo_coherente CHECK
        ((estado = 'REVOCADA') = (motivo_revocacion IS NOT NULL));
