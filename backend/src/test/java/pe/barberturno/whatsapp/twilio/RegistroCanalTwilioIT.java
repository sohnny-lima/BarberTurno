package pe.barberturno.whatsapp.twilio;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.support.JdbcNegocioPruebas;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.whatsapp.CanalWhatsApp;
import pe.barberturno.whatsapp.CanalWhatsAppRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración del registro automático e idempotente del canal de Twilio Sandbox (V2-DA-102).
 * Verifica que el servicio {@link RegistroCanalTwilioService} registre el canal con los dígitos del
 * Sandbox como {@code phone_number_id} y {@code waba_id}, que la ejecución repetida no duplique
 * filas, que un canal inactivo sea reactivado, y que se rechace la operación ante negocios inexistentes
 * o con canales incompatibles.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=",
        "barberturno.admin.password=",
        "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "barberturno.whatsapp.twilio.account-sid=AC0123456789abcdef0123456789abcdef",
        "barberturno.whatsapp.twilio.auth-token=token-ficticio-twilio-it",
        "barberturno.whatsapp.twilio.numero=+14155238886",
        "barberturno.whatsapp.twilio.negocio-slug=barberturno",
        "barberturno.whatsapp.twilio.url-publica=https://pruebas.barberturno.test"
})
@ActiveProfiles({"test", "twilio"})
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegistroCanalTwilioIT {

    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String PHONE_NUMBER_ID = "14155238886";

    @Autowired private RegistroCanalTwilioService registroCanal;
    @Autowired private CanalWhatsAppRepository canales;
    @Autowired private DirectorioNegocios directorio;
    @Autowired private JdbcNegocioPruebas sql;
    @Autowired private RelojAjustable reloj;
    @Autowired private EntityManager entityManager;
    @Autowired private org.springframework.transaction.PlatformTransactionManager gestor;

    private long negocioId;

    @BeforeEach
    void preparar() {
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
        negocioId = directorio.buscar("barberturno").orElseThrow();
    }

    @AfterEach
    void limpiar() {
        LimpiezaBaseDatos.limpiar(sql);
    }

    @Test
    void registrar_idempotente_registraUnaSolaVezAunqueSeEjecuteDosVeces() {
        // Primera ejecución: crea el canal
        registroCanal.registrar();

        var canalOpt = canales.findByPhoneNumberId(PHONE_NUMBER_ID);
        assertThat(canalOpt).isPresent();
        CanalWhatsApp canal = canalOpt.get();
        assertThat(canal.negocioId()).isEqualTo(negocioId);
        assertThat(canal.phoneNumberId()).isEqualTo(PHONE_NUMBER_ID);
        // wabaId y numeroVisible no son públicos en la entidad: se comprueban en la tabla.
        assertThat(sql.queryForObject("SELECT waba_id FROM canal_whatsapp WHERE negocio_id = ?", String.class, negocioId))
                .isEqualTo(PHONE_NUMBER_ID);
        assertThat(sql.queryForObject("SELECT numero_visible FROM canal_whatsapp WHERE negocio_id = ?", String.class,
                negocioId)).isEqualTo(NUMERO_SANDBOX);
        assertThat(canal.activo()).isTrue();

        int cantidadCanales = sql.queryForObject(
                "SELECT count(*) FROM canal_whatsapp WHERE negocio_id = ?", Integer.class, negocioId);
        assertThat(cantidadCanales).isEqualTo(1);

        // Segunda ejecución consecutiva: idempotente, no crea duplicados ni falla
        registroCanal.registrar();

        int cantidadDespues = sql.queryForObject(
                "SELECT count(*) FROM canal_whatsapp WHERE negocio_id = ?", Integer.class, negocioId);
        assertThat(cantidadDespues).isEqualTo(1);
    }

    @Test
    void registrar_canalInactivo_reactivaElCanalExistente() {
        // Crear canal previamente y desactivarlo
        registroCanal.registrar();
        // La entidad no expone la desconexión fuera de su paquete: se marca en la tabla.
        sql.update("UPDATE canal_whatsapp SET estado = 'DESCONECTADO' WHERE negocio_id = ?", negocioId);

        assertThat(canales.findByPhoneNumberId(PHONE_NUMBER_ID).orElseThrow().activo()).isFalse();

        // Ejecutar registro: debe reactivarlo sin crear otra fila
        registroCanal.registrar();

        CanalWhatsApp reactivado = canales.findByPhoneNumberId(PHONE_NUMBER_ID).orElseThrow();
        assertThat(reactivado.activo()).isTrue();
        int total = sql.queryForObject(
                "SELECT count(*) FROM canal_whatsapp WHERE negocio_id = ?", Integer.class, negocioId);
        assertThat(total).isEqualTo(1);
    }

    @Test
    void registrar_negocioInexistente_lanzaIllegalStateException() {
        TwilioPropiedades propiedadesNegocioInexistente = new TwilioPropiedades(
                "AC0123456789abcdef0123456789abcdef", "token", NUMERO_SANDBOX,
                "https://pruebas.barberturno.test", "slug-inexistente", Duration.ofSeconds(3),
                "https://api.twilio.com");
        RegistroCanalTwilioService servicioInexistente = new RegistroCanalTwilioService(
                entityManager, canales, reloj, propiedadesNegocioInexistente);

        // Sin el proxy de Spring no hay @Transactional: la transacción la abre la prueba.
        assertThatThrownBy(() -> new TransactionTemplate(gestor).executeWithoutResult(estado -> servicioInexistente.registrar()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Falta el negocio de prueba para Twilio Sandbox.");
    }

    @Test
    void registrar_negocioConCanalDistinto_lanzaIllegalStateException() {
        // Registrar un canal con número diferente en el negocio
        new TransactionTemplate(gestor).executeWithoutResult(status -> {
            CanalWhatsApp canalDistinto = new CanalWhatsApp(negocioId, "90001", "10001",
                    "+51999000001", reloj.instant());
            canales.save(canalDistinto);
        });

        assertThatThrownBy(() -> registroCanal.registrar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El negocio de prueba ya tiene un canal distinto de Twilio Sandbox.");
    }
}
