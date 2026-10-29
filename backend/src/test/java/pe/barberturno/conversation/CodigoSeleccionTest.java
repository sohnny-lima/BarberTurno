package pe.barberturno.conversation;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de codificación y decodificación de {@link CodigoSeleccion}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class CodigoSeleccionTest {

    @Test
    @DisplayName("Ida y vuelta de los 13 formatos de selección")
    void codificarYDecodificar_treceFormatos_idaYVueltaExacta() {
        // 1. Menu
        CodigoSeleccion m1 = new CodigoSeleccion.Menu(OpcionMenu.RESERVAR);
        CodigoSeleccion m2 = new CodigoSeleccion.Menu(OpcionMenu.MIS_CITAS);
        CodigoSeleccion m3 = new CodigoSeleccion.Menu(OpcionMenu.INICIO);
        assertThat(CodigoSeleccion.decodificar(m1.codificar())).contains(m1);
        assertThat(CodigoSeleccion.decodificar(m2.codificar())).contains(m2);
        assertThat(CodigoSeleccion.decodificar(m3.codificar())).contains(m3);

        // 2. Privacidad
        CodigoSeleccion p1 = new CodigoSeleccion.Privacidad(true);
        CodigoSeleccion p2 = new CodigoSeleccion.Privacidad(false);
        assertThat(CodigoSeleccion.decodificar(p1.codificar())).contains(p1);
        assertThat(CodigoSeleccion.decodificar(p2.codificar())).contains(p2);

        // 3. PaginaServicios
        CodigoSeleccion sp0 = new CodigoSeleccion.PaginaServicios(0);
        CodigoSeleccion sp9 = new CodigoSeleccion.PaginaServicios(9);
        assertThat(CodigoSeleccion.decodificar(sp0.codificar())).contains(sp0);
        assertThat(CodigoSeleccion.decodificar(sp9.codificar())).contains(sp9);

        // 4. Servicio
        CodigoSeleccion s = new CodigoSeleccion.Servicio(101L);
        CodigoSeleccion sMax = new CodigoSeleccion.Servicio(999999999999999999L);
        assertThat(CodigoSeleccion.decodificar(s.codificar())).contains(s);
        assertThat(CodigoSeleccion.decodificar(sMax.codificar())).contains(sMax);

        // 5. PaginaProfesionales
        CodigoSeleccion bp = new CodigoSeleccion.PaginaProfesionales(101L, 4);
        assertThat(CodigoSeleccion.decodificar(bp.codificar())).contains(bp);

        // 6. Profesional (con bid = 0 y bid > 0)
        CodigoSeleccion b0 = new CodigoSeleccion.Profesional(101L, 0L);
        CodigoSeleccion b1 = new CodigoSeleccion.Profesional(101L, 202L);
        assertThat(CodigoSeleccion.decodificar(b0.codificar())).contains(b0);
        assertThat(CodigoSeleccion.decodificar(b1.codificar())).contains(b1);

        // 7. Dia (con bid = 0 y bid > 0)
        CodigoSeleccion d0 = new CodigoSeleccion.Dia(101L, 0L, LocalDate.of(2026, 10, 23), 0);
        CodigoSeleccion d1 = new CodigoSeleccion.Dia(101L, 202L, LocalDate.of(2026, 12, 31), 9);
        assertThat(CodigoSeleccion.decodificar(d0.codificar())).contains(d0);
        assertThat(CodigoSeleccion.decodificar(d1.codificar())).contains(d1);

        // 8. Franja (bid > 0)
        CodigoSeleccion f = new CodigoSeleccion.Franja(101L, 202L, 1770000000L);
        assertThat(CodigoSeleccion.decodificar(f.codificar())).contains(f);

        // 9. Cita
        CodigoSeleccion c = new CodigoSeleccion.Cita(303L);
        assertThat(CodigoSeleccion.decodificar(c.codificar())).contains(c);

        // 10. AccionCita (cancelar true / false)
        CodigoSeleccion aC = new CodigoSeleccion.AccionCita(303L, true);
        CodigoSeleccion aR = new CodigoSeleccion.AccionCita(303L, false);
        assertThat(CodigoSeleccion.decodificar(aC.codificar())).contains(aC);
        assertThat(CodigoSeleccion.decodificar(aR.codificar())).contains(aR);

        // 11. DiaCambio
        CodigoSeleccion rd = new CodigoSeleccion.DiaCambio(303L, LocalDate.of(2026, 10, 24), 2);
        assertThat(CodigoSeleccion.decodificar(rd.codificar())).contains(rd);

        // 12. FranjaCambio
        CodigoSeleccion rf = new CodigoSeleccion.FranjaCambio(303L, 1770003600L);
        assertThat(CodigoSeleccion.decodificar(rf.codificar())).contains(rf);

        // 13. Confirmacion (confirma true / false)
        CodigoSeleccion oS = new CodigoSeleccion.Confirmacion(404L, "abcdefghijklmnopqrstuv", true);
        CodigoSeleccion oN = new CodigoSeleccion.Confirmacion(404L, "1234567890_-ABCDEFGHIJ", false);
        assertThat(CodigoSeleccion.decodificar(oS.codificar())).contains(oS);
        assertThat(CodigoSeleccion.decodificar(oN.codificar())).contains(oN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Ceros a la izquierda
            "s:01", "b:01:2", "b:1:02", "c:05", "f:01:2:1770000000", "f:1:02:1770000000", "f:1:2:01770000000",
            "o:01:abcdefghijklmnopqrstuv:S", "rd:01:20261023:0", "rf:01:1770000000",
            // bid = 0 donde no se admite (en f:)
            "f:101:0:1770000000",
            // id = 0 donde no se admite
            "s:0", "c:0", "a:0:C", "rd:0:20261023:0", "rf:0:1770000000", "o:0:abcdefghijklmnopqrstuv:S",
            // Fecha imposible (estricta)
            "d:101:202:20261302:0", "d:101:202:20260230:0", "d:101:202:20260431:0", "d:101:202:20260010:0",
            "rd:303:20261302:0", "rd:303:20260230:0", "rd:303:20260431:0",
            // Página 10 o fuera de 0-9
            "sp:10", "sp:-1", "bp:101:10", "d:101:202:20261023:10", "rd:303:20261023:10",
            // Nonce inválido: longitud 21 o 23, o con caracteres no base64url (+, /, =)
            "o:404:abcdefghijklmnopqrstu:S", "o:404:abcdefghijklmnopqrstuvw:S",
            "o:404:abcdefghijklmnopqr+tuv:S", "o:404:abcdefghijklmnopqr/tuv:S", "o:404:abcdefghijklmnopqr=tuv:S",
            // Signos (+ o -)
            "s:+1", "s:-1", "b:1:-2", "b:+1:2", "c:+5", "f:1:2:-1770000000",
            // 19 cifras (desbordamiento)
            "s:1234567890123456789", "c:1234567890123456789", "b:1234567890123456789:1",
            // Separador final o extra
            "s:101:", "m:RESERVAR:", "b:1:2:", "d:1:2:20261023:0:", "o:404:abcdefghijklmnopqrstuv:S:",
            "s::101", "b:1::2",
            // Prefijo desconocido
            "x:101", "menu:RESERVAR", "reserva:101", "accion:C",
            // Mayúsculas cambiadas / minúsculas donde se exigen mayúsculas
            "m:reservar", "m:mis_citas", "p:s", "p:n", "a:303:c", "a:303:r", "o:404:abcdefghijklmnopqrstuv:s",
            // Espacios o basura
            " s:101", "s:101 ", "s: 101", "m: RESERVAR", "d:1:0:20261023:0 ",
            ""
    })
    @DisplayName("Identificadores inválidos devuelven Optional.empty()")
    void decodificar_identificadoresInvalidos_devuelveVacio(String entradaInvalida) {
        assertThat(CodigoSeleccion.decodificar(entradaInvalida)).isEmpty();
    }

    @Test
    @DisplayName("Identificador nulo devuelve Optional.empty()")
    void decodificar_nulo_devuelveVacio() {
        assertThat(CodigoSeleccion.decodificar(null)).isEmpty();
    }

    @Test
    @DisplayName("Identificador con longitud superior a 200 devuelve Optional.empty()")
    void decodificar_longitudMayorA200_devuelveVacio() {
        String idLargo = "m:RESERVAR" + "a".repeat(195);
        assertThat(idLargo.length()).isGreaterThan(CodigoSeleccion.LONGITUD_MAXIMA);
        assertThat(CodigoSeleccion.decodificar(idLargo)).isEmpty();
    }

    @Test
    @DisplayName("Confirmacion.toString no contiene el nonce para evitar reutilizaciones")
    void confirmacionToString_noContieneNonce() {
        String nonceSecreto = "secreto-1234567890_abc";
        CodigoSeleccion.Confirmacion confirmacion = new CodigoSeleccion.Confirmacion(505L, nonceSecreto, true);

        String representacion = confirmacion.toString();

        assertThat(representacion).doesNotContain(nonceSecreto);
        assertThat(representacion).contains("505").contains("true");
    }
}
