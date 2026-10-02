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

    /**
     * Valida índice y tamaño del contrato §6.1 y copia contenido para impedir modificaciones posteriores.
     * @param contenido lista no nula de elementos no nulos; se copia
     * @param pagina índice de página desde cero, no negativo
     * @param tamano tamaño entre 1 y 100 elementos
     * @param totalElementos número total de elementos del resultado
     * @param totalPaginas número total de páginas del resultado
     * @throws NegocioException si pagina es negativa o tamano está fuera de 1 a 100 (VALIDACION)
     */
    public PaginaDto {
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new NegocioException(ErrorCodigo.VALIDACION,
                    "La página debe ser al menos 0 y el tamaño debe estar entre 1 y 100.");
        }
        contenido = List.copyOf(contenido);
    }

    /**
     * Adapta una página Spring preservando metadatos y el límite público de cien elementos.
     * @param pagina resultado paginado del repositorio
     * @param <T> tipo de elemento
     * @return representación con los metadatos originales
     * @throws NegocioException si la página de origen tiene índice negativo o tamaño fuera de 1 a 100
     * (VALIDACION)
     */
    public static <T> PaginaDto<T> desde(Page<T> pagina) {
        return new PaginaDto<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
