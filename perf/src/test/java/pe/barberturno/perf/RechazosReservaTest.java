package pe.barberturno.perf;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Comprueba diagnóstico de errores HTTP y conteos concurrentes sin ocultar KO. */
class RechazosReservaTest {
    @Test
    void registrar_estadosYCodigosInesperados_conservaDesgloseExacto() {
        var rechazos = new RechazosReserva();
        rechazos.registrar(201, null);
        rechazos.registrar(409, "FRANJA_NO_DISPONIBLE");
        rechazos.registrar(422, "LIMITE_RESERVAS_ACTIVAS");
        rechazos.registrar(409, "CLIENTE_CON_RESERVA_SOLAPADA");
        rechazos.registrar(503, "RECURSO_OCUPADO");
        rechazos.registrar(500, "ERROR_INTERNO");
        rechazos.registrar(422, "FRANJA_NO_DISPONIBLE");
        rechazos.registrar(500, "LIMITE_RESERVAS_ACTIVAS");
        rechazos.registrar(403, null);
        rechazos.registrar(403, "");
        assertEquals(7, rechazos.total());
        assertEquals("estado\tcodigo\tcantidad\n"
                + "403\tsin%20cuerpo\t2\n"
                + "409\tCLIENTE_CON_RESERVA_SOLAPADA\t1\n"
                + "422\tFRANJA_NO_DISPONIBLE\t1\n"
                + "500\tERROR_INTERNO\t1\n"
                + "500\tLIMITE_RESERVAS_ACTIVAS\t1\n"
                + "503\tRECURSO_OCUPADO\t1\n", rechazos.resumen());
    }

    @Test
    void registrar_concurrencia_noPierdeRechazosNiMezclaEstados() {
        var rechazos = new RechazosReserva();
        IntStream.range(0, 10000).parallel().forEach(n -> rechazos.registrar(n % 2 == 0 ? 500 : 503, "ERROR"));
        assertEquals(10000, rechazos.total());
        assertEquals("estado\tcodigo\tcantidad\n500\tERROR\t5000\n503\tERROR\t5000\n", rechazos.resumen());
    }

    @Test
    void resumen_vacioOEscape_noInventaRechazosNiRompeFilas() {
        var rechazos = new RechazosReserva();
        assertEquals("estado\tcodigo\tcantidad\n", rechazos.resumen());
        rechazos.registrar(400, "código +\t\n");
        assertEquals("estado\tcodigo\tcantidad\n400\tc%C3%B3digo%20%2B%09%0A\t1\n", rechazos.resumen());
    }
}
