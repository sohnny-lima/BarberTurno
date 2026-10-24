package pe.barberturno.scheduling;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.auth.PoliticaPassword;
import pe.barberturno.common.error.*;
import pe.barberturno.common.error.ManejadorErrores.ErrorCampo;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.scheduling.dto.*;
import pe.barberturno.users.*;
import pe.barberturno.platform.*;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.customers.VinculoClienteRepository;

/**
 * Administra personal RF-05 del ámbito V2-RN-17 con identidad de plataforma protegida y cupo RN-19.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class BarberoService {
    private final BarberoRepository barberos;
    private final UsuarioRepository usuarios;
    private final ReservaRepository reservas;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final MembresiaRepository membresias;
    private final ProveedorNegocioActual negocio;
    private final AmbitoUsuarios ambito;
    private final VinculoClienteRepository vinculos;
    private final PoliticaPassword politica = new PoliticaPassword();

    /**
     * Inyecta persistencia, BCrypt y Clock para cambios atómicos de cuenta y perfil.
     * @param barberos perfiles y bloqueo global RN-19
     * @param usuarios identidades y bloqueo de cuenta
     * @param reservas agenda para informar citas futuras
     * @param passwords BCrypt de coste 12
     * @param clock reloj para fechas y criterio de futuro
     * @param membresias pertenencias explícitas y comprobación de identidad compartida
     * @param negocio ámbito transitorio actual
     * @param ambito límite común de identidades gestionables según V2-DA-27
     * @param vinculos protección de identidad global cuando hay fichas en otros negocios
     */
    public BarberoService(BarberoRepository barberos, UsuarioRepository usuarios, ReservaRepository reservas,
            PasswordEncoder passwords, Clock clock, MembresiaRepository membresias, ProveedorNegocioActual negocio,
            AmbitoUsuarios ambito, VinculoClienteRepository vinculos) {
        this.barberos = barberos;
        this.usuarios = usuarios;
        this.reservas = reservas;
        this.passwords = passwords;
        this.clock = clock;
        this.membresias = membresias;
        this.negocio = negocio;
        this.ambito = ambito;
        this.vinculos = vinculos;
    }

    /**
     * Proyecta perfiles en orden de nombre, limitando contactos e inactivos según permiso ADMIN.
     * @param incluirInactivos solicitud de perfiles deshabilitados
     * @param admin permiso de contactos e inactivos concedido por el controlador
     * @return perfiles autorizados sin credenciales
     */
    @Transactional(readOnly = true)
    public List<BarberoDto> listar(boolean incluirInactivos, boolean admin) {
        return barberos.listar(incluirInactivos && admin).stream().map(b -> BarberoDto.desde(b, admin)).toList();
    }

    /**
     * Valida variantes excluyentes, serializa RN-19 y crea cuenta con membresía o vincula gestor del negocio.
     * El correo queda normalizado y el consentimiento se omite porque el personal no es CLIENTE.
     * @param datos alta validada por MVC
     * @return perfil activo con contactos para ADMIN
     * @throws NegocioException si la variante o contraseña falla (VALIDACION), falta usuario (NO_ENCONTRADO),
     * el vínculo no es válido (CONFLICTO), el correo existe (CORREO_DUPLICADO) o se supera RN-19
     */
    @Transactional
    public BarberoDto crear(CrearBarberoDto datos) {
        validarVariante(datos);
        barberos.bloquearLimiteActivos();
        comprobarLimite();
        Usuario usuario;
        var ahora = clock.instant();
        if (datos.usuarioId() != null) {
            usuario = usuarios.bloquearPorId(datos.usuarioId()).orElseThrow(this::noEncontrado);
            var membresia = membresias.findByNegocioIdAndUsuarioId(negocio.negocioActual().orElseThrow(), usuario.getId())
                    .filter(Membresia::isActiva).filter(m -> m.getRol() != RolNegocio.BARBERO);
            if (membresia.isEmpty()) throw noEncontrado();
            if (!usuario.isActivo() || barberos.buscarIdPorUsuario(usuario.getId()).isPresent()) {
                throw new NegocioException(ErrorCodigo.CONFLICTO,
                        "Solo puede vincular un administrador activo sin perfil de barbero.");
            }
        } else {
            String correo = datos.correo().toLowerCase(Locale.ROOT);
            if (usuarios.findByCorreo(correo).isPresent()) {
                throw new NegocioException(ErrorCodigo.CORREO_DUPLICADO, "El correo ya está registrado.");
            }
            String hash = passwords.encode(datos.passwordTemporal());
            usuario = new Usuario(datos.nombre(), correo, datos.telefono(), hash, Rol.BARBERO, null, ahora);
            usuario.cambiarPassword(hash, true, ahora);
            usuarios.saveAndFlush(usuario);
            membresias.save(new Membresia(negocio.negocioActual().orElseThrow(), usuario.getId(), RolNegocio.BARBERO, ahora));
        }
        return BarberoDto.desde(barberos.saveAndFlush(new Barbero(usuario, datos.especialidad(), ahora)), true);
    }

    /**
     * Bloquea usuario antes que perfil y edita identidad y especialidad sin cambiar correo, acceso ni reservas.
     * @param id perfil persistido
     * @param datos nombre, teléfono opcional y especialidad validados
     * @return perfil editado con contactos para ADMIN
     * @throws NegocioException si el perfil no existe (NO_ENCONTRADO) o el cambio global afecta a una
     * identidad compartida o de plataforma (IDENTIDAD_COMPARTIDA); fuera del ámbito produce 404
     */
    @Transactional
    public BarberoDto editar(long id, EditarBarberoDto datos) {
        Barbero barbero = obtenerBloqueado(id);
        var usuario = barbero.getUsuario();
        boolean cambiaIdentidad = !java.util.Objects.equals(usuario.getNombre(), datos.nombre())
                || !java.util.Objects.equals(usuario.getTelefono(), datos.telefono());
        if (cambiaIdentidad && compartida(usuario)) {
            throw new NegocioException(ErrorCodigo.IDENTIDAD_COMPARTIDA,
                    "La cuenta pertenece también a otro negocio; el cambio lo hace la plataforma");
        }
        var ahora = clock.instant();
        if (cambiaIdentidad) usuario.actualizarPerfil(datos.nombre(), datos.telefono(), ahora);
        barbero.editarEspecialidad(datos.especialidad(), ahora);
        return BarberoDto.desde(barberos.saveAndFlush(barbero), true);
    }

    /**
     * Serializa estado con bloqueo global RN-19 y después usuario y perfil; revoca sesiones al desactivar
     * BARBERO no compartido, conserva acceso de gestores y las cuentas compartidas o de plataforma.
     * Repetir el mismo estado no revoca otra vez.
     * @param id perfil que se habilita o deshabilita
     * @param activo nuevo estado solicitado
     * @return perfil resultante y cantidad de citas futuras que ocupan franja RN-14
     * @throws NegocioException si falta perfil o está fuera del ámbito (NO_ENCONTRADO),
     * o la reactivación supera RN-19
     */
    @Transactional
    public ResultadoEstadoBarberoDto cambiarEstado(long id, boolean activo) {
        barberos.bloquearLimiteActivos();
        Barbero barbero = obtenerBloqueado(id);
        boolean cambiaCuenta = membresias.findByNegocioIdAndUsuarioId(negocio.negocioActual().orElseThrow(),
                barbero.getUsuario().getId()).filter(Membresia::isActiva)
                .map(m -> m.getRol() == RolNegocio.BARBERO).orElse(false) && !compartida(barbero.getUsuario());
        var ahora = clock.instant();
        if (barbero.isActivo() != activo) {
            if (activo) {
                comprobarLimite();
                barbero.activar(ahora);
                if (cambiaCuenta) barbero.getUsuario().activar(ahora);
            } else {
                barbero.desactivar(ahora);
                if (cambiaCuenta) barbero.getUsuario().desactivar(ahora);
            }
        }
        barberos.saveAndFlush(barbero);
        return new ResultadoEstadoBarberoDto(BarberoDto.desde(barbero, true),
                reservas.contarFuturasVigentes(id, ahora));
    }

    private boolean compartida(Usuario usuario) {
        return usuario.getRolPlataforma() != RolPlataforma.NINGUNO
                || membresias.existeActivaEnOtroNegocio(usuario.getId(), negocio.negocioActual().orElseThrow())
                || vinculos.existeEnOtroNegocio(usuario.getId(), negocio.negocioActual().orElseThrow());
    }

    private Barbero obtenerBloqueado(long id) {
        long usuarioId = barberos.buscarUsuarioId(id).orElseThrow(this::noEncontrado);
        var usuario = usuarios.bloquearPorId(usuarioId).orElseThrow(this::noEncontrado);
        if (!ambito.contiene(usuario)) throw noEncontrado();
        return barberos.bloquearPorIds(List.of(id)).stream().findFirst().orElseThrow(this::noEncontrado);
    }

    private void comprobarLimite() {
        if (barberos.countByActivoTrue() >= 10) {
            throw new NegocioException(ErrorCodigo.LIMITE_BARBEROS_ACTIVOS,
                    "Solo puede haber diez barberos activos.");
        }
    }

    private void validarVariante(CrearBarberoDto datos) {
        var errores = new ArrayList<ErrorCampo>();
        if (datos.usuarioId() != null) {
            if (datos.nombre() != null || datos.correo() != null || datos.telefono() != null
                    || datos.passwordTemporal() != null) {
                errores.add(new ErrorCampo("usuarioId", "El vínculo no admite campos de una cuenta nueva."));
            }
        } else {
            if (datos.nombre() == null || datos.nombre().isBlank()) {
                errores.add(new ErrorCampo("nombre", "El nombre es obligatorio para una cuenta nueva."));
            }
            if (datos.correo() == null || datos.correo().isBlank()) {
                errores.add(new ErrorCampo("correo", "El correo es obligatorio para una cuenta nueva."));
            }
            if (!politica.validar(datos.passwordTemporal()).isEmpty()) {
                errores.add(new ErrorCampo("passwordTemporal",
                        "Debe tener de 8 a 72 caracteres, máximo 72 bytes UTF-8, una letra y un dígito."));
            }
        }
        if (!errores.isEmpty()) throw new NegocioException("Revise los datos del alta.", errores);
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "El perfil o usuario solicitado no existe.");
    }
}
