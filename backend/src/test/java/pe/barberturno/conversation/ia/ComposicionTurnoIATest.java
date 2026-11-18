package pe.barberturno.conversation.ia;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.CodigoSeleccion;
import pe.barberturno.conversation.TextosConversacion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias para {@link ComposicionTurnoIA} que verifican la forma exacta del JSON interactivo,
 * la agrupación por día con títulos de sección cortos («vie 23/10»), descripciones con fecha completa
 * y profesional («viernes 23 de octubre de 2026 · Carlos»), recorte selectivo preservando la fecha,
 * el límite de 10 filas, la codificación de identificadores y los textos fijos de respuesta del servidor.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class ComposicionTurnoIATest {

    private final ComposicionTurnoIA composicion = new ComposicionTurnoIA();
    private final TextosConversacion textos = new TextosConversacion();

    private final LocalDate hoy = LocalDate.of(2026, 10, 23);

    private final Instant f1Inicio =
            ZonedDateTime.of(2026, 10, 23, 10, 0, 0, 0, TiempoNegocio.ZONA).toInstant();
    private final Instant f1Fin =
            ZonedDateTime.of(2026, 10, 23, 10, 30, 0, 0, TiempoNegocio.ZONA).toInstant();

    private final Instant f2Inicio =
            ZonedDateTime.of(2026, 10, 23, 11, 0, 0, 0, TiempoNegocio.ZONA).toInstant();
    private final Instant f2Fin =
            ZonedDateTime.of(2026, 10, 23, 11, 30, 0, 0, TiempoNegocio.ZONA).toInstant();

    private final CodigoSeleccion sel1 =
            new CodigoSeleccion.Franja(1L, 2L, f1Inicio.getEpochSecond());
    private final CodigoSeleccion sel2 =
            new CodigoSeleccion.Franja(1L, 3L, f2Inicio.getEpochSecond());

    private final FranjaOfrecida franja1 =
            new FranjaOfrecida("f1", f1Inicio, f1Fin, "Carlos", sel1);
    private final FranjaOfrecida franja2 =
            new FranjaOfrecida("f2", f2Inicio, f2Fin, "Mateo", sel2);

    @Test
    @DisplayName("franjas: forma exacta del JSON interactivo con una sección")
    void franjas_casoNormal_formaExactaJson() {
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Horarios disponibles para tu corte:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver horarios");
        ArrayNode sections = action.putArray("sections");
        ObjectNode section = sections.addObject();
        section.put("title", "vie 23/10");
        ArrayNode rows = section.putArray("rows");

        ObjectNode row1 = rows.addObject();
        row1.put("id", sel1.codificar());
        row1.put("title", "10:00");
        row1.put("description", "viernes 23 de octubre de 2026 · Carlos");

        ObjectNode row2 = rows.addObject();
        row2.put("id", sel2.codificar());
        row2.put("title", "11:00");
        row2.put("description", "viernes 23 de octubre de 2026 · Mateo");

        ObjectNode actual = composicion.franjas(
                "Horarios disponibles para tu corte:",
                List.of(franja1, franja2),
                hoy
        );

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("franjas: agrupación de filas por día en secciones independientes con títulos cortos")
    void franjas_variosDias_agrupaPorDia() {
        LocalDate manana = hoy.plusDays(1);
        Instant f3Inicio = ZonedDateTime.of(2026, 10, 24, 15, 0, 0, 0, TiempoNegocio.ZONA).toInstant();
        Instant f3Fin = ZonedDateTime.of(2026, 10, 24, 15, 30, 0, 0, TiempoNegocio.ZONA).toInstant();
        CodigoSeleccion sel3 = new CodigoSeleccion.Franja(1L, 2L, f3Inicio.getEpochSecond());
        FranjaOfrecida franja3 = new FranjaOfrecida("f3", f3Inicio, f3Fin, "Carlos", sel3);

        ObjectNode actual = composicion.franjas(
                "Opciones:",
                List.of(franja1, franja2, franja3),
                hoy
        );

        ArrayNode sections = (ArrayNode) actual.path("action").path("sections");
        assertThat(sections).hasSize(2);

        JsonNode sec1 = sections.get(0);
        assertThat(sec1.path("title").asString()).isEqualTo("vie 23/10");
        assertThat(sec1.path("rows")).hasSize(2);
        assertThat(sec1.path("rows").get(0).path("description").asString())
                .isEqualTo("viernes 23 de octubre de 2026 · Carlos");

        JsonNode sec2 = sections.get(1);
        assertThat(sec2.path("title").asString()).isEqualTo("sáb 24/10");
        assertThat(sec2.path("rows")).hasSize(1);
        assertThat(sec2.path("rows").get(0).path("id").asString()).isEqualTo(sel3.codificar());
        assertThat(sec2.path("rows").get(0).path("description").asString())
                .isEqualTo("sábado 24 de octubre de 2026 · Carlos");
    }

    @Test
    @DisplayName("franjas: límite estricto de 10 filas en total aunque se reciban más")
    void franjas_masDe10Franjas_limitaA10Filas() {
        List<FranjaOfrecida> muchas = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Instant ini = ZonedDateTime.of(2026, 10, 23, 9, 0, 0, 0, TiempoNegocio.ZONA)
                    .plusMinutes(i * 30L).toInstant();
            Instant fin = ini.plusSeconds(1800);
            CodigoSeleccion sel = new CodigoSeleccion.Franja(1L, 2L, ini.getEpochSecond());
            muchas.add(new FranjaOfrecida("f" + (i + 1), ini, fin, "Carlos " + (i + 1), sel));
        }

        ObjectNode actual = composicion.franjas("Opciones:", muchas, hoy);

        ArrayNode sections = (ArrayNode) actual.path("action").path("sections");
        int totalFilas = 0;
        for (JsonNode sec : sections) {
            totalFilas += sec.path("rows").size();
        }
        assertThat(totalFilas).isEqualTo(10);
    }

    @Test
    @DisplayName("franjas: los ids de cada fila coinciden con franja.seleccion().codificar()")
    void franjas_idsFilas_coincidenConSeleccionCodificada() {
        ObjectNode actual = composicion.franjas("Opciones:", List.of(franja1, franja2), hoy);
        ArrayNode rows = (ArrayNode) actual.path("action").path("sections").get(0).path("rows");

        assertThat(rows.get(0).path("id").asString()).isEqualTo(sel1.codificar());
        assertThat(rows.get(1).path("id").asString()).isEqualTo(sel2.codificar());
    }

    @Test
    @DisplayName("franjas: recorta cuerpo a 1024 y descripción a 72 según los límites de Meta")
    void franjas_textosLargos_aplicaRecortesConElipsis() {
        String cuerpoMuyLargo = "A".repeat(1100);
        String profLargo = "Barbero Profesional Super Especializado Con Experiencia Internacional En Cortes";
        Instant ini = ZonedDateTime.of(2026, 10, 23, 16, 0, 0, 0, TiempoNegocio.ZONA).toInstant();
        Instant fin = ini.plusSeconds(1800);
        CodigoSeleccion sel = new CodigoSeleccion.Franja(1L, 5L, ini.getEpochSecond());
        FranjaOfrecida franjaLarga = new FranjaOfrecida("fL", ini, fin, profLargo, sel);

        ObjectNode actual = composicion.franjas(cuerpoMuyLargo, List.of(franjaLarga), hoy);

        String cuerpoRecortado = actual.path("body").path("text").asString();
        assertThat(cuerpoRecortado).hasSize(1024);
        assertThat(cuerpoRecortado).endsWith("…");

        String descRecortada = actual.path("action").path("sections").get(0)
                .path("rows").get(0).path("description").asString();
        assertThat(descRecortada).hasSize(72);
        assertThat(descRecortada).endsWith("…");
        assertThat(descRecortada).startsWith("viernes 23 de octubre de 2026 · ");
    }

    @Test
    @DisplayName("franjas: título de sección utiliza formato corto exacto de día abreviado y dd/MM en Lima")
    void franjas_tituloSeccion_formatoCortoExacto() {
        ObjectNode actual = composicion.franjas("Opciones:", List.of(franja1), hoy);
        String tituloSeccion = actual.path("action").path("sections").get(0).path("title").asString();

        assertThat(tituloSeccion).isEqualTo("vie 23/10");
        assertThat(tituloSeccion).doesNotEndWith(".");
    }

    @Test
    @DisplayName("franjas: descripción de fila incluye fecha completa exacta con el año y profesional")
    void franjas_descripcionFila_incluyeFechaCompletaExactaConAnoYProfesional() {
        ObjectNode actual = composicion.franjas("Opciones:", List.of(franja1), hoy);
        String descripcion = actual.path("action").path("sections").get(0)
                .path("rows").get(0).path("description").asString();

        assertThat(descripcion).isEqualTo("viernes 23 de octubre de 2026 · Carlos");
        assertThat(descripcion).contains("2026");
        assertThat(descripcion).contains("viernes 23 de octubre de 2026");
        assertThat(descripcion).endsWith(" · Carlos");
    }

    @Test
    @DisplayName("franjas: recorte de descripción a 72 conserva la fecha completa intacta y recorta el profesional")
    void franjas_recorteDescripcion_conservaFechaCompletaYRecortaProfesional() {
        String profMuyLargo = "Carlos Alexander de la Fuente y Ramos Profesional Maestro Barbero Titulado";
        Instant ini = ZonedDateTime.of(2026, 10, 23, 10, 0, 0, 0, TiempoNegocio.ZONA).toInstant();
        Instant fin = ini.plusSeconds(1800);
        CodigoSeleccion sel = new CodigoSeleccion.Franja(1L, 2L, ini.getEpochSecond());
        FranjaOfrecida franjaLarga = new FranjaOfrecida("fL", ini, fin, profMuyLargo, sel);

        ObjectNode actual = composicion.franjas("Opciones:", List.of(franjaLarga), hoy);
        String descripcion = actual.path("action").path("sections").get(0)
                .path("rows").get(0).path("description").asString();

        assertThat(descripcion).hasSize(72);
        assertThat(descripcion).endsWith("…");
        String fechaEsperada = "viernes 23 de octubre de 2026 · ";
        assertThat(descripcion).startsWith(fechaEsperada);
        assertThat(descripcion).contains("2026");
        String profesionalRecortado = descripcion.substring(fechaEsperada.length());
        assertThat(profesionalRecortado).hasSize(72 - fechaEsperada.length());
        assertThat(profesionalRecortado).endsWith("…");
        assertThat(profMuyLargo).startsWith(profesionalRecortado.substring(0, profesionalRecortado.length() - 1));
    }

    @Test
    @DisplayName("franjas: lista de franjas vacía o nula lanza IllegalArgumentException")
    void franjas_sinFranjas_lanzaIllegalArgumentException() {
        assertThatThrownBy(() -> composicion.franjas("Cuerpo", List.of(), hoy))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> composicion.franjas("Cuerpo", null, hoy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("franjas: hoy nulo lanza NullPointerException")
    void franjas_hoyNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> composicion.franjas("Cuerpo", List.of(franja1), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("neutro: devuelve texto neutro fijo para acompañar la lista")
    void neutro_devuelveTextoFijo() {
        assertThat(composicion.neutro()).isEqualTo("Te muestro las opciones disponibles.");
    }

    @Test
    @DisplayName("aclararPropuestas: devuelve texto que pide elegir una sola opción")
    void aclararPropuestas_devuelveTextoFijo() {
        assertThat(composicion.aclararPropuestas())
                .isEqualTo("Por favor, elige una sola opción para continuar.");
    }

    @Test
    @DisplayName("fallo: devuelve texto que invita a usar el menú")
    void fallo_devuelveTextoFijo() {
        assertThat(composicion.fallo())
                .isEqualTo("No pude entenderte bien; elige una opción del menú.");
    }

    @Test
    @DisplayName("fueraDeAlcance: devuelve texto fijo de alcance para reservar, cambiar o cancelar citas")
    void fueraDeAlcance_devuelveTextoFijo() {
        assertThat(composicion.fueraDeAlcance())
                .isEqualTo("Puedo ayudarte a reservar, cambiar o cancelar citas. Elige una opción:");
    }

    @Test
    @DisplayName("elegirFranja: devuelve texto fijo para elegir horario disponible")
    void elegirFranja_devuelveTextoFijo() {
        assertThat(composicion.elegirFranja())
                .isEqualTo("Estos son los horarios disponibles. Elige uno:");
    }
}
