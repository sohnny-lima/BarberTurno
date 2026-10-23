package pe.barberturno.customers;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import pe.barberturno.users.Usuario;

/**
 * Ficha de un cliente dentro del negocio de la transacción, protegida por RLS FORCE.
 * Los datos locales no actualizan la identidad global (V2-RN-16).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Entity
@Table(name = "cliente")
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(length = 16)
    private String telefono;
    @Column(name = "privacidad_aceptada_en")
    private Instant privacidadAceptadaEn;
    @Column(nullable = false)
    private boolean activo;
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Hidrata la ficha exclusivamente desde JPA. */
    protected Cliente() { }

    /**
     * Copia los datos iniciales de la cuenta sin mantener una sincronización posterior.
     * @param usuario cuenta vinculada, ya persistida
     * @param ahora instante del reloj del servicio
     * @throws NullPointerException si falta la cuenta o el instante
     */
    public Cliente(Usuario usuario, Instant ahora) {
        this.usuario = Objects.requireNonNull(usuario);
        this.nombre = usuario.getNombre();
        this.telefono = usuario.getTelefono();
        this.privacidadAceptadaEn = usuario.getPrivacidadAceptadaEn();
        this.activo = true;
        this.creadoEn = Objects.requireNonNull(ahora);
        this.actualizadoEn = ahora;
    }

    /**
     * Edita solo los datos locales; la identidad y las credenciales permanecen intactas.
     * @param nombre nombre local validado por el DTO
     * @param telefono teléfono local opcional
     * @param ahora instante del reloj del servicio
     * @throws NullPointerException si falta el nombre o el instante
     */
    public void actualizarDatos(String nombre, String telefono, Instant ahora) {
        this.nombre = Objects.requireNonNull(nombre);
        this.telefono = telefono;
        this.actualizadoEn = Objects.requireNonNull(ahora);
    }

    /**
     * Desactiva el papel de cliente sin modificar la cuenta ni eliminar su historial.
     * @param ahora instante del reloj del servicio
     * @throws NullPointerException si falta el instante
     */
    public void desactivar(Instant ahora) {
        this.actualizadoEn = Objects.requireNonNull(ahora);
        activo = false;
    }

    /** {@return identificador de ficha, independiente del de la cuenta} */
    public Long getId() { return id; }
    /** {@return cuenta destinataria de avisos; nula en una futura ficha sin cuenta} */
    public Usuario getUsuario() { return usuario; }
    /** {@return nombre local del cliente} */
    public String getNombre() { return nombre; }
    /** {@return teléfono local opcional} */
    public String getTelefono() { return telefono; }
    /** {@return consentimiento copiado al crear la ficha} */
    public Instant getPrivacidadAceptadaEn() { return privacidadAceptadaEn; }
    /** {@return si la ficha permite actuar como cliente en este negocio} */
    public boolean isActivo() { return activo; }
}
