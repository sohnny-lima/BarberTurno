package pe.barberturno.users;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.users.dto.*;

/**
 * Gestión RF-19 con revocación inmediata y serialización de la protección del último ADMIN.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class UsuarioAdminService {
    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Recibe persistencia, BCrypt y reloj para cambios de acceso atómicos.
     * @param usuarios identidades y bloqueos de acceso
     * @param barberos consulta del perfil opcional
     * @param passwords codificador BCrypt de coste 12
     * @param clock reloj inyectado para fechas de modificación
     */
    public UsuarioAdminService(UsuarioRepository usuarios, BarberoRepository barberos,
            PasswordEncoder passwords, Clock clock) {
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.passwords = passwords;
        this.clock = clock;
    }

    /**
     * Busca nombre o correo por fragmento literal sin distinguir mayúsculas; incluye inactivos.
     * @param q fragmento opcional; blanco equivale a todos
     * @param rol filtro opcional de categoría
     * @param pagina índice desde cero
     * @param tamano cantidad entre uno y cien
     * @param actor ADMIN vigente
     * @return página ordenada por nombre e identidad, sin credenciales
     * @throws NegocioException si el actor no es ADMIN o la paginación es inválida
     */
    @Transactional(readOnly = true)
    public PaginaDto<UsuarioAdminDto> listar(String q, Rol rol, int pagina, int tamano, UsuarioAutenticado actor) {
        exigirAdmin(actor);
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new NegocioException(ErrorCodigo.VALIDACION, "Página o tamaño inválidos.");
        }
        String texto = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String patron = "%" + texto.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Specification<Usuario> filtro = (raiz, consulta, cb) -> cb.and(
                rol == null ? cb.conjunction() : cb.equal(raiz.get("rol"), rol),
                cb.or(cb.like(cb.lower(raiz.get("nombre")), patron, '!'),
                        cb.like(cb.lower(raiz.get("correo")), patron, '!')));
        return PaginaDto.desde(usuarios.findAll(filtro,
                PageRequest.of(pagina, tamano, Sort.by("nombre", "id"))).map(this::dto));
    }

    /**
     * Genera doce caracteres ASCII con SecureRandom, letra y dígito garantizados RN-25.
     * Bajo bloqueo de usuario sustituye BCrypt, exige cambio y revoca todas las sesiones CP-17.
     * @param id identidad cuyo acceso se restablece
     * @param actor ADMIN vigente que recibe la credencial una sola vez
     * @return contraseña temporal sin almacenamiento en claro ni registro en log
     * @throws NegocioException si el rol no es ADMIN o el usuario no existe
     */
    @Transactional
    public PasswordTemporalDto restablecer(long id, UsuarioAutenticado actor) {
        exigirAdmin(actor);
        var usuario = usuarios.bloquearPorId(id).orElseThrow(this::noEncontrado);
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        String digitos = "23456789";
        String alfabeto = letras + digitos;
        char[] temporal = new char[12];
        temporal[0] = letras.charAt(aleatorio.nextInt(letras.length()));
        temporal[1] = digitos.charAt(aleatorio.nextInt(digitos.length()));
        for (int i = 2; i < temporal.length; i++) temporal[i] = alfabeto.charAt(aleatorio.nextInt(alfabeto.length()));
        for (int i = temporal.length - 1; i > 0; i--) {
            int j = aleatorio.nextInt(i + 1);
            char previo = temporal[i]; temporal[i] = temporal[j]; temporal[j] = previo;
        }
        String password = new String(temporal);
        usuario.cambiarPassword(passwords.encode(password), true, clock.instant());
        usuario.registrarAccesoCorrecto(clock.instant());
        usuarios.saveAndFlush(usuario);
        return new PasswordTemporalDto(password);
    }

    /**
     * Cambia acceso RN-16 bajo advisory 313131 antes de filas y conteo de ADMIN activos.
     * Revalida al actor tras esperar: dos ADMIN desactivándose mutuamente dejan uno activo.
     * La repetición del estado no incrementa la versión otra vez; el perfil se gestiona por RF-05.
     * @param id identidad cuyo acceso cambia
     * @param activo habilitación solicitada
     * @param actor ADMIN vigente; no puede desactivarse a sí mismo
     * @throws NegocioException si no es ADMIN, fue desactivado, falta usuario, se desactiva a sí mismo
     * o se intenta eliminar el último ADMIN activo (CONFLICTO)
     */
    @Transactional
    public void cambiarEstado(long id, boolean activo, UsuarioAutenticado actor) {
        exigirAdmin(actor);
        usuarios.bloquearAdministradoresActivos();
        var vigente = usuarios.findById(actor.id()).orElseThrow(this::noEncontrado);
        if (!vigente.isActivo() || vigente.getRol() != Rol.ADMIN) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Su acceso administrativo cambió.");
        }
        var usuario = usuarios.bloquearPorId(id).orElseThrow(this::noEncontrado);
        if (!activo && id == actor.id()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "No puede desactivar su propia cuenta.");
        }
        if (!activo && usuario.isActivo() && usuario.getRol() == Rol.ADMIN
                && usuarios.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "Debe quedar un administrador activo.");
        }
        if (usuario.isActivo() != activo) {
            if (activo) usuario.activar(clock.instant());
            else usuario.desactivar(clock.instant());
            usuarios.saveAndFlush(usuario);
        }
    }

    private UsuarioAdminDto dto(Usuario u) {
        return new UsuarioAdminDto(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(), u.getRol(),
                u.isActivo(), u.isDebeCambiarPassword(), barberos.buscarIdPorUsuario(u.getId()).orElse(null));
    }
    private void exigirAdmin(UsuarioAutenticado actor) {
        if (actor.rol() != Rol.ADMIN) throw new NegocioException(ErrorCodigo.PROHIBIDO, "Se requiere permiso administrativo.");
    }
    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "El usuario solicitado no existe.");
    }
}
