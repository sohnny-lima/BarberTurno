package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.users.Usuario;
import pe.barberturno.whatsapp.CanalWhatsAppRepository;
import pe.barberturno.whatsapp.OrigenMensaje;
import pe.barberturno.whatsapp.SalidaWhatsApp;
import pe.barberturno.whatsapp.VentanaWhatsApp;

/**
 * Transiciones atómicas de atención humana con la conversación como primer bloqueo (V2-DA-65/71/76).
 * El retorno programado abre contexto antes de su transacción y limita cada negocio a un lote.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class DerivacionConversacionService implements DerivacionConversacion {
    private static final Logger LOG = LoggerFactory.getLogger(DerivacionConversacionService.class);
    private final EntityManager entidades;
    private final SalidaWhatsApp salida;
    private final TextosConversacion textos;
    private final MenusConversacion menus;
    private final NotificacionService avisos;
    private final VentanaWhatsApp ventana;
    private final CanalWhatsAppRepository canales;
    private final EjecutorEnNegocio ejecutor;
    private final BandejaPropiedades propiedades;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    DerivacionConversacionService(EntityManager entidades, SalidaWhatsApp salida, TextosConversacion textos,
            MenusConversacion menus, NotificacionService avisos, VentanaWhatsApp ventana,
            CanalWhatsAppRepository canales, EjecutorEnNegocio ejecutor, BandejaPropiedades propiedades,
            Clock reloj, PlatformTransactionManager gestor) {
        this.entidades = entidades; this.salida = salida; this.textos = textos; this.menus = menus;
        this.avisos = avisos; this.ventana = ventana; this.canales = canales; this.ejecutor = ejecutor;
        this.propiedades = propiedades; this.reloj = reloj; this.transaccion = new TransactionTemplate(gestor);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean derivar(long conversacionId, MotivoDerivacion motivo) {
        String modo = bloquear(conversacionId);
        if ("HUMANO".equals(modo)) return false;
        if (motivo == null) throw new NegocioException(ErrorCodigo.VALIDACION, "Indique el motivo de derivación.");
        var fichas = entidades.createNativeQuery("SELECT cliente_id FROM conversacion WHERE id=:id", Long.class)
                .setParameter("id", conversacionId).getResultList();
        if (fichas.getFirst() == null) throw new NegocioException(ErrorCodigo.CONFLICTO,
                "Se requiere una ficha con consentimiento para derivar.");
        Instant ahora = reloj.instant();
        entidades.createNativeQuery("""
                UPDATE operacion_pendiente SET estado='ANULADA', actualizado_en=:ahora
                WHERE conversacion_id=:id AND estado='VIGENTE'
                """).setParameter("id", conversacionId).setParameter("ahora", ahora).executeUpdate();
        entidades.createNativeQuery("""
                UPDATE conversacion SET modo='HUMANO', derivada_en=:ahora, motivo_derivacion=:motivo,
                    ultima_respuesta_personal_en=NULL, estado='{}'::jsonb, version=version+1, actualizado_en=:ahora
                WHERE id=:id
                """).setParameter("id", conversacionId).setParameter("motivo", motivo.name())
                .setParameter("ahora", ahora).executeUpdate();
        String nombre = (String) entidades.createNativeQuery("""
                SELECT n.nombre FROM negocio n JOIN conversacion c ON c.negocio_id=n.id WHERE c.id=:id
                """, String.class).setParameter("id", conversacionId).getSingleResult();
        salida.encolarTexto(conversacionId, textos.derivada(nombre), OrigenMensaje.SISTEMA, OptionalLong.empty());
        var destinatarios = entidades.createNativeQuery("""
                SELECT m.usuario_id FROM membresia m JOIN usuario u ON u.id=m.usuario_id
                JOIN conversacion c ON c.negocio_id=m.negocio_id
                WHERE c.id=:id AND m.estado='ACTIVA' AND u.activo AND m.rol IN ('PROPIETARIO','ENCARGADO')
                ORDER BY m.usuario_id
                """, Long.class).setParameter("id", conversacionId).getResultList();
        for (Object id : destinatarios) avisos.notificarDerivacion(
                entidades.getReference(Usuario.class, ((Number) id).longValue()), conversacionId);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean devolver(long conversacionId, OptionalLong usuarioId) {
        if (!"HUMANO".equals(bloquear(conversacionId))) return false;
        Instant entrada = (Instant) entidades.createNativeQuery(
                "SELECT ultima_entrada_en FROM conversacion WHERE id=:id", Instant.class)
                .setParameter("id", conversacionId).getSingleResult();
        entidades.createNativeQuery("""
                UPDATE conversacion SET modo='ASISTENTE', derivada_en=NULL, motivo_derivacion=NULL,
                    ultima_respuesta_personal_en=NULL, estado='{}'::jsonb, version=version+1, actualizado_en=:ahora
                WHERE id=:id
                """).setParameter("id", conversacionId).setParameter("ahora", reloj.instant()).executeUpdate();
        if (ventana.abierta(entrada, reloj.instant())) {
            String texto = textos.devuelta();
            salida.encolarTexto(conversacionId, texto, OrigenMensaje.SISTEMA, OptionalLong.empty());
            salida.encolarInteractivo(conversacionId, menus.menuPrincipal(texto),
                    OrigenMensaje.SISTEMA, OptionalLong.empty());
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int devolverInactivas() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El retorno automático exige invocarse fuera de una transacción.");
        }
        int total = 0;
        for (long negocioId : canales.listarNegociosConCanal()) {
            try {
                total += ejecutor.ejecutar(negocioId, () -> transaccion.execute(tx -> devolverLote()));
            } catch (RuntimeException fallo) {
                LOG.warn("Retorno WhatsApp del negocio {}: fallo; se reintentará en la próxima pasada.", negocioId);
            }
        }
        return total;
    }

    private int devolverLote() {
        Instant limite = reloj.instant().minus(propiedades.retornoAutomatico());
        var ids = entidades.createNativeQuery("""
                SELECT id FROM conversacion WHERE modo='HUMANO'
                    AND COALESCE(ultima_respuesta_personal_en, derivada_en) < :limite
                ORDER BY id LIMIT :lote FOR UPDATE SKIP LOCKED
                """, Long.class).setParameter("limite", limite).setParameter("lote", propiedades.lote()).getResultList();
        int total = 0;
        for (Object id : ids) {
            long valor = ((Number) id).longValue();
            // Revalidación separada después de adquirir el bloqueo, con un snapshot nuevo en READ COMMITTED.
            boolean vencida = !entidades.createNativeQuery("""
                    SELECT id FROM conversacion WHERE id=:id AND modo='HUMANO'
                        AND COALESCE(ultima_respuesta_personal_en, derivada_en) < :limite
                    """, Long.class).setParameter("id", valor).setParameter("limite", limite).getResultList().isEmpty();
            if (vencida && devolver(valor, OptionalLong.empty())) total++;
        }
        return total;
    }

    private String bloquear(long id) {
        var filas = entidades.createNativeQuery("SELECT modo FROM conversacion WHERE id=:id FOR UPDATE", String.class)
                .setParameter("id", id).getResultList();
        if (filas.isEmpty()) throw new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró la conversación.");
        return (String) filas.getFirst();
    }
}
