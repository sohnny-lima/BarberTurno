-- Resolución autorizada de H-092-01: auditoría atómica de la invitación del propietario.
ALTER TABLE auditoria_plataforma
    DROP CONSTRAINT auditoria_plataforma_accion_valida,
    DROP CONSTRAINT auditoria_plataforma_destino_coherente,
    ADD CONSTRAINT auditoria_plataforma_accion_valida CHECK (accion IN
        ('CREAR_NEGOCIO', 'LISTAR_NEGOCIOS', 'SUSPENDER_NEGOCIO', 'REACTIVAR_NEGOCIO',
         'RESTABLECER_PASSWORD', 'REENVIAR_INVITACION_PROPIETARIO', 'REVOCAR_INVITACION_PROPIETARIO')),
    ADD CONSTRAINT auditoria_plataforma_destino_coherente CHECK (
        (accion = 'LISTAR_NEGOCIOS' AND negocio_id IS NULL AND objetivo_id IS NULL) OR
        (accion IN ('CREAR_NEGOCIO', 'SUSPENDER_NEGOCIO', 'REACTIVAR_NEGOCIO',
                   'REENVIAR_INVITACION_PROPIETARIO', 'REVOCAR_INVITACION_PROPIETARIO')
         AND negocio_id IS NOT NULL AND objetivo_id IS NULL) OR
        (accion = 'RESTABLECER_PASSWORD' AND negocio_id IS NULL AND objetivo_id IS NOT NULL));
