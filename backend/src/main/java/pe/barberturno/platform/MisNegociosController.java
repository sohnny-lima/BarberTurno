package pe.barberturno.platform;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.platform.dto.NegocioPropioDto;

/**
 * Expone las relaciones de la propia identidad global sin un negocio seleccionado.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
public class MisNegociosController {
    private final MisNegociosService negocios;
    private final UsuarioActual actual;

    /**
     * Impide que un id de cuenta del navegador elija qué relaciones se consultan.
     * @param negocios consulta global de relaciones propias
     * @param actual principal revalidado por el servidor
     */
    public MisNegociosController(MisNegociosService negocios, UsuarioActual actual) {
        this.negocios = negocios; this.actual = actual;
    }

    /**
     * Lista membresías y fichas activas de la cuenta autenticada.
     * @return negocios propios, vacíos si la cuenta no tiene relaciones activas
     */
    @GetMapping("/api/mis-negocios")
    public List<NegocioPropioDto> listar() { return negocios.listar(actual.id()); }
}
