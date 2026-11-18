package pe.barberturno.conversation.ia;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InstruccionesAsistenteTest {
    @Test void instrucciones_neutralesRespectoALaSede_conservanHoraDeLima() {
        assertThat(new InstruccionesAsistente().texto()).contains("de una barbería.", "fecha y hora de Lima")
                .doesNotContain("Huamanga", "(Perú)");
    }
}
