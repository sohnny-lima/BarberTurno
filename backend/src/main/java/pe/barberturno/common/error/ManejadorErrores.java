package pe.barberturno.common.error;

import java.net.URI;
import java.sql.SQLException;
import org.postgresql.util.PSQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce errores de MVC y servicios a RFC 9457 sin revelar detalles técnicos.
 * Los filtros anteriores a MVC requieren sus propios manejadores en T-10.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestControllerAdvice
public class ManejadorErrores {
    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    /**
     * @param excepcion rechazo con contenido público
     * @param peticion petición actual
     * @return problema con las extensiones del dominio
     */
    @ExceptionHandler(NegocioException.class)
    public ProblemDetail negocio(NegocioException excepcion, HttpServletRequest peticion) {
        ProblemDetail problema = problema(excepcion.codigo(), excepcion.getMessage(), peticion);
        excepcion.detalles().forEach(problema::setProperty);
        if (excepcion.codigo() == ErrorCodigo.VALIDACION) {
            problema.setProperty("errores", excepcion.errores());
        }
        return problema;
    }

    /**
     * @param excepcion validación del cuerpo
     * @param peticion petición actual
     * @return campos inválidos sin valores rechazados
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail cuerpoInvalido(MethodArgumentNotValidException excepcion, HttpServletRequest peticion) {
        List<ErrorCampo> errores = new ArrayList<>();
        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                errores.add(new ErrorCampo(error.getField(), mensaje(error.getDefaultMessage()))));
        excepcion.getBindingResult().getGlobalErrors().forEach(error ->
                errores.add(new ErrorCampo("solicitud", mensaje(error.getDefaultMessage()))));
        return validacion(errores, peticion);
    }

    /**
     * @param excepcion validación de parámetros de MVC
     * @param peticion petición actual
     * @return campos y mensajes de validación
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail parametrosInvalidos(HandlerMethodValidationException excepcion, HttpServletRequest peticion) {
        List<ErrorCampo> errores = new ArrayList<>();
        excepcion.getParameterValidationResults().forEach(resultado -> {
            String campo = resultado.getMethodParameter().getParameterName();
            resultado.getResolvableErrors().forEach(error -> errores.add(
                    new ErrorCampo(campo == null ? "solicitud" : campo, mensaje(error.getDefaultMessage()))));
        });
        excepcion.getCrossParameterValidationResults().forEach(error ->
                errores.add(new ErrorCampo("solicitud", mensaje(error.getDefaultMessage()))));
        return validacion(errores, peticion);
    }

    /**
     * @param excepcion validación de métodos con Bean Validation
     * @param peticion petición actual
     * @return campos inválidos sin valores ni objetos de dominio
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail restriccionesInvalidas(ConstraintViolationException excepcion, HttpServletRequest peticion) {
        List<ErrorCampo> errores = excepcion.getConstraintViolations().stream()
                .map(error -> new ErrorCampo(error.getPropertyPath().toString(), mensaje(error.getMessage())))
                .sorted(java.util.Comparator.comparing(ErrorCampo::campo).thenComparing(ErrorCampo::mensaje)).toList();
        return validacion(errores, peticion);
    }

    /**
     * @param peticion petición con formato o tipo incorrecto
     * @return mensaje genérico sin información del parser
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class})
    public ProblemDetail formatoInvalido(HttpServletRequest peticion) {
        return validacion(List.of(), peticion);
    }

    /**
     * @param peticion petición con versión desactualizada
     * @return conflicto que permite recargar la información
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail versionDesactualizada(HttpServletRequest peticion) {
        return problema(ErrorCodigo.VERSION_DESACTUALIZADA, "La información cambió. Recargue e intente de nuevo.", peticion);
    }

    /**
     * @param excepcion excepción de Spring y cadena de causas JDBC
     * @param peticion petición actual
     * @return traducción por SQLState y restricción exacta, nunca por mensaje
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail accesoDatos(DataAccessException excepcion, HttpServletRequest peticion) {
        Set<Throwable> visitadas = Collections.newSetFromMap(new IdentityHashMap<>());
        SQLException postgres = null;
        for (Throwable causa = excepcion; causa != null && visitadas.add(causa); causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                if ("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState())) {
                    return problema(ErrorCodigo.RECURSO_OCUPADO, "El recurso está ocupado. Intente de nuevo.", peticion);
                }
                if (esPostgres(sql)) {
                    postgres = sql;
                }
            }
        }
        if (excepcion instanceof DataIntegrityViolationException) {
            ErrorCodigo codigo = codigoIntegridad(postgres);
            return problema(codigo, codigo.titulo() + ".", peticion);
        }
        return interno(excepcion, peticion);
    }

    /**
     * @param peticion petición denegada
     * @return problema de autorización
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail prohibido(HttpServletRequest peticion) {
        return problema(ErrorCodigo.PROHIBIDO, "No tiene permiso para realizar esta acción.", peticion);
    }

    /**
     * @param peticion petición sin sesión válida
     * @return problema de autenticación
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail noAutenticado(HttpServletRequest peticion) {
        return problema(ErrorCodigo.NO_AUTENTICADO, "Se requiere una sesión válida.", peticion);
    }

    /**
     * @param peticion petición de recurso inexistente
     * @return problema sin revelar recursos ajenos
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail noEncontrado(HttpServletRequest peticion) {
        return problema(ErrorCodigo.NO_ENCONTRADO, "El recurso solicitado no existe.", peticion);
    }

    /**
     * @param excepcion error imprevisto
     * @param peticion petición actual
     * @return mensaje genérico; la traza sin mensajes originales solo se registra en el log
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail interno(Exception excepcion, HttpServletRequest peticion) {
        LOG.error("Error no controlado durante una petición.", trazaSegura(excepcion));
        return problema(ErrorCodigo.ERROR_INTERNO, "No se pudo completar la solicitud. Intente más tarde.", peticion);
    }

    private ProblemDetail validacion(List<ErrorCampo> errores, HttpServletRequest peticion) {
        ProblemDetail problema = problema(ErrorCodigo.VALIDACION, "Revise los datos de la solicitud.", peticion);
        problema.setProperty("errores", errores);
        return problema;
    }

    private ProblemDetail problema(ErrorCodigo codigo, String detalle, HttpServletRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.estado(), detalle);
        problema.setType(URI.create("about:blank"));
        problema.setTitle(codigo.titulo());
        problema.setInstance(URI.create(peticion.getRequestURI()));
        problema.setProperty("codigo", codigo.name());
        if (codigo == ErrorCodigo.VALIDACION) {
            problema.setProperty("errores", List.of());
        }
        return problema;
    }

    private String mensaje(String mensaje) {
        return mensaje == null ? "Valor inválido." : mensaje;
    }

    private ErrorCodigo codigoIntegridad(SQLException postgres) {
        if (postgres == null) {
            return ErrorCodigo.CONFLICTO;
        }
        String restriccion = restriccion(postgres);
        if ("23P01".equals(postgres.getSQLState())) {
            return switch (restriccion) {
                case "reserva_sin_solape_barbero" -> ErrorCodigo.FRANJA_NO_DISPONIBLE;
                case "reserva_sin_solape_cliente" -> ErrorCodigo.CLIENTE_CON_RESERVA_SOLAPADA;
                default -> ErrorCodigo.CONFLICTO;
            };
        }
        if ("23505".equals(postgres.getSQLState())) {
            return switch (restriccion) {
                case "usuario_correo_uk" -> ErrorCodigo.CORREO_DUPLICADO;
                case "servicio_nombre_uk" -> ErrorCodigo.NOMBRE_DUPLICADO;
                default -> ErrorCodigo.CONFLICTO;
            };
        }
        return ErrorCodigo.CONFLICTO;
    }

    private boolean esPostgres(SQLException sql) {
        return sql instanceof PSQLException;
    }

    private String restriccion(SQLException postgres) {
        if (postgres instanceof PSQLException p && p.getServerErrorMessage() != null) {
            String nombre = p.getServerErrorMessage().getConstraint();
            return nombre == null ? "" : nombre;
        }
        return "";
    }
    private Throwable trazaSegura(Throwable original) {
        Set<Throwable> visitadas = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable raiz = null;
        Throwable anterior = null;
        for (Throwable causa = original; causa != null && visitadas.add(causa); causa = causa.getCause()) {
            Throwable segura = new Throwable(causa.getClass().getName() + " (mensaje omitido)");
            segura.setStackTrace(causa.getStackTrace());
            if (raiz == null) {
                raiz = segura;
            } else {
                anterior.initCause(segura);
            }
            anterior = segura;
        }
        return raiz;
    }

    /**
     * Elemento público de validación, sin el valor rechazado.
     * @param campo nombre del campo inválido
     * @param mensaje explicación pública de la validación
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record ErrorCampo(String campo, String mensaje) { }
}
