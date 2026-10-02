package pe.barberturno.reservations;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de ocupación RN-03, RN-04 y RN-20 y bloqueo ③ de reservas según arquitectura §8.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * Cuenta citas futuras que ocupan franja RN-14 para informar sin cancelar al cambiar estado RF-05.
     * @param barberoId perfil cuya agenda se consulta
     * @param ahora instante del Clock; solo se cuentan inicios estrictamente posteriores
     * @return cantidad futura excluyendo CANCELADA
     */
    @Query("select count(r) from Reserva r where r.barbero.id = :barberoId and r.inicio > :ahora and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA")
    long contarFuturasVigentes(@Param("barberoId") Long barberoId, @Param("ahora") Instant ahora);

    /**
     * Toma PESSIMISTIC_WRITE: bloqueo ③ después de ① y ② cuando corresponden; cancelar y transicionar usan solo
     * ③.
     * @param id identificador persistente positivo del recurso, no nulo
     * @return resultado opcional; vacío si no existe el recurso
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> bloquearPorId(@Param("id") Long id);

    /**
     * Lee solapes del barbero RN-03 sin bloqueo; excluye CANCELADA y ordena por inicio e id. Sustituye excluirId
     * nulo por centinela 0L, ajeno a las identidades positivas de V1.
     * @param barberoId identificador del barbero
     * @param inicio inicio inclusivo de la búsqueda
     * @param fin fin exclusivo de la búsqueda
     * @param excluirId reserva que se omite o null
     * @return reservas solapadas, ordenadas por inicio e id
     */
    default List<Reserva> buscarSolapamientos(Long barberoId, Instant inicio, Instant fin, Long excluirId) {
        return buscarSolapamientosExcluyendo(barberoId, inicio, fin, excluirId == null ? 0L : excluirId);
    }

    /**
     * Lee solapes semiabiertos del barbero RN-03 sin bloqueo; excluye CANCELADA y la identidad indicada, con 0L
     * para no excluir ninguna; ordena por inicio e id.
     * @param barberoId identificador persistente no nulo del perfil
     * @param inicio inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no
     * nulo
     * @param fin fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo
     * @param excluirId reserva omitida; nula en el adaptador o 0L en la consulta para no excluir ninguna
     * @return resultados con el orden descrito, sin comprobación adicional de permisos
     */
    @Query("""
            select r from Reserva r
            where r.barbero.id = :barberoId
              and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA
              and r.inicio < :fin and r.fin > :inicio and r.id <> :excluirId
            order by r.inicio, r.id
            """)
    List<Reserva> buscarSolapamientosExcluyendo(@Param("barberoId") Long barberoId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin,
            @Param("excluirId") Long excluirId);

    /**
     * Lee solapes del cliente RN-04 sin bloqueo aun con otros barberos; excluye CANCELADA y ordena por inicio e
     * id. Sustituye excluirId nulo por centinela 0L de V1.
     * @param clienteId identificador persistente no nulo del cliente
     * @param inicio inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no
     * nulo
     * @param fin fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo
     * @param excluirId reserva omitida; nula en el adaptador o 0L en la consulta para no excluir ninguna
     * @return resultados con el orden descrito, sin comprobación adicional de permisos
     */
    default List<Reserva> buscarSolapamientosCliente(Long clienteId, Instant inicio, Instant fin, Long excluirId) {
        return buscarSolapamientosClienteExcluyendo(clienteId, inicio, fin, excluirId == null ? 0L : excluirId);
    }

    /**
     * Lee solapes semiabiertos del cliente RN-04 sin bloqueo; excluye CANCELADA y la identidad indicada, con 0L
     * para no excluir ninguna; ordena por inicio e id.
     * @param clienteId identificador persistente no nulo del cliente
     * @param inicio inicio inclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no
     * nulo
     * @param fin fin exclusivo del intervalo semiabierto RN-01, como instante absoluto timestamptz; no nulo
     * @param excluirId reserva omitida; nula en el adaptador o 0L en la consulta para no excluir ninguna
     * @return resultados con el orden descrito, sin comprobación adicional de permisos
     */
    @Query("""
            select r from Reserva r
            where r.cliente.id = :clienteId
              and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA
              and r.inicio < :fin and r.fin > :inicio and r.id <> :excluirId
            order by r.inicio, r.id
            """)
    List<Reserva> buscarSolapamientosClienteExcluyendo(@Param("clienteId") Long clienteId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin,
            @Param("excluirId") Long excluirId);

    /**
     * Cuenta reservas con inicio estrictamente posterior a ahora y estado distinto de CANCELADA para RN-20, sin
     * bloqueo; el servicio toma antes ①.
     * @param clienteId identificador persistente no nulo del cliente
     * @param ahora instante absoluto no nulo de evaluación aportado por Clock
     * @return número de reservas futuras que ocupan franja RN-20
     */
    @Query("""
            select count(r) from Reserva r
            where r.cliente.id = :clienteId
              and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA
              and r.inicio > :ahora
            """)
    long contarFuturasQueOcupan(@Param("clienteId") Long clienteId, @Param("ahora") Instant ahora);
}
