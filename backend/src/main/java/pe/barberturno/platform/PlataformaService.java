package pe.barberturno.platform;

import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.platform.dto.*;
import pe.barberturno.users.UsuarioRepository;
import pe.barberturno.users.dto.PasswordTemporalDto;

/**
 * Operaciones globales autorizadas y auditadas en la misma transacción, sin contexto ni datos de negocio.
 * El acceso SQL de auditoría tiene solo escritura privada acotada a actor y objetivo (V2-DA-26).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class PlataformaService {
    private final EntityManager entidades;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final InvitacionService invitaciones;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Recibe persistencia global, codificador de credenciales y reloj del servidor.
     * @param entidades sesión con guarda transaccional V2-DA-23
     * @param usuarios identidades con bloqueo pesimista por cuenta
     * @param passwords BCrypt de la aplicación
     * @param clock fuente única de instantes
     * @param invitaciones emisión del primer propietario dentro de esta transacción
     */
    public PlataformaService(EntityManager entidades, UsuarioRepository usuarios, PasswordEncoder passwords, Clock clock, InvitacionService invitaciones) {
        this.entidades = entidades; this.usuarios = usuarios; this.passwords = passwords; this.clock = clock;
        this.invitaciones = invitaciones;
    }

    /**
     * Crea negocio, invitación del propietario y auditoría en una misma transacción.
     * @param datos nombre, slug validado y correo del primer propietario
     * @param actor identidad con rol global revalidado
     * @return negocio sin miembros con enlace entregado una sola vez
     * @throws NegocioException si no es plataforma o el slug está ocupado
     */
    @Transactional
    public NegocioCreadoDto crear(CrearNegocioDto datos, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        var filas = entidades.unwrap(org.hibernate.Session.class).createNativeQuery("""
                insert into negocio(slug,nombre,creado_en,actualizado_en) values (:slug,:nombre,:ahora,:ahora)
                on conflict (slug) do nothing returning id
                """, Long.class).setParameter("slug", datos.slug()).setParameter("nombre", datos.nombre())
                .setParameter("ahora", clock.instant()).getResultList();
        if (filas.isEmpty()) throw new NegocioException(ErrorCodigo.SLUG_DUPLICADO, "El slug ya está registrado.");
        long id = ((Number) filas.getFirst()).longValue();
        auditar(actor.id(), "CREAR_NEGOCIO", id, null);
        var emitida = invitaciones.emitir(id, datos.correoPropietario(), RolNegocio.PROPIETARIO, actor.id());
        return new NegocioCreadoDto(id, datos.slug(), datos.nombre(), EstadoNegocio.ACTIVO, 0, emitida.enlaceInvitacion());
    }

    /**
     * Lista únicamente metadatos globales y recuentos; audita también esta consulta de plataforma.
     * @param actor identidad con rol global revalidado
     * @return negocios ordenados por id, sin datos personales ni operativos
     * @throws NegocioException si el actor no administra la plataforma
     */
    @Transactional
    public List<NegocioPlataformaDto> listar(UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        var filas = entidades.unwrap(org.hibernate.Session.class).createNativeQuery("""
                select n.id,n.slug,n.nombre,n.estado,
                    (select count(*) from membresia m join usuario u on u.id=m.usuario_id
                     where m.negocio_id=n.id and m.estado='ACTIVA' and u.activo)
                from negocio n order by n.id
                """, Object[].class).getResultList();
        auditar(actor.id(), "LISTAR_NEGOCIOS", null, null);
        return filas.stream().map(f -> new NegocioPlataformaDto(((Number) f[0]).longValue(), (String) f[1],
                (String) f[2], EstadoNegocio.valueOf((String) f[3]), ((Number) f[4]).longValue())).toList();
    }

    /**
     * Serializa el cambio de disponibilidad en la fila global; no modifica recursos ni membresías.
     * @param id negocio destinatario
     * @param estado disponibilidad solicitada
     * @param actor identidad con rol global revalidado
     * @throws NegocioException si no hay permiso o no existe el negocio
     */
    @Transactional
    public void cambiarEstado(long id, EstadoNegocio estado, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        int cambios = entidades.createNativeQuery("update negocio set estado=:estado,actualizado_en=:ahora where id=:id")
                .setParameter("estado", estado.name()).setParameter("ahora", clock.instant()).setParameter("id", id).executeUpdate();
        if (cambios == 0) throw noEncontrado();
        auditar(actor.id(), estado == EstadoNegocio.SUSPENDIDO ? "SUSPENDER_NEGOCIO" : "REACTIVAR_NEGOCIO", id, null);
    }

    /**
     * Restablece cualquier cuenta ajena, incluidas compartidas y de plataforma, bajo bloqueo de usuario.
     * Guarda solo BCrypt, exige cambio y revoca sesiones; no toca pertenencias ni fichas.
     * @param id cuenta destinataria
     * @param actor administrador global distinto del destinatario
     * @return temporal mostrada solo en esta respuesta
     * @throws NegocioException si no hay permiso, se intenta a sí mismo o no existe la cuenta
     */
    @Transactional
    public PasswordTemporalDto restablecer(long id, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        if (id == actor.id()) throw new NegocioException(ErrorCodigo.PROHIBIDO, "Cambie su contraseña desde su perfil.");
        var cuenta = usuarios.bloquearPorId(id).orElseThrow(this::noEncontrado);
        String temporal = generarTemporal();
        cuenta.cambiarPassword(passwords.encode(temporal), true, clock.instant());
        cuenta.registrarAccesoCorrecto(clock.instant());
        usuarios.saveAndFlush(cuenta);
        auditar(actor.id(), "RESTABLECER_PASSWORD", null, id);
        return new PasswordTemporalDto(temporal);
    }

    /**
     * Invalida la invitación inicial pendiente y entrega una capacidad nueva una sola vez.
     * @param id negocio destinatario
     * @param actor administrador global autenticado
     * @return emisión nueva con enlace secreto, nunca auditado en claro
     * @throws NegocioException si falta permiso, invitación inicial o el negocio está suspendido
     */
    @Transactional
    public InvitacionEmitidaDto reenviarInicial(long id, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        var emitida = invitaciones.gestionarInicial(id, actor.id(), true);
        auditar(actor.id(), "REENVIAR_INVITACION_PROPIETARIO", id, null);
        return emitida;
    }

    /**
     * Retira la capacidad inicial del propietario con auditoría en la misma transacción.
     * @param id negocio destinatario
     * @param actor administrador global autenticado
     * @throws NegocioException si falta permiso o la invitación inicial no existe
     */
    @Transactional
    public void revocarInicial(long id, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        invitaciones.gestionarInicial(id, actor.id(), false);
        auditar(actor.id(), "REVOCAR_INVITACION_PROPIETARIO", id, null);
    }

    private void exigirPlataforma(UsuarioAutenticado actor) {
        if (actor == null || actor.rolPlataforma() != RolPlataforma.ADMIN_PLATAFORMA) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Se requiere administración de plataforma.");
        }
    }

    private String generarTemporal() {
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
        return new String(temporal);
    }

    private void auditar(long actor, String accion, Long negocio, Long objetivo) {
        entidades.createNativeQuery("""
                insert into auditoria_plataforma(actor_id,accion,negocio_id,objetivo_id,creado_en)
                values (:actor,:accion,:negocio,:objetivo,:ahora)
                """).setParameter("actor", actor).setParameter("accion", accion).setParameter("negocio", negocio)
                .setParameter("objetivo", objetivo).setParameter("ahora", clock.instant()).executeUpdate();
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el recurso de plataforma.");
    }
}
