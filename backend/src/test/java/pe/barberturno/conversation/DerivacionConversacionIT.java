package pe.barberturno.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;

/**
 * Derivación de extremo a extremo por la entrega real (V2-11, 16-v2-11 §2; CB-3, CB-4, CB-5): evento entrante →
 * procesador → orquestador → derivación (X11) → outbox y avisos, con PostgreSQL y RLS.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class DerivacionConversacionIT extends WhatsAppSalidaPruebaBase {
    private static final String WA = "51988000111";

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    private void texto(String cuerpo) {
        String wamid = ConversacionPruebas.nuevoWamid();
        var evento = ConversacionPruebas.cuerpoTexto(mapper, NUMERO_A, WA, "Rosa Quispe", cuerpo, wamid, reloj.instant());
        ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, NUMERO_A, evento, reloj.instant());
        procesador.procesarLote();
    }

    private void seleccion(String id) {
        String wamid = ConversacionPruebas.nuevoWamid();
        var evento = ConversacionPruebas.cuerpoInteractivo(mapper, NUMERO_A, WA, "Rosa Quispe", id,
                ConversacionPruebas.esBoton(id), wamid, reloj.instant());
        ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, NUMERO_A, evento, reloj.instant());
        procesador.procesarLote();
    }

    private Map<String, Object> conversacion() {
        return ejecutor.ejecutar(a, () -> sql.queryForMap(
                "SELECT * FROM conversacion WHERE negocio_id = ? AND wa_id = ?", a, WA));
    }

    private int salientes() {
        return ejecutor.ejecutar(a, () -> sql.queryForObject("""
                SELECT count(*) FROM mensaje_whatsapp m JOIN conversacion c ON c.id = m.conversacion_id
                WHERE c.negocio_id = ? AND c.wa_id = ? AND m.direccion = 'SALIENTE'""", Integer.class, a, WA));
    }

    private int entrantesGuardados() {
        return ejecutor.ejecutar(a, () -> sql.queryForObject("""
                SELECT count(*) FROM mensaje_whatsapp m JOIN conversacion c ON c.id = m.conversacion_id
                WHERE c.negocio_id = ? AND c.wa_id = ? AND m.direccion = 'ENTRANTE'""", Integer.class, a, WA));
    }

    private int avisosDerivacion() {
        return ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM notificacion WHERE negocio_id = ? AND tipo = 'DERIVACION'", Integer.class, a));
    }

    @Test
    void sinFicha_hablarConAlguien_pideConsentimiento_yAlAceptarCreaLaFichaYDeriva() {
        texto("hola");
        seleccion("m:PERSONA");
        var tras = conversacion();
        assertThat(tras.get("modo")).isEqualTo("ASISTENTE");
        assertThat(tras.get("cliente_id")).isNull();
        assertThat(ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA).idsSeleccion())
                .contains("p:S", "p:N");
        assertThat(entrantesGuardados()).isZero();
        assertThat(avisosDerivacion()).isZero();

        seleccion("p:S");
        var derivada = conversacion();
        assertThat(derivada.get("modo")).isEqualTo("HUMANO");
        assertThat(derivada.get("cliente_id")).isNotNull();
        assertThat(derivada.get("motivo_derivacion")).isEqualTo("PETICION");
        assertThat(entrantesGuardados()).as("la aceptación es el primer mensaje visible").isEqualTo(1);
        assertThat(avisosDerivacion()).isPositive();
        var acuse = ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, a, WA);
        assertThat(acuse.origen()).isEqualTo("SISTEMA");
    }

    @Test
    void sinFicha_rechazarElConsentimientoParaDerivar_noCreaFichaNiDeriva() {
        texto("hola");
        seleccion("m:PERSONA");
        seleccion("p:N");
        var tras = conversacion();
        assertThat(tras.get("modo")).isEqualTo("ASISTENTE");
        assertThat(tras.get("cliente_id")).isNull();
        assertThat(avisosDerivacion()).isZero();
        assertThat(ConversacionPruebas.contarFichasConWa(ejecutor, sql, a, WA)).isZero();
    }

    @Test
    void dosTextosNoReconocidosSeguidos_derivanPorSinProgreso_yUnoReconocidoEntreEllosReinicia() {
        texto("hola");
        seleccion("m:PERSONA");
        seleccion("p:S");
        // Ya hay ficha (derivada); se devuelve al asistente desde la base para probar el contador.
        ejecutor.ejecutar(a, () -> sql.update("""
                UPDATE conversacion SET modo = 'ASISTENTE', derivada_en = NULL, motivo_derivacion = NULL,
                    ultima_respuesta_personal_en = NULL, estado = '{}' WHERE negocio_id = ? AND wa_id = ?""", a, WA));
        texto("qwerty asdf");
        texto("menu");
        texto("zxcv bnm");
        assertThat(conversacion().get("modo")).as("«menu» reinició la cuenta").isEqualTo("ASISTENTE");
        texto("ñlkj poiu");
        var derivada = conversacion();
        assertThat(derivada.get("modo")).isEqualTo("HUMANO");
        assertThat(derivada.get("motivo_derivacion")).isEqualTo("SIN_PROGRESO");
    }

    @Test
    void enModoHumano_noHayRespuestaAutomatica_yElEntranteSeGuarda() {
        texto("hola");
        seleccion("m:PERSONA");
        seleccion("p:S");
        int salientesAntes = salientes();
        int entrantesAntes = entrantesGuardados();
        texto("¿me pueden atender hoy?");
        seleccion("m:RESERVAR");
        assertThat(salientes()).as("V2-RN-15: sin respuestas automáticas").isEqualTo(salientesAntes);
        assertThat(entrantesGuardados()).isEqualTo(entrantesAntes + 2);
        assertThat(conversacion().get("modo")).isEqualTo("HUMANO");
    }
}
