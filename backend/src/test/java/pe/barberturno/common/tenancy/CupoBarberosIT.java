package pe.barberturno.common.tenancy;

import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import pe.barberturno.common.error.*;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.scheduling.dto.CrearBarberoDto;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1", "barberturno.admin.correo=",
        "barberturno.admin.password=", "barberturno.admin.nombre=", "barberturno.seguridad.cookie-secure=false"})
class CupoBarberosIT extends IdentidadPruebaBase {
    @RepeatedTest(20) void unaPlaza_altaYDosReactivacionesConcurrentes_exactamenteUnExito() throws Exception {
        for (int i=0; i<9; i++) perfil(1, "activo-" + i, true);
        var uno = perfil(1, "inactivo-1", false);
        var dos = perfil(1, "inactivo-2", false);
        var nueva = gestor(1, "alta");
        var salida = new CyclicBarrier(3);
        Set<Integer> conexiones = ConcurrentHashMap.newKeySet();
        try (var hilos = Executors.newFixedThreadPool(3)) {
            var resultados = List.of(
                    hilos.submit(() -> competir(salida, conexiones, () -> profesionales.crear(alta(nueva)))),
                    hilos.submit(() -> competir(salida, conexiones, () -> profesionales.cambiarEstado(uno.getId(), true))),
                    hilos.submit(() -> competir(salida, conexiones, () -> profesionales.cambiarEstado(dos.getId(), true))));
            var codigos = new ArrayList<String>();
            for (var resultado : resultados) codigos.add(resultado.get(20, TimeUnit.SECONDS));
            assertThat(codigos).containsExactlyInAnyOrder("OK", "LIMITE_BARBEROS_ACTIVOS", "LIMITE_BARBEROS_ACTIVOS");
        }
        assertThat(conexiones).hasSize(3);
        assertThat(enNegocio(1L, barberos::countByActivoTrue)).isEqualTo(10);
    }

    @Test void limitesDistintos_elNegocioLlenoNoConsumeElCupoDelOtro() throws Exception {
        long b = negocioB(); configurarCupo(b, 2);
        Barbero primero = null;
        for (int i=0; i<10; i++) primero = perfil(1, "a-" + i, true);
        var cuentaB1 = gestor(b, "b-1"); var cuentaB2 = gestor(b, "b-2");
        enNegocio(b, () -> profesionales.crear(alta(cuentaB1)));
        enNegocio(b, () -> profesionales.crear(alta(cuentaB2)));
        var cuentaB3 = gestor(b, "b-3"); var cuentaA = gestor(1, "a-alta");
        limite(() -> enNegocio(b, () -> profesionales.crear(alta(cuentaB3))));
        limite(() -> enNegocio(1L, () -> profesionales.crear(alta(cuentaA))));
        long id = primero.getId();
        enNegocio(1L, () -> {
            profesionales.cambiarEstado(id, false);
            assertThat(jdbc.queryForObject("select count(*) from pg_locks where locktype='advisory' and classid=141414 and objid=1 and objsubid=2 and granted", Integer.class)).isEqualTo(1);
            return null;
        });
        enNegocio(1L, () -> profesionales.crear(alta(cuentaA)));
        assertThat(enNegocio(b, barberos::countByActivoTrue)).isEqualTo(2);
        assertThat(enNegocio(1L, barberos::countByActivoTrue)).isEqualTo(10);
    }

    @Test void bloqueoDeDosClaves_altaEnBTerminaMientrasASigueBloqueado() throws Exception {
        long b = negocioB();
        var cuentaB = gestor(b, "otra-barberia");
        var tomado = new CountDownLatch(1); var liberar = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(() -> enNegocio(1L, () -> {
                barberos.bloquearLimiteActivos(1);
                tomado.countDown();
                try { assertThat(liberar.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            try {
                assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(sql.queryForObject("select count(*) from pg_locks where locktype='advisory' and classid=141414 and objid=1 and objsubid=2 and granted", Integer.class)).isEqualTo(1);
                var otra = hilos.submit(() -> enNegocio(b, () -> profesionales.crear(alta(cuentaB))));
                assertThat(otra.get(5, TimeUnit.SECONDS).activo()).isTrue();
                assertThat(a.isDone()).isFalse();
            } finally { liberar.countDown(); }
            a.get(10, TimeUnit.SECONDS);
        }
    }

    private String competir(CyclicBarrier salida, Set<Integer> conexiones, Supplier<?> accion) {
        try {
            enNegocio(1L, () -> {
                conexiones.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                try { salida.await(10, TimeUnit.SECONDS); }
                catch (Exception e) { throw new IllegalStateException(e); }
                return accion.get();
            });
            return "OK";
        } catch (NegocioException e) { return e.codigo().name(); }
    }
    private Usuario gestor(long negocio, String alias) {
        var u = persona(alias, Rol.ADMIN); pertenencia(negocio, u, RolNegocio.PROPIETARIO); return u;
    }
    private Barbero perfil(long negocio, String alias, boolean activo) {
        var u = gestor(negocio, alias);
        return enNegocio(negocio, () -> {
            var b = new Barbero(u, "Cortes", reloj.instant());
            if (!activo) b.desactivar(reloj.instant());
            return barberos.saveAndFlush(b);
        });
    }
    private static CrearBarberoDto alta(Usuario u) { return new CrearBarberoDto(u.getId(), null, null, null, "Cortes", null); }
    private static void limite(Runnable accion) { assertThatThrownBy(accion::run).isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.LIMITE_BARBEROS_ACTIVOS)); }
    private void configurarCupo(long b, int limite) throws Exception {
        assertThat(b).isNotEqualTo(1);
        try (var c = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"), entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"))) {
            assertThat(c.getCatalog()).isEqualTo("barberturno_v2_test");
            try (var s = c.prepareStatement("update negocio set limite_barberos_activos=? where id=?")) { s.setInt(1, limite); s.setLong(2, b); assertThat(s.executeUpdate()).isEqualTo(1); }
        }
    }
}
