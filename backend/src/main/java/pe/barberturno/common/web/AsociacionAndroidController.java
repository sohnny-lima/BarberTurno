package pe.barberturno.common.web;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publica la declaración Digital Asset Links que permite a Chrome abrir BarberTurno como Trusted Web Activity
 * sin barra de direcciones (V2-DA-95). Es una lectura pública y sin datos de usuario: la autorización la deja
 * pasar como recurso estático y el reenvío a Angular la ignora por llevar extensión.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@EnableConfigurationProperties(AsociacionAndroidPropiedades.class)
public class AsociacionAndroidController {
    /** Relación que delega en la aplicación la apertura de todas las URL del sitio. */
    static final String RELACION = "delegate_permission/common.handle_all_urls";

    private final AsociacionAndroidPropiedades propiedades;

    /**
     * Crea el controlador con la asociación configurada por el entorno.
     * @param propiedades paquete y huellas validados al arrancar
     */
    public AsociacionAndroidController(AsociacionAndroidPropiedades propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Devuelve la declaración en el formato oficial de Digital Asset Links: una lista con una sola sentencia
     * {@code android_app} para el paquete y las huellas configurados.
     * @return 200 con la declaración JSON, o 404 si falta el paquete o no hay huellas (sin asociación)
     */
    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> declaracion() {
        if (!propiedades.configurada()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> destino = Map.of(
                "namespace", "android_app",
                "package_name", propiedades.paquete(),
                "sha256_cert_fingerprints", List.copyOf(propiedades.huellas()));
        return ResponseEntity.ok(List.of(Map.of("relation", List.of(RELACION), "target", destino)));
    }
}
