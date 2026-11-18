package pe.barberturno.platform;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class MembresiaTest {
    private static final Instant INICIO = Instant.parse("2026-10-24T15:00:00Z");

    @ParameterizedTest @EnumSource(MotivoRevocacion.class)
    void perfil_soloRestableceSuPropiaBaja(MotivoRevocacion motivo) {
        var m = new Membresia(1, 2, RolNegocio.BARBERO, INICIO);
        m.revocar(motivo, INICIO);
        m.restablecerTrasReactivarPerfil(INICIO.plusSeconds(1));
        assertThat(m.isActiva()).isEqualTo(motivo == MotivoRevocacion.PERFIL_DESACTIVADO);
        assertThat(m.getMotivoRevocacion()).isEqualTo(m.isActiva() ? null : motivo);
        assertThat(m.getRol()).isEqualTo(RolNegocio.BARBERO);
    }

    @ParameterizedTest @EnumSource(MotivoRevocacion.class)
    void invitacion_restauraCualquierMotivoConElRolAcreditado(MotivoRevocacion motivo) {
        var m = new Membresia(1, 2, RolNegocio.BARBERO, INICIO);
        m.revocar(motivo, INICIO);
        m.restablecerPorInvitacion(RolNegocio.ENCARGADO, INICIO.plusSeconds(1));
        assertThat(m.isActiva()).isTrue();
        assertThat(m.getMotivoRevocacion()).isNull();
        assertThat(m.getRol()).isEqualTo(RolNegocio.ENCARGADO);
        assertThat(m.getNegocioId()).isEqualTo(1);
        assertThat(m.getUsuarioId()).isEqualTo(2);
    }

    @Test void operacionesInvalidas_noCambianLaPertenencia() {
        var m = new Membresia(1, 2, RolNegocio.BARBERO, INICIO);
        assertThatThrownBy(() -> m.revocar(null, INICIO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> m.revocar(MotivoRevocacion.SALIDA, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> m.restablecerPorInvitacion(RolNegocio.PROPIETARIO, INICIO))
                .isInstanceOf(IllegalStateException.class);
        m.restablecerTrasReactivarPerfil(INICIO);
        assertThat(m.isActiva()).isTrue();
        assertThat(m.getRol()).isEqualTo(RolNegocio.BARBERO);
        m.revocar(MotivoRevocacion.SALIDA, INICIO);
        assertThatThrownBy(() -> m.restablecerPorInvitacion(null, INICIO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> m.restablecerPorInvitacion(RolNegocio.ENCARGADO, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> m.restablecerTrasReactivarPerfil(null)).isInstanceOf(NullPointerException.class);
        m.acreditarPropietarioInicial(INICIO.plusSeconds(1));
        assertThat(m.isActiva()).isFalse();
        assertThat(m.getRol()).isEqualTo(RolNegocio.BARBERO);
        assertThat(m.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.SALIDA);
    }
}
