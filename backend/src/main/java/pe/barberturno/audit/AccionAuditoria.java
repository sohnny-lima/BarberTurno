package pe.barberturno.audit;

/**
 * Acciones registradas en la auditoría y los avisos.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum AccionAuditoria {
    /**
     * Creación de reserva auditada en la misma transacción RN-15.
     */
    CREAR,
    /**
     * Cambio de franja conservando servicio y referencias RN-09.
     */
    REPROGRAMAR,
    /**
     * Cancelación que libera disponibilidad y conserva historial RN-14.
     */
    CANCELAR,
    /**
     * Confirmación de reserva pendiente por personal autorizado.
     */
    CONFIRMAR,
    /**
     * Inicio de atención dentro de la ventana RN-12.
     */
    INICIAR,
    /**
     * Fin de una atención ya iniciada RN-10.
     */
    COMPLETAR,
    /**
     * Inasistencia registrada desde el inicio de reserva RN-12.
     */
    NO_ASISTIO
    }
