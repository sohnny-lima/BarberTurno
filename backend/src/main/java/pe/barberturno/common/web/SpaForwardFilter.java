package pe.barberturno.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Sirve la entrada Angular para navegaciones HTML sin extensión, conservando API y rutas técnicas.
 * Se instala después de la autorización y no se registra como filtro del contenedor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class SpaForwardFilter extends OncePerRequestFilter {
    private static final UrlPathHelper RUTAS = new UrlPathHelper();
    private static final List<String> PREFIJOS_RESERVADOS =
            List.of("/api", "/actuator", "/v3/api-docs", "/swagger-ui");

    /**
     * Crea el filtro sin dependencias; la misma política de rutas se comparte con la autorización.
     */
    public SpaForwardFilter() {
    }

    /**
     * Identifica lecturas públicas de archivos y rutas Angular sin conceder acceso a rutas técnicas.
     * También reserva las raíces exactas y variantes que comienzan por los prefijos técnicos.
     * Compara la ruta decodificada igual que MVC para que un prefijo codificado no eluda la autorización.
     * @param peticion solicitud cuya ruta se interpreta sin el contextPath
     * @return true para GET o HEAD fuera de los prefijos reservados
     */
    public static boolean esRecursoPublico(HttpServletRequest peticion) {
        String ruta = ruta(peticion);
        return ("GET".equals(peticion.getMethod()) || "HEAD".equals(peticion.getMethod()))
                && PREFIJOS_RESERVADOS.stream().noneMatch(ruta::startsWith);
    }

    /**
     * Reenvía navegaciones GET compatibles con HTML a la entrada Angular; los archivos, rutas técnicas
     * y solicitudes de otros tipos siguen hacia MVC sin sustitución de su respuesta.
     * @param peticion solicitud actual con negociación de contenido del navegador
     * @param respuesta respuesta en la que se sirve el archivo o continúa el procesamiento
     * @param cadena filtros restantes para solicitudes que no son navegaciones Angular
     * @throws ServletException si el reenvío o la cadena falla
     * @throws IOException si falla la lectura o escritura de la respuesta
     */
    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {
        if ("GET".equals(peticion.getMethod()) && esRecursoPublico(peticion)
                && !ruta(peticion).contains(".") && aceptaHtml(peticion)) {
            peticion.getRequestDispatcher("/index.html").forward(peticion, respuesta);
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private static String ruta(HttpServletRequest peticion) {
        return RUTAS.getPathWithinApplication(peticion);
    }

    private static boolean aceptaHtml(HttpServletRequest peticion) {
        String accept = peticion.getHeader("Accept");
        if (accept == null) return false;
        try {
            return MediaType.parseMediaTypes(accept).stream().anyMatch(tipo ->
                    tipo.getQualityValue() > 0 && tipo.isCompatibleWith(MediaType.TEXT_HTML));
        } catch (IllegalArgumentException excepcion) {
            return false;
        }
    }
}
