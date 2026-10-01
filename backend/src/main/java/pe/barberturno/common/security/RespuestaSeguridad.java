package pe.barberturno.common.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.common.error.ErrorCodigo;

/** Escribe el mismo formato RFC 9457 que el advice fuera del DispatcherServlet. */
@Component
public class RespuestaSeguridad {
    private final JsonMapper mapper;

    public RespuestaSeguridad(JsonMapper mapper) { this.mapper = mapper; }

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
