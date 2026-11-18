package pe.barberturno.conversation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la derivación y la bandeja web ({@code barberturno.whatsapp.bandeja}, V2-11, 16-v2-11 §3.3).
 * @param habilitado si el trabajo de retorno automático se ejecuta
 * @param entrantes qué mensajes entrantes se guardan (D6 de P2-06 pendiente: por defecto, los mínimos)
 * @param retornoAutomatico tiempo sin actividad del personal tras el que la conversación vuelve al asistente
 * @param intervalo periodo del trabajo de retorno automático
 * @param lote conversaciones como máximo por negocio y ejecución
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Validated
@ConfigurationProperties("barberturno.whatsapp.bandeja")
public record BandejaPropiedades(@DefaultValue("true") boolean habilitado,
        @DefaultValue("SOLO_HUMANO") @NotNull Entrantes entrantes,
        @DefaultValue("24h") @NotNull Duration retornoAutomatico,
        @DefaultValue("5m") @NotNull Duration intervalo,
        @DefaultValue("200") @Positive int lote) {

    /** Qué mensajes entrantes se guardan en el historial (16-v2-11 §4). */
    public enum Entrantes {
        /** El que provoca la derivación y los que llegan en modo humano (opción mínima de D6). */
        SOLO_HUMANO,
        /** También los del modo asistente, para dar contexto al personal; solo si el responsable lo decide (D6). */
        TODOS
    }
}
