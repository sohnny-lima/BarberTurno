package pe.barberturno.common.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.common.error.ErrorCodigo;

/**
 * Emite errores RFC 9457 desde los filtros, con el mismo catálogo que el manejador MVC (MJ-18).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class RespuestaSeguridad {
    private final JsonMapper mapper;

    /**
     * Inyecta el serializador JSON para emitir problemas desde la cadena de seguridad.
     * @param mapper serializador JSON no nulo para errores anteriores a MVC
     */
    public RespuestaSeguridad(JsonMapper mapper) { this.mapper = mapper; }

    /**
     * Escribe estado HTTP y application/problem+json sin trazas ni secretos; añade código estable y URI de
     * petición (MJ-18).
     * @param request petición HTTP actual no nula
     * @param response respuesta HTTP no nula que recibe el resultado
     * @param codigo código estable no nulo del catálogo §6.2
     * @param detalle mensaje público no nulo, sin secretos ni detalles técnicos
     * @throws java.io.IOException si no se puede escribir el JSON en la salida HTTP
     */
    public void escribir(HttpServletRequest request, HttpServletResponse response,
            ErrorCodigo codigo, String detalle) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.estado(), detalle);
        problema.setType(URI.create("about:blank"));
        problema.setTitle(codigo.titulo());
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("codigo", codigo.name());
        response.setStatus(codigo.estado().value());
        response.setContentType("application/problem+json");
        // Mapa explícito para no depender de mixins de MVC en la cadena de filtros.
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("type", problema.getType().toString());
        cuerpo.put("title", problema.getTitle());
        cuerpo.put("status", problema.getStatus());
        cuerpo.put("detail", problema.getDetail());
        cuerpo.put("instance", problema.getInstance().toString());
        cuerpo.putAll(problema.getProperties());
        mapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
