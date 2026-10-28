package pe.barberturno.whatsapp;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canales de WhatsApp con superficie mínima (V2-DA-26): sin CRUD heredado ni consultas sin negocio,
 * salvo la búsqueda por el número receptor, que es como el procesador identifica el negocio (V2-RN-02).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Transactional(readOnly = true)
public interface CanalWhatsAppRepository extends Repository<CanalWhatsApp, Long> {
    /**
     * Localiza el canal del número que recibió un mensaje.
     * @param phoneNumberId identificador del número receptor ({@code metadata.phone_number_id})
     * @return canal registrado con ese número, activo o desconectado, o vacío
     */
    Optional<CanalWhatsApp> findByPhoneNumberId(String phoneNumberId);

    /**
     * Lee el canal de un negocio para la plataforma.
     * @param negocioId negocio explícito
     * @return canal del negocio o vacío
     */
    Optional<CanalWhatsApp> findByNegocioId(long negocioId);

    /**
     * Bloquea el canal de un negocio antes de registrarlo o desconectarlo, para serializar cambios concurrentes.
     * @param negocioId negocio explícito
     * @return canal bloqueado o vacío
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CanalWhatsApp c where c.negocioId = :negocioId")
    Optional<CanalWhatsApp> bloquearPorNegocio(@Param("negocioId") long negocioId);

    /**
     * Inserta o actualiza un canal; los índices únicos imponen V2-RN-07.
     * @param canal canal nuevo o modificado
     * @return canal persistido
     */
    @Transactional
    CanalWhatsApp save(CanalWhatsApp canal);
}
