package pe.barberturno.whatsapp.dto;

import java.time.OffsetDateTime;
import pe.barberturno.conversation.MotivoDerivacion;

/**
 * Conversación derivada tal como la ve el personal en la bandeja (V2-11, 16-v2-11 §6). Nunca incluye el
 * {@code wa_id} completo. Instantes en la zona de Lima.
 * @param id identidad de la conversación
 * @param cliente nombre de la ficha
 * @param telefonoEnmascarado número enmascarado (solo los 3 últimos dígitos; todo oculto si tiene 4 o menos)
 * @param motivo por qué se derivó
 * @param derivadaEn cuándo se derivó
 * @param ultimaEntradaEn último mensaje del cliente, o nulo
 * @param ventanaAbiertaHasta vencimiento nominal de la ventana de 24 h, o nulo sin entradas
 * @param pendienteDeRespuesta el cliente escribió después de la última respuesta del personal
 * @param puedeResponder el servidor admitiría ahora una respuesta (ventana efectiva y negocio operativo)
 * @param motivoNoResponder por qué no se puede responder, o nulo si se puede
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ConversacionBandejaDto(long id, String cliente, String telefonoEnmascarado, MotivoDerivacion motivo,
        OffsetDateTime derivadaEn, OffsetDateTime ultimaEntradaEn, OffsetDateTime ventanaAbiertaHasta,
        boolean pendienteDeRespuesta, boolean puedeResponder, MotivoNoResponder motivoNoResponder) {

    /** Por qué el personal no puede responder ahora. */
    public enum MotivoNoResponder {
        /** La ventana efectiva de 24 h (con el margen del enviador) está cerrada. */
        FUERA_DE_VENTANA,
        /** El canal no está activo o el negocio está suspendido o sin propietario. */
        NEGOCIO_NO_OPERATIVO
    }
}
