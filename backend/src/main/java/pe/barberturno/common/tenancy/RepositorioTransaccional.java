package pe.barberturno.common.tenancy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conserva transacciones de escritura explícitas al declarar repositorios de solo lectura.
 * Spring Data prioriza las anotaciones de la interfaz sobre las de SimpleJpaRepository;
 * por eso se redeclaran las escrituras heredadas, sin alterar su implementación ni propagación.
 * @param <T> entidad persistente del repositorio
 * @param <ID> tipo de su identidad
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@NoRepositoryBean
public interface RepositorioTransaccional<T, ID> extends JpaRepository<T, ID> {
    /**
     * Persiste una entidad en una transacción de escritura con contexto de negocio.
     * @param entidad estado nuevo o separado que debe guardarse
     * @param <S> subtipo concreto de entidad
     * @return instancia gestionada resultante
     */
    @Override
    @Transactional
    <S extends T> S save(S entidad);

    /**
     * Persiste un conjunto con el mismo contexto transaccional.
     * @param entidades estados nuevos o separados que deben guardarse
     * @param <S> subtipo concreto de entidad
     * @return instancias gestionadas resultantes
     */
    @Override
    @Transactional
    <S extends T> List<S> saveAll(Iterable<S> entidades);

    /**
     * Persiste y sincroniza inmediatamente para comprobar las restricciones de PostgreSQL.
     * @param entidad estado nuevo o separado que debe guardarse
     * @param <S> subtipo concreto de entidad
     * @return instancia gestionada resultante
     */
    @Override
    @Transactional
    <S extends T> S saveAndFlush(S entidad);

    /**
     * Persiste un conjunto y sincroniza sus restricciones en la misma transacción.
     * @param entidades estados nuevos o separados que deben guardarse
     * @param <S> subtipo concreto de entidad
     * @return instancias gestionadas resultantes
     */
    @Override
    @Transactional
    <S extends T> List<S> saveAllAndFlush(Iterable<S> entidades);

    /** Sincroniza el contexto de persistencia dentro de una transacción de escritura. */
    @Override
    @Transactional
    void flush();

    /**
     * Elimina una identidad visible bajo las restricciones de integridad vigentes.
     * @param id identidad de la entidad que debe eliminarse
     */
    @Override
    @Transactional
    void deleteById(ID id);

    /**
     * Elimina una entidad sin cambiar las reglas de autorización del servicio.
     * @param entidad entidad persistente que debe eliminarse
     */
    @Override
    @Transactional
    void delete(T entidad);

    /**
     * Elimina las identidades visibles dentro del mismo contexto de negocio.
     * @param ids identidades que deben eliminarse
     */
    @Override
    @Transactional
    void deleteAllById(Iterable<? extends ID> ids);

    /**
     * Elimina un conjunto de entidades respetando las restricciones de PostgreSQL.
     * @param entidades entidades persistentes que deben eliminarse
     */
    @Override
    @Transactional
    void deleteAll(Iterable<? extends T> entidades);

    /** Elimina todas las entidades visibles con el contexto de la transacción de escritura. */
    @Override
    @Transactional
    void deleteAll();

    /**
     * Conserva la escritura del adaptador antiguo para evitar una transacción exterior de solo lectura.
     * @param entidades entidades persistentes que deben eliminarse en bloque
     * @deprecated usar {@link #deleteAllInBatch(Iterable)}
     */
    @Override
    @Deprecated
    @Transactional
    default void deleteInBatch(Iterable<T> entidades) {
        deleteAllInBatch(entidades);
    }

    /**
     * Elimina el conjunto con una sentencia, limitada por RLS al negocio actual.
     * @param entidades entidades persistentes que deben eliminarse en bloque
     */
    @Override
    @Transactional
    void deleteAllInBatch(Iterable<T> entidades);

    /**
     * Elimina las identidades con una sentencia y el contexto de la transacción.
     * @param ids identidades que deben eliminarse en bloque
     */
    @Override
    @Transactional
    void deleteAllByIdInBatch(Iterable<ID> ids);

    /** Elimina en bloque todas las entidades visibles, conservando RLS y las restricciones. */
    @Override
    @Transactional
    void deleteAllInBatch();
}
