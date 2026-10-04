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
 * Intervalo semiabierto sin atención de un barbero (RN-18); el servicio valida futuro y reservas antes de
 * persistir.
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

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected Bloqueo() {
    }

    /**
     * Crea bloqueo de disponibilidad de un barbero.
     * @param barbero perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo
     * @param inicio inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no
     * nulo
     * @param fin fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo
     * @param motivo texto no nulo de hasta 200 caracteres que explica la indisponibilidad
     * @param creadoPor identidad del actor que creó el bloqueo, enlazada por creado_por; no nula
     * @param creadoEn instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo
     * @throws NullPointerException si falta un dato obligatorio.
     * @throws IllegalArgumentException si inicio no precede estrictamente a fin
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

    /** {@return identificador persistente generado por V1; nulo hasta persistir la entidad} */
    public Long getId() {
        return id;
    }

    /** {@return perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo} */
    public Barbero getBarbero() {
        return barbero;
    }

    /**
     * {@return inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no
     * nulo}
     */
    public Instant getInicio() {
        return inicio;
    }

    /** {@return fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo} */
    public Instant getFin() {
        return fin;
    }

    /**
     * Motivo no nulo del bloqueo, persistido en varchar(200); explica la indisponibilidad RN-18.
     * @return explicación no nula de la indisponibilidad, hasta 200 caracteres
     */
    public String getMotivo() {
        return motivo;
    }

    /** {@return identidad del actor que creó el bloqueo, enlazada por creado_por; no nula} */
    public Usuario getCreadoPor() {
        return creadoPor;
    }

    /**
     * {@return instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo}
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }
}
