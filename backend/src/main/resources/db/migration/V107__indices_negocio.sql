-- V2-05: accesos G y accesos por padre, manteniendo RLS FORCE y los índices anteriores.
CREATE INDEX reserva_negocio_barbero_inicio_ix ON reserva (negocio_id, barbero_id, inicio);
CREATE INDEX reserva_negocio_cliente_inicio_ix ON reserva (negocio_id, cliente_id, inicio);
CREATE INDEX reserva_negocio_inicio_ix ON reserva (negocio_id, inicio);
CREATE INDEX servicio_negocio_activo_ix ON servicio (negocio_id, activo);
CREATE INDEX barbero_negocio_activo_ix ON barbero (negocio_id, activo);
CREATE INDEX notificacion_negocio_usuario_ix ON notificacion (negocio_id, usuario_id, leida, creado_en DESC);
CREATE INDEX jornada_negocio_barbero_ix ON jornada (negocio_id, barbero_id, dia_semana);
CREATE INDEX bloqueo_negocio_barbero_ix ON bloqueo (negocio_id, barbero_id, inicio, fin);
CREATE INDEX auditoria_reserva_negocio_reserva_ix ON auditoria_reserva (negocio_id, reserva_id, creado_en);
