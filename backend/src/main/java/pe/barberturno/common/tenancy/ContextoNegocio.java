package pe.barberturno.common.tenancy;

import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * Ámbito del hilo que procesa la petición, sin negocio predeterminado ni propagación a otros hilos.
 * El resolutor elimina el valor en finally; el ejecutor explícito restaura el ámbito anterior.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
public final class ContextoNegocio implements ProveedorNegocioActual {
    private final ThreadLocal<OptionalLong> actual = new ThreadLocal<>();

    /** Crea un holder vacío, compartido sin compartir valores entre hilos. */
    public ContextoNegocio() { }

    /**
     * Indica si un resolutor o ejecutor delimitó el ámbito, incluso para identidad global sin negocio.
     * @return existencia de una delimitación en este hilo
     */
    public boolean delimitado() { return actual.get() != null; }

    /**
     * Entrega el negocio de la operación actual sin consultar cachés o datos del navegador.
     * @return identificador resuelto o vacío para operaciones globales y ausencia de petición
     */
    @Override
    public OptionalLong negocioActual() {
        var valor = actual.get();
        return valor == null ? OptionalLong.empty() : valor;
    }

    void fijar(OptionalLong id) { actual.set(id); }
    void limpiar() { actual.remove(); }
}
