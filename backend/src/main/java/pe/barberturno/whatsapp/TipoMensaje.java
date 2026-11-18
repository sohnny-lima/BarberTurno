package pe.barberturno.whatsapp;

/**
 * Forma de un mensaje de WhatsApp: lo que se pidió encolar ({@code tipo}) o lo que se envió de verdad
 * ({@code enviado_como}); solo una plantilla aprobada sale fuera de la ventana de 24 h (V2-RN-13).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum TipoMensaje {
    /** Texto libre; exige la ventana de atención abierta. */
    TEXTO,
    /** Botones o lista; exige la ventana de atención abierta. */
    INTERACTIVO,
    /** Plantilla <em>utility</em> aprobada del catálogo {@link PlantillaUtility}. */
    PLANTILLA
}
