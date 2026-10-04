package pe.barberturno.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta bloqueos que se cruzan con un intervalo de disponibilidad de un barbero (RF-06 y RF-07).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    /**
     * Lee solo el perfil para tomar ② antes de cargar y eliminar un bloqueo RN-16.
     * @param id identidad persistente del bloqueo solicitado
     * @return identidad del perfil o vacío si el bloqueo no existe
     */
    @Query("select b.barbero.id from Bloqueo b where b.id = :id")
    Optional<Long> buscarBarberoId(@Param("id") Long id);

    /**
     * Lee sin bloqueo intervalos del barbero que solapan el rango semiabierto; permite contigüidad y ordena por
     * inicio e id. La escritura toma antes el bloqueo ②.
     * @param barberoId identificador persistente no nulo del perfil
     * @param inicio inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no
     * nulo
     * @param fin fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo
     * @return resultados con el orden descrito, sin comprobación adicional de permisos
     */
    @Query("""
            select b from Bloqueo b
            where b.barbero.id = :barberoId and b.inicio < :fin and b.fin > :inicio
            order by b.inicio, b.id
            """)
    List<Bloqueo> buscarQueSeCruzan(@Param("barberoId") Long barberoId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin);

    /**
     * Lee ocupaciones semiabiertas del día de Lima en una consulta para RF-07/RF-21.
     * @param ids perfiles seleccionados; colección no vacía
     * @param inicio medianoche inclusiva del día
     * @param fin medianoche exclusiva del siguiente día
     * @return bloqueos solapados ordenados por perfil, inicio e identidad
     */
    @Query("select b from Bloqueo b where b.barbero.id in :ids and b.inicio < :fin and b.fin > :inicio order by b.barbero.id, b.inicio, b.id")
    List<Bloqueo> buscarDia(@Param("ids") java.util.Collection<Long> ids,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin);
}
