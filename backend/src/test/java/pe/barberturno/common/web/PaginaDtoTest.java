package pe.barberturno.common.web;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginaDtoTest {
    @Test
    void desde_paginaIntermedia_conservaContenidoYMetadatos() {
        PaginaDto<String> dto = PaginaDto.desde(new PageImpl<>(List.of("uno", "dos"), PageRequest.of(2, 2), 9));
        assertThat(dto.contenido()).containsExactly("uno", "dos");
        assertThat(dto.pagina()).isEqualTo(2);
        assertThat(dto.tamano()).isEqualTo(2);
        assertThat(dto.totalElementos()).isEqualTo(9);
        assertThat(dto.totalPaginas()).isEqualTo(5);
    }

    @Test
    void desde_paginaVacia_devuelveCeroTotales() {
        PaginaDto<String> dto = PaginaDto.desde(Page.empty(PageRequest.of(0, 20)));
        assertThat(dto.contenido()).isEmpty();
        assertThat(dto.pagina()).isZero();
        assertThat(dto.tamano()).isEqualTo(20);
        assertThat(dto.totalElementos()).isZero();
        assertThat(dto.totalPaginas()).isZero();
    }

    @Test
    void desde_tamanoCien_aceptaLimite() {
        assertThat(PaginaDto.desde(Page.empty(PageRequest.of(0, 100))).tamano()).isEqualTo(100);
    }

    @Test
    void desde_tamanoMayorACien_rechazaConValidacion() {
        assertThatThrownBy(() -> PaginaDto.desde(Page.empty(PageRequest.of(0, 101))))
                .isInstanceOfSatisfying(NegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101})
    void construir_tamanoInvalido_rechaza(int tamano) {
        assertThatThrownBy(() -> new PaginaDto<>(List.of(), 0, tamano, 0, 0))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void construir_paginaNegativa_rechaza() {
        assertThatThrownBy(() -> new PaginaDto<>(List.of(), -1, 20, 0, 0)).isInstanceOf(NegocioException.class);
    }

    @Test
    void construir_contenidoMutable_copiaYProtegeLista() {
        List<String> lista = new ArrayList<>(List.of("uno"));
        PaginaDto<String> dto = new PaginaDto<>(lista, 0, 20, 1, 1);
        lista.clear();
        assertThat(dto.contenido()).containsExactly("uno");
        assertThatThrownBy(() -> dto.contenido().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
