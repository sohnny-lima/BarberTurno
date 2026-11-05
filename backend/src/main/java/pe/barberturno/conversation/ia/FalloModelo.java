package pe.barberturno.conversation.ia;

import java.util.Objects;
import java.util.Optional;

/**
 * Fallo de una llamada al modelo (17-v2-12 §4.1). Lleva el uso facturable cuando el proveedor lo informó, para
 * liquidarlo aunque el turno falle (consulta 127-14).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class FalloModelo extends RuntimeException {
    /** Versión de la forma serializada. */
    private static final long serialVersionUID = 1L;
    /** Por qué falló la llamada. */
    private final MotivoFalloModelo motivo;
    /** Uso facturable conocido, o nulo (no se serializa). */
    private final transient UsoModelo uso;

    /**
     * Crea el fallo.
     * @param motivo por qué falló
     * @param uso uso facturable conocido, o vacío
     * @param causa causa técnica, o nula (nunca con contenido de la conversación en su mensaje)
     */
    public FalloModelo(MotivoFalloModelo motivo, Optional<UsoModelo> uso, Throwable causa) {
        super("Fallo del modelo: " + motivo, causa);
        this.motivo = Objects.requireNonNull(motivo, "motivo");
        this.uso = uso.orElse(null);
    }

    /**
     * Por qué falló la llamada.
     * @return motivo
     */
    public MotivoFalloModelo motivo() {
        return motivo;
    }

    /**
     * Uso facturable que el proveedor informó antes de fallar.
     * @return uso, o vacío si no se conoce
     */
    public Optional<UsoModelo> uso() {
        return Optional.ofNullable(uso);
    }
}
