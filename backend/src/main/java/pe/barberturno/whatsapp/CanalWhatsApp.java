package pe.barberturno.whatsapp;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Número de WhatsApp de un negocio (tabla de plataforma, sin RLS). Un número pertenece a un solo
 * negocio y un negocio tiene como máximo uno (V2-RN-07); la base lo impone con dos índices únicos.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "canal_whatsapp")
public class CanalWhatsApp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private long negocioId;
    @Column(name = "waba_id", nullable = false, length = 32)
    private String wabaId;
    @Column(name = "phone_number_id", nullable = false, length = 32)
    private String phoneNumberId;
    @Column(name = "numero_visible", nullable = false, length = 21)
    private String numeroVisible;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private EstadoCanal estado;
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Hidrata exclusivamente desde JPA. */
    protected CanalWhatsApp() { }

    /**
     * Crea un canal activo; el servicio valida el negocio y los identificadores antes de persistirlo.
     * @param negocioId negocio propietario del canal
     * @param wabaId identificador numérico de cuenta; en Sandbox, identificador sintético del número
     * @param phoneNumberId identificador único del número receptor
     * @param numeroVisible número mostrado en formato internacional
     * @param ahora instante del reloj inyectado para creación y actualización
     */
    public CanalWhatsApp(long negocioId, String wabaId, String phoneNumberId, String numeroVisible, Instant ahora) {
        this.negocioId = negocioId;
        this.wabaId = wabaId;
        this.phoneNumberId = phoneNumberId;
        this.numeroVisible = numeroVisible;
        this.estado = EstadoCanal.ACTIVO;
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    /**
     * Sustituye los datos y reactiva el canal bajo el bloqueo adquirido por el servicio.
     * @param wabaId identificador numérico validado de cuenta
     * @param phoneNumberId número receptor, cuya sustitución controla el servicio si hay conversaciones
     * @param numeroVisible número internacional para la administración
     * @param ahora instante del reloj inyectado
     */
    public void registrar(String wabaId, String phoneNumberId, String numeroVisible, Instant ahora) {
        this.wabaId = wabaId;
        this.phoneNumberId = phoneNumberId;
        this.numeroVisible = numeroVisible;
        this.estado = EstadoCanal.ACTIVO;
        this.actualizadoEn = ahora;
    }

    /** Desconecta el canal; devuelve {@code false} si ya lo estaba. */
    boolean desconectar(Instant ahora) {
        if (estado == EstadoCanal.DESCONECTADO) return false;
        estado = EstadoCanal.DESCONECTADO;
        actualizadoEn = ahora;
        return true;
    }

    /**
     * Identifica al propietario del canal para impedir asociaciones cruzadas de un número.
     * @return id del negocio al que pertenece este canal
     */
    public long negocioId() { return negocioId; }
    String wabaId() { return wabaId; }
    /**
     * Entrega la clave que vincula la notificación entrante con el canal registrado.
     * @return identificador único del número receptor, solo dígitos
     */
    public String phoneNumberId() { return phoneNumberId; }
    String numeroVisible() { return numeroVisible; }
    EstadoCanal estado() { return estado; }
    /**
     * Comprueba si el canal admite nuevas entradas y envíos, sin sustituir la validación del negocio.
     * @return true cuando el canal está activo
     */
    public boolean activo() { return estado == EstadoCanal.ACTIVO; }
    Instant actualizadoEn() { return actualizadoEn; }
}
