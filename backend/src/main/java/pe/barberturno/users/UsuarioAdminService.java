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
import pe.barberturno.platform.*;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;

/**
 * Gestión RF-19 con identidad compartida, restricciones del encargado y último propietario por negocio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class UsuarioAdminService {
    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final MembresiaRepository membresias;
    private final ProveedorNegocioActual negocio;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Recibe persistencia, BCrypt y reloj para cambios de acceso atómicos.
     * @param usuarios identidades y bloqueos de acceso
     * @param barberos consulta del perfil opcional
     * @param passwords codificador BCrypt de coste 12
     * @param clock reloj inyectado para fechas de modificación
     * @param membresias pertenencias y bloqueo del último propietario
     * @param negocio ámbito explícito del actor
     */
    public UsuarioAdminService(UsuarioRepository usuarios, BarberoRepository barberos,
            PasswordEncoder passwords, Clock clock, MembresiaRepository membresias, ProveedorNegocioActual negocio) {
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.passwords = passwords;
        this.clock = clock;
        this.membresias = membresias;
        this.negocio = negocio;
    }

    /**
     * Busca por fragmento literal e incluye inactivos; filtra el rol efectivo del negocio.
     * El encargado no recibe propietarios ni encargados, aun con membresías revocadas (R2-08).
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
        Specification<Usuario> filtro = (raiz, consulta, cb) -> {
            var pertenencia = consulta.subquery(Long.class);
            var m = pertenencia.from(Membresia.class);
            pertenencia.select(m.get("usuarioId")).where(cb.equal(m.get("negocioId"), negocio.negocioActual().orElseThrow()),
                    cb.equal(m.get("usuarioId"), raiz.get("id")),
                    cb.equal(m.get("rol"), RolNegocio.BARBERO).not());
            var gestorProtegido = cb.exists(pertenencia);
            var personal = consulta.subquery(Long.class);
            var p = personal.from(Membresia.class);
            personal.select(p.get("usuarioId")).where(cb.equal(p.get("negocioId"), negocio.negocioActual().orElseThrow()),
                    cb.equal(p.get("estado"), "ACTIVA"), cb.equal(p.get("usuarioId"), raiz.get("id")));
            var gestor = cb.and(gestorProtegido, cb.exists(personal));
            var rolFiltro = rol == null ? cb.conjunction() : switch (rol) {
                case ADMIN -> gestor;
                case BARBERO -> cb.and(cb.exists(personal), cb.not(gestor));
                case CLIENTE -> cb.and(cb.not(cb.exists(personal)), cb.equal(raiz.get("rol"), Rol.CLIENTE));
            };
            return cb.and(rolFiltro,
                actor.rolNegocio().orElse(null) == RolNegocio.ENCARGADO ? cb.not(gestorProtegido) : cb.conjunction(),
                cb.or(cb.like(cb.lower(raiz.get("nombre")), patron, '!'),
                        cb.like(cb.lower(raiz.get("correo")), patron, '!')));
        };
        return PaginaDto.desde(usuarios.findAll(filtro,
                PageRequest.of(pagina, tamano, Sort.by("nombre", "id"))).map(this::dto));
    }

    /**
     * Genera doce caracteres ASCII con SecureRandom, letra y dígito garantizados RN-25.
     * Bajo bloqueo de usuario sustituye BCrypt, exige cambio y revoca todas las sesiones CP-17.
     * @param id identidad cuyo acceso se restablece
     * @param actor ADMIN vigente que recibe la credencial una sola vez
     * @return contraseña temporal sin almacenamiento en claro ni registro en log
     * @throws NegocioException si no hay permiso, el objetivo no es gestionable (404) o su identidad
     * es compartida (IDENTIDAD_COMPARTIDA)
     */
    @Transactional
    public PasswordTemporalDto restablecer(long id, UsuarioAutenticado actor) {
        exigirAdmin(actor);
        var usuario = usuarios.bloquearPorId(id).orElseThrow(this::noEncontrado);
        exigirGestionable(usuario, actor);
        exigirIdentidadLocal(usuario);
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
     * Cambia acceso bajo advisory (313131, negocio) antes de ① y del conteo de propietarios activos.
     * Revalida al actor tras esperar para evitar que dos propietarios se desactiven mutuamente.
     * La repetición del estado no incrementa la versión otra vez; el perfil se gestiona por RF-05.
     * @param id identidad cuyo acceso cambia
     * @param activo habilitación solicitada
     * @param actor ADMIN vigente; no puede desactivarse a sí mismo
     * @throws NegocioException si no es ADMIN, fue desactivado, falta usuario, se desactiva a sí mismo
     * o se intenta eliminar el último propietario activo (CONFLICTO); identidad compartida produce 409
     */
    @Transactional
    public void cambiarEstado(long id, boolean activo, UsuarioAutenticado actor) {
        exigirAdmin(actor);
        long negocioId = negocio.negocioActual().orElseThrow();
        membresias.bloquearPropietarios(negocioId);
        var vigente = usuarios.findById(actor.id()).orElseThrow(this::noEncontrado);
        var rolActor = rolNegocio(vigente.getId());
        if (!vigente.isActivo() || rolActor == null || rolActor == RolNegocio.BARBERO) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Su acceso administrativo cambió.");
        }
        var usuario = usuarios.bloquearPorId(id).orElseThrow(this::noEncontrado);
        exigirGestionable(usuario, new UsuarioAutenticado(actor.id(), Rol.ADMIN, actor.barberoId(),
                actor.debeCambiarPassword(), vigente.getRolPlataforma(), java.util.Optional.of(rolActor)));
        exigirIdentidadLocal(usuario);
        if (!activo && id == actor.id()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "No puede desactivar su propia cuenta.");
        }
        if (!activo && usuario.isActivo() && rolNegocio(usuario.getId()) == RolNegocio.PROPIETARIO
                && membresias.contarPropietariosActivos(negocioId) <= 1) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "Debe quedar un administrador activo.");
        }
        if (usuario.isActivo() != activo) {
            if (activo) usuario.activar(clock.instant());
            else usuario.desactivar(clock.instant());
            usuarios.saveAndFlush(usuario);
        }
    }

    private UsuarioAdminDto dto(Usuario u) {
        var rol = rolNegocio(u.getId());
        return new UsuarioAdminDto(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(),
                rol == null ? (u.getRol() == Rol.CLIENTE ? Rol.CLIENTE : null) : rol.rolEfectivo(),
                u.isActivo(), u.isDebeCambiarPassword(), barberos.buscarIdPorUsuario(u.getId()).orElse(null));
    }
    private RolNegocio rolNegocio(long usuarioId) {
        return membresias.findByNegocioIdAndUsuarioId(negocio.negocioActual().orElseThrow(), usuarioId)
                .filter(Membresia::isActiva).map(Membresia::getRol).orElse(null);
    }
    private void exigirGestionable(Usuario usuario, UsuarioAutenticado actor) {
        var rol = membresias.findByNegocioIdAndUsuarioId(negocio.negocioActual().orElseThrow(), usuario.getId())
                .map(Membresia::getRol).orElse(null);
        if (actor.rolNegocio().orElse(null) == RolNegocio.ENCARGADO
                && (rol == RolNegocio.PROPIETARIO || rol == RolNegocio.ENCARGADO)) throw noEncontrado();
    }
    private void exigirIdentidadLocal(Usuario usuario) {
        if (membresias.existeActivaEnOtroNegocio(usuario.getId(), negocio.negocioActual().orElseThrow())) {
            throw new NegocioException(ErrorCodigo.IDENTIDAD_COMPARTIDA,
                    "La cuenta pertenece también a otro negocio; el cambio lo hace la plataforma");
        }
    }
    private void exigirAdmin(UsuarioAutenticado actor) {
        if (actor.rol() != Rol.ADMIN) throw new NegocioException(ErrorCodigo.PROHIBIDO, "Se requiere permiso administrativo.");
    }
    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "El usuario solicitado no existe.");
    }
}
