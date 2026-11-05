package pe.barberturno.conversation.ia;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import pe.barberturno.conversation.CitaOpcion;
import pe.barberturno.conversation.ProfesionalOpcion;
import pe.barberturno.conversation.ServicioOpcion;

/**
 * Datos del servidor con los que se compone la respuesta de un turno (17-v2-12 §13.2.4 y §13.4). Los carga
 * {@code TurnosIAService} en la transacción final bajo RLS; las citas, solo con la ficha revalidada de la
 * conversación. Ningún campo procede del texto del modelo.
 * @param negocio nombre del negocio
 * @param hoy fecha actual en Lima
 * @param servicio servicio elegido, si lo hay
 * @param profesional estado de la elección de profesional
 * @param servicios servicios activos del catálogo
 * @param profesionales profesionales activos del servicio elegido (vacía si no hay servicio)
 * @param citas citas futuras de la ficha (vacía sin ficha)
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record DatosRespuesta(String negocio, LocalDate hoy, Optional<ServicioOpcion> servicio,
        SeleccionProfesional profesional, List<ServicioOpcion> servicios, List<ProfesionalOpcion> profesionales,
        List<CitaOpcion> citas) {
    /**
     * Valida y copia las listas.
     * @param negocio nombre del negocio
     * @param hoy fecha actual en Lima
     * @param servicio servicio elegido
     * @param profesional elección de profesional
     * @param servicios servicios activos
     * @param profesionales profesionales activos del servicio
     * @param citas citas futuras de la ficha
     * @throws NullPointerException si falta algún valor
     */
    public DatosRespuesta {
        Objects.requireNonNull(negocio, "negocio");
        Objects.requireNonNull(hoy, "hoy");
        Objects.requireNonNull(servicio, "servicio");
        Objects.requireNonNull(profesional, "profesional");
        servicios = List.copyOf(servicios);
        profesionales = List.copyOf(profesionales);
        citas = List.copyOf(citas);
    }
}
