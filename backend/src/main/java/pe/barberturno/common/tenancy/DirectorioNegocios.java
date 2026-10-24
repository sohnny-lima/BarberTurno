package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManager;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve slugs de la tabla de plataforma mediante Hibernate transaccional, sin caché de aplicación.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class DirectorioNegocios {
    private final EntityManager entidades;

    /**
     * Recibe la sesión protegida por la guarda de V2-DA-23.
     * @param entidades acceso a la tabla global negocio, que solo permite SELECT a la aplicación
     */
    public DirectorioNegocios(EntityManager entidades) { this.entidades = entidades; }

    /**
     * Valida el mismo formato y longitud del DDL y busca el slug exacto en cada invocación.
     * @param slug identificador público propuesto
     * @return id de plataforma o vacío si no existe o no tiene formato válido
     */
    @Transactional(readOnly = true)
    public OptionalLong buscar(String slug) {
        if (slug == null || slug.length() > 40 || !slug.matches("[a-z0-9](-?[a-z0-9])*")) {
            return OptionalLong.empty();
        }
        var filas = entidades.createNativeQuery("select id from negocio where slug=:slug", Long.class)
                .setParameter("slug", slug).getResultList();
        return filas.isEmpty() ? OptionalLong.empty() : OptionalLong.of(((Number) filas.getFirst()).longValue());
    }
}
