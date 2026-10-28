package pe.barberturno.whatsapp;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ExtractorEventosWhatsAppTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final ExtractorEventosWhatsApp extractor = new ExtractorEventosWhatsApp(mapper);

    JsonNode ejemplo() throws Exception {
        try (var entrada = getClass().getResourceAsStream("/whatsapp/mensajes-y-estados.json")) {
            return mapper.readTree(entrada);
        }
    }

    @Test void cuerpoOficial_unEventoPorMensajeYPorAvisoDeEstado() throws Exception {
        var eventos = extractor.extraer(ejemplo());
        assertThat(eventos).extracting(ExtractorEventosWhatsApp.EventoNuevo::clave).containsExactly(
                "m:wamid.FICTICIO-MENSAJE-001", "m:wamid.FICTICIO-MENSAJE-002",
                "s:wamid.FICTICIO-SALIENTE-001:delivered", "s:wamid.FICTICIO-SALIENTE-001:read");
        assertThat(eventos).extracting(ExtractorEventosWhatsApp.EventoNuevo::tipo)
                .containsExactly(TipoEvento.MENSAJE, TipoEvento.MENSAJE, TipoEvento.ESTADO, TipoEvento.ESTADO);
        assertThat(eventos).extracting(ExtractorEventosWhatsApp.EventoNuevo::phoneNumberId).containsOnly("100000000000001");
    }

    @Test void cuerpoDelEvento_numeroContactoDelRemitenteYDatoOriginal() throws Exception {
        var eventos = extractor.extraer(ejemplo());
        JsonNode mensaje = mapper.readTree(eventos.get(1).cuerpo());
        assertThat(mensaje.get("phoneNumberId").asString()).isEqualTo("100000000000001");
        assertThat(mensaje.get("contacto").get("waId").asString()).isEqualTo("51900000002");
        assertThat(mensaje.get("contacto").get("nombre").asString()).isEqualTo("Otra Persona Ficticia");
        assertThat(mensaje.get("dato").get("text").get("body").asString()).isEqualTo("Buenos días");
        JsonNode estado = mapper.readTree(eventos.get(2).cuerpo());
        assertThat(estado.get("contacto").isNull()).isTrue();
        assertThat(estado.get("dato").get("recipient_id").asString()).isEqualTo("51900000001");
    }

    @Test void remitenteSinContacto_contactoNulo() throws Exception {
        var raiz = (tools.jackson.databind.node.ObjectNode) ejemplo();
        var valor = (tools.jackson.databind.node.ObjectNode) raiz.get("entry").get(0).get("changes").get(0).get("value");
        valor.remove("contacts");
        assertThat(mapper.readTree(extractor.extraer(raiz).getFirst().cuerpo()).get("contacto").isNull()).isTrue();
    }

    @Test void formatosNoAdmitidos_seIgnoranSinEventos() throws Exception {
        for (String json : List.of(
                "{\"object\":\"page\",\"entry\":[]}",
                "{\"entry\":[]}",
                "{\"object\":\"whatsapp_business_account\"}",
                "{\"object\":\"whatsapp_business_account\",\"entry\":{}}",
                "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"account_update\",\"value\":{}}]}]}",
                "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"otro\",\"metadata\":{\"phone_number_id\":\"1\"},\"messages\":[{\"id\":\"x\"}]}}]}]}",
                "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"12a\"},\"messages\":[{\"id\":\"x\"}]}}]}]}",
                "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"1\"},\"messages\":[{\"from\":\"5\"},{\"id\":7},{\"id\":\"" + "a".repeat(159) + "\"}],\"statuses\":[{\"id\":\"s\"}],\"errors\":[{\"code\":1}]}}]}]}")) {
            assertThat(extractor.extraer(mapper.readTree(json))).as(json).isEmpty();
        }
    }

    @Test void claveDeIdempotencia_limiteCientoSesentaCaracteresEnLosExtremos() throws Exception {
        // Longitud 160 exacta ("m:" + 158 caracteres = 160) -> admitido
        String json160 = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"1\"},\"messages\":[{\"id\":\"" + "x".repeat(158) + "\"}]}}]}]}";
        var eventos160 = extractor.extraer(mapper.readTree(json160));
        assertThat(eventos160).hasSize(1);
        assertThat(eventos160.getFirst().clave()).hasSize(160);

        // Longitud 161 ("m:" + 159 caracteres = 161) -> ignorado
        String json161 = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"1\"},\"messages\":[{\"id\":\"" + "x".repeat(159) + "\"}]}}]}]}";
        assertThat(extractor.extraer(mapper.readTree(json161))).isEmpty();

        // Wamid de 128 caracteres con status 'delivered': "s:" + 128 + ":delivered" = 140 caracteres -> admitido
        String wamid128 = "w".repeat(128);
        String jsonEstado = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"1\"},\"statuses\":[{\"id\":\"" + wamid128 + "\",\"status\":\"delivered\"}]}}]}]}";
        var eventosEstado = extractor.extraer(mapper.readTree(jsonEstado));
        assertThat(eventosEstado).hasSize(1);
        assertThat(eventosEstado.getFirst().clave()).isEqualTo("s:" + wamid128 + ":delivered").hasSize(140);
    }
    }
}
