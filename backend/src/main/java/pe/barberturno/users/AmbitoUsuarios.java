package pe.barberturno.users;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.platform.Membresia;
import pe.barberturno.customers.Cliente;

/**
 * Delimita las identidades gestionables por un negocio según V2-RN-17 y V2-DA-27.
 * La membresía activa o la ficha activa local determinan el ámbito, sin usar usuario.rol.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
public class AmbitoUsuarios {
    private final ProveedorNegocioActual negocio;
    private final UsuarioRepository usuarios;

    /**
     * Recibe el contexto y la consulta global de identidades; las fichas se filtran además por RLS.
     * @param negocio proveedor del ámbito de la transacción
     * @param usuarios consulta de pertenencia sin devolver identidades ajenas
     */
    public AmbitoUsuarios(ProveedorNegocioActual negocio, UsuarioRepository usuarios) {
        this.negocio = negocio;
        this.usuarios = usuarios;
    }

    /**
     * Compone el mismo límite para páginas filtradas y operaciones por identidad.
     * @return predicado de membresía activa local o ficha activa en el negocio actual
     * @throws java.util.NoSuchElementException si falta el contexto transaccional obligatorio
     */
    public Specification<Usuario> filtro() {
        long negocioId = negocio.negocioActual().orElseThrow();
        return (raiz, consulta, cb) -> {
            var local = consulta.subquery(Long.class);
            var m = local.from(Membresia.class);
            local.select(m.get("usuarioId")).where(cb.equal(m.get("usuarioId"), raiz.get("id")),
                    cb.equal(m.get("negocioId"), negocioId), cb.equal(m.get("estado"), "ACTIVA"));
            var ficha = consulta.subquery(Long.class);
            var c = ficha.from(Cliente.class);
            ficha.select(c.get("usuario").get("id")).where(cb.equal(c.get("usuario").get("id"), raiz.get("id")),
                    cb.isTrue(c.get("activo")));
            return cb.or(cb.exists(local), cb.exists(ficha));
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
