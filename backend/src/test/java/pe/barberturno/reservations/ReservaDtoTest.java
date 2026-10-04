package pe.barberturno.reservations;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.reservations.dto.ReservaDto;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservaDtoTest {
    private final Instant inicio = Instant.parse("2026-10-01T15:00:00Z");
    private final ParametrosReserva parametros = new ParametrosReserva(Duration.ofHours(2), 30, 10, 3, false, 15);
    private Reserva reserva;

    @BeforeEach void preparar() {
        var cliente = mock(Usuario.class);
        when(cliente.getId()).thenReturn(1L);
        when(cliente.getNombre()).thenReturn("Cliente ficticio");
        when(cliente.getTelefono()).thenReturn("999000001");
        var usuarioBarbero = mock(Usuario.class);
        when(usuarioBarbero.getNombre()).thenReturn("Barbero ficticio");
        var barbero = mock(Barbero.class);
        when(barbero.getId()).thenReturn(2L);
        when(barbero.getUsuario()).thenReturn(usuarioBarbero);
        var servicio = mock(Servicio.class);
        when(servicio.getId()).thenReturn(3L);
        when(servicio.getNombre()).thenReturn("Corte ficticio");
        reserva = mock(Reserva.class);
        when(reserva.getId()).thenReturn(100L);
        when(reserva.getCliente()).thenReturn(cliente);
        when(reserva.getBarbero()).thenReturn(barbero);
        when(reserva.getServicio()).thenReturn(servicio);
        when(reserva.getInicio()).thenReturn(inicio);
        when(reserva.getFin()).thenReturn(inicio.plusSeconds(1800));
        when(reserva.getDuracionRefMin()).thenReturn((short) 30);
        when(reserva.getPrecioRef()).thenReturn(new BigDecimal("20.00"));
        when(reserva.getEstado()).thenReturn(EstadoReserva.CONFIRMADA);
    }

    @ParameterizedTest @CsvSource({
        "7201,true", "7200,true", "7199,false", "0,false", "-1,false"})
    void cliente_limiteExactoDeDosHoras(int anticipacion, boolean permitido) {
        var dto = ReservaDto.desde(reserva, actor(1, Rol.CLIENTE, null), inicio.minusSeconds(anticipacion), parametros);
        assertThat(dto.permisos().reprogramar()).isEqualTo(permitido);
        assertThat(dto.permisos().cancelar()).isEqualTo(permitido);
        assertThat(dto.permisos().transiciones()).isEmpty();
        assertThat(dto.cliente().telefono()).isNull();
    }

    @ParameterizedTest @EnumSource(EstadoReserva.class)
    void cliente_estadoYPropiedadRestrigenAcciones(EstadoReserva estado) {
        when(reserva.getEstado()).thenReturn(estado);
        var dto = ReservaDto.desde(reserva, actor(1, Rol.CLIENTE, null), inicio.minusSeconds(10000), parametros);
        assertThat(dto.permisos().reprogramar()).isEqualTo(estado == EstadoReserva.CONFIRMADA);
        assertThat(dto.permisos().cancelar()).isEqualTo(
                estado == EstadoReserva.CONFIRMADA || estado == EstadoReserva.PENDIENTE);
        var ajena = ReservaDto.desde(reserva, actor(99, Rol.CLIENTE, null), inicio.minusSeconds(10000), parametros);
        assertThat(ajena.permisos()).isEqualTo(new ReservaDto.PermisosDto(false, false, List.of()));
    }

    @ParameterizedTest @CsvSource({"1,true", "0,false", "-1,false"})
    void admin_capacidadAntesDelInicioExigeMotivoEnAccionPosterior(int anticipacion, boolean permitido) {
        var dto = ReservaDto.desde(reserva, actor(99, Rol.ADMIN, null), inicio.minusSeconds(anticipacion), parametros);
        assertThat(dto.permisos().reprogramar()).isEqualTo(permitido);
        assertThat(dto.permisos().cancelar()).isEqualTo(permitido);
        assertThat(dto.cliente().telefono()).isEqualTo("999000001");
    }

    @ParameterizedTest @CsvSource({
        "901,EN_ATENCION,false", "900,EN_ATENCION,true", "1,NO_ASISTIO,false", "0,NO_ASISTIO,true"})
    void barberoAsignado_ventanasDeTransicion(int anticipacion, EstadoReserva destino, boolean permitido) {
        var ahora = inicio.minusSeconds(anticipacion);
        var dto = ReservaDto.desde(reserva, actor(9, Rol.BARBERO, 2L), ahora, parametros);
        assertThat(dto.permisos().transiciones().contains(destino)).isEqualTo(permitido);
        assertThat(dto.permisos().reprogramar()).isFalse();
        assertThat(dto.permisos().cancelar()).isFalse();
        var ajena = ReservaDto.desde(reserva, actor(9, Rol.BARBERO, 99L), ahora, parametros);
        assertThat(ajena.permisos().transiciones()).isEmpty();
        var sinPerfil = ReservaDto.desde(reserva, actor(9, Rol.BARBERO, null), ahora, parametros);
        assertThat(sinPerfil.permisos().transiciones()).isEmpty();
    }

    @Test void pendiente_confirmaPersonalYExcluyeCanceladaDeTransiciones() {
        when(reserva.getEstado()).thenReturn(EstadoReserva.PENDIENTE);
        var dto = ReservaDto.desde(reserva, actor(9, Rol.BARBERO, 2L), inicio.minusSeconds(10000), parametros);
        assertThat(dto.permisos().transiciones()).containsExactly(EstadoReserva.CONFIRMADA);
        var admin = ReservaDto.desde(reserva, actor(9, Rol.ADMIN, null), inicio.minusSeconds(10000), parametros);
        assertThat(admin.permisos().cancelar()).isTrue();
        assertThat(admin.permisos().transiciones()).containsExactly(EstadoReserva.CONFIRMADA);
    }

    @Test void enAtencion_completarSinVentanaAdicional() {
        when(reserva.getEstado()).thenReturn(EstadoReserva.EN_ATENCION);
        var dto = ReservaDto.desde(reserva, actor(9, Rol.BARBERO, 2L), inicio, parametros);
        assertThat(dto.permisos().transiciones()).containsExactly(EstadoReserva.COMPLETADA);
        assertThat(dto.permisos().cancelar()).isFalse();
    }

    private UsuarioAutenticado actor(long id, Rol rol, Long perfil) {
        return new UsuarioAutenticado(id, rol, Optional.ofNullable(perfil), false);
    }
}
