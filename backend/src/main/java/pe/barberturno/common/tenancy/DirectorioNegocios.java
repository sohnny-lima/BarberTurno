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
     * @param entidades acceso transaccional a la tabla global negocio
     */
    public DirectorioNegocios(EntityManager entidades) { this.entidades = entidades; }

    /**
     * Valida el mismo formato y longitud del DDL y busca el slug exacto en cada invocación.
     * @param slug identificador público propuesto
     * @return id de plataforma o vacío si no existe o no tiene formato válido
     */
    @Transactional(readOnly = true)
    public OptionalLong buscar(String slug) {
        return buscar(slug, false);
    }

    /**
     * Resuelve una ruta pública en una sola consulta, ocultando negocios suspendidos sin caché.
     * @param slug identificador público con el formato canónico del DDL
     * @return id del negocio activo o vacío si no existe, está suspendido o el formato es inválido
     */
    @Transactional(readOnly = true)
    public OptionalLong buscarActivo(String slug) {
        return buscar(slug, true);
    }

    private OptionalLong buscar(String slug, boolean soloActivo) {
        if (slug == null || slug.length() > 40 || !slug.matches("[a-z0-9](-?[a-z0-9])*")) {
            return OptionalLong.empty();
        }
        var filas = entidades.createNativeQuery("select id from negocio where slug=:slug"
                + (soloActivo ? " and estado='ACTIVO'" : ""), Long.class)
                .setParameter("slug", slug).getResultList();
        return filas.isEmpty() ? OptionalLong.empty() : OptionalLong.of(((Number) filas.getFirst()).longValue());
    }

    /**
     * Revalida la disponibilidad administrativa sin caché, antes de acceder al dominio del negocio.
     * @param id negocio resuelto exclusivamente por la ruta
     * @return true si la plataforma lo ha suspendido
     */
    @Transactional(readOnly = true)
    public boolean suspendido(long id) {
        return !entidades.createNativeQuery("select id from negocio where id=:id and estado='SUSPENDIDO'", Long.class)
                .setParameter("id", id).getResultList().isEmpty();
    }

    /**
     * Proyecta los datos públicos de los negocios ya acreditados como propios por los repositorios.
     * @param ids identificadores derivados de pertenencias o fichas propias, nunca del cuerpo HTTP
     * @return filas id, slug y nombre, sin datos de configuración ni personales
     */
    @Transactional(readOnly = true)
    public java.util.List<Object[]> describir(java.util.Set<Long> ids) {
        if (ids.isEmpty()) return java.util.List.of();
        return entidades.createNativeQuery("select id, slug, nombre from negocio where id in (:ids)", Object[].class)
                .setParameter("ids", ids).getResultList();
    }
}
