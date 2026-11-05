package pe.barberturno.conversation.ia.evaluacion;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import pe.barberturno.conversation.ia.*;
import static org.assertj.core.api.Assertions.*;

class EvaluacionEnVivoTest {
    @Test void sinClave_seNiegaAntesDeSpring() {
        assertThatThrownBy(() -> EvaluacionEnVivo.verificarPrerrequisitos(null, "1.00")).isInstanceOf(IllegalStateException.class).hasMessageContaining("BT_IA_API_KEY");
        assertThatThrownBy(() -> EvaluacionEnVivo.exigirClave(" ")).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings = {
            "jdbc:postgresql://localhost:5433/barberturno_v2_test_respaldo",
            "jdbc:postgresql://localhost:5433/barberturno_v2",
            "jdbc:postgresql://externo:5433/barberturno_v2_test",
            "jdbc:postgresql://localhost:5432/barberturno_v2_test",
            "jdbc:postgresql://localhost:5433/barberturno_v2_test?service=otra",
            "barberturno_v2_test"})
    void destinoNoExacto_seRechaza(String url) { assertThatThrownBy(() -> SeguridadEvaluacion.validarUrl(url)).isInstanceOf(IllegalStateException.class); }

    @Test void destinoExacto_seAdmite() { SeguridadEvaluacion.validarUrl("jdbc:postgresql://localhost:5433/barberturno_v2_test"); }

    @Test void argumentoDatasourceAOtraBase_prevaleceYSeRechaza() {
        var env = entorno();
        env.getPropertySources().addFirst(new SimpleCommandLinePropertySource("--spring.datasource.url=jdbc:postgresql://localhost:5433/barberturno_v2"));
        assertThatThrownBy(() -> EvaluacionEnVivo.configurarEntorno(env, "claude-haiku-5-5", "low", "clave-ficticia"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Destino rechazado");
    }

    @Test void destinoFlywayDistinto_seRechazaAntesDeMigrar() {
        var env = entorno().withProperty("spring.flyway.url", "jdbc:postgresql://localhost:5433/barberturno_v2");
        assertThatThrownBy(() -> SeguridadEvaluacion.validarDestino(env)).isInstanceOf(IllegalStateException.class);
    }

    @Test void sinDestinoExplicito_noSuponeBaseTest() {
        assertThatThrownBy(() -> SeguridadEvaluacion.validarDestino(new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }

    @Test void rolPropietario_seRechaza() {
        assertThatThrownBy(() -> SeguridadEvaluacion.validarDestino(entorno().withProperty("spring.datasource.username", "barberturno_v2")))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.001", "1.234", "10.01", "abc", ""})
    void topeInvalido_seRechazaSinRedondear(String tope) {
        assertThatThrownBy(() -> SeguridadEvaluacion.validarTope(tope)).isInstanceOf(IllegalStateException.class);
    }

    @Test void configuracionEnsayo_superaYamlArgumentosYVariables() {
        var env = entorno().withProperty("barberturno.ia.modelo", "claude-opus-5-5").withProperty("barberturno.ia.habilitada", "false");
        env.getPropertySources().addFirst(new SimpleCommandLinePropertySource("--barberturno.ia.modelo=claude-sonnet-5-5", "--barberturno.ia.esfuerzo=high"));
        EvaluacionEnVivo.configurarEntorno(env, "claude-haiku-5-5", "low", "clave-ficticia");
        var p = Binder.get(env).bind("barberturno.ia", IaPropiedades.class).orElseThrow(IllegalStateException::new);
        SeguridadEvaluacion.validarModelo(p, "claude-haiku-5-5", "low");
        assertThat(p.modelo()).isEqualTo("claude-haiku-5-5");
        assertThat(p.habilitada()).isTrue(); assertThat(p.esfuerzo()).isEqualTo("low");
        assertThatThrownBy(() -> SeguridadEvaluacion.validarModelo(p, "claude-opus-5-5", "low"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("efectivo");
    }

    @Test void saldoGlobal_noSeReiniciaEntreModelosYReservaAntesDeLlamar() {
        var saldo = new SeguridadEvaluacion.Saldo(new BigDecimal("0.01"));
        var p = propiedades();
        var peticion = new PeticionModelo("sistema", "contexto", List.of(new MensajeModelo.DelCliente("hola")), List.of());
        long coste = SeguridadEvaluacion.costeMaximo(peticion, p);
        int llamadas = 0;
        while (saldo.reservado() + coste <= 10_000) { saldo.reservar(peticion, p); llamadas++; }
        assertThat(llamadas).isPositive();
        long antes = saldo.reservado();
        assertThatThrownBy(() -> saldo.reservar(peticion, p)).isInstanceOf(IllegalStateException.class).hasMessageContaining("global");
        assertThat(saldo.reservado()).isEqualTo(antes).isLessThanOrEqualTo(10_000);
    }

    @Test void monitor_reservaAntesDeDelegar_yCompruebaModeloEfectivo() {
        var p = propiedades(); var saldo = new SeguridadEvaluacion.Saldo(new BigDecimal("0.01"));
        var factory = new StaticListableBeanFactory(Map.of("propiedades", p));
        var monitor = new EvaluacionEnVivo.MonitorModelo(factory.getBeanProvider(IaPropiedades.class), saldo);
        // Antes de fijar modelo y esfuerzo, ni siquiera el falso puede recibir una petición.
        var falso = new ModeloConversacionalFalso();
        ModeloConversacional modelo = (ModeloConversacional) monitor.postProcessAfterInitialization(falso, "falso");
        assertThatThrownBy(() -> modelo.responder(new PeticionModelo("", "", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(falso.cantidadPeticiones()).isZero();
    }

    @Test void monitor_conConfiguracionCorrecta_reservaAntesDeCadaRespuesta() {
        var p = propiedades(); var saldo = new SeguridadEvaluacion.Saldo(new BigDecimal("0.01"));
        var factory = new StaticListableBeanFactory(Map.of("propiedades", p));
        var monitor = new EvaluacionEnVivo.MonitorModelo(factory.getBeanProvider(IaPropiedades.class), saldo);
        monitor.configurar(p.modelo(), p.esfuerzo());
        var falso = new ModeloConversacionalFalso();
        falso.programarRespuesta(new RespuestaModelo(Optional.of("Hola"), List.of(), ContinuacionProveedor.VACIA,
                new UsoModelo(p.modelo(),10,5,0,0,1), "end_turn"));
        falso.fijarGanchoAntes(() -> assertThat(saldo.reservado()).isPositive());
        var modelo = (ModeloConversacional) monitor.postProcessAfterInitialization(falso,"falso");
        modelo.responder(new PeticionModelo("", "", List.of(), List.of()));
        assertThat(falso.cantidadPeticiones()).isEqualTo(1);
        assertThat(monitor.cantidad()).isEqualTo(1);
    }

    /** g100-5: si el proveedor responde con otro modelo, la respuesta no se acepta. */
    @Test void monitor_respuestaDeOtroModelo_seRechaza() {
        var p = propiedades(); var saldo = new SeguridadEvaluacion.Saldo(new BigDecimal("0.01"));
        var factory = new StaticListableBeanFactory(Map.of("propiedades", p));
        var monitor = new EvaluacionEnVivo.MonitorModelo(factory.getBeanProvider(IaPropiedades.class), saldo);
        monitor.configurar(p.modelo(), p.esfuerzo());
        var falso = new ModeloConversacionalFalso();
        falso.programarRespuesta(new RespuestaModelo(Optional.of("Hola"), List.of(), ContinuacionProveedor.VACIA,
                new UsoModelo(p.modelo() + "-otro", 10, 5, 0, 0, 1), "end_turn"));
        var modelo = (ModeloConversacional) monitor.postProcessAfterInitialization(falso, "falso");
        assertThatThrownBy(() -> modelo.responder(new PeticionModelo("", "", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("modelo distinto");
    }

    @Test void monitor_canarioEnResultado_noLlegaAlProveedor() {
        var p = propiedades(); var saldo = new SeguridadEvaluacion.Saldo(new BigDecimal("0.01"));
        var factory = new StaticListableBeanFactory(Map.of("propiedades", p));
        var monitor = new EvaluacionEnVivo.MonitorModelo(factory.getBeanProvider(IaPropiedades.class), saldo);
        monitor.configurar(p.modelo(),p.esfuerzo()); var falso = new ModeloConversacionalFalso();
        var modelo = (ModeloConversacional) monitor.postProcessAfterInitialization(falso,"falso");
        var peticion = new PeticionModelo("", "", List.of(new MensajeModelo.ResultadosHerramientas(
                List.of(new ResultadoHerramienta("call", "Profesional Zafiro B", false)))),List.of());
        assertThatThrownBy(() -> modelo.responder(peticion)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Canario");
        assertThat(falso.cantidadPeticiones()).isZero(); assertThat(saldo.reservado()).isZero();
        assertThat(monitor.desde(0)).anyMatch(SeguridadEvaluacion::contieneCanario);
    }

    @Test void saldoCompartido_concurrente_noSuperaTope() throws Exception {
        var saldo = new SeguridadEvaluacion.Saldo(new BigDecimal("0.01"));
        var peticion = new PeticionModelo("", "", List.of(), List.of()); var p = propiedades();
        try (var hilos = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tareas = java.util.stream.IntStream.range(0,100).<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () -> {
                try { saldo.reservar(peticion,p); return true; } catch (IllegalStateException agotado) { return false; }
            }).toList();
            int aprobadas = 0;
            for (var resultado : hilos.invokeAll(tareas)) if (resultado.get()) aprobadas++;
            assertThat(saldo.reservado()).isEqualTo(aprobadas * SeguridadEvaluacion.costeMaximo(peticion,p)).isLessThanOrEqualTo(10_000);
        }
    }

    @Test void costeMaximo_incluyeMargenCacheMasCaraYContinuacionConRedondeoSuperior() {
        var p = propiedades();
        assertThat(SeguridadEvaluacion.costeMaximo(new PeticionModelo("", "", List.of(), List.of()), p)).isEqualTo(2048);
        assertThat(SeguridadEvaluacion.costeMaximo(new PeticionModelo("á", "", List.of(), List.of()), p)).isEqualTo(2049);
        var anterior = new MensajeModelo.DelAsistente(Optional.of("x"), List.of(), ContinuacionProveedor.VACIA);
        assertThat(SeguridadEvaluacion.costeMaximo(new PeticionModelo("", "", List.of(anterior), List.of()), p)).isEqualTo(10_241);
    }

    @Test void escaneoReal_excluyeComponentesYConfiguracionesDePrueba() throws Exception {
        var filtro = new EvaluacionEnVivo.ExcluirPruebas();
        var fabrica = new org.springframework.core.type.classreading.CachingMetadataReaderFactory();
        assertThat(filtro.match(fabrica.getMetadataReader("pe.barberturno.BarberTurnoApplication"), fabrica)).isFalse();
        assertThat(filtro.match(fabrica.getMetadataReader("pe.barberturno.conversation.ia.ConfiguracionPruebasIA"), fabrica)).isTrue();
        assertThat(filtro.match(fabrica.getMetadataReader("pe.barberturno.common.tenancy.ProveedorFixturesLegados"), fabrica)).isTrue();
    }

    @Test void monitorReal_rechazaAdaptadorFalsoAntesDeLlamar() {
        var factory = new StaticListableBeanFactory(Map.of("propiedades", propiedades()));
        var monitor = new EvaluacionEnVivo.MonitorModelo(factory.getBeanProvider(IaPropiedades.class), new SeguridadEvaluacion.Saldo(new BigDecimal("1.00")), true);
        assertThatThrownBy(() -> monitor.postProcessAfterInitialization(new ModeloConversacionalFalso(),"modelo"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ModeloAnthropic");
    }

    @Test void conjuntoBorrador_yFixturesExplicitosConCitasPrevias_cargan120() {
        var todos = EvaluacionEnVivo.cargarConjuntoCasos("TODAS");
        var fixtures = FixturesEvaluacion.cargar();
        assertThat(todos).hasSize(120);
        for (var caso : todos) fixtures.exigir(caso);
        var cancelacion = todos.stream().filter(c -> c.id().equals("CAN-09")).findFirst().orElseThrow();
        assertThat(fixtures.exigir(cancelacion).citasPrevias()).hasSize(1);
        assertThat(EvaluacionEnVivo.cargarConjuntoCasos("PRUEBA")).hasSize(24);
        assertThat(EvaluacionEnVivo.cargarConjuntoCasos("AJUSTE")).hasSize(96);
    }

    static MockEnvironment entorno() {
        return new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5433/barberturno_v2_test")
                .withProperty("spring.datasource.username", "barberturno_v2_app")
                .withProperty("barberturno.ia.precios.claude-haiku-5-5.entrada", "0.10")
                .withProperty("barberturno.ia.precios.claude-haiku-5-5.salida", "0.50")
                .withProperty("barberturno.ia.precios.claude-haiku-5-5.cache-lectura", "0.01")
                .withProperty("barberturno.ia.precios.claude-haiku-5-5.cache-escritura", "0.125");
    }

    static IaPropiedades propiedades() {
        var env = entorno(); EvaluacionEnVivo.configurarEntorno(env, "claude-haiku-5-5", "low", "clave-ficticia");
        return Binder.get(env).bind("barberturno.ia", IaPropiedades.class).orElseThrow(IllegalStateException::new);
    }
}
