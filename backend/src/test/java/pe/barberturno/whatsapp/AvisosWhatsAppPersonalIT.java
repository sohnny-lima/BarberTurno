package pe.barberturno.whatsapp;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import pe.barberturno.audit.AccionAuditoria;
import pe.barberturno.auth.JwtService;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.platform.RolPlataforma;
import pe.barberturno.users.Usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de la consulta y marcado de lectura de los avisos internos de WhatsApp
 * para los tres roles de personal destinatarios (propietario, encargado y profesional asignado),
 * tanto por la API HTTP ({@code /api/n/{slug}/notificaciones}) como por {@link NotificacionService}
 * (V2-DA-61, 12-v2-09-whatsapp-salida.md §4.5 y CI-9).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class AvisosWhatsAppPersonalIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JwtService jwt;

    @Autowired
    private EnviadorSalidaWhatsApp enviador;

    @Autowired
    private SalidaWhatsApp salida;

    @Autowired
    private NotificacionService notificaciones;

    private MockMvc mvc;

    @BeforeEach
    void prepararMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private Cookie sesion(Usuario usuario) {
        return new Cookie("BT_SESION", jwt.emitir(usuario));
    }

    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder solicitud, Cookie sesion) throws Exception {
        var token = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        if (sesion != null) {
            solicitud.cookie(sesion);
        }
        return solicitud.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private UsuarioAutenticado actor(Usuario usuario, RolNegocio rolNegocio, Optional<Long> barberoId) {
        return new UsuarioAutenticado(usuario.getId(), rolNegocio.rolEfectivo(), barberoId,
                false, RolPlataforma.NINGUNO, Optional.of(rolNegocio));
    }

    private long provocarFalloPermanenteConReserva(long negocioId, String textoSecreto) {
        long conv = crearConversacion(negocioId, WA_CLIENTE);
        long reserva = crearReserva(negocioId, WA_CLIENTE);
        long msg = ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarTexto(conv, textoSecreto, OrigenMensaje.SISTEMA, OptionalLong.of(reserva))));
        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();
        var fila = filaMensaje(negocioId, msg);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.DESTINO_NO_VALIDO.name());
        return reserva;
    }

    @Test
    void falloPermanenteConReserva_losTresDestinatariosListanYMarcanSuAvisoPorApiYServicioSinVerNiMarcarElAjeno()
            throws Exception {
        String textoSecreto = "Mensaje privado para Cliente Ficticio";
        long reservaA = provocarFalloPermanenteConReserva(a, textoSecreto);
        String textoEsperado = "No se pudo enviar por WhatsApp el mensaje de la reserva BT-" + reservaA
                + ": " + MotivoFallo.DESTINO_NO_VALIDO.descripcion() + ". Contacte al cliente por otro medio.";

        record Destinatario(Usuario usuario, RolNegocio rol, Optional<Long> barberoId, Cookie cookie) {}
        var destinatarios = List.of(
                new Destinatario(titular, RolNegocio.PROPIETARIO, Optional.empty(), sesion(titular)),
                new Destinatario(encargado, RolNegocio.ENCARGADO, Optional.empty(), sesion(encargado)),
                new Destinatario(barberoCuenta, RolNegocio.BARBERO, Optional.of(barberoA), sesion(barberoCuenta)));

        Map<Long, Long> avisoPorUsuario = new java.util.LinkedHashMap<>();

        // 1. Cada uno de los tres destinatarios ve el aviso en su lista y en su contador (API y servicio),
        //    sin datos personales del cliente en el texto (V2-DA-61).
        for (var d : destinatarios) {
            mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(d.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.noLeidas").value(1));

            var respuesta = mvc.perform(get("/api/n/a/notificaciones").cookie(d.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido.length()").value(1))
                    .andExpect(jsonPath("$.contenido[0].reservaId").value(reservaA))
                    .andExpect(jsonPath("$.contenido[0].tipo").value("WHATSAPP"))
                    .andExpect(jsonPath("$.contenido[0].leida").value(false))
                    .andExpect(jsonPath("$.contenido[0].mensaje").value(textoEsperado))
                    .andReturn()
                    .getResponse();

            var nodo = mapper.readTree(respuesta.getContentAsByteArray()).path("contenido").get(0);
            long avisoId = nodo.path("id").asLong();
            String mensaje = nodo.path("mensaje").asString();
            assertThat(mensaje)
                    .isEqualTo(textoEsperado)
                    .doesNotContain(WA_CLIENTE, "999000001", "Cliente Ficticio", textoSecreto);
            avisoPorUsuario.put(d.usuario().getId(), avisoId);

            var identidad = actor(d.usuario(), d.rol(), d.barberoId());
            var conteoServicio = ejecutor.ejecutar(a, () -> notificaciones.conteo(identidad));
            var paginaServicio = ejecutor.ejecutar(a, () -> notificaciones.listar(identidad, false, 0, 20));
            assertThat(conteoServicio.noLeidas()).isEqualTo(1L);
            assertThat(paginaServicio.contenido()).hasSize(1);
            assertThat(paginaServicio.contenido().getFirst().id()).isEqualTo(avisoId);
            assertThat(paginaServicio.contenido().getFirst().tipo()).isEqualTo(AccionAuditoria.WHATSAPP);
            assertThat(paginaServicio.contenido().getFirst().mensaje())
                    .isEqualTo(textoEsperado)
                    .doesNotContain(WA_CLIENTE, "999000001", "Cliente Ficticio", textoSecreto);
        }

        assertThat(Set.copyOf(avisoPorUsuario.values()))
                .as("Cada destinatario debe tener su propia fila de notificación independiente")
                .hasSize(3);

        // 2. Ningún destinatario puede marcar ni leer el aviso ajeno (404 NO_ENCONTRADO en API y servicio).
        for (var d : destinatarios) {
            long propioId = avisoPorUsuario.get(d.usuario().getId());
            var identidad = actor(d.usuario(), d.rol(), d.barberoId());
            for (var otro : destinatarios) {
                if (otro.usuario().getId().equals(d.usuario().getId())) {
                    continue;
                }
                long ajenoId = avisoPorUsuario.get(otro.usuario().getId());
                assertThat(ajenoId).isNotEqualTo(propioId);

                mvc.perform(csrf(post("/api/n/a/notificaciones/" + ajenoId + "/lectura"), d.cookie()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

                assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> {
                    notificaciones.marcarLeida(identidad, ajenoId);
                    return null;
                }))
                        .isInstanceOf(NegocioException.class)
                        .satisfies(e -> assertThat(((NegocioException) e).codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
            }
        }

        // Tras los intentos cruzados rechazados, los tres destinatarios conservan su aviso sin leer.
        for (var d : destinatarios) {
            mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(d.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.noLeidas").value(1));
        }

        // 3. Marcar como leído baja el contador solo para ese usuario y deja intactos a los demás.
        for (int i = 0; i < destinatarios.size(); i++) {
            var actual = destinatarios.get(i);
            long propioId = avisoPorUsuario.get(actual.usuario().getId());

            mvc.perform(csrf(post("/api/n/a/notificaciones/" + propioId + "/lectura"), actual.cookie()))
                    .andExpect(status().isNoContent());

            // El destinatario que marcó ve su contador en 0 y el aviso como leído.
            mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(actual.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.noLeidas").value(0));
            mvc.perform(get("/api/n/a/notificaciones").cookie(actual.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].id").value(propioId))
                    .andExpect(jsonPath("$.contenido[0].leida").value(true));
            mvc.perform(get("/api/n/a/notificaciones").cookie(actual.cookie()).param("soloNoLeidas", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(0));

            // Los destinatarios que aún no han marcado siguen con noLeidas = 1 y leida = false.
            for (int j = i + 1; j < destinatarios.size(); j++) {
                var pendiente = destinatarios.get(j);
                mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(pendiente.cookie()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.noLeidas").value(1));
                mvc.perform(get("/api/n/a/notificaciones").cookie(pendiente.cookie()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.contenido[0].leida").value(false));
            }
        }
    }

    @Test
    void marcarTodasLeidas_porApi_bajaContadorSoloParaElDestinatarioAutenticado() throws Exception {
        provocarFalloPermanenteConReserva(a, "Mensaje con fallo para lote");

        Cookie cookieTitular = sesion(titular);
        Cookie cookieEncargado = sesion(encargado);
        Cookie cookieBarbero = sesion(barberoCuenta);

        mvc.perform(csrf(post("/api/n/a/notificaciones/lectura"), cookieEncargado))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(cookieEncargado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(0));
        mvc.perform(get("/api/n/a/notificaciones").cookie(cookieEncargado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].leida").value(true));

        for (Cookie otra : List.of(cookieTitular, cookieBarbero)) {
            mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(otra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.noLeidas").value(1));
            mvc.perform(get("/api/n/a/notificaciones").cookie(otra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].leida").value(false));
        }
    }

    @Test
    void aislamientoMultinegocio_usuarioDeBNoVeNiMarcaElAvisoDeA() throws Exception {
        provocarFalloPermanenteConReserva(a, "Mensaje aislado en negocio A");

        Cookie cookieTitular = sesion(titular);
        var respuestaTitularA = mvc.perform(get("/api/n/a/notificaciones").cookie(cookieTitular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andReturn()
                .getResponse();
        long idAvisoTitularEnA = mapper.readTree(respuestaTitularA.getContentAsByteArray())
                .path("contenido").get(0).path("id").asLong();

        // 1. El profesional exclusivo de B no ve avisos en B, no puede marcar el id de A en B (404),
        //    ni tiene permiso en las rutas de A (403).
        Usuario barberoCuentaB = usuarios.findByCorreo("barbero-b-ficticio@ejemplo.test").orElseThrow();
        Cookie cookieBarberoB = sesion(barberoCuentaB);

        mvc.perform(get("/api/n/b/notificaciones/conteo").cookie(cookieBarberoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(0));
        mvc.perform(get("/api/n/b/notificaciones").cookie(cookieBarberoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.contenido").isEmpty());
        mvc.perform(csrf(post("/api/n/b/notificaciones/" + idAvisoTitularEnA + "/lectura"), cookieBarberoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(get("/api/n/a/notificaciones").cookie(cookieBarberoB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        // 2. Incluso el mismo titular (propietario en A y en B), al consultar o marcar bajo el negocio B,
        //    no ve ni altera su propio aviso del negocio A.
        mvc.perform(get("/api/n/b/notificaciones/conteo").cookie(cookieTitular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(0));
        mvc.perform(get("/api/n/b/notificaciones").cookie(cookieTitular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.contenido").isEmpty());
        mvc.perform(csrf(post("/api/n/b/notificaciones/" + idAvisoTitularEnA + "/lectura"), cookieTitular))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(csrf(post("/api/n/b/notificaciones/lectura"), cookieTitular))
                .andExpect(status().isNoContent());

        // El aviso del titular en A permanece intacto y no leído.
        mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(cookieTitular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(1));
        mvc.perform(get("/api/n/a/notificaciones").cookie(cookieTitular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(idAvisoTitularEnA))
                .andExpect(jsonPath("$.contenido[0].leida").value(false));
    }
}
