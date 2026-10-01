package pe.barberturno.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Locale;

/**
 * Usuario con sus datos de identidad y acceso.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "correo", nullable = false, length = 254)
    private String correo;

    @Column(name = "telefono", nullable = true, length = 9)
    private String telefono;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 10)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "intentos_fallidos", nullable = false)
    private short intentosFallidos;

    @Column(name = "bloqueado_hasta", nullable = true)
    private Instant bloqueadoHasta;

    @Column(name = "privacidad_aceptada_en", nullable = true)
    private Instant privacidadAceptadaEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Constructor reservado a JPA. */
    protected Usuario() {
    }

    /**
     * Crea usuario con sus datos de identidad y acceso.
     * @param nombre nombre
     * @param correo correo
     * @param telefono telefono
     * @param passwordHash passwordHash
     * @param rol rol
     * @param privacidadAceptadaEn privacidadAceptadaEn
     * @param creadoEn instante aportado por el servicio
     * @throws NullPointerException si falta un dato obligatorio.
     */
    public Usuario(String nombre, String correo, String telefono, String passwordHash, Rol rol, Instant privacidadAceptadaEn, Instant creadoEn) {
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.rol = Objects.requireNonNull(rol, "rol");
        this.correo = Objects.requireNonNull(correo, "correo").toLowerCase(Locale.ROOT);
        this.telefono = telefono;
        this.privacidadAceptadaEn = privacidadAceptadaEn;
        this.activo = true;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        this.actualizadoEn = creadoEn;
    }

    /**
     * Actualiza los campos editables sin alterar correo ni datos de acceso.
     * @param nombre nombre validado
     * @param telefono teléfono validado, opcional para el personal
     * @param actualizadoEn instante aportado por el reloj del servicio
     */
    public void actualizarPerfil(String nombre, String telefono, Instant actualizadoEn) {
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.telefono = telefono;
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
    }

    /**
     * Registra un fallo; el servicio decide cuándo y cuánto bloquear.
     * @param bloqueadoHasta vencimiento del bloqueo o null
     * @param actualizadoEn instante del cambio
     * @throws IllegalArgumentException si se agota el contador.
     */
    public void registrarIntentoFallido(Instant bloqueadoHasta, Instant actualizadoEn) {
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (intentosFallidos == Short.MAX_VALUE) {
            throw new IllegalArgumentException("Contador de intentos agotado.");
        }
        intentosFallidos++;
        this.bloqueadoHasta = bloqueadoHasta;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Reinicia el contador tras una autenticación correcta.
     * @param actualizadoEn instante del acceso
     */
    public void registrarAccesoCorrecto(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }

    /**
     * Cambia la contraseña y revoca las sesiones anteriores.
     * @param passwordHash hash de la nueva contraseña
     * @param debeCambiarPassword si requiere un cambio posterior
     * @param actualizadoEn instante del cambio
     */
    public void cambiarPassword(String passwordHash, boolean debeCambiarPassword, Instant actualizadoEn) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        this.tokenVersion = Math.incrementExact(tokenVersion);
        this.passwordHash = passwordHash;
        this.debeCambiarPassword = debeCambiarPassword;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Desactiva el acceso y revoca las sesiones.
     * @param actualizadoEn instante del cambio
     */
    public void desactivar(Instant actualizadoEn) {
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        tokenVersion = Math.incrementExact(tokenVersion);
        activo = false;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Reactiva el acceso.
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
     * Consulta correo.
     * @return correo
     */
    public String getCorreo() {
        return correo;
    }

    /**
     * Consulta telefono.
     * @return telefono
     */
    public String getTelefono() {
        return telefono;
    }

    /**
     * Consulta passwordHash.
     * @return passwordHash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Consulta rol.
     * @return rol
     */
    public Rol getRol() {
        return rol;
    }

    /**
     * Consulta activo.
     * @return activo
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Consulta debeCambiarPassword.
     * @return debeCambiarPassword
     */
    public boolean isDebeCambiarPassword() {
        return debeCambiarPassword;
    }

    /**
     * Consulta tokenVersion.
     * @return tokenVersion
     */
    public int getTokenVersion() {
        return tokenVersion;
    }

    /**
     * Consulta intentosFallidos.
     * @return intentosFallidos
     */
    public short getIntentosFallidos() {
        return intentosFallidos;
    }

    /**
     * Consulta bloqueadoHasta.
     * @return bloqueadoHasta
     */
    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    /**
     * Consulta privacidadAceptadaEn.
     * @return privacidadAceptadaEn
     */
    public Instant getPrivacidadAceptadaEn() {
        return privacidadAceptadaEn;
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
