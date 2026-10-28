package pe.barberturno.whatsapp;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.platform.RolPlataforma;
import pe.barberturno.users.Rol;
import pe.barberturno.whatsapp.dto.RegistrarCanalWhatsAppDto;
import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas de integración del servicio de canal de WhatsApp {@link CanalWhatsAppService} (CI-10, hallazgo 111-8).
 * Valida la restricción que impide sustituir el {@code phoneNumberId} de un canal cuando el negocio ya cuenta
 * con conversaciones, protegiendo la correlación de los avisos de estado entrantes, y verifica que la
 * sustitución sí se admita cuando el negocio no tiene conversaciones registradas.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class CanalWhatsAppIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private CanalWhatsAppService canalService;

    private final UsuarioAutenticado adminPlataforma = new UsuarioAutenticado(
            1L, Rol.ADMIN, Optional.empty(), false, RolPlataforma.ADMIN_PLATAFORMA, Optional.empty());

    @Test
    void sustituirNumero_conConversacionesExistentes_lanzaConflicto() {
        crearConversacion(a, WA_CLIENTE);

        var dto = new RegistrarCanalWhatsAppDto("20001", "990001", "+51999990001");

        assertThatThrownBy(() -> canalService.registrar(a, dto, adminPlataforma))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    NegocioException ne = (NegocioException) ex;
                    assertThat(ne.codigo()).isEqualTo(ErrorCodigo.CONFLICTO);
                    assertThat(ne.getMessage())
                            .isEqualTo("El número del canal no se puede sustituir porque el negocio ya tiene conversaciones.");
                });

        var canalActual = canales.findByNegocioId(a).orElseThrow();
        assertThat(canalActual.phoneNumberId()).isEqualTo(NUMERO_A);
    }

    @Test
    void sustituirNumero_sinConversaciones_actualizaCanalCorrectamente() {
        // El negocio B no tiene conversaciones creadas
        var dto = new RegistrarCanalWhatsAppDto("20002", "990002", "+51999990002");

        var resultado = canalService.registrar(b, dto, adminPlataforma);

        assertThat(resultado.phoneNumberId()).isEqualTo("990002");
        assertThat(resultado.numeroVisible()).isEqualTo("+51999990002");

        var canalEnBase = canales.findByNegocioId(b).orElseThrow();
        assertThat(canalEnBase.phoneNumberId()).isEqualTo("990002");
    }

    @Test
    void mantenerMismoNumero_conConversaciones_actualizaDatosSinConflicto() {
        crearConversacion(a, WA_CLIENTE);

        // Se mantiene NUMERO_A pero se actualiza wabaId y numeroVisible
        var dto = new RegistrarCanalWhatsAppDto("20001-actualizado", NUMERO_A, "+51999000999");

        var resultado = canalService.registrar(a, dto, adminPlataforma);

        assertThat(resultado.wabaId()).isEqualTo("20001-actualizado");
        assertThat(resultado.phoneNumberId()).isEqualTo(NUMERO_A);
        assertThat(resultado.numeroVisible()).isEqualTo("+51999000999");
    }

    @Test
    void registrar_sinPermisoPlataforma_lanzaProhibido() {
        var sinPermiso = new UsuarioAutenticado(
                titular.getId(), Rol.ADMIN, Optional.empty(), false, RolPlataforma.NINGUNO, Optional.empty());
        var dto = new RegistrarCanalWhatsAppDto("20001", NUMERO_A, "+51999000001");

        assertThatThrownBy(() -> canalService.registrar(a, dto, sinPermiso))
                .isInstanceOf(NegocioException.class)
                .extracting(e -> ((NegocioException) e).codigo())
                .isEqualTo(ErrorCodigo.PROHIBIDO);
    }
}
