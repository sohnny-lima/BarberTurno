package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.AutorizacionGestionLocal;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.conversation.DerivacionConversacion;
import pe.barberturno.conversation.MotivoDerivacion;
import pe.barberturno.whatsapp.dto.*;
import pe.barberturno.whatsapp.dto.ConversacionBandejaDto.MotivoNoResponder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bandeja local con proyecciones mínimas, ventana efectiva y membresía revalidada después de los bloqueos.
 * La conversación es el primer bloqueo de todas sus escrituras (V2-DA-73).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class BandejaWhatsApp {
    private static final String COLUMNAS = """
            SELECT c.id, f.nombre, c.wa_id, c.motivo_derivacion, c.derivada_en,
                c.ultima_entrada_en, c.ultima_respuesta_personal_en
            FROM conversacion c JOIN cliente f ON f.negocio_id=c.negocio_id AND f.id=c.cliente_id
            """;
    private final EntityManager entidades;
    private final AutorizacionGestionLocal autorizacion;
    private final VentanaWhatsApp ventana;
    private final SalidaWhatsApp salida;
    private final DerivacionConversacion derivacion;
    private final JsonMapper mapper;
    private final Clock reloj;

    BandejaWhatsApp(EntityManager entidades, AutorizacionGestionLocal autorizacion, VentanaWhatsApp ventana,
            SalidaWhatsApp salida, DerivacionConversacion derivacion, JsonMapper mapper, Clock reloj) {
        this.entidades = entidades; this.autorizacion = autorizacion; this.ventana = ventana;
        this.salida = salida; this.derivacion = derivacion; this.mapper = mapper; this.reloj = reloj;
    }

    /**
     * Pagina conversaciones humanas por entrada e identidad descendentes sin exponer el remitente completo.
     * @param usuarioId identidad autenticada
     * @param pagina índice desde cero
     * @param tamano cantidad entre 1 y 50
     * @return página estable con permisos calculados por el servidor
     * @throws NegocioException si falta gestión o los límites no son válidos
     */
    @Transactional(readOnly = true)
    public PaginaDto<ConversacionBandejaDto> listar(long usuarioId, int pagina, int tamano) {
        long negocioId = autorizacion.exigir(usuarioId, false);
        if (pagina < 0 || tamano < 1 || tamano > 50 || (long) pagina * tamano > Integer.MAX_VALUE) {
            throw new NegocioException(ErrorCodigo.VALIDACION, "Indique una página válida y un tamaño entre 1 y 50.");
        }
        long total = ((Number) entidades.createNativeQuery("""
                SELECT count(*) FROM conversacion WHERE negocio_id=:negocio AND modo='HUMANO'
                """, Long.class).setParameter("negocio", negocioId).getSingleResult()).longValue();
        @SuppressWarnings("unchecked")
        List<Object[]> filas = entidades.createNativeQuery(COLUMNAS + """
                WHERE c.negocio_id=:negocio AND c.modo='HUMANO'
                ORDER BY c.ultima_entrada_en DESC NULLS FIRST, c.id DESC
                """, Object[].class).setParameter("negocio", negocioId)
                .setFirstResult(pagina * tamano).setMaxResults(tamano).getResultList();
        boolean operativo = operativo(negocioId);
        return new PaginaDto<>(filas.stream().map(f -> conversacion(f, operativo)).toList(), pagina, tamano,
                total, (int) ((total + tamano - 1) / tamano));
    }

    /**
     * Lee hasta 50 mensajes anteriores al cursor de una conversación humana visible.
     * @param id conversación solicitada
     * @param antesDe identidad positiva exclusiva, o nula para los últimos mensajes
     * @param usuarioId identidad autenticada
     * @return detalle ordenado por identidad ascendente, con indicación de mensajes anteriores
     * @throws NegocioException si falta gestión, el cursor no es positivo o la conversación no es humana y local
     */
    @Transactional(readOnly = true)
    public DetalleConversacionDto detalle(long id, Long antesDe, long usuarioId) {
        long negocioId = autorizacion.exigir(usuarioId, false);
        if (antesDe != null && antesDe <= 0) throw new NegocioException(ErrorCodigo.VALIDACION,
                "El cursor de mensajes debe ser positivo.");
        var filas = entidades.createNativeQuery(COLUMNAS + """
                WHERE c.negocio_id=:negocio AND c.id=:id AND c.modo='HUMANO'
                """, Object[].class).setParameter("negocio", negocioId).setParameter("id", id).getResultList();
        if (filas.isEmpty()) throw noEncontrado();
        @SuppressWarnings("unchecked")
        List<Object[]> mensajes = antesDe != null ? mensajesAntes(id, negocioId, antesDe)
                : entidades.createNativeQuery(consultaMensajes() + """
                WHERE m.conversacion_id=:id AND m.negocio_id=:negocio
                ORDER BY m.id DESC
                """, Object[].class)
                .setParameter("id", id).setParameter("negocio", negocioId)
                .setMaxResults(51).getResultList();
        boolean anteriores = mensajes.size() > 50;
        List<MensajeBandejaDto> visibles = new ArrayList<>(mensajes.stream().limit(50).map(this::mensaje).toList());
        Collections.reverse(visibles);
        return new DetalleConversacionDto(conversacion((Object[]) filas.getFirst(), operativo(negocioId)),
                List.copyOf(visibles), anteriores);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> mensajesAntes(long id, long negocioId, long cursor) {
        return entidades.createNativeQuery(consultaMensajes() + """
                WHERE m.conversacion_id=:id AND m.negocio_id=:negocio AND m.id < :cursor ORDER BY m.id DESC
                """, Object[].class).setParameter("id", id).setParameter("negocio", negocioId)
                .setParameter("cursor", cursor).setMaxResults(51).getResultList();
    }

    /**
     * Serializa la respuesta con la entrega del cliente y encola texto y autor de forma atómica.
     * @param id conversación solicitada
     * @param texto cuerpo entre 1 y 4096 caracteres
     * @param usuarioId identidad autenticada que firma el mensaje
     * @return mensaje pendiente recién encolado
     * @throws NegocioException si falta gestión, modo humano, operatividad o ventana efectiva
     */
    @Transactional
    public MensajeBandejaDto responder(long id, String texto, long usuarioId) {
        long negocioId = autorizacion.exigir(usuarioId, false);
        String modo = bloquear(id, negocioId);
        autorizacion.exigir(usuarioId, false);
        if (!"HUMANO".equals(modo)) throw new NegocioException(ErrorCodigo.TRANSICION_INVALIDA,
                "La conversación ya está atendida por el asistente.");
        if (!operativo(negocioId)) throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO,
                "El canal o el negocio no está operativo.");
        Instant entrada = (Instant) entidades.createNativeQuery(
                "SELECT ultima_entrada_en FROM conversacion WHERE id=:id", Instant.class)
                .setParameter("id", id).getSingleResult();
        if (!ventana.abierta(entrada, reloj.instant())) throw new NegocioException(ErrorCodigo.FUERA_DE_VENTANA,
                "La ventana de respuesta de WhatsApp está cerrada.");
        long mensajeId = salida.encolarRespuestaPersonal(id, texto, usuarioId);
        entidades.createNativeQuery("""
                UPDATE conversacion SET ultima_respuesta_personal_en=:ahora, actualizado_en=:ahora WHERE id=:id
                """).setParameter("id", id).setParameter("ahora", reloj.instant()).executeUpdate();
        return mensaje((Object[]) entidades.createNativeQuery(consultaMensajes() + " WHERE m.id=:id", Object[].class)
                .setParameter("id", mensajeId).getSingleResult());
    }

    /**
     * Devuelve la conversación al asistente; repetir la acción no añade mensajes.
     * @param id conversación local, humana o ya devuelta
     * @param usuarioId identidad autenticada de gestión
     * @throws NegocioException si falta membresía vigente o la conversación no es local
     */
    @Transactional
    public void devolver(long id, long usuarioId) {
        long negocioId = autorizacion.exigir(usuarioId, false);
        bloquear(id, negocioId);
        autorizacion.exigir(usuarioId, false);
        derivacion.devolver(id, OptionalLong.of(usuarioId));
    }

    private String bloquear(long id, long negocioId) {
        var filas = entidades.createNativeQuery(
                "SELECT modo FROM conversacion WHERE id=:id AND negocio_id=:negocio FOR UPDATE", String.class)
                .setParameter("id", id).setParameter("negocio", negocioId).getResultList();
        if (filas.isEmpty()) throw noEncontrado();
        return (String) filas.getFirst();
    }

    private boolean operativo(long negocioId) {
        return !entidades.createNativeQuery("""
                SELECT n.id FROM negocio n JOIN canal_whatsapp c ON c.negocio_id=n.id
                WHERE n.id=:negocio AND n.estado='ACTIVO' AND c.estado='ACTIVO'
                    AND EXISTS (SELECT 1 FROM membresia m JOIN usuario u ON u.id=m.usuario_id
                        WHERE m.negocio_id=n.id AND m.rol='PROPIETARIO' AND m.estado='ACTIVA' AND u.activo)
                """, Long.class).setParameter("negocio", negocioId).getResultList().isEmpty();
    }

    private ConversacionBandejaDto conversacion(Object[] f, boolean operativo) {
        Instant entrada = instante(f[5]);
        Instant respuesta = instante(f[6]);
        String wa = (String) f[2];
        String mascara = "•••…" + (wa.length() > 4 ? wa.substring(wa.length() - 3) : "");
        MotivoNoResponder motivo = !operativo ? MotivoNoResponder.NEGOCIO_NO_OPERATIVO
                : ventana.abierta(entrada, reloj.instant()) ? null : MotivoNoResponder.FUERA_DE_VENTANA;
        return new ConversacionBandejaDto(((Number) f[0]).longValue(), (String) f[1], mascara,
                MotivoDerivacion.valueOf((String) f[3]), lima(instante(f[4])), lima(entrada),
                lima(ventana.nominal(entrada)), entrada != null && (respuesta == null || entrada.isAfter(respuesta)),
                motivo == null, motivo);
    }

    private static String consultaMensajes() {
        return """
                SELECT m.id, m.direccion, m.origen, CAST(m.contenido AS text), m.estado,
                    m.error_codigo, m.creado_en, u.nombre
                FROM mensaje_whatsapp m LEFT JOIN usuario u ON u.id=m.enviado_por
                """;
    }

    private MensajeBandejaDto mensaje(Object[] f) {
        JsonNode contenido = mapper.readTree((String) f[3]);
        boolean retirado = contenido.path("retirado").asBoolean(false);
        OrigenMensaje origen = OrigenMensaje.valueOf((String) f[2]);
        return new MensajeBandejaDto(((Number) f[0]).longValue(), "ENTRANTE".equals(f[1]), origen,
                retirado ? null : textoVisible(contenido), retirado, EstadoMensaje.valueOf((String) f[4]),
                f[5] == null ? null : MotivoFallo.valueOf((String) f[5]), lima(instante(f[6])),
                origen == OrigenMensaje.PERSONA ? (String) f[7] : null);
    }

    private String textoVisible(JsonNode contenido) {
        if (contenido.has("texto")) return contenido.path("texto").asString();
        if (contenido.has("titulo")) return contenido.path("titulo").asString();
        if (contenido.has("interactivo")) {
            var interactivo = contenido.path("interactivo");
            StringBuilder texto = new StringBuilder(interactivo.path("body").path("text").asString());
            for (JsonNode boton : interactivo.path("action").path("buttons")) {
                texto.append(" [").append(boton.path("reply").path("title").asString()).append(']');
            }
            for (JsonNode seccion : interactivo.path("action").path("sections")) {
                for (JsonNode fila : seccion.path("rows")) texto.append(" [").append(fila.path("title").asString()).append(']');
            }
            return texto.toString();
        }
        if (contenido.has("plantilla")) {
            var plantilla = PlantillaUtility.porNombre(contenido.path("plantilla").asString());
            java.util.Map<String, String> parametros = new java.util.LinkedHashMap<>();
            plantilla.parametros().forEach(n -> parametros.put(n, contenido.path("parametros").path(n).asString()));
            return plantilla.textoEnVentana(parametros);
        }
        return contenido.has("tipo") ? "[" + contenido.path("tipo").asString() + "]" : "[Mensaje]";
    }

    private static Instant instante(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Instant instante) return instante;
        if (valor instanceof OffsetDateTime fecha) return fecha.toInstant();
        return ((Timestamp) valor).toInstant();
    }

    private static OffsetDateTime lima(Instant instante) { return instante == null ? null : TiempoNegocio.aLima(instante); }
    private static NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró la conversación.");
    }
}
