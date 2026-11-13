package pe.barberturno.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.conversation.ConversacionesWhatsApp.EstadoConversacion;
import pe.barberturno.reservations.EstadoReserva;
import pe.barberturno.whatsapp.EntradaRegistrada;
import pe.barberturno.whatsapp.MensajeEntrante;
import pe.barberturno.whatsapp.OrigenMensaje;
import pe.barberturno.whatsapp.RegistroEntradaWhatsApp;
import pe.barberturno.whatsapp.SalidaWhatsApp;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Enrutado de la conversación guiada (13-v2-10 §2, §7 y §8; CJ-3, CJ-9) y, desde V2-11, de la derivación
 * (16-v2-11 §2; CB-3, CB-4, CB-5) con dobles de todas sus dependencias: qué se consulta, propone, ejecuta,
 * guarda y encola en cada caso, y qué no se toca.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
class OrquestadorConversacionTest {
    private static final long NEGOCIO = 7, CONVERSACION = 30, FICHA = 40;
    private static final Instant AHORA = Instant.parse("2026-10-29T15:00:00Z"); // 10:00 en Lima
    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private RegistroEntradaWhatsApp registro;
    private ConversacionesWhatsApp conversaciones;
    private ConsentimientoConversacion consentimiento;
    private InterpreteEntradas interprete;
    private MenusConversacion menus;
    private TextosConversacion textos;
    private ConsultasConversacion consultas;
    private OperacionesConversacion operaciones;
    private SalidaWhatsApp salida;
    private DerivacionConversacion derivacion;
    private pe.barberturno.conversation.ia.TurnosIA turnos;
    private pe.barberturno.conversation.ia.LimitesIA limites;
    private OrquestadorConversacion orquestador;
    private final ObjectNode interactivo = JsonMapper.builder().build().createObjectNode().put("type", "button");

    @BeforeEach
    void preparar() {
        registro = mock(RegistroEntradaWhatsApp.class);
        conversaciones = mock(ConversacionesWhatsApp.class);
        consentimiento = mock(ConsentimientoConversacion.class);
        interprete = mock(InterpreteEntradas.class);
        menus = mock(MenusConversacion.class);
        textos = mock(TextosConversacion.class);
        consultas = mock(ConsultasConversacion.class);
        operaciones = mock(OperacionesConversacion.class);
        salida = mock(SalidaWhatsApp.class);
        derivacion = mock(DerivacionConversacion.class);
        turnos = mock(pe.barberturno.conversation.ia.TurnosIA.class);
        limites = mock(pe.barberturno.conversation.ia.LimitesIA.class);
        var propiedades = new ConversacionPropiedades(
                new ConversacionPropiedades.Privacidad("Privacidad {negocio} {enlace}", "https://x.ejemplo.test"),
                Duration.ofHours(24), Duration.ofMinutes(10),
                new ConversacionPropiedades.Retencion(false, Duration.ofHours(1), 200, Duration.ofDays(30), null, null, null));
        orquestador = new OrquestadorConversacion(registro, conversaciones, consentimiento, interprete, menus, textos,
                consultas, operaciones, salida, propiedades, derivacion, bandeja(BandejaPropiedades.Entrantes.SOLO_HUMANO),
                turnos, limites, mock(VinculoProfesionalWhatsAppService.class), Clock.fixed(AHORA, ZoneOffset.UTC));
        when(registro.operativo(NEGOCIO)).thenReturn(true);
        when(consultas.nombreNegocio()).thenReturn("Barbería Norte");
        when(textos.bienvenida(anyString())).thenReturn("bienvenida");
        when(textos.noDisponible()).thenReturn("no disponible");
        when(menus.menuPrincipal(anyString())).thenReturn(interactivo);
    }

    private void entrada(Optional<Instant> anterior, OptionalLong ficha, ObjectNode estado, Entrada interpretada) {
        entrada(anterior, ficha, estado, interpretada, ModoConversacion.ASISTENTE);
    }

    private void entrada(Optional<Instant> anterior, OptionalLong ficha, ObjectNode estado, Entrada interpretada,
            ModoConversacion modo) {
        when(registro.registrar(any())).thenReturn(new EntradaRegistrada(CONVERSACION, anterior));
        when(conversaciones.bloquear(CONVERSACION))
                .thenReturn(new EstadoConversacion(CONVERSACION, "51999000111", ficha, estado, modo));
        when(interprete.interpretar(any(), any())).thenReturn(interpretada);
    }

    private static BandejaPropiedades bandeja(BandejaPropiedades.Entrantes entrantes) {
        return new BandejaPropiedades(false, entrantes, Duration.ofHours(24), Duration.ofMinutes(5), 200);
    }

    private void recibir() {
        orquestador.recibir(new MensajeEntrante(1, NEGOCIO, "51999000111", OptionalLong.empty(), "Ana Pérez",
                "wamid.x", "interactive", AHORA, mapper.createObjectNode()));
    }

    private static Entrada seleccion(CodigoSeleccion codigo) {
        return new Entrada.Seleccion(codigo);
    }

    @Test
    void negocioNoOperativo_noRegistraNiRespondeNiEscribe() {
        when(registro.operativo(NEGOCIO)).thenReturn(false);
        recibir();
        verify(registro, never()).registrar(any());
        verifyNoInteractions(conversaciones, consentimiento, interprete, consultas, operaciones, salida);
    }

    @Test
    void entradaTrasMasDe24Horas_vaciaElEstadoYAnulaLaVigenteAntesDeAtender() {
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PASO, "CONFIRMACION");
        entrada(Optional.of(AHORA.minus(Duration.ofHours(24))), OptionalLong.of(FICHA), estado,
                seleccion(new CodigoSeleccion.Menu(OpcionMenu.RESERVAR)));
        var servicio = new ServicioOpcion(3, "Corte", 30, new BigDecimal("25.00"));
        when(consultas.servicios()).thenReturn(java.util.List.of(servicio));
        when(textos.elegirServicio()).thenReturn("elige");
        when(menus.servicios("elige", java.util.List.of(servicio), 0)).thenReturn(interactivo);
        recibir();
        verify(conversaciones).vaciar(CONVERSACION);
        verify(operaciones).anularVigente(CONVERSACION);
        verify(salida).encolarInteractivo(CONVERSACION, interactivo, OrigenMensaje.ASISTENTE, OptionalLong.empty());
    }

    @Test
    void entradaReciente_conservaElEstadoYLaOperacion() {
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PASO, "CONFIRMACION");
        entrada(Optional.of(AHORA.minus(Duration.ofHours(23))), OptionalLong.of(FICHA), estado,
                seleccion(new CodigoSeleccion.Menu(OpcionMenu.RESERVAR)));
        var servicio = new ServicioOpcion(3, "Corte", 30, new BigDecimal("25.00"));
        when(consultas.servicios()).thenReturn(java.util.List.of(servicio));
        when(textos.elegirServicio()).thenReturn("elige");
        when(menus.servicios("elige", java.util.List.of(servicio), 0)).thenReturn(interactivo);
        recibir();
        verify(conversaciones, never()).vaciar(anyLong());
        verify(operaciones, never()).anularVigente(anyLong());
    }

    @Test
    void franjaSinFicha_guardaLaFranjaPendienteYPideConsentimientoSinProponer() {
        var franja = new CodigoSeleccion.Franja(3, 4, AHORA.plus(Duration.ofDays(1)).getEpochSecond());
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(), seleccion(franja));
        when(textos.privacidad("Privacidad {negocio} {enlace}", "Barbería Norte", "https://x.ejemplo.test")).thenReturn("aviso");
        when(menus.consentimiento("aviso")).thenReturn(interactivo);
        recibir();
        var guardado = ArgumentCaptor.forClass(ObjectNode.class);
        verify(conversaciones).guardar(eq(CONVERSACION), guardado.capture());
        assertThat(guardado.getValue().path(ConversacionesWhatsApp.PENDIENTE).asString()).isEqualTo(franja.codificar());
        verify(menus).consentimiento("aviso");
        verifyNoInteractions(operaciones, consentimiento);
    }

    @Test
    void aceptarConFranjaPendiente_creaLaFichaYProponeEsaFranja() {
        var franja = new CodigoSeleccion.Franja(3, 4, AHORA.plus(Duration.ofDays(1)).getEpochSecond());
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PENDIENTE, franja.codificar())
                .put(ConversacionesWhatsApp.PASO, "CONSENTIMIENTO");
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), estado, seleccion(new CodigoSeleccion.Privacidad(true)));
        when(consentimiento.aceptar(any(), eq("Ana Pérez"))).thenReturn(FICHA);
        var resumen = new ResumenOperacion(TipoOperacion.CREAR, "Corte", "Carlos", AHORA, AHORA, 30,
                new BigDecimal("25.00"), Optional.empty());
        when(operaciones.proponerCreacion(CONVERSACION, FICHA, 3, 4, Instant.ofEpochSecond(franja.inicioEpochSeg())))
                .thenReturn(new Propuesta.Creada(99, "AAAAAAAAAAAAAAAAAAAAAA", resumen));
        when(textos.resumen(resumen)).thenReturn("resumen");
        when(menus.confirmacion("resumen", 99, "AAAAAAAAAAAAAAAAAAAAAA")).thenReturn(interactivo);
        recibir();
        verify(consentimiento).aceptar(any(), eq("Ana Pérez"));
        verify(menus).confirmacion("resumen", 99, "AAAAAAAAAAAAAAAAAAAAAA");
    }

    @Test
    void rechazarPrivacidad_noCreaFichaNiPropone() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Privacidad(false)));
        when(textos.privacidadRechazada()).thenReturn("sin ficha");
        recibir();
        verifyNoInteractions(consentimiento);
        verify(operaciones, never()).proponerCreacion(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(menus).menuPrincipal("sin ficha");
    }

    @Test
    void aceptarSinAvisoPresentado_noCreaFicha() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Privacidad(true)));
        recibir();
        verifyNoInteractions(consentimiento);
        verify(operaciones, never()).proponerCreacion(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(menus).menuPrincipal("bienvenida");
    }

    @Test
    void textoNoReconocidoTrasUnaPropuesta_anulaLaVigenteYVaciaElEstado() {
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PASO, "CONFIRMACION");
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), estado, new Entrada.Texto(PalabraClave.OTRA));
        recibir();
        verify(operaciones).anularVigente(CONVERSACION);
        verify(conversaciones).vaciar(CONVERSACION);
        verify(operaciones, never()).confirmarVigente(anyLong(), anyLong());
    }

    @Test
    void franjaConInicioNoRepresentable_noProponeYRespondeNoDisponible() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Franja(3, 4, 999_999_999_999_999_999L)));
        recibir();
        verify(operaciones, never()).proponerCreacion(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(menus).menuPrincipal("no disponible");
    }

    @Test
    void franjaCambioConInicioNoRepresentable_noProponeYRespondeNoDisponible() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.FranjaCambio(245, 999_999_999_999_999_999L)));
        recibir();
        verify(operaciones, never()).proponerReprogramacion(anyLong(), anyLong(), anyLong(), any());
        verify(menus).menuPrincipal("no disponible");
    }

    @Test
    void confirmacionSinFicha_respondeNoVigenteSinTocarOperaciones() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Confirmacion(5, "AAAAAAAAAAAAAAAAAAAAAA", true)));
        var noVigente = new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA);
        when(textos.resultado(noVigente)).thenReturn("no válida");
        recibir();
        verify(operaciones, never()).confirmar(anyLong(), anyLong(), anyLong(), anyString());
        verify(operaciones, never()).anular(anyLong(), anyLong(), anyLong(), anyString());
        verify(operaciones, never()).anularVigente(anyLong());
        verify(salida).encolarTexto(CONVERSACION, "no válida", OrigenMensaje.ASISTENTE, OptionalLong.empty());
        verify(menus, never()).menuPrincipal(anyString());
    }

    @Test
    void confirmacionConNonceIncorrecto_respondeSoloTextoYConservaLaVigente() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Confirmacion(5, "AAAAAAAAAAAAAAAAAAAAAA", true)));
        var noVigente = new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA);
        when(operaciones.confirmar(CONVERSACION, FICHA, 5, "AAAAAAAAAAAAAAAAAAAAAA")).thenReturn(noVigente);
        when(textos.resultado(noVigente)).thenReturn("no válida");
        recibir();
        verify(operaciones, never()).anularVigente(anyLong());
        verify(conversaciones, never()).vaciar(anyLong());
        verify(salida).encolarTexto(CONVERSACION, "no válida", OrigenMensaje.ASISTENTE, OptionalLong.empty());
    }

    @Test
    void repetirUnaEjecutadaConOtraVigente_anulaLaVigenteAlMostrarElMenu() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Confirmacion(5, "AAAAAAAAAAAAAAAAAAAAAA", true)));
        var cita = new CitaOpcion(245, "BT-245", 3, "Corte", 4, "Carlos", AHORA, AHORA.plusSeconds(1800),
                new BigDecimal("25.00"), EstadoReserva.CONFIRMADA, 0, true, true);
        var repetida = new ResultadoOperacion.Ejecutada(TipoOperacion.CREAR, cita, true);
        when(operaciones.confirmar(CONVERSACION, FICHA, 5, "AAAAAAAAAAAAAAAAAAAAAA")).thenReturn(repetida);
        when(textos.resultado(repetida)).thenReturn("listo");
        recibir();
        verify(operaciones).anularVigente(CONVERSACION);
        verify(salida).encolarInteractivo(CONVERSACION, interactivo, OrigenMensaje.SISTEMA, OptionalLong.of(245));
    }

    @Test
    void aceptarConFranjaValidaPeroSinElPasoDeConsentimiento_noCreaFicha() {
        var franja = new CodigoSeleccion.Franja(3, 4, AHORA.plus(Duration.ofDays(1)).getEpochSecond());
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PENDIENTE, franja.codificar())
                .put(ConversacionesWhatsApp.PASO, "FRANJA");
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), estado, seleccion(new CodigoSeleccion.Privacidad(true)));
        recibir();
        verifyNoInteractions(consentimiento);
    }

    @Test
    void franjaConUnSegundoMasQueElMaximo_noPropone() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Franja(3, 4, OrquestadorConversacion.INSTANTE_MAXIMO + 1)));
        recibir();
        verify(operaciones, never()).proponerCreacion(anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void siSinOperacionVigente_muestraElMenu() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.SI));
        when(operaciones.confirmarVigente(CONVERSACION, FICHA))
                .thenReturn(new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
        recibir();
        verify(menus).menuPrincipal("bienvenida");
    }

    @Test
    void ejecutada_vaciaElEstadoYEncolaElResultadoComoSistemaConLaReserva() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Confirmacion(5, "AAAAAAAAAAAAAAAAAAAAAA", true)));
        var cita = new CitaOpcion(245, "BT-245", 3, "Corte", 4, "Carlos", AHORA, AHORA.plusSeconds(1800),
                new BigDecimal("25.00"), EstadoReserva.CONFIRMADA, 0, true, true);
        var ejecutada = new ResultadoOperacion.Ejecutada(TipoOperacion.CREAR, cita, false);
        when(operaciones.confirmar(CONVERSACION, FICHA, 5, "AAAAAAAAAAAAAAAAAAAAAA")).thenReturn(ejecutada);
        when(textos.resultado(ejecutada)).thenReturn("listo");
        recibir();
        verify(conversaciones).vaciar(CONVERSACION);
        verify(salida).encolarInteractivo(CONVERSACION, interactivo, OrigenMensaje.SISTEMA, OptionalLong.of(245));
    }

    @Test
    void volverAlMenu_anulaLaVigenteYVaciaElEstado() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Menu(OpcionMenu.INICIO)));
        recibir();
        verify(operaciones).anularVigente(CONVERSACION);
        verify(conversaciones).vaciar(CONVERSACION);
        verify(menus).menuPrincipal("bienvenida");
    }

    @Test
    void diaFueraDeLosSieteOfrecidos_noConsultaFranjas() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Dia(3, 0, HOY.plusDays(7), 0)));
        when(menus.dias(anyString(), anyLong(), anyLong(), any())).thenReturn(interactivo);
        recibir();
        verify(consultas, never()).franjas(anyLong(), any(), any());
        verify(menus).dias("no disponible", 3, 0, HOY);
    }

    @Test
    void citaQueNoEsDeLaFicha_respondeNoDisponibleSinAcciones() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Cita(245)));
        when(consultas.cita(FICHA, 245)).thenReturn(Optional.empty());
        recibir();
        verify(menus, never()).accionesCita(anyString(), any());
        verify(menus).menuPrincipal("no disponible");
    }

    @Test
    void propuestaRechazada_respondeConLaReglaYSinConfirmacion() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.AccionCita(245, true)));
        when(operaciones.proponerCancelacion(CONVERSACION, FICHA, 245))
                .thenReturn(new Propuesta.Rechazada(ErrorCodigo.FUERA_DE_POLITICA));
        when(textos.rechazo(ErrorCodigo.FUERA_DE_POLITICA)).thenReturn("ya no se puede");
        recibir();
        verify(menus, never()).confirmacion(anyString(), anyLong(), anyString());
        verify(menus).menuPrincipal("ya no se puede");
    }

    @Test
    void misCitasSinFicha_noConsultaCitas() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Menu(OpcionMenu.MIS_CITAS)));
        when(textos.sinCitas()).thenReturn("sin citas");
        recibir();
        verify(consultas, never()).citasFuturas(anyLong());
        verify(menus).menuPrincipal("sin citas");
    }

    @Test
    void modoHumano_guardaElEntranteYNoInterpretaNiRespondeNiConfirma() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Confirmacion(9, "AAAAAAAAAAAAAAAAAAAAAA", true)), ModoConversacion.HUMANO);
        recibir();
        verify(registro).guardarEntrante(eq(CONVERSACION), any());
        verify(interprete).interpretar(any(), any());
        verifyNoInteractions(operaciones, salida, derivacion, consentimiento);
        verify(conversaciones, never()).guardar(anyLong(), any());
    }

    @Test
    void modoHumano_conPalabraInicio_anulaOperacionDevuelveAlAsistenteYEnviaBienvenida() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.MENU), ModoConversacion.HUMANO);
        when(textos.bienvenida("Barbería Norte")).thenReturn("bienvenida");
        when(menus.menuPrincipal("bienvenida")).thenReturn(interactivo);
        recibir();
        verify(registro).guardarEntrante(eq(CONVERSACION), any());
        verify(operaciones).anularVigente(CONVERSACION);
        verify(derivacion).devolver(CONVERSACION, OptionalLong.empty());
        verify(salida).encolarInteractivo(CONVERSACION, interactivo, OrigenMensaje.SISTEMA, OptionalLong.empty());
    }

    @Test
    void personaConFicha_guardaElEntranteUnaVezYDerivaPorPeticion() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.PERSONA));
        recibir();
        verify(registro).guardarEntrante(eq(CONVERSACION), any());
        verify(derivacion).derivar(CONVERSACION, MotivoDerivacion.PETICION);
        verify(salida, never()).encolarInteractivo(anyLong(), any(), any(), any());
    }

    @Test
    void botonHablarConAlguienConFichaYEntrantesTodos_guardaUnaSolaVezYDeriva() {
        var propiedades = new ConversacionPropiedades(
                new ConversacionPropiedades.Privacidad("Privacidad {negocio} {enlace}", "https://x.ejemplo.test"),
                Duration.ofHours(24), Duration.ofMinutes(10),
                new ConversacionPropiedades.Retencion(false, Duration.ofHours(1), 200, Duration.ofDays(30), null, null, null));
        orquestador = new OrquestadorConversacion(registro, conversaciones, consentimiento, interprete, menus, textos,
                consultas, operaciones, salida, propiedades, derivacion, bandeja(BandejaPropiedades.Entrantes.TODOS),
                turnos, limites, mock(VinculoProfesionalWhatsAppService.class), Clock.fixed(AHORA, ZoneOffset.UTC));
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Menu(OpcionMenu.PERSONA)));
        recibir();
        verify(registro).guardarEntrante(eq(CONVERSACION), any());
        verify(derivacion).derivar(CONVERSACION, MotivoDerivacion.PETICION);
    }

    @Test
    void personaSinFicha_pideElConsentimientoConLaDerivacionPendienteSinDerivarNiGuardar() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(),
                seleccion(new CodigoSeleccion.Menu(OpcionMenu.PERSONA)));
        when(textos.privacidad("Privacidad {negocio} {enlace}", "Barbería Norte", "https://x.ejemplo.test")).thenReturn("aviso");
        when(menus.consentimiento("aviso")).thenReturn(interactivo);
        var guardado = ArgumentCaptor.forClass(ObjectNode.class);
        recibir();
        verify(conversaciones).guardar(eq(CONVERSACION), guardado.capture());
        assertThat(guardado.getValue().path(ConversacionesWhatsApp.PENDIENTE).asString()).isEqualTo("m:PERSONA");
        assertThat(guardado.getValue().path(OrquestadorConversacion.MOTIVO).asString()).isEqualTo("PETICION");
        assertThat(guardado.getValue().path(ConversacionesWhatsApp.PASO).asString()).isEqualTo("CONSENTIMIENTO");
        verify(salida).encolarInteractivo(CONVERSACION, interactivo, OrigenMensaje.ASISTENTE, OptionalLong.empty());
        verifyNoInteractions(derivacion);
        verify(registro, never()).guardarEntrante(anyLong(), any());
    }

    @Test
    void aceptarConDerivacionPendiente_creaLaFichaGuardaLaAceptacionYDerivaConElMotivoGuardado() {
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PENDIENTE, "m:PERSONA")
                .put(OrquestadorConversacion.MOTIVO, "SIN_PROGRESO").put(ConversacionesWhatsApp.PASO, "CONSENTIMIENTO");
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), estado,
                seleccion(new CodigoSeleccion.Privacidad(true)));
        recibir();
        verify(consentimiento).aceptar(any(), eq("Ana Pérez"));
        verify(registro).guardarEntrante(eq(CONVERSACION), any());
        verify(derivacion).derivar(CONVERSACION, MotivoDerivacion.SIN_PROGRESO);
        verify(operaciones, never()).proponerCreacion(anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void rechazarConDerivacionPendiente_noCreaFichaNiDerivaYExplicaQueSinAceptarNoSePuede() {
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PENDIENTE, "m:PERSONA")
                .put(ConversacionesWhatsApp.PASO, "CONSENTIMIENTO");
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), estado,
                seleccion(new CodigoSeleccion.Privacidad(false)));
        when(textos.consentimientoDerivacionRechazado()).thenReturn("sin aceptar no");
        recibir();
        verifyNoInteractions(consentimiento, derivacion);
        verify(menus).menuPrincipal("sin aceptar no");
    }

    @Test
    void aceptarDerivacionSinQueSeHayaPedido_noCreaFichaNiDeriva() {
        var estado = mapper.createObjectNode().put(ConversacionesWhatsApp.PENDIENTE, "m:PERSONA");
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), estado,
                seleccion(new CodigoSeleccion.Privacidad(true)));
        recibir();
        verifyNoInteractions(consentimiento, derivacion);
    }

    @Test
    void primerTextoNoReconocido_respondeConElAvisoYGuardaElContadorTrasVaciarElMenu() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.OTRA));
        when(textos.noEntendido(1)).thenReturn("no te entendí");
        var guardado = ArgumentCaptor.forClass(ObjectNode.class);
        recibir();
        verify(menus).menuPrincipal("no te entendí");
        var orden = org.mockito.Mockito.inOrder(conversaciones);
        orden.verify(conversaciones).vaciar(CONVERSACION);
        orden.verify(conversaciones).guardar(eq(CONVERSACION), guardado.capture());
        assertThat(guardado.getValue().path(OrquestadorConversacion.FALLOS).asInt()).isEqualTo(1);
        verifyNoInteractions(derivacion);
    }

    @Test
    void segundoTextoNoReconocidoSeguido_derivaPorSinProgreso() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA),
                mapper.createObjectNode().put(OrquestadorConversacion.FALLOS, 1), new Entrada.Texto(PalabraClave.OTRA));
        recibir();
        verify(derivacion).derivar(CONVERSACION, MotivoDerivacion.SIN_PROGRESO);
        verify(registro).guardarEntrante(eq(CONVERSACION), any());
        verify(menus, never()).menuPrincipal(anyString());
    }

    @Test
    void entradaReconocidaTrasUnFallo_reiniciaElContador() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA),
                mapper.createObjectNode().put(OrquestadorConversacion.FALLOS, 1),
                seleccion(new CodigoSeleccion.Menu(OpcionMenu.MIS_CITAS)));
        when(consultas.citasFuturas(FICHA)).thenReturn(java.util.List.of());
        when(textos.sinCitas()).thenReturn("sin citas");
        var guardado = ArgumentCaptor.forClass(ObjectNode.class);
        recibir();
        verify(conversaciones, org.mockito.Mockito.atLeastOnce()).guardar(eq(CONVERSACION), guardado.capture());
        assertThat(guardado.getAllValues().getFirst().has(OrquestadorConversacion.FALLOS)).isFalse();
        verifyNoInteractions(derivacion);
    }

    @Test
    void segundoTextoNoReconocidoSinFicha_pideConsentimientoParaDerivar() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(),
                mapper.createObjectNode().put(OrquestadorConversacion.FALLOS, 1), new Entrada.Texto(PalabraClave.OTRA));
        when(textos.privacidad("Privacidad {negocio} {enlace}", "Barbería Norte", "https://x.ejemplo.test")).thenReturn("aviso");
        when(menus.consentimiento("aviso")).thenReturn(interactivo);
        var guardado = ArgumentCaptor.forClass(ObjectNode.class);
        recibir();
        verify(conversaciones).guardar(eq(CONVERSACION), guardado.capture());
        assertThat(guardado.getValue().path(OrquestadorConversacion.MOTIVO).asString()).isEqualTo("SIN_PROGRESO");
        assertThat(guardado.getValue().has(OrquestadorConversacion.FALLOS)).isFalse();
        verifyNoInteractions(derivacion);
    }

    private void recibirTexto(String cuerpo) {
        var dato = mapper.createObjectNode();
        dato.putObject("text").put("body", cuerpo);
        orquestador.recibir(new MensajeEntrante(1, NEGOCIO, "51999000111", OptionalLong.empty(), "Ana Pérez",
                "wamid.x", "text", AHORA, dato));
    }

    @Test
    void textoLibreConIaDisponible_encolaElTurnoSaneadoConLaRevisionYNoResponde() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.OTRA));
        when(limites.disponibilidad(CONVERSACION)).thenReturn(pe.barberturno.conversation.ia.DisponibilidadIA.DISPONIBLE);
        when(conversaciones.revision(CONVERSACION)).thenReturn(17L);
        recibirTexto("quiero corte mañana, mi cel 987 654 321 y ana@ejemplo.test");
        verify(turnos).encolar(CONVERSACION, 17L, "quiero corte mañana, mi cel [dato] y [dato]");
        verify(salida, never()).encolarInteractivo(anyLong(), any(), any(), any());
        verify(conversaciones, never()).guardar(anyLong(), any());
        verifyNoInteractions(derivacion);
    }

    @Test
    void textoLibreSinFichaConIaDisponible_tambienVaALaIA() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.empty(), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.OTRA));
        when(limites.disponibilidad(CONVERSACION)).thenReturn(pe.barberturno.conversation.ia.DisponibilidadIA.DISPONIBLE);
        recibirTexto("¿hay espacio el sábado?");
        verify(turnos).encolar(eq(CONVERSACION), anyLong(), eq("¿hay espacio el sábado?"));
        verify(consentimiento, never()).aceptar(any(), anyString());
    }

    @Test
    void textoLibreConLimiteAlcanzado_sigueElCaminoDeV2_11() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.OTRA));
        when(limites.disponibilidad(CONVERSACION)).thenReturn(pe.barberturno.conversation.ia.DisponibilidadIA.LIMITE_MINUTO);
        when(textos.noEntendido(1)).thenReturn("no te entendí");
        recibirTexto("hola qué tal todo");
        verifyNoInteractions(turnos);
        verify(menus).menuPrincipal("no te entendí");
    }

    @Test
    void palabraClaveYBotonesConIaDisponible_noVanALaIA() {
        entrada(Optional.of(AHORA.minusSeconds(60)), OptionalLong.of(FICHA), mapper.createObjectNode(),
                new Entrada.Texto(PalabraClave.PERSONA));
        when(limites.disponibilidad(CONVERSACION)).thenReturn(pe.barberturno.conversation.ia.DisponibilidadIA.DISPONIBLE);
        recibirTexto("hablar con alguien");
        verifyNoInteractions(turnos);
        verify(derivacion).derivar(CONVERSACION, MotivoDerivacion.PETICION);
    }
}
