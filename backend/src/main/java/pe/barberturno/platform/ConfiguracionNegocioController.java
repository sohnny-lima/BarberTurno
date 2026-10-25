package pe.barberturno.platform;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.platform.dto.ConfiguracionNegocioDto;

/**
 * API de configuración sobre el negocio resuelto por V2-05 y con protección CSRF al escribir.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionNegocioController {
    private final ConfiguracionNegocioService configuracion;

    /**
     * Conecta HTTP con la autorización y persistencia local del servicio.
     * @param configuracion operaciones transaccionales del negocio
     */
    public ConfiguracionNegocioController(ConfiguracionNegocioService configuracion) { this.configuracion = configuracion; }

    /**
     * Muestra exclusivamente la configuración editable al propietario.
     * @param actor identidad autenticada y revalidada en el negocio de la ruta
     * @return parámetros vigentes sin atributos reservados
     * @throws NegocioException si el actor no es propietario activo
     */
    @GetMapping
    public ConfiguracionNegocioDto consultar(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return configuracion.consultar(actor);
    }

    /**
     * Valida y guarda todos los campos editables, conservando la identidad pública del negocio.
     * @param datos parámetros completos dentro de los rangos de V2-04 y SQL
     * @param actor propietario autenticado
     * @return configuración guardada
     * @throws NegocioException si faltan permisos, el negocio está suspendido o se reduce el cupo por debajo de sus activos
     */
    @PutMapping
    public ConfiguracionNegocioDto actualizar(@Valid @RequestBody ConfiguracionNegocioDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return configuracion.actualizar(datos, actor);
    }
}
