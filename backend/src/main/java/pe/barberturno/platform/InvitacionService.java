package pe.barberturno.platform;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.barberturno.auth.PoliticaPassword;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.platform.dto.*;
import pe.barberturno.users.UsuarioRepository;
import pe.barberturno.users.Usuario;
import pe.barberturno.users.Rol;

/**
 * Emite capacidades secretas y consume una sola vez bajo bloqueo de fila, sin vincular perfiles.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class InvitacionService {
    private final InvitacionRepository invitaciones;
    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final ProveedorNegocioActual negocio;
    private final EntityManager entidades;
    private final Clock clock;
    private final PasswordEncoder passwords;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Recibe persistencia acotada y el reloj común, sin proveedor de correo ni servicios externos.
     * @param invitaciones acreditaciones sin CRUD global
     * @param membresias pertenencias por negocio y cuenta
     * @param usuarios identidades verificadas y bloqueo de cuenta
     * @param negocio ámbito de la ruta para la gestión local
     * @param entidades lectura y bloqueo del estado administrativo
     * @param clock fuente única de instantes
     * @param passwords codificador BCrypt compartido con el registro de clientes
     */
    public InvitacionService(InvitacionRepository invitaciones, MembresiaRepository membresias,
            UsuarioRepository usuarios, ProveedorNegocioActual negocio, EntityManager entidades, Clock clock,
            PasswordEncoder passwords) {
        this.invitaciones = invitaciones; this.membresias = membresias; this.usuarios = usuarios;
        this.negocio = negocio; this.entidades = entidades; this.clock = clock;
        this.passwords = passwords;
    }

    /**
     * Emite una invitación del negocio resuelto, con el rol autorizado por R2-08.
     * @param datos destinatario y rol validados
     * @param actor personal revalidado en el negocio
     * @return metadatos y enlace mostrado únicamente en esta respuesta
     * @throws NegocioException si falta permiso o existe una pendiente para el correo
     */
    @Transactional
    public InvitacionEmitidaDto crear(CrearInvitacionDto datos, UsuarioAutenticado actor) {
        exigirGestion(actor, datos.rol());
        return emitir(negocio.negocioActual().orElseThrow(), datos.correo(), datos.rol(), actor.id());
    }

    // Invocado solo tras autorización global de PlataformaService, dentro de su misma transacción.
    InvitacionEmitidaDto gestionarInicial(long negocioId, long actorId, boolean reenviar) {
        var inicial = invitaciones.findFirstByNegocioIdAndRolOrderByIdAsc(negocioId, RolNegocio.PROPIETARIO)
                .orElseThrow(this::noEncontrado);
        for (var pendiente : invitaciones.listarPendientes(negocioId)) {
            if (pendiente.rol() == RolNegocio.PROPIETARIO && pendiente.correo().equals(inicial.correo())) {
                var bloqueada = invitaciones.bloquearPorNegocio(negocioId, pendiente.id()).orElseThrow(this::noEncontrado);
                if (bloqueada.pendiente()) bloqueada.revocar(clock.instant());
            }
        }
        entidades.flush();
        return reenviar ? emitir(negocioId, inicial.correo(), RolNegocio.PROPIETARIO, actorId) : null;
    }

    // Invocado solo tras autorización global de PlataformaService, dentro de su misma transacción.
    InvitacionEmitidaDto emitir(long negocioId, String correo, RolNegocio rol, long actorId) {
        // Serializa emisiones por negocio y evita que una violación única registre correos en Hibernate.
        var estado = entidades.createNativeQuery("select estado from negocio where id=:id for update", String.class)
                .setParameter("id", negocioId).getSingleResult();
        if ("SUSPENDIDO".equals(estado)) throw suspendido();
        String normalizado = correo.toLowerCase(Locale.ROOT);
        if (invitaciones.listarPendientes(negocioId).stream().anyMatch(i -> i.correo().equals(normalizado))) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "Ya existe una invitación pendiente.");
        }
        byte[] bytes = new byte[32]; aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var invitacion = invitaciones.save(new Invitacion(negocioId, normalizado, rol, hash(token), actorId, clock.instant()));
        return new InvitacionEmitidaDto(dto(invitacion), "/api/invitaciones/" + token);
    }

    /**
     * Lista pendientes del negocio con información de gestión y sin recuperar sus enlaces.
     * @param actor propietario o encargado del ámbito actual
     * @return metadatos pendientes ordenados
     * @throws NegocioException si no tiene rol de gestión
     */
    @Transactional(readOnly = true)
    public List<InvitacionDto> listar(UsuarioAutenticado actor) {
        exigirGestion(actor, RolNegocio.BARBERO);
        return invitaciones.listarPendientes(negocio.negocioActual().orElseThrow()).stream().map(this::dto).toList();
    }

    /**
     * Invalida la capacidad bajo el mismo bloqueo usado por su aceptación.
     * @param id pendiente del negocio de la ruta
     * @param actor personal revalidado con permiso para el rol invitado
     * @throws NegocioException si es ajena, ya consumida o carece de permiso
     */
    @Transactional
    public void revocar(long id, UsuarioAutenticado actor) {
        exigirGestion(actor, RolNegocio.BARBERO);
        var invitacion = invitaciones.bloquearPorNegocio(negocio.negocioActual().orElseThrow(), id).orElseThrow(this::noEncontrado);
        exigirGestion(actor, invitacion.rol());
        if (!invitacion.pendiente()) throw noEncontrado();
        invitacion.revocar(clock.instant());
    }

    /**
     * Revela negocio, rol, correo oculto y estado solo ante quien tiene la capacidad (V2-DA-46).
     * @param token capacidad opaca presentada por la persona invitada
     * @return proyección pública mínima sin identificadores de cuenta ni hash
     * Una invitación pendiente de una barbería suspendida no se presenta como aceptable (V2-DA-38/46).
     * @throws NegocioException si el token es desconocido o está mal formado (404), o si sigue pendiente
     * y su barbería está suspendida (409 NEGOCIO_SUSPENDIDO)
     */
    @Transactional(readOnly = true)
    public InvitacionPublicaDto consultar(String token) {
        var invitacion = invitaciones.findByTokenHash(hashValido(token)).orElseThrow(this::noEncontrado);
        String estado = invitacion.estadoPublico(clock.instant());
        String nombre = "PENDIENTE".equals(estado) ? (String) estadoNegocio(invitacion.negocioId(), false)[0]
                : entidades.unwrap(org.hibernate.Session.class)
                        .createNativeQuery("select nombre from negocio where id=:id", String.class)
                        .setParameter("id", invitacion.negocioId()).getSingleResult();
        String correo = invitacion.correo();
        return new InvitacionPublicaDto(nombre, invitacion.rol(), correo.substring(0, 1) + "***@" + correo.substring(correo.indexOf('@') + 1),
                estado);
    }

    /**
     * Crea o restablece exclusivamente la membresía acreditada; no usa cuerpo ni contexto local.
     * Bloquea invitación y cuenta para serializar consumos y dos tokens de una misma persona.
     * @param token secreto presentado, del que solo se consulta SHA-256
     * @param actor identidad autenticada cuyo correo se contrasta en persistencia
     * @throws NegocioException con 404 si el token es desconocido o mal formado, 403 si el correo
     * corresponde a otra cuenta y 409 si no está vigente, ya pertenece o el negocio está suspendido
     */
    @Transactional
    public void aceptar(String token, UsuarioAutenticado actor) {
        if (actor == null) throw new NegocioException(ErrorCodigo.PROHIBIDO, "Se requiere una cuenta autenticada.");
        var invitacion = invitaciones.bloquearPorToken(hashValido(token)).orElseThrow(this::noEncontrado);
        if (!invitacion.vigente(clock.instant())) {
            throw new NegocioException(ErrorCodigo.INVITACION_NO_VIGENTE,
                    "La invitación no está vigente: " + invitacion.estadoPublico(clock.instant()) + ".");
        }
        // Toda acreditación del equipo usa el bloqueo de propietarios antes de ① (V2-RN-06).
        membresias.bloquearPropietarios(invitacion.negocioId());
        var usuario = usuarios.bloquearPorId(actor.id()).orElseThrow(this::noEncontrado);
        if (!usuario.getCorreo().equalsIgnoreCase(invitacion.correo())) {
            throw new NegocioException(ErrorCodigo.INVITACION_OTRA_CUENTA,
                    "Esta invitación es para otra cuenta. Ingrese con el correo invitado.");
        }
        estadoNegocio(invitacion.negocioId(), true);
        var anterior = membresias.findByNegocioIdAndUsuarioId(invitacion.negocioId(), actor.id());
        if (anterior.filter(Membresia::isActiva).isPresent()) {
            throw new NegocioException(ErrorCodigo.YA_ES_MIEMBRO, "La cuenta ya pertenece al negocio.");
        }
        if (anterior.isPresent()) anterior.get().restablecerPorInvitacion(invitacion.rol(), clock.instant());
        else membresias.save(new Membresia(invitacion.negocioId(), actor.id(), invitacion.rol(), clock.instant()));
        invitacion.aceptar(actor.id(), clock.instant());
    }

    /**
     * Registra y acredita personal en una sola transacción, sin ficha ni permisos de plataforma.
     * El bloqueo del token serializa los registros; el de propietarios precede a la creación de la cuenta.
     * El rol heredado CLIENTE solo satisface el esquema de v1 y nunca concede acceso en v2.
     * @param token capacidad que determina correo, negocio y rol de la membresía
     * @param datos identidad, teléfono, contraseña y consentimiento validados por MVC
     * @return cuenta nueva cuya sesión se emite después del commit
     * @throws NegocioException si el token no existe, no está vigente, el negocio está suspendido,
     * el correo ya existe o la contraseña incumple RN-25; ningún cambio parcial se conserva
     */
    @Transactional
    public Usuario registrar(String token, RegistroInvitadoDto datos) {
        var invitacion = invitaciones.bloquearPorToken(hashValido(token)).orElseThrow(this::noEncontrado);
        if (!invitacion.vigente(clock.instant())) {
            throw new NegocioException(ErrorCodigo.INVITACION_NO_VIGENTE,
                    "La invitación no está vigente: " + invitacion.estadoPublico(clock.instant()) + ".");
        }
        membresias.bloquearPropietarios(invitacion.negocioId());
        estadoNegocio(invitacion.negocioId(), true);
        if (!new PoliticaPassword().validar(datos.password()).isEmpty()) {
            throw new NegocioException(ErrorCodigo.VALIDACION,
                    "La contraseña debe tener de 8 a 72 caracteres, máximo 72 bytes UTF-8, una letra y un dígito.");
        }
        if (usuarios.findByCorreo(invitacion.correo()).isPresent()) {
            throw new NegocioException(ErrorCodigo.CORREO_DUPLICADO, "El correo ya está registrado. Ingrese con su cuenta.");
        }
        var ahora = clock.instant();
        var usuario = usuarios.saveAndFlush(new Usuario(datos.nombre(), invitacion.correo(), datos.telefono(),
                passwords.encode(datos.password()), Rol.CLIENTE, ahora, ahora));
        // Reutiliza el consumo íntegro: mismo bloqueo, revalidación y reglas que la aceptación autenticada.
        aceptar(token, new UsuarioAutenticado(usuario.getId(), null, Optional.empty(), false));
        return usuario;
    }

    private Object[] estadoNegocio(long id, boolean bloquear) {
        var fila = entidades.unwrap(org.hibernate.Session.class).createNativeQuery("select nombre,estado from negocio where id=:id" + (bloquear ? " for share" : ""), Object[].class)
                .setParameter("id", id).getSingleResult();
        if ("SUSPENDIDO".equals(fila[1])) throw suspendido();
        return fila;
    }
    private void exigirGestion(UsuarioAutenticado actor, RolNegocio rol) {
        if (actor == null || !(actor.rolNegocio().filter(r -> r == RolNegocio.PROPIETARIO
                || r == RolNegocio.ENCARGADO && rol == RolNegocio.BARBERO).isPresent())) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "No tiene permiso para gestionar esta invitación.");
        }
    }
    private InvitacionDto dto(Invitacion i) { return new InvitacionDto(i.id(), i.correo(), i.rol(), i.expiraEn()); }
    private byte[] hashValido(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw noEncontrado();
        return hash(token);
    }
    private byte[] hash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 no disponible.", error); }
    }
    private NegocioException noEncontrado() { return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró la invitación."); }
    private NegocioException suspendido() { return new NegocioException(ErrorCodigo.NEGOCIO_SUSPENDIDO, "El negocio está suspendido."); }
}
