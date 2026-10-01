package pe.barberturno.catalog.dto;

import java.math.BigDecimal;
import pe.barberturno.catalog.Servicio;

/** Datos públicos de un servicio del catálogo. */
public record ServicioDto(Long id, String nombre, String descripcion, short duracionMin,
        BigDecimal precio, boolean activo) {
    /** @param servicio entidad persistida
     * @return representación pública sin fechas internas */
    public static ServicioDto desde(Servicio servicio) {
        return new ServicioDto(servicio.getId(), servicio.getNombre(), servicio.getDescripcion(),
                servicio.getDuracionMin(), servicio.getPrecio().setScale(2), servicio.isActivo());
    }
}
