package pe.barberturno.platform;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.customers.VinculoClienteRepository;
import pe.barberturno.platform.dto.NegocioPropioDto;

/**
 * Consulta global de relaciones propias, sin abrir contexto de negocio ni leer fichas con RLS.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class MisNegociosService {
    private final MembresiaRepository membresias;
    private final VinculoClienteRepository vinculos;
    private final DirectorioNegocios negocios;

    /**
     * Recibe las dos operaciones de plataforma permitidas para la cuenta autenticada.
     * @param membresias consulta de pertenencias propias activas
     * @param vinculos índice de fichas activas mantenido exclusivamente por la base
     * @param negocios proyección de datos públicos por los ids ya acreditados
     */
    public MisNegociosService(MembresiaRepository membresias, VinculoClienteRepository vinculos,
            DirectorioNegocios negocios) {
        this.membresias = membresias; this.vinculos = vinculos; this.negocios = negocios;
    }

    /**
     * Deduplica por negocio y conserva la prioridad de la membresía activa (V2-DA-25).
     * @param usuarioId id del principal autenticado; el controlador no acepta un id alternativo
     * @return negocios propios ordenados por slug, con el rol de ese negocio
     */
    @Transactional(readOnly = true)
    public List<NegocioPropioDto> listar(long usuarioId) {
        Map<Long, String> roles = new HashMap<>();
        vinculos.listarNegociosPropiosActivos(usuarioId).forEach(id -> roles.put(id, "CLIENTE"));
        membresias.listarPropiasActivas(usuarioId).forEach(m -> roles.put(m.getNegocioId(), m.getRol().name()));
        var resultado = new TreeMap<String, NegocioPropioDto>();
        for (var fila : negocios.describir(roles.keySet())) {
            String slug = (String) fila[1];
            resultado.put(slug, new NegocioPropioDto(slug, (String) fila[2], roles.get(((Number)fila[0]).longValue())));
        }
        return List.copyOf(resultado.values());
    }
}
