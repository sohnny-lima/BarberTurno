package pe.barberturno.common.error;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rechazo previsto de negocio; sus detalles deben ser aptos para el cliente.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class NegocioException extends RuntimeException {
    private static final Set<String> RESERVADAS = Set.of(
            "type", "title", "status", "detail", "instance", "codigo", "errores");
    private final ErrorCodigo codigo;
    private final Map<String, Object> detalles;

    /**
     * @param codigo código estable del rechazo
     * @param detalle explicación pública, sin datos sensibles ni detalles técnicos
     */
    public NegocioException(ErrorCodigo codigo, String detalle) {
        this(codigo, detalle, Map.of());
    }

    /**
     * @param codigo código estable del rechazo
     * @param detalle explicación pública
     * @param detalles extensiones públicas, por ejemplo reservas con sus ids
     * @throws IllegalArgumentException si se intenta sustituir una propiedad estándar
     */
    public NegocioException(ErrorCodigo codigo, String detalle, Map<String, Object> detalles) {
        super(Objects.requireNonNull(detalle));
        this.codigo = Objects.requireNonNull(codigo);
        this.detalles = Map.copyOf(detalles);
        if (this.detalles.keySet().stream().anyMatch(RESERVADAS::contains)) {
            throw new IllegalArgumentException("Las extensiones no pueden sustituir el formato de error.");
        }
    }

    /** @return código estable */
    public ErrorCodigo codigo() { return codigo; }

    /** @return extensiones públicas no modificables (copia superficial) */
    public Map<String, Object> detalles() { return detalles; }
}
