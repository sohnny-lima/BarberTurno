package pe.barberturno.notifications;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.time.TiempoNegocio;

/**
 * Reserva atómicamente el aviso diario del negocio y lo dirige a su propietario activo.
 * El aviso se confirma junto con la decisión presupuestaria, sin datos personales.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class AvisosLimiteIA {
    private final EntityManager em;
    private final Clock reloj;

    AvisosLimiteIA(EntityManager em, Clock reloj) { this.em = em; this.reloj = reloj; }

    /**
     * Emite como máximo un aviso al día por negocio, ligado a la conversación que alcanzó el límite.
     * @param conversacionId conversación del negocio actual, bajo su bloqueo
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void presupuesto(long conversacionId) {
        var dia = TiempoNegocio.fechaLima(reloj.instant());
        int reservado = em.createNativeQuery("""
                UPDATE negocio SET ia_aviso_limite_en=:dia
                WHERE id=app_negocio_actual() AND ia_aviso_limite_en IS DISTINCT FROM :dia
                """).setParameter("dia", dia).executeUpdate();
        if (reservado == 0) return;
        em.createNativeQuery("""
                INSERT INTO notificacion (usuario_id, conversacion_id, tipo, mensaje, leida, creado_en)
                SELECT m.usuario_id, :conversacion, 'LIMITE_IA',
                    'Se alcanzó el presupuesto mensual de IA. Las conversaciones continúan con el menú.', false, :ahora
                FROM membresia m JOIN usuario u ON u.id=m.usuario_id
                WHERE m.negocio_id=app_negocio_actual() AND m.rol='PROPIETARIO' AND m.estado='ACTIVA' AND u.activo
                ORDER BY m.usuario_id LIMIT 1
                """).setParameter("conversacion", conversacionId).setParameter("ahora", reloj.instant()).executeUpdate();
    }
}
