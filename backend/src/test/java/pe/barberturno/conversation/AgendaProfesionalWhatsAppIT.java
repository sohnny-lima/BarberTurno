package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.ConversacionPruebas.MensajeSalientePrueba;
import pe.barberturno.customers.Cliente;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;
import pe.barberturno.whatsapp.dto.CodigoVinculoProfesionalDto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración de la consulta de agenda profesional por WhatsApp (CT-17, V2-DA-106).
 * Valida la respuesta a «agenda hoy» y «agenda mañana» (y su variante sin tilde «agenda manana»);
 * que solo se muestren citas PENDIENTE y CONFIRMADA de ese barbero en ese negocio y en ese día de Lima;
 * que las citas aparezcan ordenadas cronológicamente;
 * que no se incluyan citas canceladas ni de otros profesionales;
 * que no se expongan teléfonos ni correos de los clientes, formateando el nombre como «Nombre I.»;
 * que la respuesta de no acceso sea exactamente la misma ante un cliente sin vínculo,
 * un barbero vinculado en otro negocio y un barbero con membresía revocada (borrando en este último su vínculo);
 * y que ante más de veinticinco citas se aplique el recorte con «y N más: revisa la agenda web».
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class AgendaProfesionalWhatsAppIT extends WhatsAppSalidaPruebaBase {

    private static final String WA_BARBERO = "51999888111";
    private static final String SIN_ACCESO = "Este WhatsApp no tiene acceso a una agenda de esta barbería.";

    @Autowired
    private VinculoProfesionalWhatsAppService vinculoService;

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private JornadaRepository jornadas;

    @BeforeEach
    void preparar() {
        sql.update("UPDATE negocio SET limite_reservas_activas = 50, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, b, barberoB);

        // Vincular al barberoA con WA_BARBERO en negocio A
        CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(barberoA.getUsuario().getId()));
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + dto.codigo());
        assertThat(ultimoMensaje(a, WA_BARBERO).texto()).contains("Listo");
    }

    private long enviarTexto(String phoneNumberId, String waId, String nombrePerfil, String texto) {
        String wamid = ConversacionPruebas.nuevoWamid();
        var cuerpo = ConversacionPruebas.cuerpoTexto(mapper, phoneNumberId, waId, nombrePerfil, texto, wamid, reloj.instant());
        long id = ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
        return id;
    }

    private MensajeSalientePrueba ultimoMensaje(long negocioId, String waId) {
        return ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, negocioId, waId);
    }

    @Test
    void agendaHoy_muestraCitasOrdenadas_formatoExactoSinTelefonoNiCorreo() {
        LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());
        Instant inicio1 = TiempoNegocio.aInstante(hoy.atTime(9, 0));
        Instant inicio2 = TiempoNegocio.aInstante(hoy.atTime(11, 30));

        ejecutor.ejecutar(a, () -> {
            Cliente c1 = clientes.save(new Cliente("Juan Pérez Rodriguez", "51900111222"));
            Cliente c2 = clientes.save(new Cliente("Ana Gomez", "51900333444"));

            // Cita 1: 09:00 CONFIRMADA
            Reserva r1 = new Reserva(barberoA, servicioA, c1, inicio1, inicio1.plusSeconds(1800), new BigDecimal("25.00"));
            r1.confirmar(reloj.instant());
            reservas.save(r1);

            // Cita 2: 11:30 PENDIENTE
            Reserva r2 = new Reserva(barberoA, servicioA, c2, inicio2, inicio2.plusSeconds(1800), new BigDecimal("25.00"));
            reservas.save(r2);
            return null;
        });

        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "agenda hoy");

        var respuesta = ultimoMensaje(a, WA_BARBERO);
        String texto = respuesta.texto();

        String fechaEsperada = hoy.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("es-PE")));
        assertThat(texto).contains("Tu agenda de hoy, " + fechaEsperada + " (Barbería Norte):");
        assertThat(texto).contains("09:00 · Corte clásico · Juan P.");
        assertThat(texto).contains("11:30 · Corte clásico · Ana G.");

        // Verificar orden: 09:00 antes de 11:30
        assertThat(texto.indexOf("09:00")).isLessThan(texto.indexOf("11:30"));

        // CT-17: No debe figurar ningún teléfono ni correo electrónico
        assertThat(texto).doesNotContain("51900111222")
                .doesNotContain("51900333444")
                .doesNotContain("@");
    }

    @ParameterizedTest
    @ValueSource(strings = {"agenda mañana", "agenda manana", "AGENDA MAÑANA", "agenda   manana"})
    void agendaManana_aceptaVariantesDeComando_muestraCitasDelDiaSiguiente(String comando) {
        LocalDate manana = TiempoNegocio.fechaLima(reloj.instant()).plusDays(1);
        Instant inicioManana = TiempoNegocio.aInstante(manana.atTime(14, 0));

        ejecutor.ejecutar(a, () -> {
            Cliente c = clientes.save(new Cliente("Mario Vargas", "51988887766"));
            Reserva r = new Reserva(barberoA, servicioA, c, inicioManana, inicioManana.plusSeconds(1800), new BigDecimal("25.00"));
            r.confirmar(reloj.instant());
            reservas.save(r);
            return null;
        });

        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", comando);

        var respuesta = ultimoMensaje(a, WA_BARBERO);
        String texto = respuesta.texto();

        String fechaEsperada = manana.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("es-PE")));
        assertThat(texto).contains("Tu agenda de mañana, " + fechaEsperada + " (Barbería Norte):");
        assertThat(texto).contains("14:00 · Corte clásico · Mario V.");
    }

    @Test
    void agenda_noMuestraCitasDeOtroBarberoNiCanceladas() {
        LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());
        Instant inicioHoy = TiempoNegocio.aInstante(hoy.atTime(10, 0));

        ejecutor.ejecutar(a, () -> {
            Cliente c = clientes.save(new Cliente("Cliente Prueba", "51977778888"));

            // 1. Cita CANCELADA del barberoA
            Reserva rCancelada = new Reserva(barberoA, servicioA, c, inicioHoy, inicioHoy.plusSeconds(1800), new BigDecimal("25.00"));
            rCancelada.cancelar("Cancelada por cliente", reloj.instant());
            reservas.save(rCancelada);
            return null;
        });

        // 2. Cita CONFIRMADA del barberoB (en negocio B)
        ejecutor.ejecutar(b, () -> {
            Cliente cB = clientes.save(new Cliente("Cliente Negocio B", "51966665555"));
            Reserva rOtroBarbero = new Reserva(barberoB, servicioB, cB, inicioHoy, inicioHoy.plusSeconds(1800), new BigDecimal("30.00"));
            rOtroBarbero.confirmar(reloj.instant());
            reservas.save(rOtroBarbero);
            return null;
        });

        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "agenda hoy");

        var respuesta = ultimoMensaje(a, WA_BARBERO);
        assertThat(respuesta.texto()).contains("No tienes citas para hoy.");
        assertThat(respuesta.texto()).doesNotContain("Cliente Prueba").doesNotContain("Cliente Negocio B");
    }

    @Test
    void respuestaGenericaSinAcceso_esIdenticaEnLosTresCasos_yBorraVinculoSiMembresiaInactiva() {
        // Caso 1: Cliente sin vínculo escribe «agenda hoy» al negocio A
        String waSinVinculo = "51911112222";
        enviarTexto(NUMERO_A, waSinVinculo, "Cliente Anonimo", "agenda hoy");
        assertThat(ultimoMensaje(a, waSinVinculo).texto()).isEqualTo(SIN_ACCESO);

        // Caso 2: Barbero vinculado en «sur» (negocio B) escribe al número de «barberturno» (negocio A)
        String waBarberoSur = "51955554444";
        CodigoVinculoProfesionalDto dtoB = ejecutor.ejecutar(b, () -> vinculoService.generar(barberoB.getUsuario().getId()));
        enviarTexto(NUMERO_B, waBarberoSur, "Barbero Sur", "vincular " + dtoB.codigo());
        assertThat(ultimoMensaje(b, waBarberoSur).texto()).contains("Listo");

        // Ese barbero de B escribe al número de A: recibe la respuesta idéntica de no acceso
        enviarTexto(NUMERO_A, waBarberoSur, "Barbero Sur", "agenda hoy");
        assertThat(ultimoMensaje(a, waBarberoSur).texto()).isEqualTo(SIN_ACCESO);

        // Caso 3: Barbero vinculado en A cuya membresía es revocada
        ejecutor.ejecutar(a, () -> {
            sql.update("UPDATE membresia SET estado='REVOCADA' WHERE usuario_id=?", barberoA.getUsuario().getId());
            return null;
        });

        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "agenda hoy");
        assertThat(ultimoMensaje(a, WA_BARBERO).texto()).isEqualTo(SIN_ACCESO);

        // CT-17: Al fallar por membresía inactiva, el vínculo local se borra automáticamente
        int vinculosRestantes = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE wa_id=?", Integer.class, WA_BARBERO));
        assertThat(vinculosRestantes).as("el vínculo debe borrarse al detectar membresía revocada").isZero();
    }

    @Test
    void agendaConMasDe25Citas_recortaConTextoYNMas() {
        LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());

        // Crear 27 citas en el día para barberoA
        ejecutor.ejecutar(a, () -> {
            Cliente c = clientes.save(new Cliente("Cliente Masivo", "51900009999"));
            for (int i = 0; i < 27; i++) {
                Instant inicio = TiempoNegocio.aInstante(hoy.atTime(8, 0)).plus(Duration.ofMinutes(15L * i));
                Reserva r = new Reserva(barberoA, servicioA, c, inicio, inicio.plus(Duration.ofMinutes(15)), new BigDecimal("25.00"));
                r.confirmar(reloj.instant());
                reservas.save(r);
            }
            return null;
        });

        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "agenda hoy");

        var respuesta = ultimoMensaje(a, WA_BARBERO);
        String texto = respuesta.texto();

        // Verificar el aviso de recorte exacto
        assertThat(texto).contains("y 2 más: revisa la agenda web");

        // Contar líneas de citas: debe haber exactamente 25 líneas con formato HH:mm · ...
        long lineasCitas = texto.lines().filter(linea -> linea.matches("^\\d{2}:\\d{2} · .*")).count();
        assertThat(lineasCitas).isEqualTo(25);
    }
}
