package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.platform.MembresiaRepository;
import pe.barberturno.platform.RolPlataforma;
import pe.barberturno.whatsapp.dto.CanalWhatsAppDto;
import pe.barberturno.whatsapp.dto.RegistrarCanalWhatsAppDto;

/**
 * Alta manual, consulta y desconexión del canal de WhatsApp de un negocio por la plataforma (V2-DA-56).
 * Bloquea primero la fila del negocio, lo que serializa el alta con la suspensión, y audita cada cambio en
 * {@code auditoria_plataforma} dentro de la misma transacción.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class CanalWhatsAppService {
    private final EntityManager entidades;
    private final CanalWhatsAppRepository canales;
    private final MembresiaRepository membresias;
    private final Clock clock;
    private final EjecutorEnNegocio ejecutor;
    private final TransactionTemplate transaccion;

    /**
     * Recibe la persistencia de plataforma y el reloj inyectado.
     * @param entidades bloqueo del negocio y auditoría de plataforma
     * @param canales canales de WhatsApp de superficie mínima
     * @param membresias recuento de propietarios activos del negocio
     * @param clock reloj del servidor
     * @param ejecutor delimita el negocio antes de comprobar conversaciones protegidas por RLS
     * @param gestor gestor que abre la transacción del registro en ese negocio
     */
    public CanalWhatsAppService(EntityManager entidades, CanalWhatsAppRepository canales,
            MembresiaRepository membresias, Clock clock, EjecutorEnNegocio ejecutor,
            PlatformTransactionManager gestor) {
        this.entidades = entidades;
        this.canales = canales;
        this.membresias = membresias;
        this.clock = clock;
        this.ejecutor = ejecutor;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Consulta el canal de un negocio.
     * @param negocioId negocio de la ruta
     * @param actor identidad con rol de plataforma revalidado
     * @return canal del negocio, sin secretos
     * @throws NegocioException PROHIBIDO sin administración de plataforma; NO_ENCONTRADO si no existe el negocio o el canal
     */
    @Transactional(readOnly = true)
    public CanalWhatsAppDto consultar(long negocioId, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        return canales.findByNegocioId(negocioId).map(CanalWhatsAppService::dto).orElseThrow(this::noEncontrado);
    }

    /**
     * Registra el número del negocio, o lo sustituye, y deja el canal activo.
     * @param negocioId negocio de la ruta
     * @param datos identificadores del número en la Cloud API, ya validados
     * @param actor identidad con rol de plataforma revalidado
     * @return canal resultante
     * @throws NegocioException PROHIBIDO sin administración de plataforma; NO_ENCONTRADO si no existe el negocio;
     *         NEGOCIO_SUSPENDIDO si está suspendido; CONFLICTO si no tiene propietario activo o se intenta
     *         sustituir el número de un canal con conversaciones;
     *         NUMERO_WHATSAPP_EN_USO si el número pertenece a otro negocio (también ante una carrera, por el índice único)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CanalWhatsAppDto registrar(long negocioId, RegistrarCanalWhatsAppDto datos, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        return ejecutor.ejecutar(negocioId, () -> transaccion.execute(tx -> registrarEnNegocio(negocioId, datos, actor)));
    }

    private CanalWhatsAppDto registrarEnNegocio(long negocioId, RegistrarCanalWhatsAppDto datos, UsuarioAutenticado actor) {
        String estado = bloquearNegocio(negocioId);
        if ("SUSPENDIDO".equals(estado)) {
            throw new NegocioException(ErrorCodigo.NEGOCIO_SUSPENDIDO, "El negocio está suspendido.");
        }
        if (membresias.contarPropietariosActivos(negocioId) == 0) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "El negocio no tiene propietario activo.");
        }
        canales.findByPhoneNumberId(datos.phoneNumberId()).filter(otro -> otro.negocioId() != negocioId).ifPresent(otro -> {
            throw new NegocioException(ErrorCodigo.NUMERO_WHATSAPP_EN_USO, "El número ya está registrado en otro negocio.");
        });
        var ahora = clock.instant();
        var canal = canales.bloquearPorNegocio(negocioId).orElse(null);
        if (canal != null && !canal.phoneNumberId().equals(datos.phoneNumberId())
                && !entidades.createNativeQuery("SELECT id FROM conversacion WHERE negocio_id = :negocio LIMIT 1", Long.class)
                        .setParameter("negocio", negocioId).getResultList().isEmpty()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO,
                    "El número del canal no se puede sustituir porque el negocio ya tiene conversaciones.");
        }
        // La auditoría va antes del cambio: su INSERT nativo no debe vaciar un UPDATE pendiente fuera del
        // repositorio, donde una carrera por el índice único no se traduciría a NUMERO_WHATSAPP_EN_USO.
        auditar(actor.id(), "REGISTRAR_CANAL_WHATSAPP", negocioId);
        if (canal == null) {
            canal = new CanalWhatsApp(negocioId, datos.wabaId(), datos.phoneNumberId(), datos.numeroVisible(), ahora);
        } else {
            canal.registrar(datos.wabaId(), datos.phoneNumberId(), datos.numeroVisible(), ahora);
        }
        return dto(canales.save(canal));
    }

    /**
     * Desconecta el canal del negocio; repetirlo no cambia nada ni vuelve a auditar.
     * @param negocioId negocio de la ruta
     * @param actor identidad con rol de plataforma revalidado
     * @throws NegocioException PROHIBIDO sin administración de plataforma; NO_ENCONTRADO si no existe el negocio o el canal
     */
    @Transactional
    public void desconectar(long negocioId, UsuarioAutenticado actor) {
        exigirPlataforma(actor);
        bloquearNegocio(negocioId);
        var canal = canales.bloquearPorNegocio(negocioId).orElseThrow(this::noEncontrado);
        if (canal.desconectar(clock.instant())) {
            canales.save(canal);
            auditar(actor.id(), "DESCONECTAR_CANAL_WHATSAPP", negocioId);
        }
    }

    private String bloquearNegocio(long negocioId) {
        List<?> filas = entidades.createNativeQuery("select estado from negocio where id = :id for update")
                .setParameter("id", negocioId).getResultList();
        if (filas.isEmpty()) throw noEncontrado();
        return String.valueOf(filas.getFirst());
    }

    private void auditar(long actor, String accion, long negocioId) {
        entidades.createNativeQuery("""
                insert into auditoria_plataforma(actor_id, accion, negocio_id, objetivo_id, creado_en)
                values (:actor, :accion, :negocio, null, :ahora)
                """).setParameter("actor", actor).setParameter("accion", accion).setParameter("negocio", negocioId)
                .setParameter("ahora", clock.instant()).executeUpdate();
    }

    private static void exigirPlataforma(UsuarioAutenticado actor) {
        if (actor == null || actor.rolPlataforma() != RolPlataforma.ADMIN_PLATAFORMA) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Se requiere administración de plataforma.");
        }
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el recurso de plataforma.");
    }

    private static CanalWhatsAppDto dto(CanalWhatsApp canal) {
        return new CanalWhatsAppDto(canal.negocioId(), canal.wabaId(), canal.phoneNumberId(), canal.numeroVisible(),
                canal.estado(), TiempoNegocio.aLima(canal.actualizadoEn()));
    }
}
