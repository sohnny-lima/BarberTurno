package pe.barberturno.whatsapp;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Resultado clasificado de una petición de envío a la Graph API (12-v2-09-whatsapp-salida.md §4.1). La
 * distinción entre {@link NoEnviado} y {@link Desconocido} decide si un reintento es seguro (V2-DA-58).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface ResultadoEnvio {

    /**
     * Meta aceptó el mensaje y devolvió su identificador.
     * @param wamid identificador de WhatsApp del mensaje ({@code messages[0].id}), no en blanco y de hasta
     * 128 caracteres
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    record Aceptado(String wamid) implements ResultadoEnvio {
        /**
         * Exige un identificador utilizable.
         * @param wamid identificador devuelto por Meta
         * @throws IllegalArgumentException si es nulo, está en blanco o supera 128 caracteres
         */
        public Aceptado {
            if (wamid == null || wamid.isBlank() || wamid.length() > 128) {
                throw new IllegalArgumentException("wamid inválido");
            }
        }
    }

    /**
     * Meta respondió con un error.
     * @param estadoHttp código HTTP de la respuesta
     * @param codigoMeta {@code error.code} de Meta, o vacío si la respuesta no lo traía
     * @param transitorio {@code true} si conviene reintentar con espera (§4.2)
     * @param motivo motivo permanente; obligatorio si no es transitorio y nulo si lo es
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    record Rechazado(int estadoHttp, OptionalInt codigoMeta, boolean transitorio, MotivoFallo motivo)
            implements ResultadoEnvio {
        /**
         * Exige coherencia entre la transitoriedad y el motivo.
         * @param estadoHttp código HTTP
         * @param codigoMeta código de Meta o vacío
         * @param transitorio si conviene reintentar
         * @param motivo motivo permanente o {@code null}
         * @throws IllegalArgumentException si un rechazo permanente no tiene motivo o uno transitorio lo tiene
         */
        public Rechazado {
            Objects.requireNonNull(codigoMeta, "codigoMeta");
            if (transitorio == (motivo != null)) {
                throw new IllegalArgumentException("Un rechazo permanente exige motivo y uno transitorio no lo lleva.");
            }
        }
    }

    /**
     * La petición no llegó a Meta (conexión rechazada o agotada al conectar): reintentar no puede duplicar
     * el mensaje.
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    record NoEnviado() implements ResultadoEnvio { }

    /**
     * La petición pudo llegar a Meta y no hay respuesta válida (tiempo de respuesta agotado, conexión
     * cortada o éxito sin identificador): reenviar podría duplicar el mensaje.
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    record Desconocido() implements ResultadoEnvio { }
}
