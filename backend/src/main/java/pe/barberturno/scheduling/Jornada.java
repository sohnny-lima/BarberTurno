package pe.barberturno.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.time.LocalTime;

/**
 * Intervalo semanal de atención RN-17: día ISO y horas locales de America/Lima sin cruzar medianoche.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "jornada")
public class Jornada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbero_id", nullable = false)
    private Barbero barbero;

    @Column(name = "dia_semana", nullable = false)
    private short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected Jornada() {
    }

    /**
     * Crea intervalo semanal de atención de un barbero.
     * @param barbero perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo
     * @param diaSemana día ISO de uno a siete
     * @param horaInicio hora inclusiva de jornada en America/Lima; no nula y anterior a hora_fin (RN-17)
     * @param horaFin hora exclusiva de jornada en America/Lima; no nula y posterior a hora_inicio (RN-17)
     * @throws NullPointerException si falta un dato obligatorio.
     * @throws IllegalArgumentException si día no está entre 1 y 7 o inicio no precede a fin
     */
    public Jornada(Barbero barbero, short diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        this.barbero = Objects.requireNonNull(barbero, "barbero");
        this.horaInicio = Objects.requireNonNull(horaInicio, "horaInicio");
        this.horaFin = Objects.requireNonNull(horaFin, "horaFin");
        if (diaSemana < 1 || diaSemana > 7 || !horaInicio.isBefore(horaFin)) {
            throw new IllegalArgumentException("Día o intervalo de jornada inválidos.");
        }
        this.diaSemana = diaSemana;
    }

    /**
     * Identificador persistente generado por V1; nulo hasta persistir la entidad.
     * @return identificador persistente generado por V1; nulo hasta persistir la entidad.
     */
    public Long getId() {
        return id;
    }

    /**
     * Perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo.
     * @return perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo.
     */
    public Barbero getBarbero() {
        return barbero;
    }

    /**
     * Día semanal ISO entre 1 (lunes) y 7 (domingo), persistido en dia_semana.
     * @return día semanal ISO entre 1 (lunes) y 7 (domingo), persistido en dia_semana.
     */
    public short getDiaSemana() {
        return diaSemana;
    }

    /**
     * Hora inclusiva de jornada en America/Lima; no nula y anterior a hora_fin (RN-17).
     * @return hora inclusiva de jornada en America/Lima; no nula y anterior a hora_fin (RN-17).
     */
    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    /**
     * Hora exclusiva de jornada en America/Lima; no nula y posterior a hora_inicio (RN-17).
     * @return hora exclusiva de jornada en America/Lima; no nula y posterior a hora_inicio (RN-17).
     */
    public LocalTime getHoraFin() {
        return horaFin;
    }
}
