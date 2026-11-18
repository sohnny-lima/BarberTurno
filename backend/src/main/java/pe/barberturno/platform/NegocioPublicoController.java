package pe.barberturno.platform;

import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.platform.dto.NegocioPublicoDto;

/**
 * Lectura pública de parámetros de /api/n/{slug}/negocio tras resolver el prefijo canónico.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/negocio")
public class NegocioPublicoController {
    private final NegocioPublicoService negocio;

    /**
     * Enlaza la consulta HTTP con la proyección pública transaccional.
     * @param negocio lectura del negocio de la ruta
     */
    public NegocioPublicoController(NegocioPublicoService negocio) { this.negocio = negocio; }

    /**
     * Expone los parámetros públicos vigentes también a visitantes sin sesión.
     * @return únicamente los seis campos aprobados en V2-DA-44
     * @throws NegocioException si el negocio está suspendido o no existe
     */
    @GetMapping
    public NegocioPublicoDto consultar() { return negocio.consultar(); }
}
