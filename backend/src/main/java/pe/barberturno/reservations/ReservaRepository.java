package pe.barberturno.reservations;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio de Reserva. */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** Bloquea la reserva después del cliente y los barberos, cuando correspondan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> bloquearPorId(@Param("id") Long id);

    /**
     * Busca reservas que ocupan franja de un barbero.
     * Un id excluido null se transforma en 0: las identidades de V1 son positivas.
     * Así PostgreSQL siempre recibe un parámetro bigint y no debe inferir un null.
     *
     * @param barberoId identificador del barbero
     * @param inicio inicio inclusivo de la búsqueda
     * @param fin fin exclusivo de la búsqueda
     * @param excluirId reserva que se omite o null
     * @return reservas solapadas, ordenadas por inicio e id
     */
    default List<Reserva> buscarSolapamientos(Long barberoId, Instant inicio, Instant fin, Long excluirId) {
        return buscarSolapamientosExcluyendo(barberoId, inicio, fin, excluirId == null ? 0L : excluirId);
    }

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

    /** Aplica el mismo centinela 0 que buscarSolapamientos para el cliente. */
    default List<Reserva> buscarSolapamientosCliente(Long clienteId, Instant inicio, Instant fin, Long excluirId) {
        return buscarSolapamientosClienteExcluyendo(clienteId, inicio, fin, excluirId == null ? 0L : excluirId);
    }

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

    @Query("""
            select count(r) from Reserva r
            where r.cliente.id = :clienteId
              and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA
              and r.inicio > :ahora
            """)
    long contarFuturasQueOcupan(@Param("clienteId") Long clienteId, @Param("ahora") Instant ahora);
}
