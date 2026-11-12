package pe.barberturno.conversation;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.conversation.ConversacionPruebas.MensajeSalientePrueba;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.CodigoVinculoWhatsApp;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;
import pe.barberturno.whatsapp.dto.CodigoVinculoProfesionalDto;
import pe.barberturno.whatsapp.dto.EstadoVinculoProfesionalDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de la vinculación profesional por WhatsApp (CT-16, V2-DA-106).
 * Valida la generación del código con SecureRandom y expiración a 10 min;
 * la vinculación mediante «vincular XXXX-XXXX» (tolerante a minúsculas y sin guiones);
 * la conservación única del hash SHA-256 en la base de datos (nunca el código en claro);
 * el rechazo ante reutilización, caducidad, códigos de otro negocio o reemplazo por uno nuevo;
 * el bloqueo tras el quinto fallo consecutivo en una hora;
 * el 404 ante intentos de generación por clientes o cuentas ajenas al negocio;
 * la consulta GET de estado sin exponer números ni identificadores de WhatsApp;
 * y la revocación de acceso tanto por DELETE web como por el comando WhatsApp «desvincular».
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class VinculoProfesionalWhatsAppIT extends WhatsAppSalidaPruebaBase {

    private static final String WA_BARBERO = "51999888777";

    @Autowired
    private VinculoProfesionalWhatsAppService vinculoService;

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private JornadaRepository jornadas;

    @BeforeEach
    void configurarReglas() {
        sql.update("UPDATE negocio SET limite_reservas_activas = 3, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
    }

    private long enviarTexto(String phoneNumberId, String waId, String nombrePerfil, String texto) {
        String wamid = ConversacionPruebas.nuevoWamid();
        var cuerpo = ConversacionPruebas.cuerpoTexto(mapper, phoneNumberId, waId, nombrePerfil, texto, wamid, reloj.instant());
        long id = ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
        return id;
    }

    private long usuarioDeBarbero(long negocioId, long barberoId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT usuario_id FROM barbero WHERE id=?", Long.class, barberoId));
    }

    private MensajeSalientePrueba ultimoMensaje(long negocioId, String waId) {
        return ConversacionPruebas.leerUltimoMensajeSaliente(ejecutor, sql, mapper, negocioId, waId);
    }

    @Test
    void generarYVincular_conFormatoNormalizado_vinculaCorrectamente_yGuardaSoloElHash() {
        long usuarioId = barberoCuenta.getId();

        // 1. Generar código desde la cuenta del barbero
        CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));
        assertThat(dto.codigo()).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}$");
        assertThat(dto.caducaEn()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(10)));

        // 2. Verificar que en la tabla solo figura el hash SHA-256 de 64 hex, nunca el código en claro
        String hashEsperado = CodigoVinculoWhatsApp.hash(dto.codigo());
        String hashEnBase = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT codigo_hash FROM codigo_vinculo_whatsapp WHERE barbero_id=?", String.class, barberoA));
        assertThat(hashEnBase).isEqualTo(hashEsperado);
        assertThat(hashEnBase).matches("^[0-9a-f]{64}$");

        // Verificar que no se guardó el texto en claro en ninguna columna
        var columnasCodigo = ejecutor.ejecutar(a, () -> sql.queryForMap(
                "SELECT * FROM codigo_vinculo_whatsapp WHERE barbero_id=?", barberoA));
        assertThat(columnasCodigo.values().stream().map(String::valueOf).toList())
                .allSatisfy(valor -> assertThat(valor).doesNotContain(dto.codigo()));

        // 3. Vincular por WhatsApp con minúsculas y sin guión: «vincular xxxxxxxx»
        String comandoNormalizado = "vincular " + dto.codigo().toLowerCase().replace("-", "");
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", comandoNormalizado);

        var respuesta = ultimoMensaje(a, WA_BARBERO);
        assertThat(respuesta.texto()).contains("Listo, Profesional ficticio: este WhatsApp quedó vinculado a tu agenda en Barbería Norte");
        assertThat(respuesta.texto()).contains("Escribe «agenda hoy» o «agenda mañana»");

        // 4. Verificar que se creó el registro en vinculo_whatsapp_profesional y se marcó usado_en
        int vinculos = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE barbero_id=? AND wa_id=?",
                Integer.class, barberoA, WA_BARBERO));
        assertThat(vinculos).isEqualTo(1);

        Boolean usado = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT usado_en IS NOT NULL FROM codigo_vinculo_whatsapp WHERE codigo_hash=?",
                Boolean.class, hashEsperado));
        assertThat(usado).isTrue();
    }

    @Test
    void codigoReutilizado_noTieneEfecto_devuelveCodigoInvalido() {
        long usuarioId = barberoCuenta.getId();
        CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));

        // Primera vinculación exitosa
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + dto.codigo());
        assertThat(ultimoMensaje(a, WA_BARBERO).texto()).contains("Listo");

        // Segundo intento con el mismo código ya usado
        enviarTexto(NUMERO_A, "51999111222", "Otro Celular", "vincular " + dto.codigo());
        var respReutilizado = ultimoMensaje(a, "51999111222");
        assertThat(respReutilizado.texto()).isEqualTo("El código no es válido o caducó. Genera uno nuevo desde tu cuenta de BarberTurno.");

        // No se vinculó el nuevo número
        int vinculosNuevo = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE wa_id='51999111222'", Integer.class));
        assertThat(vinculosNuevo).isZero();
    }

    @Test
    void codigoCaducado_conRelojDePrueba_noTieneEfecto() {
        long usuarioId = barberoCuenta.getId();
        CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));

        // Avanzar el reloj 10 minutos y 1 segundo
        reloj.adelantar(Duration.ofSeconds(601));

        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + dto.codigo());
        var respuesta = ultimoMensaje(a, WA_BARBERO);
        assertThat(respuesta.texto()).isEqualTo("El código no es válido o caducó. Genera uno nuevo desde tu cuenta de BarberTurno.");

        int vinculos = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE wa_id=?", Integer.class, WA_BARBERO));
        assertThat(vinculos).isZero();
    }

    @Test
    void codigoDeOtroNegocio_noTieneEfectoEnEsteNegocio() {
        // Generar código en negocio B para barberoB
        long cuentaB = usuarioDeBarbero(b, barberoB);
        CodigoVinculoProfesionalDto dtoB = ejecutor.ejecutar(b, () -> vinculoService.generar(cuentaB));

        // Enviar ese código al número del negocio A
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + dtoB.codigo());

        var respuesta = ultimoMensaje(a, WA_BARBERO);
        assertThat(respuesta.texto()).isEqualTo("El código no es válido o caducó. Genera uno nuevo desde tu cuenta de BarberTurno.");

        int vinculosEnA = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE wa_id=?", Integer.class, WA_BARBERO));
        assertThat(vinculosEnA).isZero();
    }

    @Test
    void nuevoCodigo_invalidaElAnteriorSinUsar() {
        long usuarioId = barberoCuenta.getId();

        CodigoVinculoProfesionalDto codigo1 = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));
        CodigoVinculoProfesionalDto codigo2 = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));

        // Código 1 anterior quedó invalidado/eliminado
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + codigo1.codigo());
        assertThat(ultimoMensaje(a, WA_BARBERO).texto()).isEqualTo("El código no es válido o caducó. Genera uno nuevo desde tu cuenta de BarberTurno.");

        // Código 2 nuevo sí funciona
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + codigo2.codigo());
        assertThat(ultimoMensaje(a, WA_BARBERO).texto()).contains("Listo");
    }

    @Test
    void quintoFalloEnUnaHora_bloqueaIntentos() {
        String waFallos = "51999777666";

        // Primeros 4 fallos responden con código inválido
        for (int i = 1; i <= 4; i++) {
            enviarTexto(NUMERO_A, waFallos, "Fallo User", "vincular CODIGO" + i);
            assertThat(ultimoMensaje(a, waFallos).texto()).isEqualTo("El código no es válido o caducó. Genera uno nuevo desde tu cuenta de BarberTurno.");
        }

        // Quinto fallo responde con bloqueo
        enviarTexto(NUMERO_A, waFallos, "Fallo User", "vincular CODIGO5");
        assertThat(ultimoMensaje(a, waFallos).texto()).isEqualTo("Demasiados intentos. Vuelve a intentarlo más tarde.");

        // Intentos subsiguientes en la misma hora siguen bloqueados
        enviarTexto(NUMERO_A, waFallos, "Fallo User", "vincular CODIGO6");
        assertThat(ultimoMensaje(a, waFallos).texto()).isEqualTo("Demasiados intentos. Vuelve a intentarlo más tarde.");
    }

    @Test
    void cuentaSinPerfilOEnOtroNegocio_generarDevuelve404() {
        // Cuenta de usuario titular (no tiene perfil de barbero en negocio A)
        assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> vinculoService.generar(titular.getId())))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));

        // Cuenta de barberoB que pertenece al negocio B intentando generar en negocio A
        long cuentaB = usuarioDeBarbero(b, barberoB);
        assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> vinculoService.generar(cuentaB)))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test
    void getEstado_noExponeWaIdNiNumero() {
        long usuarioId = barberoCuenta.getId();

        // Antes de vincular
        EstadoVinculoProfesionalDto estadoInicial = ejecutor.ejecutar(a, () -> vinculoService.estado(usuarioId));
        assertThat(estadoInicial.vinculado()).isFalse();
        assertThat(estadoInicial.vinculadoEn()).isNull();

        // Vincular
        CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + dto.codigo());

        // Después de vincular
        EstadoVinculoProfesionalDto estadoVinculado = ejecutor.ejecutar(a, () -> vinculoService.estado(usuarioId));
        assertThat(estadoVinculado.vinculado()).isTrue();
        assertThat(estadoVinculado.vinculadoEn()).isNotNull();

        // El DTO de estado contiene exclusivamente vinculado y vinculadoEn
        assertThat(EstadoVinculoProfesionalDto.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("vinculado", "vinculadoEn");
    }

    @Test
    void deleteWeb_y_desvincularWhatsApp_quitanAcceso() {
        long usuarioId = barberoCuenta.getId();

        // 1. Vincular
        CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + dto.codigo());
        assertThat(ejecutor.ejecutar(a, () -> vinculoService.estado(usuarioId)).vinculado()).isTrue();

        // 2. Desvincular por la web (DELETE)
        ejecutor.ejecutar(a, () -> {
            vinculoService.desvincular(usuarioId);
            return null;
        });
        assertThat(ejecutor.ejecutar(a, () -> vinculoService.estado(usuarioId)).vinculado()).isFalse();

        int vinculosTrasDelete = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE barbero_id=?", Integer.class, barberoA));
        assertThat(vinculosTrasDelete).isZero();

        // 3. Volver a vincular
        CodigoVinculoProfesionalDto dto2 = ejecutor.ejecutar(a, () -> vinculoService.generar(usuarioId));
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "vincular " + dto2.codigo());
        assertThat(ejecutor.ejecutar(a, () -> vinculoService.estado(usuarioId)).vinculado()).isTrue();

        // 4. Desvincular por WhatsApp («desvincular»)
        enviarTexto(NUMERO_A, WA_BARBERO, "Carlos Barbero", "desvincular");
        var respuestaDesvincular = ultimoMensaje(a, WA_BARBERO);
        assertThat(respuestaDesvincular.texto()).isEqualTo("Este WhatsApp quedó desvinculado de tu agenda en esta barbería.");

        int vinculosTrasComando = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE wa_id=?", Integer.class, WA_BARBERO));
        assertThat(vinculosTrasComando).isZero();
    }
}
