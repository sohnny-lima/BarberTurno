package pe.barberturno.common.tenancy;

import java.sql.DriverManager;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import pe.barberturno.common.config.*;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.*;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.DisponibilidadService;
import pe.barberturno.users.Rol;
import static org.assertj.core.api.Assertions.*;

/** Reglas distintas A/B, exclusivamente sobre datos creados como aplicación y parámetros de B como propietario. */
class ParametrosNegocioIT extends TenancyPruebaBase {
    @Autowired ReglasNegocioService reglas;
    @Autowired ReservaService reservas;
    @Autowired ReservaConsultaService consultas;
    @Autowired DisponibilidadService disponibilidad;
    @Autowired org.springframework.context.ApplicationContext contexto;

    @Test void valoresIniciales_igualesAlYamlDeV1YSinBeanGlobal() throws Exception {
        var fuente = new YamlPropertySourceLoader().load("v1", new ClassPathResource("application.yml")).getFirst();
        var yaml = new Binder(ConfigurationPropertySources.from(fuente))
                .bind("barberturno.reservas", Bindable.of(ParametrosReserva.class)).get();
        assertThat(enNegocio(1L, reglas::reservas)).isEqualTo(yaml);
        assertThat(enNegocio(1L, reglas::limiteBarberosActivos)).isEqualTo(10);
        assertThat(contexto.getBeansOfType(ParametrosReserva.class)).isEmpty();
    }

    @Test void lecturasAlternadas_sinCacheYLosCambiosDeBSoloAfectanAB() throws Exception {
        long b = negocioConfigurado();
        var a = enNegocio(1L, reglas::reservas);
        assertThat(enNegocio(b, reglas::reservas)).isEqualTo(new ParametrosReserva(Duration.ofHours(4), 1, 10, 1, true, 60));
        assertThat(enNegocio(1L, reglas::reservas)).isEqualTo(a);
        configurar(b, "horizonte_dias=7");
        assertThat(enNegocio(b, reglas::reservas).horizonteDias()).isEqualTo(7);
        assertThat(enNegocio(1L, reglas::reservas)).isEqualTo(a);
    }

    @ParameterizedTest @CsvSource({"anticipacion_cambio_cliente_min,-1", "horizonte_dias,0", "limite_reservas_activas,0", "tolerancia_inicio_min,-1"})
    void valoresInvalidos_conservanBeanValidation(String columna, int valor) throws Exception {
        long b = negocioConfigurado();
        configurar(b, columna + "=" + valor);
        assertThatThrownBy(() -> enNegocio(b, reglas::reservas)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(enNegocio(1L, reglas::reservas).horizonteDias()).isEqualTo(30);
    }

    @Test void horizonte_aplicadoEnDisponibilidadCreacionYReprogramacion() throws Exception {
        long b = negocioConfigurado();
        var a = escenario(1, "horizonte-a"); var datosB = escenario(b, "horizonte-b");
        LocalDate dia = LocalDate.of(2026, 10, 1);
        assertThat(enNegocio(1L, () -> disponibilidad.consultarFranjas(a.servicio(), Optional.of(a.barbero()), dia, Optional.empty())).franjas()).isNotEmpty();
        assertThat(enNegocio(b, () -> disponibilidad.consultarFranjas(datosB.servicio(), Optional.of(datosB.barbero()), dia, Optional.empty())).franjas()).isEmpty();
        codigo(() -> crear(b, datosB, dia, "12:00"), ErrorCodigo.FUERA_DE_HORIZONTE);
        var citaA = crear(1, a, dia, "12:00");
        var citaB = crear(b, datosB, dia.minusDays(3), "12:00");
        enNegocio(b, () -> reservas.transicionar(citaB.id(), new TransicionarReservaDto(EstadoReserva.CONFIRMADA, 0), admin(datosB)));
        codigo(() -> enNegocio(b, () -> reservas.reprogramar(citaB.id(), new ReprogramarReservaDto(inicio(dia, "13:00"), null, 1, "Cambio de prueba"), admin(datosB))), ErrorCodigo.FUERA_DE_HORIZONTE);
        var movida = enNegocio(1L, () -> reservas.reprogramar(citaA.id(), new ReprogramarReservaDto(inicio(dia.plusDays(1), "13:00"), null, 0, null), cliente(a)));
        assertThat(movida.inicio()).isEqualTo(inicio(dia.plusDays(1), "13:00"));
    }

    @Test void confirmacionYMaximoReservas_sonLocalesIncluidasLasPendientes() throws Exception {
        long b = negocioConfigurado();
        var a = escenario(1, "maximo-a"); var datosB = escenario(b, "maximo-b");
        LocalDate dia = LocalDate.of(2026, 9, 29);
        assertThat(crear(1, a, dia, "10:00").estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        var pendiente = crear(b, datosB, dia, "10:00");
        assertThat(pendiente.estado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(pendiente.permisos().cancelar()).isTrue();
        assertThat(pendiente.permisos().reprogramar()).isFalse();
        codigo(() -> crear(b, datosB, dia, "11:00"), ErrorCodigo.LIMITE_RESERVAS_ACTIVAS);
        crear(1, a, dia, "11:00"); crear(1, a, dia, "12:00");
        codigo(() -> crear(1, a, dia, "13:00"), ErrorCodigo.LIMITE_RESERVAS_ACTIVAS);
        assertThat(enNegocio(b, () -> jdbc.queryForObject("select count(*) from reserva", Integer.class))).isEqualTo(1);
    }

    @Test void anticipacion_consistenteEnPermisosConsultaCancelacionYReprogramacion() throws Exception {
        long b = negocioConfigurado();
        var a = escenario(1, "anticipacion-a"); var datosB = escenario(b, "anticipacion-b");
        LocalDate hoy = LocalDate.of(2026, 9, 28);
        var citaA = crear(1, a, hoy, "12:00"); var pendiente = crear(b, datosB, hoy, "12:00");
        var citaB = enNegocio(b, () -> reservas.transicionar(pendiente.id(), new TransicionarReservaDto(EstadoReserva.CONFIRMADA, 0), admin(datosB)));
        assertThat(citaA.permisos().cancelar()).isTrue();
        var detalle = enNegocio(b, () -> consultas.detalle(cliente(datosB), citaB.id()));
        assertThat(detalle.permisos().cancelar()).isFalse();
        assertThat(detalle.permisos().reprogramar()).isFalse();
        assertThat(enNegocio(b, () -> consultas.mias(cliente(datosB), null, null, null, 0, 20)).contenido().getFirst().permisos()).isEqualTo(detalle.permisos());
        codigo(() -> enNegocio(b, () -> reservas.cancelar(citaB.id(), new CancelarReservaDto(1, null), cliente(datosB))), ErrorCodigo.FUERA_DE_POLITICA);
        codigo(() -> enNegocio(b, () -> reservas.reprogramar(citaB.id(), new ReprogramarReservaDto(inicio(hoy, "13:00"), null, 1, null), cliente(datosB))), ErrorCodigo.FUERA_DE_POLITICA);
        var movida = enNegocio(1L, () -> reservas.reprogramar(citaA.id(), new ReprogramarReservaDto(inicio(hoy, "13:00"), null, 0, null), cliente(a)));
        assertThat(enNegocio(1L, () -> reservas.cancelar(movida.id(), new CancelarReservaDto(1, null), cliente(a))).estado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test void tolerancia_inicioYPermisosDeAgendaUsanElNegocioActual() throws Exception {
        long b = negocioConfigurado();
        var a = escenario(1, "tolerancia-a"); var datosB = escenario(b, "tolerancia-b");
        LocalDate hoy = LocalDate.of(2026, 9, 28);
        var citaA = crear(1, a, hoy, "10:00"); var pendiente = crear(b, datosB, hoy, "10:00");
        enNegocio(b, () -> reservas.transicionar(pendiente.id(), new TransicionarReservaDto(EstadoReserva.CONFIRMADA, 0), admin(datosB)));
        assertThat(enNegocio(1L, () -> consultas.detalle(admin(a), citaA.id())).permisos().transiciones()).doesNotContain(EstadoReserva.EN_ATENCION);
        assertThat(enNegocio(b, () -> consultas.listar(admin(datosB), hoy, hoy, null, null, null, null, 0, 20)).contenido().getFirst().permisos().transiciones()).contains(EstadoReserva.EN_ATENCION);
        codigo(() -> enNegocio(1L, () -> reservas.transicionar(citaA.id(), new TransicionarReservaDto(EstadoReserva.EN_ATENCION, 0), admin(a))), ErrorCodigo.FUERA_DE_VENTANA);
        assertThat(enNegocio(b, () -> reservas.transicionar(pendiente.id(), new TransicionarReservaDto(EstadoReserva.EN_ATENCION, 1), admin(datosB))).estado()).isEqualTo(EstadoReserva.EN_ATENCION);
    }

    private DatosNegocioPrueba escenario(long negocio, String alias) {
        return enNegocio(negocio, () -> {
            var d = DatosNegocioPrueba.crear(jdbc, alias);
            jdbc.update("delete from notificacion"); jdbc.update("delete from auditoria_reserva"); jdbc.update("delete from reserva");
            jdbc.update("delete from jornada");
            for (int dia=1; dia<=7; dia++) jdbc.update("insert into jornada (barbero_id,dia_semana,hora_inicio,hora_fin) values (?,?,'09:00','18:00')", d.barbero(), dia);
            return d;
        });
    }
    private long negocioConfigurado() throws Exception {
        long b = negocioB();
        configurar(b, "anticipacion_cambio_cliente_min=240,horizonte_dias=1,limite_reservas_activas=1,confirmacion_manual=true,tolerancia_inicio_min=60");
        return b;
    }
    private void configurar(long b, String asignacion) throws Exception {
        assertThat(b).isNotEqualTo(1);
        try (var c = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"), entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"))) {
            assertThat(c.getCatalog()).isEqualTo("barberturno_v2_test");
            try (var s = c.prepareStatement("update negocio set " + asignacion + " where id=?")) { s.setLong(1, b); assertThat(s.executeUpdate()).isEqualTo(1); }
        }
    }
    private ReservaDto crear(long negocio, DatosNegocioPrueba datos, LocalDate dia, String hora) {
        return enNegocio(negocio, () -> reservas.crear(new CrearReservaDto(datos.servicio(), datos.barbero(), inicio(dia, hora), null), cliente(datos)));
    }
    private static OffsetDateTime inicio(LocalDate dia, String hora) { return dia.atTime(LocalTime.parse(hora)).atZone(TiempoNegocio.ZONA).toOffsetDateTime(); }
    private static UsuarioAutenticado cliente(DatosNegocioPrueba d) { return new UsuarioAutenticado(d.cliente(), Rol.CLIENTE, Optional.empty(), false); }
    private static UsuarioAutenticado admin(DatosNegocioPrueba d) { return new UsuarioAutenticado(d.admin(), Rol.ADMIN, Optional.of(d.barbero()), false); }
    private static void codigo(Runnable accion, ErrorCodigo codigo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(codigo));
    }
}
