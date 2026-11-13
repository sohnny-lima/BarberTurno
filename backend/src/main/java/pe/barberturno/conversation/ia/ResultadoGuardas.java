package pe.barberturno.conversation.ia;

import java.util.Objects;

/**
 * Resultado de revisar el texto del modelo (17-v2-12 §5).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface ResultadoGuardas {
    /**
     * El texto puede enviarse tal cual.
     * @param texto texto aceptado
     */
    record Aceptado(String texto) implements ResultadoGuardas {
        /**
         * Valida.
         * @param texto texto aceptado
         * @throws NullPointerException si falta
         */
        public Aceptado {
            Objects.requireNonNull(texto, "texto");
        }
    }

    /**
     * El texto se descarta y se envía la composición del servidor.
     * @param motivo primera regla incumplida
     */
    record Sustituido(MotivoGuarda motivo) implements ResultadoGuardas {
        /**
         * Valida.
         * @param motivo regla incumplida
         * @throws NullPointerException si falta
         */
        public Sustituido {
            Objects.requireNonNull(motivo, "motivo");
        }
    }
}
