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
 * Identidad y acceso RF-01 a RF-03; conserva correo normalizado RN-24, bloqueo RN-25 y revocación por
 * token_version.
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

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected Usuario() {
    }

    /**
     * Crea usuario con sus datos de identidad y acceso.
     * @param nombre nombre visible no nulo; DTO o servicio valida longitud y formato
     * @param correo correo de acceso único sin distinguir mayúsculas; normalizado según RN-24, no nulo
     * @param telefono teléfono de nueve dígitos; puede ser nulo para el personal, obligatorio para CLIENTE
     * @param passwordHash hash BCrypt persistido en password_hash; nunca se expone por la API ni en logs
     * @param rol rol persistido que determina los permisos del servidor; no nulo
     * @param privacidadAceptadaEn instante absoluto del consentimiento del cliente; puede ser nulo para el
     * personal
     * @param creadoEn instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo
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
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void actualizarPerfil(String nombre, String telefono, Instant actualizadoEn) {
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.telefono = telefono;
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
    }

    /**
     * Registra un fallo; el servicio decide cuándo y cuánto bloquear.
     * @param bloqueadoHasta vencimiento del bloqueo o null
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws IllegalArgumentException si se agota el contador.
     * @throws NullPointerException si un dato indicado como no nulo está ausente
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
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void registrarAccesoCorrecto(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }

    /**
     * Sustituye hash y marca temporal; incrementa token_version revocando todas las sesiones previas.
     * @param passwordHash hash de la nueva contraseña
     * @param debeCambiarPassword si requiere un cambio posterior
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws ArithmeticException si token_version alcanza el límite de int y no puede incrementarse
     * @throws NullPointerException si un dato indicado como no nulo está ausente
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
     * Deshabilita acceso sin borrar historial (RN-16) e incrementa token_version revocando sesiones.
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws ArithmeticException si token_version alcanza el límite de int y no puede incrementarse
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void desactivar(Instant actualizadoEn) {
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        tokenVersion = Math.incrementExact(tokenVersion);
        activo = false;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Habilita acceso sin reducir token_version; las sesiones revocadas permanecen inválidas.
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

    /** {@return correo de acceso único sin distinguir mayúsculas; normalizado según RN-24, no nulo} */
    public String getCorreo() {
        return correo;
    }

    /** {@return teléfono de nueve dígitos; puede ser nulo para el personal, obligatorio para CLIENTE} */
    public String getTelefono() {
        return telefono;
    }

    /** {@return hash BCrypt persistido en password_hash; nunca se expone por la API ni en logs} */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** {@return rol persistido que determina los permisos del servidor; no nulo} */
    public Rol getRol() {
        return rol;
    }

    /** {@return estado de habilitación lógica; false conserva la fila histórica según RN-16} */
    public boolean isActivo() {
        return activo;
    }

    /** {@return marca que limita la cuenta a auth y lectura del perfil hasta cambiar su credencial} */
    public boolean isDebeCambiarPassword() {
        return debeCambiarPassword;
    }

    /**
     * {@return versión de revocación en token_version; debe coincidir con el claim tv para aceptar la
     * sesión}
     */
    public int getTokenVersion() {
        return tokenVersion;
    }

    /** {@return contador de accesos fallidos consecutivos; RN-25 bloquea al alcanzar cinco} */
    public short getIntentosFallidos() {
        return intentosFallidos;
    }

    /** {@return instante absoluto de vencimiento del bloqueo de acceso; nulo si no hay bloqueo} */
    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    /** {@return instante absoluto del consentimiento del cliente; puede ser nulo para el personal} */
    public Instant getPrivacidadAceptadaEn() {
        return privacidadAceptadaEn;
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
