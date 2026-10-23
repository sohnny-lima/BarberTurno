package pe.barberturno.common.tenancy;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import pe.barberturno.common.error.*;
import pe.barberturno.platform.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1", "barberturno.admin.correo=",
        "barberturno.admin.password=", "barberturno.admin.nombre=", "barberturno.seguridad.cookie-secure=false"})
class UltimoPropietarioIT extends IdentidadPruebaBase {
    @RepeatedTest(20) void dosPropietariosDesactivacionCruzada_exactamenteUnExitoYQuedaUnoActivo() throws Exception {
        var uno = propietario("primero");
        var dos = propietario("segundo");
        var cookieUno = sesion(uno);
        var cookieDos = sesion(dos);
        var csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var salida = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var primero = hilos.submit(() -> {
                salida.await(10, TimeUnit.SECONDS);
                return mvc.perform(estado(dos.getId(), false).cookie(cookieUno, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())).andReturn().getResponse().getStatus();
            });
            var segundo = hilos.submit(() -> {
                salida.await(10, TimeUnit.SECONDS);
                return mvc.perform(estado(uno.getId(), false).cookie(cookieDos, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())).andReturn().getResponse().getStatus();
            });
            assertThat(List.of(primero.get(20, TimeUnit.SECONDS), segundo.get(20, TimeUnit.SECONDS)))
                    .satisfies(r -> assertThat(r.stream().filter(c -> c == 200)).hasSize(1))
                    .allMatch(c -> c == 200 || c == 401 || c == 403 || c == 409);
        }
        assertThat(membresias.contarPropietariosActivos(1L)).isEqualTo(1);
    }

    @Test void ultimoPropietario_noLoSustituyenEncargadosNiPropietariosRevocadosODeOtroNegocio() throws Exception {
        long b = negocioB();
        var titular = propietario("unico");
        var encargado = persona("gestor", Rol.ADMIN);
        pertenencia(1L, encargado, RolNegocio.ENCARGADO);
        var otro = persona("revocado", Rol.ADMIN);
        var revocada = pertenencia(1L, otro, RolNegocio.PROPIETARIO);
        revocada.revocar(reloj.instant()); membresias.save(revocada);
        var inactivo = propietario("inactivo");
        inactivo.desactivar(reloj.instant()); usuarios.saveAndFlush(inactivo);
        pertenencia(b, persona("titular-b", Rol.ADMIN), RolNegocio.PROPIETARIO);
        assertThat(membresias.contarPropietariosActivos(1L)).isEqualTo(1);
        mvc.perform(csrf(estado(titular.getId(), false), sesion(titular))).andExpect(status().isConflict());
        // Un actor administrativo ya revalidado no puede eludir el conteo usando un rol legado.
        var actor = auth.autenticar(titular.getId(), 0).orElseThrow();
        assertThatThrownBy(() -> gestion.cambiarEstado(titular.getId(), false, actor)).isInstanceOf(NegocioException.class);
        assertThat(usuarios.findById(titular.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test void bloqueoEsDeDosClavesYNoSerializaOtroNegocio() throws Exception {
        long b = negocioB();
        var tomado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var primerNegocio = hilos.submit(() -> enNegocio(1L, () -> {
                membresias.bloquearPropietarios(1L);
                tomado.countDown();
                try { assertThat(liberar.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                return null;
            }));
            try {
                assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(sql.queryForObject("select count(*) from pg_locks where locktype='advisory' and classid=313131 and objid=1 and objsubid=2 and granted", Integer.class)).isEqualTo(1);
                var otroNegocio = hilos.submit(() -> enNegocio(b, () -> membresias.bloquearPropietarios(b)));
                assertThat(otroNegocio.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            } finally { liberar.countDown(); }
            primerNegocio.get(10, TimeUnit.SECONDS);
        }
    }
}
