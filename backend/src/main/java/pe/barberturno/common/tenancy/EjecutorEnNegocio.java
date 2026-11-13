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
        return delimitar(id, accion);
    }

    /**
     * Ejecuta la acción en el negocio que una tabla de plataforma ya identificó (por ejemplo, el
     * canal de WhatsApp del número receptor, V2-RN-02); nunca un identificador recibido del exterior.
     * @param negocioId negocio existente, leído de datos de la plataforma
     * @param accion trabajo que abre sus propias transacciones de servicio
     * @param <T> tipo del resultado del trabajo
     * @return resultado del trabajo
     * @throws IllegalStateException si existe una transacción abierta
     */
    public <T> T ejecutar(long negocioId, Supplier<T> accion) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El negocio debe fijarse antes de abrir la transacción.");
        }
        return delimitar(OptionalLong.of(negocioId), accion);
    }

    private <T> T delimitar(OptionalLong id, Supplier<T> accion) {
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
