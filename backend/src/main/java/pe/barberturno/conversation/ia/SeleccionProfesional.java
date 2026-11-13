package pe.barberturno.conversation.ia;

import java.util.Objects;
import pe.barberturno.conversation.ProfesionalOpcion;

/**
 * Estado de la elección de profesional en un turno (17-v2-12 §13.4; consulta 138-1). Distingue «aún sin elegir» de
 * «cualquiera», que V2-10 representa con {@code barberoId = 0}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface SeleccionProfesional {
    /** El cliente aún no eligió profesional. */
    record SinElegir() implements SeleccionProfesional { }

    /** El cliente acepta a cualquier profesional del servicio. */
    record Cualquiera() implements SeleccionProfesional { }

    /**
     * Profesional concreto, validado bajo RLS del negocio.
     * @param profesional profesional activo del servicio
     */
    record Concreto(ProfesionalOpcion profesional) implements SeleccionProfesional {
        /**
         * Valida.
         * @param profesional profesional activo del servicio
         * @throws NullPointerException si es nulo
         */
        public Concreto {
            Objects.requireNonNull(profesional, "profesional");
        }
    }
}
