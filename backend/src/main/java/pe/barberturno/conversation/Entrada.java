package pe.barberturno.conversation;

/**
 * Mensaje entrante ya interpretado de forma determinista, sin IA (13-v2-10-conversacion-guiada.md §3.1).
 * Lo produce {@link InterpreteEntradas} y lo enruta {@code OrquestadorConversacion}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface Entrada {

    /**
     * Pulsación de un botón o de una fila de lista cuyo identificador se decodificó sin errores.
     * El identificador no es de confianza: el servidor revalida todo lo que nombra.
     * @param codigo selección decodificada
     */
    record Seleccion(CodigoSeleccion codigo) implements Entrada {
        /**
         * Exige la selección.
         * @param codigo selección decodificada; no nula
         * @throws NullPointerException si falta
         */
        public Seleccion {
            java.util.Objects.requireNonNull(codigo, "codigo");
        }
    }

    /**
     * Texto escrito por el cliente reducido a una palabra clave; el texto original no se conserva.
     * @param clave palabra clave reconocida u {@link PalabraClave#OTRA}
     */
    record Texto(PalabraClave clave) implements Entrada {
        /**
         * Exige la palabra clave.
         * @param clave palabra reconocida; no nula
         * @throws NullPointerException si falta
         */
        public Texto {
            java.util.Objects.requireNonNull(clave, "clave");
        }
    }

    /**
     * Mensaje de un tipo que la conversación guiada no entiende (audio, imagen, ubicación…) o una
     * pulsación con un identificador inválido; se responde con el menú (06-flujos.md §10).
     * @param tipo tipo de Meta recibido, solo para registro sin datos personales
     */
    record NoSoportada(String tipo) implements Entrada { }
}
