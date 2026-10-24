package pe.barberturno.common.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Locale;
import java.util.OptionalLong;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.security.RespuestaSeguridad;

/**
 * Resuelve la ruta después de StrictHttpFirewall y antes de SecurityContextHolderFilter (Security 7.1).
 * StrictHttpFirewall 7.1.1 rechaza, sin corregirlos, segmentos punto, barras dobles o invertidas,
 * punto y barra codificados, porcentajes codificados y parámetros con punto y coma. No decodifica
 * letras ni cambia mayúsculas o barras finales. Por V2-DA-36 se rechaza todo porcentaje de la ruta API
 * antes de clasificar; la consulta no interviene. UrlPathHelper de Framework 7.0.9 obtiene la ruta
 * decodificada dentro de la aplicación; tras esos rechazos coincide con los segmentos de PathPattern
 * de MVC, sin quitar barras finales ni cambiar mayúsculas (variantes que aquí terminan en 404).
 * La cadena única de SecurityConfig ve esa misma ruta en los tres getters. Se recalcula RequestPath,
 * que FilterChainProxy puede haber cacheado antes; se restaura al salir. El reset del firewall no
 * altera los getters del envoltorio exterior. CSRF y DA-22 reciben así la misma ruta que el controlador.
 * La respuesta conserva el prefijo en Location para que el URI canónico de una reserva no salte al alias.
 * No es un bean Filter: solo se registra dentro de Spring Security, nunca en el contenedor.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public class ResolutorNegocio extends OncePerRequestFilter {
    private final ContextoNegocio contexto;
    private final DirectorioNegocios directorio;
    private final RespuestaSeguridad errores;
    private final String alias;

    /**
     * Recibe contexto, búsqueda y escritor común de errores RFC 9457.
     * @param contexto holder que se elimina en finally en todas las salidas
     * @param directorio resolución exacta de slugs sin caché
     * @param errores respuesta ProblemDetail sin trazas
     * @param entorno configuración del alias, validada antes de conectar por ConfiguracionTenancy
     */
    public ResolutorNegocio(ContextoNegocio contexto, DirectorioNegocios directorio,
            RespuestaSeguridad errores, Environment entorno) {
        this.contexto = contexto;
        this.directorio = directorio;
        this.errores = errores;
        this.alias = entorno.getProperty("barberturno.tenancy.alias-legado.slug");
    }

    /**
     * Rechaza variantes no canónicas antes de delimitar operaciones globales o locales por ruta o alias.
     * @param request petición ya validada por el cortafuegos
     * @param response salida HTTP
     * @param chain resto de seguridad y MVC, con una única ruta normalizada
     * @throws ServletException si falla el procesamiento posterior
     * @throws IOException si falla la escritura HTTP
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var anterior = ServletRequestPathUtils.hasParsedRequestPath(request)
                ? ServletRequestPathUtils.getParsedRequestPath(request) : null;
        try {
            String cruda = request.getRequestURI().substring(request.getContextPath().length());
            String ruta = esRutaApi(cruda) ? null : UrlPathHelper.defaultInstance.getPathWithinApplication(request);
            boolean api = esRutaApi(cruda) || esRutaApi(ruta);
            if (api && cruda.contains("%")) {
                errores.escribir(request, response, ErrorCodigo.VALIDACION,
                        "La ruta de la API no admite codificación porcentual.");
                return;
            }
            if (ruta == null) ruta = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
            if (api && (!ruta.equals(ruta.toLowerCase(Locale.ROOT)) || ruta.endsWith("/"))) {
                noEncontrado(request, response);
                return;
            }
            HttpServletRequest efectiva = api ? new PeticionNegocio(request, ruta) : request;
            HttpServletResponse salida = response;
            OptionalLong id = OptionalLong.empty();
            contexto.fijar(id);
            if (ruta.equals("/api/n") || ruta.startsWith("/api/n/")) {
                String[] partes = ruta.split("/", 5);
                if (partes.length < 5 || partes[4].isEmpty() || (partes[4].equals("auth") || partes[4].startsWith("auth/") || esGlobal("/api/" + partes[4]))) {
                    noEncontrado(request, response);
                    return;
                }
                id = directorio.buscar(partes[3]);
                if (id.isEmpty()) { noEncontrado(request, response); return; }
                efectiva = new PeticionNegocio(request, "/api/" + partes[4]);
                salida = new RespuestaNegocio(response, request.getContextPath() + "/api/n/" + partes[3] + "/");
            } else if (ruta.startsWith("/api/") && !esGlobal(ruta)) {
                id = directorio.buscar(alias);
                if (id.isEmpty()) { noEncontrado(request, response); return; }
            }
            contexto.fijar(id);
            ServletRequestPathUtils.parseAndCache(efectiva);
            chain.doFilter(efectiva, salida);
        } finally {
            contexto.limpiar();
            ServletRequestPathUtils.setParsedRequestPath(anterior, request);
        }
    }

    private boolean esRutaApi(String ruta) {
        return ruta != null && (ruta.equalsIgnoreCase("/api") || ruta.regionMatches(true, 0, "/api/", 0, 5));
    }

    private boolean esGlobal(String ruta) {
        return (ruta.equals("/api/auth") || ruta.startsWith("/api/auth/"))
                && !ruta.equals("/api/auth/registro")
                || ruta.equals("/api/perfil") || ruta.startsWith("/api/perfil/")
                || ruta.equals("/api/mis-negocios") || ruta.startsWith("/api/mis-negocios/");
    }

    private void noEncontrado(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errores.escribir(request, response, ErrorCodigo.NO_ENCONTRADO, "No se encontró la ruta del negocio.");
    }

    private static final class PeticionNegocio extends HttpServletRequestWrapper {
        private final String ruta;
        PeticionNegocio(HttpServletRequest request, String ruta) { super(request); this.ruta = ruta; }
        @Override public String getRequestURI() { return getContextPath() + ruta; }
        @Override public String getServletPath() { return ruta; }
        @Override public String getPathInfo() { return null; }
    }

    private static final class RespuestaNegocio extends HttpServletResponseWrapper {
        private final String prefijo;
        RespuestaNegocio(HttpServletResponse response, String prefijo) { super(response); this.prefijo = prefijo; }
        private String localizar(String nombre, String valor) {
            return nombre.equalsIgnoreCase("Location") && valor != null && valor.startsWith("/api/")
                    ? prefijo + valor.substring("/api/".length()) : valor;
        }
        @Override public void setHeader(String nombre, String valor) { super.setHeader(nombre, localizar(nombre, valor)); }
        @Override public void addHeader(String nombre, String valor) { super.addHeader(nombre, localizar(nombre, valor)); }
    }
}
