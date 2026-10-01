package pe.barberturno.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.auth.dto.*;
import pe.barberturno.common.error.*;
import pe.barberturno.common.error.ManejadorErrores.ErrorCampo;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.users.*;

/**
 * Registro, autenticación, cambio de contraseña y administrador inicial idempotente.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class AuthService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final String CREDENCIALES = "No se pudo iniciar sesión con las credenciales indicadas.";
    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final PoliticaPassword politica = new PoliticaPassword();
    private final String hashFicticio;

    /** @param usuarios identidades persistidas
     * @param barberos perfiles opcionales
     * @param passwords BCrypt de coste 12
     * @param clock reloj de negocio */
    public AuthService(UsuarioRepository usuarios, BarberoRepository barberos,
            PasswordEncoder passwords, Clock clock) {
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.passwords = passwords;
        this.clock = clock;
        hashFicticio = passwords.encode(UUID.randomUUID().toString());
    }

    /** @param datos registro validado
     * @return usuario recién creado
     * @throws NegocioException si el correo existe o la contraseña incumple RN-25 */
    @Transactional
    public Usuario registrar(RegistroDto datos) {
        validarPassword(datos.password());
        String correo = normalizar(datos.correo());
        if (usuarios.findByCorreo(correo).isPresent()) {
            throw new NegocioException(ErrorCodigo.CORREO_DUPLICADO, "El correo ya está registrado.");
        }
        var ahora = clock.instant();
        Usuario usuario = usuarios.saveAndFlush(new Usuario(datos.nombre(), correo, datos.telefono(),
                passwords.encode(datos.password()), Rol.CLIENTE, ahora, ahora));
        LOG.info("Registro de usuario id={}.", usuario.getId());
        return usuario;
    }

    /** Conserva los fallos y serializa accesos a una misma cuenta.
     * @param datos credenciales
     * @return usuario autenticado
     * @throws NegocioException con mensaje genérico si el acceso no está permitido */
    @Transactional(noRollbackFor = NegocioException.class)
    public Usuario login(LoginDto datos) {
        var encontrado = usuarios.bloquearPorCorreo(normalizar(datos.correo()));
        if (encontrado.isEmpty()) {
            coincide(datos.password(), hashFicticio);
            LOG.info("Login fallido de cuenta no encontrada.");
            throw rechazo(ErrorCodigo.CREDENCIALES_INVALIDAS);
        }
        Usuario usuario = encontrado.get();
        var ahora = clock.instant();
        boolean correcta = coincide(datos.password(), usuario.getPasswordHash());
        if (!usuario.isActivo()) {
            LOG.info("Login fallido de usuario id={}.", usuario.getId());
            throw rechazo(ErrorCodigo.CREDENCIALES_INVALIDAS);
        }
        if (usuario.getBloqueadoHasta() != null && ahora.isBefore(usuario.getBloqueadoHasta())) {
            throw rechazo(ErrorCodigo.CUENTA_BLOQUEADA_TEMPORALMENTE);
        }
        // Un bloqueo vencido inicia una nueva serie de intentos.
        if (usuario.getBloqueadoHasta() != null) usuario.registrarAccesoCorrecto(ahora);
        if (!correcta) {
            boolean bloquear = usuario.getIntentosFallidos() + 1 >= 5;
            usuario.registrarIntentoFallido(bloquear ? ahora.plus(Duration.ofMinutes(15)) : null, ahora);
            LOG.info("Login fallido de usuario id={}.", usuario.getId());
            if (bloquear) LOG.warn("Bloqueo temporal de usuario id={}.", usuario.getId());
            throw rechazo(bloquear ? ErrorCodigo.CUENTA_BLOQUEADA_TEMPORALMENTE
                    : ErrorCodigo.CREDENCIALES_INVALIDAS);
        }
        usuario.registrarAccesoCorrecto(ahora);
        return usuario;
    }

    /** @param usuario identidad persistida
     * @return DTO sin datos sensibles */
    @Transactional(readOnly = true)
    public UsuarioSesionDto sesion(Usuario usuario) {
        return new UsuarioSesionDto(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getRol(), barberos.buscarIdPorUsuario(usuario.getId()).orElse(null),
                usuario.isDebeCambiarPassword());
    }

    /** @param id id validado por el resource server
     * @return datos públicos vigentes
     * @throws NegocioException si ya no existe la identidad */
    @Transactional(readOnly = true)
    public UsuarioSesionDto sesion(long id) {
        return sesion(usuarios.findById(id).orElseThrow(() -> rechazo(ErrorCodigo.NO_AUTENTICADO)));
    }

    /** @param id identificador del token
     * @param tokenVersion versión firmada
     * @return principal vigente o vacío ante una revocación */
    @Transactional(readOnly = true)
    public java.util.Optional<UsuarioAutenticado> autenticar(long id, int tokenVersion) {
        return usuarios.findById(id).filter(u -> u.isActivo() && u.getTokenVersion() == tokenVersion)
                .map(u -> new UsuarioAutenticado(u.getId(), u.getRol(),
                        barberos.buscarIdPorUsuario(u.getId()), u.isDebeCambiarPassword()));
    }

    /** @param correo correo inicial
     * @param password contraseña inicial
     * @param nombre nombre inicial
     * @throws NegocioException si la contraseña no cumple RN-25 */
    @Transactional
    public void crearAdminInicial(String correo, String password, String nombre) {
        if (usuarios.existsByRolAndActivoTrue(Rol.ADMIN)) return;
        if (correo.isBlank() || password.isBlank() || nombre.isBlank()) {
            LOG.info("Administrador inicial sin configurar; no se crea ninguna cuenta.");
            return;
        }
        validarPassword(password);
        var ahora = clock.instant();
        Usuario admin = usuarios.saveAndFlush(new Usuario(nombre, normalizar(correo), null,
                passwords.encode(password), Rol.ADMIN, null, ahora));
        LOG.info("Registro de administrador inicial id={}.", admin.getId());
    }

    /** Cambia la credencial bajo el mismo bloqueo usado por login.
     * @param id identidad autenticada
     * @param datos credencial actual y nueva propuesta
     * @return usuario con hash y versión nuevos para renovar la cookie
     * @throws NegocioException si la identidad no está activa o las credenciales son inválidas */
    @Transactional
    public Usuario cambiarPassword(long id, CambiarPasswordDto datos) {
        Usuario usuario = usuarios.bloquearPorId(id).filter(Usuario::isActivo)
                .orElseThrow(() -> new NegocioException(ErrorCodigo.NO_AUTENTICADO,
                        "Se requiere una sesión válida."));
        if (!coincide(datos.passwordActual(), usuario.getPasswordHash())) {
            throw new NegocioException("No se pudo cambiar la contraseña.",
                    List.of(new ErrorCampo("passwordActual", "No se pudo verificar la contraseña actual.")));
        }
        var errores = politica.validar(datos.passwordNueva()).stream()
                .map(incumplimiento -> new ErrorCampo("passwordNueva", switch (incumplimiento) {
                    case OBLIGATORIA -> "La contraseña nueva es obligatoria.";
                    case LONGITUD -> "Debe tener entre 8 y 72 caracteres.";
                    case BYTES_UTF8 -> "Debe ocupar como máximo 72 bytes UTF-8.";
                    case LETRA -> "Debe incluir al menos una letra.";
                    case DIGITO -> "Debe incluir al menos un dígito.";
                })).toList();
        if (!errores.isEmpty()) throw new NegocioException("Revise la contraseña nueva.", errores);
        if (coincide(datos.passwordNueva(), usuario.getPasswordHash())) {
            throw new NegocioException("Revise la contraseña nueva.",
                    List.of(new ErrorCampo("passwordNueva", "Debe ser distinta de la contraseña actual.")));
        }
        usuario.cambiarPassword(passwords.encode(datos.passwordNueva()), false, clock.instant());
        return usuario;
    }

    private boolean coincide(String password, String hash) {
        boolean longitudValida = password.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean coincide = passwords.matches(longitudValida ? password : "entrada-fuera-del-limite", hash);
        return longitudValida && coincide;
    }
    private void validarPassword(String password) {
        if (!politica.validar(password).isEmpty()) {
            throw new NegocioException(ErrorCodigo.VALIDACION,
                    "La contraseña debe tener de 8 a 72 caracteres, máximo 72 bytes UTF-8, una letra y un dígito.");
        }
    }
    private String normalizar(String correo) { return correo.toLowerCase(Locale.ROOT); }
    private NegocioException rechazo(ErrorCodigo codigo) {
        return new NegocioException(codigo, CREDENCIALES);
    }
}