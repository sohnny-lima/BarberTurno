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
 * Servicio del catálogo con duración y precio referencial.
 *
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

    /** Constructor reservado a JPA. */
    protected Servicio() {
    }

    /**
     * Crea servicio del catálogo con duración y precio referencial.
     * @param nombre nombre
     * @param descripcion descripcion
     * @param duracionMin duracionMin
     * @param precio precio
     * @param creadoEn instante aportado por el servicio
     * @throws NullPointerException si falta un dato obligatorio.
     * @throws IllegalArgumentException si los datos violan las restricciones simples de V1.
     */
    public Servicio(String nombre, String descripcion, short duracionMin, BigDecimal precio, Instant creadoEn) {
        editar(nombre, descripcion, duracionMin, precio, creadoEn);
        this.activo = true;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        this.actualizadoEn = creadoEn;
    }

    /**
     * Edita los datos del catálogo; las reservas conservan sus referencias.
     * @param nombre nombre
     * @param descripcion descripcion
     * @param duracionMin minutos en múltiplos de diez
     * @param precio precio referencial
     * @param actualizadoEn instante del cambio
     * @throws IllegalArgumentException si la duración o el precio son inválidos.
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
     * Desactiva el servicio.
     * @param actualizadoEn instante del cambio
     */
    public void desactivar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = false;
    }

    /**
     * Activa el servicio.
     * @param actualizadoEn instante del cambio
     */
    public void activar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = true;
    }

    /**
     * Consulta id.
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * Consulta nombre.
     * @return nombre
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Consulta descripcion.
     * @return descripcion
     */
    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Consulta duracionMin.
     * @return duracionMin
     */
    public short getDuracionMin() {
        return duracionMin;
    }

    /**
     * Consulta precio.
     * @return precio
     */
    public BigDecimal getPrecio() {
        return precio;
    }

    /**
     * Consulta activo.
     * @return activo
     */
    public boolean isActivo() {
        return activo;
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
