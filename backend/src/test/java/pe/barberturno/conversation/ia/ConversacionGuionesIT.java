package pe.barberturno.conversation.ia;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import pe.barberturno.conversation.ConversacionPruebas;
import pe.barberturno.conversation.ConversacionPruebas.MensajeSalientePrueba;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suite determinista de integración para la conversación guiada con IA (CI-11; 08-pruebas §4.1).
 * Ejecuta los 12 guiones YAML mínimos verificando:
 * <ul>
 *     <li>Sustitución por guardas cuando el modelo inventa horas o afirma confirmación sin ejecución.</li>
 *     <li>Rechazo de confirmación tardía y comportamiento idempotente ante doble pulsación.</li>
 *     <li>Fallo del proveedor que deriva a menús sin exponer errores técnicos.</li>
 *     <li>Límite diario local de llamadas por conversación que deriva a menús sin llamar al modelo.</li>
 *     <li>Aislamiento estricto en modo humano sin llamadas a IA ni mensajes salientes automáticos.</li>
 *     <li>Rechazo de alias de herramientas ajenas o maliciosas con resultado de error.</li>
 *     <li>Neutralización de inyecciones de prompt sin saltar reglas de negocio.</li>
 *     <li>Derivación a persona mediante la herramienta correspondiente.</li>
 *     <li>Aislamiento multitenant de canarios entre negocios A y B en la petición enviada al modelo.</li>
 *     <li>Descarte de turnos pendientes cuando llega un nuevo mensaje del cliente sin salientes huérfanos.</li>
 * </ul>
 *
 * <p><strong>Configuración de entorno (H-XI-01):</strong> Las propiedades de prueba se declaran
 * directamente sobre la clase mediante {@link TestPropertySource} para garantizar su aplicación en
 * el contexto de Spring Boot.</p>
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.2
 */
@Import(ConfiguracionPruebasIA.class)
@TestPropertySource(properties = {
        "barberturno.ia.habilitada=true",
        "barberturno.ia.clave=clave-ficticia-local",
        "barberturno.ia.trabajador.habilitado=false",
        "barberturno.ia.trabajador.lote=1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ConversacionGuionesIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired(required = false)
    private TurnosIA turnosIA;

    @Autowired
    private ModeloConversacionalFalso modeloFalso;

    @Autowired
    private ObservadorEvaluacionRegistro observadorRegistro;

    @Autowired
    private JornadaRepository jornadas;

    /**
     * Constructor por defecto para pruebas de integración de Spring Boot.
     */
    public ConversacionGuionesIT() { }

    @Test
    void expectativas_primerSalienteProhibidoNoSeOcultaConElUltimoCorrecto() {
        asegurarFichaCliente(a, WA_CLIENTE, "Cliente ficticio");
        insertarSalidaPrueba("prohibido"); insertarSalidaPrueba("correcto");
        var mensaje = new GuionConversacion.MensajeEsperado(Optional.of("TEXTO"), List.of(), List.of("prohibido"));
        var espera = new GuionConversacion.ExpectativaPaso(List.of(mensaje, mensaje), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> comprobarExpectativas(a, WA_CLIENTE, espera, null, null, 0, 2, 0)).isInstanceOf(AssertionError.class);
    }

    @Test
    void expectativas_mensajeAnteriorNoAcreditaSalidaNueva() {
        asegurarFichaCliente(a, WA_CLIENTE, "Cliente ficticio"); insertarSalidaPrueba("correcto");
        long ultimo = consultarUltimoMensajeSalienteId(a, WA_CLIENTE);
        var mensaje = new GuionConversacion.MensajeEsperado(Optional.of("TEXTO"), List.of("correcto"), List.of());
        var espera = new GuionConversacion.ExpectativaPaso(List.of(mensaje), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> comprobarExpectativas(a, WA_CLIENTE, espera, null, ultimo, 0, 0, 0)).isInstanceOf(AssertionError.class);
    }

    @Test
    void expectativas_sinMensajesEsperados_rechazaCualquierSalienteNuevo() {
        asegurarFichaCliente(a, WA_CLIENTE, "Cliente ficticio"); insertarSalidaPrueba("salida inesperada");
        var espera = new GuionConversacion.ExpectativaPaso(List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> comprobarExpectativas(a, WA_CLIENTE, espera, null, null, 0, 1, 0)).isInstanceOf(AssertionError.class);
    }

    @Test
    void expectativas_guardaDeTurnoAnteriorNoAcreditaPasoActual() {
        observadorRegistro.fin(900, "HECHO", Optional.of(new ResultadoGuardas.Sustituido(MotivoGuarda.AFIRMA_OPERACION)), Optional.empty());
        var espera = new GuionConversacion.ExpectativaPaso(List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("AFIRMA_OPERACION"));
        assertThatThrownBy(() -> comprobarExpectativas(a, WA_CLIENTE, espera, 901L, null, 0, 0, 0)).isInstanceOf(AssertionError.class);
    }

    @Test
    void expectativas_errorDeHerramientaAnteriorNoAcreditaPasoActual() {
        observadorRegistro.herramienta(900, new LlamadaHerramienta("call", HerramientaIA.LISTAR_SERVICIOS, "{}"),
                new ResultadoHerramienta("call", "{}", true), List.of());
        var espera = new GuionConversacion.ExpectativaPaso(List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.of(true));
        assertThatThrownBy(() -> comprobarExpectativas(a, WA_CLIENTE, espera, 901L, null, 0, 0, 0)).isInstanceOf(AssertionError.class);
    }

    private void insertarSalidaPrueba(String texto) {
        ejecutor.ejecutar(a, () -> sql.update("""
                INSERT INTO mensaje_whatsapp(conversacion_id,direccion,origen,tipo,contenido,estado,creado_en,actualizado_en,proximo_intento)
                SELECT id,'SALIENTE','ASISTENTE','TEXTO',cast(? as jsonb),'PENDIENTE',?,?,?
                FROM conversacion WHERE wa_id=?
                """, mapper.writeValueAsString(Map.of("texto", texto)), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), WA_CLIENTE));
    }

    @BeforeEach
    void prepararEscenarioIA() {
        modeloFalso.reiniciar();
        observadorRegistro.reiniciar();

        sql.update("UPDATE negocio SET limite_reservas_activas = 3, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        sql.update("UPDATE negocio SET ia_habilitada = true WHERE id IN (?, ?)", a, b);

        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, b, barberoB);
    }

    @ParameterizedTest(name = "Guión determinista: {0}")
    @ValueSource(strings = {
            "01-inventa-hora",
            "02-afirma-confirmada-sin-ejecucion",
            "03-si-tardio-operacion-caducada",
            "04-doble-boton",
            "05-fallo-proveedor",
            "05-limite-diario-superado",
            "06-modo-humano",
            "07-herramienta-alias-ajeno",
            "08-inyeccion-ignora-reglas",
            "09-derivacion-por-herramienta",
            "10-canarios-dos-negocios",
            "11-turno-descartado-mensaje-nuevo"
    })
    @DisplayName("ejecutarGuion_casosMinimos_cumplenExpectativas")
    void ejecutarGuion_casosMinimos_cumplenExpectativas(String nombreGuion) throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/" + nombreGuion + ".yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion01_modeloInventaHora_guardaSustituyePorServidor")
    void guion01_modeloInventaHora_guardaSustituyePorServidor() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/01-inventa-hora.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion02_modeloAfirmaConfirmadaSinEjecucion_bloqueadoPorGuarda")
    void guion02_modeloAfirmaConfirmadaSinEjecucion_bloqueadoPorGuarda() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/02-afirma-confirmada-sin-ejecucion.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion03_siTardioSobreOperacionCaducada_noCreaReserva")
    void guion03_siTardioSobreOperacionCaducada_noCreaReserva() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/03-si-tardio-operacion-caducada.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion04_dobleBotonInteractivo_produceUnaSolaReserva")
    void guion04_dobleBotonInteractivo_produceUnaSolaReserva() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/04-doble-boton.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion05a_falloProveedor_derivaAMenus")
    void guion05a_falloProveedor_derivaAMenus() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/05-fallo-proveedor.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion05b_limiteDiarioAlcanzado_derivaAMenusSinLlamarModelo")
    void guion05b_limiteDiarioAlcanzado_derivaAMenusSinLlamarModelo() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/05-limite-diario-superado.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion06_modoHumano_noGeneraRespuestaIA")
    void guion06_modoHumano_noGeneraRespuestaIA() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/06-modo-humano.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion07_herramientaConAliasAjeno_errorYNoFiltraDatos")
    void guion07_herramientaConAliasAjeno_errorYNoFiltraDatos() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/07-herramienta-alias-ajeno.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion08_inyeccionPrompt_sinEfectoPerjudicial")
    void guion08_inyeccionPrompt_sinEfectoPerjudicial() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/08-inyeccion-ignora-reglas.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion09_derivarAPersona_cambiaAModoHumano")
    void guion09_derivarAPersona_cambiaAModoHumano() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/09-derivacion-por-herramienta.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion10_canariosDosNegocios_aislamientoEstrictoPrompt")
    void guion10_canariosDosNegocios_aislamientoEstrictoPrompt() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/10-canarios-dos-negocios.yaml");
        ejecutarGuionCompleto(guion);
    }

    @Test
    @DisplayName("guion11_turnoDescartadoPorMensajeNuevo_sinSalida")
    void guion11_turnoDescartadoPorMensajeNuevo_sinSalida() throws IOException {
        GuionConversacion guion = GuionConversacion.leerRecurso("/conversaciones/11-turno-descartado-mensaje-nuevo.yaml");
        ejecutarGuionCompleto(guion);
    }

    private void ejecutarGuionCompleto(GuionConversacion guion) {
        reloj.fijar(guion.reloj());
        long negocioId = "B".equalsIgnoreCase(guion.negocio()) ? b : a;
        String phoneNumberId = "B".equalsIgnoreCase(guion.negocio()) ? NUMERO_B : NUMERO_A;
        String waId = WA_CLIENTE;

        sql.update("UPDATE negocio SET ia_habilitada = ? WHERE id = ?", guion.iaNegocio(), negocioId);

        if (guion.limiteLlamadasDia().isPresent()) {
            sql.update("UPDATE negocio SET ia_llamadas_dia_conversacion = ? WHERE id = ?",
                    guion.limiteLlamadasDia().get(), negocioId);
        }

        if ("10-canarios-dos-negocios".equals(guion.nombre())) {
            sql.update("UPDATE negocio SET nombre = 'Barbería Canario A' WHERE id = ?", a);
            sql.update("UPDATE negocio SET nombre = 'Barbería Canario B' WHERE id = ?", b);
            ejecutor.ejecutar(b, () -> sql.update("UPDATE barbero SET nombre_visible='Profesional Canario B' WHERE id=?", barberoB));
            ejecutor.ejecutar(b, () -> sql.update("UPDATE servicio SET nombre='Servicio Canario B' WHERE id=?", servicioB));
        }

        if (guion.ficha()) {
            asegurarFichaCliente(negocioId, waId, "Cliente Prueba");
        } else {
            eliminarFichaCliente(negocioId, waId);
        }

        String ultimaSeleccionEnviada = null;

        for (GuionConversacion.PasoGuion paso : guion.pasos()) {
            Long ultimoMensajeIdAntes = consultarUltimoMensajeSalienteId(negocioId, waId);
            Long turnoAntes = consultarUltimoTurnoId(negocioId, waId);
            int llamadasModeloAntes = modeloFalso.cantidadPeticiones();
            int salientesAntes = contarMensajesSalientes(negocioId, waId);

            if (paso.avanzarMinutos().isPresent()) {
                reloj.adelantar(Duration.ofMinutes(paso.avanzarMinutos().get()));
            }

            for (GuionConversacion.RespuestaProgramadaGuion resp : paso.modelo()) {
                if (resp.fallo().isPresent()) {
                    modeloFalso.programarFallo(resp.fallo().get());
                } else {
                    List<LlamadaHerramienta> llamadas = resp.llamadas().stream()
                            .map(ll -> new LlamadaHerramienta("call_" + System.nanoTime(), ll.herramienta(), ll.argumentosJson()))
                            .toList();
                    RespuestaModelo respuesta = new RespuestaModelo(
                            resp.texto(),
                            llamadas,
                            ContinuacionProveedor.VACIA,
                            ModeloConversacionalFalso.USO_DEFECTO,
                            resp.parada()
                    );
                    modeloFalso.programarRespuesta(respuesta);
                }
            }

            if (paso.durante().isPresent()) {
                var durante = paso.durante().get();
                modeloFalso.fijarGanchoAntes(() -> {
                    durante.texto().ifPresent(txt -> {
                        enviarTexto(phoneNumberId, waId, "Cliente", txt);
                    });
                });
            }

            if (paso.cliente().texto().isPresent()) {
                enviarTexto(phoneNumberId, waId, "Cliente", paso.cliente().texto().get());
            } else if (paso.cliente().seleccion().isPresent()) {
                ultimaSeleccionEnviada = paso.cliente().seleccion().get();
                enviarSeleccion(phoneNumberId, waId, "Cliente", ultimaSeleccionEnviada);
            } else if (paso.cliente().repetirSeleccion().orElse(false)) {
                assertThat(ultimaSeleccionEnviada)
                        .as("No se encontró selección previa para repetir en este paso")
                        .isNotNull();
                enviarSeleccion(phoneNumberId, waId, "Cliente", ultimaSeleccionEnviada);
            } else if (paso.cliente().seleccionDelUltimoMensaje().isPresent()) {
                int idx = paso.cliente().seleccionDelUltimoMensaje().get();
                MensajeSalientePrueba ultimoMsgPrevio = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, negocioId, waId);
                assertThat(ultimoMsgPrevio)
                        .as("No se encontró mensaje previo con botones para seleccionar")
                        .isNotNull();
                assertThat(ultimoMsgPrevio.idsSeleccion())
                        .as("El último mensaje saliente no tiene opciones interactivas")
                        .isNotEmpty();
                assertThat(idx)
                        .as("Índice de opción fuera de rango: " + idx)
                        .isGreaterThanOrEqualTo(0)
                        .isLessThan(ultimoMsgPrevio.idsSeleccion().size());
                String opt = ultimoMsgPrevio.idsSeleccion().get(idx);
                ultimaSeleccionEnviada = opt;
                enviarSeleccion(phoneNumberId, waId, "Cliente", opt);
            }

            Long turnoIdPaso = consultarUltimoTurnoId(negocioId, waId);
            if (java.util.Objects.equals(turnoAntes, turnoIdPaso)) {
                // Un paso sin entrada puede drenar el turno que el gancho del paso anterior dejó pendiente.
                // Nunca se reutiliza un turno ya terminado para acreditar una guarda o una herramienta.
                boolean sinEntrada = paso.cliente().texto().isEmpty() && paso.cliente().seleccion().isEmpty()
                        && paso.cliente().seleccionDelUltimoMensaje().isEmpty() && !paso.cliente().repetirSeleccion().orElse(false);
                turnoIdPaso = sinEntrada && turnoIdPaso != null && "PENDIENTE".equals(consultarEstadoTurno(negocioId, turnoIdPaso))
                        ? turnoIdPaso : null;
            }

            if (turnosIA != null) {
                turnosIA.procesarPendientes();
            }

            int llamadasModeloDespues = modeloFalso.cantidadPeticiones();
            int salientesDespues = contarMensajesSalientes(negocioId, waId);

            comprobarExpectativas(negocioId, waId, paso.espera(), turnoIdPaso, ultimoMensajeIdAntes,
                    llamadasModeloDespues - llamadasModeloAntes, salientesDespues - salientesAntes, llamadasModeloAntes);
        }

        if ("10-canarios-dos-negocios".equals(guion.nombre())) {
            var prohibidos = List.of("Barbería Canario B", "Profesional Canario B", "Servicio Canario B");
            for (var peticion : modeloFalso.peticiones()) for (String canario : prohibidos)
                assertThat(ModeloConversacionalFalso.extraerPeticionCompleta(peticion)).doesNotContain(canario);
            for (var turno : observadorRegistro.turnos()) {
                for (var h : turno.herramientas()) for (String canario : prohibidos) {
                    assertThat(h.llamada().argumentosJson()).doesNotContain(canario);
                    assertThat(h.resultado().json()).doesNotContain(canario);
                }
                for (var r : turno.respuestas()) for (String canario : prohibidos)
                    assertThat(r.texto().orElse("")).doesNotContain(canario);
            }
            var salientes = ejecutor.ejecutar(negocioId, () -> sql.queryForList("SELECT contenido::text FROM mensaje_whatsapp WHERE direccion='SALIENTE'", String.class));
            for (String salida : salientes) for (String canario : prohibidos) assertThat(salida).doesNotContain(canario);
        }
        modeloFalso.verificarSinDatosPrivados();
    }

    private void comprobarExpectativas(
            long negocioId,
            String waId,
            GuionConversacion.ExpectativaPaso espera,
            Long turnoIdPaso,
            Long ultimoMensajeIdAntes,
            int llamadasModeloDelta,
            int salientesNuevosDelta,
            int peticionesAntes
    ) {
        var nuevos = ejecutor.ejecutar(negocioId, () -> sql.queryForList("""
                SELECT m.tipo,m.contenido::text AS contenido FROM mensaje_whatsapp m
                JOIN conversacion c ON c.id=m.conversacion_id AND c.negocio_id=m.negocio_id
                WHERE c.wa_id=? AND m.direccion='SALIENTE' AND m.id>? ORDER BY m.id
                """, waId, ultimoMensajeIdAntes == null ? 0L : ultimoMensajeIdAntes));
        assertThat(nuevos).as("Todos los salientes del paso deben tener expectativa, en orden")
                .hasSize(espera.mensajes().size());
        if (!espera.mensajes().isEmpty()) {
            for (int i = 0; i < nuevos.size(); i++) {
                var fila = nuevos.get(i);
                var me = espera.mensajes().get(i);
                var contenido = mapper.readTree(fila.get("contenido").toString());
                // g100-3: en un interactivo se comprueba todo (cuerpo, encabezado, pie, botones y filas), no solo el cuerpo.
                String texto = "TEXTO".equals(fila.get("tipo")) ? contenido.path("texto").asText("")
                        : contenido.path("interactivo").toString();
                me.tipo().ifPresent(tipo -> assertThat(fila.get("tipo")).isEqualTo(tipo));
                for (String c : me.contiene()) assertThat(texto).contains(c);
                for (String nc : me.noContiene()) assertThat(texto).doesNotContain(nc);
            }
        }
        if (espera.reservasCreadas().isPresent()) {
            int reservas = contarReservasActivas(negocioId);
            assertThat(reservas).isEqualTo(espera.reservasCreadas().get());
        }

        if (espera.operacionVigente().isPresent()) {
            boolean tieneOperacion = tieneOperacionPendienteVigente(negocioId, waId);
            assertThat(tieneOperacion).isEqualTo(espera.operacionVigente().get());
        }

        if (espera.modoConversacion().isPresent()) {
            String modo = consultarModoConversacion(negocioId, waId);
            assertThat(modo).isEqualTo(espera.modoConversacion().get());
        }

        if (espera.sustitucionGuardas().isPresent()) {
            String motivoEsperado = espera.sustitucionGuardas().get();
            assertThat(turnoIdPaso).as("La sustitución debe pertenecer al turno NUEVO del paso").isNotNull();
            assertThat(observadorRegistro.buscarTurno(turnoIdPaso)).as("Observación del turno del paso").isPresent();
            boolean sustituido = observadorRegistro.buscarTurno(turnoIdPaso).orElseThrow().motivoSustitucion()
                    .map(Enum::name).filter(m -> m.equalsIgnoreCase(motivoEsperado)).isPresent();
            assertThat(sustituido)
                    .as("Se esperaba sustitución por guarda: " + motivoEsperado)
                    .isTrue();
        }

        if (espera.estadoTurno().isPresent()) {
            String estadoEsperado = espera.estadoTurno().get();
            String estadoReal = (turnoIdPaso != null)
                    ? consultarEstadoTurno(negocioId, turnoIdPaso)
                    : null;
            assertThat(estadoReal)
                    .as("Estado del turno IA de la conversación " + waId)
                    .isEqualTo(estadoEsperado);
        }

        if (espera.llamadasModelo().isPresent()) {
            assertThat(llamadasModeloDelta)
                    .as("Llamadas al modelo realizadas en este paso")
                    .isEqualTo(espera.llamadasModelo().get());
        }

        if (espera.salientesNuevos().isPresent()) {
            assertThat(salientesNuevosDelta)
                    .as("Mensajes salientes nuevos generados en este paso")
                    .isEqualTo(espera.salientesNuevos().get());
        }

        if (!espera.peticionNoContiene().isEmpty()) {
            assertThat(modeloFalso.peticiones().subList(peticionesAntes, modeloFalso.cantidadPeticiones()))
                    .as("Se esperaba inspeccionar peticiones NUEVAS del paso")
                    .isNotEmpty();
            String textoPeticion = modeloFalso.peticiones().subList(peticionesAntes, modeloFalso.cantidadPeticiones()).stream().map(ModeloConversacionalFalso::extraerPeticionCompleta).collect(java.util.stream.Collectors.joining("\n"));
            for (String prohibido : espera.peticionNoContiene()) {
                assertThat(textoPeticion)
                        .as("La petición enviada al modelo no debe contener: " + prohibido)
                        .doesNotContain(prohibido);
            }
        }

        if (espera.herramientaError().isPresent()) {
            boolean esperadoError = espera.herramientaError().get();
            assertThat(turnoIdPaso).as("El error de herramienta debe pertenecer al turno NUEVO del paso").isNotNull();
            assertThat(observadorRegistro.buscarTurno(turnoIdPaso)).as("Observación del turno del paso").isPresent();
            var ultimoTurnoObs = observadorRegistro.buscarTurno(turnoIdPaso).orElseThrow();
            assertThat(ultimoTurnoObs.herramientas())
                    .as("No se registraron herramientas en el turno observado")
                    .isNotEmpty();
            boolean algunError = ultimoTurnoObs.herramientas().stream()
                    .anyMatch(h -> h.resultado().error());
            assertThat(algunError)
                    .as("El resultado de la herramienta debe tener indicador de error")
                    .isEqualTo(esperadoError);
        }
    }

    private long enviarTexto(String phoneNumberId, String waId, String nombrePerfil, String texto) {
        String wamid = ConversacionPruebas.nuevoWamid();
        var cuerpo = ConversacionPruebas.cuerpoTexto(mapper, phoneNumberId, waId, nombrePerfil, texto, wamid, reloj.instant());
        long id = ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
        return id;
    }

    private long enviarSeleccion(String phoneNumberId, String waId, String nombrePerfil, String seleccionId) {
        String wamid = ConversacionPruebas.nuevoWamid();
        boolean esBoton = ConversacionPruebas.esBoton(seleccionId);
        var cuerpo = ConversacionPruebas.cuerpoInteractivo(mapper, phoneNumberId, waId, nombrePerfil, seleccionId, esBoton, wamid, reloj.instant());
        long id = ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
        return id;
    }

    private void asegurarFichaCliente(long negocioId, String waId, String nombre) {
        ejecutor.ejecutar(negocioId, () -> {
            var existentes = sql.queryForList("SELECT id FROM cliente WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            Long clienteId;
            if (existentes.isEmpty()) {
                clienteId = sql.queryForObject("""
                        INSERT INTO cliente (negocio_id, nombre, wa_id, activo, creado_en, actualizado_en)
                        VALUES (?, ?, ?, true, ?, ?) RETURNING id
                        """, Long.class, negocioId, nombre, waId, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
            } else {
                clienteId = existentes.getFirst();
            }

            var convs = sql.queryForList("SELECT id FROM conversacion WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            if (convs.isEmpty()) {
                sql.update("""
                        INSERT INTO conversacion (negocio_id, wa_id, cliente_id, ultima_entrada_en, creado_en, actualizado_en)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, negocioId, waId, clienteId, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
            } else {
                sql.update("UPDATE conversacion SET cliente_id = ? WHERE negocio_id = ? AND wa_id = ?",
                        clienteId, negocioId, waId);
            }
            return null;
        });
    }

    private void eliminarFichaCliente(long negocioId, String waId) {
        ejecutor.ejecutar(negocioId, () -> {
            sql.update("UPDATE conversacion SET cliente_id = NULL WHERE negocio_id = ? AND wa_id = ?", negocioId, waId);
            return sql.update("DELETE FROM cliente WHERE negocio_id = ? AND wa_id = ?", negocioId, waId);
        });
    }

    private int contarReservasActivas(long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM reserva WHERE negocio_id = ? AND estado <> 'CANCELADA'",
                Integer.class, negocioId));
    }

    private boolean tieneOperacionPendienteVigente(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var conversacionIds = sql.queryForList("SELECT id FROM conversacion WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            if (conversacionIds.isEmpty()) return false;
            long cid = conversacionIds.getFirst();
            Integer conteo = sql.queryForObject("""
                    SELECT count(*) FROM operacion_pendiente
                    WHERE negocio_id = ? AND conversacion_id = ? AND expira_en > ?
                    """, Integer.class, negocioId, cid, Timestamp.from(reloj.instant()));
            return conteo != null && conteo > 0;
        });
    }

    private String consultarModoConversacion(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var filas = sql.queryForList("SELECT estado, modo FROM conversacion WHERE negocio_id = ? AND wa_id = ?",
                    negocioId, waId);
            if (filas.isEmpty()) return "ASISTENTE";
            Map<String, Object> fila = filas.getFirst();
            if (fila.containsKey("modo") && fila.get("modo") != null) {
                return (String) fila.get("modo");
            }
            return (String) fila.getOrDefault("estado", "ASISTENTE");
        });
    }

    private Long consultarUltimoMensajeSalienteId(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var filas = sql.queryForList("""
                    SELECT m.id FROM mensaje_whatsapp m
                    JOIN conversacion c ON c.id = m.conversacion_id AND c.negocio_id = m.negocio_id
                    WHERE c.negocio_id = ? AND c.wa_id = ? AND m.direccion = 'SALIENTE'
                    ORDER BY m.id DESC LIMIT 1
                    """, Long.class, negocioId, waId);
            return filas.isEmpty() ? null : filas.getFirst();
        });
    }

    private int contarMensajesSalientes(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            Integer total = sql.queryForObject("""
                    SELECT count(m.id) FROM mensaje_whatsapp m
                    JOIN conversacion c ON c.id = m.conversacion_id AND c.negocio_id = m.negocio_id
                    WHERE c.negocio_id = ? AND c.wa_id = ? AND m.direccion = 'SALIENTE'
                    """, Integer.class, negocioId, waId);
            return total != null ? total : 0;
        });
    }

    private Long consultarUltimoTurnoId(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var filas = sql.queryForList("""
                    SELECT t.id FROM turno_ia t
                    JOIN conversacion c ON c.id = t.conversacion_id AND c.negocio_id = t.negocio_id
                    WHERE c.negocio_id = ? AND c.wa_id = ?
                    ORDER BY t.id DESC LIMIT 1
                    """, Long.class, negocioId, waId);
            return filas.isEmpty() ? null : filas.getFirst();
        });
    }

    private String consultarEstadoTurno(long negocioId, long turnoId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT estado FROM turno_ia WHERE id = ?", String.class, turnoId));
    }

    private String consultarUltimoEstadoTurno(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var filas = sql.queryForList("""
                    SELECT t.estado FROM turno_ia t
                    JOIN conversacion c ON c.id = t.conversacion_id AND c.negocio_id = t.negocio_id
                    WHERE c.negocio_id = ? AND c.wa_id = ?
                    ORDER BY t.id DESC LIMIT 1
                    """, String.class, negocioId, waId);
            return filas.isEmpty() ? null : filas.getFirst();
        });
    }
}
