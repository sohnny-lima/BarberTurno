-- V2-03: una cuenta inactiva conserva su relación con el negocio, como las membresías de V101.
-- Corregir el relleno de V102 sin editar una migración ya aplicada. La autenticación sigue
-- exigiendo usuario.activo; ficha.activo es un estado local independiente de la identidad.
SELECT set_config('app.negocio_id', id::text, true) FROM negocio WHERE slug = '${negocioUnicoSlug}';
UPDATE cliente c SET activo = true
FROM usuario u
WHERE c.negocio_id = app_negocio_actual() AND c.usuario_id = u.id AND c.id = u.id
    AND c.creado_en = u.creado_en AND u.rol = 'CLIENTE' AND NOT c.activo;
-- No cambia ninguna columna de v1 ni las marcas temporales de la copia inicial.
