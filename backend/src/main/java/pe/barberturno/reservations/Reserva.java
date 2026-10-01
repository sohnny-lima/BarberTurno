package pe.barberturno.reservations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import pe.barberturno.users.Usuario;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.scheduling.Barbero;

/**
 * Reserva con duración y precio acordados al crearla.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbero_id", nullable = false)
    private Barbero barbero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @Column(name = "inicio", nullable = false)
    private Instant inicio;

    @Column(name = "fin", nullable = false)
    private Instant fin;

    @Column(name = "duracion_ref_min", nullable = false)
    private short duracionRefMin;

    @Column(name = "precio_ref", nullable = false, precision = 8, scale = 2)
    private BigDecimal precioRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoReserva estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por", nullable = false)
    private Usuario creadaPor;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Constructor reservado a JPA. */
    protected Reserva() {
    }

    /**
     * Crea reserva con duración y precio acordados al crearla.
     * @param cliente cliente
     * @param barbero barbero
     * @param servicio servicio
     * @param inicio inicio
     * @param estado estado inicial decidido por el servicio
     * @param creadaPor creadaPor
     * @param creadoEn instante aportado por el servicio
     * @throws NullPointerException si falta un dato obligatorio.
     */
    public Reserva(Usuario cliente, Barbero barbero, Servicio servicio, Instant inicio, EstadoReserva estado, Usuario creadaPor, Instant creadoEn) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.barbero = Objects.requireNonNull(barbero, "barbero");
        this.servicio = Objects.requireNonNull(servicio, "servicio");
        this.inicio = Objects.requireNonNull(inicio, "inicio");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.creadaPor = Objects.requireNonNull(creadaPor, "creadaPor");
        this.duracionRefMin = servicio.getDuracionMin();
        this.precioRef = servicio.getPrecio();
        this.fin = inicio.plus(duracionRefMin, ChronoUnit.MINUTES);
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        this.actualizadoEn = creadoEn;
    }

    /**
     * Reprograma conservando el servicio, la duración y el precio acordados.
     * @param barbero nuevo barbero
     * @param inicio nuevo inicio
     * @param actualizadoEn instante del cambio
     */
    public void reprogramar(Barbero barbero, Instant inicio, Instant actualizadoEn) {
        Objects.requireNonNull(barbero, "barbero");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        Instant nuevoFin = inicio.plus(duracionRefMin, ChronoUnit.MINUTES);
        this.barbero = barbero;
        this.inicio = inicio;
        this.fin = nuevoFin;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Cambia el estado preservando RN-10; el servicio verifica primero actor y ventana.
     * @param estado estado de destino
     * @param actualizadoEn instante del cambio
     * @throws NullPointerException si falta el destino o el instante
     * @throws IllegalStateException si RN-10 no permite la transición
     */
    public void cambiarEstado(EstadoReserva estado, Instant actualizadoEn) {
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (!this.estado.puedePasarA(estado)) {
            throw new IllegalStateException("Transición de reserva inválida.");
        }
        this.estado = estado;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Consulta id.
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * Consulta cliente.
     * @return cliente
     */
    public Usuario getCliente() {
        return cliente;
    }

    /**
     * Consulta barbero.
     * @return barbero
     */
    public Barbero getBarbero() {
        return barbero;
    }

    /**
     * Consulta servicio.
     * @return servicio
     */
    public Servicio getServicio() {
        return servicio;
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
     * Consulta duracionRefMin.
     * @return duracionRefMin
     */
    public short getDuracionRefMin() {
        return duracionRefMin;
    }

    /**
     * Consulta precioRef.
     * @return precioRef
     */
    public BigDecimal getPrecioRef() {
        return precioRef;
    }

    /**
     * Consulta estado.
     * @return estado
     */
    public EstadoReserva getEstado() {
        return estado;
    }

    /**
     * Consulta creadaPor.
     * @return creadaPor
     */
    public Usuario getCreadaPor() {
        return creadaPor;
    }

    /**
     * Consulta version.
     * @return version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Consulta creadoEn.
     * @return creadoEn
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }

    /**
     * Consulta actualizadoEn.
     * @return actualizadoEn
     */
    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
