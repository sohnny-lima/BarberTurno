package pe.barberturno.whatsapp;

import tools.jackson.databind.node.ObjectNode;

/**
 * Puerto hacia el envío de mensajes de la Graph API oficial de WhatsApp Cloud
 * ({@code POST /{versión}/{phone_number_id}/messages}, V2-DA-09). Nunca se invoca dentro de una
 * transacción: el enviador lo llama sin bloqueos ni conexiones de base de datos retenidas.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ClienteGraphWhatsApp {
    /**
     * Envía un mensaje y clasifica la respuesta sin lanzar excepciones por errores de red o de Meta.
     * @param phoneNumberId identificador del número emisor del negocio (solo dígitos)
     * @param cuerpo JSON completo de la petición ({@code messaging_product}, {@code to}, {@code type}…)
     * @return resultado clasificado según 12-v2-09-whatsapp-salida.md §4.1 y §4.2
     */
    ResultadoEnvio enviar(String phoneNumberId, ObjectNode cuerpo);
}
