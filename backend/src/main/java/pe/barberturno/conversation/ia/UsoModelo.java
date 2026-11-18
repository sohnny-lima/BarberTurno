package pe.barberturno.conversation.ia;

import java.util.Objects;

/**
 * Uso facturable de una llamada (17-v2-12 §4.1 y §6). La entrada no incluye los tokens leídos ni escritos en caché.
 * @param modelo identificador del modelo que respondió
 * @param entrada tokens de entrada no cacheados
 * @param salida tokens de salida (incluido el razonamiento)
 * @param cacheLeida tokens leídos de la caché
 * @param cacheEscrita tokens escritos en la caché
 * @param duracionMs duración de la llamada en milisegundos
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record UsoModelo(String modelo, long entrada, long salida, long cacheLeida, long cacheEscrita, long duracionMs) {
    /**
     * Valida.
     * @param modelo modelo que respondió
     * @param entrada tokens de entrada
     * @param salida tokens de salida
     * @param cacheLeida tokens leídos de caché
     * @param cacheEscrita tokens escritos en caché
     * @param duracionMs duración
     * @throws NullPointerException si falta el modelo
     * @throws IllegalArgumentException si algún contador es negativo
     */
    public UsoModelo {
        Objects.requireNonNull(modelo, "modelo");
        if (entrada < 0 || salida < 0 || cacheLeida < 0 || cacheEscrita < 0 || duracionMs < 0) {
            throw new IllegalArgumentException("Uso negativo.");
        }
    }
}
