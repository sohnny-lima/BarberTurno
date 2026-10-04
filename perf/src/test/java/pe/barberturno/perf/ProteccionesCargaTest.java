package pe.barberturno.perf;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Comprueba que la herramienta no escriba otras bases ni oculte rechazos inesperados. */
class ProteccionesCargaTest {
    @Test
    void base_fueraDeLaDedicada_rechaza() {
        for (String url : new String[]{"jdbc:postgresql://localhost:5433/barberturno_test",
                "jdbc:postgresql://localhost:5433/barberturno", "jdbc:postgresql://remoto:5433/barberturno_perf",
                "jdbc:postgresql://localhost:5433/barberturno_perf?otra=opcion"}) {
            assertThrows(IllegalArgumentException.class, () -> DatosCarga.validarBase(url));
        }
        assertDoesNotThrow(() -> DatosCarga.validarBase("jdbc:postgresql://localhost:5433/barberturno_perf"));
    }

    @Test
    void rechazo_soloCombinacionesAutorizadas_sonEsperadas() {
        assertTrue(ResultadoReserva.esperado(409, "FRANJA_NO_DISPONIBLE"));
        assertTrue(ResultadoReserva.esperado(422, "LIMITE_RESERVAS_ACTIVAS"));
        assertFalse(ResultadoReserva.esperado(409, "CLIENTE_CON_RESERVA_SOLAPADA"));
        assertFalse(ResultadoReserva.esperado(422, "FUERA_DE_HORARIO"));
        assertFalse(ResultadoReserva.esperado(422, "FRANJA_NO_DISPONIBLE"));
        assertFalse(ResultadoReserva.esperado(500, "LIMITE_RESERVAS_ACTIVAS"));
        assertFalse(ResultadoReserva.esperado(409, null));
    }
}
