package pe.barberturno.conversation;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.OrigenMensaje;
import pe.barberturno.whatsapp.PlantillaUtility;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.SalidaWhatsApp;
import pe.barberturno.whatsapp.SalidaWhatsAppService;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenario 10: Fallo técnico tras crear la ficha (rollback completo y reintento, CJ-8).
 * Se aísla en esta clase con {@link DirtiesContext} y {@link ConfiguracionFalloSalida} para no alterar el
 * contexto de Spring de las demás pruebas.
 * Verifica que si {@link SalidaWhatsApp#encolarInteractivo} falla con una excepción técnica durante la respuesta
 * a [Aceptar], la transacción de entrega completa se revierte atómicamente:
 * 0 fichas, conversación sin {@code cliente_id}, 0 operaciones, 0 mensajes nuevos en outbox,
 * y el evento queda {@code PENDIENTE} con {@code intentos = 1}. Al cesar el fallo y vencer {@code proximo_intento},
 * el procesador reintenta el evento con éxito, creando la ficha y enviando el resumen de confirmación.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(ConversacionGuiadaFalloTecnicoIT.ConfiguracionFalloSalida.class)
class ConversacionGuiadaFalloTecnicoIT extends WhatsAppSalidaPruebaBase {

    private static final AtomicBoolean FALLAR_SALIDA = new AtomicBoolean(false);

    /**
     * Configuración de prueba que sustituye {@link SalidaWhatsApp} por un decorador con fallo conmutable.
     */
    @TestConfiguration
    static class ConfiguracionFalloSalida {

        /**
         * Bean primario que decora al servicio real de salida.
         * @param real servicio real inyectado por su tipo concreto
         * @return implementación con fallo conmutable
         */
        @Bean
        @Primary
        SalidaWhatsApp salidaWhatsAppConFalloControlado(SalidaWhatsAppService real) {
            return new SalidaWhatsApp() {
                @Override
                public long encolarTexto(long conversacionId, String texto, OrigenMensaje origen, OptionalLong reservaId) {
                    if (FALLAR_SALIDA.get()) {
                        throw new RuntimeException("Fallo técnico simulado en salida");
                    }
                    return real.encolarTexto(conversacionId, texto, origen, reservaId);
                }

                @Override
                public long encolarInteractivo(long conversacionId, JsonNode interactivo, OrigenMensaje origen, OptionalLong reservaId) {
                    if (FALLAR_SALIDA.get()) {
                        throw new RuntimeException("Fallo técnico simulado en salida tras aceptar consentimiento");
                    }
                    return real.encolarInteractivo(conversacionId, interactivo, origen, reservaId);
                }

                @Override
                public long encolarPlantilla(long conversacionId, PlantillaUtility plantilla, Map<String, String> parametros, OptionalLong reservaId) {
                    if (FALLAR_SALIDA.get()) {
                        throw new RuntimeException("Fallo técnico simulado en salida");
                    }
                    return real.encolarPlantilla(conversacionId, plantilla, parametros, reservaId);
                }

                @Override
                public long encolarAviso(long conversacionId, PlantillaUtility plantilla, Map<String, String> parametros, OptionalLong reservaId) {
                    if (FALLAR_SALIDA.get()) {
                        throw new RuntimeException("Fallo técnico simulado en salida");
                    }
                    return real.encolarAviso(conversacionId, plantilla, parametros, reservaId);
                }
            };
        }
    }

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private JornadaRepository jornadas;

    @BeforeEach
    void configurarReglasYJornadas() {
        FALLAR_SALIDA.set(false);
        sql.update("UPDATE negocio SET limite_reservas_activas = 3, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
    }

    @AfterEach
    void restaurarEstado() {
        FALLAR_SALIDA.set(false);
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
        var cuerpo = ConversacionPruebas.cuerpoInteractivo(mapper, phoneNumberId, waId, nombrePerfil, seleccionId, esBoton, wamid, reloj.instant());
        ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
    }

    @Test
    void escenario10_falloTecnicoTrasAceptar_revierteFichaYOperacionYReintentaConExito() {
        // 1. Recorrer servicios, profesional, día y franja hasta el aviso de consentimiento
        enviarTexto(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", "hola");
        var msgMenu = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgMenu.idExacto("m:RESERVAR"));

        var msgServicios = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgServicios.primerIdQueEmpieceCon("s:"));

        var msgProfesionales = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgProfesionales.primerIdQueEmpieceCon("b:"));

        var msgDias = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA_CLIENTE);
        String idDia = msgDias.idsSeleccion().size() > 1 ? msgDias.idsSeleccion().get(1) : msgDias.primerIdQueEmpieceCon("d:");
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", idDia);

        var msgFranjas = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA_CLIENTE);
        enviarSeleccion(NUMERO_A, WA_CLIENTE, "Cliente WhatsApp", msgFranjas.primerIdQueEmpieceCon("f:"));

        // Comprobar que se presentó el aviso de privacidad y aún no existe ficha ni cliente_id
        var msgConsent = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA_CLIENTE);
        assertThat(msgConsent.idsSeleccion()).containsExactly("p:S", "p:N");
        long convId = msgConsent.conversacionId();
        assertThat(ConversacionPruebas.contarFichas(ejecutor, sql, a)).isZero();
        int msgsAntes = ConversacionPruebas.contarMensajesSalientes(ejecutor, sql, a, convId);

        // 2. Activar fallo técnico simulado solo para la respuesta a [Aceptar]
        FALLAR_SALIDA.set(true);

        // 3. Enviar evento de [Aceptar] (p:S) y procesar
        String wamidAceptar = ConversacionPruebas.nuevoWamid();
        var cuerpoAceptar = ConversacionPruebas.cuerpoInteractivo(mapper, NUMERO_A, WA_CLIENTE, "Cliente WhatsApp",
                "p:S", true, wamidAceptar, reloj.instant());
        long evAceptar = ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamidAceptar, NUMERO_A,
                cuerpoAceptar, reloj.instant());

        procesador.procesarLote();

        // 4. Verificar rollback completo (CJ-8):
        // 0 fichas, conversación sin cliente_id, 0 operaciones, 0 mensajes nuevos
        assertThat(ConversacionPruebas.contarFichas(ejecutor, sql, a)).isZero();
        Long clienteId = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT cliente_id FROM conversacion WHERE id = ?", Long.class, convId));
        assertThat(clienteId).isNull();
        assertThat(ConversacionPruebas.contarOperaciones(ejecutor, sql, a)).isZero();
        assertThat(ConversacionPruebas.contarMensajesSalientes(ejecutor, sql, a, convId)).isEqualTo(msgsAntes);

        // El evento sigue PENDIENTE con intentos incrementado y proximo_intento en el futuro
        var filaEv = sql.queryForMap("SELECT * FROM evento_whatsapp_entrante WHERE id = ?", evAceptar);
        assertThat(filaEv.get("estado")).isEqualTo("PENDIENTE");
        assertThat(((Number) filaEv.get("intentos")).intValue()).isEqualTo(1);
        Instant proximoIntento = ((Timestamp) filaEv.get("proximo_intento")).toInstant();
        assertThat(proximoIntento).isAfter(reloj.instant());

        // 5. Recuperación: deja de fallar, adelantar reloj hasta proximo_intento y volver a procesar
        FALLAR_SALIDA.set(false);
        reloj.adelantar(Duration.between(reloj.instant(), proximoIntento).plusSeconds(1));

        int procesados = procesador.procesarLote();
        assertThat(procesados).isEqualTo(1);

        // 6. Verificar éxito tras el reintento:
        // Evento PROCESADO, 1 ficha creada y enlazada a la conversación, 1 operación VIGENTE y resumen enviado
        var filaEvPost = sql.queryForMap("SELECT * FROM evento_whatsapp_entrante WHERE id = ?", evAceptar);
        assertThat(filaEvPost.get("estado")).isEqualTo("PROCESADO");

        assertThat(ConversacionPruebas.contarFichas(ejecutor, sql, a)).isEqualTo(1);
        long fichaId = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT id FROM cliente WHERE negocio_id = ? AND wa_id = ?", Long.class, a, WA_CLIENTE));
        Long clienteIdPost = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT cliente_id FROM conversacion WHERE id = ?", Long.class, convId));
        assertThat(clienteIdPost).isEqualTo(fichaId);

        assertThat(ConversacionPruebas.contarOperaciones(ejecutor, sql, a, "VIGENTE")).isEqualTo(1);
        var msgResumen = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA_CLIENTE);
        assertThat(msgResumen.idsSeleccion()).anyMatch(id -> id.endsWith(":S"));
    }
}
