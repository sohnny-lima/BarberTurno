package pe.barberturno.conversation.ia;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mensaje de la conversación del turno con el modelo (17-v2-12 §4.1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface MensajeModelo {
    /**
     * Texto del cliente, ya saneado (sin teléfonos ni correos, 17-v2-12 §7).
     * @param texto texto saneado
     */
    record DelCliente(String texto) implements MensajeModelo {
        /**
         * Valida el texto.
         * @param texto texto saneado
         * @throws NullPointerException si falta
         */
        public DelCliente {
            Objects.requireNonNull(texto, "texto");
        }
    }

    /**
     * Respuesta anterior del modelo en este turno, que se le devuelve intacta.
     * @param texto texto del modelo, si lo hubo
     * @param llamadas herramientas que pidió
     * @param continuacion contenido opaco del proveedor
     */
    record DelAsistente(Optional<String> texto, List<LlamadaHerramienta> llamadas,
            ContinuacionProveedor continuacion) implements MensajeModelo {
        /**
         * Valida y copia.
         * @param texto texto del modelo, si lo hubo
         * @param llamadas herramientas que pidió
         * @param continuacion contenido opaco del proveedor
         * @throws NullPointerException si falta algún valor
         */
        public DelAsistente {
            Objects.requireNonNull(texto, "texto");
            llamadas = List.copyOf(llamadas);
            Objects.requireNonNull(continuacion, "continuacion");
        }
    }

    /**
     * Resultados de todas las herramientas de una respuesta, juntos y con sus ids (consulta 127-9).
     * @param resultados un resultado por llamada
     */
    record ResultadosHerramientas(List<ResultadoHerramienta> resultados) implements MensajeModelo {
        /**
         * Copia la lista.
         * @param resultados un resultado por llamada
         */
        public ResultadosHerramientas {
            resultados = List.copyOf(resultados);
        }
    }
}
