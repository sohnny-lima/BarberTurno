package pe.barberturno.conversation;

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
 * Pruebas de integración de la navegación y repetición de menús desde todos los pasos (CT-15, V2-DA-105).
 * Valida que la palabra de inicio «menu» vuelva a la bienvenida desde cualquier paso del flujo
 * (servicio, profesional, día, hora, consentimiento, resumen pendiente y conversación derivada a humano);
 * que una operación pendiente de confirmar no se confirme y quede descartada;
 * que las citas previamente confirmadas permanezcan intactas;
 * que una conversación derivada a humano retorne al asistente;
 * y que un texto libre no reconocido repita el menú del paso actual regenerado.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class NavegacionPasosConversacionIT extends WhatsAppSalidaPruebaBase {

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

    private Map<String, Object> sqlOperacion(long negocioId, long operacionId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForMap(
                "SELECT * FROM operacion_pendiente WHERE negocio_id = ? AND id = ?", negocioId, operacionId));
    }

    @Test
    void menu_desdePasoServicio_vuelveABienvenida() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        var msgMenu = ultimoMensaje(a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", msgMenu.idExacto("m:RESERVAR"));

        var msgServicios = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgServicios.primerIdQueEmpieceCon("s:")).isNotNull();

        // Enviar «menu» desde servicios
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "menu");
        var msgVuelta = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgVuelta.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");
    }

    @Test
    void menu_desdePasoProfesional_vuelveABienvenida() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).idExacto("m:RESERVAR"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("s:"));

        var msgProfesionales = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgProfesionales.primerIdQueEmpieceCon("b:")).isNotNull();

        // Enviar «menu» desde profesionales
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "menu");
        var msgVuelta = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgVuelta.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");
    }

    @Test
    void menu_desdePasoDia_vuelveABienvenida() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).idExacto("m:RESERVAR"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("s:"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("b:"));

        var msgDias = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgDias.primerIdQueEmpieceCon("d:")).isNotNull();

        // Enviar «menu» desde días
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "menu");
        var msgVuelta = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgVuelta.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");
    }

    @Test
    void menu_desdePasoHoraFranja_vuelveABienvenida() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).idExacto("m:RESERVAR"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("s:"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("b:"));
        var msgDias = ultimoMensaje(a, WA_CLIENTE);
        String idDia = msgDias.idsSeleccion().size() > 1 ? msgDias.idsSeleccion().get(1) : msgDias.primerIdQueEmpieceCon("d:");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", idDia);

        var msgFranjas = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgFranjas.primerIdQueEmpieceCon("f:")).isNotNull();

        // Enviar «menu» desde franjas
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "menu");
        var msgVuelta = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgVuelta.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");
    }

    @Test
    void menu_desdePasoConsentimiento_vuelveABienvenidaYSinCrearFicha() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).idExacto("m:RESERVAR"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("s:"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("b:"));
        var msgDias = ultimoMensaje(a, WA_CLIENTE);
        String idDia = msgDias.idsSeleccion().size() > 1 ? msgDias.idsSeleccion().get(1) : msgDias.primerIdQueEmpieceCon("d:");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", idDia);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("f:"));

        var msgConsentimiento = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgConsentimiento.idsSeleccion()).contains("p:S", "p:N");

        // Enviar «menu» desde consentimiento
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "menu");
        var msgVuelta = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgVuelta.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");
        assertThat(ConversacionPruebas.contarFichasConWa(ejecutor, sql, a, WA_CLIENTE)).isZero();
    }

    @Test
    void menu_desdePasoResumenPendiente_anulaOperacionPendienteYVuelveABienvenida_sinConfirmar() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).idExacto("m:RESERVAR"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("s:"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("b:"));
        var msgDias = ultimoMensaje(a, WA_CLIENTE);
        String idDia = msgDias.idsSeleccion().size() > 1 ? msgDias.idsSeleccion().get(1) : msgDias.primerIdQueEmpieceCon("d:");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", idDia);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("f:"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", "p:S");

        var msgResumen = ultimoMensaje(a, WA_CLIENTE);
        String idConfirmar = msgResumen.primerIdQueTermineCon(":S");
        long opId = Long.parseLong(idConfirmar.split(":")[1]);
        assertThat(sqlOperacion(a, opId).get("estado")).isEqualTo("VIGENTE");

        // Enviar «menu» desde resumen
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "menu");

        // 1. Vuelve a la bienvenida
        var msgVuelta = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgVuelta.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");

        // 2. La operación pendiente NO se confirma: queda ANULADA
        assertThat(sqlOperacion(a, opId).get("estado")).isEqualTo("ANULADA");
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isZero();

        // 3. Un «sí» posterior no crea reservas porque la operación ya fue anulada
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "sí");
        assertThat(ConversacionPruebas.contarReservas(ejecutor, sql, a)).isZero();
    }

    @Test
    void menu_desdeConversacionDerivada_vuelveAlAsistente_citaConfirmadaPreviaSigueIgual() {
        // Crear cliente con cita previa CONFIRMADA
        long reservaPreviaId = crearReserva(a, WA_CLIENTE, INICIO_RESERVA_BASE);

        // Iniciar conversación y solicitar hablar con persona
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", "m:PERSONA");

        // Modo pasa a HUMANO
        String modo = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT modo FROM conversacion WHERE negocio_id=? AND wa_id=?", String.class, a, WA_CLIENTE));
        assertThat(modo).isEqualTo("HUMANO");

        // Enviar «menu» desde modo humano
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "menu");

        // Vuelve al asistente
        String modoDevuelto = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT modo FROM conversacion WHERE negocio_id=? AND wa_id=?", String.class, a, WA_CLIENTE));
        assertThat(modoDevuelto).isEqualTo("ASISTENTE");

        // Mensaje recibido es la bienvenida
        var msgVuelta = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgVuelta.idsSeleccion()).contains("m:RESERVAR", "m:MIS_CITAS");

        // La cita confirmada previa sigue CONFIRMADA
        String estadoCita = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT estado FROM reserva WHERE id = ?", String.class, reservaPreviaId));
        assertThat(estadoCita).isEqualTo("CONFIRMADA");
    }

    @Test
    void textoNoReconocido_repiteMenuDelPasoActual_enServicios() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).idExacto("m:RESERVAR"));

        // En paso servicios, enviar texto libre no reconocido
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "cualquier cosa");

        var msgRepetido = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgRepetido.texto()).contains("No entendí");
        assertThat(msgRepetido.primerIdQueEmpieceCon("s:")).as("debe repetir el menú de servicios").isNotNull();
    }

    @Test
    void textoNoReconocido_repiteMenuDelPasoActual_enDias() {
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "hola");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).idExacto("m:RESERVAR"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("s:"));
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente CT15", ultimoMensaje(a, WA_CLIENTE).primerIdQueEmpieceCon("b:"));

        // En paso días, enviar texto libre no reconocido
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente CT15", "texto inesperado");

        var msgRepetido = ultimoMensaje(a, WA_CLIENTE);
        assertThat(msgRepetido.texto()).contains("No entendí");
        assertThat(msgRepetido.primerIdQueEmpieceCon("d:")).as("debe repetir el menú de días").isNotNull();
    }
}
