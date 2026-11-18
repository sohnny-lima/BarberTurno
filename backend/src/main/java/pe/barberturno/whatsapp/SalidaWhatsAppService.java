package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Encola salidas en la misma transacción de la operación de negocio, sin comprobar condiciones de entrega.
 * Valida el contenido y la propiedad de conversación y reserva antes de insertar bajo RLS (CI-1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SalidaWhatsAppService implements SalidaWhatsApp {
    private final ColaSalidaWhatsApp cola;
    private final EntityManager entidades;
    private final ProveedorNegocioActual negocio;
    private final JsonMapper mapper;
    private final Clock reloj;

    SalidaWhatsAppService(ColaSalidaWhatsApp cola, EntityManager entidades, ProveedorNegocioActual negocio,
            JsonMapper mapper, Clock reloj) {
        this.cola = cola;
        this.entidades = entidades;
        this.negocio = negocio;
        this.mapper = mapper;
        this.reloj = reloj;
    }

    /** {@inheritDoc} */
    @Override
    public long encolarTexto(long conversacionId, String texto, OrigenMensaje origen, OptionalLong reservaId) {
        if (texto == null || texto.isBlank() || texto.length() > 4096) throw validacion();
        return insertar(conversacionId, reservaId, TipoMensaje.TEXTO, origen,
                mapper.createObjectNode().put("texto", texto));
    }

    /** {@inheritDoc} */
    @Override
    public long encolarInteractivo(long conversacionId, JsonNode interactivo, OrigenMensaje origen,
            OptionalLong reservaId) {
        if (interactivo == null || !interactivo.isObject()
                || !("button".equals(interactivo.path("type").asString())
                    || "list".equals(interactivo.path("type").asString()))
                || !interactivo.path("body").path("text").isString()
                || interactivo.path("body").path("text").asString().isBlank()
                || mapper.writeValueAsString(interactivo).getBytes(StandardCharsets.UTF_8).length > 8192) {
            throw validacion();
        }
        ObjectNode contenido = mapper.createObjectNode();
        contenido.set("interactivo", interactivo);
        return insertar(conversacionId, reservaId, TipoMensaje.INTERACTIVO, origen, contenido);
    }

    /** {@inheritDoc} */
    @Override
    public long encolarPlantilla(long conversacionId, PlantillaUtility plantilla, Map<String, String> parametros,
            OptionalLong reservaId) {
        return plantilla(conversacionId, plantilla, parametros, reservaId, false);
    }

    /** {@inheritDoc} */
    @Override
    public long encolarAviso(long conversacionId, PlantillaUtility plantilla, Map<String, String> parametros,
            OptionalLong reservaId) {
        return plantilla(conversacionId, plantilla, parametros, reservaId, true);
    }

    private long plantilla(long conversacionId, PlantillaUtility plantilla, Map<String, String> parametros,
            OptionalLong reservaId, boolean textoEnVentana) {
        if (plantilla == null) throw validacion();
        plantilla.validar(parametros);
        ObjectNode contenido = mapper.createObjectNode().put("plantilla", plantilla.nombre())
                .put("idioma", plantilla.idioma()).put("textoEnVentana", textoEnVentana);
        ObjectNode valores = contenido.putObject("parametros");
        plantilla.parametros().forEach(nombre -> valores.put(nombre, parametros.get(nombre)));
        return insertar(conversacionId, reservaId, TipoMensaje.PLANTILLA, OrigenMensaje.SISTEMA, contenido);
    }

    private long insertar(long conversacionId, OptionalLong reservaId, TipoMensaje tipo, OrigenMensaje origen,
            ObjectNode contenido) {
        if (origen == null || origen == OrigenMensaje.CLIENTE || origen == OrigenMensaje.PERSONA
                || reservaId == null) throw validacion();
        long negocioId = negocio.negocioActual().orElseThrow(this::noEncontrado);
        cola.conversacion(conversacionId, negocioId).orElseThrow(this::noEncontrado);
        if (reservaId.isPresent() && entidades.createNativeQuery("""
                SELECT id FROM reserva WHERE id = :id AND negocio_id = :negocio
                """, Long.class).setParameter("id", reservaId.getAsLong()).setParameter("negocio", negocioId)
                .getResultList().isEmpty()) throw noEncontrado();
        return cola.insertar(conversacionId, reservaId.isPresent() ? reservaId.getAsLong() : null,
                tipo, origen, mapper.writeValueAsString(contenido), reloj.instant());
    }

    /** {@inheritDoc} */
    @Override
    public long encolarRespuestaPersonal(long conversacionId, String texto, long usuarioId) {
        if (texto == null || texto.isBlank() || texto.length() > 4096 || usuarioId <= 0) throw validacion();
        long negocioId = negocio.negocioActual().orElseThrow(this::noEncontrado);
        cola.conversacion(conversacionId, negocioId).orElseThrow(this::noEncontrado);
        return cola.insertar(conversacionId, null, TipoMensaje.TEXTO, OrigenMensaje.PERSONA,
                mapper.writeValueAsString(mapper.createObjectNode().put("texto", texto)), reloj.instant(), usuarioId);
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró la conversación o la reserva.");
    }

    private static NegocioException validacion() {
        return new NegocioException(ErrorCodigo.VALIDACION, "El contenido o el origen del mensaje no es válido.");
    }
}
