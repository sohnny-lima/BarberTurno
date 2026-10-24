package pe.barberturno.common.tenancy;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.barberturno.catalog.ServicioService;
import pe.barberturno.catalog.dto.GuardarServicioDto;
import pe.barberturno.common.error.*;
import pe.barberturno.platform.RolNegocio;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContextoPeticionIT extends PeticionNegocioPruebaBase {
    @MockitoSpyBean ServicioService catalogo;
    @Autowired DataSource fuente;
    final Set<Integer> conexiones = ConcurrentHashMap.newKeySet();
    final AtomicBoolean fallarA = new AtomicBoolean();

    @BeforeEach void observarTransacciones() {
        org.mockito.stubbing.Answer<Object> observar = invocacion -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            conexiones.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            long ambito = contexto.negocioActual().orElseThrow();
            Object resultado = invocacion.callRealMethod();
            if (ambito == a && fallarA.compareAndSet(true, false)) {
                throw new NegocioException(ErrorCodigo.CONFLICTO, "Fallo ficticio después de escribir.");
            }
            return resultado;
        };
        doAnswer(observar).when(catalogo).crear(any(GuardarServicioDto.class));
        doAnswer(observar).when(catalogo).listar(anyBoolean());
        servicio("a"); servicio("b");
    }

    static String nuevo(String nombre) {
        return "{\"nombre\":\"Nuevo " + nombre + "\",\"descripcion\":\"Ficticio\",\"duracionMin\":30,\"precio\":20}";
    }

    void conexionLimpia() throws Exception {
        try (var conexion = fuente.getConnection(); var consulta = conexion.createStatement();
                var resultado = consulta.executeQuery("select pg_backend_pid(), nullif(current_setting('app.negocio_id', true), ''), current_user")) {
            resultado.next();
            conexiones.add(resultado.getInt(1));
            assertThat(resultado.getString(2)).isNull();
            assertThat(resultado.getString(3)).isEqualTo("barberturno_v2_app");
        }
        assertThat(conexiones).hasSize(1);
        assertThat(contexto.delimitado()).isFalse();
    }

    @Test void errorDespuesDeInsertarEnA_rollback_yBReutilizaLaMismaConexionSinContextoAnterior() throws Exception {
        var actor = sesion(personal("gestor-error", "a", RolNegocio.PROPIETARIO));
        fallarA.set(true);
        mvc.perform(csrf(post("/api/n/a/servicios").contentType("application/json").content(nuevo("a")), actor))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLICTO"));
        conexionLimpia();
        assertThat(this.<Long>en("a", servicios::count)).isEqualTo(1);
        mvc.perform(get("/api/n/b/servicios").cookie(sesion(personal("gestor-b", "b", RolNegocio.PROPIETARIO))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Corte b"));
        conexionLimpia();
    }

    @RepeatedTest(20)
    void peticionesConcurrentesConSesionCsrfYRolReales_noCruzanDatosConUnaConexion() throws Exception {
        var solicitudA = csrf(post("/api/n/a/servicios").contentType("application/json").content(nuevo("a")),
                sesion(personal("concurrente-a", "a", RolNegocio.PROPIETARIO)));
        var solicitudB = csrf(post("/api/n/b/servicios").contentType("application/json").content(nuevo("b")),
                sesion(personal("concurrente-b", "b", RolNegocio.PROPIETARIO)));
        var inicio = new CountDownLatch(1);
        var hilos = Executors.newFixedThreadPool(2);
        try {
            var x = hilos.submit(() -> { inicio.await(); mvc.perform(solicitudA).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nombre").value("Nuevo a")); assertThat(contexto.delimitado()).isFalse(); return true; });
            var y = hilos.submit(() -> { inicio.await(); mvc.perform(solicitudB).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nombre").value("Nuevo b")); assertThat(contexto.delimitado()).isFalse(); return true; });
            inicio.countDown();
            assertThat(x.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(y.get(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            inicio.countDown(); hilos.shutdownNow();
            assertThat(hilos.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(this.<java.util.List<String>>en("a", () -> jdbc.queryForList("select nombre from servicio order by nombre", String.class)))
                .containsExactly("Corte a", "Nuevo a");
        assertThat(this.<java.util.List<String>>en("b", () -> jdbc.queryForList("select nombre from servicio order by nombre", String.class)))
                .containsExactly("Corte b", "Nuevo b");
        conexionLimpia();
    }
}
