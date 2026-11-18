package pe.barberturno.conversation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de interpretación determinista de mensajes en {@link InterpreteEntradas}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class InterpreteEntradasTest {

    private final InterpreteEntradas interprete = new InterpreteEntradas();

    @Test
    @DisplayName("Mensaje interactivo con button_reply decodifica a Entrada.Seleccion")
    void interpretar_interactivoButtonReply_devuelveSeleccion() {
        ObjectNode dato = JsonNodeFactory.instance.objectNode();
        dato.putObject("interactive")
                .putObject("button_reply")
                .put("id", "m:RESERVAR")
                .put("title", "Reservar");

        Entrada resultado = interprete.interpretar("interactive", dato);

        assertThat(resultado).isInstanceOf(Entrada.Seleccion.class);
        Entrada.Seleccion seleccion = (Entrada.Seleccion) resultado;
        assertThat(seleccion.codigo()).isEqualTo(new CodigoSeleccion.Menu(OpcionMenu.RESERVAR));
    }

    @Test
    @DisplayName("Mensaje interactivo con list_reply decodifica a Entrada.Seleccion")
    void interpretar_interactivoListReply_devuelveSeleccion() {
        ObjectNode dato = JsonNodeFactory.instance.objectNode();
        dato.putObject("interactive")
                .putObject("list_reply")
                .put("id", "s:123")
                .put("title", "Corte clásico");

        Entrada resultado = interprete.interpretar("interactive", dato);

        assertThat(resultado).isInstanceOf(Entrada.Seleccion.class);
        Entrada.Seleccion seleccion = (Entrada.Seleccion) resultado;
        assertThat(seleccion.codigo()).isEqualTo(new CodigoSeleccion.Servicio(123L));
    }

    @Test
    @DisplayName("Mensaje tipo button con payload decodifica a Entrada.Seleccion")
    void interpretar_botonConPayload_devuelveSeleccion() {
        ObjectNode dato = JsonNodeFactory.instance.objectNode();
        dato.putObject("button")
                .put("payload", "p:S")
                .put("text", "Aceptar");

        Entrada resultado = interprete.interpretar("button", dato);

        assertThat(resultado).isInstanceOf(Entrada.Seleccion.class);
        Entrada.Seleccion seleccion = (Entrada.Seleccion) resultado;
        assertThat(seleccion.codigo()).isEqualTo(new CodigoSeleccion.Privacidad(true));
    }

    @Test
    @DisplayName("Mensaje de texto reduce el body con PalabraClave a Entrada.Texto")
    void interpretar_textoConBody_devuelveTextoConPalabraClave() {
        ObjectNode datoHola = JsonNodeFactory.instance.objectNode();
        datoHola.putObject("text").put("body", "¡Hola!");

        ObjectNode datoSi = JsonNodeFactory.instance.objectNode();
        datoSi.putObject("text").put("body", "Sí.");

        ObjectNode datoOtra = JsonNodeFactory.instance.objectNode();
        datoOtra.putObject("text").put("body", "Quisiera información");

        assertThat(interprete.interpretar("text", datoHola))
                .isEqualTo(new Entrada.Texto(PalabraClave.MENU));
        assertThat(interprete.interpretar("text", datoSi))
                .isEqualTo(new Entrada.Texto(PalabraClave.SI));
        assertThat(interprete.interpretar("text", datoOtra))
                .isEqualTo(new Entrada.Texto(PalabraClave.OTRA));
    }

    @Test
    @DisplayName("Identificador inválido en interactive o button devuelve Entrada.NoSoportada")
    void interpretar_identificadorInvalido_devuelveNoSoportada() {
        ObjectNode datoInteractive = JsonNodeFactory.instance.objectNode();
        datoInteractive.putObject("interactive").putObject("button_reply").put("id", "s:invalido");

        ObjectNode datoButton = JsonNodeFactory.instance.objectNode();
        datoButton.putObject("button").put("payload", "prefijo_desconocido:1");

        assertThat(interprete.interpretar("interactive", datoInteractive))
                .isEqualTo(new Entrada.NoSoportada("interactive"));
        assertThat(interprete.interpretar("button", datoButton))
                .isEqualTo(new Entrada.NoSoportada("button"));
    }

    @Test
    @DisplayName("Tipos no soportados (image, audio, location) devuelven Entrada.NoSoportada con su tipo")
    void interpretar_tiposNoSoportados_devuelveNoSoportadaConTipo() {
        ObjectNode datoImg = JsonNodeFactory.instance.objectNode();
        datoImg.putObject("image").put("id", "img123");

        ObjectNode datoAudio = JsonNodeFactory.instance.objectNode();
        datoAudio.putObject("audio").put("id", "aud123");

        ObjectNode datoLoc = JsonNodeFactory.instance.objectNode();
        datoLoc.putObject("location").put("latitude", -13.16);

        assertThat(interprete.interpretar("image", datoImg))
                .isEqualTo(new Entrada.NoSoportada("image"));
        assertThat(interprete.interpretar("audio", datoAudio))
                .isEqualTo(new Entrada.NoSoportada("audio"));
        assertThat(interprete.interpretar("location", datoLoc))
                .isEqualTo(new Entrada.NoSoportada("location"));
    }

    @Test
    @DisplayName("Estructuras rotas (interactive sin id, text sin body, dato vacío) devuelven Entrada.NoSoportada")
    void interpretar_estructurasRotas_devuelveNoSoportada() {
        ObjectNode interSinId = JsonNodeFactory.instance.objectNode();
        interSinId.putObject("interactive").putObject("button_reply").put("title", "Sin id");

        ObjectNode textSinBody = JsonNodeFactory.instance.objectNode();
        textSinBody.putObject("text");

        ObjectNode datoVacio = JsonNodeFactory.instance.objectNode();

        assertThat(interprete.interpretar("interactive", interSinId))
                .isEqualTo(new Entrada.NoSoportada("interactive"));
        assertThat(interprete.interpretar("text", textSinBody))
                .isEqualTo(new Entrada.NoSoportada("text"));
        assertThat(interprete.interpretar("text", datoVacio))
                .isEqualTo(new Entrada.NoSoportada("text"));
        assertThat(interprete.interpretar("interactive", datoVacio))
                .isEqualTo(new Entrada.NoSoportada("interactive"));
        assertThat(interprete.interpretar("button", datoVacio))
                .isEqualTo(new Entrada.NoSoportada("button"));
    }

    @Test
    @DisplayName("Tipo o dato nulo devuelven Entrada.NoSoportada sin lanzar excepción")
    void interpretar_nulos_devuelveNoSoportada() {
        assertThat(interprete.interpretar(null, null)).isInstanceOf(Entrada.NoSoportada.class);
        assertThat(interprete.interpretar("text", null)).isInstanceOf(Entrada.NoSoportada.class);
        assertThat(interprete.interpretar(null, JsonNodeFactory.instance.objectNode())).isInstanceOf(Entrada.NoSoportada.class);
    }
}
