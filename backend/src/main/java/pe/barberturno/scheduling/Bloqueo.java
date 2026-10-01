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
import java.time.Instant;
import java.util.Objects;
import pe.barberturno.users.Usuario;

/**
 * Bloqueo de disponibilidad de un barbero.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "bloqueo")
public class Bloqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbero_id", nullable = false)
    private Barbero barbero;

    @Column(name = "inicio", nullable = false)
    private Instant inicio;

    @Column(name = "fin", nullable = false)
    private Instant fin;

    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    /** Constructor reservado a JPA. */
    protected Bloqueo() {
    }

    /**
     * Crea bloqueo de disponibilidad de un barbero.
     * @param barbero barbero
     * @param inicio inicio
     * @param fin fin
     * @param motivo motivo
     * @param creadoPor creadoPor
     * @param creadoEn instante aportado por el servicio
     * @throws NullPointerException si falta un dato obligatorio.
     * @throws IllegalArgumentException si los datos violan las restricciones simples de V1.
     */
    public Bloqueo(Barbero barbero, Instant inicio, Instant fin, String motivo, Usuario creadoPor, Instant creadoEn) {
        this.barbero = Objects.requireNonNull(barbero, "barbero");
        this.inicio = Objects.requireNonNull(inicio, "inicio");
        this.fin = Objects.requireNonNull(fin, "fin");
        this.motivo = Objects.requireNonNull(motivo, "motivo");
        this.creadoPor = Objects.requireNonNull(creadoPor, "creadoPor");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException("El inicio debe ser anterior al fin.");
        }
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
     * Consulta inicio.
     * @return inicio
     */
    public Instant getInicio() {
        return inicio;
    }

    /**
     * Consulta fin.
     * @return fin
     */
    public Instant getFin() {
        return fin;
    }

    /**
     * Consulta motivo.
     * @return motivo
     */
    public String getMotivo() {
        return motivo;
    }

    /**
     * Consulta creadoPor.
     * @return creadoPor
     */
    public Usuario getCreadoPor() {
        return creadoPor;
    }

    /**
     * Consulta creadoEn.
     * @return creadoEn
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }
}
