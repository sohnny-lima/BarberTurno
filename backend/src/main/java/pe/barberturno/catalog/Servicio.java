package pe.barberturno.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.math.BigDecimal;

/**
 * Catálogo RF-04 con precio referencial (RN-23) y desactivación lógica (RN-16); sus cambios no alteran RN-13.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "servicio")
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "duracion_min", nullable = false)
    private short duracionMin;

    @Column(name = "precio", nullable = false, precision = 8, scale = 2)
    private BigDecimal precio;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected Servicio() {
    }

    /**
     * Crea servicio del catálogo con duración y precio referencial.
     * @param nombre nombre visible no nulo; DTO o servicio valida longitud y formato
     * @param descripcion detalle visible del servicio, no nulo, de hasta 300 caracteres
     * @param duracionMin duración del catálogo entre 10 y 180 minutos, múltiplo de diez
     * @param precio precio referencial no negativo en soles, numeric(8,2); el pago es presencial (RN-23)
     * @param creadoEn instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo
     * @throws NullPointerException si falta un dato obligatorio.
     * @throws IllegalArgumentException si duración está fuera de 10 a 180 o no es múltiplo de diez, o el precio
     * es negativo
     */
    public Servicio(String nombre, String descripcion, short duracionMin, BigDecimal precio, Instant creadoEn) {
        editar(nombre, descripcion, duracionMin, precio, creadoEn);
        this.activo = true;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        this.actualizadoEn = creadoEn;
    }

    /**
     * Edita los datos del catálogo; las reservas conservan sus referencias.
     * @param nombre nombre visible no nulo; DTO o servicio valida longitud y formato
     * @param descripcion detalle visible del servicio, no nulo, de hasta 300 caracteres
     * @param duracionMin minutos en múltiplos de diez
     * @param precio precio referencial
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws IllegalArgumentException si la duración o el precio son inválidos.
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void editar(String nombre, String descripcion, short duracionMin, BigDecimal precio, Instant actualizadoEn) {
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(descripcion, "descripcion");
        Objects.requireNonNull(precio, "precio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (duracionMin < 10 || duracionMin > 180 || duracionMin % 10 != 0 || precio.signum() < 0) {
            throw new IllegalArgumentException("Duración o precio inválidos.");
        }
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.duracionMin = duracionMin;
        this.precio = precio;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Retira servicio de nuevas reservas (RN-06) sin borrar fila ni referencias históricas (RN-16).
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void desactivar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = false;
    }

    /**
     * Habilita servicio para nuevas reservas (RN-06), conservando identidad e historial.
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void activar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = true;
    }

    /** {@return identificador persistente generado por V1; nulo hasta persistir la entidad} */
    public Long getId() {
        return id;
    }

    /** {@return nombre visible de la persona o del servicio, no nulo} */
    public String getNombre() {
        return nombre;
    }

    /** {@return detalle visible del servicio, no nulo, de hasta 300 caracteres} */
    public String getDescripcion() {
        return descripcion;
    }

    /** {@return duración del catálogo entre 10 y 180 minutos, múltiplo de diez} */
    public short getDuracionMin() {
        return duracionMin;
    }

    /** {@return precio referencial no negativo en soles, numeric(8,2); el pago es presencial (RN-23)} */
    public BigDecimal getPrecio() {
        return precio;
    }

    /** {@return estado de habilitación lógica; false conserva la fila histórica según RN-16} */
    public boolean isActivo() {
        return activo;
    }

    /**
     * {@return instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo}
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }

    /** {@return instante absoluto del último cambio, aportado por el Clock del servicio; no nulo} */
    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
