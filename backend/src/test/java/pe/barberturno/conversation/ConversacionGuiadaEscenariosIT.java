package pe.barberturno.conversation;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.barberturno.conversation.ConversacionPruebas.MensajeSalientePrueba;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios de integración de extremo a extremo de la conversación guiada sin IA (V2-10).
 * Comprueba la composición real completa con PostgreSQL:
 * evento entrante → {@link ProcesadorEventosWhatsApp} → {@link OrquestadorConversacion} real →
 * menús e interpretación determinista → consentimiento y ficha → operaciones con nonce → outbox de salida.
 * Cubre los escenarios solicitados en el encargo g075:
 * 1. Reserva completa (V2-CP-07, CJ-4)
 * 2. Doble pulsación (idempotencia y resultado repetido)
 * 3. Caducada (expiración tras 10 min)
 * 4. Sustituida y «sí» (anulación de propuesta previa y confirmación por texto)
 * 5. Texto no reconocido anula vigente
 * 6. Aislamiento multitenant (CJ-12)
 * 7. Ids manipulados (reserva ajena y privacidad no solicitada)
 * 8. Mis citas, cancelación y reprogramación con versionado y auditoría
 * 9. Negocio suspendido entre propuesta y confirmación (descarte V2-08)
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class ConversacionGuiadaEscenariosIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private JornadaRepository jornadas;

    @BeforeEach
    void configurarReglasYJornadas() {
        sql.update("UPDATE negocio SET limite_reservas_activas = 3, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, b, barberoB);
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

    private MensajeSalientePrueba ultimoMensaje(long negocioId, String waId) {
        return ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, negocioId, waId);
    }

    private Map<String, Object> filaEvento(long eventoId) {
        return sql.queryForMap("SELECT * FROM evento_whatsapp_entrante WHERE id = ?", eventoId);
    }

    private Map<String, Object> sqlFicha(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForMap(
                "SELECT * FROM cliente WHERE negocio_id = ? AND wa_id = ?", negocioId, waId));
    }

    private long sqlConversacionId(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT id FROM conversacion WHERE negocio_id = ? AND wa_id = ?", Long.class, negocioId, waId));
    }

    private Map<String, Object> sqlReserva(long negocioId, long reservaId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForMap(
                "SELECT * FROM reserva WHERE negocio_id = ? AND id = ?", negocioId, reservaId));
    }

    private List<Map<String, Object>> sqlAuditoriasReserva(long negocioId, long reservaId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForList(
                "SELECT * FROM auditoria_reserva WHERE negocio_id = ? AND reserva_id = ? ORDER BY id", negocioId, reservaId));
    }

    private Map<String, Object> sqlOperacion(long negocioId, long operacionId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForMap(
                "SELECT * FROM operacion_pendiente WHERE negocio_id = ? AND id = ?", negocioId, operacionId));
    }

    private String alcanzarResumen(long negocioId, String numero, String waId) {
        enviarTexto(numero, waId, "Cliente Ficticio", "hola");
        var msgMenu = ultimoMensaje(negocioId, waId);
        enviarSeleccion(numero, waId, "Cliente Ficticio", msgMenu.idExacto("m:RESERVAR"));
        var msgServicios = ultimoMensaje(negocioId, waId);
        enviarSeleccion(numero, waId, "Cliente Ficticio", msgServicios.primerIdQueEmpieceCon("s:"));
        var msgProfesionales = ultimoMensaje(negocioId, waId);
        enviarSeleccion(numero, waId, "Cliente Ficticio", msgProfesionales.primerIdQueEmpieceCon("b:"));
        var msgDias = ultimoMensaje(negocioId, waId);
        String idDia = msgDias.idsSeleccion().size() > 1 ? msgDias.idsSeleccion().get(1) : msgDias.primerIdQueEmpieceCon("d:");
        enviarSeleccion(numero, waId, "Cliente Ficticio", idDia);
        var msgFranjas = ultimoMensaje(negocioId, waId);
        enviarSeleccion(numero, waId, "Cliente Ficticio", msgFranjas.primerIdQueEmpieceCon("f:"));
        var msgConsent = ultimoMensaje(negocioId, waId);
        if (msgConsent.idsSeleccion().contains("p:S")) {
            enviarSeleccion(numero, waId, "Cliente Ficticio", "p:S");
        }
        var msgResumen = ultimoMensaje(negocioId, waId);
        return msgResumen.primerIdQueTermineCon(":S");
    }

    @Test
    void escenario1_reservaCompleta_dePrincipioAFinPorBotones_creaReservaFichaYAuditoria() {
        // 1. «hola» → menú principal
        long evHola = enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", "hola");
        assertThat(filaEvento(evHola).get("estado")).isEqualTo("PROCESADO");

        var msgMenu = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgMenu.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");

        // 2. Reservar → lista de servicios
        long evReservar = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgMenu.idExacto("m:RESERVAR"));
        assertThat(filaEvento(evReservar).get("estado")).isEqualTo("PROCESADO");

        var msgServicios = ultimoMensaje(a, WA_CLIENTE);
        String idServicio = msgServicios.primerIdQueEmpieceCon("s:");

        // 3. Servicio → lista de profesionales
        long evServicio = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idServicio);
        assertThat(filaEvento(evServicio).get("estado")).isEqualTo("PROCESADO");

        var msgProfesionales = ultimoMensaje(a, WA_CLIENTE);
        String idProfesional = msgProfesionales.primerIdQueEmpieceCon("b:");

        // 4. Profesional → lista de días (7 días)
        long evProfesional = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idProfesional);
        assertThat(filaEvento(evProfesional).get("estado")).isEqualTo("PROCESADO");

        var msgDias = ultimoMensaje(a, WA_CLIENTE);
        String idDia = msgDias.idsSeleccion().size() > 1 ? msgDias.idsSeleccion().get(1) : msgDias.primerIdQueEmpieceCon("d:");

        // 5. Día con franjas → lista de franjas
        long evDia = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idDia);
        assertThat(filaEvento(evDia).get("estado")).isEqualTo("PROCESADO");

        var msgFranjas = ultimoMensaje(a, WA_CLIENTE);
        String idFranja = msgFranjas.primerIdQueEmpieceCon("f:");

        // 6. Sin ficha → aviso de privacidad (0 fichas en cliente)
        assertThat(ConversacionPruebas.contarFichas(ejecutor, sql, a)).isZero();
        long evFranja = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idFranja);
        assertThat(filaEvento(evFranja).get("estado")).isEqualTo("PROCESADO");

        assertThat(ConversacionPruebas.contarFichas(ejecutor, sql, a)).isZero();
        var msgConsentimiento = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgConsentimiento.idsSeleccion()).containsExactly("p:S", "p:N");

        // 7. [Aceptar] → crea 1 ficha con wa_id y fecha de aceptación → resumen con [Confirmar]
        long evAceptar = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgConsentimiento.idExacto("p:S"));
        assertThat(filaEvento(evAceptar).get("estado")).isEqualTo("PROCESADO");

        assertThat(ConversacionPruebas.contarFichas(ejecutor, sql, a)).isEqualTo(1);
        var ficha = sqlFicha(a, WA_CLIENTE);
        assertThat(ficha.get("wa_id")).isEqualTo(WA_CLIENTE);
        assertThat(ficha.get("privacidad_aceptada_en")).isNotNull();
        long fichaId = ((Number) ficha.get("id")).longValue();

        var msgResumen = ultimoMensaje(a, WA_CLIENTE);
        String idConfirmar = msgResumen.primerIdQueTermineCon(":S");
        assertThat(ConversacionPruebas.contarOperaciones(ejecutor, sql, a, "VIGENTE")).isEqualTo(1);

        // 8. [Confirmar] → 1 reserva (canal='WHATSAPP', creada_por=null), 1 auditoría (actor_cliente_id),
        // operación EJECUTADA, evento PROCESADO y respuesta SISTEMA con reserva_id
        long evConfirmar = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idConfirmar);
        assertThat(filaEvento(evConfirmar).get("estado")).isEqualTo("PROCESADO");

        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isEqualTo(1);
        var msgResultado = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgResultado.origen()).isEqualTo("SISTEMA");
        assertThat(msgResultado.reservaId()).isNotNull();
        long reservaId = msgResultado.reservaId();

        var filaReserva = sqlReserva(a, reservaId);
        assertThat(filaReserva.get("canal")).isEqualTo("WHATSAPP");
        assertThat(filaReserva.get("creada_por")).isNull();
        assertThat(filaReserva.get("cliente_id")).isEqualTo(fichaId);
        assertThat(filaReserva.get("estado")).isEqualTo("CONFIRMADA");

        var auditorias = sqlAuditoriasReserva(a, reservaId);
        assertThat(auditorias).singleElement().satisfies(aud -> {
            assertThat(aud.get("actor_cliente_id")).isEqualTo(fichaId);
            assertThat(aud.get("actor_id")).isNull();
            assertThat(aud.get("accion")).isEqualTo("CREAR");
            assertThat(aud.get("estado_anterior")).isNull();
            assertThat(aud.get("estado_nuevo")).isEqualTo("CONFIRMADA");
        });

        assertThat(ConversacionPruebas.contarOperaciones(ejecutor, sql, a, "EJECUTADA")).isEqualTo(1);
    }

    @Test
    void escenario2_doblePulsacion_mismoBotonEnDosEventos_unaSolaReservaYRepiteTexto() {
        String idConfirmar = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);

        // Primer evento de confirmación
        long ev1 = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idConfirmar);
        assertThat(filaEvento(ev1).get("estado")).isEqualTo("PROCESADO");
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isEqualTo(1);
        String texto1 = ultimoMensaje(a, WA_CLIENTE).texto();

        // Segundo evento con el mismo botón en otro evento entrante distinto
        long ev2 = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idConfirmar);
        assertThat(filaEvento(ev2).get("estado")).isEqualTo("PROCESADO");
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isEqualTo(1);
        String texto2 = ultimoMensaje(a, WA_CLIENTE).texto();
        assertThat(texto2).isEqualTo(texto1);
    }

    @Test
    void escenario3_caducada_trasDiezMinutos_rechazaSinReservaYMarcaCaducada() {
        String idConfirmar = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);

        // Adelantar reloj 10 minutos para vencer la vigencia de la operación pendiente
        reloj.adelantar(Duration.ofMinutes(10));

        long ev = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idConfirmar);
        assertThat(filaEvento(ev).get("estado")).isEqualTo("PROCESADO");

        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isZero();
        assertThat(ConversacionPruebas.contarOperaciones(ejecutor, sql, a, "CADUCADA")).isEqualTo(1);
    }

    @Test
    void escenario4_sustituidaYSi_confirmarAnteriorFallaYSiEjecutaVigente() {
        // Recorrido hasta franjas para capturar dos franjas distintas
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "hola");
        var msgMenu = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", msgMenu.idExacto("m:RESERVAR"));
        var msgServicios = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", msgServicios.primerIdQueEmpieceCon("s:"));
        var msgProfesionales = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", msgProfesionales.primerIdQueEmpieceCon("b:"));
        var msgDias = ultimoMensaje(a, WA_CLIENTE);
        String idDia = msgDias.idsSeleccion().size() > 1 ? msgDias.idsSeleccion().get(1) : msgDias.primerIdQueEmpieceCon("d:");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", idDia);

        var msgFranjas = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgFranjas.idsSeleccion().size()).isGreaterThanOrEqualTo(2);
        String franjaA = msgFranjas.idsSeleccion().get(0);
        String franjaB = msgFranjas.idsSeleccion().get(1);

        // Elegir franja A y aceptar privacidad → Resumen A
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", franjaA);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "p:S");
        var msgResumenA = ultimoMensaje(a, WA_CLIENTE);
        String idConfirmarA = msgResumenA.primerIdQueTermineCon(":S");
        long opAId = Long.parseLong(idConfirmarA.split(":")[1]);

        // Elegir otra franja (B) directamente → Resumen B
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", franjaB);
        var msgResumenB = ultimoMensaje(a, WA_CLIENTE);
        String idConfirmarB = msgResumenB.primerIdQueTermineCon(":S");
        long opBId = Long.parseLong(idConfirmarB.split(":")[1]);

        assertThat(opBId).isNotEqualTo(opAId);
        assertThat(sqlOperacion(a, opAId).get("estado")).isEqualTo("ANULADA");
        assertThat(sqlOperacion(a, opBId).get("estado")).isEqualTo("VIGENTE");

        // Confirmar de A → 0 reservas (A sigue ANULADA)
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", idConfirmarA);
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isZero();
        assertThat(sqlOperacion(a, opAId).get("estado")).isEqualTo("ANULADA");

        // Después «sí» → se ejecuta la operación vigente B (1 reserva)
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "sí");
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isEqualTo(1);
        assertThat(sqlOperacion(a, opBId).get("estado")).isEqualTo("EJECUTADA");
    }

    @Test
    void escenario5_textoNoReconocido_anulaOperacionVigente_siPosteriorNoReserva() {
        String idConfirmar = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);
        long opId = Long.parseLong(idConfirmar.split(":")[1]);
        assertThat(ConversacionPruebas.contarOperaciones(ejecutor, sql, a, "VIGENTE")).isEqualTo(1);

        // «qué tal» no reconocido → muestra menú principal y anula la operación vigente
        long evQueTal = enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "qué tal");
        assertThat(filaEvento(evQueTal).get("estado")).isEqualTo("PROCESADO");
        assertThat(sqlOperacion(a, opId).get("estado")).isEqualTo("ANULADA");

        // Un «sí» posterior sin operación vigente no crea reservas y la operación sigue ANULADA
        long evSi = enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", "sí");
        assertThat(filaEvento(evSi).get("estado")).isEqualTo("PROCESADO");
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isZero();
        assertThat(sqlOperacion(a, opId).get("estado")).isEqualTo("ANULADA");
    }

    @Test
    void escenario6_aislamiento_mismoWaIdEnDosNegocios_datosYAccionesCompletamenteSeparadas() {
        // Propuesta en negocio A
        String idConfirmarA = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);
        long opAId = Long.parseLong(idConfirmarA.split(":")[1]);
        assertThat(sqlOperacion(a, opAId).get("estado")).isEqualTo("VIGENTE");

        // El botón Confirmar de una operación de A enviado por el número de B → NO_ENCONTRADA y A sigue VIGENTE
        long evConfirmarEnB = enviarSeleccion(NUMERO_B, WA_CLIENTE, "Cliente Ficticio", idConfirmarA);
        assertThat(filaEvento(evConfirmarEnB).get("estado")).isEqualTo("PROCESADO");
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, b)).isZero();
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isZero();
        assertThat(sqlOperacion(a, opAId).get("estado")).isEqualTo("VIGENTE");

        // Confirmar la operación en A
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Ficticio", idConfirmarA);
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isEqualTo(1);
        long reservaAId = ultimoMensaje(a, WA_CLIENTE).reservaId();

        // El mismo wa_id escribe al negocio B: conversaciones y fichas separadas
        enviarTexto(NUMERO_B, WA_CLIENTE, "Cliente Ficticio", "hola");
        long convA = sqlConversacionId(a, WA_CLIENTE);
        long convB = sqlConversacionId(b, WA_CLIENTE);
        assertThat(convB).isNotEqualTo(convA);
        assertThat(ConversacionPruebas.contarFichasConWa(ejecutor, sql, b, WA_CLIENTE)).isZero();

        // «Mis citas» por B no muestra la cita de A
        var msgMenuB = ultimoMensaje(b, WA_CLIENTE);
        enviarSeleccion(NUMERO_B, WA_CLIENTE, "Cliente Ficticio", msgMenuB.idExacto("m:MIS_CITAS"));
        var msgMisCitasB = ultimoMensaje(b, WA_CLIENTE);
        assertThat(msgMisCitasB.idsSeleccion()).noneMatch(id -> id.contains(String.valueOf(reservaAId)));
        assertThat(msgMisCitasB.texto()).doesNotContain("BT-" + reservaAId);

        // Una cita c:<id> de A pedida por B → «no disponible» y sin acciones
        enviarSeleccion(NUMERO_B, WA_CLIENTE, "Cliente Ficticio", "c:" + reservaAId);
        var msgCitaAjena = ultimoMensaje(b, WA_CLIENTE);
        assertThat(msgCitaAjena.idsSeleccion()).noneMatch(id -> id.startsWith("a:"));
    }

    @Test
    void escenario7_idsManipulados_citaDeOtraFichaOConfirmarSinAviso_rechazaSinAccionesNiFichas() {
        // Cliente 1 completa reserva en negocio A
        String idConf1 = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente Uno", idConf1);
        long reserva1Id = ultimoMensaje(a, WA_CLIENTE).reservaId();
        long ficha1Id = ((Number) sqlFicha(a, WA_CLIENTE).get("id")).longValue();

        // Cliente 2 (otro wa_id en el mismo negocio) completa consentimiento para tener ficha propia
        String waCliente2 = "51999000099";
        alcanzarResumen(a, NUMERO_A, waCliente2);
        long ficha2Id = ((Number) sqlFicha(a, waCliente2).get("id")).longValue();
        assertThat(ficha2Id).isNotEqualTo(ficha1Id);

        // Cliente 2 pide c:<id de cita de cliente 1> → no disponible y sin acciones
        enviarSeleccion(NUMERO_A, waCliente2, "Cliente Dos", "c:" + reserva1Id);
        var msgRespCitaAjena = ultimoMensaje(a, waCliente2);
        assertThat(msgRespCitaAjena.idsSeleccion()).noneMatch(id -> id.startsWith("a:"));
        assertThat(msgRespCitaAjena.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");

        // Cliente 3 sin aviso previo envía p:S → 0 fichas creadas
        String waCliente3 = "51999000088";
        enviarSeleccion(NUMERO_A, waCliente3, "Cliente Tres", "p:S");
        assertThat(ConversacionPruebas.contarFichasConWa(ejecutor, sql, a, waCliente3)).isZero();
    }

    @Test
    void escenario8_misCitas_cancelarYReprogramar_actualizanEstadoYVersionConAuditoria() {
        // Reservar primera cita R1
        String idConf1 = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idConf1);
        long r1Id = ultimoMensaje(a, WA_CLIENTE).reservaId();
        long fichaId = ((Number) sqlFicha(a, WA_CLIENTE).get("id")).longValue();

        // Mis citas → la cita → Cancelar → resumen → Confirmar → CANCELADA con auditoría
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", "m:MIS_CITAS");
        var msgCitas1 = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgCitas1.idExacto("c:" + r1Id));

        var msgAcciones1 = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgAcciones1.primerIdQueEmpieceCon("a:" + r1Id + ":C"));

        var msgResumenCanc = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgResumenCanc.primerIdQueTermineCon(":S"));

        assertThat(sqlReserva(a, r1Id).get("estado")).isEqualTo("CANCELADA");
        var audCanc = sqlAuditoriasReserva(a, r1Id);
        assertThat(audCanc).anySatisfy(aud -> {
            assertThat(aud.get("accion")).isEqualTo("CANCELAR");
            assertThat(aud.get("actor_cliente_id")).isEqualTo(fichaId);
            assertThat(aud.get("actor_id")).isNull();
            assertThat(aud.get("estado_anterior")).isEqualTo("CONFIRMADA");
            assertThat(aud.get("estado_nuevo")).isEqualTo("CANCELADA");
        });

        // Otra reserva R2
        String idConf2 = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idConf2);
        long r2Id = ultimoMensaje(a, WA_CLIENTE).reservaId();
        var r2Antes = sqlReserva(a, r2Id);
        Instant inicioAntes = ((Timestamp) r2Antes.get("inicio")).toInstant();
        int versionAntes = ((Number) r2Antes.get("version")).intValue();

        // Reprogramar R2 → día → franja → Confirmar → nuevo inicio y sube versión
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", "m:MIS_CITAS");
        var msgCitas2 = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgCitas2.idExacto("c:" + r2Id));

        var msgAcciones2 = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgAcciones2.primerIdQueEmpieceCon("a:" + r2Id + ":R"));

        var msgDiasCambio = ultimoMensaje(a, WA_CLIENTE);
        String idDiaCambio = msgDiasCambio.idsSeleccion().size() > 2 ? msgDiasCambio.idsSeleccion().get(2)
                : msgDiasCambio.primerIdQueEmpieceCon("rd:");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idDiaCambio);

        var msgFranjasCambio = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgFranjasCambio.primerIdQueEmpieceCon("rf:"));

        var msgResumenReprog = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgResumenReprog.primerIdQueTermineCon(":S"));

        var r2Despues = sqlReserva(a, r2Id);
        Instant inicioDespues = ((Timestamp) r2Despues.get("inicio")).toInstant();
        int versionDespues = ((Number) r2Despues.get("version")).intValue();

        assertThat(inicioDespues).isNotEqualTo(inicioAntes);
        assertThat(versionDespues).isGreaterThan(versionAntes);
        assertThat(r2Despues.get("estado")).isEqualTo("CONFIRMADA");

        var audReprog = sqlAuditoriasReserva(a, r2Id);
        assertThat(audReprog).anySatisfy(aud -> {
            assertThat(aud.get("accion")).isEqualTo("REPROGRAMAR");
            assertThat(aud.get("actor_cliente_id")).isEqualTo(fichaId);
            assertThat(aud.get("actor_id")).isNull();
            assertThat(aud.get("estado_anterior")).isEqualTo("CONFIRMADA");
            assertThat(aud.get("estado_nuevo")).isEqualTo("CONFIRMADA");
        });
    }

    @Test
    void escenario9_negocioSuspendido_entrePropuestaYConfirmacion_descartaSinReservaNiMensaje() {
        String idConfirmar = alcanzarResumen(a, NUMERO_A, WA_CLIENTE);
        long convId = sqlConversacionId(a, WA_CLIENTE);
        int msgsAntes = ConversacionPruebas.contarMensajesSalientes(ejecutor, sql, a, convId);

        // Suspender negocio A entre la propuesta y la confirmación
        sql.update("UPDATE negocio SET estado = 'SUSPENDIDO' WHERE id = ?", a);

        long evConfirmar = enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idConfirmar);

        // V2-08 descarta el evento en resolución como NEGOCIO_NO_OPERATIVO
        var filaEv = filaEvento(evConfirmar);
        assertThat(filaEv.get("estado")).isEqualTo("DESCARTADO");
        assertThat(filaEv.get("motivo")).isEqualTo("NEGOCIO_NO_OPERATIVO");

        // 0 reservas y ningún mensaje saliente nuevo
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isZero();
        assertThat(ConversacionPruebas.contarMensajesSalientes(ejecutor, sql, a, convId)).isEqualTo(msgsAntes);
    }
}
