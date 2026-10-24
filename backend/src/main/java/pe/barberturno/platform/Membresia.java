package pe.barberturno.platform;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Pertenencia única de una identidad global a un negocio, sin políticas RLS (V2-DA-24).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Entity
@Table(name = "membresia")
public class Membresia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private long negocioId;
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private long usuarioId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private RolNegocio rol;
    @Column(nullable = false, length = 10)
    private String estado;
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Hidrata exclusivamente desde JPA. */
    protected Membresia() { }

    /**
     * Inicia una pertenencia activa; la base impone las referencias y la unicidad.
     * @param negocioId ámbito inmutable de la pertenencia
     * @param usuarioId identidad global que pertenece al negocio
     * @param rol permisos del personal
     * @param ahora instante aportado por el reloj del servicio
     * @throws NullPointerException si falta rol o instante
     */
    public Membresia(long negocioId, long usuarioId, RolNegocio rol, Instant ahora) {
        this.negocioId = negocioId;
        this.usuarioId = usuarioId;
        this.rol = Objects.requireNonNull(rol);
        this.creadoEn = Objects.requireNonNull(ahora);
        this.actualizadoEn = ahora;
        this.estado = "ACTIVA";
    }

    /**
     * Revoca los permisos sin borrar la pertenencia histórica ni modificar el JWT.
     * @param ahora instante del cambio aportado por el reloj
     * @throws NullPointerException si falta el instante
     */
    public void revocar(Instant ahora) {
        actualizadoEn = Objects.requireNonNull(ahora);
        estado = "REVOCADA";
    }

    /**
     * Restablece los permisos del mismo rol al reactivar el perfil profesional local (V2-DA-31).
     * El servicio limita esta operación a la pertenencia BARBERO del negocio actual.
     * @param ahora instante del cambio aportado por el reloj
     * @throws NullPointerException si falta el instante
     */
    public void restablecer(Instant ahora) {
        actualizadoEn = Objects.requireNonNull(ahora);
        estado = "ACTIVA";
    }

    /**
     * Completa la propiedad acreditada por la configuración inicial de confianza, sin duplicar filas.
     * Solo el arranque de la cuenta BT_ADMIN usa esta operación; no es gestión de equipo por API.
     * @param ahora instante del arranque aportado por el reloj
     * @throws NullPointerException si falta el instante
     */
    public void acreditarPropietarioInicial(Instant ahora) {
        Objects.requireNonNull(ahora);
        if (rol != RolNegocio.PROPIETARIO || !isActiva()) {
            rol = RolNegocio.PROPIETARIO;
            estado = "ACTIVA";
            actualizadoEn = ahora;
        }
    }

    /**
     * Identificador de la membresía asignado por la base.
     * @return identidad persistente de la pertenencia
     */
    public Long getId() { return id; }
    /**
     * Negocio en el que la persona tiene este rol.
     * @return negocio al que pertenece la fila
     */
    public long getNegocioId() { return negocioId; }
    /**
     * Cuenta global de la persona miembro (V2-DA-03).
     * @return identidad global del miembro
     */
    public long getUsuarioId() { return usuarioId; }
    /**
     * Rol de la persona dentro de este negocio; no concede permisos en otros.
     * @return permisos asignados en este negocio
     */
    public RolNegocio getRol() { return rol; }
    /**
     * Indica si la membresía está vigente; una membresía revocada no da permisos en la petición siguiente.
     * @return si la pertenencia concede permisos, sujeto al estado de la cuenta
     */
    public boolean isActiva() { return "ACTIVA".equals(estado); }
}
