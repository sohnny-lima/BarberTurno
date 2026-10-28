package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.audit.AccionAuditoria;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.platform.MembresiaRepository;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.users.Usuario;

/**
 * Genera avisos internos por transición a fallo, atómicos con el mensaje y sin datos del cliente (§4.5).
 * Consulta el profesional actualmente asignado y elimina destinatarios repetidos.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class AvisoFalloWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(AvisoFalloWhatsApp.class);
    private final EntityManager entidades;
    private final MembresiaRepository membresias;
    private final NotificacionService notificaciones;

    AvisoFalloWhatsApp(EntityManager entidades, MembresiaRepository membresias, NotificacionService notificaciones) {
        this.entidades = entidades;
        this.membresias = membresias;
        this.notificaciones = notificaciones;
    }

    void avisar(MensajeWhatsApp anterior, MotivoFallo motivo) {
        if (anterior.estado() == EstadoMensaje.FALLIDO && anterior.errorCodigo() == motivo) return;
        LOG.warn("Mensaje WhatsApp {}: {}.", anterior.id(), motivo);
        if (anterior.reservaId() == null) return;
        var reserva = entidades.find(Reserva.class, anterior.reservaId());
        String codigo = "BT-" + anterior.reservaId();
        String texto = motivo == MotivoFallo.RESULTADO_DESCONOCIDO
                ? "No se pudo confirmar si se envió por WhatsApp el mensaje de la reserva " + codigo
                    + ". Compruébelo con el cliente."
                : "No se pudo enviar por WhatsApp el mensaje de la reserva " + codigo + ": "
                    + motivo.descripcion() + ". Contacte al cliente por otro medio.";
        for (Long usuarioId : membresias.destinatariosFalloWhatsApp(anterior.negocioId(), anterior.reservaId())) {
            notificaciones.notificar(entidades.getReference(Usuario.class, usuarioId), reserva,
                    AccionAuditoria.WHATSAPP, texto);
        }
    }
}
