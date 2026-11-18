package pe.barberturno.conversation.ia;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.*;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.*;

/** CI-2/3/4/6/7/8: aplicación real, dos negocios y HTTP falso sin ninguna transacción abierta. */
@Import(TurnosIAIT.Configuracion.class)
@TestPropertySource(properties = {"barberturno.ia.habilitada=true", "barberturno.ia.clave=clave-ficticia-local",
        "barberturno.ia.trabajador.habilitado=false", "barberturno.ia.trabajador.lote=1"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class TurnosIAIT extends WhatsAppSalidaPruebaBase {
    @Autowired TurnosIA turnos;
    @Autowired LimitesIAService limites;
    @Autowired HerramientasReserva herramientas;
    @Autowired ConstructorContextoModelo constructor;
    @Autowired ModeloControlado modelo;
    @Autowired ObservadorEvaluacionRegistro observador;
    @Autowired JornadaRepository jornadas;
    @Autowired RetencionWhatsApp retencion;
    @MockitoSpyBean OperacionesConversacion propuestas;
    @MockitoSpyBean ConsultasConversacion consultas;
    @MockitoSpyBean pe.barberturno.notifications.AvisosLimiteIA avisos;
    @Autowired pe.barberturno.whatsapp.ReceptorMensajesWhatsApp receptor;
    @Autowired pe.barberturno.whatsapp.SalidaWhatsApp salida;
    @Autowired ConversacionesWhatsApp conversaciones;
    @Autowired pe.barberturno.customers.AnonimizacionFichas anonimizacion;
    private int numeroConversacion;

    @BeforeEach void prepararIA() {
        modelo.reiniciar(); modelo.antes = () -> { };
        observador.reiniciar();
        reloj.fijar(Instant.parse("2026-11-04T14:00:00Z"));
        sql.update("UPDATE negocio SET ia_habilitada=true, limite_reservas_activas=3, confirmacion_manual=false WHERE id IN (?,?)", a, b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, b, barberoB);
    }

    @Test void textoSinTerminal_menuNeutro_yCosteLiquidado() {
        long c = conversacion(a, true), t = encolar(a, c, "Busco un corte");
        modelo.programarTexto("¿Qué servicio prefieres?");
        assertThat(turnos.procesarPendientes()).isEqualTo(1);
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(mensajes(a, c)).isEqualTo(1);
        assertThat(contenido(a, c)).contains(new ComposicionTurnoIA().neutro()).doesNotContain("¿Qué servicio prefieres?");
        assertThat(observador.buscarTurno(t).orElseThrow().textoDescartado()).contains("¿Qué servicio prefieres?");
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT texto FROM turno_ia WHERE id=?", String.class, t)).isNull();
            assertThat(sql.queryForObject("SELECT estado FROM llamada_ia WHERE turno_id=?", String.class, t)).isEqualTo("LIQUIDADA");
            // 100×4 + 50×20 micro-USD, sin categorías de caché del falso.
            assertThat(sql.queryForObject("SELECT coste_micro_usd FROM uso_ia WHERE negocio_id=?", Long.class, a)).isEqualTo(1400L);
            return null;
        });
    }

    @Test void otroMensajeDuranteHttp_descartaSinSalidaNiOperacion() {
        long c = conversacion(a, true), t = encolar(a, c, "Un corte");
        modelo.antes = () -> ejecutor.ejecutar(a, () -> sql.update("UPDATE conversacion SET version=version+1 WHERE id=?", c));
        modelo.programarTexto("¿Qué servicio prefieres?");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("DESCARTADO");
        assertThat(mensajes(a, c)).isZero();
        assertThat(operaciones(a)).isZero();
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1);
    }

    @Test void concesionVencida_noAplica_yNuevaFichaNoSePisa() {
        long c = conversacion(a, true), t = encolar(a, c, "Un corte");
        modelo.antes = () -> ejecutor.ejecutar(a, () -> sql.update("""
                UPDATE turno_ia SET reclamado_hasta=?, intentos=intentos+1 WHERE id=?
                """, Timestamp.from(reloj.instant().minusSeconds(1)), t));
        modelo.programarTexto("¿Qué servicio prefieres?");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("PENDIENTE");
        assertThat(mensajes(a, c)).isZero();
        assertThat(operaciones(a)).isZero();
    }

    @Test void anteriorPendiente_seDescarta_yGanaUltimaEntrada() {
        long c = conversacion(a, false), primero = encolar(a, c, "Primero"), segundo = encolar(a, c, "Segundo");
        assertThat(estado(a, primero)).isEqualTo("DESCARTADO");
        modelo.programarTexto("¿Qué servicio prefieres?");
        turnos.procesarPendientes();
        assertThat(estado(a, segundo)).isEqualTo("HECHO");
        assertThat(((MensajeModelo.DelCliente) modelo.ultimaPeticion().mensajes().getFirst()).texto()).isEqualTo("Segundo");
    }

    @Test void segundoTrabajador_reclamaConFichaNueva_yElAnteriorNoAplicaTexto() {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        var entradas = new AtomicInteger();
        modelo.antes = () -> {
            if (entradas.getAndIncrement() != 0) return;
            ejecutor.ejecutar(a, () -> sql.update("UPDATE turno_ia SET reclamado_hasta=? WHERE id=?",
                    Timestamp.from(reloj.instant().minusSeconds(1)), t));
            turnos.procesarPendientes();
        };
        modelo.programarTexto("Texto que el trabajador antiguo no debe enviar.");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("FALLIDO");
        assertThat(mensajes(a, c)).isEqualTo(1);
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1); // La reserva única impide repetir el HTTP.
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT intentos FROM turno_ia WHERE id=?", Integer.class, t)).isEqualTo(2);
            assertThat(sql.queryForObject("SELECT CAST(contenido AS text) FROM mensaje_whatsapp WHERE conversacion_id=?", String.class, c))
                    .doesNotContain("Texto que el trabajador antiguo").contains("menú");
            return null;
        });
    }

    @Test void limiteHerramientas_todosLosResultadosVanJuntos_yExcesoEsGenerico() {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        modelo.programarLlamadas(java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> llamada(HerramientaIA.LISTAR_SERVICIOS, "{}")).toList());
        modelo.programarTexto("¿Qué servicio prefieres?");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        var mensajesModelo = modelo.ultimaPeticion().mensajes();
        var resultados = (MensajeModelo.ResultadosHerramientas) mensajesModelo.getLast();
        assertThat(resultados.resultados()).hasSize(6);
        assertThat(resultados.resultados().stream().filter(ResultadoHerramienta::error).count()).isEqualTo(2);
        assertThat(resultados.resultados().getLast().json()).contains("Demasiadas consultas");
    }

    @Test void limiteCuatroLlamadas_fallaConMenuSinQuintaConsulta() {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        for (int i = 0; i < 5; i++) modelo.programarLlamada(HerramientaIA.LISTAR_SERVICIOS, "{}");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("FALLIDO");
        assertThat(modelo.cantidadPeticiones()).isEqualTo(4);
        assertThat(mensajes(a, c)).isEqualTo(1);
    }

    @Test void continuacion_soloViveEnElBucle_yNoSeEntregaAlObservador() {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        var opaca = new ContinuacionProveedor() { };
        modelo.programarRespuesta(new RespuestaModelo(Optional.empty(),
                List.of(llamada(HerramientaIA.LISTAR_SERVICIOS, "{}")), opaca,
                new UsoModelo("claude-opus-5-5", 10, 20, 0, 0, 1), "tool_use"));
        modelo.programarTexto("¿Qué servicio prefieres?");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        var devuelta = (MensajeModelo.DelAsistente) modelo.ultimaPeticion().mensajes().get(1);
        assertThat(devuelta.continuacion()).isSameAs(opaca);
        assertThat(observador.turnos().getFirst().respuestas()).allSatisfy(
                respuesta -> assertThat(respuesta.continuacion()).isSameAs(ContinuacionProveedor.VACIA));
    }

    @Test void creacion_seValidaSinOperacion_ySoloSeProponeAlFinal() {
        long c = conversacion(a, true), t = encolar(a, c, "Un corte");
        programarPropuesta(false);
        modelo.antes = () -> assertThat(operaciones(a)).isZero();
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(operaciones(a)).isEqualTo(1);
        assertThat(reservas(a)).isZero();
        ejecutor.ejecutar(a, () -> {
            var contenido = sql.queryForObject("SELECT CAST(contenido AS text) FROM mensaje_whatsapp WHERE conversacion_id=?", String.class, c);
            assertThat(contenido).contains("Confirmar", "2026");
            return null;
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void vencimientoDuranteAplicacion_reviertePropuestaYSalida(boolean venceConcesion) {
        long c = conversacion(a, true), t = encolar(a, c, "Un corte");
        programarPropuesta(false);
        OperacionesConversacion destino = AopTestUtils.getUltimateTargetObject(propuestas);
        doAnswer(invocacion -> {
            Object resultado = invocacion.callRealMethod();
            reloj.fijar(reloj.instant().plusSeconds(venceConcesion ? 121 : 91));
            return resultado;
        }).when(destino).proponerCreacion(anyLong(), anyLong(), anyLong(), anyLong(), any(Instant.class));
        turnos.procesarPendientes();
        assertThat(operaciones(a)).isZero();
        assertThat(reservas(a)).isZero();
        assertThat(estado(a, t)).isEqualTo(venceConcesion ? "PENDIENTE" : "FALLIDO");
        assertThat(mensajes(a, c)).isEqualTo(venceConcesion ? 0 : 1);
        comprobarSalidasObservadas(c, t, venceConcesion ? 0 : 1);
    }

    @Test void sinFicha_pideConsentimientoConFranjaPendiente() {
        long c = conversacion(a, false), t = encolar(a, c, "Un corte");
        programarPropuesta(false);
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(operaciones(a)).isZero();
        ejecutor.ejecutar(a, () -> {
            var estado = sql.queryForObject("SELECT CAST(estado AS text) FROM conversacion WHERE id=?", String.class, c);
            assertThat(estado).contains("CONSENTIMIENTO", "pendiente");
            return null;
        });
    }

    @Test void propuestaIA_seConfirmaPorElMismoCaminoDeterminista() {
        long c = conversacion(a, true);
        encolar(a, c, "Un corte"); programarPropuesta(false); turnos.procesarPendientes();
        assertThat(reservas(a)).isZero();
        long ficha = ficha(a, c).orElseThrow();
        var resultado = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                contextoConfirmacion(c, ficha)));
        assertThat(resultado).isInstanceOf(ResultadoOperacion.Ejecutada.class);
        assertThat(reservas(a)).isEqualTo(1);
    }

    private ResultadoOperacion contextoConfirmacion(long c, long ficha) {
        return propuestas.confirmarVigente(c, ficha);
    }

    @Test void dosPropuestasDistintas_noCreaNinguna_yAclara() {
        long c = conversacion(a, true), t = encolar(a, c, "Un corte");
        programarPropuesta(true);
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(operaciones(a)).isZero();
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT CAST(contenido AS text) FROM mensaje_whatsapp WHERE conversacion_id=?", String.class, c))
                    .contains("elige una sola");
            return null;
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cambios_soloProponenYRevalidanPropiedadSinModificarCita(boolean mover) {
        long c = conversacion(a, true);
        long reserva = citaFutura(c);
        long t = encolar(a, c, "Cambiar mi cita");
        modelo.programarLlamada(HerramientaIA.MIS_CITAS, "{}");
        if (mover) modelo.programarLlamada(HerramientaIA.BUSCAR_DISPONIBILIDAD,
                argumentosDisponibilidad().replace("\"cita\":null", "\"cita\":\"c1\""));
        modelo.programarLlamada(mover ? HerramientaIA.PROPONER_REPROGRAMACION : HerramientaIA.PROPONER_CANCELACION,
                mover ? "{\"cita\":\"c1\",\"franja\":\"f1\"}" : "{\"cita\":\"c1\"}");
        modelo.programarTexto("Te envío el resumen para que lo confirmes.");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(operaciones(a)).isEqualTo(1);
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT tipo FROM operacion_pendiente WHERE conversacion_id=?", String.class, c))
                    .isEqualTo(mover ? "REPROGRAMAR" : "CANCELAR");
            assertThat(sql.queryForObject("SELECT estado FROM reserva WHERE id=?", String.class, reserva)).isEqualTo("CONFIRMADA");
            assertThat(sql.queryForObject("SELECT version FROM reserva WHERE id=?", Integer.class, reserva)).isZero();
            return null;
        });
    }

    private long citaFutura(long conversacion) {
        return citaFutura(conversacion, Instant.parse("2026-11-06T15:00:00Z"));
    }

    private long citaFutura(long conversacion, Instant inicio) {
        long ficha = ficha(a, conversacion).orElseThrow();
        return ejecutor.ejecutar(a, () -> sql.queryForObject("""
                INSERT INTO reserva(cliente_id,barbero_id,servicio_id,inicio,fin,duracion_ref_min,precio_ref,
                    estado,canal,creado_en,actualizado_en)
                VALUES (?,?,?,?,?,30,25,
                    'CONFIRMADA','WHATSAPP',?,?) RETURNING id
                """, Long.class, ficha, barberoA, servicioA, Timestamp.from(inicio), Timestamp.from(inicio.plusSeconds(1800)),
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));
    }

    @Test void derivacion_tienePrecedenciaSobrePropuesta() {
        long c = conversacion(a, true), t = encolar(a, c, "Ayuda");
        modelo.programarLlamadas(List.of(llamada(HerramientaIA.DERIVAR_A_PERSONA, "{\"motivo\":\"QUEJA\"}"),
                llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"ajena\"}")));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(operaciones(a)).isZero();
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT modo FROM conversacion WHERE id=?", String.class, c))).isEqualTo("HUMANO");
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1);
    }

    /** V2-DA-83 (H-XI-06): con SOLO_HUMANO, la bandeja ve el texto saneado que provocó la derivación, antes del acuse. */
    @Test void derivacionPorIA_guardaElTextoSaneadoDelCliente_antesDelAcuse() {
        long c = conversacion(a, true), t = encolar(a, c, "Quiero poner una queja, llámenme al 966 123 456");
        modelo.programarLlamadas(List.of(llamada(HerramientaIA.DERIVAR_A_PERSONA, "{\"motivo\":\"QUEJA\"}")));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        ejecutor.ejecutar(a, () -> {
            var filas = sql.queryForList("""
                    SELECT direccion, origen, estado, wamid, CAST(contenido AS text) AS contenido
                    FROM mensaje_whatsapp WHERE conversacion_id=? ORDER BY id""", c);
            assertThat(filas).hasSize(2);
            assertThat(filas.get(0)).containsEntry("direccion", "ENTRANTE").containsEntry("origen", "CLIENTE")
                    .containsEntry("estado", "RECIBIDO");
            assertThat(filas.get(0).get("wamid")).isNull();
            assertThat((String) filas.get(0).get("contenido")).contains("Quiero poner una queja").doesNotContain("966 123 456");
            assertThat(filas.get(1)).containsEntry("direccion", "SALIENTE").containsEntry("origen", "SISTEMA");
            assertThat(sql.queryForObject("SELECT modo FROM conversacion WHERE id=?", String.class, c)).isEqualTo("HUMANO");
            return null;
        });
    }

    @ParameterizedTest @EnumSource(value = MotivoFalloModelo.class, names = {"TIEMPO_AGOTADO", "PROVEEDOR", "LIMITE_PROVEEDOR", "RECHAZO", "SALIDA_INVALIDA"})
    void falloModelo_terminaConMenu_yConservaReservaDesconocida(MotivoFalloModelo motivo) {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        modelo.programarFallo(motivo);
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("FALLIDO");
        assertThat(mensajes(a, c)).isEqualTo(1);
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT estado FROM llamada_ia WHERE turno_id=?", String.class, t)).isEqualTo("RESERVADA");
            assertThat(sql.queryForObject("SELECT coste_micro_usd FROM uso_ia WHERE negocio_id=?", Long.class, a)).isPositive();
            return null;
        });
    }

    @Test void falloConUso_liquidaCacheSinDobleConteo() {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        modelo.programarFallo(new FalloModelo(MotivoFalloModelo.RECHAZO,
                Optional.of(new UsoModelo("claude-opus-5-5", 10, 20, 30, 40, 5)), null));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("FALLIDO");
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT coste_micro_usd FROM uso_ia WHERE negocio_id=?", Long.class, a)))
                .isEqualTo(646L); // 10×4 + 20×20 + 30×0.2 + 40×5
    }

    @Test void abandono_sinIaHabilitada_vaciaTextoSinRespuesta() {
        long c = conversacion(a, false), t = encolar(a, c, "Temporal");
        sql.update("UPDATE negocio SET ia_habilitada=false WHERE id=?", a);
        reloj.fijar(reloj.instant().plusSeconds(901));
        retencion.purgar();
        assertThat(estado(a, t)).isEqualTo("FALLIDO");
        assertThat(mensajes(a, c)).isZero();
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT texto FROM turno_ia WHERE id=?", String.class, t))).isNull();
    }

    @Test void contexto_soloListaBlancaSaneada_ySinDatosDeB() {
        long c = conversacion(a, true);
        sql.update("UPDATE negocio SET nombre='CANARIO_B_NO_ENVIAR' WHERE id=?", b);
        var contexto = new ContextoTurno(a, ficha(a, c), TiempoNegocio.fechaLima(reloj.instant()));
        String proyectado = ejecutor.ejecutar(a, () -> constructor.construir(c, contexto));
        assertThat(proyectado).contains("\"alias\":\"s1\"", "\"ultimaRespuesta\":\"NEUTRO\"")
                .doesNotContain("CANARIO_B_NO_ENVIAR", "Cliente", "Apellido", "@", "wa_id", "cliente_id", "BT-", "519990");
        long t = encolar(a, c, "Mi teléfono 999888777 y correo uno@ejemplo.test");
        modelo.programarTexto("¿Qué servicio prefieres?");
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(((MensajeModelo.DelCliente) modelo.ultimaPeticion().mensajes().getFirst()).texto())
                .isEqualTo("Mi teléfono [dato] y correo [dato]");
    }

    @Test void herramientas_rechazanAliasFinalidadNegocioYCamposExtra_sinEscrituras() {
        long c = conversacion(a, true);
        var contexto = new ContextoTurno(a, ficha(a, c), TiempoNegocio.fechaLima(reloj.instant()));
        ejecutor.ejecutar(a, () -> constructor.construir(c, contexto));
        for (var llamada : List.of(llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f999\"}"),
                llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"s1\"}"),
                llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f1\",\"franja\":\"f2\"}"),
                llamada(HerramientaIA.MIS_CITAS, "{} {}"),
                llamada(HerramientaIA.MIS_CITAS, "{\"cliente_id\":1}"),
                llamada(HerramientaIA.DERIVAR_A_PERSONA, "{\"motivo\":\"MALICIOSO\"}"))) {
            assertThat(ejecutor.ejecutar(a, () -> herramientas.ejecutar(contexto, llamada)).paraModelo().error()).isTrue();
        }
        var fuera = ejecutor.ejecutar(b, () -> herramientas.ejecutar(contexto, llamada(HerramientaIA.LISTAR_SERVICIOS, "{}")));
        assertThat(fuera.paraModelo().error()).isTrue();
        assertThat(fuera.paraModelo().json()).doesNotContain("Corte", "f999", "cliente_id", "SQL");
        assertThat(operaciones(a)).isZero();
        assertThat(reservas(a)).isZero();
    }

    @Test void contexto_sinHistorialTextual_conUltimaRespuesta_yVaciado() {
        long c = conversacion(a, true);
        encolar(a, c, "Hola");
        modelo.programarLlamadas(List.of(terminal("SALUDO", null, null, null)));
        turnos.procesarPendientes();
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx -> {
            salida.encolarTexto(c, "CANARIO_ASISTENTE_NO_ENVIAR BT-123456", pe.barberturno.whatsapp.OrigenMensaje.ASISTENTE, OptionalLong.empty());
            salida.encolarRespuestaPersonal(c, "CANARIO_PERSONAL_NO_ENVIAR", titular.getId());
            return null;
        }));
        String contexto = contexto(a, c);
        assertThat(mapper.readTree(contexto).path("ultimaRespuesta").asString()).isEqualTo("SALUDO");
        assertThat(contexto).doesNotContain("historial", "CANARIO_", "BT-", "Cliente", "ia_corte_mensaje");
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx -> {
            conversaciones.bloquear(c); conversaciones.vaciar(c); return null;
        }));
        assertThat(mapper.readTree(contexto(a, c)).path("ultimaRespuesta").asString()).isEqualTo("NEUTRO");
    }

    @Test void disponibilidad_limitaRangoHorasYVeinteFranjas() {
        long c = conversacion(a, true);
        var contexto = new ContextoTurno(a, ficha(a, c), TiempoNegocio.fechaLima(reloj.instant()));
        ejecutor.ejecutar(a, () -> constructor.construir(c, contexto));
        var resultado = ejecutor.ejecutar(a, () -> herramientas.ejecutar(contexto,
                llamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad())));
        assertThat(resultado.paraModelo().error()).isFalse();
        var franjas = ((EfectoTurno.MostrarFranjas) resultado.efecto().orElseThrow()).franjas();
        assertThat(franjas).hasSize(20);
        assertThat(franjas).allMatch(f -> !TiempoNegocio.aLima(f.inicio()).toLocalTime().isBefore(LocalTime.of(10, 0)));
        for (String argumentos : List.of(argumentosDisponibilidad().replace("2026-11-04", "2026-11-03"),
                argumentosDisponibilidad().replace("2026-11-10", "2026-11-11"),
                argumentosDisponibilidad().replace("10:00", "25:00")))
            assertThat(ejecutor.ejecutar(a, () -> herramientas.ejecutar(contexto,
                    llamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentos))).paraModelo().error()).isTrue();
        var nuevoTurno = new ContextoTurno(a, contexto.ficha, contexto.hoy);
        assertThat(ejecutor.ejecutar(a, () -> herramientas.ejecutar(nuevoTurno,
                llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f1\"}"))).paraModelo().error()).isTrue();
    }

    @Test void presupuestoConcurrente_reservaPreviaSoloPermiteUna_yAvisaUnaVezPorDia() throws Exception {
        long c1 = conversacion(a, true), c2 = conversacion(a, true);
        long t1 = encolar(a, c1, "Uno"), t2 = encolar(a, c2, "Dos");
        // Cada petición pequeña reserva entre 80k y 140k micro-USD; el saldo solo permite una.
        sql.update("UPDATE negocio SET ia_presupuesto_mensual_usd=0.15 WHERE id=?", a);
        var inicio = new CountDownLatch(1);
        try (var hilosPrueba = Executors.newFixedThreadPool(2)) {
            var uno = hilosPrueba.submit(() -> { inicio.await(); return ejecutor.ejecutar(a, () -> limites.reservar(t1, 1, peticion())); });
            var dos = hilosPrueba.submit(() -> { inicio.await(); return ejecutor.ejecutar(a, () -> limites.reservar(t2, 1, peticion())); });
            inicio.countDown();
            assertThat(List.of(uno.get(10, TimeUnit.SECONDS), dos.get(10, TimeUnit.SECONDS)).stream().filter(Optional::isPresent).count()).isEqualTo(1);
        }
        ejecutor.ejecutar(a, () -> limites.reservar(t1, 2, peticion()));
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT count(*) FROM llamada_ia", Long.class)).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT coste_micro_usd FROM uso_ia WHERE negocio_id=?", Long.class, a)).isLessThanOrEqualTo(150000L);
            assertThat(sql.queryForObject("SELECT count(*) FROM notificacion WHERE tipo='LIMITE_IA'", Long.class)).isEqualTo(1);
            return null;
        });
        assertThat(ejecutor.ejecutar(b, () -> sql.queryForObject("SELECT count(*) FROM uso_ia", Long.class))).isZero();
        long conversacionB = conversacion(b, true), turnoB = encolar(b, conversacionB, "Consulta de B");
        assertThat(ejecutor.ejecutar(b, () -> limites.reservar(turnoB, 1, peticion()))).isPresent();
        assertThat(ejecutor.ejecutar(b, () -> sql.queryForObject("SELECT count(*) FROM uso_ia", Long.class))).isEqualTo(1);
        reloj.fijar(reloj.instant().plus(Duration.ofDays(1)));
        ejecutor.ejecutar(a, () -> limites.reservar(t1, 3, peticion()));
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT count(*) FROM notificacion WHERE tipo='LIMITE_IA'", Long.class))).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"ultima", "vacia", "recorte"})
    void dosBusquedas_cuerpoFijo_enTodasLasVariantes(String caso) {
        long c = conversacion(a, true), t = encolar(a, c, "Busco horarios");
        String primera = argumentosDisponibilidad().replace("2026-11-10", "2026-11-04");
        modelo.programarLlamadas(List.of(
                llamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, primera),
                llamada(HerramientaIA.BUSCAR_DISPONIBILIDAD,
                        "vacia".equals(caso) ? primera.replace("10:00", "23:00")
                                : "ultima".equals(caso) ? primera.replace("10:00", "15:00") : primera)));
        // V2-DA-85: el texto del modelo no puede citar horas; en "vacia" se comprueba que un texto sin horas se acepta.
        String texto = "recorte".equals(caso) ? "Hay opciones a las 17:00."
                : "vacia".equals(caso) ? "Revisa las opciones disponibles." : "Hay opciones a las 10:00.";
        modelo.programarTexto(texto);
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        var registro = observador.buscarTurno(t).orElseThrow();
        var mostrar = (EfectoTurno.MostrarFranjas) registro.efecto().orElseThrow();
        assertThat(mostrar.franjas()).hasSizeLessThanOrEqualTo(10).isNotEmpty();
        if ("vacia".equals(caso))
            assertThat(registro.herramientas().getLast().resultado().json()).isEqualTo("[]");
        assertThat(registro.guardas()).isEmpty();
        assertThat(registro.textoDescartado()).contains(texto);
        ejecutor.ejecutar(a, () -> {
            var contenido = mapper.readTree(sql.queryForObject(
                    "SELECT CAST(contenido AS text) FROM mensaje_whatsapp WHERE conversacion_id=?", String.class, c))
                    .path("interactivo");
            var filas = new ArrayList<String>();
            for (var seccion : contenido.path("action").path("sections"))
                for (var fila : seccion.path("rows")) filas.add(fila.path("id").asString());
            assertThat(filas).containsExactlyElementsOf(mostrar.franjas().stream()
                    .map(f -> f.seleccion().codificar()).toList());
            assertThat(contenido.path("body").path("text").asString())
                    .isEqualTo(new ComposicionTurnoIA().elegirFranja());
            return null;
        });
    }

    @Test void falloEnDiaPosterior_noConcedeAliasNiCambiaBorrador() {
        long c = conversacion(a, true);
        var contexto = new ContextoTurno(a, ficha(a, c), TiempoNegocio.fechaLima(reloj.instant()));
        ejecutor.ejecutar(a, () -> constructor.construir(c, contexto));
        ConsultasConversacion destino = AopTestUtils.getUltimateTargetObject(consultas);
        doAnswer(invocacion -> { throw new IllegalStateException("Fallo simulado del segundo día"); })
                .when(destino).franjas(anyLong(), any(OptionalLong.class), eq(LocalDate.of(2026, 11, 5)));
        var resultado = ejecutor.ejecutar(a, () -> herramientas.ejecutar(contexto,
                llamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad())));
        assertThat(resultado.paraModelo().error()).isTrue();
        assertThat(resultado.efecto()).isEmpty();
        assertThat(contexto.franjas).isEmpty();
        assertThat(contexto.servicioBorrador).isNull();
        assertThat(ejecutor.ejecutar(a, () -> herramientas.ejecutar(contexto,
                llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f1\"}"))).paraModelo().error()).isTrue();
        assertThat(operaciones(a)).isZero();
    }

    @Test void presupuestoAgotadoAntesDeEncolar_avisaEnEntregaBajoBloqueo_ySoloUnaVezPorDia() {
        long c = conversacion(a, true);
        sql.update("UPDATE negocio SET ia_presupuesto_mensual_usd=0.01 WHERE id=?", a);
        ejecutor.ejecutar(a, () -> sql.update("""
                INSERT INTO uso_ia(negocio_id,dia,llamadas,coste_micro_usd) VALUES (?, ?, 1, 10000)
                """, a, TiempoNegocio.fechaLima(reloj.instant())));
        pe.barberturno.notifications.AvisosLimiteIA destino = AopTestUtils.getUltimateTargetObject(avisos);
        var avisosComprobados = new AtomicInteger();
        doAnswer(invocacion -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            long conversacionId = invocacion.getArgument(0);
            // Otra transacción del rol real no puede adquirir la conversación: la entrega ya la bloqueó.
            try (var hilo = Executors.newSingleThreadExecutor()) {
                hilo.submit(() -> ejecutor.ejecutar(a, () -> {
                    assertThatThrownBy(() -> sql.queryForObject(
                            "SELECT id FROM conversacion WHERE id=? FOR UPDATE NOWAIT", Long.class, conversacionId))
                            .hasRootCauseInstanceOf(java.sql.SQLException.class)
                            .satisfies(error -> assertThat(((java.sql.SQLException)
                                    org.springframework.core.NestedExceptionUtils.getMostSpecificCause(error)).getSQLState())
                                    .isEqualTo("55P03"));
                    return null;
                })).get(5, TimeUnit.SECONDS);
            }
            avisosComprobados.incrementAndGet();
            return invocacion.callRealMethod();
        }).when(destino).presupuesto(anyLong());
        for (int i = 0; i < 2; i++) recibirTextoIA(c);
        assertThat(avisosComprobados).hasValue(2);
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM notificacion WHERE tipo='LIMITE_IA' AND conversacion_id=? AND usuario_id=?",
                Long.class, c, titular.getId()))).isEqualTo(1);
        reloj.fijar(reloj.instant().plus(Duration.ofDays(1)));
        recibirTextoIA(conversacion(a, true));
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM notificacion WHERE tipo='LIMITE_IA'", Long.class))).isEqualTo(2);
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT count(*) FROM turno_ia", Long.class))).isZero();
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT count(*) FROM llamada_ia", Long.class))).isZero();
        assertThat(modelo.cantidadPeticiones()).isZero();
    }

    private void recibirTextoIA(long c) {
        String wa = ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT wa_id FROM conversacion WHERE id=?", String.class, c));
        var dato = mapper.createObjectNode();
        dato.putObject("text").put("body", "Busco un corte especial");
        var mensaje = new pe.barberturno.whatsapp.MensajeEntrante(1, a, wa, ficha(a, c), "Cliente",
                "wamid.g095." + UUID.randomUUID(), "text", reloj.instant(), dato);
        ejecutor.ejecutar(a, () -> sql.enTransaccion(() -> { receptor.recibir(mensaje); return null; }));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void efectoDegradado_aMenuOAclaracion_noSeNotificaComoAplicado(boolean doble) {
        long c = conversacion(a, true), t = encolar(a, c, "Un corte");
        programarPropuesta(doble);
        if (!doble) {
            OperacionesConversacion destino = AopTestUtils.getUltimateTargetObject(propuestas);
            doAnswer(invocacion -> new Propuesta.Rechazada(pe.barberturno.common.error.ErrorCodigo.VALIDACION))
                    .when(destino).proponerCreacion(anyLong(), anyLong(), anyLong(), anyLong(), any(Instant.class));
        }
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(operaciones(a)).isZero();
        assertThat(mensajes(a, c)).isEqualTo(1);
        assertThat(observador.buscarTurno(t).orElseThrow().efecto()).isEmpty();
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT CAST(contenido AS text) FROM mensaje_whatsapp WHERE conversacion_id=?", String.class, c)))
                .contains(doble ? "elige una sola" : new TextosConversacion().noDisponible());
    }

    @Test void liquidacion_idempotente_yAgregadoSobreviveAPurga() {
        long c = conversacion(a, false), t = encolar(a, c, "Temporal");
        var reserva = ejecutor.ejecutar(a, () -> limites.reservar(t, 1, peticion())).orElseThrow();
        var uso = new UsoModelo("claude-opus-5-5", 10, 20, 30, 40, 1);
        ejecutor.ejecutar(a, () -> { limites.liquidar(reserva, uso); limites.liquidar(reserva, uso); return null; });
        reloj.fijar(reloj.instant().plus(Duration.ofDays(31)));
        retencion.purgar();
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT count(*) FROM conversacion WHERE id=?", Long.class, c)).isZero();
            assertThat(sql.queryForObject("SELECT count(*) FROM llamada_ia WHERE turno_id=?", Long.class, t)).isZero();
            assertThat(sql.queryForObject("SELECT coste_micro_usd FROM uso_ia WHERE negocio_id=?", Long.class, a)).isEqualTo(646L);
            return null;
        });
    }

    @Test void anonimizarDuranteHttp_borraTurnosBajoBloqueo_yNoRecreaConversacion() {
        long c = conversacion(a, true), ficha = ficha(a, c).orElseThrow(), t = encolar(a, c, "Temporal");
        var actor = new pe.barberturno.common.security.UsuarioAutenticado(titular.getId(), pe.barberturno.users.Rol.ADMIN,
                Optional.empty(), false);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(actor, "", List.of()));
        try {
            modelo.antes = () -> ejecutor.ejecutar(a, () -> { anonimizacion.anonimizar(ficha); return null; });
            modelo.programarTexto("Respuesta que no debe salir.");
            turnos.procesarPendientes();
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.queryForObject("SELECT count(*) FROM conversacion WHERE id=?", Long.class, c)).isZero();
            assertThat(sql.queryForObject("SELECT count(*) FROM turno_ia WHERE id=?", Long.class, t)).isZero();
            assertThat(sql.queryForObject("SELECT count(*) FROM llamada_ia WHERE turno_id=?", Long.class, t)).isZero();
            assertThat(sql.queryForObject("SELECT count(*) FROM mensaje_whatsapp WHERE conversacion_id=?", Long.class, c)).isZero();
            assertThat(sql.queryForObject("SELECT coste_micro_usd FROM uso_ia WHERE negocio_id=?", Long.class, a)).isPositive();
            assertThat(sql.queryForObject("SELECT anonimizada_en IS NOT NULL FROM cliente WHERE id=?", Boolean.class, ficha)).isTrue();
            return null;
        });
    }

    @Test void llamadasDia_yMinuto_seAplicanPorConversacion() {
        long c = conversacion(a, true), t = encolar(a, c, "Uno");
        sql.update("UPDATE negocio SET ia_llamadas_dia_conversacion=1, ia_entradas_minuto=1 WHERE id=?", a);
        ejecutor.ejecutar(a, () -> {
            var reserva = limites.reservar(t, 1, peticion());
            assertThat(reserva).isPresent();
            assertThat(sql.enTransaccion(() -> limites.disponibilidad(c))).isEqualTo(DisponibilidadIA.LIMITE_DIA);
            assertThat(limites.reservar(t, 2, peticion())).isEmpty();
            return null;
        });
        long otro = conversacion(a, false);
        ejecutor.ejecutar(a, () -> {
            assertThat(sql.enTransaccion(() -> limites.disponibilidad(otro))).isEqualTo(DisponibilidadIA.DISPONIBLE);
            sql.update("UPDATE conversacion SET minuto_entradas=?, entradas_minuto=2 WHERE id=?",
                    Timestamp.from(reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)), otro);
            assertThat(sql.enTransaccion(() -> limites.disponibilidad(otro))).isEqualTo(DisponibilidadIA.LIMITE_MINUTO);
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"intencion\":\"INVENTADA\",\"falta\":null,\"servicio\":null,\"profesional\":null}",
            "{\"intencion\":\"SALUDO\",\"falta\":\"DIA\",\"servicio\":null,\"profesional\":null}",
            "{\"intencion\":\"PEDIR_DATO\",\"falta\":null,\"servicio\":null,\"profesional\":null}",
            "{\"intencion\":\"SALUDO\",\"falta\":null,\"servicio\":\"s999\",\"profesional\":null}",
            "{\"intencion\":\"SALUDO\",\"falta\":null,\"servicio\":\"s1\",\"profesional\":\"p999\"}",
            "{\"intencion\":\"SALUDO\",\"falta\":null,\"servicio\":\"p1\",\"profesional\":null}",
            "{\"intencion\":\"SALUDO\",\"falta\":null,\"servicio\":null,\"profesional\":null,\"texto\":\"inyectado\"}",
            "{\"intencion\":\"SALUDO\",\"intencion\":\"SALUDO\",\"falta\":null,\"servicio\":null,\"profesional\":null}",
            "{\"intencion\":\"SALUDO\",\"falta\":null,\"servicio\":null,\"profesional\":null} {}",
            "{\"intencion\":\"SALUDO\",\"falta\":null,\"servicio\":1,\"profesional\":null}"
    })
    void terminalInvalida_cierraSinSegundaLlamadaNiSeleccionParcial(String argumentos) {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        modelo.programarLlamada(HerramientaIA.RESPONDER_AL_CLIENTE, argumentos);
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1);
        assertThat(contenido(a, c)).contains(new ComposicionTurnoIA().neutro());
        var registro = observador.buscarTurno(t).orElseThrow();
        assertThat(registro.herramientas()).isEmpty();
        assertThat(registro.decision()).isEmpty();
        assertThat(borrador(a, c).has("servicio")).isFalse();
        assertThat(operaciones(a)).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void terminalDuplicada_conservaSoloEfectosValidos(boolean hayFranjas) {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        var llamadas = new ArrayList<LlamadaHerramienta>();
        llamadas.add(terminal("SALUDO", null, "s1", "p1"));
        if (hayFranjas) llamadas.add(llamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad()));
        llamadas.add(terminal("FUERA_DE_ALCANCE", null, null, null));
        modelo.programarLlamadas(llamadas);
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1);
        assertThat(observador.buscarTurno(t).orElseThrow().decision()).isEmpty();
        assertThat(contenido(a, c)).contains(hayFranjas ? new ComposicionTurnoIA().elegirFranja() : new ComposicionTurnoIA().neutro());
    }

    @Test void terminalAntesDeLecturaYPropuesta_seEjecutaUltima_yNoAnulaLaPropuesta() {
        long c = conversacion(a, true);
        citaFutura(c);
        long t = encolar(a, c, "Cancelar mi cita");
        modelo.programarLlamadas(List.of(terminal("SALUDO", null, null, null),
                llamada(HerramientaIA.MIS_CITAS, "{}"),
                llamada(HerramientaIA.PROPONER_CANCELACION, "{\"cita\":\"c1\"}")));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1);
        assertThat(operaciones(a)).isEqualTo(1);
        assertThat(contenido(a, c)).contains("Confirmar");
        assertThat(observador.buscarTurno(t).orElseThrow().herramientas()).hasSize(2);
        assertThat(borrador(a, c).path("ultima").asString()).isEqualTo("PROPUESTA");
    }

    @ParameterizedTest @ValueSource(ints = {3, 4})
    void terminalCuentaEnElLimite_ySiempreCierra(int lecturas) {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        var llamadas = new ArrayList<LlamadaHerramienta>();
        llamadas.add(terminal("SALUDO", null, null, null));
        for (int i = 0; i < lecturas; i++) llamadas.add(llamada(HerramientaIA.LISTAR_SERVICIOS, "{}"));
        modelo.programarLlamadas(llamadas);
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1);
        assertThat(observador.buscarTurno(t).orElseThrow().decision().isPresent()).isEqualTo(lecturas == 3);
        assertThat(borrador(a, c).path("ultima").asString()).isEqualTo(lecturas == 3 ? "SALUDO" : "NEUTRO");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "MOSTRAR_PROFESIONALES,-,-,-,SERVICIO",
            "PEDIR_DATO,PROFESIONAL,-,-,SERVICIO",
            "PEDIR_DATO,DIA,-,-,SERVICIO",
            "PEDIR_DATO,DIA,s1,-,PROFESIONAL",
            "PEDIR_DATO,DIA,s1,cualquiera,DIA",
            "PEDIR_DATO,DIA,s1,p1,DIA"
    }, nullValues = "-")
    void datosFaltantes_seDegradan_yCualquieraUsaCero(String intencion, String falta, String servicio, String profesional, DatoFaltante esperado) {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        modelo.programarLlamadas(List.of(terminal(intencion, falta, servicio, profesional)));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(observador.buscarTurno(t).orElseThrow().decision()).contains(
                new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(esperado)));
        assertThat(borrador(a, c).path("ultima").asString()).isEqualTo("PEDIR_DATO:" + esperado);
        if ("cualquiera".equals(profesional)) {
            assertThat(borrador(a, c).path("profesional").asLong()).isZero();
            assertThat(contenido(a, c)).contains("d:" + servicioA + ":0:");
        }
    }

    @Test void seleccionesParciales_seRecuperanSinBuscarDisponibilidad() {
        long c = conversacion(a, true);
        encolar(a, c, "Un corte");
        modelo.programarLlamadas(List.of(terminal("PEDIR_DATO", "PROFESIONAL", "s1", null)));
        turnos.procesarPendientes();
        assertThat(borrador(a, c).path("servicio").asLong()).isEqualTo(servicioA);
        assertThat(borrador(a, c).has("dia")).isFalse();
        assertThat(borrador(a, c).has("profesional")).isFalse();
        encolar(a, c, "Cualquiera");
        modelo.programarLlamadas(List.of(terminal("PEDIR_DATO", "DIA", null, "cualquiera")));
        turnos.procesarPendientes();
        var contexto = mapper.readTree(modelo.ultimaPeticion().contexto());
        assertThat(contexto.path("borrador").path("servicio").asString()).isEqualTo("s1");
        assertThat(contexto.path("ultimaRespuesta").asString()).isEqualTo("PEDIR_DATO:PROFESIONAL");
        assertThat(borrador(a, c).path("profesional").asLong()).isZero();
        var siguiente = mapper.readTree(contexto(a, c));
        assertThat(siguiente.path("borrador").path("profesional").asString()).isEqualTo("cualquiera");
        assertThat(siguiente.path("ultimaRespuesta").asString()).isEqualTo("PEDIR_DATO:DIA");
    }

    @ParameterizedTest @ValueSource(strings = {"ninguna", "vacia", "invalidaDespues", "llenaDespues"})
    void sinDisponibilidad_exigeUltimaBusquedaValidaVacia(String caso) {
        long c = conversacion(a, true), t = encolar(a, c, "Sin horarios");
        String vacia = argumentosDisponibilidad().replace("10:00", "23:00");
        if (!"ninguna".equals(caso)) modelo.programarLlamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, vacia);
        if ("invalidaDespues".equals(caso)) modelo.programarLlamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, "{}");
        if ("llenaDespues".equals(caso)) modelo.programarLlamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad());
        modelo.programarLlamadas(List.of(terminal("SIN_DISPONIBILIDAD", null, null, null)));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        var registro = observador.buscarTurno(t).orElseThrow();
        assertThat(registro.decision().isPresent()).isEqualTo("vacia".equals(caso) || "invalidaDespues".equals(caso));
        assertThat(contenido(a, c)).contains("llenaDespues".equals(caso) ? new ComposicionTurnoIA().elegirFranja()
                : "ninguna".equals(caso) ? new ComposicionTurnoIA().neutro() : new TextosConversacion().sinOpciones());
    }

    @ParameterizedTest @ValueSource(strings = {"catalogo", "profesionales", "servicioDesactivado", "profesionalDesactivado"})
    void datosVaciosODesactivados_noEmitenListasVacias(String caso) {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        Runnable desactivar = () -> ejecutor.ejecutar(a, () -> sql.update(
                caso.startsWith("profesional") ? "UPDATE barbero SET activo=false WHERE id=?" : "UPDATE servicio SET activo=false WHERE id=?",
                caso.startsWith("profesional") ? barberoA : servicioA));
        if (caso.endsWith("Desactivado")) modelo.antes = desactivar;
        else desactivar.run();
        modelo.programarLlamadas(List.of(terminal(caso.equals("catalogo") ? "MOSTRAR_SERVICIOS" : "MOSTRAR_PROFESIONALES",
                null, caso.equals("catalogo") ? null : "s1", caso.equals("profesionalDesactivado") ? "p1" : null)));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(contenido(a, c)).contains(new TextosConversacion().sinOpciones()).doesNotContain("\"rows\": []");
    }

    @Test void borradorObsoleto_revalidaIdsDiaYUltima_porListaBlanca() {
        long c = conversacion(a, true);
        ejecutor.ejecutar(a, () -> sql.update("UPDATE conversacion SET estado=CAST(? AS jsonb) WHERE id=?",
                "{\"ia\":{\"servicio\":" + servicioB + ",\"profesional\":" + barberoB + ",\"dia\":\"2026-02-30\",\"ultima\":\"TEXTO_NO_ENVIAR\"}}", c));
        var contexto = mapper.readTree(contexto(a, c));
        assertThat(contexto.path("borrador").size()).isZero();
        assertThat(contexto.path("ultimaRespuesta").asString()).isEqualTo("NEUTRO");
        assertThat(contexto.toString()).doesNotContain("TEXTO_NO_ENVIAR", "2026-02-30");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mostrarCitas_usaSoloFichaRevalidada_ySinFichaNoRevelaExistencia(boolean conFicha) {
        long propia = conversacion(a, conFicha), ajena = conversacion(a, true);
        long reservaAjena = citaFutura(ajena);
        long t = encolar(a, propia, "Mis citas");
        modelo.programarLlamadas(List.of(terminal("MOSTRAR_CITAS", null, null, null)));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(contenido(a, propia)).contains(new TextosConversacion().sinCitas()).doesNotContain("c:" + reservaAjena);
        if (conFicha) {
            long mia = citaFutura(propia, Instant.parse("2026-11-06T16:00:00Z"));
            encolar(a, propia, "Mis citas");
            modelo.programarLlamadas(List.of(terminal("PEDIR_DATO", "CITA", null, null)));
            turnos.procesarPendientes();
            assertThat(contenido(a, propia)).contains("c:" + mia).doesNotContain("c:" + reservaAjena);
        }
    }

    @Test void fichaCambiadaDuranteHttp_descartaAntesDeConsultarCitas() {
        long c = conversacion(a, false);
        long t = encolar(a, c, "Mis citas");
        modelo.antes = () -> ejecutor.ejecutar(a, () -> {
            long ficha = sql.queryForObject("""
                    INSERT INTO cliente(nombre,wa_id,creado_en,actualizado_en)
                    SELECT 'Cliente ficticio',wa_id,?,? FROM conversacion WHERE id=? RETURNING id
                    """, Long.class, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), c);
            return sql.update("UPDATE conversacion SET cliente_id=? WHERE id=?", ficha, c);
        });
        modelo.programarLlamadas(List.of(terminal("MOSTRAR_CITAS", null, null, null)));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("DESCARTADO");
        assertThat(mensajes(a, c)).isZero();
    }

    @ParameterizedTest @EnumSource(IntencionRespuesta.class)
    void intenciones_soloComposicionDelServidor_yTextoDescartadoEnObservador(IntencionRespuesta intencion) {
        long c = conversacion(a, true), t = encolar(a, c, "Consulta");
        String canario = "Carlos ya te tiene un hueco CANARIO_PROVEEDOR_NO_ENVIAR";
        modelo.programarRespuesta(new RespuestaModelo(Optional.of(canario),
                List.of(terminal(intencion.name(), intencion == IntencionRespuesta.PEDIR_DATO ? "SERVICIO" : null, null, null)),
                ContinuacionProveedor.VACIA, new UsoModelo("claude-opus-5-5", 100, 50, 0, 0, 1), "tool_use"));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(contenido(a, c)).doesNotContain(canario, "CANARIO_PROVEEDOR");
        var registro = observador.buscarTurno(t).orElseThrow();
        assertThat(registro.textoDescartado()).contains(canario);
        assertThat(registro.guardas()).isEmpty();
        assertThat(registro.herramientas()).isEmpty();
        assertThat(registro.decision().isPresent()).isEqualTo(intencion != IntencionRespuesta.SIN_DISPONIBILIDAD);
        assertThat(borrador(a, c).path("ultima").asString()).isEqualTo(intencion == IntencionRespuesta.SIN_DISPONIBILIDAD
                ? "NEUTRO" : intencion == IntencionRespuesta.PEDIR_DATO || intencion == IntencionRespuesta.MOSTRAR_PROFESIONALES
                        ? "PEDIR_DATO:SERVICIO" : intencion.name());
    }

    @Test void terminalInvalida_yDerivacion_noImpidenExaminarHerramientasPosteriores() {
        long c = conversacion(a, true), t = encolar(a, c, "Ayuda");
        modelo.programarLlamadas(List.of(llamada(HerramientaIA.RESPONDER_AL_CLIENTE, "{}"),
                llamada(HerramientaIA.DERIVAR_A_PERSONA, "{\"motivo\":\"PETICION\"}"),
                llamada(HerramientaIA.LISTAR_SERVICIOS, "{}")));
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        assertThat(modelo.cantidadPeticiones()).isEqualTo(1);
        var registro = observador.buscarTurno(t).orElseThrow();
        assertThat(registro.herramientas()).hasSize(2);
        assertThat(registro.decision()).isEmpty();
        assertThat(registro.efecto()).get().isInstanceOf(EfectoTurno.Derivar.class);
        assertThat(borrador(a, c).path("ultima").asString()).isEqualTo("DERIVAR");
        assertThat(borrador(a, c).has("servicio")).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"FRANJAS", "PROPUESTA", "CONSENTIMIENTO", "TERMINAL"})
    void salidasObservadas_coincidenConCadaMensajePersistido_enNumeroYOrden(String caso) {
        long c = conversacion(a, !"CONSENTIMIENTO".equals(caso)), t = encolar(a, c, "Un corte");
        switch (caso) {
            case "FRANJAS" -> {
                modelo.programarLlamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad());
                modelo.programarLlamadas(List.of(terminal("SALUDO", null, null, null)));
            }
            case "PROPUESTA", "CONSENTIMIENTO" -> programarPropuesta(false);
            case "TERMINAL" -> modelo.programarLlamadas(List.of(terminal("MOSTRAR_SERVICIOS", null, null, null)));
            default -> throw new IllegalArgumentException(caso);
        }
        turnos.procesarPendientes();
        assertThat(estado(a, t)).isEqualTo("HECHO");
        comprobarSalidasObservadas(c, t, 1);
    }

    private void comprobarSalidasObservadas(long conversacion, long turno, int cantidad) {
        var persistidas = ejecutor.ejecutar(a, () -> sql.queryForList("""
                SELECT CAST(contenido AS text) FROM mensaje_whatsapp
                WHERE conversacion_id=? AND direccion='SALIENTE' ORDER BY id
                """, String.class, conversacion)).stream().map(mapper::readTree).toList();
        var observadas = observador.buscarTurno(turno).orElseThrow().salidas().stream().map(mensaje -> {
            var contenido = mapper.createObjectNode();
            switch (mensaje) {
                case MensajeAsistente.Interactivo(var interactivo) -> contenido.set("interactivo", interactivo);
                case MensajeAsistente.Texto(var texto) -> contenido.put("texto", texto);
            }
            return contenido;
        }).toList();
        assertThat(observadas).hasSize(cantidad);
        assertThat(persistidas).containsExactlyElementsOf(observadas);
    }

    private LlamadaHerramienta terminal(String intencion, String falta, String servicio, String profesional) {
        var argumentos = mapper.createObjectNode().put("intencion", intencion).put("falta", falta)
                .put("servicio", servicio).put("profesional", profesional);
        return llamada(HerramientaIA.RESPONDER_AL_CLIENTE, mapper.writeValueAsString(argumentos));
    }
    private String contenido(long negocio, long c) {
        return ejecutor.ejecutar(negocio, () -> sql.queryForObject(
                "SELECT CAST(contenido AS text) FROM mensaje_whatsapp WHERE conversacion_id=? ORDER BY id DESC LIMIT 1", String.class, c));
    }
    private tools.jackson.databind.JsonNode borrador(long negocio, long c) {
        return mapper.readTree(ejecutor.ejecutar(negocio, () -> sql.queryForObject(
                "SELECT CAST(estado AS text) FROM conversacion WHERE id=?", String.class, c))).path("ia");
    }
    private String contexto(long negocio, long c) {
        return ejecutor.ejecutar(negocio, () -> constructor.construir(c,
                new ContextoTurno(negocio, ficha(negocio, c), TiempoNegocio.fechaLima(reloj.instant()))));
    }

    private PeticionModelo peticion() { return new PeticionModelo("Sistema", "Contexto", List.of(new MensajeModelo.DelCliente("Texto")), List.of()); }
    private long conversacion(long negocio, boolean conFicha) {
        String wa = "51988000" + String.format("%03d", ++numeroConversacion);
        return ejecutor.ejecutar(negocio, () -> {
            Long ficha = conFicha ? sql.queryForObject("""
                    INSERT INTO cliente(nombre,wa_id,creado_en,actualizado_en)
                    VALUES ('Cliente Apellido NoEnviar',?,?,?) RETURNING id
                    """, Long.class, wa, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())) : null;
            return sql.queryForObject("""
                    INSERT INTO conversacion(wa_id,cliente_id,ultima_entrada_en,creado_en,actualizado_en)
                    VALUES (?,?,?,?,?) RETURNING id
                    """, Long.class, wa, ficha, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
        });
    }
    private OptionalLong ficha(long negocio, long c) {
        Long id = ejecutor.ejecutar(negocio, () -> sql.queryForObject("SELECT cliente_id FROM conversacion WHERE id=?", Long.class, c));
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }
    private long encolar(long negocio, long c, String texto) {
        return ejecutor.ejecutar(negocio, () -> new TransactionTemplate(gestor).execute(tx -> {
            entityManager.createNativeQuery("SELECT id FROM conversacion WHERE id=:id FOR UPDATE").setParameter("id", c).getSingleResult();
            long revision = ((Number) entityManager.createNativeQuery("SELECT revision FROM conversacion WHERE id=:id").setParameter("id", c).getSingleResult()).longValue();
            return turnos.encolar(c, revision, texto);
        }));
    }
    private String estado(long negocio, long turno) { return ejecutor.ejecutar(negocio, () -> sql.queryForObject("SELECT estado FROM turno_ia WHERE id=?", String.class, turno)); }
    private long mensajes(long negocio, long c) { return ejecutor.ejecutar(negocio, () -> sql.queryForObject("SELECT count(*) FROM mensaje_whatsapp WHERE conversacion_id=?", Long.class, c)); }
    private long operaciones(long negocio) { return ejecutor.ejecutar(negocio, () -> sql.queryForObject("SELECT count(*) FROM operacion_pendiente", Long.class)); }
    private long reservas(long negocio) { return ejecutor.ejecutar(negocio, () -> sql.queryForObject("SELECT count(*) FROM reserva", Long.class)); }
    private static LlamadaHerramienta llamada(HerramientaIA h, String args) { return new LlamadaHerramienta("t_" + UUID.randomUUID(), h, args); }
    private String argumentosDisponibilidad() {
        return "{\"servicio\":\"s1\",\"profesional\":null,\"desde\":\"2026-11-04\",\"hasta\":\"2026-11-10\",\"horaDesde\":\"10:00\",\"horaHasta\":null,\"cita\":null}";
    }
    private void programarPropuesta(boolean doble) {
        modelo.programarLlamada(HerramientaIA.BUSCAR_DISPONIBILIDAD, argumentosDisponibilidad());
        var lista = new ArrayList<LlamadaHerramienta>();
        lista.add(llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f1\"}"));
        if (doble) lista.add(llamada(HerramientaIA.PROPONER_RESERVA, "{\"franja\":\"f2\"}"));
        modelo.programarLlamadas(lista).programarTexto("Te envío el resumen para que lo confirmes.");
    }

    static class ModeloControlado extends ModeloConversacionalFalso {
        Runnable antes = () -> { };
        @Override public synchronized RespuestaModelo responder(PeticionModelo peticion) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            antes.run();
            return super.responder(peticion);
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Configuracion {
        @Bean @Primary ModeloControlado modeloControlado() { return new ModeloControlado(); }
        @Bean @Primary ObservadorEvaluacionRegistro observadorRegistro() { return new ObservadorEvaluacionRegistro(); }
    }
}
