package pe.barberturno.platform;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Superficie mínima de plataforma: consultas con negocio explícito, sin CRUD heredado (V2-DA-26).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Transactional(readOnly = true)
public interface MembresiaRepository extends Repository<Membresia, Long> {
    /**
     * Lee el equipo histórico exclusivamente del negocio autorizado.
     * @param negocioId ámbito explícito de la ruta
     * @return pertenencias activas y revocadas ordenadas por id
     */
    java.util.List<Membresia> findByNegocioIdOrderByIdAsc(long negocioId);

    /**
     * Resuelve un recurso del equipo sin revelar pertenencias ajenas.
     * @param negocioId ámbito explícito de la ruta
     * @param id pertenencia solicitada
     * @return pertenencia local, si existe
     */
    Optional<Membresia> findByNegocioIdAndId(long negocioId, long id);
    /**
     * Lee exclusivamente las pertenencias activas de la cuenta autenticada (V2-DA-26, V2-05).
     * @param usuarioId identidad del principal, nunca un parámetro del navegador
     * @return pertenencias propias vigentes para construir mis-negocios
     */
    @Query("select m from Membresia m where m.usuarioId = :usuarioId and m.estado = 'ACTIVA'")
    java.util.List<Membresia> listarPropiasActivas(@Param("usuarioId") long usuarioId);

    /**
     * Busca la pertenencia sin mezclar negocios; puede estar revocada.
     * @param negocioId ámbito explícito de consulta
     * @param usuarioId identidad global buscada
     * @return pertenencia de esa identidad en ese negocio, si existe
     */
    Optional<Membresia> findByNegocioIdAndUsuarioId(long negocioId, long usuarioId);

    /**
     * Comprueba V2-RN-16 sin revelar los otros negocios ni sus pertenencias.
     * @param usuarioId identidad global evaluada
     * @param negocioId negocio que pretende modificar la identidad
     * @return si existe una pertenencia activa fuera del negocio indicado
     */
    @Query("select (count(m) > 0) from Membresia m where m.usuarioId = :usuarioId and m.negocioId <> :negocioId and m.estado = 'ACTIVA'")
    boolean existeActivaEnOtroNegocio(@Param("usuarioId") long usuarioId, @Param("negocioId") long negocioId);

    /**
     * Cuenta titulares con pertenencia y cuenta activas, tras tomar el bloqueo de V2-RN-06.
     * @param negocioId ámbito explícito del conteo
     * @return número de propietarios que conservan el acceso
     */
    @Query("select count(m) from Membresia m, Usuario u where m.usuarioId = u.id and m.negocioId = :negocioId and m.rol = pe.barberturno.platform.RolNegocio.PROPIETARIO and m.estado = 'ACTIVA' and u.activo = true")
    long contarPropietariosActivos(@Param("negocioId") long negocioId);

    /**
     * Elige cuentas activas de gestores y del profesional actual de la reserva, con membresía activa.
     * La reserva y el perfil se consultan bajo RLS y con negocio explícito; UNION evita avisos repetidos.
     * @param negocioId negocio del mensaje fallido
     * @param reservaId reserva local asociada al mensaje
     * @return identidades globales autorizadas, sin duplicados y ordenadas
     */
    @Query(value = """
            SELECT m.usuario_id FROM membresia m JOIN usuario u ON u.id = m.usuario_id
            WHERE m.negocio_id = :negocioId AND m.estado = 'ACTIVA' AND u.activo
                AND m.rol IN ('PROPIETARIO', 'ENCARGADO')
            UNION
            SELECT b.usuario_id FROM reserva r JOIN barbero b
                ON b.negocio_id = r.negocio_id AND b.id = r.barbero_id
                JOIN membresia m ON m.negocio_id = b.negocio_id AND m.usuario_id = b.usuario_id
                JOIN usuario u ON u.id = b.usuario_id
            WHERE r.negocio_id = :negocioId AND r.id = :reservaId AND m.estado = 'ACTIVA' AND u.activo
            ORDER BY 1
            """, nativeQuery = true)
    java.util.List<Long> destinatariosFalloWhatsApp(@Param("negocioId") long negocioId,
            @Param("reservaId") long reservaId);

    /**
     * Serializa cambios de propietarios antes de cualquier bloqueo de usuario (①).
     * @param negocioId ámbito que cabe en int4 según negocio_id_rango
     * @return centinela después de adquirir el bloqueo hasta fin de transacción
     */
    @Transactional
    @Query(value = "select 1 from pg_advisory_xact_lock(313131, cast(:negocioId as integer))", nativeQuery = true)
    int bloquearPropietarios(@Param("negocioId") long negocioId);

    /**
     * Persiste la pertenencia creada o modificada por un servicio autorizado.
     * @param membresia pertenencia con su negocio inmutable
     * @return pertenencia persistida
     */
    @Transactional
    Membresia save(Membresia membresia);
}
