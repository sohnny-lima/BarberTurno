package pe.barberturno.common.error;

import org.springframework.http.HttpStatus;

/**
 * Catálogo estable de errores de la API (arquitectura §6.2).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum ErrorCodigo {
    /**
     * Datos inválidos; respuesta HTTP 400 según catálogo §6.2.
     */
    VALIDACION(400, "Datos inválidos"),
    /**
     * Jornada inválida; respuesta HTTP 400 según catálogo §6.2.
     */
    JORNADA_INVALIDA(400, "Jornada inválida"),
    /**
     * Intervalo inválido; respuesta HTTP 400 según catálogo §6.2.
     */
    INTERVALO_INVALIDO(400, "Intervalo inválido"),
    /**
     * Rango de fechas inválido; respuesta HTTP 400 según catálogo §6.2.
     */
    RANGO_FECHAS_INVALIDO(400, "Rango de fechas inválido"),
    /**
     * Sesión requerida; respuesta HTTP 401 según catálogo §6.2.
     */
    NO_AUTENTICADO(401, "Sesión requerida"),
    /**
     * Credenciales inválidas; respuesta HTTP 401 según catálogo §6.2.
     */
    CREDENCIALES_INVALIDAS(401, "Credenciales inválidas"),
    /**
     * Cuenta bloqueada temporalmente; respuesta HTTP 401 según catálogo §6.2.
     */
    CUENTA_BLOQUEADA_TEMPORALMENTE(401, "Cuenta bloqueada temporalmente"),
    /**
     * Acceso denegado; respuesta HTTP 403 según catálogo §6.2.
     */
    PROHIBIDO(403, "Acceso denegado"),
    /**
     * Cambio de contraseña requerido; respuesta HTTP 403 según catálogo §6.2.
     */
    CAMBIO_PASSWORD_REQUERIDO(403, "Cambio de contraseña requerido"),
    /**
     * Recurso no encontrado; respuesta HTTP 404 según catálogo §6.2.
     */
    NO_ENCONTRADO(404, "Recurso no encontrado"),
    /**
     * Correo ya registrado; respuesta HTTP 409 según catálogo §6.2.
     */
    CORREO_DUPLICADO(409, "Correo ya registrado"),
    /**
     * Nombre ya registrado; respuesta HTTP 409 según catálogo §6.2.
     */
    NOMBRE_DUPLICADO(409, "Nombre ya registrado"),
    /**
     * Franja no disponible; respuesta HTTP 409 según catálogo §6.2.
     */
    FRANJA_NO_DISPONIBLE(409, "Franja no disponible"),
    /**
     * Cliente con reserva solapada; respuesta HTTP 409 según catálogo §6.2.
     */
    CLIENTE_CON_RESERVA_SOLAPADA(409, "Cliente con reserva solapada"),
    /**
     * Conflicto con reservas; respuesta HTTP 409 según catálogo §6.2.
     */
    CONFLICTO_CON_RESERVAS(409, "Conflicto con reservas"),
    /**
     * Transición inválida; respuesta HTTP 409 según catálogo §6.2.
     */
    TRANSICION_INVALIDA(409, "Transición inválida"),
    /**
     * Versión desactualizada; respuesta HTTP 409 según catálogo §6.2.
     */
    VERSION_DESACTUALIZADA(409, "Versión desactualizada"),
    /**
     * Fuera de la política de cambios; respuesta HTTP 422 según catálogo §6.2.
     */
    FUERA_DE_POLITICA(422, "Fuera de la política de cambios"),
    /**
     * Motivo requerido; respuesta HTTP 422 según catálogo §6.2.
     */
    MOTIVO_REQUERIDO(422, "Motivo requerido"),
    /**
     * Fuera del horario; respuesta HTTP 422 según catálogo §6.2.
     */
    FUERA_DE_HORARIO(422, "Fuera del horario"),
    /**
     * Fuera del horizonte de reservas; respuesta HTTP 422 según catálogo §6.2.
     */
    FUERA_DE_HORIZONTE(422, "Fuera del horizonte de reservas"),
    /**
     * Inicio en el pasado; respuesta HTTP 422 según catálogo §6.2.
     */
    INICIO_EN_PASADO(422, "Inicio en el pasado"),
    /**
     * Recurso inactivo; respuesta HTTP 422 según catálogo §6.2.
     */
    RECURSO_INACTIVO(422, "Recurso inactivo"),
    /**
     * Límite de reservas activas; respuesta HTTP 422 según catálogo §6.2.
     */
    LIMITE_RESERVAS_ACTIVAS(422, "Límite de reservas activas"),
    /**
     * Límite de barberos activos; respuesta HTTP 422 según catálogo §6.2.
     */
    LIMITE_BARBEROS_ACTIVOS(422, "Límite de barberos activos"),
    /**
     * Fuera de la ventana de atención; respuesta HTTP 422 según catálogo §6.2.
     */
    FUERA_DE_VENTANA(422, "Fuera de la ventana de atención"),
    /**
     * Conflicto de datos; respuesta HTTP 409 según catálogo §6.2.
     */
    CONFLICTO(409, "Conflicto de datos"),
    /**
     * Error interno; respuesta HTTP 500 según catálogo §6.2.
     */
    ERROR_INTERNO(500, "Error interno"),
    /**
     * Recurso ocupado; respuesta HTTP 503 según catálogo §6.2.
     */
    RECURSO_OCUPADO(503, "Recurso ocupado");

    private final HttpStatus estado;
    private final String titulo;

    ErrorCodigo(int estado, String titulo) {
        this.estado = HttpStatus.valueOf(estado);
        this.titulo = titulo;
    }

    /**
     * Estado HTTP estable del código para que MVC y filtros emitan la misma respuesta (§6.2).
     * @return estado HTTP asociado al código según arquitectura §6.2
     */
    public HttpStatus estado() { return estado; }

    /**
     * Título público en español de Problem Details, sin datos de petición ni detalles técnicos.
     * @return título público en español, sin datos de la petición
     */
    public String titulo() { return titulo; }
}
