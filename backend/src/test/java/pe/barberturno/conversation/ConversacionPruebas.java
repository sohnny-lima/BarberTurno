package pe.barberturno.conversation;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.scheduling.Jornada;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.support.JdbcNegocioPruebas;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utilidades compartidas para los escenarios de integración de la conversación guiada (V2-10).
 * Facilita la creación e inserción de eventos entrantes de WhatsApp, la lectura y extracción
 * de identificadores de respuestas salientes deterministas, y el conteo de entidades bajo RLS.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
final class ConversacionPruebas {

    private static final AtomicLong SECUENCIA = new AtomicLong(1000);

    private ConversacionPruebas() { }

    /**
     * Genera un identificador wamid ficticio único.
     * @return identificador wamid
     */
    static String nuevoWamid() {
        return "wamid.ficticio." + SECUENCIA.incrementAndGet();
    }

    /**
     * Determina si un identificador de selección corresponde a un botón interactivo.
     * @param seleccionId identificador codificado
     * @return {@code true} si corresponde a un botón
     */
    static boolean esBoton(String seleccionId) {
        return seleccionId.startsWith("m:") || seleccionId.startsWith("p:")
                || seleccionId.startsWith("o:") || seleccionId.startsWith("a:");
    }

    /**
     * Construye el cuerpo JSON de un evento de mensaje de texto.
     * @param mapper mapeador JSON
     * @param phoneNumberId identificador del número receptor
     * @param waId identificador WhatsApp del cliente
     * @param nombrePerfil nombre de perfil del contacto
     * @param texto contenido del mensaje
     * @param wamid identificador del mensaje en Meta
     * @param ahora instante de recepción
     * @return árbol JSON del cuerpo del evento
     */
    static ObjectNode cuerpoTexto(JsonMapper mapper, String phoneNumberId, String waId,
            String nombrePerfil, String texto, String wamid, Instant ahora) {
        ObjectNode cuerpo = mapper.createObjectNode();
        cuerpo.put("phoneNumberId", phoneNumberId);
        ObjectNode contacto = cuerpo.putObject("contacto");
        contacto.put("waId", waId);
        if (nombrePerfil != null) {
            contacto.put("nombre", nombrePerfil);
        } else {
            contacto.putNull("nombre");
        }
        ObjectNode dato = cuerpo.putObject("dato");
        dato.put("id", wamid);
        dato.put("from", waId);
        dato.put("timestamp", String.valueOf(ahora.getEpochSecond()));
        dato.put("type", "text");
        dato.putObject("text").put("body", texto);
        return cuerpo;
    }

    /**
     * Construye el cuerpo JSON de un evento de mensaje interactivo (botón o fila de lista).
     * @param mapper mapeador JSON
     * @param phoneNumberId identificador del número receptor
     * @param waId identificador WhatsApp del cliente
     * @param nombrePerfil nombre de perfil del contacto
     * @param seleccionId identificador de la selección elegida
     * @param esBoton {@code true} si es {@code button_reply}, {@code false} si es {@code list_reply}
     * @param wamid identificador del mensaje en Meta
     * @param ahora instante de recepción
     * @return árbol JSON del cuerpo del evento
     */
    static ObjectNode cuerpoInteractivo(JsonMapper mapper, String phoneNumberId, String waId,
            String nombrePerfil, String seleccionId, boolean esBoton, String wamid, Instant ahora) {
        ObjectNode cuerpo = mapper.createObjectNode();
        cuerpo.put("phoneNumberId", phoneNumberId);
        ObjectNode contacto = cuerpo.putObject("contacto");
        contacto.put("waId", waId);
        if (nombrePerfil != null) {
            contacto.put("nombre", nombrePerfil);
        } else {
            contacto.putNull("nombre");
        }
        ObjectNode dato = cuerpo.putObject("dato");
        dato.put("id", wamid);
        dato.put("from", waId);
        dato.put("timestamp", String.valueOf(ahora.getEpochSecond()));
        dato.put("type", "interactive");
        ObjectNode interactive = dato.putObject("interactive");
        if (esBoton) {
            interactive.put("type", "button_reply");
            interactive.putObject("button_reply").put("id", seleccionId).put("title", "Opción");
        } else {
            interactive.put("type", "list_reply");
            interactive.putObject("list_reply").put("id", seleccionId).put("title", "Opción");
        }
        return cuerpo;
    }

    /**
     * Inserta un evento entrante en {@code evento_whatsapp_entrante} listo para ser reclamado.
     * @param sql acceso JDBC
     * @param mapper mapeador JSON
     * @param clave clave de idempotencia única
     * @param phoneNumberId identificador del número receptor
     * @param cuerpo árbol JSON del cuerpo
     * @param ahora instante del servidor
     * @return identificador asignado al evento
     */
    static long insertarEvento(JdbcNegocioPruebas sql, JsonMapper mapper, String clave,
            String phoneNumberId, JsonNode cuerpo, Instant ahora) {
        sql.update("""
                INSERT INTO evento_whatsapp_entrante(clave_idempotencia, tipo, phone_number_id, cuerpo, recibido_en, proximo_intento)
                VALUES (?, 'MENSAJE', ?, cast(? as jsonb), ?, ?)
                ON CONFLICT (clave_idempotencia) DO NOTHING
                """, clave, phoneNumberId, mapper.writeValueAsString(cuerpo),
                Timestamp.from(ahora), Timestamp.from(ahora));
        return sql.queryForObject("SELECT id FROM evento_whatsapp_entrante WHERE clave_idempotencia = ?", Long.class, clave);
    }

    /**
     * Proyección simplificada del último mensaje saliente de una conversación.
     * @param id identificador del mensaje en {@code mensaje_whatsapp}
     * @param conversacionId identificador de la conversación
     * @param reservaId identificador de reserva asociada o nulo
     * @param origen origen del mensaje ({@code ASISTENTE}, {@code SISTEMA}, etc.)
     * @param tipo tipo del mensaje ({@code INTERACTIVO}, {@code TEXTO}, etc.)
     * @param estado estado del mensaje ({@code PENDIENTE}, etc.)
     * @param texto cuerpo de texto legible del interactivo
     * @param idsSeleccion identificadores de botones o filas presentes en el interactivo
     * @param interactivo nodo JSON del interactivo
     */
    record MensajeSalientePrueba(
            long id,
            long conversacionId,
            Long reservaId,
            String origen,
            String tipo,
            String estado,
            String texto,
            List<String> idsSeleccion,
            JsonNode interactivo
    ) {
        /**
         * Obtiene el primer identificador que comience con el prefijo indicado.
         * @param prefijo prefijo buscado (p. ej. {@code "s:"}, {@code "b:"})
         * @return identificador coincidente
         * @throws IllegalStateException si no existe ningún identificador con ese prefijo
         */
        String primerIdQueEmpieceCon(String prefijo) {
            return idsSeleccion.stream()
                    .filter(id -> id.startsWith(prefijo))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontró id con prefijo '" + prefijo + "' en " + idsSeleccion));
        }

        /**
         * Obtiene el primer identificador que termine con el sufijo indicado.
         * @param sufijo sufijo buscado (p. ej. {@code ":S"})
         * @return identificador coincidente
         * @throws IllegalStateException si no existe ningún identificador con ese sufijo
         */
        String primerIdQueTermineCon(String sufijo) {
            return idsSeleccion.stream()
                    .filter(id -> id.endsWith(sufijo))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontró id con sufijo '" + sufijo + "' en " + idsSeleccion));
        }

        /**
         * Obtiene el identificador exacto si está presente.
         * @param idExacto identificador esperado (p. ej. {@code "m:RESERVAR"}, {@code "p:S"})
         * @return identificador encontrado
         * @throws IllegalStateException si no está presente
         */
        String idExacto(String idExacto) {
            return idsSeleccion.stream()
                    .filter(id -> id.equals(idExacto))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontró id exacto '" + idExacto + "' en " + idsSeleccion));
        }
    }

    /**
     * Consulta el último mensaje saliente de una conversación en un negocio bajo RLS.
     * @param ejecutor ejecutor de contexto de negocio
     * @param sql acceso JDBC
     * @param mapper mapeador JSON
     * @param negocioId identificador del negocio
     * @param waId identificador WhatsApp del cliente
     * @return mensaje proyectado o {@code null} si no existe
     */
    static MensajeSalientePrueba leerUltimoMensajeSaliente(EjecutorEnNegocio ejecutor,
            JdbcNegocioPruebas sql, JsonMapper mapper, long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var conversacionIds = sql.queryForList("SELECT id FROM conversacion WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            if (conversacionIds.isEmpty()) return null;
            long conversacionId = conversacionIds.getFirst();
            var filas = sql.queryForList("""
                    SELECT id, conversacion_id, reserva_id, origen, tipo, estado, contenido
                    FROM mensaje_whatsapp
                    WHERE negocio_id = ? AND conversacion_id = ? AND direccion = 'SALIENTE'
                    ORDER BY id DESC LIMIT 1
                    """, negocioId, conversacionId);
            if (filas.isEmpty()) return null;
            var fila = filas.getFirst();
            long id = ((Number) fila.get("id")).longValue();
            Long reservaId = fila.get("reserva_id") == null ? null : ((Number) fila.get("reserva_id")).longValue();
            String origen = (String) fila.get("origen");
            String tipo = (String) fila.get("tipo");
            String estado = (String) fila.get("estado");
            JsonNode contenido = mapper.readTree(fila.get("contenido").toString());
            JsonNode interactivo = contenido.path("interactivo");
            String texto = interactivo.path("body").path("text").asText("");
            List<String> ids = new ArrayList<>();
            JsonNode buttons = interactivo.path("action").path("buttons");
            if (buttons.isArray()) {
                for (JsonNode btn : buttons) {
                    String bid = btn.path("reply").path("id").asText(null);
                    if (bid != null) ids.add(bid);
                }
            }
            JsonNode sections = interactivo.path("action").path("sections");
            if (sections.isArray()) {
                for (JsonNode sec : sections) {
                    JsonNode rows = sec.path("rows");
                    if (rows.isArray()) {
                        for (JsonNode row : rows) {
                            String rid = row.path("id").asText(null);
                            if (rid != null) ids.add(rid);
                        }
                    }
                }
            }
            return new MensajeSalientePrueba(id, conversacionId, reservaId, origen, tipo, estado, texto, ids, interactivo);
        });
    }

    /**
     * Cuenta cuántas reservas existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @return total de reservas
     */
    static int contarReservas(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM reserva WHERE negocio_id = ?", Integer.class, negocioId));
    }

    /**
     * Cuenta cuántas reservas con un estado específico existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @param estado estado buscado
     * @return total de reservas con ese estado
     */
    static int contarReservas(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId, String estado) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM reserva WHERE negocio_id = ? AND estado = ?", Integer.class, negocioId, estado));
    }

    /**
     * Cuenta cuántas auditorías de reserva existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @return total de auditorías
     */
    static int contarAuditorias(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM auditoria_reserva WHERE negocio_id = ?", Integer.class, negocioId));
    }

    /**
     * Cuenta cuántos avisos o notificaciones existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @return total de notificaciones
     */
    static int contarNotificaciones(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM notificacion WHERE negocio_id = ?", Integer.class, negocioId));
    }

    /**
     * Cuenta cuántas fichas de cliente existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @return total de clientes
     */
    static int contarFichas(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM cliente WHERE negocio_id = ?", Integer.class, negocioId));
    }

    /**
     * Cuenta cuántas fichas de cliente con un {@code wa_id} específico existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @param waId identificador WhatsApp
     * @return total de clientes coincidentes
     */
    static int contarFichasConWa(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM cliente WHERE negocio_id = ? AND wa_id = ?", Integer.class, negocioId, waId));
    }

    /**
     * Cuenta cuántas operaciones pendientes existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @return total de operaciones
     */
    static int contarOperaciones(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM operacion_pendiente WHERE negocio_id = ?", Integer.class, negocioId));
    }

    /**
     * Cuenta cuántas operaciones pendientes con un estado específico existen en un negocio bajo RLS.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @param estado estado de la operación ({@code VIGENTE}, {@code EJECUTADA}, etc.)
     * @return total de operaciones coincidentes
     */
    static int contarOperaciones(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId, String estado) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM operacion_pendiente WHERE negocio_id = ? AND estado = ?", Integer.class, negocioId, estado));
    }

    /**
     * Cuenta cuántos mensajes salientes existen para una conversación específica.
     * @param ejecutor ejecutor de negocio
     * @param sql acceso JDBC
     * @param negocioId identificador del negocio
     * @param conversacionId identificador de la conversación
     * @return total de mensajes salientes
     */
    static int contarMensajesSalientes(EjecutorEnNegocio ejecutor, JdbcNegocioPruebas sql, long negocioId, long conversacionId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM mensaje_whatsapp WHERE negocio_id = ? AND conversacion_id = ? AND direccion = 'SALIENTE'",
                Integer.class, negocioId, conversacionId));
    }

    /**
     * Configura jornadas laborales semanales (lunes a domingo de 09:00 a 18:00) para un barbero.
     * @param ejecutor ejecutor de negocio
     * @param barberos repositorio de barberos
     * @param jornadas repositorio de jornadas
     * @param negocioId identificador del negocio
     * @param barberoId identificador del barbero
     */
    static void prepararJornadasSemana(EjecutorEnNegocio ejecutor, BarberoRepository barberos,
            JornadaRepository jornadas, long negocioId, long barberoId) {
        ejecutor.ejecutar(negocioId, () -> {
            Barbero b = barberos.findById(barberoId).orElseThrow();
            for (short dia = 1; dia <= 7; dia++) {
                jornadas.save(new Jornada(b, dia, LocalTime.of(9, 0), LocalTime.of(18, 0)));
            }
            return null;
        });
    }
}
