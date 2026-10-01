package pe.barberturno.common.web;

import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;

/**
 * Representación pública de una página; los controladores usarán pagina=0 y tamano=20
 * como valores predeterminados y validarán esos parámetros antes de consultar.
 * @param <T> tipo de elemento
 * @param contenido elementos de esta página
 * @param pagina índice desde cero
 * @param tamano tamaño solicitado, entre 1 y 100
 * @param totalElementos total de elementos
 * @param totalPaginas total de páginas
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record PaginaDto<T>(List<T> contenido, @Min(value = 0, message = "La página debe ser al menos 0.") int pagina,
        @Min(value = 1, message = "El tamaño debe ser positivo.")
        @Max(value = 100, message = "El tamaño no puede superar 100.") int tamano, long totalElementos, int totalPaginas) {

    public PaginaDto {
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new NegocioException(ErrorCodigo.VALIDACION,
                    "La página debe ser al menos 0 y el tamaño debe estar entre 1 y 100.");
        }
        contenido = List.copyOf(contenido);
    }

    /**
     * @param pagina resultado paginado del repositorio
     * @param <T> tipo de elemento
     * @return representación con los metadatos originales
     * @throws NegocioException si el tamaño o el índice no son válidos
     */
    public static <T> PaginaDto<T> desde(Page<T> pagina) {
        return new PaginaDto<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
