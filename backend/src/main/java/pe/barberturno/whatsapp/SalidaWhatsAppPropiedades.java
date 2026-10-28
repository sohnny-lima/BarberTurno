package pe.barberturno.whatsapp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la salida de WhatsApp ({@code barberturno.whatsapp.salida}, 12-v2-09-whatsapp-salida.md
 * §4.6). El token de Graph sale de {@code BT_WHATSAPP_ACCESS_TOKEN} y nunca se registra ni se devuelve; sin
 * él, el enviador no reclama mensajes.
 * @param habilitado activa el disparo periódico del enviador; {@code false} en las pruebas
 * @param intervalo espera entre dos pasadas del enviador
 * @param lote mensajes reclamados como máximo por negocio y pasada (1–500)
 * @param maxIntentos intentos de envío antes de {@code REINTENTOS_AGOTADOS} (1–100, cabe en {@code smallint})
 * @param esperaBase espera tras el primer error transitorio; se duplica en cada intento
 * @param esperaMaxima tope de la espera entre intentos
 * @param maxInciertos resultados desconocidos admitidos antes de {@code RESULTADO_DESCONOCIDO}; con 2, un
 * mensaje cuyo envío no se pudo confirmar se reenvía una sola vez (V2-DA-58)
 * @param esperaConciliacion espera tras un resultado desconocido, para que un aviso de estado concilie el
 * mensaje antes de reenviarlo
 * @param reclamo plazo tras el que un mensaje reclamado y no cerrado vuelve a estar disponible
 * @param antiguedadMaxima edad a partir de la cual un mensaje pendiente caduca sin enviarse
 * @param margenVentana margen que se resta a las 24 h de la ventana de atención para no apurarla
 * @param graph conexión con la Graph API
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@ConfigurationProperties("barberturno.whatsapp.salida")
public record SalidaWhatsAppPropiedades(@DefaultValue("true") boolean habilitado,
        @DefaultValue("2s") Duration intervalo, @DefaultValue("20") int lote, @DefaultValue("5") int maxIntentos,
        @DefaultValue("30s") Duration esperaBase, @DefaultValue("5m") Duration esperaMaxima,
        @DefaultValue("2") int maxInciertos, @DefaultValue("2m") Duration esperaConciliacion,
        @DefaultValue("60s") Duration reclamo, @DefaultValue("6h") Duration antiguedadMaxima,
        @DefaultValue("5m") Duration margenVentana, @DefaultValue Graph graph) {

    /** Duración de la ventana de atención de WhatsApp (V2-RN-13). */
    public static final Duration VENTANA_ATENCION = Duration.ofHours(24);

    /**
     * Valida los rangos para que una configuración errónea impida arrancar en lugar de enviar mal.
     * @param habilitado disparo periódico
     * @param intervalo espera entre pasadas, positiva
     * @param lote tamaño del lote, de 1 a 500
     * @param maxIntentos intentos, de 1 a 100
     * @param esperaBase espera inicial, positiva
     * @param esperaMaxima tope, no menor que la base
     * @param maxInciertos resultados desconocidos admitidos, de 1 a {@code maxIntentos}
     * @param esperaConciliacion espera de conciliación, positiva
     * @param reclamo plazo del reclamo, mayor que la suma de los tiempos de Graph
     * @param antiguedadMaxima caducidad, positiva
     * @param margenVentana margen, no negativo y menor que 24 h
     * @param graph conexión con Graph; si es nula, la de por defecto
     * @throws IllegalArgumentException si algún valor está fuera de rango
     */
    public SalidaWhatsAppPropiedades {
        graph = graph == null ? new Graph("https://graph.facebook.com", "v26.0", "", Duration.ofSeconds(5),
                Duration.ofSeconds(15)) : graph;
        if (intervalo.isNegative() || intervalo.isZero() || lote < 1 || lote > 500 || maxIntentos < 1
                || maxIntentos > 100 || maxInciertos < 1 || maxInciertos > maxIntentos
                || esperaConciliacion.isNegative() || esperaConciliacion.isZero()
                || esperaBase.isNegative() || esperaBase.isZero() || esperaMaxima.compareTo(esperaBase) < 0
                || antiguedadMaxima.isNegative() || antiguedadMaxima.isZero() || margenVentana.isNegative()
                || margenVentana.compareTo(VENTANA_ATENCION) >= 0
                || reclamo.compareTo(graph.conexion().plus(graph.respuesta())) <= 0) {
            throw new IllegalArgumentException("Configuración de la salida de WhatsApp fuera de rango.");
        }
    }

    /** {@return {@code true} si hay token de Graph con el que enviar} */
    public boolean envioConfigurado() {
        return !graph.token().isBlank();
    }

    /**
     * Conexión con la Graph API oficial ({@code POST {urlBase}/{version}/{phone_number_id}/messages}).
     * @param urlBase origen de la API, sin barra final; las pruebas lo apuntan a un servidor falso
     * @param version versión fijada de la Graph API, por ejemplo {@code v26.0}
     * @param token token de acceso de la app (Fase 1); vacío si no está configurado
     * @param conexion tiempo máximo para establecer la conexión
     * @param respuesta tiempo máximo de espera de la respuesta, una vez enviada la petición
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record Graph(@DefaultValue("https://graph.facebook.com") String urlBase,
            @DefaultValue("v26.0") String version, @DefaultValue("") String token,
            @DefaultValue("5s") Duration conexion, @DefaultValue("15s") Duration respuesta) {
        /**
         * Valida la forma del origen y de la versión y los tiempos.
         * @param urlBase origen HTTPS (o HTTP solo hacia {@code localhost} o {@code 127.0.0.1}, para pruebas)
         * @param version versión con la forma {@code vNN.N}
         * @param token token o vacío
         * @param conexion tiempo de conexión, positivo
         * @param respuesta tiempo de respuesta, positivo
         * @throws IllegalArgumentException si algún valor no es válido
         */
        public Graph {
            token = token == null ? "" : token.strip();
            if (urlBase == null || urlBase.endsWith("/")
                    || !(urlBase.startsWith("https://") || urlBase.startsWith("http://localhost:")
                            || urlBase.startsWith("http://127.0.0.1:"))
                    || version == null || !version.matches("v[0-9]{1,3}\\.[0-9]") || conexion == null
                    || respuesta == null || conexion.isNegative() || conexion.isZero() || respuesta.isNegative()
                    || respuesta.isZero()) {
                throw new IllegalArgumentException("Configuración de la Graph API fuera de rango.");
            }
        }

        /**
         * Representación sin el token, para que un registro accidental no lo exponga.
         * @return origen, versión y tiempos, con el token oculto
         */
        @Override
        public String toString() {
            return "Graph[urlBase=" + urlBase + ", version=" + version + ", token=" + (token.isEmpty() ? "" : "***")
                    + ", conexion=" + conexion + ", respuesta=" + respuesta + "]";
        }
    }
}
