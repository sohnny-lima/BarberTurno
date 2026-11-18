package pe.barberturno.conversation.ia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.CitaOpcion;
import pe.barberturno.conversation.CodigoSeleccion;
import pe.barberturno.conversation.MenusConversacion;
import pe.barberturno.conversation.ProfesionalOpcion;
import pe.barberturno.conversation.ServicioOpcion;
import pe.barberturno.conversation.TextosConversacion;
import pe.barberturno.reservations.EstadoReserva;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de {@link RespuestasAsistente} (17-v2-12 §13.3; V2-DA-86).
 * Verifica cada fila de composición, las degradaciones deterministas ante datos o listas vacías,
 * la validez de todos los códigos de selección con {@link CodigoSeleccion#decodificar(String)},
 * la ausencia de listas con {@code rows: []}, el cumplimiento de los límites de Meta con nombres largos
 * y el carácter determinista de todas las salidas.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class RespuestasAsistenteTest {

    private final MenusConversacion menus = new MenusConversacion();
    private final TextosConversacion textos = new TextosConversacion();
    private final ComposicionTurnoIA composicion = new ComposicionTurnoIA();
    private final RespuestasAsistente respuestas = new RespuestasAsistente(menus, textos, composicion);

    private final LocalDate hoy = LocalDate.of(2026, 11, 5);
    private final ServicioOpcion servicio1 =
            new ServicioOpcion(10L, "Corte clásico", 30, new BigDecimal("25.00"));
    private final ServicioOpcion servicio2 =
            new ServicioOpcion(11L, "Barba completa", 20, new BigDecimal("18.50"));
    private final ProfesionalOpcion prof1 = new ProfesionalOpcion(20L, "Carlos");
    private final ProfesionalOpcion prof2 = new ProfesionalOpcion(21L, "Mateo");

    private final Instant citaInicio =
            ZonedDateTime.of(2026, 11, 6, 16, 30, 0, 0, TiempoNegocio.ZONA).toInstant();
    private final Instant citaFin = citaInicio.plusSeconds(1800);
    private final CitaOpcion cita1 = new CitaOpcion(
            100L,
            "BT-100",
            10L,
            "Corte clásico",
            20L,
            "Carlos",
            citaInicio,
            citaFin,
            new BigDecimal("25.00"),
            EstadoReserva.CONFIRMADA,
            1,
            true,
            true);

    private final Instant franja1Inicio =
            ZonedDateTime.of(2026, 11, 5, 10, 0, 0, 0, TiempoNegocio.ZONA).toInstant();
    private final Instant franja1Fin = franja1Inicio.plusSeconds(1800);
    private final Instant franja2Inicio =
            ZonedDateTime.of(2026, 11, 6, 15, 30, 0, 0, TiempoNegocio.ZONA).toInstant();
    private final Instant franja2Fin = franja2Inicio.plusSeconds(1800);

    private final FranjaOfrecida franjaCreacion = new FranjaOfrecida(
            "f1",
            franja1Inicio,
            franja1Fin,
            "Carlos",
            new CodigoSeleccion.Franja(10L, 20L, franja1Inicio.getEpochSecond()));
    private final FranjaOfrecida franjaCambio = new FranjaOfrecida(
            "f2",
            franja2Inicio,
            franja2Fin,
            "Mateo",
            new CodigoSeleccion.FranjaCambio(100L, franja2Inicio.getEpochSecond()));

    private DatosRespuesta datosBase() {
        return new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.of(servicio1),
                new SeleccionProfesional.Concreto(prof1),
                List.of(servicio1, servicio2),
                List.of(prof1, prof2),
                List.of(cita1));
    }

    @Test
    @DisplayName("responder: PEDIR_DATO(SERVICIO) compone lista de servicios con precios del catálogo")
    void responder_pedirDatoServicio_muestraListaServiciosConPreciosServidor() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.SERVICIO));
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.servicios(textos.elegirServicio(), List.of(servicio1, servicio2), 0));
        ArrayNode rows = filasUnicaSeccion(json);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).path("description").asString()).isEqualTo("30 min · S/ 25,00");
        assertThat(CodigoSeleccion.decodificar(rows.get(0).path("id").asString()))
                .contains(new CodigoSeleccion.Servicio(10L));
    }

    @Test
    @DisplayName("responder: MOSTRAR_SERVICIOS compone la misma lista de servicios de V2-10")
    void responder_mostrarServicios_muestraListaServicios() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.MOSTRAR_SERVICIOS, Optional.empty());
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.servicios(textos.elegirServicio(), List.of(servicio1, servicio2), 0));
    }

    @Test
    @DisplayName("responder: PEDIR_DATO(PROFESIONAL) compone lista de profesionales encabezada por Cualquiera")
    void responder_pedirDatoProfesional_muestraListaProfesionales() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.PROFESIONAL));
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.profesionales(
                textos.elegirProfesional(servicio1.nombre()),
                servicio1.id(),
                List.of(prof1, prof2),
                0));
        ArrayNode rows = filasUnicaSeccion(json);
        assertThat(rows).hasSize(3);
        assertThat(CodigoSeleccion.decodificar(rows.get(0).path("id").asString()))
                .contains(new CodigoSeleccion.Profesional(10L, 0L));
        assertThat(CodigoSeleccion.decodificar(rows.get(1).path("id").asString()))
                .contains(new CodigoSeleccion.Profesional(10L, 20L));
    }

    @Test
    @DisplayName("responder: MOSTRAR_PROFESIONALES compone lista de profesionales del servicio elegido")
    void responder_mostrarProfesionales_muestraListaProfesionales() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.MOSTRAR_PROFESIONALES, Optional.empty());
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.profesionales(
                textos.elegirProfesional(servicio1.nombre()),
                servicio1.id(),
                List.of(prof1, prof2),
                0));
    }

    @Test
    @DisplayName("responder: PEDIR_DATO(DIA) con profesional concreto compone lista de 7 días con barberoId")
    void responder_pedirDatoDiaConProfesionalConcreto_muestraDiasConBarberoId() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA));
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.dias(textos.elegirDia(), servicio1.id(), prof1.id(), hoy));
        ArrayNode rows = filasUnicaSeccion(json);
        assertThat(rows).hasSize(7);
        assertThat(CodigoSeleccion.decodificar(rows.get(0).path("id").asString()))
                .contains(new CodigoSeleccion.Dia(10L, 20L, hoy, 0));
    }

    @Test
    @DisplayName("responder: PEDIR_DATO(DIA) con Cualquiera compone lista de 7 días con barberoId = 0")
    void responder_pedirDatoDiaConCualquiera_muestraDiasConBarberoCero() {
        DatosRespuesta datos = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.of(servicio1),
                new SeleccionProfesional.Cualquiera(),
                List.of(servicio1),
                List.of(prof1),
                List.of());
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA));

        MensajeAsistente actual = respuestas.responder(decision, datos);

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.dias(textos.elegirDia(), servicio1.id(), 0L, hoy));
        ArrayNode rows = filasUnicaSeccion(json);
        assertThat(rows).hasSize(7);
        assertThat(CodigoSeleccion.decodificar(rows.get(0).path("id").asString()))
                .contains(new CodigoSeleccion.Dia(10L, 0L, hoy, 0));
    }

    @Test
    @DisplayName("responder: PEDIR_DATO(CITA) y MOSTRAR_CITAS con citas componen lista de citas")
    void responder_citasConDatos_muestraListaCitas() {
        DecisionRespuesta pedirCita = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.CITA));
        DecisionRespuesta mostrarCitas = new DecisionRespuesta(
                IntencionRespuesta.MOSTRAR_CITAS, Optional.empty());

        ObjectNode jsonPedir = verificarInteractivoValido(respuestas.responder(pedirCita, datosBase()));
        ObjectNode jsonMostrar = verificarInteractivoValido(respuestas.responder(mostrarCitas, datosBase()));

        ObjectNode esperado = menus.citas(textos.misCitas(), List.of(cita1));
        assertThat(jsonPedir).isEqualTo(esperado);
        assertThat(jsonMostrar).isEqualTo(esperado);
        ArrayNode rows = filasUnicaSeccion(jsonPedir);
        assertThat(rows).hasSize(1);
        assertThat(CodigoSeleccion.decodificar(rows.get(0).path("id").asString()))
                .contains(new CodigoSeleccion.Cita(100L));
    }

    @Test
    @DisplayName("responder: SIN_DISPONIBILIDAD muestra menú principal con sinOpciones")
    void responder_sinDisponibilidad_muestraMenuPrincipalConSinOpciones() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.SIN_DISPONIBILIDAD, Optional.empty());
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.menuPrincipal(textos.sinOpciones()));
    }

    @Test
    @DisplayName("responder: FUERA_DE_ALCANCE muestra menú principal con texto de alcance y no noSoportado")
    void responder_fueraDeAlcance_muestraMenuPrincipalConTextoDeAlcance() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.FUERA_DE_ALCANCE, Optional.empty());
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.menuPrincipal(composicion.fueraDeAlcance()));
        assertThat(json.path("body").path("text").asString())
                .isEqualTo(composicion.fueraDeAlcance())
                .isNotEqualTo(textos.noSoportado());
    }

    @Test
    @DisplayName("responder: SALUDO muestra menú principal con bienvenida del negocio")
    void responder_saludo_muestraMenuPrincipalConBienvenida() {
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.SALUDO, Optional.empty());
        MensajeAsistente actual = respuestas.responder(decision, datosBase());

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(menus.menuPrincipal(textos.bienvenida("Barbería El Centro")));
    }

    @Test
    @DisplayName("franjas: compone lista interactiva con texto fijo del servidor y códigos válidos")
    void franjas_conFranjas_componeListaConTextoFijo() {
        MensajeAsistente actual = respuestas.franjas(List.of(franjaCreacion, franjaCambio), hoy);

        ObjectNode json = verificarInteractivoValido(actual);
        assertThat(json).isEqualTo(composicion.franjas(
                composicion.elegirFranja(), List.of(franjaCreacion, franjaCambio), hoy));
        assertThat(json.path("body").path("text").asString())
                .isEqualTo("Estos son los horarios disponibles. Elige uno:");
    }

    @Test
    @DisplayName("menu: cada MotivoMenu compone el menú principal con su texto fijo correspondiente")
    void menu_cadaMotivo_componeMenuPrincipalConTextoFijo() {
        ObjectNode jsonNeutro = verificarInteractivoValido(respuestas.menu(MotivoMenu.NEUTRO));
        ObjectNode jsonFallo = verificarInteractivoValido(respuestas.menu(MotivoMenu.FALLO));
        ObjectNode jsonVarias = verificarInteractivoValido(respuestas.menu(MotivoMenu.VARIAS_PROPUESTAS));
        ObjectNode jsonNoDisp = verificarInteractivoValido(respuestas.menu(MotivoMenu.NO_DISPONIBLE));

        assertThat(jsonNeutro).isEqualTo(menus.menuPrincipal(composicion.neutro()));
        assertThat(jsonFallo).isEqualTo(menus.menuPrincipal(composicion.fallo()));
        assertThat(jsonVarias).isEqualTo(menus.menuPrincipal(composicion.aclararPropuestas()));
        assertThat(jsonNoDisp).isEqualTo(menus.menuPrincipal(textos.noDisponible()));
    }

    @Test
    @DisplayName("degradación: servicios vacíos devuelve menú principal con sinOpciones y nunca rows vacío")
    void responder_serviciosVacios_degradaAMenuPrincipalSinOpciones() {
        DatosRespuesta sinServicios = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.empty(),
                new SeleccionProfesional.SinElegir(),
                List.of(),
                List.of(),
                List.of());

        ObjectNode jsonPedir = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.SERVICIO)),
                sinServicios));
        ObjectNode jsonMostrar = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_SERVICIOS, Optional.empty()),
                sinServicios));

        ObjectNode esperado = menus.menuPrincipal(textos.sinOpciones());
        assertThat(jsonPedir).isEqualTo(esperado);
        assertThat(jsonMostrar).isEqualTo(esperado);
    }

    @Test
    @DisplayName("degradación: profesionales vacíos devuelve menú principal con sinOpciones")
    void responder_profesionalesVacios_degradaAMenuPrincipalSinOpciones() {
        DatosRespuesta sinProfesionales = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.of(servicio1),
                new SeleccionProfesional.SinElegir(),
                List.of(servicio1),
                List.of(),
                List.of());

        ObjectNode jsonPedir = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.PROFESIONAL)),
                sinProfesionales));
        ObjectNode jsonMostrar = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_PROFESIONALES, Optional.empty()),
                sinProfesionales));

        ObjectNode esperado = menus.menuPrincipal(textos.sinOpciones());
        assertThat(jsonPedir).isEqualTo(esperado);
        assertThat(jsonMostrar).isEqualTo(esperado);
    }

    @Test
    @DisplayName("degradación: pedir o mostrar profesionales sin servicio elegido degrada a lista de servicios")
    void responder_profesionalesSinServicio_degradaAListaServicios() {
        DatosRespuesta sinServicioElegido = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.empty(),
                new SeleccionProfesional.SinElegir(),
                List.of(servicio1, servicio2),
                List.of(),
                List.of());

        ObjectNode jsonPedir = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.PROFESIONAL)),
                sinServicioElegido));
        ObjectNode jsonMostrar = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_PROFESIONALES, Optional.empty()),
                sinServicioElegido));

        ObjectNode esperado = menus.servicios(textos.elegirServicio(), List.of(servicio1, servicio2), 0);
        assertThat(jsonPedir).isEqualTo(esperado);
        assertThat(jsonMostrar).isEqualTo(esperado);
    }

    @Test
    @DisplayName("degradación: PEDIR_DATO(DIA) con SinElegir degrada a lista de profesionales")
    void responder_pedirDatoDiaConSinElegir_degradaAListaProfesionales() {
        DatosRespuesta datos = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.of(servicio1),
                new SeleccionProfesional.SinElegir(),
                List.of(servicio1),
                List.of(prof1, prof2),
                List.of());
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA));

        ObjectNode json = verificarInteractivoValido(respuestas.responder(decision, datos));
        assertThat(json).isEqualTo(menus.profesionales(
                textos.elegirProfesional(servicio1.nombre()),
                servicio1.id(),
                List.of(prof1, prof2),
                0));
    }

    @Test
    @DisplayName("degradación: PEDIR_DATO(DIA) con SinElegir y sin profesionales degrada a menú sinOpciones")
    void responder_pedirDatoDiaConSinElegirYSinProfesionales_degradaAMenuSinOpciones() {
        DatosRespuesta datos = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.of(servicio1),
                new SeleccionProfesional.SinElegir(),
                List.of(servicio1),
                List.of(),
                List.of());
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA));

        ObjectNode json = verificarInteractivoValido(respuestas.responder(decision, datos));
        assertThat(json).isEqualTo(menus.menuPrincipal(textos.sinOpciones()));
    }

    @Test
    @DisplayName("degradación: PEDIR_DATO(DIA) sin servicio degrada a lista de servicios")
    void responder_pedirDatoDiaSinServicio_degradaAListaServicios() {
        DatosRespuesta datos = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.empty(),
                new SeleccionProfesional.SinElegir(),
                List.of(servicio1),
                List.of(prof1),
                List.of());
        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA));

        ObjectNode json = verificarInteractivoValido(respuestas.responder(decision, datos));
        assertThat(json).isEqualTo(menus.servicios(textos.elegirServicio(), List.of(servicio1), 0));
    }

    @Test
    @DisplayName("degradación: citas vacías devuelve menú principal con sinCitas")
    void responder_citasVacias_muestraMenuPrincipalConSinCitas() {
        DatosRespuesta sinCitas = new DatosRespuesta(
                "Barbería El Centro",
                hoy,
                Optional.of(servicio1),
                new SeleccionProfesional.Concreto(prof1),
                List.of(servicio1),
                List.of(prof1),
                List.of());

        ObjectNode jsonPedir = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.CITA)),
                sinCitas));
        ObjectNode jsonMostrar = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_CITAS, Optional.empty()),
                sinCitas));

        ObjectNode esperado = menus.menuPrincipal(textos.sinCitas());
        assertThat(jsonPedir).isEqualTo(esperado);
        assertThat(jsonMostrar).isEqualTo(esperado);
    }

    @Test
    @DisplayName("degradación: franjas vacías o nulas devuelve menú principal NEUTRO")
    void franjas_vaciasONulas_devuelveMenuNeutro() {
        ObjectNode esperado = menus.menuPrincipal(composicion.neutro());

        ObjectNode jsonVacia = verificarInteractivoValido(respuestas.franjas(List.of(), hoy));
        ObjectNode jsonNula = verificarInteractivoValido(respuestas.franjas(null, hoy));

        assertThat(jsonVacia).isEqualTo(esperado);
        assertThat(jsonNula).isEqualTo(esperado);
    }

    @Test
    @DisplayName("límites de Meta: recorta cuerpo a 1024 y títulos a 24 con nombres muy largos y preserva códigos válidos")
    void responderYFranjas_nombresLargosYListasGrandes_respetaLimitesDeMeta() {
        String negocioLargo = "Barbería Tradicional ".repeat(60);
        String servicioLargoNombre = "Servicio Especial de Corte y Barba Imperial ".repeat(30);
        String profesionalLargoNombre = "Carlos Alejandro De La Fuente y Sotomayor Maestro Barbero";

        List<ServicioOpcion> muchosServicios = new ArrayList<>();
        List<ProfesionalOpcion> muchosProfesionales = new ArrayList<>();
        List<CitaOpcion> muchasCitas = new ArrayList<>();
        List<FranjaOfrecida> muchasFranjas = new ArrayList<>();

        for (int i = 1; i <= 12; i++) {
            muchosServicios.add(new ServicioOpcion(
                    i, servicioLargoNombre + i, 45, new BigDecimal("40.00")));
            muchosProfesionales.add(new ProfesionalOpcion(
                    100L + i, profesionalLargoNombre + " " + i));
            Instant ini = franja1Inicio.plusSeconds(i * 1800L);
            Instant fin = ini.plusSeconds(1800L);
            muchasCitas.add(new CitaOpcion(
                    200L + i,
                    "BT-" + (200 + i),
                    i,
                    servicioLargoNombre,
                    100L + i,
                    profesionalLargoNombre,
                    ini,
                    fin,
                    new BigDecimal("40.00"),
                    EstadoReserva.CONFIRMADA,
                    1,
                    true,
                    true));
            muchasFranjas.add(new FranjaOfrecida(
                    "f" + i,
                    ini,
                    fin,
                    profesionalLargoNombre,
                    new CodigoSeleccion.Franja(1L, 101L, ini.getEpochSecond())));
        }

        DatosRespuesta datosLargos = new DatosRespuesta(
                negocioLargo,
                hoy,
                Optional.of(muchosServicios.getFirst()),
                new SeleccionProfesional.Concreto(muchosProfesionales.getFirst()),
                muchosServicios,
                muchosProfesionales,
                muchasCitas);

        ObjectNode saludo = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.SALUDO, Optional.empty()), datosLargos));
        assertThat(saludo.path("body").path("text").asString()).hasSize(1024);

        ObjectNode profs = verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_PROFESIONALES, Optional.empty()), datosLargos));
        assertThat(profs.path("body").path("text").asString()).hasSize(1024);

        verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_SERVICIOS, Optional.empty()), datosLargos));
        verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_CITAS, Optional.empty()), datosLargos));
        verificarInteractivoValido(respuestas.responder(
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA)), datosLargos));
        verificarInteractivoValido(respuestas.franjas(muchasFranjas, hoy));
    }

    @Test
    @DisplayName("determinismo: dos llamadas con los mismos argumentos producen un resultado idéntico")
    void responderFranjasYMenu_dosLlamadasIguales_producenResultadoIdentico() {
        RespuestasAsistente otraInstancia = new RespuestasAsistente();
        DatosRespuesta datos = datosBase();

        List<DecisionRespuesta> decisiones = List.of(
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.SERVICIO)),
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.PROFESIONAL)),
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.DIA)),
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.CITA)),
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_SERVICIOS, Optional.empty()),
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_PROFESIONALES, Optional.empty()),
                new DecisionRespuesta(IntencionRespuesta.MOSTRAR_CITAS, Optional.empty()),
                new DecisionRespuesta(IntencionRespuesta.SIN_DISPONIBILIDAD, Optional.empty()),
                new DecisionRespuesta(IntencionRespuesta.FUERA_DE_ALCANCE, Optional.empty()),
                new DecisionRespuesta(IntencionRespuesta.SALUDO, Optional.empty()));

        for (DecisionRespuesta d : decisiones) {
            assertThat(respuestas.responder(d, datos))
                    .isEqualTo(respuestas.responder(d, datos))
                    .isEqualTo(otraInstancia.responder(d, datos));
        }

        assertThat(respuestas.franjas(List.of(franjaCreacion, franjaCambio), hoy))
                .isEqualTo(respuestas.franjas(List.of(franjaCreacion, franjaCambio), hoy))
                .isEqualTo(otraInstancia.franjas(List.of(franjaCreacion, franjaCambio), hoy));

        for (MotivoMenu motivo : MotivoMenu.values()) {
            assertThat(respuestas.menu(motivo))
                    .isEqualTo(respuestas.menu(motivo))
                    .isEqualTo(otraInstancia.menu(motivo));
        }
    }

    @Test
    @DisplayName("validación de nulos: rechaza argumentos nulos en constructor y métodos públicos")
    void metodosYConstructor_argumentosNulos_lanzaNullPointerException() {
        assertThatThrownBy(() -> new RespuestasAsistente(null, textos, composicion))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RespuestasAsistente(menus, null, composicion))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RespuestasAsistente(menus, textos, null))
                .isInstanceOf(NullPointerException.class);

        DecisionRespuesta decision = new DecisionRespuesta(
                IntencionRespuesta.SALUDO, Optional.empty());
        assertThatThrownBy(() -> respuestas.responder(null, datosBase()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> respuestas.responder(decision, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> respuestas.franjas(List.of(franjaCreacion), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> respuestas.menu(null))
                .isInstanceOf(NullPointerException.class);
    }

    private ObjectNode verificarInteractivoValido(MensajeAsistente mensaje) {
        assertThat(mensaje).isInstanceOf(MensajeAsistente.Interactivo.class);
        ObjectNode json = ((MensajeAsistente.Interactivo) mensaje).contenido();

        String cuerpo = json.path("body").path("text").asString();
        assertThat(cuerpo).isNotEmpty();
        assertThat(cuerpo.length()).isLessThanOrEqualTo(1024);

        String tipo = json.path("type").asString();
        assertThat(tipo).isIn("button", "list");

        if ("button".equals(tipo)) {
            ArrayNode buttons = (ArrayNode) json.path("action").path("buttons");
            assertThat(buttons).isNotEmpty();
            assertThat(buttons.size()).isLessThanOrEqualTo(3);
            for (JsonNode b : buttons) {
                String id = b.path("reply").path("id").asString();
                String title = b.path("reply").path("title").asString();
                assertThat(CodigoSeleccion.decodificar(id))
                        .as("Código de selección válido para botón: %s", id)
                        .isPresent();
                assertThat(title).isNotEmpty();
                assertThat(title.length()).isLessThanOrEqualTo(20);
            }
        } else {
            String botonLista = json.path("action").path("button").asString();
            assertThat(botonLista).isNotEmpty();
            assertThat(botonLista.length()).isLessThanOrEqualTo(20);

            ArrayNode sections = (ArrayNode) json.path("action").path("sections");
            assertThat(sections).isNotEmpty();
            int totalFilas = 0;
            for (JsonNode sec : sections) {
                String secTitle = sec.path("title").asString();
                assertThat(secTitle).isNotEmpty();
                assertThat(secTitle.length()).isLessThanOrEqualTo(24);

                ArrayNode rows = (ArrayNode) sec.path("rows");
                assertThat(rows)
                        .as("Ninguna sección puede tener rows vacío")
                        .isNotEmpty();
                totalFilas += rows.size();
                for (JsonNode row : rows) {
                    String id = row.path("id").asString();
                    String title = row.path("title").asString();
                    String description = row.path("description").asString();
                    assertThat(CodigoSeleccion.decodificar(id))
                            .as("Código de selección válido para fila: %s", id)
                            .isPresent();
                    assertThat(title).isNotEmpty();
                    assertThat(title.length()).isLessThanOrEqualTo(24);
                    assertThat(description.length()).isLessThanOrEqualTo(72);
                }
            }
            assertThat(totalFilas).isBetween(1, 10);
        }
        return json;
    }

    private ArrayNode filasUnicaSeccion(ObjectNode json) {
        ArrayNode sections = (ArrayNode) json.path("action").path("sections");
        assertThat(sections).hasSize(1);
        return (ArrayNode) sections.get(0).path("rows");
    }
}
