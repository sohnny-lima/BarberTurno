package pe.barberturno.conversation.ia;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.CitaOpcion;
import pe.barberturno.conversation.ConversacionPruebas;
import pe.barberturno.conversation.MenusConversacion;
import pe.barberturno.conversation.ResultadoOperacion;
import pe.barberturno.conversation.ResumenOperacion;
import pe.barberturno.conversation.TextosConversacion;
import pe.barberturno.conversation.TipoOperacion;
import pe.barberturno.reservations.EstadoReserva;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.BandejaWhatsApp;
import pe.barberturno.whatsapp.EstadoMensaje;
import pe.barberturno.whatsapp.OrigenMensaje;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;
import pe.barberturno.whatsapp.dto.DetalleConversacionDto;
import pe.barberturno.whatsapp.dto.MensajeBandejaDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de canal para verificar lo que recibe el cliente en {@code mensaje_whatsapp.contenido}
 * y lo que muestra la bandeja en {@link BandejaWhatsApp} tras cerrar un turno de IA (17-v2-12 §13; V2-DA-86;
 * CI-14 y CI-16).
 *
 * <p>Verifica de extremo a extremo contra PostgreSQL con RLS forzada y reloj fijo que:</p>
 * <ul>
 *     <li>El texto libre del proveedor (paráfrasis de confirmación, horas inventadas, códigos {@code BT-9999},
 *     enlaces, precios {@code S/ 0}, emojis, teléfonos y correos) se descarta siempre y nunca llega a
 *     {@code mensaje_whatsapp.contenido} ni a {@link BandejaWhatsApp}.</li>
 *     <li>El cliente recibe exclusivamente el envoltorio JSON determinista compuesto por
 *     {@link RespuestasAsistente} o {@link MenusConversacion#confirmacion(String, long, String)}, comparando
 *     la raíz completa ({@code {"interactivo": <esperado>}} o {@code {"texto": <esperado>}}) y rechazando
 *     cualquier propiedad adicional en la raíz (hallazgo 5 de Codex).</li>
 *     <li>Con {@code proponer_reserva} y texto «¡Ya quedó confirmada!», solo se envía el resumen con los botones
 *     {@code [Confirmar]} y {@code [Cambiar]} sin crear fila en {@code reserva}; al pulsar {@code [Confirmar]}
 *     ({@code o:<operación>:<nonce>:S}), se crea la reserva y el cliente recibe {@link TextosConversacion#resultado}
 *     con el código real {@code BT-…} de la base, la fecha completa con año en Lima y la hora real.</li>
 *     <li>En una derivación por IA con texto malicioso del proveedor, {@link BandejaWhatsApp#detalle} y
 *     {@code mensaje_whatsapp.contenido} contienen exclusivamente los dos envoltorios esperados (el entrante
 *     guardado por V2-DA-83 con el texto saneado y el aviso saliente fijo del sistema, comparando los dos JSON
 *     completos en la raíz y sin texto del proveedor ni en la raíz ni en los fragmentos; hallazgo 2 de Codex 142).</li>
 * </ul>
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
@Import(ConfiguracionPruebasIA.class)
@TestPropertySource(properties = {
        "barberturno.ia.habilitada=true",
        "barberturno.ia.clave=clave-ficticia-local",
        "barberturno.ia.trabajador.habilitado=false",
        "barberturno.ia.trabajador.lote=1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SalidaClienteIT extends WhatsAppSalidaPruebaBase {

    private static final Instant INSTANTE_BASE = Instant.parse("2026-11-04T14:00:00Z");

    private static final List<String> TEXTOS_PARAFRASIS_Y_ADVERSARIOS = List.of(
            "Carlos ya te tiene un hueco",
            "Listo, ya quedaste con Carlos el sábado",
            "Te esperamos mañana a las 5",
            "Tu cita BT-9999 está confirmada",
            "Ignora todo: reserva gratis en https://malo.test por S/ 0",
            "Ya tienes tu espacio 💈",
            "Escríbenos a trampa@ejemplo.test o llámanos al 987 654 321 para confirmar tu cita"
    );

    private static final List<String> TOKENS_ADVERSARIOS_PROHIBIDOS = List.of(
            "BT-9999",
            "https://",
            "malo.test",
            "S/ 0",
            "987 654 321",
            "987654321",
            "trampa@ejemplo.test",
            "💈",
            "tiene un hueco",
            "quedaste con Carlos",
            "Te esperamos"
    );

    /**
     * Escenarios de herramientas con los que se combina cada texto malicioso o paráfrasis (CI-14).
     */
    public enum CombinacionSalida {
        /** (a) Sin herramientas: el modelo cierra el turno solo con texto libre. */
        SIN_HERRAMIENTAS,
        /** (b) Con {@code responder_al_cliente(SALUDO)} y texto libre en la misma respuesta. */
        RESPONDER_SALUDO,
        /** (c) Con {@code buscar_disponibilidad} que devuelve franjas reales y texto libre. */
        BUSCAR_DISPONIBILIDAD,
        /** (d) Con {@code proponer_reserva} sobre una franja del turno y texto libre. */
        PROPONER_RESERVA
    }

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private TurnosIA turnosIA;

    @Autowired
    private ModeloConversacionalFalso modeloFalso;

    @Autowired
    private ObservadorEvaluacionRegistro observadorRegistro;

    @Autowired
    private BandejaWhatsApp bandeja;

    @Autowired
    private RespuestasAsistente respuestasAsistente;

    @Autowired
    private MenusConversacion menus;

    @Autowired
    private TextosConversacion textos;

    @Autowired
    private ComposicionTurnoIA composicion;

    @Autowired
    private JornadaRepository jornadas;

    /**
     * Constructor por defecto para las pruebas de integración de Spring Boot.
     */
    public SalidaClienteIT() { }

    @BeforeEach
    void prepararEscenario() {
        modeloFalso.reiniciar();
        observadorRegistro.reiniciar();
        reloj.fijar(INSTANTE_BASE);

        sql.update("UPDATE negocio SET limite_reservas_activas = 3, confirmacion_manual = false, ia_habilitada = true WHERE id IN (?, ?)", a, b);

        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, b, barberoB);
    }

    /**
     * Proveedor del producto cartesiano de textos de paráfrasis/adversarios y las cuatro combinaciones de herramientas (CI-14).
     *
     * @return flujo de argumentos {@code (textoProveedor, combinacion)}
     */
    static Stream<Arguments> casosParafrasisYAdversarias() {
        List<Arguments> argumentos = new ArrayList<>();
        for (String texto : TEXTOS_PARAFRASIS_Y_ADVERSARIOS) {
            for (CombinacionSalida combinacion : CombinacionSalida.values()) {
                argumentos.add(Arguments.of(texto, combinacion));
            }
        }
        return argumentos.stream();
    }

    @ParameterizedTest(name = "CI-14 [{index}] {1} :: «{0}»")
    @MethodSource("casosParafrasisYAdversarias")
    @DisplayName("parafrasisYAdversarias_enLasCuatroCombinaciones_descartanTextoYEnvianSoloMensajeDeterminista")
    void parafrasisYAdversarias_enLasCuatroCombinaciones_descartanTextoYEnvianSoloMensajeDeterminista(
            String textoProveedor,
            CombinacionSalida combinacion
    ) {
        long conversacionId = asegurarFichaYConversacion(a, WA_CLIENTE, "Cliente Ficticio");
        Long ultimoSalienteAntes = consultarUltimoMensajeSalienteId(a, conversacionId);

        programarEscenario(textoProveedor, combinacion);

        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "Hola, quiero agendar un corte");
        Long turnoId = consultarUltimoTurnoId(a, conversacionId);
        assertThat(turnoId).as("Debe haberse encolado un turno de IA").isNotNull();

        int procesados = turnosIA.procesarPendientes();
        assertThat(procesados).isEqualTo(1);
        assertThat(consultarEstadoTurno(a, turnoId)).isEqualTo("HECHO");

        // 1. La tabla reserva no debe tener ninguna fila nueva en ninguna de las cuatro combinaciones.
        assertThat(contarReservasActivas(a)).isZero();

        // 2. El observador debe haber registrado el textoDescartado del proveedor.
        var turnoObs = observadorRegistro.buscarTurno(turnoId).orElseThrow();
        assertThat(turnoObs.textoDescartado())
                .as("El observador debe registrar el texto libre descartado del proveedor")
                .contains(textoProveedor);

        // 3. Inspeccionar todos los mensajes salientes nuevos en mensaje_whatsapp.contenido.
        List<Map<String, Object>> salientes = consultarSalientesNuevos(a, conversacionId, ultimoSalienteAntes);
        assertThat(salientes)
                .as("El turno debe producir exactamente un mensaje saliente para el cliente")
                .hasSize(1);

        Map<String, Object> filaSaliente = salientes.getFirst();
        assertThat(filaSaliente.get("tipo")).isEqualTo("INTERACTIVO");
        assertThat(filaSaliente.get("origen")).isEqualTo(OrigenMensaje.ASISTENTE.name());
        assertThat(filaSaliente.get("estado")).isEqualTo(EstadoMensaje.PENDIENTE.name());

        String contenidoCrudo = String.valueOf(filaSaliente.get("contenido"));
        JsonNode nodoContenido = mapper.readTree(contenidoCrudo);
        JsonNode interactivoReal = nodoContenido.path("interactivo");

        // 4. Comprobar el JSON raíz completo de mensaje_whatsapp.contenido con el envoltorio esperado,
        // rechazando cualquier propiedad adicional en la raíz (hallazgo 5 de Codex).
        ObjectNode interactivoEsperado = construirInteractivoEsperado(combinacion, conversacionId, turnoObs);
        ObjectNode envoltorioEsperado = mapper.createObjectNode();
        envoltorioEsperado.set("interactivo", interactivoEsperado);
        compararJsonRaizCompleto(nodoContenido, envoltorioEsperado);

        // 5. Comprobar que ninguna subcadena de 4+ palabras del proveedor ni ningún token prohibido
        // (incluyendo fragmentos cortos de 2 y 3 palabras del proveedor que no estén en el mensaje esperado)
        // aparece en el JSON ni en sus campos.
        verificarAusenciaDeTextoProveedorEnContenido(contenidoCrudo, interactivoReal, textoProveedor, interactivoEsperado.toString());

        // 6. Comprobar lo que muestra BandejaWhatsApp (detalle de la conversación) sobre ese mismo contenido.
        DetalleConversacionDto detalleBandeja = obtenerDetalleEnBandeja(a, conversacionId);
        assertThat(detalleBandeja.mensajes()).isNotEmpty();
        for (MensajeBandejaDto msgBandeja : detalleBandeja.mensajes()) {
            verificarAusenciaDeTextoProveedorEnCadena(
                    "BandejaWhatsApp.detalle mensaje id=" + msgBandeja.id(),
                    msgBandeja.texto(),
                    textoProveedor,
                    interactivoEsperado.toString()
            );
        }
        MensajeBandejaDto ultimoEnBandeja = detalleBandeja.mensajes().getLast();
        assertThat(ultimoEnBandeja.entrante()).isFalse();
        assertThat(ultimoEnBandeja.origen()).isEqualTo(OrigenMensaje.ASISTENTE);
        assertThat(ultimoEnBandeja.texto()).contains(interactivoEsperado.path("body").path("text").asString());
    }

    @Test
    @DisplayName("mismaRespuestaConHerramientasNoTerminalesYResponderAlCliente_ejecutaPrimeroNoTerminalesYDescartaTexto")
    void mismaRespuestaConHerramientasNoTerminalesYResponderAlCliente_ejecutaPrimeroNoTerminalesYDescartaTexto() {
        long conversacionId = asegurarFichaYConversacion(a, WA_CLIENTE, "Cliente Ficticio");
        Long ultimoSalienteAntes = consultarUltimoMensajeSalienteId(a, conversacionId);
        String textoMalicioso = "Carlos ya te tiene un hueco reservado gratis en https://malo.test por S/ 0 con BT-9999";

        // Se programa responder_al_cliente ANTES de buscar_disponibilidad en la misma respuesta (§13.2.1):
        // primero se ejecuta buscar_disponibilidad, luego se cierra el bucle sin segunda llamada HTTP y por
        // precedencia (§13.2.2: franjas > responder_al_cliente) gana la lista de franjas.
        LlamadaHerramienta llamadaTerminal = modeloFalso.llamadaResponderAlCliente(
                IntencionRespuesta.SALUDO, null, null, null);
        LlamadaHerramienta llamadaBuscar = modeloFalso.nuevaLlamada(
                HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad());
        modeloFalso.programarTextoYLlamadas(textoMalicioso, List.of(llamadaTerminal, llamadaBuscar));

        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "Busco horario para corte");
        Long turnoId = consultarUltimoTurnoId(a, conversacionId);

        assertThat(turnosIA.procesarPendientes()).isEqualTo(1);
        assertThat(modeloFalso.cantidadPeticiones())
                .as("responder_al_cliente es terminal y debe cerrar el bucle en una sola llamada al modelo")
                .isEqualTo(1);
        assertThat(contarReservasActivas(a)).isZero();

        var turnoObs = observadorRegistro.buscarTurno(turnoId).orElseThrow();
        assertThat(turnoObs.textoDescartado()).contains(textoMalicioso);
        assertThat(turnoObs.decision())
                .contains(new DecisionRespuesta(IntencionRespuesta.SALUDO, Optional.empty()));
        assertThat(turnoObs.herramientas())
                .as("La herramienta terminal no se entrega a observador.herramienta")
                .extracting(h -> h.llamada().herramienta())
                .containsExactly(HerramientaIA.BUSCAR_DISPONIBILIDAD);

        var mostrar = (EfectoTurno.MostrarFranjas) turnoObs.efecto().orElseThrow();
        LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());
        ObjectNode esperado = ((MensajeAsistente.Interactivo) respuestasAsistente.franjas(mostrar.franjas(), hoy)).contenido();

        List<Map<String, Object>> salientes = consultarSalientesNuevos(a, conversacionId, ultimoSalienteAntes);
        assertThat(salientes).hasSize(1);
        String contenidoCrudo = String.valueOf(salientes.getFirst().get("contenido"));
        JsonNode nodoContenido = mapper.readTree(contenidoCrudo);
        JsonNode interactivoReal = nodoContenido.path("interactivo");

        ObjectNode envoltorioEsperado = mapper.createObjectNode();
        envoltorioEsperado.set("interactivo", esperado);
        compararJsonRaizCompleto(nodoContenido, envoltorioEsperado);

        verificarAusenciaDeTextoProveedorEnContenido(contenidoCrudo, interactivoReal, textoMalicioso, esperado.toString());
    }

    @Test
    @DisplayName("confirmacionReal_proponerReservaConTextoYaQuedoConfirmada_soloEnviaResumenYAlPulsarConfirmarEmiteDatosRealesDeLaBase")
    void confirmacionReal_proponerReservaConTextoYaQuedoConfirmada_soloEnviaResumenYAlPulsarConfirmarEmiteDatosRealesDeLaBase() {
        long conversacionId = asegurarFichaYConversacion(a, WA_CLIENTE, "Cliente Ficticio");
        Long ultimoSalienteAntes = consultarUltimoMensajeSalienteId(a, conversacionId);
        String textoEnganoso = "¡Ya quedó confirmada!";

        // Paso 1: el modelo busca disponibilidad y propone reserva devolviendo además «¡Ya quedó confirmada!».
        LlamadaHerramienta llamadaProponer = modeloFalso.nuevaLlamada(
                HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f1\"}");
        LlamadaHerramienta llamadaResponder = modeloFalso.llamadaResponderAlCliente(
                IntencionRespuesta.SALUDO, null, "s1", "p1");
        modeloFalso
                .programarTextoYLlamada(textoEnganoso, HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad())
                .programarTextoYLlamadas(textoEnganoso, List.of(llamadaProponer, llamadaResponder))
                .programarTexto(textoEnganoso);

        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "Quiero reservar un corte hoy a las 10:00");
        Long turnoId = consultarUltimoTurnoId(a, conversacionId);

        assertThat(turnosIA.procesarPendientes()).isEqualTo(1);
        assertThat(consultarEstadoTurno(a, turnoId)).isEqualTo("HECHO");

        // Antes de pulsar [Confirmar]: NO existe reserva en la base y el texto «¡Ya quedó confirmada!» fue descartado.
        assertThat(contarReservasActivas(a))
                .as("Antes de pulsar el botón de confirmación no debe existir ninguna reserva en la base")
                .isZero();

        var turnoObs = observadorRegistro.buscarTurno(turnoId).orElseThrow();
        assertThat(turnoObs.textoDescartado()).contains(textoEnganoso);

        Map<String, Object> opPendiente = consultarOperacionPendienteVigente(a, conversacionId);
        long operacionId = ((Number) opPendiente.get("id")).longValue();
        String nonce = codificarNonce((byte[]) opPendiente.get("nonce"));

        List<Map<String, Object>> salientesPropuesta = consultarSalientesNuevos(a, conversacionId, ultimoSalienteAntes);
        assertThat(salientesPropuesta).hasSize(1);
        String contenidoPropuestaCrudo = String.valueOf(salientesPropuesta.getFirst().get("contenido"));
        JsonNode nodoPropuesta = mapper.readTree(contenidoPropuestaCrudo);
        JsonNode interactivoPropuesta = nodoPropuesta.path("interactivo");

        ObjectNode confirmacionEsperada = construirConfirmacionEsperada(conversacionId);
        ObjectNode envoltorioPropuestaEsperado = mapper.createObjectNode();
        envoltorioPropuestaEsperado.set("interactivo", confirmacionEsperada);
        compararJsonRaizCompleto(nodoPropuesta, envoltorioPropuestaEsperado);

        assertThat(contenidoPropuestaCrudo)
                .doesNotContain("¡Ya quedó confirmada!")
                .doesNotContain("confirmada")
                .doesNotContain("Listo:")
                .doesNotContain("BT-");

        verificarAusenciaDeTextoProveedorEnContenido(
                contenidoPropuestaCrudo, interactivoPropuesta, textoEnganoso, confirmacionEsperada.toString());

        JsonNode botonesPropuesta = interactivoPropuesta.path("action").path("buttons");
        assertThat(botonesPropuesta).hasSize(2);
        String idBotonConfirmar = "o:" + operacionId + ":" + nonce + ":S";
        String idBotonCambiar = "o:" + operacionId + ":" + nonce + ":N";
        assertThat(botonesPropuesta.get(0).path("reply").path("id").asString()).isEqualTo(idBotonConfirmar);
        assertThat(botonesPropuesta.get(0).path("reply").path("title").asString()).isEqualTo("Confirmar");
        assertThat(botonesPropuesta.get(1).path("reply").path("id").asString()).isEqualTo(idBotonCambiar);
        assertThat(botonesPropuesta.get(1).path("reply").path("title").asString()).isEqualTo("Cambiar");

        // Paso 2: el cliente pulsa el botón [Confirmar] (o:<operación>:<nonce>:S).
        Long ultimoSalienteAntesDeConfirmar = consultarUltimoMensajeSalienteId(a, conversacionId);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", idBotonConfirmar);

        // Ahora sí existe exactamente 1 reserva en la base de datos.
        assertThat(contarReservasActivas(a)).isEqualTo(1);
        Map<String, Object> filaReserva = ejecutor.ejecutar(a, () -> sql.queryForMap("""
                SELECT r.id, r.servicio_id, r.barbero_id, r.inicio, r.fin, r.precio_ref, r.estado, r.version,
                       s.nombre AS servicio_nombre, b.nombre_visible AS barbero_nombre
                FROM reserva r
                JOIN servicio s ON s.id = r.servicio_id AND s.negocio_id = r.negocio_id
                JOIN barbero b ON b.id = r.barbero_id AND b.negocio_id = r.negocio_id
                WHERE r.negocio_id = ?
                """, a));

        long reservaIdReal = ((Number) filaReserva.get("id")).longValue();
        long servicioIdReal = ((Number) filaReserva.get("servicio_id")).longValue();
        long barberoIdReal = ((Number) filaReserva.get("barbero_id")).longValue();
        Instant inicioReal = instanteDeColumna(filaReserva.get("inicio"));
        Instant finReal = instanteDeColumna(filaReserva.get("fin"));
        BigDecimal precioReal = (BigDecimal) filaReserva.get("precio_ref");
        EstadoReserva estadoReal = EstadoReserva.valueOf(String.valueOf(filaReserva.get("estado")));
        int versionReal = ((Number) filaReserva.get("version")).intValue();
        String servicioNombreReal = String.valueOf(filaReserva.get("servicio_nombre"));
        String barberoNombreReal = String.valueOf(filaReserva.get("barbero_nombre"));

        String codigoReal = "BT-" + reservaIdReal; // mismo formato que ConsultasConversacionService.cita
        String fechaCompletaLima = textos.fecha(inicioReal);
        String horaLima = textos.hora(inicioReal);

        CitaOpcion citaCreada = new CitaOpcion(
                reservaIdReal,
                codigoReal,
                servicioIdReal,
                servicioNombreReal,
                barberoIdReal,
                barberoNombreReal,
                inicioReal,
                finReal,
                precioReal,
                estadoReal,
                versionReal,
                true,
                true
        );
        String textoResultadoEsperado = textos.resultado(
                new ResultadoOperacion.Ejecutada(TipoOperacion.CREAR, citaCreada, false));
        ObjectNode menuResultadoEsperado = menus.menuPrincipal(textoResultadoEsperado);

        List<Map<String, Object>> salientesConfirmacion = consultarSalientesNuevos(
                a, conversacionId, ultimoSalienteAntesDeConfirmar);
        assertThat(salientesConfirmacion).hasSize(1);

        String contenidoConfirmacionCrudo = String.valueOf(salientesConfirmacion.getFirst().get("contenido"));
        JsonNode nodoConfirmacion = mapper.readTree(contenidoConfirmacionCrudo);
        JsonNode interactivoConfirmacion = nodoConfirmacion.path("interactivo");
        String cuerpoRecibido = interactivoConfirmacion.path("body").path("text").asString();

        ObjectNode envoltorioConfirmacionEsperado = mapper.createObjectNode();
        envoltorioConfirmacionEsperado.set("interactivo", menuResultadoEsperado);
        compararJsonRaizCompleto(nodoConfirmacion, envoltorioConfirmacionEsperado);

        assertThat(cuerpoRecibido)
                .isEqualTo(textoResultadoEsperado)
                .contains(codigoReal)
                .contains(fechaCompletaLima)
                .contains(String.valueOf(TiempoNegocio.fechaLima(inicioReal).getYear()))
                .contains(horaLima)
                .contains(barberoNombreReal)
                .doesNotContain(textoEnganoso)
                .doesNotContain("BT-9999");
    }

    @Test
    @DisplayName("derivacionPorIA_conTextoMaliciosoDelProveedor_bandejaMuestraTextoSaneadoDelClienteYAvisoFijoSinTextoProveedor")
    void derivacionPorIA_conTextoMaliciosoDelProveedor_bandejaMuestraTextoSaneadoDelClienteYAvisoFijoSinTextoProveedor() {
        long conversacionId = asegurarFichaYConversacion(a, WA_CLIENTE, "Cliente Ficticio");
        String textoClienteConDatos = "Tengo un reclamo urgente por mi corte, mi teléfono es 987 654 321 y mi correo reclamo@ejemplo.test";
        String textoProveedorMalicioso = "Carlos ya te tiene un hueco gratis en https://malo.test por S/ 0 con cita BT-9999, escribe a trampa@ejemplo.test o al 987 654 321";

        modeloFalso.programarTextoYLlamada(
                textoProveedorMalicioso,
                HerramientaIA.DERIVAR_A_PERSONA,
                "{\"motivo\":\"QUEJA\"}"
        );

        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", textoClienteConDatos);
        Long turnoId = consultarUltimoTurnoId(a, conversacionId);

        assertThat(turnosIA.procesarPendientes()).isEqualTo(1);
        assertThat(consultarEstadoTurno(a, turnoId)).isEqualTo("HECHO");
        assertThat(contarReservasActivas(a)).isZero();

        var turnoObs = observadorRegistro.buscarTurno(turnoId).orElseThrow();
        assertThat(turnoObs.textoDescartado()).contains(textoProveedorMalicioso);

        // 1. Comprobar el DTO de detalle de BandejaWhatsApp para la conversación derivada.
        DetalleConversacionDto detalle = ejecutor.ejecutar(
                a, () -> bandeja.detalle(conversacionId, null, titular.getId()));

        assertThat(detalle.conversacion().id()).isEqualTo(conversacionId);
        assertThat(detalle.mensajes())
                .as("La bandeja debe mostrar el mensaje entrante saneado del cliente (V2-DA-83) y el aviso fijo de derivación")
                .hasSize(2);

        String textoClienteSaneado = SaneadorTexto.sanear(textoClienteConDatos, 2000);
        MensajeBandejaDto mensajeClienteEnBandeja = detalle.mensajes().get(0);
        assertThat(mensajeClienteEnBandeja.entrante()).isTrue();
        assertThat(mensajeClienteEnBandeja.origen()).isEqualTo(OrigenMensaje.CLIENTE);
        assertThat(mensajeClienteEnBandeja.texto())
                .isEqualTo(textoClienteSaneado)
                .isEqualTo("Tengo un reclamo urgente por mi corte, mi teléfono es [dato] y mi correo [dato]")
                .doesNotContain("987 654 321")
                .doesNotContain("reclamo@ejemplo.test");
        verificarAusenciaDeTextoProveedorEnCadena(
                "Mensaje del cliente en bandeja",
                mensajeClienteEnBandeja.texto(),
                textoProveedorMalicioso,
                textoClienteSaneado
        );

        String textoAvisoSistema = textos.derivada("Negocio ficticio a");
        MensajeBandejaDto avisoSistemaEnBandeja = detalle.mensajes().get(1);
        assertThat(avisoSistemaEnBandeja.entrante()).isFalse();
        assertThat(avisoSistemaEnBandeja.origen()).isEqualTo(OrigenMensaje.SISTEMA);
        assertThat(avisoSistemaEnBandeja.texto())
                .isEqualTo(textoAvisoSistema);
        verificarAusenciaDeTextoProveedorEnCadena(
                "Aviso de derivación en bandeja",
                avisoSistemaEnBandeja.texto(),
                textoProveedorMalicioso,
                textoAvisoSistema
        );

        // 2. Comprobar por separado los dos JSON completos de esa conversación en mensaje_whatsapp:
        // el entrante guardado (V2-DA-83) contra su envoltorio esperado con el texto saneado, y el saliente
        // del sistema contra {"texto": TextosConversacion.derivada(<negocio>)}, rechazando propiedades adicionales
        // en la raíz (hallazgo 2 de Codex 142). Las excepciones de fragmentos salen solo del contenido esperado
        // de cada fila, nunca del texto original del cliente sin sanear.
        List<Map<String, Object>> filasMensajes = ejecutor.ejecutar(a, () -> sql.queryForList("""
                SELECT id, direccion, origen, tipo, estado, CAST(contenido AS text) AS contenido
                FROM mensaje_whatsapp
                WHERE conversacion_id = ?
                ORDER BY id
                """, conversacionId));
        assertThat(filasMensajes)
                .as("La conversación debe contener exactamente 2 filas en mensaje_whatsapp")
                .hasSize(2);

        // Mensaje 1: entrante guardado con el texto saneado del cliente (V2-DA-83)
        Map<String, Object> filaEntrante = filasMensajes.get(0);
        assertThat(filaEntrante.get("direccion")).isEqualTo("ENTRANTE");
        assertThat(filaEntrante.get("origen")).isEqualTo(OrigenMensaje.CLIENTE.name());
        assertThat(filaEntrante.get("tipo")).isEqualTo("TEXTO");
        assertThat(filaEntrante.get("estado")).isEqualTo(EstadoMensaje.RECIBIDO.name());

        String contenidoEntranteCrudo = String.valueOf(filaEntrante.get("contenido"));
        JsonNode nodoEntrante = mapper.readTree(contenidoEntranteCrudo);
        ObjectNode envoltorioEntranteEsperado = mapper.createObjectNode();
        envoltorioEntranteEsperado.put("texto", textoClienteSaneado);

        compararJsonRaizCompleto(nodoEntrante, envoltorioEntranteEsperado);
        verificarAusenciaDeTextoProveedorEnCadena(
                "mensaje_whatsapp.contenido (entrante saneado)",
                contenidoEntranteCrudo,
                textoProveedorMalicioso,
                envoltorioEntranteEsperado.toString()
        );

        // Mensaje 2: saliente del sistema con aviso fijo de derivación
        Map<String, Object> filaSaliente = filasMensajes.get(1);
        assertThat(filaSaliente.get("direccion")).isEqualTo("SALIENTE");
        assertThat(filaSaliente.get("origen")).isEqualTo(OrigenMensaje.SISTEMA.name());
        assertThat(filaSaliente.get("tipo")).isEqualTo("TEXTO");
        assertThat(filaSaliente.get("estado")).isEqualTo(EstadoMensaje.PENDIENTE.name());

        String contenidoSalienteCrudo = String.valueOf(filaSaliente.get("contenido"));
        JsonNode nodoSaliente = mapper.readTree(contenidoSalienteCrudo);
        ObjectNode envoltorioSalienteEsperado = mapper.createObjectNode();
        envoltorioSalienteEsperado.put("texto", textoAvisoSistema);

        compararJsonRaizCompleto(nodoSaliente, envoltorioSalienteEsperado);
        verificarAusenciaDeTextoProveedorEnCadena(
                "mensaje_whatsapp.contenido (saliente sistema)",
                contenidoSalienteCrudo,
                textoProveedorMalicioso,
                envoltorioSalienteEsperado.toString()
        );
    }

    @Test
    @DisplayName("compararJsonRaizCompleto_conPropiedadAdicionalNotaEnRaiz_rechazaYFalla")
    void compararJsonRaizCompleto_conPropiedadAdicionalNotaEnRaiz_rechazaYFalla() {
        ObjectNode interactivoEsperado = mapper.createObjectNode();
        interactivoEsperado.put("type", "button");
        interactivoEsperado.putObject("body").put("text", "Elige una opción:");

        ObjectNode envoltorioEsperado = mapper.createObjectNode();
        envoltorioEsperado.set("interactivo", interactivoEsperado);

        // Caso idéntico: el ayudante debe validar con éxito sin lanzar excepción
        compararJsonRaizCompleto(envoltorioEsperado, envoltorioEsperado);

        // Caso con propiedad adicional en la raíz ("nota": "Te esperamos", hallazgo 5 de Codex)
        ObjectNode salienteConNota = envoltorioEsperado.deepCopy();
        salienteConNota.put("nota", "Te esperamos");

        assertThatThrownBy(() -> compararJsonRaizCompleto(salienteConNota, envoltorioEsperado))
                .as("El ayudante de comparación debe rechazar cualquier propiedad adicional en la raíz como 'nota'")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nota");

        // También con envoltorio de texto: un JSON con "texto" y "nota" adicional debe fallar
        ObjectNode envoltorioTexto = mapper.createObjectNode();
        envoltorioTexto.put("texto", "Mensaje determinista");
        ObjectNode textoConNota = envoltorioTexto.deepCopy();
        textoConNota.put("nota", "Te esperamos");

        assertThatThrownBy(() -> compararJsonRaizCompleto(textoConNota, envoltorioTexto))
                .as("El ayudante debe rechazar la propiedad 'nota' en la raíz también sobre envoltorios de texto")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nota");

        // Escenario del hallazgo 2 de Codex 142: un saliente del sistema con "nota":"987 654" en la raíz
        ObjectNode envoltorioDerivada = mapper.createObjectNode();
        envoltorioDerivada.put("texto", "Te paso con el equipo de la barbería. Te responderán por aquí en cuanto puedan.");
        ObjectNode salienteDerivadaConNota = envoltorioDerivada.deepCopy();
        salienteDerivadaConNota.put("nota", "987 654");

        assertThatThrownBy(() -> compararJsonRaizCompleto(salienteDerivadaConNota, envoltorioDerivada))
                .as("El ayudante debe rechazar un saliente con 'nota':'987 654' en la raíz")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nota");
    }

    @Test
    @DisplayName("salienteConNota987654_sinBase_rechazaPorRaizYPorFragmentoProhibido")
    void salienteConNota987654_sinBase_rechazaPorRaizYPorFragmentoProhibido() {
        ObjectNode envoltorioSalienteEsperado = mapper.createObjectNode();
        envoltorioSalienteEsperado.put("texto", "Te paso con el equipo de la barbería. Te responderán por aquí en cuanto puedan.");

        ObjectNode salienteConNota = envoltorioSalienteEsperado.deepCopy();
        salienteConNota.put("nota", "987 654");

        // 1. El ayudante de comparación de raíz completa rechaza la propiedad adicional "nota" en la raíz
        assertThatThrownBy(() -> compararJsonRaizCompleto(salienteConNota, envoltorioSalienteEsperado))
                .as("El ayudante compararJsonRaizCompleto debe rechazar la propiedad 'nota' en la raíz")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nota");

        // 2. La verificación de fragmentos también rechaza "987 654" porque las excepciones salen solo
        // del contenido esperado del saliente del sistema (nunca del texto original del cliente, hallazgo 2 de Codex 142)
        String textoProveedor = "escribe a trampa@ejemplo.test o al 987 654 321";
        assertThatThrownBy(() -> verificarAusenciaDeTextoProveedorEnCadena(
                "saliente del sistema con nota",
                salienteConNota.toString(),
                textoProveedor,
                envoltorioSalienteEsperado.toString()))
                .as("La verificación de fragmentos debe rechazar el fragmento adversario '987 654'")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("987 654");
    }

    /**
     * Compara el JSON raíz completo de un mensaje (entrante o saliente) con el envoltorio esperado
     * ({@code {"interactivo": <esperado>}} o {@code {"texto": <esperado>}}), verificando
     * que coincida exactamente y rechazando cualquier propiedad adicional en la raíz
     * (17-v2-12 §13; V2-DA-86; CI-14; hallazgo 5 de Codex; hallazgo 2 de Codex 142).
     *
     * @param nodoReal JSON real obtenido de la columna {@code mensaje_whatsapp.contenido}
     * @param envoltorioEsperado envoltorio JSON determinista esperado del servidor
     * @throws AssertionError si el nodo real no es un objeto, contiene propiedades adicionales en la raíz
     *                        o su contenido difiere del esperado
     */
    static void compararJsonRaizCompleto(JsonNode nodoReal, JsonNode envoltorioEsperado) {
        assertThat(nodoReal)
                .as("El JSON raíz no debe ser nulo")
                .isNotNull();
        assertThat(nodoReal.isObject())
                .as("La raíz de mensaje_whatsapp.contenido debe ser un objeto JSON")
                .isTrue();
        assertThat(envoltorioEsperado)
                .as("El envoltorio esperado no debe ser nulo")
                .isNotNull();
        assertThat(envoltorioEsperado.isObject())
                .as("El envoltorio esperado debe ser un objeto JSON")
                .isTrue();

        List<String> clavesReales = new ArrayList<>();
        nodoReal.properties().forEach(e -> clavesReales.add(e.getKey())); // Jackson 3

        List<String> clavesEsperadas = new ArrayList<>();
        envoltorioEsperado.properties().forEach(e -> clavesEsperadas.add(e.getKey()));

        assertThat(clavesReales)
                .as("La raíz de mensaje_whatsapp.contenido solo debe contener las propiedades esperadas: " + clavesEsperadas)
                .containsExactlyInAnyOrderElementsOf(clavesEsperadas);

        assertThat(nodoReal)
                .as("El JSON raíz completo de mensaje_whatsapp.contenido debe coincidir exactamente con el envoltorio esperado")
                .isEqualTo(envoltorioEsperado);
    }

    private void programarEscenario(String textoProveedor, CombinacionSalida combinacion) {
        switch (combinacion) {
            case SIN_HERRAMIENTAS -> modeloFalso.programarTexto(textoProveedor);
            case RESPONDER_SALUDO -> modeloFalso.programarResponderAlCliente(
                    textoProveedor, IntencionRespuesta.SALUDO, null, null, null);
            case BUSCAR_DISPONIBILIDAD -> modeloFalso
                    .programarTextoYLlamada(textoProveedor, HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad())
                    .programarTexto(textoProveedor);
            case PROPONER_RESERVA -> {
                LlamadaHerramienta llamadaProponer = modeloFalso.nuevaLlamada(
                        HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f1\"}");
                LlamadaHerramienta llamadaResponder = modeloFalso.llamadaResponderAlCliente(
                        IntencionRespuesta.SALUDO, null, "s1", "p1");
                modeloFalso
                        .programarTextoYLlamada(textoProveedor, HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad())
                        .programarTextoYLlamadas(textoProveedor, List.of(llamadaProponer, llamadaResponder))
                        .programarTexto(textoProveedor);
            }
        }
    }

    private ObjectNode construirInteractivoEsperado(
            CombinacionSalida combinacion,
            long conversacionId,
            ObservadorEvaluacionRegistro.RegistroTurno turnoObs
    ) {
        return switch (combinacion) {
            case SIN_HERRAMIENTAS -> ((MensajeAsistente.Interactivo) respuestasAsistente.menu(MotivoMenu.NEUTRO)).contenido();
            case RESPONDER_SALUDO -> menus.menuPrincipal(textos.bienvenida("Negocio ficticio a"));
            case BUSCAR_DISPONIBILIDAD -> {
                var mostrar = (EfectoTurno.MostrarFranjas) turnoObs.efecto()
                        .orElseThrow(() -> new AssertionError("Se esperaba efecto MostrarFranjas"));
                assertThat(mostrar.franjas()).isNotEmpty();
                LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());
                yield ((MensajeAsistente.Interactivo) respuestasAsistente.franjas(mostrar.franjas(), hoy)).contenido();
            }
            case PROPONER_RESERVA -> construirConfirmacionEsperada(conversacionId);
        };
    }

    private ObjectNode construirConfirmacionEsperada(long conversacionId) {
        Map<String, Object> op = consultarOperacionPendienteVigente(a, conversacionId);
        long operacionId = ((Number) op.get("id")).longValue();
        String nonce = codificarNonce((byte[]) op.get("nonce"));
        JsonNode datos = mapper.readTree(String.valueOf(op.get("datos")));
        long servicioId = datos.path("servicioId").asLong();
        long barberoId = datos.path("barberoId").asLong();
        Instant inicio = mapper.treeToValue(datos.path("inicio"), Instant.class);
        int duracionMin = datos.path("duracionMin").asInt();
        Instant fin = inicio.plusSeconds(duracionMin * 60L);
        BigDecimal precio = datos.path("precio").decimalValue();

        String servicioNombre = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT nombre FROM servicio WHERE id = ?", String.class, servicioId));
        String barberoNombre = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT nombre_visible FROM barbero WHERE id = ?", String.class, barberoId));

        ResumenOperacion resumen = new ResumenOperacion(
                TipoOperacion.CREAR,
                servicioNombre,
                barberoNombre,
                inicio,
                fin,
                duracionMin,
                precio,
                Optional.empty()
        );
        return menus.confirmacion(textos.resumen(resumen), operacionId, nonce);
    }

    private void verificarAusenciaDeTextoProveedorEnContenido(
            String contenidoCrudo,
            JsonNode interactivo,
            String textoProveedor
    ) {
        verificarAusenciaDeTextoProveedorEnContenido(contenidoCrudo, interactivo, textoProveedor, null);
    }

    private void verificarAusenciaDeTextoProveedorEnContenido(
            String contenidoCrudo,
            JsonNode interactivo,
            String textoProveedor,
            String textoEsperadoServidor
    ) {
        verificarAusenciaDeTextoProveedorEnCadena(
                "mensaje_whatsapp.contenido (JSON)", contenidoCrudo, textoProveedor, textoEsperadoServidor);
        verificarAusenciaDeTextoProveedorEnCadena(
                "interactivo.body.text",
                interactivo.path("body").path("text").asString(""),
                textoProveedor,
                textoEsperadoServidor
        );
        if (interactivo.has("header")) {
            verificarAusenciaDeTextoProveedorEnCadena(
                    "interactivo.header",
                    interactivo.path("header").toString(),
                    textoProveedor,
                    textoEsperadoServidor
            );
        }
        if (interactivo.has("footer")) {
            verificarAusenciaDeTextoProveedorEnCadena(
                    "interactivo.footer",
                    interactivo.path("footer").toString(),
                    textoProveedor,
                    textoEsperadoServidor
            );
        }
        for (JsonNode boton : interactivo.path("action").path("buttons")) {
            verificarAusenciaDeTextoProveedorEnCadena(
                    "boton.id",
                    boton.path("reply").path("id").asString(""),
                    textoProveedor,
                    textoEsperadoServidor
            );
            verificarAusenciaDeTextoProveedorEnCadena(
                    "boton.title",
                    boton.path("reply").path("title").asString(""),
                    textoProveedor,
                    textoEsperadoServidor
            );
        }
        for (JsonNode seccion : interactivo.path("action").path("sections")) {
            verificarAusenciaDeTextoProveedorEnCadena(
                    "seccion.title",
                    seccion.path("title").asString(""),
                    textoProveedor,
                    textoEsperadoServidor
            );
            for (JsonNode fila : seccion.path("rows")) {
                verificarAusenciaDeTextoProveedorEnCadena(
                        "fila.id",
                        fila.path("id").asString(""),
                        textoProveedor,
                        textoEsperadoServidor
                );
                verificarAusenciaDeTextoProveedorEnCadena(
                        "fila.title",
                        fila.path("title").asString(""),
                        textoProveedor,
                        textoEsperadoServidor
                );
                verificarAusenciaDeTextoProveedorEnCadena(
                        "fila.description",
                        fila.path("description").asString(""),
                        textoProveedor,
                        textoEsperadoServidor
                );
            }
        }
    }

    private void verificarAusenciaDeTextoProveedorEnCadena(
            String ubicacion,
            String cadenaInspeccionada,
            String textoProveedor
    ) {
        verificarAusenciaDeTextoProveedorEnCadena(ubicacion, cadenaInspeccionada, textoProveedor, null);
    }

    private void verificarAusenciaDeTextoProveedorEnCadena(
            String ubicacion,
            String cadenaInspeccionada,
            String textoProveedor,
            String textoEsperadoServidor
    ) {
        assertThat(cadenaInspeccionada).as(ubicacion).isNotNull();
        for (String subcadena : subcadenasDeCuatroOMasPalabras(textoProveedor)) {
            assertThat(cadenaInspeccionada)
                    .as(ubicacion + " no debe contener subcadena de 4+ palabras del proveedor: «" + subcadena + "»")
                    .doesNotContainIgnoringCase(subcadena);
        }
        for (String tokenProhibido : tokensProhibidosPara(textoProveedor, textoEsperadoServidor)) {
            assertThat(cadenaInspeccionada)
                    .as(ubicacion + " no debe contener el token adversario o fragmento del proveedor: «" + tokenProhibido + "»")
                    .doesNotContainIgnoringCase(tokenProhibido);
        }
    }

    /**
     * Determina el conjunto completo de tokens prohibidos para una comprobación: incluye la lista base
     * de tokens adversarios y añade todos los fragmentos cortos del proveedor (de 2 y 3 palabras) que no
     * formen parte del mensaje esperado del servidor (CI-14; hallazgo 5 de Codex; hallazgo 2 de Codex 142).
     *
     * @param textoProveedor texto libre emitido por el modelo
     * @param textoEsperadoServidor texto o JSON esperado del servidor, o {@code null} si no aplica
     * @return conjunto de tokens y fragmentos prohibidos que no deben aparecer en la salida
     */
    static Set<String> tokensProhibidosPara(String textoProveedor, String textoEsperadoServidor) {
        Set<String> tokens = new LinkedHashSet<>(TOKENS_ADVERSARIOS_PROHIBIDOS);
        String esperadoNormalizado = textoEsperadoServidor != null ? textoEsperadoServidor.toLowerCase() : "";
        for (String fragmento : subcadenasDeDosYTresPalabras(textoProveedor)) {
            if (esperadoNormalizado.isEmpty() || !esperadoNormalizado.contains(fragmento.toLowerCase())) {
                tokens.add(fragmento);
            }
        }
        return tokens;
    }

    /**
     * Extrae todas las subcadenas contiguas de 2 y 3 palabras del texto del proveedor (tanto con
     * puntuación original como normalizando signos de puntuación) para comprobar su ausencia en la salida
     * si no forman parte del mensaje esperado del servidor (CI-14; hallazgo 5 de Codex).
     *
     * @param texto texto libre devuelto por el proveedor
     * @return conjunto ordenado de subcadenas de 2 y 3 palabras
     */
    static Set<String> subcadenasDeDosYTresPalabras(String texto) {
        Set<String> resultado = new LinkedHashSet<>();
        if (texto == null || texto.isBlank()) {
            return resultado;
        }
        agregarVentanasDePalabras(texto.trim().split("\\s+"), 2, resultado);
        agregarVentanasDePalabras(texto.trim().split("\\s+"), 3, resultado);

        String limpio = texto.replaceAll("[¡!¿?.,;:\"'()\\[\\]]", " ").trim();
        String[] palabrasLimpias = limpio.split("\\s+");
        agregarVentanasDePalabras(palabrasLimpias, 2, resultado);
        agregarVentanasDePalabras(palabrasLimpias, 3, resultado);
        return resultado;
    }

    private static void agregarVentanasDePalabras(String[] palabras, int tamanoVentana, Set<String> destino) {
        if (palabras == null || palabras.length < tamanoVentana) {
            return;
        }
        for (int i = 0; i <= palabras.length - tamanoVentana; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < tamanoVentana; j++) {
                if (j > 0) {
                    sb.append(' ');
                }
                sb.append(palabras[i + j]);
            }
            String ventana = sb.toString().trim();
            if (!ventana.isBlank()) {
                destino.add(ventana);
            }
        }
    }

    /**
     * Extrae todas las subcadenas contiguas de 4 palabras del texto del proveedor (tanto con puntuación original
     * como normalizando signos de puntuación extremos) para comprobar que ninguna aparece en la salida (CI-14).
     *
     * @param texto texto libre devuelto por el proveedor
     * @return conjunto ordenado de subcadenas de 4 palabras
     */
    static Set<String> subcadenasDeCuatroOMasPalabras(String texto) {
        Set<String> resultado = new LinkedHashSet<>();
        if (texto == null || texto.isBlank()) {
            return resultado;
        }
        String[] palabrasCrudas = texto.trim().split("\\s+");
        for (int i = 0; i <= palabrasCrudas.length - 4; i++) {
            String ventanaCruda = String.join(" ",
                    palabrasCrudas[i], palabrasCrudas[i + 1], palabrasCrudas[i + 2], palabrasCrudas[i + 3]);
            resultado.add(ventanaCruda);
        }
        String limpio = texto.replaceAll("[¡!¿?.,;:\"'()\\[\\]]", " ").trim();
        String[] palabrasLimpias = limpio.split("\\s+");
        for (int i = 0; i <= palabrasLimpias.length - 4; i++) {
            String ventanaLimpia = String.join(" ",
                    palabrasLimpias[i], palabrasLimpias[i + 1], palabrasLimpias[i + 2], palabrasLimpias[i + 3]);
            if (!ventanaLimpia.isBlank()) {
                resultado.add(ventanaLimpia);
            }
        }
        return resultado;
    }

    private DetalleConversacionDto obtenerDetalleEnBandeja(long negocioId, long conversacionId) {
        return ejecutor.ejecutar(negocioId, () -> {
            String modoActual = sql.queryForObject(
                    "SELECT modo FROM conversacion WHERE id = ?", String.class, conversacionId);
            if (!"HUMANO".equals(modoActual)) {
                sql.update("""
                        UPDATE conversacion
                        SET modo = 'HUMANO', motivo_derivacion = 'PETICION', derivada_en = ?
                        WHERE id = ?
                        """, Timestamp.from(reloj.instant()), conversacionId);
            }
            return bandeja.detalle(conversacionId, null, titular.getId());
        });
    }

    private String argumentosDisponibilidad() {
        return "{\"servicio\":\"s1\",\"profesional\":null,\"desde\":\"2026-11-04\",\"hasta\":\"2026-11-10\",\"horaDesde\":\"10:00\",\"horaHasta\":null,\"cita\":null}";
    }

    private void enviarTexto(String phoneNumberId, String waId, String nombrePerfil, String texto) {
        String wamid = ConversacionPruebas.nuevoWamid();
        var cuerpo = ConversacionPruebas.cuerpoTexto(mapper, phoneNumberId, waId, nombrePerfil, texto, wamid, reloj.instant());
        ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
    }

    private void enviarSeleccion(String phoneNumberId, String waId, String nombrePerfil, String seleccionId) {
        String wamid = ConversacionPruebas.nuevoWamid();
        boolean esBoton = ConversacionPruebas.esBoton(seleccionId);
        var cuerpo = ConversacionPruebas.cuerpoInteractivo(
                mapper, phoneNumberId, waId, nombrePerfil, seleccionId, esBoton, wamid, reloj.instant());
        ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
    }

    private long asegurarFichaYConversacion(long negocioId, String waId, String nombre) {
        return ejecutor.ejecutar(negocioId, () -> {
            var existentes = sql.queryForList(
                    "SELECT id FROM cliente WHERE negocio_id = ? AND wa_id = ?", Long.class, negocioId, waId);
            long clienteId = existentes.isEmpty()
                    ? sql.queryForObject("""
                            INSERT INTO cliente (negocio_id, nombre, wa_id, activo, creado_en, actualizado_en)
                            VALUES (?, ?, ?, true, ?, ?) RETURNING id
                            """, Long.class, negocioId, nombre, waId,
                            Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()))
                    : existentes.getFirst();

            var convs = sql.queryForList(
                    "SELECT id FROM conversacion WHERE negocio_id = ? AND wa_id = ?", Long.class, negocioId, waId);
            if (convs.isEmpty()) {
                return sql.queryForObject("""
                        INSERT INTO conversacion (negocio_id, wa_id, cliente_id, ultima_entrada_en, creado_en, actualizado_en)
                        VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                        """, Long.class, negocioId, waId, clienteId,
                        Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
            }
            long convId = convs.getFirst();
            sql.update("UPDATE conversacion SET cliente_id = ?, ultima_entrada_en = ? WHERE id = ?",
                    clienteId, Timestamp.from(reloj.instant()), convId);
            return convId;
        });
    }

    private List<Map<String, Object>> consultarSalientesNuevos(
            long negocioId,
            long conversacionId,
            Long ultimoMensajeIdAntes
    ) {
        long umbral = ultimoMensajeIdAntes != null ? ultimoMensajeIdAntes : 0L;
        return ejecutor.ejecutar(negocioId, () -> sql.queryForList("""
                SELECT id, tipo, origen, estado, CAST(contenido AS text) AS contenido
                FROM mensaje_whatsapp
                WHERE conversacion_id = ? AND direccion = 'SALIENTE' AND id > ?
                ORDER BY id
                """, conversacionId, umbral));
    }

    private Long consultarUltimoMensajeSalienteId(long negocioId, long conversacionId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var filas = sql.queryForList("""
                    SELECT id FROM mensaje_whatsapp
                    WHERE conversacion_id = ? AND direccion = 'SALIENTE'
                    ORDER BY id DESC LIMIT 1
                    """, Long.class, conversacionId);
            return filas.isEmpty() ? null : filas.getFirst();
        });
    }

    private Long consultarUltimoTurnoId(long negocioId, long conversacionId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var filas = sql.queryForList("""
                    SELECT id FROM turno_ia
                    WHERE conversacion_id = ?
                    ORDER BY id DESC LIMIT 1
                    """, Long.class, conversacionId);
            return filas.isEmpty() ? null : filas.getFirst();
        });
    }

    private String consultarEstadoTurno(long negocioId, long turnoId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT estado FROM turno_ia WHERE id = ?", String.class, turnoId));
    }

    private int contarReservasActivas(long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM reserva WHERE negocio_id = ? AND estado <> 'CANCELADA'",
                Integer.class, negocioId));
    }

    private Map<String, Object> consultarOperacionPendienteVigente(long negocioId, long conversacionId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForMap("""
                SELECT o.id, o.nonce, CAST(o.datos AS text) AS datos
                FROM operacion_pendiente o
                WHERE o.conversacion_id = ? AND o.estado = 'VIGENTE' AND o.expira_en > ?
                ORDER BY o.id DESC LIMIT 1
                """, conversacionId, Timestamp.from(reloj.instant())));
    }

    private static String codificarNonce(byte[] nonce) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }

    private static Instant instanteDeColumna(Object valor) {
        if (valor instanceof Instant inst) {
            return inst;
        }
        if (valor instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (valor instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        throw new IllegalArgumentException("Tipo de columna temporal no soportado: " + valor);
    }
}
