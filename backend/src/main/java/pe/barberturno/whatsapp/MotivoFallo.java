package pe.barberturno.whatsapp;

/**
 * Motivo codificado de un mensaje saliente {@link EstadoMensaje#FALLIDO}, guardado en
 * {@code mensaje_whatsapp.error_codigo}; nunca contiene datos personales. Cada valor lleva el texto
 * breve que se muestra al personal en el aviso interno (12-v2-09-whatsapp-salida.md §4.1 y §4.5).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum MotivoFallo {
    /** Texto o interactivo con la ventana de 24 h cerrada, o código 131047 de Meta. */
    VENTANA_CERRADA("la ventana de 24 horas de WhatsApp está cerrada"),
    /** El canal del negocio no está activo al enviar. */
    CANAL_DESCONECTADO("el canal de WhatsApp del negocio está desconectado"),
    /** El negocio está suspendido o sin propietario activo al enviar (V2-DA-53). */
    NEGOCIO_NO_OPERATIVO("el negocio no está operativo"),
    /** El mensaje superó la antigüedad máxima antes de poder enviarse. */
    MENSAJE_CADUCADO("el mensaje caducó antes de poder enviarse"),
    /** El destinatario no puede recibirlo (131026, 131021, 131051). */
    DESTINO_NO_VALIDO("el número del cliente no puede recibir el mensaje"),
    /** Plantilla inexistente, no aprobada, pausada o con parámetros rechazados (1320xx). */
    PLANTILLA_RECHAZADA("WhatsApp rechazó la plantilla del mensaje"),
    /** Token ausente, caducado o sin permisos (0, 10, 190, 200, 131005). */
    CREDENCIAL_RECHAZADA("WhatsApp rechazó las credenciales del canal"),
    /** Cuenta o número restringidos, sin pago o sin registrar (368, 1310xx, 133010). */
    CUENTA_RESTRINGIDA("la cuenta de WhatsApp del negocio está restringida"),
    /** Cualquier otro rechazo permanente de la solicitud. */
    SOLICITUD_RECHAZADA("WhatsApp rechazó la solicitud"),
    /** Errores transitorios en todos los intentos permitidos. */
    REINTENTOS_AGOTADOS("WhatsApp no respondió tras varios intentos"),
    /** No se pudo confirmar si Meta aceptó el envío tras los reenvíos permitidos (V2-DA-58). */
    RESULTADO_DESCONOCIDO("no se pudo confirmar si el mensaje llegó a enviarse"),
    /** Aviso de estado {@code failed} de Meta. */
    FALLO_ENTREGA("WhatsApp no pudo entregar el mensaje");

    private final String descripcion;

    MotivoFallo(String descripcion) {
        this.descripcion = descripcion;
    }

    /** {@return texto breve en español, en minúscula inicial y sin datos personales, para el aviso interno} */
    public String descripcion() {
        return descripcion;
    }
}
