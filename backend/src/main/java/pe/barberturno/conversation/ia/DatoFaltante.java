package pe.barberturno.conversation.ia;

/**
 * Dato que falta para avanzar, junto con {@link IntencionRespuesta#PEDIR_DATO} (17-v2-12 §13.1). El servidor lo
 * degrada al primer dato realmente ausente (§13.2.3) y pide el dato con la lista determinista de V2-10.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum DatoFaltante {
    /** Servicio del catálogo. */
    SERVICIO,
    /** Profesional (o «cualquiera»). */
    PROFESIONAL,
    /** Día de la cita. */
    DIA,
    /** Cita de la ficha sobre la que actuar. */
    CITA
}
