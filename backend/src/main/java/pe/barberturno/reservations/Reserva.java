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
 * Reserva RN-02 con intervalo semiabierto RN-01 y precio y duración copiados al crearla (RN-13).
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

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected Reserva() {
    }

    /**
     * Crea una reserva RN-02 y copia precio y duración del servicio (RN-13); calcula fin exclusivo RN-01. El
     * servicio decide estado RN-21 y comprueba disponibilidad, permisos y política antes de persistir.
     * @param cliente usuario propietario de la reserva, enlazado por cliente_id; no nulo (RN-02)
     * @param barbero perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo
     * @param servicio servicio enlazado por servicio_id; no nulo; sus cambios no alteran las referencias RN-13
     * @param inicio inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no
     * nulo
     * @param estado estado inicial decidido por el servicio
     * @param creadaPor identidad del actor que creó la reserva, cliente o administrador; no nula
     * @param creadoEn instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo
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
     * Reprograma conservando servicio y referencias RN-09/13 y recalcula fin RN-01. El servicio toma los
     * bloqueos ① ② ③, valida disponibilidad y escribe auditoría y avisos RN-15 en la misma transacción.
     * @param barbero nuevo barbero
     * @param inicio nuevo inicio
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws NullPointerException si un dato indicado como no nulo está ausente
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
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
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
     * Identificador persistente generado por V1; nulo hasta persistir la entidad.
     * @return identificador persistente generado por V1; nulo hasta persistir la entidad.
     */
    public Long getId() {
        return id;
    }

    /**
     * Usuario propietario de la reserva, enlazado por cliente_id; no nulo (RN-02).
     * @return usuario propietario de la reserva, enlazado por cliente_id; no nulo (RN-02).
     */
    public Usuario getCliente() {
        return cliente;
    }

    /**
     * Perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo.
     * @return perfil de atención al que pertenece el intervalo, enlazado por barbero_id; no nulo.
     */
    public Barbero getBarbero() {
        return barbero;
    }

    /**
     * Servicio enlazado por servicio_id; no nulo; sus cambios no alteran las referencias RN-13.
     * @return servicio enlazado por servicio_id; no nulo; sus cambios no alteran las referencias RN-13.
     */
    public Servicio getServicio() {
        return servicio;
    }

    /**
     * Inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo.
     * @return inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo.
     */
    public Instant getInicio() {
        return inicio;
    }

    /**
     * Fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo.
     * @return fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo.
     */
    public Instant getFin() {
        return fin;
    }

    /**
     * Duración en minutos copiada del catálogo al crear la reserva e inmutable al reprogramar (RN-13).
     * @return duración en minutos copiada del catálogo al crear la reserva e inmutable al reprogramar (RN-13).
     */
    public short getDuracionRefMin() {
        return duracionRefMin;
    }

    /**
     * Precio referencial en soles copiado al crear la reserva; numeric(8,2), conservado según RN-13.
     * @return precio referencial en soles copiado al crear la reserva; numeric(8,2), conservado según RN-13.
     */
    public BigDecimal getPrecioRef() {
        return precioRef;
    }

    /**
     * Estado persistido de la máquina RN-10; solo CANCELADA libera la franja (RN-14).
     * @return estado persistido de la máquina RN-10; solo CANCELADA libera la franja (RN-14).
     */
    public EstadoReserva getEstado() {
        return estado;
    }

    /**
     * Identidad del actor que creó la reserva, cliente o administrador; no nula.
     * @return identidad del actor que creó la reserva, cliente o administrador; no nula.
     */
    public Usuario getCreadaPor() {
        return creadaPor;
    }

    /**
     * Versión para bloqueo optimista; la API la exige y una diferencia produce VERSION_DESACTUALIZADA.
     * @return versión para bloqueo optimista; la API la exige y una diferencia produce VERSION_DESACTUALIZADA.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Instante absoluto de creación, aportado por el Clock del servicio y persistido como timestamptz; no nulo.
     * @return instante absoluto de creación, aportado por el Clock del servicio y persistido como timestamptz;
     * no nulo.
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }

    /**
     * Instante absoluto del último cambio, aportado por el Clock del servicio; no nulo.
     * @return instante absoluto del último cambio, aportado por el Clock del servicio; no nulo.
     */
    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
