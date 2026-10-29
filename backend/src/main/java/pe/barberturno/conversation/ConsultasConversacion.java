package pe.barberturno.conversation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Lecturas de la conversación guiada dentro de la transacción y el contexto del negocio de la entrega
 * (13-v2-10 §3.3). Solo devuelve datos del negocio actual (RLS) y nunca citas de otra ficha.
 * Implementación: Codex (bloque X), con los servicios existentes.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ConsultasConversacion {

    /**
     * Nombre visible del negocio actual, para los textos.
     * @return nombre del negocio
     */
    String nombreNegocio();

    /**
     * Servicios activos del negocio, ordenados por nombre.
     * @return servicios activos; vacío si no hay
     */
    List<ServicioOpcion> servicios();

    /**
     * Un servicio activo del negocio.
     * @param servicioId servicio pedido por el identificador de una selección
     * @return el servicio, o vacío si no existe en el negocio o está inactivo
     */
    Optional<ServicioOpcion> servicio(long servicioId);

    /**
     * Profesionales activos que pueden atender el servicio, ordenados por nombre.
     * @param servicioId servicio elegido
     * @return profesionales; vacío si el servicio no existe o está inactivo
     */
    List<ProfesionalOpcion> profesionales(long servicioId);

    /**
     * Franjas libres de un día con las reglas de la web ({@code DisponibilidadService}).
     * @param servicioId servicio elegido
     * @param barberoId profesional, o vacío para «Cualquiera»
     * @param dia fecha en Lima
     * @param excluirReservaId reserva que se reprograma, cuyo hueco no cuenta como ocupado (DA-21)
     * @return franjas ordenadas por inicio y profesional; vacío si no hay o algún dato no es válido
     */
    List<FranjaOpcion> franjas(long servicioId, OptionalLong barberoId, LocalDate dia, OptionalLong excluirReservaId);

    /**
     * Citas futuras de la ficha (las que todavía ocupan franja), con sus permisos.
     * @param fichaId ficha de la conversación
     * @return citas ordenadas por inicio, como máximo 10
     */
    List<CitaOpcion> citasFuturas(long fichaId);

    /**
     * Una cita de la ficha.
     * @param fichaId ficha de la conversación
     * @param reservaId reserva pedida por el identificador de una selección
     * @return la cita, o vacío si no existe en el negocio o no es de esa ficha
     */
    Optional<CitaOpcion> cita(long fichaId, long reservaId);
}
