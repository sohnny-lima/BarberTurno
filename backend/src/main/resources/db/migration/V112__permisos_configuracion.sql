-- V2-06.5: la aplicación solo actualiza configuración y estado de plataforma.
-- INSERT de plataforma y SELECT se conservan; los identificadores no son editables.
REVOKE UPDATE ON negocio FROM "${rolAplicacion}";
GRANT UPDATE (nombre, estado, anticipacion_cambio_cliente_min, horizonte_dias,
    limite_reservas_activas, limite_barberos_activos, confirmacion_manual,
    tolerancia_inicio_min, actualizado_en) ON negocio TO "${rolAplicacion}";
