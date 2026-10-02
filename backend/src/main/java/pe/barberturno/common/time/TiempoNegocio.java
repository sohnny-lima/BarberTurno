package pe.barberturno.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Conversiones de negocio independientes de la zona del servidor y del reloj actual.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class TiempoNegocio {
    /**
     * Zona America/Lima para fechas y jornadas de negocio (RNF-13).
     */
    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    private TiempoNegocio() { }

    /**
     * Convierte medianoche de Lima al límite inclusivo de consultas por día (RNF-13).
     * @param fecha día de Lima
     * @return primer instante del día
     */
    public static Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA).toInstant();
    }

    /**
     * Calcula medianoche siguiente en Lima como fin exclusivo de consultas por día (RNF-13).
     * @param fecha día de Lima
     * @return límite exclusivo: inicio del día siguiente
     */
    public static Instant finDelDia(LocalDate fecha) {
        return inicioDelDia(fecha.plusDays(1));
    }

    /**
     * Convierte un instante absoluto al desfase de Lima exigido en los DTO, sin usar la zona del servidor.
     * @param instante instante absoluto
     * @return fecha y hora con desfase de Lima
     */
    public static OffsetDateTime aLima(Instant instante) {
        return instante.atZone(ZONA).toOffsetDateTime();
    }

    /**
     * Determina día ISO según la fecha de Lima para seleccionar jornadas RN-17.
     * @param instante instante absoluto
     * @return día ISO, lunes 1 y domingo 7
     */
    public static int diaIso(Instant instante) {
        return fechaLima(instante).getDayOfWeek().getValue();
    }

    /**
     * Determina fecha del negocio en Lima desde un instante absoluto, independiente de la zona del servidor.
     * @param instante instante absoluto
     * @return fecha local de Lima
     */
    public static LocalDate fechaLima(Instant instante) {
        return aLima(instante).toLocalDate();
    }
}
