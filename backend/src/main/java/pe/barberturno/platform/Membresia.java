package pe.barberturno.platform;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;

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
    @Enumerated(EnumType.STRING) @Column(name = "motivo_revocacion", length = 20)
    private MotivoRevocacion motivoRevocacion;
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
     * Revoca sin modificar el JWT; una baja previa conserva causa e instante salvo que
     * la decisión del gestor sustituya una baja del perfil (V2-DA-41).
     * @param motivo causa que limita las vías posteriores de restablecimiento
     * @param ahora instante del cambio aportado por el reloj
     * @throws NullPointerException si falta el motivo o el instante
     */
    public void revocar(MotivoRevocacion motivo, Instant ahora) {
        Objects.requireNonNull(motivo);
        Objects.requireNonNull(ahora);
        if (!isActiva() && !(motivoRevocacion == MotivoRevocacion.PERFIL_DESACTIVADO
                && motivo == MotivoRevocacion.DECISION_GESTOR)) return;
        motivoRevocacion = motivo;
        actualizadoEn = ahora;
        estado = "REVOCADA";
    }

    /**
     * Deshace exclusivamente la baja del perfil; otras causas conservan estado, motivo e instante.
     * El servicio limita esta operación a la pertenencia BARBERO del negocio actual (V2-DA-37).
     * @param ahora instante del cambio aportado por el reloj
     * @throws NullPointerException si falta el instante
     */
    public void restablecerTrasReactivarPerfil(Instant ahora) {
        Objects.requireNonNull(ahora);
        if (!isActiva() && motivoRevocacion == MotivoRevocacion.PERFIL_DESACTIVADO) {
            restablecerPorInvitacion(rol, ahora);
        }
    }

    /**
     * Restablece una pertenencia revocada con el rol acreditado por una invitación aceptada.
     * El futuro servicio de invitaciones validará token, correo y negocio antes de invocarlo.
     * @param rolInvitado permisos acreditados por la invitación, nunca por el cuerpo del aceptante
     * @param ahora instante de aceptación aportado por el reloj
     * @throws NullPointerException si falta rol o instante
     * @throws IllegalStateException si la pertenencia ya está activa
     */
    public void restablecerPorInvitacion(RolNegocio rolInvitado, Instant ahora) {
        Objects.requireNonNull(rolInvitado);
        Objects.requireNonNull(ahora);
        if (isActiva()) throw new IllegalStateException("La membresía ya está activa.");
        rol = rolInvitado;
        estado = "ACTIVA";
        motivoRevocacion = null;
        actualizadoEn = ahora;
    }

    /**
     * Completa la propiedad inicial solo si está activa; una baja nunca se deshace en el arranque.
     * Solo el arranque de la cuenta BT_ADMIN usa esta operación; no es gestión de equipo por API.
     * @param ahora instante del arranque aportado por el reloj
     * @throws NullPointerException si falta el instante
     */
    public void acreditarPropietarioInicial(Instant ahora) {
        Objects.requireNonNull(ahora);
        if (isActiva() && rol != RolNegocio.PROPIETARIO) {
            rol = RolNegocio.PROPIETARIO;
            estado = "ACTIVA";
            actualizadoEn = ahora;
        }
    }

    /**
     * Sustituye permisos sin restablecer una baja ni modificar la identidad global.
     * @param nuevoRol permisos autorizados por el propietario
     * @param ahora instante del cambio aportado por el reloj
     * @throws NullPointerException si falta rol o instante
     * @throws NegocioException si la pertenencia está revocada (TRANSICION_INVALIDA)
     */
    public void cambiarRol(RolNegocio nuevoRol, Instant ahora) {
        if (!isActiva()) throw new NegocioException(ErrorCodigo.TRANSICION_INVALIDA,
                "No se puede cambiar el rol de una membresía revocada.");
        rol = Objects.requireNonNull(nuevoRol);
        actualizadoEn = Objects.requireNonNull(ahora);
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

    /**
     * Expone la causa de una baja para limitar la reactivación del perfil profesional.
     * @return causa de la revocación, o null si está activa
     */
    public MotivoRevocacion getMotivoRevocacion() { return motivoRevocacion; }
}
