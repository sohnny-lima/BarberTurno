package pe.barberturno.whatsapp;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda en la cola, en una sola transacción, los eventos de una notificación cuya firma ya se comprobó.
 * No resuelve negocios ni procesa mensajes: eso es asíncrono (V2-DA-15, V2-DA-52), para responder 200 en
 * menos de 2 s (V2-RNF-03).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class RecepcionWebhookService {
    private final JsonMapper mapper;
    private final ExtractorEventosWhatsApp extractor;
    private final EventoEntranteRepository eventos;
    private final Clock clock;

    /**
     * Recibe el mapeador, el extractor, la cola y el reloj inyectado.
     * @param mapper mapeador JSON de la aplicación
     * @param extractor conversión de la notificación en eventos
     * @param eventos inserción idempotente de la cola
     * @param clock reloj del servidor
     */
    public RecepcionWebhookService(JsonMapper mapper, ExtractorEventosWhatsApp extractor,
            EventoEntranteRepository eventos, Clock clock) {
        this.mapper = mapper;
        this.extractor = extractor;
        this.eventos = eventos;
        this.clock = clock;
    }

    /**
     * Inserta cada mensaje y aviso de estado una sola vez por clave.
     * @param cuerpo bytes de la notificación, con firma válida
     * @return número de eventos nuevos; 0 si todos estaban ya en la cola o no había ninguno
     * @throws NegocioException VALIDACION si el cuerpo no es un objeto JSON válido
     */
    @Transactional
    public int recibir(byte[] cuerpo) {
        JsonNode raiz;
        try {
            raiz = mapper.readTree(cuerpo);
        } catch (JacksonException mal) {
            throw new NegocioException(ErrorCodigo.VALIDACION, "La notificación no es un JSON válido.");
        }
        if (raiz == null || !raiz.isObject()) {
            throw new NegocioException(ErrorCodigo.VALIDACION, "La notificación no es un objeto JSON.");
        }
        Instant ahora = clock.instant();
        int nuevos = 0;
        for (var evento : extractor.extraer(raiz)) {
            nuevos += eventos.insertarSiNoExiste(evento.clave(), evento.tipo().name(), evento.phoneNumberId(),
                    evento.cuerpo(), ahora);
        }
        return nuevos;
    }
}
