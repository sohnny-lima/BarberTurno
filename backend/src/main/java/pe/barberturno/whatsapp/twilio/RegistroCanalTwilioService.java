package pe.barberturno.whatsapp.twilio;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.whatsapp.CanalWhatsApp;
import pe.barberturno.whatsapp.CanalWhatsAppRepository;

/**
 * Registra idempotentemente el canal de prueba con el repositorio existente (V2-DA-102).
 * Bloquea primero el negocio, como el alta manual, y nunca sustituye otro número de un negocio.
 * No inventa una identidad de plataforma ni genera una auditoría atribuida a una persona.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
@Profile("twilio")
public class RegistroCanalTwilioService {
    private final EntityManager entidades;
    private final CanalWhatsAppRepository canales;
    private final Clock reloj;
    private final TwilioPropiedades propiedades;

    /**
     * Recibe la persistencia de plataforma; el registro no accede a tablas protegidas por RLS.
     * @param entidades bloqueo de la fila del negocio
     * @param canales repositorio existente de canales
     * @param reloj fecha del alta o reactivación
     * @param propiedades pares configurados de número y negocio, con compatibilidad del par histórico
     */
    public RegistroCanalTwilioService(EntityManager entidades, CanalWhatsAppRepository canales,
            Clock reloj, TwilioPropiedades propiedades) {
        this.entidades = entidades; this.canales = canales;
        this.reloj = reloj; this.propiedades = propiedades;
    }

    /**
     * Registra todos los pares configurados en una transacción: crea, reactiva o conserva cada canal activo.
     * Usa los dígitos de cada número como phone_number_id y como waba_id sintético, sin guardar AccountSid.
     * La operatividad del negocio y el propietario se revalidan al procesar entradas y salidas;
     * este alta de metadatos no concede permisos ni crea propietarios.
     * @throws IllegalStateException si falta el negocio o existe un canal incompatible
     */
    @Transactional
    public void registrar() {
        for (var canal : propiedades.canalesEfectivos()) registrar(canal);
    }

    private void registrar(TwilioPropiedades.Canal configuracion) {
        var negocios = entidades.createNativeQuery("select id from negocio where slug = :slug for update", Long.class)
                .setParameter("slug", configuracion.negocioSlug()).getResultList();
        if (negocios.isEmpty()) throw new IllegalStateException("Falta el negocio de prueba para Twilio Sandbox.");
        long negocioId = ((Number) negocios.getFirst()).longValue();
        String numero = configuracion.numero().substring(1);
        canales.findByPhoneNumberId(numero).filter(canal -> canal.negocioId() != negocioId).ifPresent(canal -> {
            throw new IllegalStateException("El canal de Twilio Sandbox ya pertenece a otro negocio.");
        });
        var canal = canales.bloquearPorNegocio(negocioId).orElse(null);
        if (canal != null && !numero.equals(canal.phoneNumberId())) {
            throw new IllegalStateException("El negocio de prueba ya tiene un canal distinto de Twilio Sandbox.");
        }
        if (canal != null && canal.activo()) return;
        if (canal == null) {
            canal = new CanalWhatsApp(negocioId, numero, numero, configuracion.numero(), reloj.instant());
        } else {
            canal.registrar(numero, numero, configuracion.numero(), reloj.instant());
        }
        canales.save(canal);
    }
}
