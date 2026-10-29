package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.barberturno.reservations.EstadoReserva;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de construcción de mensajes interactivos en {@link MenusConversacion}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class MenusConversacionTest {

    private final MenusConversacion menus = new MenusConversacion();
    private final TextosConversacion textos = new TextosConversacion();

    @Test
    @DisplayName("menuPrincipal: JSON exacto")
    void menuPrincipal_casoNormal_jsonExacto() {
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "button");
        esperado.putObject("body").put("text", "Elige una opción:");
        ArrayNode btns = esperado.putObject("action").putArray("buttons");
        btns.addObject().put("type", "reply").putObject("reply")
                .put("id", "m:RESERVAR").put("title", "Reservar");
        btns.addObject().put("type", "reply").putObject("reply")
                .put("id", "m:MIS_CITAS").put("title", "Mis citas");

        ObjectNode actual = menus.menuPrincipal("Elige una opción:");

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("consentimiento: JSON exacto")
    void consentimiento_casoNormal_jsonExacto() {
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "button");
        esperado.putObject("body").put("text", "¿Aceptas la política de privacidad?");
        ArrayNode btns = esperado.putObject("action").putArray("buttons");
        btns.addObject().put("type", "reply").putObject("reply")
                .put("id", "p:S").put("title", "Aceptar");
        btns.addObject().put("type", "reply").putObject("reply")
                .put("id", "p:N").put("title", "No, gracias");

        ObjectNode actual = menus.consentimiento("¿Aceptas la política de privacidad?");

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("servicios: JSON exacto")
    void servicios_casoNormal_jsonExacto() {
        List<ServicioOpcion> lista = List.of(
                new ServicioOpcion(1L, "Corte clásico", 30, new BigDecimal("25.00")),
                new ServicioOpcion(2L, "Barba completa", 20, new BigDecimal("15.00"))
        );
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Servicios disponibles:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver opciones");
        ArrayNode rows = action.putArray("sections").addObject()
                .put("title", "Opciones").putArray("rows");
        rows.addObject().put("id", "s:1").put("title", "Corte clásico").put("description", "30 min · S/ 25,00");
        rows.addObject().put("id", "s:2").put("title", "Barba completa").put("description", "20 min · S/ 15,00");

        ObjectNode actual = menus.servicios("Servicios disponibles:", lista, 0);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("profesionales: JSON exacto (página 0 contiene Cualquiera)")
    void profesionales_casoNormal_jsonExacto() {
        List<ProfesionalOpcion> lista = List.of(
                new ProfesionalOpcion(10L, "Carlos"),
                new ProfesionalOpcion(20L, "Mateo")
        );
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Profesionales:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver opciones");
        ArrayNode rows = action.putArray("sections").addObject()
                .put("title", "Opciones").putArray("rows");
        rows.addObject().put("id", "b:1:0").put("title", "Cualquiera").put("description", "El primer profesional libre");
        rows.addObject().put("id", "b:1:10").put("title", "Carlos").put("description", "Profesional");
        rows.addObject().put("id", "b:1:20").put("title", "Mateo").put("description", "Profesional");

        ObjectNode actual = menus.profesionales("Profesionales:", 1L, lista, 0);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("dias: JSON exacto con 7 días desde hoy")
    void dias_casoNormal_jsonExacto() {
        LocalDate hoy = LocalDate.of(2026, 10, 23); // viernes
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Elige un día:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver opciones");
        ArrayNode rows = action.putArray("sections").addObject()
                .put("title", "Opciones").putArray("rows");
        rows.addObject().put("id", "d:1:10:20261023:0").put("title", "Hoy").put("description", "viernes 23 de octubre de 2026");
        rows.addObject().put("id", "d:1:10:20261024:0").put("title", "Mañana").put("description", "sábado 24 de octubre de 2026");
        rows.addObject().put("id", "d:1:10:20261025:0").put("title", "dom 25/10").put("description", "domingo 25 de octubre de 2026");
        rows.addObject().put("id", "d:1:10:20261026:0").put("title", "lun 26/10").put("description", "lunes 26 de octubre de 2026");
        rows.addObject().put("id", "d:1:10:20261027:0").put("title", "mar 27/10").put("description", "martes 27 de octubre de 2026");
        rows.addObject().put("id", "d:1:10:20261028:0").put("title", "mié 28/10").put("description", "miércoles 28 de octubre de 2026");
        rows.addObject().put("id", "d:1:10:20261029:0").put("title", "jue 29/10").put("description", "jueves 29 de octubre de 2026");

        ObjectNode actual = menus.dias("Elige un día:", 1L, 10L, hoy);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("franjas: JSON exacto")
    void franjas_casoNormal_jsonExacto() {
        LocalDate dia = LocalDate.of(2026, 10, 23);
        Instant inicio = Instant.parse("2026-10-23T23:00:00Z"); // 18:00 Lima
        Instant fin = Instant.parse("2026-10-23T23:30:00Z");
        List<FranjaOpcion> lista = List.of(new FranjaOpcion(10L, "Carlos", inicio, fin));

        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Franjas:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver opciones");
        ArrayNode rows = action.putArray("sections").addObject()
                .put("title", "Opciones").putArray("rows");
        rows.addObject().put("id", "f:1:10:" + inicio.getEpochSecond())
                .put("title", "18:00 · Carlos")
                .put("description", "viernes 23 de octubre de 2026");

        ObjectNode actual = menus.franjas("Franjas:", 1L, 10L, dia, lista, 0);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("citas: JSON exacto")
    void citas_casoNormal_jsonExacto() {
        Instant inicio = Instant.parse("2026-10-23T23:00:00Z"); // 23/10 18:00 Lima
        Instant fin = Instant.parse("2026-10-23T23:30:00Z");
        List<CitaOpcion> lista = List.of(
                new CitaOpcion(50L, "BT-245", 1L, "Corte", 10L, "Carlos", inicio, fin,
                        new BigDecimal("25.00"), EstadoReserva.CONFIRMADA, 1, true, true)
        );

        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Mis citas:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver opciones");
        ArrayNode rows = action.putArray("sections").addObject()
                .put("title", "Opciones").putArray("rows");
        rows.addObject().put("id", "c:50")
                .put("title", "BT-245 · 23/10 18:00")
                .put("description", "Corte con Carlos");

        ObjectNode actual = menus.citas("Mis citas:", lista);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("accionesCita: JSON exacto con cancelar y reprogramar permitidos")
    void accionesCita_ambosPermisos_jsonExacto() {
        Instant inicio = Instant.parse("2026-10-23T23:00:00Z");
        Instant fin = Instant.parse("2026-10-23T23:30:00Z");
        CitaOpcion cita = new CitaOpcion(50L, "BT-245", 1L, "Corte", 10L, "Carlos", inicio, fin,
                new BigDecimal("25.00"), EstadoReserva.CONFIRMADA, 1, true, true);

        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "button");
        esperado.putObject("body").put("text", "Detalle de tu cita:");
        ArrayNode btns = esperado.putObject("action").putArray("buttons");
        btns.addObject().put("type", "reply").putObject("reply").put("id", "a:50:C").put("title", "Cancelar");
        btns.addObject().put("type", "reply").putObject("reply").put("id", "a:50:R").put("title", "Reprogramar");
        btns.addObject().put("type", "reply").putObject("reply").put("id", "m:INICIO").put("title", "Menú");

        ObjectNode actual = menus.accionesCita("Detalle de tu cita:", cita);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("diasCambio: JSON exacto con 7 días para reprogramar")
    void diasCambio_casoNormal_jsonExacto() {
        LocalDate hoy = LocalDate.of(2026, 10, 31); // sábado
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Elige nuevo día:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver opciones");
        ArrayNode rows = action.putArray("sections").addObject()
                .put("title", "Opciones").putArray("rows");
        rows.addObject().put("id", "rd:50:20261031:0").put("title", "Hoy").put("description", "sábado 31 de octubre de 2026");
        rows.addObject().put("id", "rd:50:20261101:0").put("title", "Mañana").put("description", "domingo 1 de noviembre de 2026");
        rows.addObject().put("id", "rd:50:20261102:0").put("title", "lun 02/11").put("description", "lunes 2 de noviembre de 2026");
        rows.addObject().put("id", "rd:50:20261103:0").put("title", "mar 03/11").put("description", "martes 3 de noviembre de 2026");
        rows.addObject().put("id", "rd:50:20261104:0").put("title", "mié 04/11").put("description", "miércoles 4 de noviembre de 2026");
        rows.addObject().put("id", "rd:50:20261105:0").put("title", "jue 05/11").put("description", "jueves 5 de noviembre de 2026");
        rows.addObject().put("id", "rd:50:20261106:0").put("title", "vie 06/11").put("description", "viernes 6 de noviembre de 2026");

        ObjectNode actual = menus.diasCambio("Elige nuevo día:", 50L, hoy);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("franjasCambio: JSON exacto")
    void franjasCambio_casoNormal_jsonExacto() {
        LocalDate dia = LocalDate.of(2026, 10, 24);
        Instant inicio = Instant.parse("2026-10-24T15:00:00Z"); // 10:00 Lima
        Instant fin = Instant.parse("2026-10-24T15:30:00Z");
        List<FranjaOpcion> lista = List.of(new FranjaOpcion(10L, "Carlos", inicio, fin));

        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "list");
        esperado.putObject("body").put("text", "Nuevos horarios:");
        ObjectNode action = esperado.putObject("action");
        action.put("button", "Ver opciones");
        ArrayNode rows = action.putArray("sections").addObject()
                .put("title", "Opciones").putArray("rows");
        rows.addObject().put("id", "rf:50:" + inicio.getEpochSecond())
                .put("title", "10:00 · Carlos")
                .put("description", "sábado 24 de octubre de 2026");

        ObjectNode actual = menus.franjasCambio("Nuevos horarios:", 50L, dia, lista, 0);

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("confirmacion: JSON exacto con Confirmar y Cambiar")
    void confirmacion_casoNormal_jsonExacto() {
        ObjectNode esperado = JsonNodeFactory.instance.objectNode();
        esperado.put("type", "button");
        esperado.putObject("body").put("text", "Resumen de la cita");
        ArrayNode btns = esperado.putObject("action").putArray("buttons");
        btns.addObject().put("type", "reply").putObject("reply")
                .put("id", "o:88:abcdefghijklmnopqrstuv:S").put("title", "Confirmar");
        btns.addObject().put("type", "reply").putObject("reply")
                .put("id", "o:88:abcdefghijklmnopqrstuv:N").put("title", "Cambiar");

        ObjectNode actual = menus.confirmacion("Resumen de la cita", 88L, "abcdefghijklmnopqrstuv");

        assertThat(actual).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Límites de Meta: cuerpo de 2000 se recorta a 1024 y títulos de 30 a 24 con «…»")
    void limites_cuerpoYTitulosLargos_recortaDeterministaConElipsis() {
        String cuerpo2000 = "A".repeat(2000);
        String nombre30 = "123456789012345678901234567890";
        List<ServicioOpcion> lista = List.of(new ServicioOpcion(1L, nombre30, 30, BigDecimal.TEN));

        ObjectNode res = menus.servicios(cuerpo2000, lista, 0);

        String textoCuerpo = res.path("body").path("text").asString();
        assertThat(textoCuerpo).hasSize(1024);
        assertThat(textoCuerpo).endsWith("…");

        String tituloFila = res.path("action").path("sections").get(0).path("rows").get(0).path("title").asString();
        assertThat(tituloFila).hasSize(24);
        assertThat(tituloFila).isEqualTo("12345678901234567890123…");
    }

    @Test
    @DisplayName("Paginación de servicios con 0, 9, 10, 19 y 25 opciones")
    void servicios_paginacionOpciones_distribuyeFilasYVerMasCorrectamente() {
        // 0 opciones -> 0 filas
        ObjectNode res0 = menus.servicios("c", List.of(), 0);
        assertThat(res0.path("action").path("sections").get(0).path("rows")).isEmpty();

        // 9 opciones -> página 0 tiene 9 filas sin Ver más
        List<ServicioOpcion> lista9 = crearServicios(9);
        ObjectNode res9 = menus.servicios("c", lista9, 0);
        ArrayNode filas9 = (ArrayNode) res9.path("action").path("sections").get(0).path("rows");
        assertThat(filas9).hasSize(9);
        assertThat(filas9.get(8).path("title").asString()).isNotEqualTo("Ver más");

        // 10 opciones -> página 0 tiene 10 filas (9 + Ver más a pág 1); página 1 tiene 1 fila
        List<ServicioOpcion> lista10 = crearServicios(10);
        ObjectNode res10P0 = menus.servicios("c", lista10, 0);
        ArrayNode filas10P0 = (ArrayNode) res10P0.path("action").path("sections").get(0).path("rows");
        assertThat(filas10P0).hasSize(10);
        assertThat(filas10P0.get(9).path("title").asString()).isEqualTo("Ver más");
        assertThat(filas10P0.get(9).path("id").asString()).isEqualTo("sp:1");

        ObjectNode res10P1 = menus.servicios("c", lista10, 1);
        ArrayNode filas10P1 = (ArrayNode) res10P1.path("action").path("sections").get(0).path("rows");
        assertThat(filas10P1).hasSize(1);
        assertThat(filas10P1.get(0).path("id").asString()).isEqualTo("s:10");

        // 19 opciones -> pág 0: 9 + VM(sp:1); pág 1: 9 + VM(sp:2); pág 2: 1 fila
        List<ServicioOpcion> lista19 = crearServicios(19);
        ObjectNode res19P0 = menus.servicios("c", lista19, 0);
        ArrayNode filas19P0 = (ArrayNode) res19P0.path("action").path("sections").get(0).path("rows");
        assertThat(filas19P0).hasSize(10);
        assertThat(filas19P0.get(9).path("id").asString()).isEqualTo("sp:1");

        ObjectNode res19P1 = menus.servicios("c", lista19, 1);
        ArrayNode filas19P1 = (ArrayNode) res19P1.path("action").path("sections").get(0).path("rows");
        assertThat(filas19P1).hasSize(10);
        assertThat(filas19P1.get(9).path("id").asString()).isEqualTo("sp:2");

        ObjectNode res19P2 = menus.servicios("c", lista19, 2);
        ArrayNode filas19P2 = (ArrayNode) res19P2.path("action").path("sections").get(0).path("rows");
        assertThat(filas19P2).hasSize(1);
        assertThat(filas19P2.get(0).path("id").asString()).isEqualTo("s:19");

        // 25 opciones -> pág 2 tiene 7 filas (25 - 18 = 7) sin Ver más
        List<ServicioOpcion> lista25 = crearServicios(25);
        ObjectNode res25P2 = menus.servicios("c", lista25, 2);
        ArrayNode filas25P2 = (ArrayNode) res25P2.path("action").path("sections").get(0).path("rows");
        assertThat(filas25P2).hasSize(7);
        assertThat(filas25P2.get(6).path("title").asString()).isNotEqualTo("Ver más");
    }

    @Test
    @DisplayName("Paginación de profesionales: pág 0 tiene Cualquiera + 8 (+ Ver más); pág 1 tiene 9")
    void profesionales_paginacionOpciones_distribucionCorrecta() {
        // 8 profesionales -> Cualquiera + 8 = 9 filas (sin Ver más)
        List<ProfesionalOpcion> lista8 = crearProfesionales(8);
        ObjectNode res8 = menus.profesionales("c", 1L, lista8, 0);
        ArrayNode filas8 = (ArrayNode) res8.path("action").path("sections").get(0).path("rows");
        assertThat(filas8).hasSize(9);
        assertThat(filas8.get(0).path("id").asString()).isEqualTo("b:1:0");
        assertThat(filas8.get(8).path("id").asString()).isEqualTo("b:1:8");

        // 9 profesionales -> Cualquiera + 8 + Ver más = 10 filas en pág 0; pág 1 tiene 1 fila
        List<ProfesionalOpcion> lista9 = crearProfesionales(9);
        ObjectNode res9P0 = menus.profesionales("c", 1L, lista9, 0);
        ArrayNode filas9P0 = (ArrayNode) res9P0.path("action").path("sections").get(0).path("rows");
        assertThat(filas9P0).hasSize(10);
        assertThat(filas9P0.get(9).path("title").asString()).isEqualTo("Ver más");
        assertThat(filas9P0.get(9).path("id").asString()).isEqualTo("bp:1:1");

        ObjectNode res9P1 = menus.profesionales("c", 1L, lista9, 1);
        ArrayNode filas9P1 = (ArrayNode) res9P1.path("action").path("sections").get(0).path("rows");
        assertThat(filas9P1).hasSize(1);
        assertThat(filas9P1.get(0).path("id").asString()).isEqualTo("b:1:9");
    }

    @ParameterizedTest
    @CsvSource({"-1", "10", "5"})
    @DisplayName("Página fuera de rango muestra página 0")
    void listas_paginaFueraDeRango_muestraPaginaCero(int paginaInvalida) {
        List<ServicioOpcion> servicios = crearServicios(5);
        ObjectNode resServicios = menus.servicios("c", servicios, paginaInvalida);
        assertThat(resServicios.path("action").path("sections").get(0).path("rows").get(0).path("id").asString())
                .isEqualTo("s:1");

        List<ProfesionalOpcion> profesionales = crearProfesionales(5);
        ObjectNode resProfesionales = menus.profesionales("c", 1L, profesionales, paginaInvalida);
        assertThat(resProfesionales.path("action").path("sections").get(0).path("rows").get(0).path("id").asString())
                .isEqualTo("b:1:0");

        List<FranjaOpcion> franjas = List.of(new FranjaOpcion(1L, "C", Instant.EPOCH.plusSeconds(1000), Instant.EPOCH.plusSeconds(2000)));
        ObjectNode resFranjas = menus.franjas("c", 1L, 1L, LocalDate.of(2026, 10, 23), franjas, paginaInvalida);
        assertThat(resFranjas.path("action").path("sections").get(0).path("rows").get(0).path("id").asString())
                .isEqualTo("f:1:1:1000");
    }

    @Test
    @DisplayName("Todos los identificadores generados por los menús decodifican con CodigoSeleccion")
    void todosLosIds_decodificanCorrectamente() {
        // Verificar botones
        comprobarIdsDeBotones(menus.menuPrincipal("c"));
        comprobarIdsDeBotones(menus.consentimiento("c"));
        comprobarIdsDeBotones(menus.confirmacion("c", 10L, "abcdefghijklmnopqrstuv"));

        Instant ini = Instant.parse("2026-10-23T23:00:00Z");
        CitaOpcion cita = new CitaOpcion(1L, "BT-1", 1L, "S", 1L, "P", ini, ini.plusSeconds(1800), BigDecimal.TEN, EstadoReserva.CONFIRMADA, 1, true, true);
        comprobarIdsDeBotones(menus.accionesCita("c", cita));

        // Verificar listas
        comprobarIdsDeLista(menus.servicios("c", crearServicios(12), 0));
        comprobarIdsDeLista(menus.profesionales("c", 1L, crearProfesionales(12), 0));
        comprobarIdsDeLista(menus.dias("c", 1L, 0L, LocalDate.of(2026, 10, 23)));
        comprobarIdsDeLista(menus.diasCambio("c", 1L, LocalDate.of(2026, 10, 23)));
        comprobarIdsDeLista(menus.citas("c", List.of(cita)));

        List<FranjaOpcion> franjas = List.of(new FranjaOpcion(1L, "P", ini, ini.plusSeconds(1800)));
        comprobarIdsDeLista(menus.franjas("c", 1L, 0L, LocalDate.of(2026, 10, 23), franjas, 0));
        comprobarIdsDeLista(menus.franjasCambio("c", 1L, LocalDate.of(2026, 10, 23), franjas, 0));
    }

    @ParameterizedTest
    @CsvSource({
            "true, true, 3",
            "true, false, 2",
            "false, true, 2",
            "false, false, 1"
    })
    @DisplayName("accionesCita: botones según combinaciones de permisos, máximo 3 botones")
    void accionesCita_combinacionesPermisos_generaBotonesCorrectos(boolean puedeCancelar, boolean puedeReprogramar, int esperadoBotones) {
        Instant ini = Instant.parse("2026-10-23T23:00:00Z");
        CitaOpcion cita = new CitaOpcion(99L, "BT-99", 1L, "S", 1L, "P", ini, ini.plusSeconds(1800), BigDecimal.TEN,
                EstadoReserva.CONFIRMADA, 1, puedeCancelar, puedeReprogramar);

        ObjectNode res = menus.accionesCita("Detalle", cita);
        ArrayNode botones = (ArrayNode) res.path("action").path("buttons");

        assertThat(botones).hasSize(esperadoBotones);
        boolean tieneCancelar = false;
        boolean tieneReprogramar = false;
        boolean tieneMenu = false;
        for (JsonNode b : botones) {
            String title = b.path("reply").path("title").asString();
            if ("Cancelar".equals(title)) tieneCancelar = true;
            if ("Reprogramar".equals(title)) tieneReprogramar = true;
            if ("Menú".equals(title)) tieneMenu = true;
        }

        assertThat(tieneCancelar).isEqualTo(puedeCancelar);
        assertThat(tieneReprogramar).isEqualTo(puedeReprogramar);
        assertThat(tieneMenu).isTrue(); // Menú siempre presente
    }

    @Test
    @DisplayName("Ningún menú ni lista incluye «Hablar con alguien»")
    void menus_sinHablarConAlguien() {
        List<ObjectNode> todosLosMenus = List.of(
                menus.menuPrincipal("c"),
                menus.consentimiento("c"),
                menus.confirmacion("c", 1L, "abcdefghijklmnopqrstuv"),
                menus.servicios("c", crearServicios(3), 0),
                menus.profesionales("c", 1L, crearProfesionales(3), 0),
                menus.dias("c", 1L, 0L, LocalDate.of(2026, 10, 23))
        );

        for (ObjectNode nodo : todosLosMenus) {
            String json = nodo.toString().toLowerCase();
            assertThat(json).doesNotContain("hablar con alguien");
            assertThat(json).doesNotContain("humano");
        }
    }

    private void comprobarIdsDeBotones(ObjectNode obj) {
        ArrayNode btns = (ArrayNode) obj.path("action").path("buttons");
        assertThat(btns).isNotEmpty();
        for (JsonNode b : btns) {
            String id = b.path("reply").path("id").asString();
            assertThat(CodigoSeleccion.decodificar(id)).as("Id de botón: " + id).isPresent();
        }
    }

    private void comprobarIdsDeLista(ObjectNode obj) {
        ArrayNode rows = (ArrayNode) obj.path("action").path("sections").get(0).path("rows");
        assertThat(rows).isNotEmpty();
        for (JsonNode r : rows) {
            String id = r.path("id").asString();
            assertThat(CodigoSeleccion.decodificar(id)).as("Id de fila: " + id).isPresent();
        }
    }

    private List<ServicioOpcion> crearServicios(int cantidad) {
        List<ServicioOpcion> res = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            res.add(new ServicioOpcion(i, "Servicio " + i, 30, BigDecimal.TEN));
        }
        return res;
    }

    private List<ProfesionalOpcion> crearProfesionales(int cantidad) {
        List<ProfesionalOpcion> res = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            res.add(new ProfesionalOpcion(i, "Barbero " + i));
        }
        return res;
    }
}
