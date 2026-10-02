package pe.barberturno.scheduling.dto;

import jakarta.validation.constraints.*;
import java.time.format.DateTimeFormatter;
import pe.barberturno.scheduling.Jornada;

/**
 * Intervalo semanal RF-06; las horas se intercambian exclusivamente como HH:mm en Lima.
 * @param diaSemana día ISO de lunes (1) a domingo (7)
 * @param horaInicio límite inclusivo, con minutos y sin segundos
 * @param horaFin límite exclusivo del mismo día, con minutos y sin segundos
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record JornadaDto(
        @NotNull(message = "Indique el día ISO.")
        @Min(value = 1, message = "El día ISO debe estar entre 1 y 7.")
        @Max(value = 7, message = "El día ISO debe estar entre 1 y 7.") Integer diaSemana,
        @NotNull(message = "Indique la hora de inicio.")
        @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = "Use una hora HH:mm sin segundos.") String horaInicio,
        @NotNull(message = "Indique la hora de fin.")
        @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = "Use una hora HH:mm sin segundos.") String horaFin) {

    /**
     * Proyecta una fila persistida sin exponer identidades internas ni relaciones.
     * @param jornada intervalo validado y persistido
     * @return día ISO y horas de Lima en formato HH:mm
     */
    public static JornadaDto desde(Jornada jornada) {
        var formato = DateTimeFormatter.ofPattern("HH:mm");
        return new JornadaDto((int) jornada.getDiaSemana(), jornada.getHoraInicio().format(formato),
                jornada.getHoraFin().format(formato));
    }
}
