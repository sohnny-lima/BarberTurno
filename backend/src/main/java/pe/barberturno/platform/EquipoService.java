package pe.barberturno.platform;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.platform.dto.EquipoDto;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.users.UsuarioRepository;

/**
 * Gestiona permisos locales con el orden total de bloqueos de V2-DA-40 y sin escribir cuentas.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class EquipoService {
    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final ProveedorNegocioActual negocio;
    private final Clock clock;

    /**
     * Recibe persistencia acotada y el contexto establecido por la ruta.
     * @param membresias pertenencias y bloqueo de propietarios
     * @param usuarios bloqueo de cuenta previo al perfil
     * @param barberos cupo y bloqueo de perfiles locales
     * @param negocio ámbito resuelto en el servidor
     * @param clock fuente única de instantes
     */
    public EquipoService(MembresiaRepository membresias, UsuarioRepository usuarios,
            BarberoRepository barberos, ProveedorNegocioActual negocio, Clock clock) {
        this.membresias = membresias; this.usuarios = usuarios; this.barberos = barberos;
        this.negocio = negocio; this.clock = clock;
    }

    /**
     * Lista pertenencias de este negocio para su personal de gestión.
     * @param actor propietario o encargado vigente
     * @return equipo histórico local sin datos de cuentas
     * @throws NegocioException si falta el permiso de gestión
     */
    @Transactional(readOnly = true)
    public List<EquipoDto> listar(UsuarioAutenticado actor) {
        exigirGestor(vigente(actor));
        return membresias.findByNegocioIdOrderByIdAsc(idNegocio()).stream()
                .map(m -> new EquipoDto(m.getId(), m.getUsuarioId(), m.getRol(),
                        m.isActiva() ? "ACTIVA" : "REVOCADA", m.getMotivoRevocacion())).toList();
    }

    /**
     * Cambia un rol activo sin modificar credenciales ni reabrir pertenencias revocadas.
     * @param id membresía del negocio actual
     * @param rol permisos solicitados por el propietario
     * @param actor propietario revalidado tras esperar los bloqueos
     * @throws NegocioException si falta permiso, el recurso es ajeno o quedaría sin propietario
     */
    @Transactional
    public void cambiarRol(long id, RolNegocio rol, UsuarioAutenticado actor) {
        bloquear();
        if (vigente(actor).getRol() != RolNegocio.PROPIETARIO) throw prohibido();
        var m = objetivo(id);
        protegerUltimo(m, rol == RolNegocio.PROPIETARIO);
        if (m.getRol() != rol) {
            desactivarPerfil(m);
            m.cambiarRol(rol, clock.instant());
        }
    }

    /**
     * Retira el acceso por decisión del gestor y conserva esa causa al desactivar el perfil.
     * @param id membresía local que se retira
     * @param actor propietario, o encargado sobre BARBERO exclusivamente
     * @throws NegocioException si falta permiso, el recurso es ajeno o es el último propietario
     */
    @Transactional
    public void revocar(long id, UsuarioAutenticado actor) {
        bloquear();
        var gestor = vigente(actor); exigirGestor(gestor);
        var m = objetivo(id);
        if (gestor.getRol() == RolNegocio.ENCARGADO && m.getRol() != RolNegocio.BARBERO) throw prohibido();
        retirar(m, MotivoRevocacion.DECISION_GESTOR);
    }

    /**
     * Permite abandonar exclusivamente la pertenencia del principal autenticado.
     * @param actor miembro que solicita su propia salida
     * @throws NegocioException si ya no pertenece o es el último propietario activo
     */
    @Transactional
    public void salir(UsuarioAutenticado actor) {
        bloquear();
        var propia = vigente(actor);
        usuarios.bloquearPorId(propia.getUsuarioId()).orElseThrow(this::noEncontrado);
        retirar(propia, MotivoRevocacion.SALIDA);
    }

    private void bloquear() {
        membresias.bloquearPropietarios(idNegocio());
        // Siempre antes de ①, incluso si un alta concurrente está creando el perfil.
        barberos.bloquearLimiteActivos(idNegocio());
    }
    private Membresia vigente(UsuarioAutenticado actor) {
        if (actor == null || !usuarios.findById(actor.id()).map(u -> u.isActivo()).orElse(false)) throw prohibido();
        return membresias.findByNegocioIdAndUsuarioId(idNegocio(), actor.id())
                .filter(Membresia::isActiva).orElseThrow(this::prohibido);
    }
    private void exigirGestor(Membresia m) { if (m.getRol() == RolNegocio.BARBERO) throw prohibido(); }
    private Membresia objetivo(long id) {
        var m = membresias.findByNegocioIdAndId(idNegocio(), id).orElseThrow(this::noEncontrado);
        usuarios.bloquearPorId(m.getUsuarioId()).orElseThrow(this::noEncontrado);
        return m;
    }
    private void protegerUltimo(Membresia m, boolean conservaPropiedad) {
        if (!conservaPropiedad && m.isActiva() && m.getRol() == RolNegocio.PROPIETARIO
                && usuarios.findById(m.getUsuarioId()).orElseThrow(this::noEncontrado).isActivo()
                && membresias.contarPropietariosActivos(idNegocio()) <= 1) {
            throw new NegocioException(ErrorCodigo.ULTIMO_PROPIETARIO, "El negocio debe conservar un propietario activo.");
        }
    }
    private void retirar(Membresia m, MotivoRevocacion motivo) {
        protegerUltimo(m, false);
        desactivarPerfil(m);
        m.revocar(motivo, clock.instant());
    }
    private void desactivarPerfil(Membresia m) {
        barberos.buscarIdPorUsuario(m.getUsuarioId()).ifPresent(id ->
                barberos.bloquearPorIds(List.of(id)).forEach(b -> {
                    if (b.isActivo()) b.desactivar(clock.instant());
                }));
    }
    private long idNegocio() { return negocio.negocioActual().orElseThrow(); }
    private NegocioException prohibido() { return new NegocioException(ErrorCodigo.PROHIBIDO, "No tiene permiso para gestionar esta membresía."); }
    private NegocioException noEncontrado() { return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró la membresía."); }
}
