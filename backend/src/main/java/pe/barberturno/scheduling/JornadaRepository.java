package pe.barberturno.scheduling;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistencia de intervalos semanales RN-17; el servicio valida solapes bajo el bloqueo ② del barbero.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface JornadaRepository extends JpaRepository<Jornada, Long> {

    /**
     * Lee la semana completa de un perfil, incluso inactivo, ordenada por día ISO, inicio e id.
     * @param barberoId perfil cuya jornada se consulta
     * @return intervalos ordenados; lista vacía si no tiene jornada
     */
    @Query("select j from Jornada j where j.barbero.id = :barberoId order by j.diaSemana, j.horaInicio, j.id")
    List<Jornada> listarPorBarbero(@Param("barberoId") Long barberoId);

    /**
     * Elimina la semana con una sentencia antes de insertar el reemplazo; requiere el bloqueo ② en el servicio.
     * @param barberoId perfil bloqueado cuya semana se sustituye
     */
    @Modifying
    @Query("delete from Jornada j where j.barbero.id = :barberoId")
    void borrarPorBarbero(@Param("barberoId") Long barberoId);

    /**
     * Lee en una consulta los intervalos del día ISO para todos los perfiles solicitados RF-07/RF-21.
     * @param ids identidades de perfiles; colección no vacía
     * @param dia día ISO entre lunes 1 y domingo 7
     * @return jornadas ordenadas por perfil, hora e identidad
     */
    @Query("select j from Jornada j where j.barbero.id in :ids and j.diaSemana = :dia order by j.barbero.id, j.horaInicio, j.id")
    List<Jornada> buscarDia(@Param("ids") java.util.Collection<Long> ids, @Param("dia") int dia);
}
