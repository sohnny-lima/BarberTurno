package pe.barberturno.catalog.dto;

import java.math.BigDecimal;
import pe.barberturno.catalog.Servicio;

/**
 * Representación pública del catálogo RF-04, con duración en minutos y precio referencial en soles (RN-23).
 * @param id identificador persistido del servicio
 * @param nombre nombre visible no nulo
 * @param descripcion detalle visible del servicio, no nulo, de hasta 300 caracteres
 * @param duracionMin duración del catálogo entre 10 y 180 minutos, múltiplo de diez
 * @param precio precio referencial no negativo en soles con dos decimales
 * @param activo estado de habilitación lógica; false conserva la fila histórica según RN-16
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ServicioDto(Long id, String nombre, String descripcion, short duracionMin,
        BigDecimal precio, boolean activo) {
    /**
     * Proyecta el catálogo sin fechas internas y fija dos decimales en el precio referencial RF-04.
     * @param servicio entidad persistida
     * @return representación pública sin fechas internas
     * @throws ArithmeticException si el precio contiene fracciones no representables con dos decimales
     */
    public static ServicioDto desde(Servicio servicio) {
        return new ServicioDto(servicio.getId(), servicio.getNombre(), servicio.getDescripcion(),
                servicio.getDuracionMin(), servicio.getPrecio().setScale(2), servicio.isActivo());
    }
}
