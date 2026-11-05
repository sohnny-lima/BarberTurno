package pe.barberturno.conversation.ia.evaluacion;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias para {@link EvaluacionEnVivo} (CI-12; 08-pruebas §4.2).
 * Comprueba que el arnés se niegue estrictamente a iniciar sin los prerrequisitos obligatorios
 * (clave de API, base desechable o tope presupuestario) y que la carga y deserialización
 * de los casos de evaluación del conjunto v1 funcione correctamente.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class EvaluacionEnVivoTest {

    @Test
    @DisplayName("verificarPrerrequisitos_sinClaveEnEntorno_seNiegaAEjecutar")
    void verificarPrerrequisitos_sinClaveEnEntorno_seNiegaAEjecutar() {
        // En el entorno de CI y desarrollo no existe la clave BT_IA_API_KEY
        assertThatThrownBy(EvaluacionEnVivo::verificarPrerrequisitos)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BT_IA_API_KEY");
    }

    @Test
    @DisplayName("cargarConjuntoCasos_particionAjuste_cargaCasosCorrectamente")
    void cargarConjuntoCasos_particionAjuste_cargaCasosCorrectamente() {
        List<Correctores.CasoEvaluacion> casos = EvaluacionEnVivo.cargarConjuntoCasos("AJUSTE");

        assertThat(casos)
                .as("El conjunto de ajuste debe contener casos")
                .isNotEmpty();

        assertThat(casos.stream().allMatch(c -> c.particion().equalsIgnoreCase("AJUSTE")))
                .as("Todos los casos filtrados deben pertenecer a AJUSTE")
                .isTrue();

        assertThat(casos.stream().allMatch(c -> !c.mensajesCliente().isEmpty()))
                .as("Cada caso debe contener al menos un mensaje del cliente")
                .isTrue();
    }

    @Test
    @DisplayName("cargarConjuntoCasos_particionPrueba_cargaCasosCorrectamente")
    void cargarConjuntoCasos_particionPrueba_cargaCasosCorrectamente() {
        List<Correctores.CasoEvaluacion> casos = EvaluacionEnVivo.cargarConjuntoCasos("PRUEBA");

        assertThat(casos)
                .as("El conjunto de prueba debe contener casos")
                .isNotEmpty();

        assertThat(casos.stream().allMatch(c -> c.particion().equalsIgnoreCase("PRUEBA")))
                .as("Todos los casos filtrados deben pertenecer a PRUEBA")
                .isTrue();
    }

    @Test
    @DisplayName("obtenerUrlBaseDatos_detectaPorDefectoBaseTest")
    void obtenerUrlBaseDatos_detectaPorDefectoBaseTest() {
        String url = EvaluacionEnVivo.obtenerUrlBaseDatos();
        assertThat(url).contains("barberturno_v2_test");
    }
}
