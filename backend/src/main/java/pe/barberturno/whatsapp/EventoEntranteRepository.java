package pe.barberturno.whatsapp;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserción idempotente de la cola de entrada con superficie mínima (V2-DA-26): el webhook solo inserta;
 * el reclamo y los cambios de estado son del procesador. La idempotencia está en la base (V2-DA-15).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Transactional(readOnly = true)
public interface EventoEntranteRepository extends Repository<EventoEntrante, Long> {
    /**
     * Inserta un evento pendiente si su clave no existe; un reintento o un duplicado de Meta no crea otra fila.
     * @param clave clave de idempotencia del mensaje o del aviso de estado
     * @param tipo {@code MENSAJE} o {@code ESTADO}
     * @param phoneNumberId número receptor, sin resolver aún el negocio
     * @param cuerpo JSON con el formato de 11-v2-08-whatsapp-entrada.md §4.2
     * @param ahora instante de recepción y primer intento, del reloj inyectado
     * @return 1 si se insertó, 0 si la clave ya existía
     */
    @Transactional
    @Modifying
    @Query(value = """
            insert into evento_whatsapp_entrante
                (clave_idempotencia, tipo, phone_number_id, cuerpo, estado, intentos, proximo_intento, recibido_en)
            values (:clave, :tipo, :phoneNumberId, cast(:cuerpo as jsonb), 'PENDIENTE', 0, :ahora, :ahora)
            on conflict (clave_idempotencia) do nothing
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("clave") String clave, @Param("tipo") String tipo,
            @Param("phoneNumberId") String phoneNumberId, @Param("cuerpo") String cuerpo, @Param("ahora") Instant ahora);
}
