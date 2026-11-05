package pe.barberturno.conversation.ia;

/**
 * Herramientas que el modelo puede pedir (17-v2-12 §4.2; 04-arquitectura §7.2). Las define y ejecuta el servidor:
 * solo leen o <strong>proponen</strong>; ninguna escribe reservas ni acepta ids internos, negocio o cliente. Los
 * esquemas son estrictos ({@code additionalProperties: false}, todos los campos en {@code required}; los opcionales
 * admiten {@code null}).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum HerramientaIA {
    /** {@code listar_servicios}: Servicios activos de la barbería: alias, nombre, duración en minutos y precio en soles. */
    LISTAR_SERVICIOS("listar_servicios",
            "Servicios activos de la barbería: alias, nombre, duración en minutos y precio en soles.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}"),
    /** {@code listar_profesionales}: Profesionales activos de la barbería: alias y nombre. */
    LISTAR_PROFESIONALES("listar_profesionales",
            "Profesionales activos de la barbería: alias y nombre.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}"),
    /** {@code buscar_disponibilidad}: Franjas libres reales para un servicio (o para mover una cita), de hasta 7 días y opcionalmente entre dos horas. Es la única fuente de horarios. */
    BUSCAR_DISPONIBILIDAD("buscar_disponibilidad",
            "Franjas libres reales para un servicio (o para mover una cita), de hasta 7 días y opcionalmente entre dos horas. Es la única fuente de horarios.",
            "{\"type\":\"object\",\"properties\":{\"servicio\":{\"type\":[\"string\",\"null\"],\"description\":\"Alias del servicio (p. ej. s1). Nulo solo si se indica cita.\"},\"profesional\":{\"type\":[\"string\",\"null\"],\"description\":\"Alias del profesional, o nulo para cualquiera.\"},\"desde\":{\"type\":\"string\",\"description\":\"Primer día, yyyy-MM-dd (desde hoy).\"},\"hasta\":{\"type\":\"string\",\"description\":\"Último día, yyyy-MM-dd (como máximo 7 días después de desde).\"},\"horaDesde\":{\"type\":[\"string\",\"null\"],\"description\":\"Hora mínima de inicio HH:mm en Lima, inclusiva, o nulo.\"},\"horaHasta\":{\"type\":[\"string\",\"null\"],\"description\":\"Hora máxima de inicio HH:mm en Lima, inclusiva, o nulo.\"},\"cita\":{\"type\":[\"string\",\"null\"],\"description\":\"Alias de una cita para buscar franjas donde moverla (mismo profesional y duración), o nulo.\"}},\"required\":[\"servicio\",\"profesional\",\"desde\",\"hasta\",\"horaDesde\",\"horaHasta\",\"cita\"],\"additionalProperties\":false}"),
    /** {@code mis_citas}: Citas futuras del cliente con lo que puede hacer con cada una (cancelar, reprogramar). */
    MIS_CITAS("mis_citas",
            "Citas futuras del cliente con lo que puede hacer con cada una (cancelar, reprogramar).",
            "{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}"),
    /** {@code proponer_reserva}: Prepara una reserva para una franja devuelta en este turno. No reserva: el servidor envía el resumen y el cliente confirma con un botón. */
    PROPONER_RESERVA("proponer_reserva",
            "Prepara una reserva para una franja devuelta en este turno. No reserva: el servidor envía el resumen y el cliente confirma con un botón.",
            "{\"type\":\"object\",\"properties\":{\"franja\":{\"type\":\"string\",\"description\":\"Alias de una franja devuelta en este turno para reservar.\"}},\"required\":[\"franja\"],\"additionalProperties\":false}"),
    /** {@code proponer_reprogramacion}: Prepara mover una cita a una franja devuelta para esa cita. No cambia nada hasta que el cliente confirma. */
    PROPONER_REPROGRAMACION("proponer_reprogramacion",
            "Prepara mover una cita a una franja devuelta para esa cita. No cambia nada hasta que el cliente confirma.",
            "{\"type\":\"object\",\"properties\":{\"cita\":{\"type\":\"string\",\"description\":\"Alias de la cita.\"},\"franja\":{\"type\":\"string\",\"description\":\"Alias de una franja para mover esa cita.\"}},\"required\":[\"cita\",\"franja\"],\"additionalProperties\":false}"),
    /** {@code proponer_cancelacion}: Prepara cancelar una cita. No cancela nada hasta que el cliente confirma. */
    PROPONER_CANCELACION("proponer_cancelacion",
            "Prepara cancelar una cita. No cancela nada hasta que el cliente confirma.",
            "{\"type\":\"object\",\"properties\":{\"cita\":{\"type\":\"string\",\"description\":\"Alias de la cita.\"}},\"required\":[\"cita\"],\"additionalProperties\":false}"),
    /** {@code derivar_a_persona}: Pasa la conversación al equipo de la barbería: queja, petición de una persona, tema ajeno a las reservas o falta de avance. */
    DERIVAR_A_PERSONA("derivar_a_persona",
            "Pasa la conversación al equipo de la barbería: queja, petición de una persona, tema ajeno a las reservas o falta de avance.",
            "{\"type\":\"object\",\"properties\":{\"motivo\":{\"type\":\"string\",\"enum\":[\"QUEJA\",\"PETICION\",\"FUERA_DE_ALCANCE\",\"SIN_AVANCE\"],\"description\":\"Por qué se pasa con el equipo.\"}},\"required\":[\"motivo\"],\"additionalProperties\":false}");

    private final String nombre;
    private final String descripcion;
    private final String esquemaJson;

    HerramientaIA(String nombre, String descripcion, String esquemaJson) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.esquemaJson = esquemaJson;
    }

    /**
     * Nombre con el que la ve el modelo.
     * @return nombre de API
     */
    public String nombre() {
        return nombre;
    }

    /**
     * Descripción para el modelo.
     * @return descripción
     */
    public String descripcion() {
        return descripcion;
    }

    /**
     * Esquema JSON estricto de los argumentos.
     * @return esquema en JSON
     */
    public String esquemaJson() {
        return esquemaJson;
    }

    /**
     * Busca una herramienta por su nombre de API.
     * @param nombre nombre recibido del modelo
     * @return la herramienta
     * @throws IllegalArgumentException si no existe (salida inválida del modelo)
     */
    public static HerramientaIA porNombre(String nombre) {
        for (var herramienta : values()) {
            if (herramienta.nombre.equals(nombre)) return herramienta;
        }
        throw new IllegalArgumentException("Herramienta desconocida.");
    }
}
