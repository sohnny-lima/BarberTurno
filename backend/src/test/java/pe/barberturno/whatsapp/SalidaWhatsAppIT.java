package pe.barberturno.whatsapp;

import java.sql.Timestamp;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas de integración del outbox transaccional {@link SalidaWhatsApp} (12-v2-09-whatsapp-salida.md §4.1, CI-1).
 * Verifica atomicidad transaccional, obligatoriedad de transacción activa ({@code MANDATORY}),
 * aislamiento por negocio de conversaciones y reservas, y validaciones de origen y contenido.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class SalidaWhatsAppIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private SalidaWhatsApp salida;

    @Test
    void encolarTexto_enTransaccionConfirmada_insertaPendienteConCamposExactos() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long reservaId = crearReserva(a, WA_CLIENTE);

        long id = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarTexto(conversacionId, "Hola, confirmamos tu turno.", OrigenMensaje.ASISTENTE,
                        OptionalLong.of(reservaId))));

        assertThat(id).isPositive();
        var fila = filaMensaje(id);
        assertThat(fila.get("negocio_id")).isEqualTo(a);
        assertThat(fila.get("conversacion_id")).isEqualTo(conversacionId);
        assertThat(fila.get("reserva_id")).isEqualTo(reservaId);
        assertThat(fila.get("direccion")).isEqualTo("SALIENTE");
        assertThat(fila.get("origen")).isEqualTo("ASISTENTE");
        assertThat(fila.get("tipo")).isEqualTo("TEXTO");
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat(fila.get("intentos")).isEqualTo((short) 0);
        assertThat(fila.get("inciertos")).isEqualTo((short) 0);
        assertThat(fila.get("wamid")).isNull();
        assertThat(fila.get("enviado_como")).isNull();
        assertThat(fila.get("error_codigo")).isNull();
        assertThat(fila.get("codigo_meta")).isNull();
        assertThat(((Timestamp) fila.get("proximo_intento")).toInstant()).isEqualTo(reloj.instant());
        assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(reloj.instant());
        assertThat(fila.get("contenido").toString()).contains("Hola, confirmamos tu turno.");
    }

    @Test
    void encolarInteractivo_enTransaccionConfirmada_insertaPendienteConObjetoInteractivo() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        ObjectNode interactivo = mapper.createObjectNode();
        interactivo.put("type", "button");
        interactivo.putObject("body").put("text", "¿Deseas confirmar?");

        long id = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarInteractivo(conversacionId, interactivo, OrigenMensaje.ASISTENTE, OptionalLong.empty())));

        var fila = filaMensaje(id);
        assertThat(fila.get("tipo")).isEqualTo("INTERACTIVO");
        assertThat(fila.get("origen")).isEqualTo("ASISTENTE");
        assertThat(fila.get("reserva_id")).isNull();
        assertThat(fila.get("contenido").toString()).contains("¿Deseas confirmar?");
    }

    @Test
    void encolarPlantilla_enTransaccionConfirmada_insertaPendienteConOrigenSistemaYTextoEnVentanaFalso() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long reservaId = crearReserva(a, WA_CLIENTE);
        Map<String, String> parametros = Map.of(
                "negocio", "Barbería Ficticia",
                "codigo", "BT-100",
                "fecha", "28 de octubre 10:00",
                "profesional", "Barbero A");

        long id = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarPlantilla(conversacionId, PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO,
                        parametros, OptionalLong.of(reservaId))));

        var fila = filaMensaje(id);
        assertThat(fila.get("tipo")).isEqualTo("PLANTILLA");
        assertThat(fila.get("origen")).isEqualTo("SISTEMA");
        assertThat(fila.get("contenido").toString())
                .contains("bt_cita_modificada_por_negocio")
                .contains("\"textoEnVentana\":false");
    }

    @Test
    void encolarAviso_enTransaccionConfirmada_insertaPendienteConOrigenSistemaYTextoEnVentanaVerdadero() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long reservaId = crearReserva(a, WA_CLIENTE);
        Map<String, String> parametros = Map.of(
                "negocio", "Barbería Ficticia",
                "fecha", "28 de octubre 10:00",
                "profesional", "Barbero A",
                "codigo", "BT-100");

        long id = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarAviso(conversacionId, PlantillaUtility.BT_RECORDATORIO_CITA,
                        parametros, OptionalLong.of(reservaId))));

        var fila = filaMensaje(id);
        assertThat(fila.get("tipo")).isEqualTo("PLANTILLA");
        assertThat(fila.get("origen")).isEqualTo("SISTEMA");
        assertThat(fila.get("contenido").toString())
                .contains("bt_recordatorio_cita")
                .contains("\"textoEnVentana\":true");
    }

    @Test
    void atomicidad_transaccionRevertida_noGuardaElMensaje() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);

        ejecutor.ejecutar(a, () -> {
            new TransactionTemplate(gestor).executeWithoutResult(status -> {
                salida.encolarTexto(conversacionId, "Texto que se revertirá", OrigenMensaje.SISTEMA, OptionalLong.empty());
                status.setRollbackOnly();
            });
            return null;
        });

        assertThat(contarMensajes(a, EstadoMensaje.PENDIENTE)).isZero();
    }

    @Test
    void encolado_sinTransaccionActiva_fallaConIllegalTransactionState() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);

        // Sin TransactionTemplate, fuera de transacción
        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                salida.encolarTexto(conversacionId, "Texto fuera de tx", OrigenMensaje.SISTEMA, OptionalLong.empty())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void encolar_conConversacionDeOtroNegocio_lanzaNoEncontrado() {
        long conversacionB = crearConversacion(b, WA_CLIENTE);

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarTexto(conversacionB, "Texto a negocio ajeno", OrigenMensaje.SISTEMA, OptionalLong.empty()))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test
    void encolar_conReservaDeOtroNegocio_lanzaNoEncontrado() {
        long conversacionA = crearConversacion(a, WA_CLIENTE);
        long reservaB = crearReserva(b, WA_CLIENTE);

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarTexto(conversacionA, "Texto con reserva ajena", OrigenMensaje.SISTEMA, OptionalLong.of(reservaB)))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test
    void encolar_conOrigenCliente_lanzaValidacion() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarTexto(conversacionId, "Texto con cliente", OrigenMensaje.CLIENTE, OptionalLong.empty()))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void encolarTexto_conTextoVacioOBlanco_lanzaValidacion(String textoInvalido) {
        long conversacionId = crearConversacion(a, WA_CLIENTE);

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarTexto(conversacionId, textoInvalido, OrigenMensaje.SISTEMA, OptionalLong.empty()))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @Test
    void encolarTexto_conTextoMayorA4096Caracteres_lanzaValidacion() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        String textoLargo = "a".repeat(4097);

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarTexto(conversacionId, textoLargo, OrigenMensaje.SISTEMA, OptionalLong.empty()))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @Test
    void encolarInteractivo_conJsonNuloOMayorA8KiB_lanzaValidacion() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarInteractivo(conversacionId, null, OrigenMensaje.SISTEMA, OptionalLong.empty()))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));

        ObjectNode interactivoEnorme = mapper.createObjectNode();
        interactivoEnorme.put("contenido", "x".repeat(9000)); // > 8192 bytes

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarInteractivo(conversacionId, interactivoEnorme, OrigenMensaje.SISTEMA, OptionalLong.empty()))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @Test
    void encolarPlantilla_conParametrosInvalidos_lanzaValidacion() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        Map<String, String> parametrosIncompletos = Map.of("negocio", "Barbería"); // faltan otros

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () ->
                new TransactionTemplate(gestor).execute(status ->
                        salida.encolarPlantilla(conversacionId, PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO,
                                parametrosIncompletos, OptionalLong.empty()))))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }
}
