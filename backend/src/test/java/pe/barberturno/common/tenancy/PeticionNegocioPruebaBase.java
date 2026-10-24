package pe.barberturno.common.tenancy;

import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.auth.*;
import pe.barberturno.catalog.*;
import pe.barberturno.customers.*;
import pe.barberturno.platform.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** HTTP usa siempre el holder real; los fixtures delimitan un slug explícito antes de la transacción. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1", "barberturno.admin.correo=",
        "barberturno.admin.password=", "barberturno.admin.nombre=", "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PeticionNegocioPruebaBase {
    @Autowired MockMvc mvc;
    @Autowired ContextoNegocio contexto;
    @Autowired EjecutorEnNegocio ejecutor;
    @Autowired DirectorioNegocios directorio;
    @Autowired PlatformTransactionManager gestor;
    @Autowired Environment entorno;
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcNegocioPruebas sql;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired ClienteRepository clientes;
    @Autowired ServicioRepository servicios;
    @Autowired BarberoRepository barberos;
    @Autowired JornadaRepository jornadas;
    @Autowired JwtService jwt;
    @Autowired RelojAjustable reloj;
    long a;
    long b;

    @BeforeEach void prepararPeticiones() throws Exception {
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
        try (var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"));
                var sentencia = conexion.prepareStatement("insert into negocio(slug,nombre,creado_en,actualizado_en) values (?, ?, CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            assertThat(conexion.getCatalog()).isEqualTo("barberturno_v2_test");
            for (String slug : new String[]{"a", "b"}) {
                sentencia.setString(1, slug); sentencia.setString(2, "Negocio ficticio " + slug); sentencia.executeUpdate();
            }
        }
        a = directorio.buscar("a").orElseThrow(); b = directorio.buscar("b").orElseThrow();
        SqlPruebas.limpiar();
    }

    @AfterEach void terminarPeticiones() {
        assertThat(contexto.delimitado()).isFalse();
        LimpiezaBaseDatos.limpiar(sql);
        SqlPruebas.limpiar();
    }

    <T> T en(String slug, Supplier<T> accion) {
        return ejecutor.ejecutar(slug, () -> new TransactionTemplate(gestor).execute(tx -> accion.get()));
    }

    Usuario persona(String nombre) {
        return usuarios.saveAndFlush(new Usuario("Persona ficticia " + nombre, nombre + "@ejemplo.test", "999000001",
                "hash-ficticio-no-utilizable", Rol.CLIENTE, reloj.instant(), reloj.instant()));
    }

    Usuario personal(String nombre, String slug, RolNegocio rol) {
        var usuario = persona(nombre);
        membresias.save(new Membresia(directorio.buscar(slug).orElseThrow(), usuario.getId(), rol, reloj.instant()));
        return usuario;
    }

    Servicio servicio(String slug) {
        return en(slug, () -> servicios.saveAndFlush(new Servicio("Corte " + slug, "Ficticio", (short)30,
                new java.math.BigDecimal("20.00"), reloj.instant())));
    }

    Cookie sesion(Usuario usuario) { return new Cookie("BT_SESION", jwt.emitir(usuario)); }

    MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder solicitud, Cookie sesion) throws Exception {
        var token = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (sesion != null) solicitud.cookie(sesion);
        return solicitud.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
