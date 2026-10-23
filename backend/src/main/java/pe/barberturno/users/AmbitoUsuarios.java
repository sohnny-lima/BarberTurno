package pe.barberturno.users;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.RolPlataforma;

/**
 * Delimita las identidades gestionables por un negocio según V2-RN-17 y V2-DA-27.
 * Los clientes sin membresías siguen ligados exclusivamente al negocio único hasta V2-03.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
public class AmbitoUsuarios {
    private final ProveedorNegocioActual negocio;
    private final long negocioUnicoId;
    private final UsuarioRepository usuarios;

    /**
     * Recibe el contexto y el identificador configurado del negocio que hereda los clientes de v1.
     * @param negocio proveedor del ámbito de la transacción
     * @param negocioUnicoId negocio único de la transición, independiente del contexto de prueba
     * @param usuarios consulta de pertenencia sin devolver identidades ajenas
     */
    public AmbitoUsuarios(ProveedorNegocioActual negocio,
            @Value("${barberturno.tenancy.negocio-unico-id}") long negocioUnicoId, UsuarioRepository usuarios) {
        this.negocio = negocio;
        this.negocioUnicoId = negocioUnicoId;
        this.usuarios = usuarios;
    }

    /**
     * Compone el mismo límite para páginas filtradas y operaciones por identidad.
     * @return predicado de membresía activa local o cliente heredado sin ninguna membresía
     * @throws java.util.NoSuchElementException si falta el contexto transaccional obligatorio
     */
    public Specification<Usuario> filtro() {
        long negocioId = negocio.negocioActual().orElseThrow();
        return (raiz, consulta, cb) -> {
            var local = consulta.subquery(Long.class);
            var m = local.from(Membresia.class);
            local.select(m.get("usuarioId")).where(cb.equal(m.get("usuarioId"), raiz.get("id")),
                    cb.equal(m.get("negocioId"), negocioId), cb.equal(m.get("estado"), "ACTIVA"));
            var cualquiera = consulta.subquery(Long.class);
            var otra = cualquiera.from(Membresia.class);
            cualquiera.select(otra.get("usuarioId")).where(cb.equal(otra.get("usuarioId"), raiz.get("id")));
            var clienteV1 = cb.and(negocioId == negocioUnicoId ? cb.conjunction() : cb.disjunction(),
                    cb.equal(raiz.get("rol"), Rol.CLIENTE),
                    cb.equal(raiz.get("rolPlataforma"), RolPlataforma.NINGUNO), cb.not(cb.exists(cualquiera)));
            return cb.or(cb.exists(local), clienteV1);
        };
    }

    /**
     * Evalúa el límite completo incluso si la identidad tiene un perfil local antiguo o revocado.
     * Debe invocarse dentro de la transacción del servicio que gestiona el usuario.
     * @param usuario identidad global ya bloqueada por el servicio
     * @return si el negocio puede gestionar esa identidad
     */
    public boolean contiene(Usuario usuario) {
        return usuarios.exists(filtro().and((raiz, consulta, cb) -> cb.equal(raiz.get("id"), usuario.getId())));
    }
}
