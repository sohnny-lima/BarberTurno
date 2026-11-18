package pe.barberturno.common.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Asociación del sitio con la aplicación Android de BarberTurno (TWA, V2-DA-95; {@code barberturno.android}).
 * Ambos valores llegan solo por el entorno ({@code BT_TWA_PAQUETE}, {@code BT_TWA_HUELLAS}) porque dependen de la
 * clave con la que se firme cada instalación: ningún binario lleva una huella fija. Si falta cualquiera de los dos,
 * {@code /.well-known/assetlinks.json} responde 404 y Chrome abre la aplicación como pestaña normal.
 * Un valor mal formado impide arrancar, para no publicar una asociación que nunca verificaría.
 * @param paquete identificador de la aplicación Android (p. ej., {@code pe.barberturno.prueba.twa}); vacío = sin asociación
 * @param huellas SHA-256 de los certificados de firma admitidos, en hexadecimal mayúscula separado por dos puntos
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Validated
@ConfigurationProperties("barberturno.android")
public record AsociacionAndroidPropiedades(
        @DefaultValue("") @NotNull
        @Pattern(regexp = "^$|^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$",
                message = "debe ser un identificador de paquete Android") String paquete,
        @DefaultValue({}) @NotNull
        List<@Pattern(regexp = "^([0-9A-F]{2}:){31}[0-9A-F]{2}$",
                message = "debe ser una huella SHA-256 en hexadecimal mayúscula separada por dos puntos") String> huellas) {

    /**
     * Indica si hay datos suficientes para publicar la asociación.
     * @return true con un paquete y al menos una huella
     */
    public boolean configurada() {
        return !paquete.isBlank() && !huellas.isEmpty();
    }
}
