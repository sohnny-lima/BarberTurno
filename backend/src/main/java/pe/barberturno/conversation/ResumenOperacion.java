package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Datos que el resumen de una propuesta muestra antes de la confirmación explícita (V2-RF-11).
 * Para cancelar, {@code inicio} y {@code fin} son los de la cita que se cancela.
 * @param tipo operación propuesta
 * @param servicio nombre del servicio
 * @param profesional nombre visible del profesional
 * @param inicio inicio propuesto (o el de la cita, al cancelar)
 * @param fin fin propuesto (o el de la cita, al cancelar)
 * @param duracionMin duración en minutos
 * @param precio precio en soles
 * @param antes cita actual, al reprogramar o cancelar; vacía al crear
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ResumenOperacion(TipoOperacion tipo, String servicio, String profesional, Instant inicio,
        Instant fin, int duracionMin, BigDecimal precio, Optional<CitaOpcion> antes) { }
