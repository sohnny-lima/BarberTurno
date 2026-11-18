package pe.barberturno.common.tenancy;

import java.util.OptionalLong;

/** Exclusivo de pruebas: cada hilo programa el ámbito antes de abrir su transacción. */
final class ProveedorNegocioProgramable implements ProveedorNegocioActual {
    private final ThreadLocal<OptionalLong> actual = ThreadLocal.withInitial(() -> OptionalLong.of(1));

    void fijar(Long id) {
        actual.set(id == null ? OptionalLong.empty() : OptionalLong.of(id));
    }

    void reiniciar() {
        actual.remove();
    }

    @Override
    public OptionalLong negocioActual() {
        return actual.get();
    }
}
