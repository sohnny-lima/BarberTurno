package pe.barberturno.whatsapp;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de registrar un mensaje entrante en su conversación (ventana de 24 h, V2-09; ciclo de vida,
 * V2-10).
 * @param conversacionId conversación creada o actualizada en el negocio actual
 * @param entradaAnterior {@code ultima_entrada_en} antes de este mensaje; vacía si la conversación es nueva
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record EntradaRegistrada(long conversacionId, Optional<Instant> entradaAnterior) {
    /**
     * Exige la entrada anterior, aunque esté vacía.
     * @param conversacionId conversación
     * @param entradaAnterior entrada anterior; no nula
     * @throws NullPointerException si falta
     */
    public EntradaRegistrada {
        Objects.requireNonNull(entradaAnterior, "entradaAnterior");
    }
}
