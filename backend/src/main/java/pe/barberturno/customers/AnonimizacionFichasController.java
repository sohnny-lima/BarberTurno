package pe.barberturno.customers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;

/**
 * Acción de anonimización local exclusiva del propietario, protegida por sesión y CSRF.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/clientes")
public class AnonimizacionFichasController {
    private final AnonimizacionFichas fichas;

    AnonimizacionFichasController(AnonimizacionFichas fichas) { this.fichas = fichas; }

    /**
     * Retira la identidad local y sus conversaciones sin borrar el historial de reservas.
     * @param id ficha solicitada bajo el negocio de la ruta
     * @throws NegocioException si no está autorizado o existen dependencias activas
     */
    @PostMapping("/{id}/anonimizar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void anonimizar(@PathVariable long id) { fichas.anonimizar(id); }
}
