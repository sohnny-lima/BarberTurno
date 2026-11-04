package pe.barberturno.common.tenancy;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.conversation.*;
import pe.barberturno.platform.*;
import pe.barberturno.users.Usuario;
import pe.barberturno.whatsapp.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API real: dos negocios, sesiones y CSRF, permisos de gestión, historial y transiciones. */
class BandejaWhatsAppIT extends PeticionNegocioPruebaBase {
    @MockitoBean TextosConversacion textos;
    @Autowired DerivacionConversacion derivacion;
    @Autowired BandejaWhatsApp bandeja;
    @Autowired SalidaWhatsApp salida;
    Usuario titular;
    Usuario encargado;
    long ficha;
    long conversacion;

    @BeforeEach void prepararBandeja() {
        when(textos.derivada(anyString())).thenReturn("El equipo le atenderá por aquí.");
        when(textos.devuelta()).thenReturn("Puede continuar con el asistente.");
        titular = personal("titular", "a", RolNegocio.PROPIETARIO);
        encargado = personal("encargado", "a", RolNegocio.ENCARGADO);
        en("a", () -> jdbc.update("""
                insert into canal_whatsapp(negocio_id,waba_id,phone_number_id,numero_visible,creado_en,actualizado_en)
                values (?, '100', '200', '+51999000001', ?, ?)
                """, a, ahora(), ahora()));
        ficha = crearFicha("a", "51999000123");
        conversacion = crearConversacion("a", ficha, "51999000123");
    }

    @ParameterizedTest @ValueSource(strings = {"respuestas", "devolver", "anonimizar"})
    void escritura_sesionValida_sinCsrfOIncorrecto403_yAnonimo401(String accion) throws Exception {
        mvc.perform(accion(accion, "a", recurso(accion)).cookie(sesion(titular))).andExpect(status().isForbidden());
        Cookie token = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(accion(accion, "a", recurso(accion)).cookie(sesion(titular), token)
                .header("X-XSRF-TOKEN", "incorrecto")).andExpect(status().isForbidden());
        mvc.perform(csrf(accion(accion, "a", recurso(accion)), null)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"respuestas", "devolver", "anonimizar"})
    void escritura_profesionalClienteYPlataformaSinMembresia403_yAjeno404(String accion) throws Exception {
        var profesional = personal("profesional", "a", RolNegocio.BARBERO);
        en("a", () -> barberos.saveAndFlush(new pe.barberturno.scheduling.Barbero(profesional, "Corte", reloj.instant())));
        var cliente = persona("cliente");
        en("a", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(cliente, reloj.instant())));
        var plataforma = persona("plataforma");
        plataforma.habilitarAdministracionPlataforma(); usuarios.saveAndFlush(plataforma);
        for (Usuario actor : List.of(profesional, cliente, plataforma)) {
            mvc.perform(csrf(accion(accion, "a", recurso(accion)), sesion(actor))).andExpect(status().isForbidden());
        }
        long fichaB = crearFicha("b", "51999000123");
        long cB = crearConversacion("b", fichaB, "51999000123");
        mvc.perform(csrf(accion(accion, "a", accion.equals("anonimizar") ? fichaB : cB), sesion(titular)))
                .andExpect(status().isNotFound());
        mvc.perform(csrf(accion(accion, "inexistente", recurso(accion)), sesion(titular))).andExpect(status().isNotFound());
    }

    @Test void lecturas_soloGestionActiva_yNuncaExponenElWaId() throws Exception {
        derivar();
        for (Usuario actor : List.of(titular, encargado)) {
            var respuesta = mvc.perform(get(ruta()).cookie(sesion(actor))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].telefonoEnmascarado").value("•••…123"))
                    .andExpect(jsonPath("$.contenido[0].pendienteDeRespuesta").value(true))
                    .andExpect(jsonPath("$.contenido[0].puedeResponder").value(true))
                    .andReturn().getResponse().getContentAsString();
            assertThat(respuesta).doesNotContain("51999000123");
        }
        var externo = persona("externo"); externo.habilitarAdministracionPlataforma(); usuarios.saveAndFlush(externo);
        mvc.perform(get(ruta()).cookie(sesion(externo))).andExpect(status().isForbidden());
        mvc.perform(get(ruta())).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta() + "/" + conversacion).param("antesDe", "0").cookie(sesion(titular)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ruta()).param("tamano", "51").cookie(sesion(titular))).andExpect(status().isBadRequest());
        var profesional = personal("profesional-lectura", "a", RolNegocio.BARBERO);
        en("a", () -> barberos.saveAndFlush(new pe.barberturno.scheduling.Barbero(profesional, "Corte", reloj.instant())));
        var cliente = persona("cliente-lectura");
        en("a", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(cliente, reloj.instant())));
        for (Usuario actor : List.of(profesional, cliente)) {
            mvc.perform(get(ruta()).cookie(sesion(actor))).andExpect(status().isForbidden());
            mvc.perform(get(ruta() + "/" + conversacion).cookie(sesion(actor))).andExpect(status().isForbidden());
        }
    }

    @Test void responder_firmaConAutorActual_yDevolverEsIdempotente() throws Exception {
        derivar();
        mvc.perform(csrf(accion("respuestas", "a", conversacion), sesion(encargado))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("PERSONA"))
                .andExpect(jsonPath("$.enviadoPor").value(encargado.getNombre()));
        mvc.perform(get(ruta()).cookie(sesion(titular))).andExpect(jsonPath("$.contenido[0].pendienteDeRespuesta").value(false));
        en("a", () -> jdbc.update("update usuario set nombre='Nombre actual' where id=?", encargado.getId()));
        mvc.perform(get(ruta() + "/" + conversacion).cookie(sesion(titular))).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes[1].enviadoPor").value("Nombre actual"));
        mvc.perform(csrf(accion("devolver", "a", conversacion), sesion(titular))).andExpect(status().isNoContent());
        long mensajes = contarMensajes();
        mvc.perform(csrf(accion("devolver", "a", conversacion), sesion(titular))).andExpect(status().isNoContent());
        assertThat(contarMensajes()).isEqualTo(mensajes);
        mvc.perform(get(ruta() + "/" + conversacion).cookie(sesion(titular))).andExpect(status().isNotFound());
        mvc.perform(csrf(accion("respuestas", "a", conversacion), sesion(titular))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test void ventana_igualdadEfectiva_yEntradaNula_rechazanResponder_sinEncolarAlDevolver() throws Exception {
        derivar();
        for (Timestamp entrada : new Timestamp[]{Timestamp.from(reloj.instant().minus(Duration.ofHours(24)).plusSeconds(300)), null}) {
            en("a", () -> jdbc.update("update conversacion set ultima_entrada_en=? where id=?", entrada, conversacion));
            mvc.perform(get(ruta()).cookie(sesion(titular))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].puedeResponder").value(false))
                    .andExpect(jsonPath("$.contenido[0].motivoNoResponder").value("FUERA_DE_VENTANA"));
            mvc.perform(csrf(accion("respuestas", "a", conversacion), sesion(titular))).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo").value("FUERA_DE_VENTANA"));
        }
        long mensajes = contarMensajes();
        mvc.perform(csrf(accion("devolver", "a", conversacion), sesion(titular))).andExpect(status().isNoContent());
        assertThat(contarMensajes()).isEqualTo(mensajes);
    }

    @Test void derivar_anulaOperacion_vaciaEstado_yAvisaSoloGestoresActivos_sinDuplicar() throws Exception {
        var revocado = personal("revocado", "a", RolNegocio.ENCARGADO);
        en("a", () -> jdbc.update("update membresia set estado='REVOCADA',motivo_revocacion='DECISION_GESTOR' where usuario_id=?", revocado.getId()));
        personal("gestor-b", "b", RolNegocio.PROPIETARIO);
        personal("profesional", "a", RolNegocio.BARBERO);
        en("a", () -> jdbc.update("""
                insert into operacion_pendiente(conversacion_id,cliente_id,tipo,datos,nonce,expira_en,creado_en,actualizado_en)
                values (?,?,'CREAR','{}',?,?,?,?)
                """, conversacion, ficha, new byte[16], Timestamp.from(reloj.instant().plusSeconds(600)), ahora(), ahora()));
        assertThat(en("a", () -> derivacion.derivar(conversacion, MotivoDerivacion.PETICION))).isTrue();
        assertThat(en("a", () -> derivacion.derivar(conversacion, MotivoDerivacion.SIN_PROGRESO))).isFalse();
        assertThat(en("a", () -> jdbc.queryForObject("select estado from operacion_pendiente where conversacion_id=?", String.class, conversacion))).isEqualTo("ANULADA");
        assertThat(en("a", () -> jdbc.queryForObject("select estado::text from conversacion where id=?", String.class, conversacion))).isEqualTo("{}");
        assertThat(en("a", () -> jdbc.queryForList("select usuario_id from notificacion where conversacion_id=? order by usuario_id", Long.class, conversacion)))
                .containsExactly(titular.getId(), encargado.getId());
        assertThat(contarMensajes()).isEqualTo(1);
        mvc.perform(get("/api/n/a/notificaciones").cookie(sesion(titular))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].conversacionId").value(conversacion))
                .andExpect(jsonPath("$.contenido[0].reservaId").isEmpty());
    }

    @Test void historial_paginaCincuentaEnOrden_yRenderizaCadaContenido_sinFiltrarMasDeTresDigitos() throws Exception {
        derivar();
        en("a", () -> {
            for (int i = 0; i < 55; i++) jdbc.update("""
                    insert into mensaje_whatsapp(conversacion_id,direccion,origen,tipo,contenido,estado,creado_en,actualizado_en)
                    values (?,'ENTRANTE','CLIENTE','TEXTO','{"texto":"Mensaje ficticio"}','RECIBIDO',?,?)
                    """, conversacion, ahora(), ahora());
            return null;
        });
        var detalle = en("a", () -> bandeja.detalle(conversacion, null, titular.getId()));
        assertThat(detalle.mensajes()).hasSize(50).isSortedAccordingTo(java.util.Comparator.comparingLong(pe.barberturno.whatsapp.dto.MensajeBandejaDto::id));
        assertThat(detalle.hayAnteriores()).isTrue();
        var anteriores = en("a", () -> bandeja.detalle(conversacion, detalle.mensajes().getFirst().id(), titular.getId()));
        assertThat(anteriores.mensajes()).hasSize(6);
        assertThat(anteriores.hayAnteriores()).isFalse();
    }

    @Test void textosVisibles_seleccionInteractivoPlantillaTipoYRetirado_yNombreActualAunqueDejeElEquipo() throws Exception {
        derivar();
        en("a", () -> {
            for (String contenido : List.of("{\"titulo\":\"Seleccionado\",\"seleccion\":\"s:1\"}",
                    "{\"interactivo\":{\"body\":{\"text\":\"Elija\"},\"action\":{\"buttons\":[{\"reply\":{\"title\":\"Primero\"}}]}}}",
                    "{\"tipo\":\"audio\"}", "{\"retirado\":true}")) {
                jdbc.update("""
                        insert into mensaje_whatsapp(conversacion_id,direccion,origen,tipo,contenido,estado,creado_en,actualizado_en)
                        values (?,'ENTRANTE','CLIENTE','TEXTO',cast(? as jsonb),'RECIBIDO',?,?)
                        """, conversacion, contenido, ahora(), ahora());
            }
            return null;
        });
        en("a", () -> bandeja.responder(conversacion, "Respondido", encargado.getId()));
        en("a", () -> jdbc.update("update membresia set estado='REVOCADA',motivo_revocacion='SALIDA' where usuario_id=?", encargado.getId()));
        en("a", () -> jdbc.update("update usuario set nombre='Nombre vigente' where id=?", encargado.getId()));
        var mensajes = en("a", () -> bandeja.detalle(conversacion, null, titular.getId())).mensajes();
        assertThat(mensajes).extracting(pe.barberturno.whatsapp.dto.MensajeBandejaDto::texto)
                .contains("Seleccionado", "Elija [Primero]", "[audio]", null, "Respondido");
        assertThat(mensajes.get(4).retirado()).isTrue();
        assertThat(mensajes.getLast().enviadoPor()).isEqualTo("Nombre vigente");
    }

    @Test void historial_muestraLaPlantillaRenderizada_yLasFilasDelInteractivo() {
        derivar();
        en("a", () -> salida.encolarPlantilla(conversacion, PlantillaUtility.BT_RECORDATORIO_CITA,
                java.util.Map.of("negocio", "Negocio ficticio", "fecha", "Fecha ficticia",
                        "profesional", "Profesional ficticio", "codigo", "BT-123"), java.util.OptionalLong.empty()));
        var lista = tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode().put("type", "list");
        lista.putObject("body").put("text", "Elija lista");
        var accion = lista.putObject("action").put("button", "Elegir");
        accion.putArray("sections").addObject().putArray("rows").addObject().put("id", "s:1").put("title", "Fila visible");
        en("a", () -> salida.encolarInteractivo(conversacion, lista, OrigenMensaje.SISTEMA, java.util.OptionalLong.empty()));
        var mensajes = en("a", () -> bandeja.detalle(conversacion, null, titular.getId())).mensajes();
        assertThat(mensajes.get(1).texto()).contains("Negocio ficticio", "Fecha ficticia", "Profesional ficticio", "BT-123");
        assertThat(mensajes.get(2).texto()).isEqualTo("Elija lista [Fila visible]");
    }

    @Test void lista_empatesPorIdMascaraCorta_yEstadoNoOperativo() throws Exception {
        derivar();
        long corta = crearFicha("a", "1234");
        long otra = crearConversacion("a", corta, "1234");
        en("a", () -> derivacion.derivar(otra, MotivoDerivacion.SIN_PROGRESO));
        var lista = en("a", () -> bandeja.listar(titular.getId(), 0, 50));
        assertThat(lista.contenido()).extracting(pe.barberturno.whatsapp.dto.ConversacionBandejaDto::id).containsExactly(otra, conversacion);
        assertThat(lista.contenido().getFirst().telefonoEnmascarado()).isEqualTo("•••…");
        en("a", () -> jdbc.update("update canal_whatsapp set estado='DESCONECTADO' where negocio_id=?", a));
        mvc.perform(get(ruta()).cookie(sesion(titular))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].motivoNoResponder").value("NEGOCIO_NO_OPERATIVO"));
    }

    @Test void anonimizar_soloPropietario_idempotente_conservaFichaInactiva_yBloqueaEdicion() throws Exception {
        mvc.perform(csrf(accion("anonimizar", "a", ficha), sesion(encargado))).andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) mvc.perform(csrf(accion("anonimizar", "a", ficha), sesion(titular)))
                .andExpect(status().isNoContent());
        assertThat(en("a", () -> jdbc.queryForMap("select nombre,wa_id,telefono,activo,anonimizada_en from cliente where id=?", ficha)))
                .containsEntry("nombre", "Cliente eliminado").containsEntry("wa_id", null)
                .containsEntry("telefono", null).containsEntry("activo", false).hasEntrySatisfying("anonimizada_en", v -> assertThat(v).isNotNull());
        mvc.perform(csrf(patch("/api/n/a/clientes/" + ficha).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Otro nombre\",\"telefono\":null}"), sesion(titular))).andExpect(status().isConflict());
        assertThat(en("a", () -> jdbc.queryForObject("select count(*) from conversacion where id=?", Integer.class, conversacion))).isZero();
        long nuevaFicha = crearFicha("a", "51999000123");
        assertThat(nuevaFicha).isNotEqualTo(ficha);
        // El registro de una nueva entrada no encuentra la ficha anonimizada por su número.
        assertThat(en("a", () -> clientes.findByNegocioIdAndWaId(a, "51999000123").orElseThrow().getId())).isEqualTo(nuevaFicha);
    }

    private void derivar() { en("a", () -> derivacion.derivar(conversacion, MotivoDerivacion.PETICION)); }
    private long contarMensajes() { return en("a", () -> jdbc.queryForObject("select count(*) from mensaje_whatsapp where conversacion_id=?", Long.class, conversacion)); }
    private long recurso(String accion) { return accion.equals("anonimizar") ? ficha : conversacion; }
    private String ruta() { return "/api/n/a/whatsapp/conversaciones"; }
    private MockHttpServletRequestBuilder accion(String accion, String slug, long id) {
        return accion.equals("anonimizar") ? post("/api/n/" + slug + "/clientes/" + id + "/anonimizar")
                : post("/api/n/" + slug + "/whatsapp/conversaciones/" + id + "/" + accion)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Respuesta ficticia\"}");
    }
    private Timestamp ahora() { return Timestamp.from(reloj.instant()); }
    private long crearFicha(String slug, String wa) { return en(slug, () -> jdbc.queryForObject("insert into cliente(nombre,wa_id,creado_en,actualizado_en) values ('Ficha ficticia',?,?,?) returning id", Long.class, wa, ahora(), ahora())); }
    private long crearConversacion(String slug, long f, String wa) { return en(slug, () -> jdbc.queryForObject("insert into conversacion(wa_id,cliente_id,ultima_entrada_en,creado_en,actualizado_en) values (?,?,?,?,?) returning id", Long.class, wa, f, ahora(), ahora(), ahora())); }
}
