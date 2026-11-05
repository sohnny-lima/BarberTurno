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

    @BeforeEach
    void prepararEscenarioIA() {
        modeloFalso.reiniciar();
        observadorRegistro.reiniciar();

        sql.update("UPDATE negocio SET limite_reservas_activas = 3, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        try {
            sql.update("UPDATE negocio SET ia_habilitada = true WHERE id IN (?, ?)", a, b);
        } catch (Exception e) {
            // Columna ia_habilitada de V117 pendiente de migración en esta rama
        }

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

        try {
            sql.update("UPDATE negocio SET ia_habilitada = ? WHERE id = ?", guion.iaNegocio(), negocioId);
        } catch (Exception e) {
            // Columna de V117 pendiente
        }

        if (guion.limiteLlamadasDia().isPresent()) {
            sql.update("UPDATE negocio SET ia_llamadas_dia_conversacion = ? WHERE id = ?",
                    guion.limiteLlamadasDia().get(), negocioId);
        }

        if ("10-canarios-dos-negocios".equals(guion.nombre())) {
            sql.update("UPDATE negocio SET nombre = 'Barbería Canario A' WHERE id = ?", a);
            sql.update("UPDATE negocio SET nombre = 'Barbería Canario B' WHERE id = ?", b);
        }

        if (guion.ficha()) {
            asegurarFichaCliente(negocioId, waId, "Cliente Prueba");
        } else {
            eliminarFichaCliente(negocioId, waId);
        }

        String ultimaSeleccionEnviada = null;

        for (GuionConversacion.PasoGuion paso : guion.pasos()) {
            Long ultimoMensajeIdAntes = consultarUltimoMensajeSalienteId(negocioId, waId);
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

            if (turnosIA != null) {
                turnosIA.procesarPendientes();
            }

            int llamadasModeloDespues = modeloFalso.cantidadPeticiones();
            int salientesDespues = contarMensajesSalientes(negocioId, waId);

            comprobarExpectativas(negocioId, waId, paso.espera(), turnoIdPaso, ultimoMensajeIdAntes,
                    llamadasModeloDespues - llamadasModeloAntes, salientesDespues - salientesAntes);
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
            int salientesNuevosDelta
    ) {
        MensajeSalientePrueba ultimoMsg = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, negocioId, waId);

        if (!espera.mensajes().isEmpty()) {
            assertThat(ultimoMsg)
                    .as("Se esperaban mensajes salientes para " + waId + " pero no se encontró ninguno")
                    .isNotNull();
            assertThat(ultimoMensajeIdAntes == null || ultimoMsg.id() > ultimoMensajeIdAntes)
                    .as("Se esperaba un mensaje saliente nuevo en este paso")
                    .isTrue();

            for (GuionConversacion.MensajeEsperado me : espera.mensajes()) {
                if (me.tipo().isPresent()) {
                    assertThat(ultimoMsg.tipo()).isEqualTo(me.tipo().get());
                }
                for (String c : me.contiene()) {
                    assertThat(ultimoMsg.texto()).contains(c);
                }
                for (String nc : me.noContiene()) {
                    assertThat(ultimoMsg.texto()).doesNotContain(nc);
                }
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
            assertThat(observadorRegistro.turnos())
                    .as("Se esperaba sustitución por guardas pero el observador de turnos está vacío")
                    .isNotEmpty();
            boolean sustituido = observadorRegistro.turnos().stream()
                    .anyMatch(t -> t.motivoSustitucion()
                            .map(Enum::name)
                            .filter(m -> m.equalsIgnoreCase(motivoEsperado))
                            .isPresent());
            assertThat(sustituido)
                    .as("Se esperaba sustitución por guarda: " + motivoEsperado)
                    .isTrue();
        }

        if (espera.estadoTurno().isPresent()) {
            String estadoEsperado = espera.estadoTurno().get();
            String estadoReal = (turnoIdPaso != null)
                    ? consultarEstadoTurno(negocioId, turnoIdPaso)
                    : consultarUltimoEstadoTurno(negocioId, waId);
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
            assertThat(modeloFalso.peticiones())
                    .as("Se esperaba inspeccionar la petición al modelo pero no se recibieron llamadas")
                    .isNotEmpty();
            PeticionModelo ultPeticion = modeloFalso.ultimaPeticion();
            String textoPeticion = ModeloConversacionalFalso.extraerTextoCompleto(ultPeticion);
            for (String prohibido : espera.peticionNoContiene()) {
                assertThat(textoPeticion)
                        .as("La petición enviada al modelo no debe contener: " + prohibido)
                        .doesNotContain(prohibido);
            }
        }

        if (espera.herramientaError().isPresent()) {
            boolean esperadoError = espera.herramientaError().get();
            assertThat(observadorRegistro.turnos())
                    .as("Se esperaba verificar resultado de herramienta pero no hay turnos observados")
                    .isNotEmpty();
            var ultimoTurnoObs = observadorRegistro.turnos().get(observadorRegistro.turnos().size() - 1);
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
