package pe.barberturno.common.error;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import pe.barberturno.common.error.ManejadorErrores.ErrorCampo;

/**
 * Rechazo previsto de negocio; sus detalles deben ser aptos para el cliente.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class NegocioException extends RuntimeException {
    private static final Set<String> RESERVADAS = Set.of(
            "type", "title", "status", "detail", "instance", "codigo", "errores");
    /**
     * Campos públicos de validación; necesarios también en la documentación de serialización.
     */
    private final List<ErrorCampo> errores;
    /**
     * Código estable del rechazo serializado; no contiene datos privados.
     */
    private final ErrorCodigo codigo;
    /**
     * Extensiones copiadas sin propiedades reservadas de RFC 9457.
     */
    private final Map<String, Object> detalles;

    /**
     * Construye un rechazo público RFC 9457 con propiedades reservadas protegidas y colecciones copiadas.
     * @param codigo código estable del rechazo
     * @param detalle explicación pública, sin datos sensibles ni detalles técnicos
     */
    public NegocioException(ErrorCodigo codigo, String detalle) {
        this(codigo, detalle, Map.of());
    }

    /**
     * Construye un rechazo público RFC 9457 con propiedades reservadas protegidas y colecciones copiadas.
     * @param codigo código estable del rechazo
     * @param detalle explicación pública
     * @param detalles extensiones públicas, por ejemplo reservas con sus ids
     * @throws IllegalArgumentException si se intenta sustituir una propiedad estándar
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public NegocioException(ErrorCodigo codigo, String detalle, Map<String, Object> detalles) {
        super(Objects.requireNonNull(detalle));
        this.codigo = Objects.requireNonNull(codigo);
        this.errores = List.of();
        this.detalles = Map.copyOf(detalles);
        if (this.detalles.keySet().stream().anyMatch(RESERVADAS::contains)) {
            throw new IllegalArgumentException("Las extensiones no pueden sustituir el formato de error.");
        }
    }

    /**
     * Construye un rechazo público RFC 9457 con propiedades reservadas protegidas y colecciones copiadas.
     * @param detalle explicación pública sin valores rechazados
     * @param errores campos y mensajes públicos; se copia la lista
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public NegocioException(String detalle, List<ErrorCampo> errores) {
        super(Objects.requireNonNull(detalle));
        this.codigo = ErrorCodigo.VALIDACION;
        this.detalles = Map.of();
        this.errores = List.copyOf(errores);
    }

    /**
     * Lista inmutable de campos inválidos sin valores rechazados; vacía para rechazos sin validación.
     * @return lista inmutable de campos inválidos; vacía si no es validación
     */
    public List<ErrorCampo> errores() { return errores; }

    /**
     * Código estable que determina estado HTTP y título según arquitectura §6.2.
     * @return código estable que clasifica este rechazo público
     */
    public ErrorCodigo codigo() { return codigo; }

    /**
     * Extensiones públicas con copia superficial no modificable; no sustituyen propiedades estándar RFC 9457.
     * @return mapa superficial no modificable de extensiones públicas
     */
    public Map<String, Object> detalles() { return detalles; }
}
