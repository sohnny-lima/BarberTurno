package pe.barberturno.common.tenancy;

import java.util.OptionalLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Delimita trabajos sin petición por slug explícito antes de abrir sus transacciones.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
public class EjecutorEnNegocio {
    private final ContextoNegocio contexto;
    private final DirectorioNegocios directorio;

    /**
     * Comparte el holder de producción y la resolución de plataforma sin caché.
     * @param contexto ámbito del hilo
     * @param directorio búsqueda del slug configurado
     */
    public EjecutorEnNegocio(ContextoNegocio contexto, DirectorioNegocios directorio) {
        this.contexto = contexto;
        this.directorio = directorio;
    }

    /**
     * Ejecuta la acción y restaura el ámbito aun con excepción; nunca cambia una transacción abierta.
     * @param slug negocio configurado explícitamente
     * @param accion trabajo que abre sus propias transacciones de servicio
     * @param <T> tipo del resultado del trabajo
     * @return resultado del trabajo
     * @throws IllegalStateException si existe una transacción abierta o el slug no existe
     */
    public <T> T ejecutar(String slug, Supplier<T> accion) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El negocio debe fijarse antes de abrir la transacción.");
        }
        OptionalLong id = directorio.buscar(slug);
        if (id.isEmpty()) throw new IllegalStateException("No existe el negocio configurado para la tarea.");
        boolean delimitado = contexto.delimitado();
        var anterior = contexto.negocioActual();
        contexto.fijar(id);
        try {
            return accion.get();
        } finally {
            if (delimitado) contexto.fijar(anterior);
            else contexto.limpiar();
        }
    }
}
