package pe.barberturno.reservations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistencia de reservas: ocupación RN-03/04/20, bloqueo ③ (§8) y consultas paginadas RF-11/13.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    /**
     * Lee datos escalares RF-09 sin poblar el contexto JPA antes de los bloqueos ① ② ③.
     * @param id reserva cuya propiedad y asignación inicial se necesitan
     * @return fotografía o vacío si no existe; debe revalidarse después de esperar
     */
    @Query("select new pe.barberturno.reservations.ReservaLectura(r.cliente.id, r.barbero.id, r.barbero.usuario.id, r.version, r.estado, r.inicio) from Reserva r where r.id = :id")
    Optional<ReservaLectura> leerParaReprogramar(@Param("id") long id);


    /**
     * Pagina filtros RF-11/13 con relaciones to-one en la consulta de contenido, evitando N+1.
     * Spring Data calcula el total con una consulta separada sin cargar esas relaciones.
     * @param filtros predicados de propiedad y búsqueda ya autorizados por el servicio
     * @param pagina límites y orden estable por inicio e identidad
     * @return página con cliente, barbero, usuario asignado y servicio disponibles
     */
    @Override
    @EntityGraph(attributePaths = {"cliente", "barbero.usuario", "servicio"})
    Page<Reserva> findAll(Specification<Reserva> filtros, Pageable pagina);

    /**
     * Lee un detalle sin bloqueo con todas las relaciones necesarias para autorización y DTO.
     * @param id identidad solicitada
     * @return reserva o vacío; el servicio oculta también los recursos ajenos con 404
     */
    @EntityGraph(attributePaths = {"cliente", "barbero.usuario", "servicio"})
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> buscarDetalle(@Param("id") long id);

    /**
     * Carga ocupación futura RN-17 después del bloqueo ②; incluye todo estado salvo CANCELADA (MJ-03).
     * @param barberoId perfil bloqueado cuya agenda se valida
     * @param ahora instante del Clock; solo inicios estrictamente posteriores
     * @return reservas futuras ordenadas por inicio e id, sin tomar el bloqueo ③
     */
    @Query("select r from Reserva r where r.barbero.id = :barberoId and r.inicio > :ahora and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA order by r.inicio, r.id")
    List<Reserva> buscarFuturasQueOcupan(@Param("barberoId") Long barberoId, @Param("ahora") Instant ahora);

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

    /**
     * Lee propiedad sin cargar relaciones para autorizar excluirReservaId sin revelar reservas ajenas.
     * @param id identidad de la reserva solicitada
     * @return propietario o vacío cuando no existe la reserva
     */
    @Query("select r.cliente.id from Reserva r where r.id = :id")
    Optional<Long> buscarClienteId(@Param("id") long id);

    /**
     * Lee en una consulta ocupaciones MJ-03 del día de Lima para todos los perfiles RF-21.
     * @param ids perfiles seleccionados; colección no vacía
     * @param inicio medianoche inclusiva del día
     * @param fin medianoche exclusiva del siguiente día
     * @param excluirId reserva ya autorizada que se omite; cero no excluye ninguna
     * @return reservas de cualquier estado salvo CANCELADA que solapan el día
     */
    @Query("""
            select r from Reserva r where r.barbero.id in :ids
              and r.estado <> pe.barberturno.reservations.EstadoReserva.CANCELADA
              and r.inicio < :fin and r.fin > :inicio and r.id <> :excluirId
            order by r.barbero.id, r.inicio, r.id
            """)
    List<Reserva> buscarDia(@Param("ids") java.util.Collection<Long> ids,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin, @Param("excluirId") long excluirId);
}
