package pe.barberturno.conversation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la conversación guiada ({@code barberturno.whatsapp.conversacion}, 13-v2-10 §7–§8).
 * El texto de privacidad no tiene valor por defecto: solo los perfiles {@code dev} y {@code test} traen
 * un borrador marcado, y sin él la aplicación no arranca (V2-DA-68, P2-06).
 * @param privacidad texto y enlace del aviso de privacidad
 * @param inactividad tiempo sin entradas tras el que se vacía el estado (24 h, 04-arquitectura §7.1)
 * @param vigenciaOperacion vigencia de una operación pendiente (10 min, V2-RN-10)
 * @param retencion retención y purga (V2-DA-67)
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Validated
@ConfigurationProperties("barberturno.whatsapp.conversacion")
public record ConversacionPropiedades(@NotNull @Valid Privacidad privacidad,
        @DefaultValue("24h") @NotNull Duration inactividad,
        @DefaultValue("10m") @NotNull Duration vigenciaOperacion,
        @DefaultValue @NotNull @Valid Retencion retencion) {

    /**
     * Aviso de privacidad (P2-06 pendiente: el texto vigente es un borrador).
     * @param texto plantilla con los marcadores {@code {negocio}} y {@code {enlace}}
     * @param enlace URL del aviso de privacidad
     */
    public record Privacidad(@NotBlank String texto, @NotBlank String enlace) { }

    /**
     * Retención y purga por negocio.
     * @param habilitada si el trabajo programado se ejecuta
     * @param intervalo periodo entre ejecuciones
     * @param lote filas como máximo por negocio y ejecución
     * @param conversacionesSinFicha antigüedad tras la que se borra una conversación sin ficha (30 días,
     *        decidido en 04-arquitectura §3 y 06-flujos §6)
     * @param cuerpoEventos antigüedad tras la que se vacía el cuerpo de un evento entrante terminado;
     *        nula = desactivado hasta que P2-06 fije el plazo
     */
    public record Retencion(@DefaultValue("true") boolean habilitada,
            @DefaultValue("1h") @NotNull Duration intervalo,
            @DefaultValue("200") @Positive int lote,
            @DefaultValue("30d") @NotNull Duration conversacionesSinFicha,
            Duration cuerpoEventos) { }
}
