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
 * Intervalo semanal de atención de un barbero.
 *
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

    /** Constructor reservado a JPA. */
    protected Jornada() {
    }

    /**
     * Crea intervalo semanal de atención de un barbero.
     * @param barbero barbero
     * @param diaSemana día ISO de uno a siete
     * @param horaInicio horaInicio
     * @param horaFin horaFin
     * @throws NullPointerException si falta un dato obligatorio.
     * @throws IllegalArgumentException si los datos violan las restricciones simples de V1.
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
     * Consulta id.
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * Consulta barbero.
     * @return barbero
     */
    public Barbero getBarbero() {
        return barbero;
    }

    /**
     * Consulta diaSemana.
     * @return diaSemana
     */
    public short getDiaSemana() {
        return diaSemana;
    }

    /**
     * Consulta horaInicio.
     * @return horaInicio
     */
    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    /**
     * Consulta horaFin.
     * @return horaFin
     */
    public LocalTime getHoraFin() {
        return horaFin;
    }
}
