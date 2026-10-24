package pe.barberturno.common.tenancy;

import java.util.OptionalLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

/** Solo fixtures: HTTP siempre lee el holder real, incluso vacío en las rutas globales. */
@Component
@Primary
class ProveedorFixturesLegados implements ProveedorNegocioActual {
    private final ContextoNegocio contexto;
    private final ObjectProvider<ProveedorNegocioProgramable> programable;
    private final Environment entorno;
    ProveedorFixturesLegados(ContextoNegocio contexto, ObjectProvider<ProveedorNegocioProgramable> programable,
            Environment entorno) {
        this.contexto = contexto; this.programable = programable; this.entorno = entorno;
    }
    @Override public OptionalLong negocioActual() {
        if (contexto.delimitado()) return contexto.negocioActual();
        var proveedor = programable.getIfAvailable();
        return proveedor == null ? OptionalLong.of(entorno.getProperty("pruebas.negocio-id", Long.class, 1L))
                : proveedor.negocioActual();
    }
}
