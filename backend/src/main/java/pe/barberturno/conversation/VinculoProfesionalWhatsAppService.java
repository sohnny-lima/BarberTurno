package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.whatsapp.CodigoVinculoWhatsApp;
import pe.barberturno.whatsapp.MensajeEntrante;
import pe.barberturno.whatsapp.dto.CodigoVinculoProfesionalDto;
import pe.barberturno.whatsapp.dto.EstadoVinculoProfesionalDto;

/**
 * Capacidades de un uso y lectura de agenda profesional con aislamiento RLS (V2-DA-106).
 * Cada comando revalida perfil, cuenta y membresía del negocio del canal. Las modificaciones del
 * vínculo se serializan por negocio; nunca se consulta una agenda a partir de un identificador externo.
 * El límite de fallos vive en esta instancia y no registra las capacidades ni las identidades.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
@Transactional
public class VinculoProfesionalWhatsAppService {
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String SIN_ACCESO = "Este WhatsApp no tiene acceso a una agenda de esta barbería.";
    private static final String CODIGO_INVALIDO = "El código no es válido o caducó. Genera uno nuevo desde tu cuenta de BarberTurno.";
    private static final String LIMITE = "Demasiados intentos. Vuelve a intentarlo más tarde.";
    private final EntityManager entidades;
    private final ProveedorNegocioActual negocio;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();
    private final Map<Persona, Deque<Instant>> fallos = new HashMap<>();

    VinculoProfesionalWhatsAppService(EntityManager entidades, ProveedorNegocioActual negocio, Clock reloj) {
        this.entidades = entidades; this.negocio = negocio; this.reloj = reloj;
    }

    /**
     * Genera una capacidad de diez minutos e invalida la anterior sin usar, bajo el mismo bloqueo.
     * @param usuarioId cuenta autenticada, nunca el perfil elegido por el navegador
     * @return código que solo se muestra en esta respuesta y su caducidad
     * @throws NegocioException NO_ENCONTRADO si no hay perfil, cuenta y membresía activos
     */
    public CodigoVinculoProfesionalDto generar(long usuarioId) {
        bloquear();
        long barbero = perfil(usuarioId);
        entidades.createNativeQuery("DELETE FROM codigo_vinculo_whatsapp WHERE barbero_id=:barbero AND usado_en IS NULL")
                .setParameter("barbero", barbero).executeUpdate();
        var codigo = new StringBuilder(8);
        for (int i = 0; i < 8; i++) codigo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        Instant ahora = reloj.instant();
        Instant caduca = ahora.plus(Duration.ofMinutes(10));
        entidades.createNativeQuery("""
                INSERT INTO codigo_vinculo_whatsapp(barbero_id, codigo_hash, caduca_en, creado_en)
                VALUES (:barbero, :hash, :caduca, :ahora)
                """).setParameter("barbero", barbero).setParameter("hash", CodigoVinculoWhatsApp.hash(codigo.toString()))
                .setParameter("caduca", caduca).setParameter("ahora", ahora).executeUpdate();
        return new CodigoVinculoProfesionalDto(codigo.substring(0, 4) + "-" + codigo.substring(4), caduca);
    }

    /**
     * Lee solo el estado del profesional de la cuenta; jamás proyecta el identificador de WhatsApp.
     * @param usuarioId cuenta autenticada
     * @return estado y fecha del vínculo local
     * @throws NegocioException NO_ENCONTRADO si el perfil o la membresía no están activos
     */
    @Transactional(readOnly = true)
    public EstadoVinculoProfesionalDto estado(long usuarioId) {
        long barbero = perfil(usuarioId);
        var filas = entidades.createNativeQuery("SELECT creado_en FROM vinculo_whatsapp_profesional WHERE barbero_id=:barbero", Instant.class)
                .setParameter("barbero", barbero).getResultList();
        return new EstadoVinculoProfesionalDto(!filas.isEmpty(), filas.isEmpty() ? null : (Instant) filas.getFirst());
    }

    /**
     * Retira el vínculo y las capacidades sin usar para evitar que una baja se revierta con un código anterior.
     * @param usuarioId cuenta autenticada propietaria del perfil
     * @throws NegocioException NO_ENCONTRADO si el perfil o la membresía no están activos
     */
    public void desvincular(long usuarioId) {
        bloquear();
        long barbero = perfil(usuarioId);
        borrarPerfil(barbero);
        entidades.createNativeQuery("DELETE FROM codigo_vinculo_whatsapp WHERE barbero_id=:barbero AND usado_en IS NULL")
                .setParameter("barbero", barbero).executeUpdate();
    }

    /**
     * Atiende comandos profesionales antes del flujo de cliente y sin crear fichas ni aceptar su privacidad.
     * @param mensaje entrada con el contexto del negocio resuelto por el canal y conversación bloqueada
     * @return respuesta si es un comando profesional, o vacío para seguir el flujo de cliente
     * @throws IllegalStateException si el canal y el contexto de negocio no coinciden
     */
    public Optional<String> atender(MensajeEntrante mensaje) {
        if (mensaje.negocioId() != negocio.negocioActual().orElseThrow()) {
            throw new IllegalStateException("El comando no corresponde al negocio actual.");
        }
        if (!"text".equals(mensaje.tipo())) return Optional.empty();
        String texto = mensaje.dato().path("text").path("body").asString("").strip()
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (texto.startsWith("vincular")) {
            String hash = mensaje.dato().path("bt_codigo_vinculo_hash").asString("");
            if (!hash.matches("[0-9a-f]{64}")) {
                hash = CodigoVinculoWhatsApp.hash(texto.length() > 8 ? texto.substring(8).strip() : "");
            }
            return Optional.of(vincular(mensaje.waId(), hash));
        }
        if (texto.equals("desvincular")) {
            bloquear();
            entidades.createNativeQuery("DELETE FROM vinculo_whatsapp_profesional WHERE wa_id=:wa")
                    .setParameter("wa", mensaje.waId()).executeUpdate();
            return Optional.of("Este WhatsApp quedó desvinculado de tu agenda en esta barbería.");
        }
        if (texto.equals("agenda hoy") || texto.equals("agenda mañana") || texto.equals("agenda manana")) {
            return Optional.of(agenda(mensaje.waId(), !texto.equals("agenda hoy")));
        }
        return Optional.empty();
    }

    private String vincular(String waId, String hash) {
        Persona persona = new Persona(negocio.negocioActual().orElseThrow(), waId);
        if (limitada(persona, false)) return LIMITE;
        bloquear();
        var filas = entidades.createNativeQuery("""
                SELECT c.barbero_id, b.nombre_visible FROM codigo_vinculo_whatsapp c
                JOIN barbero b ON b.negocio_id=c.negocio_id AND b.id=c.barbero_id
                JOIN membresia m ON m.negocio_id=b.negocio_id AND m.usuario_id=b.usuario_id
                JOIN usuario u ON u.id=b.usuario_id
                WHERE c.codigo_hash=:hash AND c.usado_en IS NULL AND c.caduca_en>:ahora
                    AND b.activo AND m.estado='ACTIVA' AND u.activo
                """).setParameter("hash", hash).setParameter("ahora", reloj.instant()).getResultList();
        if (filas.isEmpty()) return limitada(persona, true) ? LIMITE : CODIGO_INVALIDO;
        Object[] fila = (Object[]) filas.getFirst();
        long barbero = ((Number) fila[0]).longValue();
        entidades.createNativeQuery("DELETE FROM vinculo_whatsapp_profesional WHERE barbero_id=:barbero OR wa_id=:wa")
                .setParameter("barbero", barbero).setParameter("wa", waId).executeUpdate();
        entidades.createNativeQuery("""
                INSERT INTO vinculo_whatsapp_profesional(barbero_id, wa_id, creado_en) VALUES (:barbero, :wa, :ahora)
                """).setParameter("barbero", barbero).setParameter("wa", waId).setParameter("ahora", reloj.instant()).executeUpdate();
        entidades.createNativeQuery("UPDATE codigo_vinculo_whatsapp SET usado_en=:ahora WHERE codigo_hash=:hash")
                .setParameter("ahora", reloj.instant()).setParameter("hash", hash).executeUpdate();
        return "Listo, " + fila[1] + ": este WhatsApp quedó vinculado a tu agenda en " + nombreNegocio()
                + ". Escribe «agenda hoy» o «agenda mañana».";
    }

    private String agenda(String waId, boolean manana) {
        bloquear();
        // Revalidación completa en CADA consulta, sin usar permisos guardados al vincular.
        var perfiles = entidades.createNativeQuery("""
                SELECT v.barbero_id FROM vinculo_whatsapp_profesional v
                JOIN barbero b ON b.negocio_id=v.negocio_id AND b.id=v.barbero_id
                JOIN membresia m ON m.negocio_id=b.negocio_id AND m.usuario_id=b.usuario_id
                JOIN usuario u ON u.id=b.usuario_id
                WHERE v.wa_id=:wa AND b.activo AND m.estado='ACTIVA' AND u.activo
                """, Long.class).setParameter("wa", waId).getResultList();
        if (perfiles.isEmpty()) {
            entidades.createNativeQuery("DELETE FROM vinculo_whatsapp_profesional WHERE wa_id=:wa")
                    .setParameter("wa", waId).executeUpdate();
            return SIN_ACCESO;
        }
        var dia = TiempoNegocio.fechaLima(reloj.instant()).plusDays(manana ? 1 : 0);
        var citas = entidades.createNativeQuery("""
                SELECT r.inicio, s.nombre, c.nombre, count(*) OVER () FROM reserva r
                JOIN servicio s ON s.negocio_id=r.negocio_id AND s.id=r.servicio_id
                JOIN cliente c ON c.negocio_id=r.negocio_id AND c.id=r.cliente_id
                JOIN barbero b ON b.negocio_id=r.negocio_id AND b.id=r.barbero_id
                JOIN vinculo_whatsapp_profesional v ON v.negocio_id=b.negocio_id AND v.barbero_id=b.id
                JOIN membresia m ON m.negocio_id=b.negocio_id AND m.usuario_id=b.usuario_id
                JOIN usuario u ON u.id=b.usuario_id
                WHERE r.negocio_id=:negocio AND r.barbero_id=:barbero AND r.inicio>=:desde AND r.inicio<:hasta
                    AND v.wa_id=:wa AND b.activo AND m.estado='ACTIVA' AND u.activo
                    AND r.estado IN ('PENDIENTE','CONFIRMADA') ORDER BY r.inicio, r.id LIMIT 25
                """).setParameter("negocio", negocio.negocioActual().orElseThrow())
                .setParameter("wa", waId)
                .setParameter("barbero", ((Number) perfiles.getFirst()).longValue())
                .setParameter("desde", TiempoNegocio.inicioDelDia(dia)).setParameter("hasta", TiempoNegocio.finDelDia(dia))
                .getResultList();
        String cuando = manana ? "mañana" : "hoy";
        var texto = new StringBuilder("Tu agenda de " + cuando + ", "
                + dia.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("es-PE")))
                + " (" + nombreNegocio() + "):");
        if (citas.isEmpty()) return texto + "\nNo tienes citas para " + cuando + ".";
        for (Object objeto : citas) {
            Object[] fila = (Object[]) objeto;
            Instant inicio = switch (fila[0]) {
                case Instant instante -> instante;
                case OffsetDateTime desplazado -> desplazado.toInstant();
                case Timestamp sello -> sello.toInstant();
                default -> throw new IllegalStateException("Formato temporal de agenda no admitido.");
            };
            texto.append('\n').append(TiempoNegocio.aLima(inicio).format(DateTimeFormatter.ofPattern("HH:mm")))
                    .append(" · ").append(fila[1]).append(" · ").append(nombreBreve((String) fila[2]));
        }
        long total = ((Number) ((Object[]) citas.getFirst())[3]).longValue();
        if (total > 25) texto.append("\ny ").append(total - 25).append(" más: revisa la agenda web");
        return texto.toString();
    }

    private long perfil(long usuarioId) {
        var filas = entidades.createNativeQuery("""
                SELECT b.id FROM barbero b JOIN membresia m ON m.negocio_id=b.negocio_id AND m.usuario_id=b.usuario_id
                JOIN usuario u ON u.id=b.usuario_id WHERE b.usuario_id=:usuario AND b.activo AND u.activo
                    AND m.estado='ACTIVA' AND b.negocio_id=:negocio
                """, Long.class).setParameter("usuario", usuarioId)
                .setParameter("negocio", negocio.negocioActual().orElseThrow()).getResultList();
        if (filas.isEmpty()) throw new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el perfil profesional.");
        return ((Number) filas.getFirst()).longValue();
    }

    private void bloquear() {
        entidades.createNativeQuery("SELECT pg_advisory_xact_lock(106106, CAST(:negocio AS integer))")
                .setParameter("negocio", negocio.negocioActual().orElseThrow()).getSingleResult();
    }

    private void borrarPerfil(long barbero) {
        entidades.createNativeQuery("DELETE FROM vinculo_whatsapp_profesional WHERE barbero_id=:barbero")
                .setParameter("barbero", barbero).executeUpdate();
    }

    private String nombreNegocio() {
        return (String) entidades.createNativeQuery("SELECT nombre FROM negocio WHERE id=:negocio", String.class)
                .setParameter("negocio", negocio.negocioActual().orElseThrow()).getSingleResult();
    }

    private static String nombreBreve(String nombre) {
        String[] partes = nombre.strip().split("\\s+");
        return partes[0] + (partes.length > 1 ? " " + new String(Character.toChars(partes[1].codePointAt(0))) + "." : "");
    }

    private synchronized boolean limitada(Persona persona, boolean fallo) {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(Duration.ofHours(1));
        fallos.entrySet().removeIf(e -> {
            e.getValue().removeIf(fecha -> !fecha.isAfter(limite));
            return e.getValue().isEmpty();
        });
        Deque<Instant> lista = fallos.computeIfAbsent(persona, clave -> new ArrayDeque<>());
        if (fallo && lista.size() < 5) lista.addLast(ahora);
        return lista.size() >= 5;
    }

    private record Persona(long negocioId, String waId) { }
}
