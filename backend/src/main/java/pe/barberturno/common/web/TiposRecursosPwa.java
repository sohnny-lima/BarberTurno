package pe.barberturno.common.web;

import java.util.Map;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * Tipos de los archivos estáticos de la PWA que ni el contenedor ni Spring conocen (V2-18): sin esta
 * correspondencia, {@code manifest.webmanifest} sale como {@code application/octet-stream} y el navegador no
 * lo reconoce como manifest. El manejador de recursos estáticos solo consulta su propia tabla de extensiones
 * cuando el contenedor no da un tipo, así que no altera ningún tipo ya conocido ni la negociación de la API.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
public class TiposRecursosPwa {
    /** Tipo registrado por la especificación Web App Manifest. */
    static final MediaType MANIFEST = MediaType.valueOf("application/manifest+json");
    /** Extensiones añadidas a cada manejador de recursos estáticos. */
    static final Map<String, MediaType> EXTENSIONES = Map.of("webmanifest", MANIFEST);

    /** Constructor usado por Spring. */
    public TiposRecursosPwa() { }

    /**
     * Añade las extensiones de la PWA a los manejadores de recursos estáticos registrados por Spring MVC,
     * una vez inicializado cada mapeo de URL (sus manejadores no son beans propios).
     * @return procesador que solo actúa sobre mapeos con manejadores de recursos
     */
    @Bean
    static BeanPostProcessor tiposRecursosPwaProcesador() {
        return new BeanPostProcessor() {
            /**
             * Completa los tipos de los manejadores de recursos del mapeo recién inicializado.
             * @param bean bean ya inicializado
             * @param nombre nombre del bean
             * @return el mismo bean
             */
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (bean instanceof AbstractUrlHandlerMapping mapeo) {
                    mapeo.getHandlerMap().values().forEach(manejador -> {
                        if (manejador instanceof ResourceHttpRequestHandler recursos) {
                            recursos.setMediaTypes(EXTENSIONES);
                        }
                    });
                }
                return bean;
            }
        };
    }
}
