package pe.barberturno.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;

/**
 * V2-DA-109: con WhatsApp apagado en el despliegue, las tres operaciones del vínculo responden 404
 * antes de tocar el servicio, y la agenda oculta el panel.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class VinculoProfesionalApagadoTest {

    /** Sin servicio: si el controlador lo usara, la prueba fallaría con NullPointerException. */
    private final VinculoProfesionalWhatsAppController controlador = new VinculoProfesionalWhatsAppController(null, false);

    @Test
    void estado_conWhatsAppApagado_respondeNoEncontrado() {
        assertThatThrownBy(() -> controlador.estado(null)).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test
    void generar_conWhatsAppApagado_respondeNoEncontrado() {
        assertThatThrownBy(() -> controlador.generar(null)).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test
    void desvincular_conWhatsAppApagado_respondeNoEncontrado() {
        assertThatThrownBy(() -> controlador.desvincular(null)).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }
}
