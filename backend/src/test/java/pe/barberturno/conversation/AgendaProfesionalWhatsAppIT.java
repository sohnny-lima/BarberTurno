package pe.barberturno.conversation;

import pe.barberturno.platform.Membresia;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private JornadaRepository jornadas;

    private long barberoA2;

    @BeforeEach
    void preparar() {
        sql.update("UPDATE negocio SET limite_reservas_activas = 50, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        sql.update("UPDATE negocio SET nombre = 'Barbería Norte' WHERE id = ?", a);
        sql.update("UPDATE negocio SET nombre = 'Barbería Sur' WHERE id = ?", b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, b, barberoB);

        // Crear otro barbero en el mismo negocio A
        Usuario usuarioA2 = usuarios.save(new Usuario("Segundo Barbero A", "barbero-a2@ejemplo.test", "999000005", "hash-ficticio", Rol.BARBERO, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(a, usuarioA2.getId(), RolNegocio.BARBERO, reloj.instant()));
        Barbero bA2 = ejecutor.ejecutar(a, () -> barberos.save(new Barbero(usuarioA2, "Segundo Barbero A", reloj.instant())));
        barberoA2 = bA2.getId();
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA2);

        // Vincular al barberoA con WA_BARBERO en negocio A
        CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(barberoCuenta.getId()));
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

    private long insertarCita(long negocioId, long barberoId, long servicioId, String nombreCliente, String telefonoCliente, Instant inicio, String estado) {
        return ejecutor.ejecutar(negocioId, () -> {
            long clienteId = sql.queryForObject("""
                    INSERT INTO cliente(negocio_id, nombre, telefono, activo, creado_en, actualizado_en)
                    VALUES (?, ?, ?, true, ?, ?) RETURNING id
                    """, Long.class, negocioId, nombreCliente, telefonoCliente,
                    Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
            Instant fin = inicio.plusSeconds(1800);
            return sql.queryForObject("""
                    INSERT INTO reserva(negocio_id, cliente_id, barbero_id, servicio_id, inicio, fin,
                        duracion_ref_min, precio_ref, estado, creada_por, creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, ?, 30, 25.00, ?, ?, ?, ?) RETURNING id
                    """, Long.class, negocioId, clienteId, barberoId, servicioId,
                    Timestamp.from(inicio), Timestamp.from(fin),
                    estado, titular.getId(), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
        });
    }

    @Test
    void agendaHoy_muestraCitasOrdenadas_formatoExactoSinTelefonoNiCorreo() {
        LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());
        Instant inicio1 = hoy.atTime(9, 0).atZone(TiempoNegocio.ZONA).toInstant();
        Instant inicio2 = hoy.atTime(11, 30).atZone(TiempoNegocio.ZONA).toInstant();

        insertarCita(a, barberoA, servicioA, "Juan Pérez Rodriguez", "51900111222", inicio1, "CONFIRMADA");
        insertarCita(a, barberoA, servicioA, "Ana Gomez", "51900333444", inicio2, "PENDIENTE");

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
        Instant inicioManana = manana.atTime(14, 0).atZone(TiempoNegocio.ZONA).toInstant();

        insertarCita(a, barberoA, servicioA, "Mario Vargas", "51988887766", inicioManana, "CONFIRMADA");

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
        Instant inicioHoy = hoy.atTime(10, 0).atZone(TiempoNegocio.ZONA).toInstant();

        // 1. Cita CANCELADA del barberoA
        insertarCita(a, barberoA, servicioA, "Cliente Prueba", "51977778888", inicioHoy, "CANCELADA");

        // 2. Cita CONFIRMADA del barberoB (en negocio B)
        insertarCita(b, barberoB, servicioB, "Cliente Negocio B", "51966665555", inicioHoy, "CONFIRMADA");

        // 3. Cita CONFIRMADA del barberoA2 (segundo barbero en el MISMO negocio A)
        insertarCita(a, barberoA2, servicioA, "Cliente Segundo Barbero A", "51944443333", inicioHoy, "CONFIRMADA");

        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "agenda hoy");

        var respuesta = ultimoMensaje(a, WA_BARBERO);
        assertThat(respuesta.texto()).contains("No tienes citas para hoy.");
        assertThat(respuesta.texto())
                .doesNotContain("Cliente Prueba")
                .doesNotContain("Cliente Negocio B")
                .doesNotContain("Cliente Segundo Barbero A");
    }

    @Test
    void agenda_conCitasCercaDeMedianocheEnAyerHoyYManana_eInsertadasEnOrdenInverso_muestraSoloLasSuyasEnOrdenExacto() {
        LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());
        LocalDate ayer = hoy.minusDays(1);
        LocalDate manana = hoy.plusDays(1);

        Instant ayerNoche = ayer.atTime(23, 30).atZone(TiempoNegocio.ZONA).toInstant();
        Instant hoyMadrugada = hoy.atTime(0, 15).atZone(TiempoNegocio.ZONA).toInstant();
        Instant hoyMediodia = hoy.atTime(12, 0).atZone(TiempoNegocio.ZONA).toInstant();
        Instant hoyNoche = hoy.atTime(23, 45).atZone(TiempoNegocio.ZONA).toInstant();
        Instant mananaMadrugada = manana.atTime(0, 30).atZone(TiempoNegocio.ZONA).toInstant();
        Instant mananaNoche = manana.atTime(23, 15).atZone(TiempoNegocio.ZONA).toInstant();

        // Inserción en orden INVERSO cronológico (desde mañana hacia ayer)
        insertarCita(a, barberoA, servicioA, "Cliente Manana Noche", "51900000006", mananaNoche, "CONFIRMADA");
        insertarCita(a, barberoA, servicioA, "Cliente Manana Madrugada", "51900000005", mananaMadrugada, "CONFIRMADA");
        // Cita del segundo barbero en el MISMO negocio hoy
        insertarCita(a, barberoA2, servicioA, "Cliente Otro Barbero Mismo Negocio", "51900000099", hoyMediodia, "CONFIRMADA");
        insertarCita(a, barberoA, servicioA, "Cliente Hoy Noche", "51900000004", hoyNoche, "CONFIRMADA");
        insertarCita(a, barberoA, servicioA, "Cliente Hoy Mediodia", "51900000003", hoyMediodia, "PENDIENTE");
        insertarCita(a, barberoA, servicioA, "Cliente Hoy Madrugada", "51900000002", hoyMadrugada, "CONFIRMADA");
        insertarCita(a, barberoA, servicioA, "Cliente Ayer Noche", "51900000001", ayerNoche, "CONFIRMADA");

        // 1. Consulta «agenda hoy»: solo citas de hoy de barberoA, orden cronológico exacto
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "agenda hoy");
        String textoHoy = ultimoMensaje(a, WA_BARBERO).texto();

        String fechaHoyEsperada = hoy.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("es-PE")));
        assertThat(textoHoy).contains("Tu agenda de hoy, " + fechaHoyEsperada + " (Barbería Norte):");
        assertThat(textoHoy).contains("00:15 · Corte clásico · Cliente H.");
        assertThat(textoHoy).contains("12:00 · Corte clásico · Cliente H.");
        assertThat(textoHoy).contains("23:45 · Corte clásico · Cliente H.");

        // Orden cronológico estricto independientemente del orden de inserción inverso
        int idxHoyMadrugada = textoHoy.indexOf("00:15");
        int idxHoyMediodia = textoHoy.indexOf("12:00");
        int idxHoyNoche = textoHoy.indexOf("23:45");
        assertThat(idxHoyMadrugada).isLessThan(idxHoyMediodia);
        assertThat(idxHoyMediodia).isLessThan(idxHoyNoche);

        // No incluye citas de ayer, de mañana ni del otro barbero en el mismo negocio
        assertThat(textoHoy).doesNotContain("Cliente Ayer");
        assertThat(textoHoy).doesNotContain("Cliente Manana");
        assertThat(textoHoy).doesNotContain("Cliente Otro Barbero Mismo Negocio");
        assertThat(textoHoy).doesNotContain("23:15");
        assertThat(textoHoy).doesNotContain("00:30");

        // 2. Consulta «agenda mañana»: solo citas de mañana de barberoA, orden cronológico exacto
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "agenda mañana");
        String textoManana = ultimoMensaje(a, WA_BARBERO).texto();

        String fechaMananaEsperada = manana.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("es-PE")));
        assertThat(textoManana).contains("Tu agenda de mañana, " + fechaMananaEsperada + " (Barbería Norte):");
        assertThat(textoManana).contains("00:30 · Corte clásico · Cliente M.");
        assertThat(textoManana).contains("23:15 · Corte clásico · Cliente M.");

        int idxMananaMadrugada = textoManana.indexOf("00:30");
        int idxMananaNoche = textoManana.indexOf("23:15");
        assertThat(idxMananaMadrugada).isLessThan(idxMananaNoche);

        assertThat(textoManana).doesNotContain("Cliente Ayer");
        assertThat(textoManana).doesNotContain("Cliente Hoy");
        assertThat(textoManana).doesNotContain("Cliente Otro Barbero Mismo Negocio");
        assertThat(textoManana).doesNotContain("00:15");
        assertThat(textoManana).doesNotContain("23:45");
    }

    @Test
    void respuestaGenericaSinAcceso_esIdenticaEnLosTresCasos_yBorraVinculoSiMembresiaInactiva() {
        // Caso 1: Cliente sin vínculo escribe «agenda hoy» al negocio A
        String waSinVinculo = "51911112222";
        enviarTexto(NUMERO_A, waSinVinculo, "Cliente Anonimo", "agenda hoy");
        assertThat(ultimoMensaje(a, waSinVinculo).texto()).isEqualTo(SIN_ACCESO);

        // Caso 2: Barbero vinculado en «sur» (negocio B) escribe al número de «barberturno» (negocio A)
        String waBarberoSur = "51955554444";
        CodigoVinculoProfesionalDto dtoB = ejecutor.ejecutar(b, () -> vinculoService.generar(
                barberos.findById(barberoB).orElseThrow().getUsuario().getId()));
        enviarTexto(NUMERO_B, waBarberoSur, "Barbero Sur", "vincular " + dtoB.codigo());
        assertThat(ultimoMensaje(b, waBarberoSur).texto()).contains("Listo");

        // Ese barbero de B escribe al número de A: recibe la respuesta idéntica de no acceso
        enviarTexto(NUMERO_A, waBarberoSur, "Barbero Sur", "agenda hoy");
        assertThat(ultimoMensaje(a, waBarberoSur).texto()).isEqualTo(SIN_ACCESO);

        // Caso 3: Barbero vinculado en A cuya membresía es revocada
        ejecutor.ejecutar(a, () -> {
            sql.update("UPDATE membresia SET estado='REVOCADA', motivo_revocacion='DECISION_GESTOR' WHERE negocio_id=? AND usuario_id=?",
                    a, barberoCuenta.getId());
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

        // Crear 27 citas consecutivas de 30 min en el día para barberoA (desde las 07:00 hasta las 20:30)
        for (int i = 0; i < 27; i++) {
            Instant inicio = hoy.atTime(7, 0).atZone(TiempoNegocio.ZONA).toInstant().plus(Duration.ofMinutes(30L * i));
            insertarCita(a, barberoA, servicioA, "Cliente Masivo " + i, "5190000" + String.format("%04d", i), inicio, "CONFIRMADA");
        }

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
