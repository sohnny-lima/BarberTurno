package pe.barberturno.conversation;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Identificador de un botón o de una fila de lista generado por el servidor (13-v2-10 §3.1). Cada
 * selección lleva todo lo que el paso siguiente necesita, así el estado de la conversación no guarda
 * servicio, profesional ni fecha; pero el identificador vuelve del cliente y **no es de confianza**:
 * quien lo usa revalida el negocio (RLS), la ficha, el estado y las reglas.
 *
 * <p>Formatos (como máximo 200 caracteres; ids de 1 a 18 cifras, sin ceros a la izquierda y mayores que
 * cero salvo {@code bid = 0} = cualquiera; fecha {@code aaaammdd} real; página de 0 a 9):
 * {@code m:RESERVAR|MIS_CITAS|INICIO} · {@code p:S|N} · {@code sp:<pág>} · {@code s:<sid>} ·
 * {@code bp:<sid>:<pág>} · {@code b:<sid>:<bid>} · {@code d:<sid>:<bid>:<aaaammdd>:<pág>} ·
 * {@code f:<sid>:<bid>:<seg>} ({@code bid > 0}) · {@code c:<rid>} · {@code a:<rid>:C|R} ·
 * {@code rd:<rid>:<aaaammdd>:<pág>} · {@code rf:<rid>:<seg>} · {@code o:<oid>:<nonce>:S|N}
 * ({@code nonce} base64url sin relleno de 22 caracteres).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface CodigoSeleccion {

    /** Longitud máxima de un identificador (límite de Meta para filas de lista). */
    int LONGITUD_MAXIMA = 200;

    /**
     * Representa la selección como identificador de botón o fila. Implementación: Gemini (bloque G).
     * @return identificador con el formato de la tabla de la interfaz
     */
    default String codificar() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Decodifica un identificador recibido. Cualquier desviación del formato devuelve vacío, nunca una
     * excepción. Implementación: Gemini (bloque G).
     * @param id identificador del botón o la fila; puede ser nulo
     * @return selección decodificada, o vacío si no cumple exactamente un formato
     */
    static Optional<CodigoSeleccion> decodificar(String id) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Opción del menú principal.
     * @param opcion opción elegida
     */
    record Menu(OpcionMenu opcion) implements CodigoSeleccion {
        /**
         * Exige la opción.
         * @param opcion opción; no nula
         * @throws NullPointerException si falta
         */
        public Menu {
            Objects.requireNonNull(opcion, "opcion");
        }
    }

    /**
     * Respuesta al aviso de privacidad.
     * @param acepta verdadero para [Aceptar], falso para [No, gracias]
     */
    record Privacidad(boolean acepta) implements CodigoSeleccion { }

    /**
     * Página de la lista de servicios.
     * @param pagina página de 0 a 9
     */
    record PaginaServicios(int pagina) implements CodigoSeleccion { }

    /**
     * Servicio elegido.
     * @param servicioId servicio
     */
    record Servicio(long servicioId) implements CodigoSeleccion { }

    /**
     * Página de la lista de profesionales de un servicio.
     * @param servicioId servicio elegido
     * @param pagina página de 0 a 9
     */
    record PaginaProfesionales(long servicioId, int pagina) implements CodigoSeleccion { }

    /**
     * Profesional elegido para un servicio.
     * @param servicioId servicio elegido
     * @param barberoId profesional, o 0 para «Cualquiera»
     */
    record Profesional(long servicioId, long barberoId) implements CodigoSeleccion { }

    /**
     * Día elegido y página de sus franjas.
     * @param servicioId servicio elegido
     * @param barberoId profesional, o 0 para «Cualquiera»
     * @param dia fecha en Lima
     * @param pagina página de franjas de 0 a 9
     */
    record Dia(long servicioId, long barberoId, LocalDate dia, int pagina) implements CodigoSeleccion {
        /**
         * Exige la fecha.
         * @param servicioId servicio elegido
         * @param barberoId profesional o 0
         * @param dia fecha; no nula
         * @param pagina página
         * @throws NullPointerException si falta la fecha
         */
        public Dia {
            Objects.requireNonNull(dia, "dia");
        }
    }

    /**
     * Franja elegida para reservar.
     * @param servicioId servicio elegido
     * @param barberoId profesional concreto de la franja (mayor que cero)
     * @param inicioEpochSeg inicio en segundos desde la época
     */
    record Franja(long servicioId, long barberoId, long inicioEpochSeg) implements CodigoSeleccion { }

    /**
     * Cita elegida en «Mis citas».
     * @param reservaId reserva
     */
    record Cita(long reservaId) implements CodigoSeleccion { }

    /**
     * Acción elegida sobre una cita.
     * @param reservaId reserva
     * @param cancelar verdadero para cancelar, falso para reprogramar
     */
    record AccionCita(long reservaId, boolean cancelar) implements CodigoSeleccion { }

    /**
     * Día elegido para reprogramar una cita con el mismo profesional y servicio.
     * @param reservaId reserva
     * @param dia fecha en Lima
     * @param pagina página de franjas de 0 a 9
     */
    record DiaCambio(long reservaId, LocalDate dia, int pagina) implements CodigoSeleccion {
        /**
         * Exige la fecha.
         * @param reservaId reserva
         * @param dia fecha; no nula
         * @param pagina página
         * @throws NullPointerException si falta la fecha
         */
        public DiaCambio {
            Objects.requireNonNull(dia, "dia");
        }
    }

    /**
     * Franja nueva elegida para reprogramar.
     * @param reservaId reserva
     * @param inicioEpochSeg nuevo inicio en segundos desde la época
     */
    record FranjaCambio(long reservaId, long inicioEpochSeg) implements CodigoSeleccion { }

    /**
     * Respuesta al resumen de una operación pendiente (V2-RN-10).
     * @param operacionId operación pendiente
     * @param nonce nonce en base64url sin relleno, 22 caracteres
     * @param confirma verdadero para [Confirmar], falso para [Cambiar]
     */
    record Confirmacion(long operacionId, String nonce, boolean confirma) implements CodigoSeleccion {
        /**
         * Exige el nonce.
         * @param operacionId operación
         * @param nonce nonce; no nulo
         * @param confirma decisión
         * @throws NullPointerException si falta el nonce
         */
        public Confirmacion {
            Objects.requireNonNull(nonce, "nonce");
        }

        /**
         * Oculta el nonce: un registro de la selección no debe permitir reutilizarlo.
         * @return representación sin el nonce
         */
        @Override
        public String toString() {
            return "Confirmacion[operacionId=" + operacionId + ", confirma=" + confirma + "]";
        }
    }
}
