package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pe.barberturno.common.error.*;
import pe.barberturno.reservations.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propuestas y confirmaciones deterministas, atómicas con la entrega y los cambios RN-15.
 * Nunca registra el nonce ni contenido o identidades del remitente.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NegocioException.class)
public class OperacionesConversacionService implements OperacionesConversacion {
    private final OperacionPendienteRepository operaciones;
    private final ReservaService reservas;
    private final ConsultasConversacionService consultas;
    private final JsonMapper json;
    private final Clock reloj;
    private final ConversacionPropiedades propiedades;
    private final SecureRandom azar = new SecureRandom();

    /**
     * Comparte colaboradores dentro de la transacción de entrega, sin transacciones independientes.
     * @param operaciones persistencia acotada y bloqueos
     * @param reservas reglas y escrituras existentes por ficha
     * @param consultas proyecciones con permisos comunes
     * @param json serialización de precondiciones e instantáneas
     * @param reloj caducidad del servidor
     * @param propiedades vigencia configurable de la operación
     */
    public OperacionesConversacionService(OperacionPendienteRepository operaciones, ReservaService reservas,
            ConsultasConversacionService consultas, JsonMapper json, Clock reloj, ConversacionPropiedades propiedades) {
        this.operaciones = operaciones;
        this.reservas = reservas;
        this.consultas = consultas;
        this.json = json;
        this.reloj = reloj;
        this.propiedades = propiedades;
    }

    @Override
    public Propuesta proponerCreacion(long conversacionId, long fichaId, long servicioId, long barberoId, Instant inicio) {
        return proponer(conversacionId, fichaId, TipoOperacion.CREAR, 0, servicioId, barberoId, inicio);
    }

    @Override
    public Propuesta proponerReprogramacion(long conversacionId, long fichaId, long reservaId, Instant inicio) {
        return proponer(conversacionId, fichaId, TipoOperacion.REPROGRAMAR, reservaId, 0, 0, inicio);
    }

    @Override
    public Propuesta proponerCancelacion(long conversacionId, long fichaId, long reservaId) {
        return proponer(conversacionId, fichaId, TipoOperacion.CANCELAR, reservaId, 0, 0, null);
    }

    private Propuesta proponer(long conversacionId, long fichaId, TipoOperacion tipo, long reservaId,
            long servicioId, long barberoId, Instant inicio) {
        if (!operaciones.bloquearConversacion(conversacionId, fichaId)) return new Propuesta.Rechazada(ErrorCodigo.NO_ENCONTRADO);
        var vigente = operaciones.vigente(conversacionId); // antes de los bloqueos del dominio
        try {
            Reserva reserva;
            Optional<CitaOpcion> antes = Optional.empty();
            if (tipo == TipoOperacion.CREAR) {
                reserva = reservas.prepararCreacionFicha(fichaId, servicioId, barberoId, inicio);
            } else {
                var cita = consultas.cita(fichaId, reservaId).orElseThrow(() ->
                        new NegocioException(ErrorCodigo.NO_ENCONTRADO, "Cita no disponible."));
                reserva = reservas.prepararCambioFicha(fichaId, reservaId, cita.version(), inicio);
                antes = Optional.of(consultas.proyectarCita(reserva, fichaId));
            }
            Instant destino = inicio == null ? reserva.getInicio() : inicio;
            var datos = new Datos(reservaId, reserva.getServicio().getId(), reserva.getBarbero().getId(), destino,
                    reserva.getDuracionRefMin(), reserva.getPrecioRef(), reserva.getVersion());
            var resumen = new ResumenOperacion(tipo, reserva.getServicio().getNombre(), reserva.getBarbero().getNombreVisible(),
                    destino, destino.plusSeconds(datos.duracionMin * 60L), datos.duracionMin, datos.precio, antes);
            byte[] nonce = new byte[16];
            azar.nextBytes(nonce);
            Instant ahora = reloj.instant();
            vigente.ifPresent(o -> operaciones.terminar(o.id, "ANULADA", null, ahora));
            long id = operaciones.insertar(conversacionId, fichaId, tipo, json.writeValueAsString(datos), nonce,
                    ahora, ahora.plus(propiedades.vigenciaOperacion()));
            return new Propuesta.Creada(id, Base64.getUrlEncoder().withoutPadding().encodeToString(nonce), resumen);
        } catch (NegocioException rechazo) {
            return new Propuesta.Rechazada(rechazo.codigo());
        }
    }

    @Override
    public ResultadoOperacion confirmar(long conversacionId, long fichaId, long operacionId, String nonce) {
        if (!operaciones.bloquearConversacion(conversacionId, fichaId)) return noEncontrada();
        var operacion = operaciones.bloquear(operacionId);
        if (operacion.isEmpty() || !coincide(operacion.get(), conversacionId, fichaId, nonce)) return noEncontrada();
        return ejecutar(operacion.get());
    }

    @Override
    public ResultadoOperacion confirmarVigente(long conversacionId, long fichaId) {
        if (!operaciones.bloquearConversacion(conversacionId, fichaId)) return noEncontrada();
        return operaciones.vigente(conversacionId).filter(o -> o.fichaId == fichaId)
                .map(this::ejecutar).orElseGet(this::noEncontrada);
    }

    private ResultadoOperacion ejecutar(OperacionPendiente operacion) {
        var anterior = resultadoAnterior(operacion, true);
        if (anterior != null) return anterior;
        try {
            var d = json.readValue(operacion.datos, Datos.class);
            Reserva reserva;
            if (operacion.tipo == TipoOperacion.CREAR) {
                reserva = reservas.crearFicha(operacion.fichaId, d.servicioId, d.barberoId, d.inicio, d.duracionMin, d.precio);
            } else {
                var actual = reservas.prepararCambioFicha(operacion.fichaId, d.reservaId, d.version,
                        operacion.tipo == TipoOperacion.REPROGRAMAR ? d.inicio : null);
                if (actual.getServicio().getId() != d.servicioId || actual.getBarbero().getId() != d.barberoId
                        || actual.getDuracionRefMin() != d.duracionMin || actual.getPrecioRef().compareTo(d.precio) != 0) {
                    throw new NegocioException(ErrorCodigo.VERSION_DESACTUALIZADA, "La cita cambió. Solicite otro resumen.");
                }
                reserva = operacion.tipo == TipoOperacion.REPROGRAMAR
                        ? reservas.reprogramarFicha(operacion.fichaId, d.reservaId, d.version, d.inicio)
                        : reservas.cancelarFicha(operacion.fichaId, d.reservaId, d.version);
            }
            var cita = consultas.proyectarCita(reserva, operacion.fichaId);
            operaciones.terminar(operacion.id, "EJECUTADA",
                    json.writeValueAsString(new Instantanea(cita.reservaId(), cita.version(), cita)), reloj.instant());
            operaciones.vaciarConversacion(operacion.conversacionId, reloj.instant());
            return new ResultadoOperacion.Ejecutada(operacion.tipo, cita, false);
        } catch (NegocioException rechazo) {
            operaciones.terminar(operacion.id, "RECHAZADA", json.writeValueAsString(Map.of("codigo", rechazo.codigo())), reloj.instant());
            return new ResultadoOperacion.Rechazada(rechazo.codigo());
        }
    }

    private boolean coincide(OperacionPendiente o, long conversacionId, long fichaId, String nonce) {
        byte[] recibido;
        try {
            // Valida también la representación canónica para que un nonce tenga un único identificador.
            recibido = nonce != null && nonce.matches("[A-Za-z0-9_-]{22}") ? Base64.getUrlDecoder().decode(nonce) : new byte[0];
        } catch (IllegalArgumentException invalido) {
            recibido = new byte[0];
        }
        boolean igual = MessageDigest.isEqual(o.nonce, recibido);
        return igual && o.conversacionId == conversacionId && o.fichaId == fichaId
                && Base64.getUrlEncoder().withoutPadding().encodeToString(recibido).equals(nonce);
    }

    private ResultadoOperacion resultadoAnterior(OperacionPendiente o, boolean repetir) {
        if (o.estado.equals("EJECUTADA") && repetir) {
            return new ResultadoOperacion.Ejecutada(o.tipo, json.readValue(o.resultado, Instantanea.class).cita, true);
        }
        if (!o.estado.equals("VIGENTE")) {
            return new ResultadoOperacion.NoVigente(o.estado.equals("ANULADA") ? MotivoNoVigente.SUSTITUIDA : MotivoNoVigente.USADA);
        }
        if (!o.expira.isAfter(reloj.instant())) {
            operaciones.terminar(o.id, "CADUCADA", null, reloj.instant());
            return new ResultadoOperacion.NoVigente(MotivoNoVigente.CADUCADA);
        }
        return null;
    }

    @Override
    public ResultadoOperacion anular(long conversacionId, long fichaId, long operacionId, String nonce) {
        if (!operaciones.bloquearConversacion(conversacionId, fichaId)) return noEncontrada();
        var o = operaciones.bloquear(operacionId);
        if (o.isEmpty() || !coincide(o.get(), conversacionId, fichaId, nonce)) return noEncontrada();
        var anterior = resultadoAnterior(o.get(), false);
        if (anterior != null) return anterior;
        operaciones.terminar(operacionId, "ANULADA", null, reloj.instant());
        return new ResultadoOperacion.Anulada();
    }

    @Override
    public boolean anularVigente(long conversacionId) {
        if (!operaciones.bloquearConversacion(conversacionId, null)) return false;
        var o = operaciones.vigente(conversacionId);
        o.ifPresent(v -> operaciones.terminar(v.id, "ANULADA", null, reloj.instant()));
        return o.isPresent();
    }

    private ResultadoOperacion noEncontrada() { return new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA); }

    private record Datos(long reservaId, long servicioId, long barberoId, Instant inicio,
            int duracionMin, BigDecimal precio, int version) { }
    private record Instantanea(long reservaId, int version, CitaOpcion cita) { }
}
