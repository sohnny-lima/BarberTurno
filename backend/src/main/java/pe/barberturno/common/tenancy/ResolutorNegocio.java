package pe.barberturno.common.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.OptionalLong;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.security.RespuestaSeguridad;

/**
 * Resuelve la ruta después de StrictHttpFirewall y antes de SecurityContextHolderFilter (Security 7.1).
 * La cadena única de SecurityConfig ve los mismos getters que MVC 7.0. Se recalcula también RequestPath,
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
     * Delimita operaciones globales sin negocio y las locales por ruta o alias explícito.
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
        contexto.fijar(OptionalLong.empty());
        try {
            String ruta = request.getRequestURI().substring(request.getContextPath().length());
            HttpServletRequest efectiva = request;
            HttpServletResponse salida = response;
            OptionalLong id = OptionalLong.empty();
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
