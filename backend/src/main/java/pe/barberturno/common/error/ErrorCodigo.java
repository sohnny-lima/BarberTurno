package pe.barberturno.common.error;

import org.springframework.http.HttpStatus;

/**
 * Catálogo estable de errores de la API (arquitectura §6.2).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum ErrorCodigo {
    VALIDACION(400, "Datos inválidos"),
    JORNADA_INVALIDA(400, "Jornada inválida"),
    INTERVALO_INVALIDO(400, "Intervalo inválido"),
    RANGO_FECHAS_INVALIDO(400, "Rango de fechas inválido"),
    NO_AUTENTICADO(401, "Sesión requerida"),
    CREDENCIALES_INVALIDAS(401, "Credenciales inválidas"),
    CUENTA_BLOQUEADA_TEMPORALMENTE(401, "Cuenta bloqueada temporalmente"),
    PROHIBIDO(403, "Acceso denegado"),
    CAMBIO_PASSWORD_REQUERIDO(403, "Cambio de contraseña requerido"),
    NO_ENCONTRADO(404, "Recurso no encontrado"),
    CORREO_DUPLICADO(409, "Correo ya registrado"),
    NOMBRE_DUPLICADO(409, "Nombre ya registrado"),
    FRANJA_NO_DISPONIBLE(409, "Franja no disponible"),
    CLIENTE_CON_RESERVA_SOLAPADA(409, "Cliente con reserva solapada"),
    CONFLICTO_CON_RESERVAS(409, "Conflicto con reservas"),
    TRANSICION_INVALIDA(409, "Transición inválida"),
    VERSION_DESACTUALIZADA(409, "Versión desactualizada"),
    FUERA_DE_POLITICA(422, "Fuera de la política de cambios"),
    MOTIVO_REQUERIDO(422, "Motivo requerido"),
    FUERA_DE_HORARIO(422, "Fuera del horario"),
    FUERA_DE_HORIZONTE(422, "Fuera del horizonte de reservas"),
    INICIO_EN_PASADO(422, "Inicio en el pasado"),
    RECURSO_INACTIVO(422, "Recurso inactivo"),
    LIMITE_RESERVAS_ACTIVAS(422, "Límite de reservas activas"),
    LIMITE_BARBEROS_ACTIVOS(422, "Límite de barberos activos"),
    FUERA_DE_VENTANA(422, "Fuera de la ventana de atención"),
    CONFLICTO(409, "Conflicto de datos"),
    ERROR_INTERNO(500, "Error interno"),
    RECURSO_OCUPADO(503, "Recurso ocupado");

    private final HttpStatus estado;
    private final String titulo;

    ErrorCodigo(int estado, String titulo) {
        this.estado = HttpStatus.valueOf(estado);
        this.titulo = titulo;
    }

    /** @return estado HTTP del código */
    public HttpStatus estado() { return estado; }

    /** @return título público en español */
    public String titulo() { return titulo; }
}
