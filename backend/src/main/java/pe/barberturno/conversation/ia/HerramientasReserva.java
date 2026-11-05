package pe.barberturno.conversation.ia;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.*;
import pe.barberturno.reservations.ReservaService;
import tools.jackson.databind.JsonNode;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Herramientas cerradas por esquema y capacidades del turno (CI-4). Cada consulta o validación
 * abre una transacción corta; ninguna crea reservas ni operaciones pendientes.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class HerramientasReserva {
    private final ConsultasConversacion consultas;
    private final ReservaService reservas;
    private final ProveedorNegocioActual negocio;
    private final JsonMapper json;
    private final JsonMapper argumentosEstrictos = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final TransactionTemplate tx;

    /**
     * Comparte las reglas de ficha y el contexto RLS de la aplicación.
     * @param consultas lecturas que verifican negocio y propiedad
     * @param reservas validador en seco de las reglas del dominio
     * @param negocio ámbito del hilo fijado por el trabajador
     * @param json mapper de la aplicación, separado del SDK
     * @param gestor transacciones cortas, con bloqueos de dominio cuando proceda
     */
    public HerramientasReserva(ConsultasConversacion consultas, ReservaService reservas,
            ProveedorNegocioActual negocio, JsonMapper json, PlatformTransactionManager gestor) {
        this.consultas = consultas; this.reservas = reservas; this.negocio = negocio;
        this.json = json; this.tx = new TransactionTemplate(gestor);
    }

    /**
     * Valida exactamente el esquema y resuelve solo alias conocidos de este contexto.
     * Una búsqueda concede alias solo tras consultar todo el rango y no emite efecto si está vacía.
     * @param contexto capacidades de un solo turno
     * @param llamada herramienta y JSON del proveedor
     * @return error genérico o resultado y efecto proyectados, sin entidades gestionadas
     */
    public EjecucionHerramienta ejecutar(ContextoTurno contexto, LlamadaHerramienta llamada) {
        try {
            if (negocio.negocioActual().orElseThrow() != contexto.negocioId) throw new IllegalArgumentException();
            JsonNode argumentos = argumentosEstrictos.readTree(llamada.argumentosJson());
            validar(argumentos, json.readTree(llamada.herramienta().esquemaJson()));
            return tx.execute(estado -> resolver(contexto, llamada, argumentos));
        } catch (RuntimeException rechazo) {
            // No refleja argumentos, mensajes SQL, nombres ajenos ni causas técnicas al proveedor.
            return error(llamada.id(), "NO_DISPONIBLE", "No se puede realizar esta consulta.");
        }
    }

    static EjecucionHerramienta error(String id, String codigo, String mensaje) {
        return new EjecucionHerramienta(new ResultadoHerramienta(id,
                "{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\"}", true), Optional.empty());
    }

    private void validar(JsonNode argumentos, JsonNode esquema) {
        if (!argumentos.isObject() || argumentos.size() != esquema.path("properties").size())
            throw new IllegalArgumentException();
        for (var campo : esquema.path("required")) {
            String nombre = campo.asString();
            var valor = argumentos.get(nombre);
            var propiedad = esquema.path("properties").path(nombre);
            if (valor == null) throw new IllegalArgumentException();
            boolean nulo = false;
            for (var tipo : propiedad.path("type")) if ("null".equals(tipo.asString())) nulo = true;
            if (!(valor.isString() || (valor.isNull() && nulo))) throw new IllegalArgumentException();
            if (propiedad.has("enum")) {
                boolean permitido = false;
                for (var opcion : propiedad.get("enum")) if (opcion.equals(valor)) permitido = true;
                if (!permitido) throw new IllegalArgumentException();
            }
        }
    }

    private EjecucionHerramienta resolver(ContextoTurno c, LlamadaHerramienta l, JsonNode a) {
        return switch (l.herramienta()) {
            case LISTAR_SERVICIOS -> {
                var lista = json.createArrayNode();
                for (var s : consultas.servicios()) lista.addObject().put("alias", c.servicio(s))
                        .put("nombre", limpio(s.nombre())).put("duracion", s.duracionMin()).put("precio", s.precio());
                yield ok(l, lista, null);
            }
            case LISTAR_PROFESIONALES -> {
                var lista = json.createArrayNode();
                // No hay relación servicio-profesional: basta un servicio activo para la consulta común.
                var servicios = consultas.servicios();
                if (!servicios.isEmpty()) for (var p : consultas.profesionales(servicios.getFirst().id()))
                    lista.addObject().put("alias", c.profesional(p)).put("nombre", limpio(p.nombre()));
                yield ok(l, lista, null);
            }
            case MIS_CITAS -> {
                var lista = json.createArrayNode();
                if (c.ficha.isPresent()) for (var cita : consultas.citasFuturas(c.ficha.getAsLong()))
                    lista.addObject().put("alias", c.cita(cita)).put("servicio", limpio(cita.servicio()))
                            .put("profesional", limpio(cita.profesional()))
                            .put("inicio", TiempoNegocio.aLima(cita.inicio()).toString())
                            .put("cancelar", cita.puedeCancelar()).put("reprogramar", cita.puedeReprogramar());
                yield ok(l, lista, null);
            }
            case BUSCAR_DISPONIBILIDAD -> disponibilidad(c, l, a);
            case PROPONER_RESERVA -> {
                var f = Objects.requireNonNull(c.franjas.get(texto(a, "franja")));
                if (f.citaId() != null) throw new IllegalArgumentException();
                EfectoTurno efecto;
                if (c.ficha.isPresent()) {
                    reservas.prepararCreacionFicha(c.ficha.getAsLong(), f.servicioId(), f.barberoId(), f.ofrecida().inicio());
                    efecto = new EfectoTurno.ProponerReserva(f.servicioId(), f.barberoId(), f.ofrecida().inicio());
                } else efecto = new EfectoTurno.SolicitarConsentimientoReserva(f.servicioId(), f.barberoId(), f.ofrecida().inicio());
                yield propuesta(l, efecto);
            }
            case PROPONER_REPROGRAMACION, PROPONER_CANCELACION -> {
                var cita = Objects.requireNonNull(c.citas.get(texto(a, "cita")));
                long ficha = c.ficha.orElseThrow();
                Instant inicio = null;
                if (l.herramienta() == HerramientaIA.PROPONER_REPROGRAMACION) {
                    var f = Objects.requireNonNull(c.franjas.get(texto(a, "franja")));
                    if (!Objects.equals(f.citaId(), cita.reservaId())) throw new IllegalArgumentException();
                    inicio = f.ofrecida().inicio();
                }
                reservas.prepararCambioFicha(ficha, cita.reservaId(), cita.version(), inicio);
                yield propuesta(l, inicio == null ? new EfectoTurno.ProponerCancelacion(cita.reservaId())
                        : new EfectoTurno.ProponerReprogramacion(cita.reservaId(), inicio));
            }
            case DERIVAR_A_PERSONA -> {
                String motivo = texto(a, "motivo");
                var m = switch (motivo) {
                    case "SIN_AVANCE" -> MotivoDerivacion.SIN_PROGRESO;
                    case "PETICION" -> MotivoDerivacion.PETICION;
                    default -> MotivoDerivacion.HERRAMIENTA;
                };
                yield propuesta(l, c.ficha.isPresent() ? new EfectoTurno.Derivar(m)
                        : new EfectoTurno.SolicitarConsentimientoDerivacion(m));
            }
        };
    }

    private EjecucionHerramienta disponibilidad(ContextoTurno c, LlamadaHerramienta l, JsonNode a) {
        LocalDate desde = fecha(texto(a, "desde")), hasta = fecha(texto(a, "hasta"));
        if (desde.isBefore(c.hoy) || hasta.isBefore(desde) || hasta.isAfter(desde.plusDays(6)))
            throw new IllegalArgumentException();
        LocalTime hDesde = hora(texto(a, "horaDesde")), hHasta = hora(texto(a, "horaHasta"));
        if (hDesde != null && hHasta != null && hDesde.isAfter(hHasta)) throw new IllegalArgumentException();
        String aliasCita = texto(a, "cita");
        CitaOpcion cita = aliasCita == null ? null : Objects.requireNonNull(c.citas.get(aliasCita));
        long servicio = cita == null ? Objects.requireNonNull(c.servicios.get(texto(a, "servicio"))).id() : cita.servicioId();
        String aliasProfesional = texto(a, "profesional");
        OptionalLong profesional = cita != null ? OptionalLong.of(cita.barberoId()) : aliasProfesional == null
                ? OptionalLong.empty() : OptionalLong.of(Objects.requireNonNull(c.profesionales.get(aliasProfesional)).id());
        var candidatas = new ArrayList<FranjaOpcion>();
        // Consulta todo el rango antes de conceder capacidades: un fallo posterior no deja alias.
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            var opciones = cita == null ? consultas.franjas(servicio, profesional, dia)
                    : consultas.franjasCambio(c.ficha.orElseThrow(), cita.reservaId(), dia);
            for (var f : opciones) {
                LocalTime hora = TiempoNegocio.aLima(f.inicio()).toLocalTime();
                if ((hDesde == null || !hora.isBefore(hDesde)) && (hHasta == null || !hora.isAfter(hHasta))) {
                    if (candidatas.size() < 20) candidatas.add(f);
                }
            }
        }
        var ofrecidas = candidatas.stream()
                .map(f -> c.franja(servicio, cita == null ? null : cita.reservaId(), f)).toList();
        c.servicioBorrador = servicio;
        c.profesionalBorrador = profesional.isPresent() ? profesional.getAsLong() : null;
        c.diaBorrador = desde;
        var lista = json.createArrayNode();
        for (var f : ofrecidas) lista.addObject().put("alias", f.alias())
                .put("inicio", TiempoNegocio.aLima(f.inicio()).toString())
                .put("fin", TiempoNegocio.aLima(f.fin()).toString()).put("profesional", limpio(f.profesional()));
        return ok(l, lista, ofrecidas.isEmpty() ? null : new EfectoTurno.MostrarFranjas(ofrecidas));
    }

    private static LocalDate fecha(String valor) {
        if (valor == null || !valor.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
        return LocalDate.parse(valor);
    }

    private static LocalTime hora(String valor) {
        if (valor == null) return null;
        if (!valor.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException();
        return LocalTime.parse(valor);
    }

    private static String texto(JsonNode nodo, String campo) {
        var valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }

    static String limpio(String valor) {
        return SaneadorTexto.sanear(valor, 2000).replaceAll("(?i)\\bBT-\\d+\\b", "[dato]");
    }

    private EjecucionHerramienta propuesta(LlamadaHerramienta l, EfectoTurno efecto) {
        ObjectNode resultado = json.createObjectNode().put("mensaje", "El servidor enviará el resumen para que lo confirme.");
        return ok(l, resultado, efecto);
    }

    private EjecucionHerramienta ok(LlamadaHerramienta l, JsonNode resultado, EfectoTurno efecto) {
        return new EjecucionHerramienta(new ResultadoHerramienta(l.id(), json.writeValueAsString(resultado), false),
                Optional.ofNullable(efecto));
    }
}
