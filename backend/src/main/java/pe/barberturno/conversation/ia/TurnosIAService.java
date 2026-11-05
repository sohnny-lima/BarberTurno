package pe.barberturno.conversation.ia;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.*;
import pe.barberturno.whatsapp.*;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cola con ficha de reclamo y aplicación atómica de un solo efecto (V2-DA-77/78).
 * Nunca conserva una conexión ni un bloqueo durante el HTTP. La revisión, el modo y la ficha
 * se verifican nuevamente bajo bloqueo antes de cada llamada y de cualquier salida.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class TurnosIAService implements TurnosIA {
    private static final Logger LOG = LoggerFactory.getLogger(TurnosIAService.class);
    private final EntityManager em;
    private final TransactionTemplate tx;
    private final EjecutorEnNegocio ejecutor;
    private final CanalWhatsAppRepository canales;
    private final ConversacionesWhatsApp conversaciones;
    private final RegistroEntradaWhatsApp entradas;
    private final OperacionesConversacion operaciones;
    private final ConsultasConversacion consultas;
    private final DerivacionConversacion derivacion;
    private final SalidaWhatsApp salida;
    private final MenusConversacion menus;
    private final TextosConversacion textos;
    private final ConversacionPropiedades conversacionPropiedades;
    private final IaPropiedades propiedades;
    private final ConstructorContextoModelo constructor;
    private final HerramientasReserva herramientas;
    private final LimitesIAService limites;
    private final ModeloConversacional modelo;
    private final GuardasSalida guardas;
    private final ComposicionTurnoIA composicion;
    private final InstruccionesAsistente instrucciones;
    private final ObservadorEvaluacion observador;
    private final Clock reloj;

    TurnosIAService(EntityManager em, PlatformTransactionManager gestor, EjecutorEnNegocio ejecutor,
            CanalWhatsAppRepository canales, ConversacionesWhatsApp conversaciones, RegistroEntradaWhatsApp entradas,
            OperacionesConversacion operaciones, ConsultasConversacion consultas, DerivacionConversacion derivacion,
            SalidaWhatsApp salida, MenusConversacion menus, TextosConversacion textos,
            ConversacionPropiedades conversacionPropiedades, IaPropiedades propiedades, ConstructorContextoModelo constructor,
            HerramientasReserva herramientas, LimitesIAService limites, ModeloConversacional modelo,
            GuardasSalida guardas, ComposicionTurnoIA composicion, InstruccionesAsistente instrucciones,
            ObservadorEvaluacion observador, Clock reloj) {
        this.em = em; this.tx = new TransactionTemplate(gestor); this.ejecutor = ejecutor; this.canales = canales;
        this.conversaciones = conversaciones; this.entradas = entradas; this.operaciones = operaciones;
        this.consultas = consultas; this.derivacion = derivacion; this.salida = salida; this.menus = menus;
        this.textos = textos; this.conversacionPropiedades = conversacionPropiedades; this.propiedades = propiedades;
        this.constructor = constructor; this.herramientas = herramientas; this.limites = limites; this.modelo = modelo;
        this.guardas = guardas; this.composicion = composicion; this.instrucciones = instrucciones;
        this.observador = observador; this.reloj = reloj;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long encolar(long conversacionId, long revision, String texto) {
        em.createNativeQuery("""
                UPDATE turno_ia SET estado='DESCARTADO', texto=NULL, motivo='NUEVA_ENTRADA', actualizado_en=:ahora
                WHERE conversacion_id=:id AND estado='PENDIENTE'
                """).setParameter("id", conversacionId).setParameter("ahora", reloj.instant()).executeUpdate();
        return ((Number) em.createNativeQuery("""
                INSERT INTO turno_ia(conversacion_id, revision, texto, creado_en, actualizado_en)
                VALUES (:id, :revision, :texto, :ahora, :ahora) RETURNING id
                """).setParameter("id", conversacionId).setParameter("revision", revision)
                .setParameter("texto", SaneadorTexto.sanear(texto, 2000)).setParameter("ahora", reloj.instant())
                .getSingleResult()).longValue();
    }

    /** {@inheritDoc} */
    @Override
    public int procesarPendientes() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("El trabajador de IA exige invocarse fuera de una transacción.");
        int total = 0;
        var negocios = tx.execute(estado -> canales.listarNegociosConCanal());
        for (long negocio : negocios) {
            try {
                total += ejecutor.ejecutar(negocio, () -> procesarNegocio(negocio));
            } catch (RuntimeException fallo) {
                LOG.warn("Trabajador IA del negocio {}: fallo técnico; se reintentará con una nueva ficha.", negocio);
            }
        }
        return total;
    }

    private int procesarNegocio(long negocio) {
        int terminados = 0;
        for (int i = 0; i < propiedades.trabajador().lote(); i++) {
            Reclamo reclamo = tx.execute(estado -> reclamar(negocio));
            if (reclamo == null) break;
            resolver(reclamo);
            boolean terminado = tx.execute(estado -> em.createNativeQuery(
                    "SELECT id FROM turno_ia WHERE id=:id AND estado='PENDIENTE'")
                    .setParameter("id", reclamo.id()).getResultList().isEmpty());
            if (terminado) terminados++;
        }
        return terminados;
    }

    private Reclamo reclamar(long negocio) {
        var filas = em.createNativeQuery("""
                SELECT t.id, t.conversacion_id, t.revision, t.texto, t.intentos, c.cliente_id
                FROM turno_ia t JOIN conversacion c ON c.id=t.conversacion_id AND c.negocio_id=t.negocio_id
                WHERE t.estado='PENDIENTE' AND t.intentos < 3
                    AND t.creado_en > :limite
                    AND (t.reclamado_hasta IS NULL OR t.reclamado_hasta <= :ahora)
                ORDER BY t.id LIMIT 1 FOR UPDATE OF t SKIP LOCKED
                """, Object[].class).setParameter("ahora", reloj.instant())
                .setParameter("limite", reloj.instant().minusSeconds(900)).getResultList();
        if (filas.isEmpty()) return null;
        Object[] f = (Object[]) filas.getFirst();
        long id = ((Number) f[0]).longValue();
        int ficha = ((Number) f[4]).intValue() + 1;
        em.createNativeQuery("""
                UPDATE turno_ia SET intentos=:ficha, reclamado_hasta=:hasta, actualizado_en=:ahora WHERE id=:id
                """).setParameter("id", id).setParameter("ficha", ficha).setParameter("hasta", reloj.instant().plusSeconds(120))
                .setParameter("ahora", reloj.instant()).executeUpdate();
        return new Reclamo(id, negocio, ((Number) f[1]).longValue(), ((Number) f[2]).longValue(), (String) f[3], ficha,
                f[5] == null ? OptionalLong.empty() : OptionalLong.of(((Number) f[5]).longValue()), reloj.instant(), System.nanoTime());
    }

    private void resolver(Reclamo r) {
        var c = new ContextoTurno(r.negocio(), r.cliente(), TiempoNegocio.fechaLima(reloj.instant()));
        var efectos = new ArrayList<EfectoTurno>();
        var mensajes = new ArrayList<MensajeModelo>();
        mensajes.add(new MensajeModelo.DelCliente(HerramientasReserva.limpio(r.texto())));
        String texto = "";
        try {
            String contexto = constructor.construir(r.conversacion(), c);
            for (int numero = 1; numero <= Math.min(4, propiedades.maxLlamadas()); numero++) {
                if (agotado(r, propiedades.tiempoLlamada())) throw fallo(MotivoFalloModelo.TIEMPO_AGOTADO);
                var peticion = new PeticionModelo(instrucciones.texto(), contexto, mensajes, List.of(HerramientaIA.values()));
                int n = numero;
                var reserva = tx.execute(estado -> {
                    if (!validar(r, true)) return Optional.<LimitesIAService.ReservaLlamada>empty();
                    return limites.reservar(r.id(), n, peticion);
                });
                if (reserva.isEmpty()) {
                    finalizar(r, c, texto, efectos, "LIMITE");
                    return;
                }
                RespuestaModelo respuesta;
                try {
                    // Punto único de HTTP; la reserva anterior ya está confirmada y no quedan bloqueos.
                    respuesta = modelo.responder(peticion);
                    limites.liquidar(reserva.get(), respuesta.uso());
                } catch (FalloModelo fallo) {
                    fallo.uso().ifPresent(uso -> limites.liquidar(reserva.get(), uso));
                    throw fallo;
                }
                observador.llamada(r.id(), respuesta);
                if ("refusal".equals(respuesta.parada())) throw fallo(MotivoFalloModelo.RECHAZO);
                if ("max_tokens".equals(respuesta.parada())) throw fallo(MotivoFalloModelo.SALIDA_INVALIDA);
                texto = respuesta.texto().orElse("");
                if (respuesta.llamadas().isEmpty()) {
                    finalizar(r, c, texto, efectos, null);
                    return;
                }
                var resultados = new ArrayList<ResultadoHerramienta>();
                boolean derivar = false;
                int herramientasRespuesta = 0;
                for (var llamada : respuesta.llamadas()) {
                    if (agotado(r, Duration.ZERO)) throw fallo(MotivoFalloModelo.TIEMPO_AGOTADO);
                    var ejecucion = ++herramientasRespuesta <= Math.min(4, propiedades.maxHerramientas())
                            ? herramientas.ejecutar(c, llamada)
                            : HerramientasReserva.error(llamada.id(), "LIMITE", "Demasiadas consultas.");
                    resultados.add(ejecucion.paraModelo());
                    if (ejecucion.efecto().isPresent()) {
                        var efecto = ejecucion.efecto().get();
                        efectos.add(efecto);
                        if (efecto instanceof EfectoTurno.Derivar || efecto instanceof EfectoTurno.SolicitarConsentimientoDerivacion)
                            derivar = true;
                    }
                    var ofrecidas = ejecucion.efecto().filter(EfectoTurno.MostrarFranjas.class::isInstance)
                            .map(EfectoTurno.MostrarFranjas.class::cast).map(EfectoTurno.MostrarFranjas::franjas).orElse(List.of());
                    observador.herramienta(r.id(), llamada, ejecucion.paraModelo(), ofrecidas);
                    if (derivar) break;
                }
                if (derivar) {
                    finalizar(r, c, texto, efectos, null);
                    return;
                }
                mensajes.add(new MensajeModelo.DelAsistente(respuesta.texto(), respuesta.llamadas(), respuesta.continuacion()));
                mensajes.add(new MensajeModelo.ResultadosHerramientas(resultados));
            }
            finalizar(r, c, texto, efectos, "MAX_LLAMADAS");
        } catch (FalloModelo fallo) {
            finalizar(r, c, texto, efectos, fallo.motivo().name());
        } catch (RuntimeException fallo) {
            LOG.warn("Turno IA: fallo técnico; se intenta cerrar con el menú.");
            finalizar(r, c, texto, efectos, "TECNICO");
        }
    }

    private boolean agotado(Reclamo r, Duration margen) {
        Duration plazo = propiedades.plazoTurno().compareTo(Duration.ofSeconds(90)) > 0 ? Duration.ofSeconds(90) : propiedades.plazoTurno();
        return !reloj.instant().plus(margen).isBefore(r.inicio().plus(plazo))
                || System.nanoTime() - r.nanoInicio() + margen.toNanos() >= plazo.toNanos();
    }

    private boolean validar(Reclamo r, boolean renovar) {
        // Orden común de aplicación: conversación → turno → operación → protocolo del dominio.
        var conversacionesActuales = em.createNativeQuery("""
                SELECT revision, modo, cliente_id FROM conversacion WHERE id=:id FOR UPDATE
                """, Object[].class).setParameter("id", r.conversacion()).getResultList();
        var turnos = em.createNativeQuery("""
                SELECT id FROM turno_ia WHERE id=:id AND estado='PENDIENTE' AND intentos=:ficha
                    AND reclamado_hasta > :ahora FOR UPDATE
                """, Long.class).setParameter("id", r.id()).setParameter("ficha", r.ficha())
                .setParameter("ahora", reloj.instant()).getResultList();
        if (turnos.isEmpty()) return false;
        boolean valido = !conversacionesActuales.isEmpty();
        if (valido) {
            Object[] fila = (Object[]) conversacionesActuales.getFirst();
            OptionalLong cliente = fila[2] == null ? OptionalLong.empty() : OptionalLong.of(((Number) fila[2]).longValue());
            valido = ((Number) fila[0]).longValue() == r.revision() && "ASISTENTE".equals(fila[1])
                    && cliente.equals(r.cliente()) && entradas.operativo(r.negocio());
        }
        if (!valido) {
            cerrar(r, "DESCARTADO", "CONTEXTO_CAMBIADO");
            return false;
        }
        if (renovar) em.createNativeQuery("""
                UPDATE turno_ia SET reclamado_hasta=:hasta, actualizado_en=:ahora
                WHERE id=:id AND estado='PENDIENTE' AND intentos=:ficha AND reclamado_hasta > :ahora
                """).setParameter("id", r.id()).setParameter("ficha", r.ficha()).setParameter("hasta", reloj.instant().plusSeconds(120))
                .setParameter("ahora", reloj.instant()).executeUpdate();
        return true;
    }

    private void finalizar(Reclamo r, ContextoTurno c, String texto, List<EfectoTurno> efectos, String motivo) {
        tx.executeWithoutResult(estado -> {
            if (!validar(r, false)) return;
            if (motivo != null || agotado(r, Duration.ZERO)) {
                menu(r, composicion.fallo());
                cerrar(r, "FALLIDO", motivo == null ? "TIEMPO_AGOTADO" : motivo);
                return;
            }
            var franjas = c.franjas.values().stream().map(ContextoTurno.FranjaValidada::ofrecida).toList();
            var resultadoGuardas = guardas.revisar(texto, franjas, reloj.instant().atZone(TiempoNegocio.ZONA));
            String seguro = resultadoGuardas instanceof ResultadoGuardas.Aceptado aceptado ? aceptado.texto() : composicion.neutro();
            var distintos = efectos.stream().filter(this::esPropuesta).distinct().toList();
            EfectoTurno efecto = elegir(efectos);
            if (efecto instanceof EfectoTurno.Derivar d) derivacion.derivar(r.conversacion(), d.motivo());
            else if (efecto instanceof EfectoTurno.SolicitarConsentimientoDerivacion d)
                consentimiento(r, new CodigoSeleccion.Menu(OpcionMenu.PERSONA).codificar(), d.motivo());
            else if (distintos.size() > 1) menu(r, composicion.aclararPropuestas());
            else if (efecto instanceof EfectoTurno.SolicitarConsentimientoReserva s)
                consentimiento(r, new CodigoSeleccion.Franja(s.servicioId(), s.barberoId(), s.inicio().getEpochSecond()).codificar(), null);
            else if (esPropuesta(efecto)) {
                long ficha = r.cliente().orElseThrow();
                Propuesta propuesta = switch (efecto) {
                    case EfectoTurno.ProponerReserva p -> operaciones.proponerCreacion(r.conversacion(), ficha, p.servicioId(), p.barberoId(), p.inicio());
                    case EfectoTurno.ProponerReprogramacion p -> operaciones.proponerReprogramacion(r.conversacion(), ficha, p.reservaId(), p.inicio());
                    case EfectoTurno.ProponerCancelacion p -> operaciones.proponerCancelacion(r.conversacion(), ficha, p.reservaId());
                    default -> throw new IllegalStateException();
                };
                if (propuesta instanceof Propuesta.Creada p) {
                    salida.encolarInteractivo(r.conversacion(), menus.confirmacion(textos.resumen(p.resumen()), p.operacionId(), p.nonce()),
                            OrigenMensaje.ASISTENTE, OptionalLong.empty());
                    guardarBorrador(r, c);
                } else menu(r, textos.noDisponible());
            } else if (efecto instanceof EfectoTurno.MostrarFranjas mostrar && !mostrar.franjas().isEmpty()) {
                salida.encolarInteractivo(r.conversacion(), composicion.franjas(seguro, mostrar.franjas(), c.hoy),
                        OrigenMensaje.ASISTENTE, OptionalLong.empty());
                guardarBorrador(r, c);
            } else if (resultadoGuardas instanceof ResultadoGuardas.Aceptado) {
                salida.encolarTexto(r.conversacion(), seguro, OrigenMensaje.ASISTENTE, OptionalLong.empty());
                guardarBorrador(r, c);
            } else menu(r, composicion.neutro());
            cerrar(r, "HECHO", null);
            observarFin(r.id(), "HECHO", texto.isEmpty() && efecto != null ? Optional.empty() : Optional.of(resultadoGuardas),
                    Optional.ofNullable(efecto));
        });
    }

    private void guardarBorrador(Reclamo r, ContextoTurno c) {
        ObjectNode estado = conversaciones.bloquear(r.conversacion()).estado();
        estado.put("ia_corte_mensaje", c.corteHistorial);
        if (c.servicioBorrador != null) {
            var ia = estado.putObject("ia").put("servicio", c.servicioBorrador).put("dia", c.diaBorrador.toString());
            if (c.profesionalBorrador != null) ia.put("profesional", c.profesionalBorrador);
        }
        conversaciones.guardar(r.conversacion(), estado);
    }

    private void consentimiento(Reclamo r, String pendiente, MotivoDerivacion motivo) {
        operaciones.anularVigente(r.conversacion());
        ObjectNode estado = conversaciones.bloquear(r.conversacion()).estado();
        estado.removeAll();
        estado.put(ConversacionesWhatsApp.PENDIENTE, pendiente).put(ConversacionesWhatsApp.PASO, "CONSENTIMIENTO");
        if (motivo != null) estado.put("motivo", motivo.name());
        conversaciones.guardar(r.conversacion(), estado);
        var privacidad = conversacionPropiedades.privacidad();
        salida.encolarInteractivo(r.conversacion(), menus.consentimiento(textos.privacidad(privacidad.texto(), consultas.nombreNegocio(), privacidad.enlace())),
                OrigenMensaje.ASISTENTE, OptionalLong.empty());
    }

    private void menu(Reclamo r, String cuerpo) {
        operaciones.anularVigente(r.conversacion());
        conversaciones.vaciar(r.conversacion());
        salida.encolarInteractivo(r.conversacion(), menus.menuPrincipal(cuerpo), OrigenMensaje.ASISTENTE, OptionalLong.empty());
    }

    private void cerrar(Reclamo r, String estado, String motivo) {
        int cerrados = em.createNativeQuery("""
                UPDATE turno_ia SET estado=:estado, texto=NULL, motivo=:motivo, actualizado_en=:ahora
                WHERE id=:id AND estado='PENDIENTE' AND intentos=:ficha AND reclamado_hasta > :ahora
                """).setParameter("id", r.id()).setParameter("ficha", r.ficha()).setParameter("estado", estado)
                .setParameter("motivo", motivo).setParameter("ahora", reloj.instant()).executeUpdate();
        if (cerrados > 0 && !"HECHO".equals(estado)) observarFin(r.id(), estado, Optional.empty(), Optional.empty());
    }

    private void observarFin(long turnoId, String estado, Optional<ResultadoGuardas> resultado, Optional<EfectoTurno> efecto) {
        // La evaluación solo ve cierres confirmados; un rollback nunca registra un HECHO ficticio.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** Notifica una vez que la transacción que aplica el efecto se confirmó. */
            @Override public void afterCommit() { observador.fin(turnoId, estado, resultado, efecto); }
        });
    }

    private boolean esPropuesta(EfectoTurno e) {
        return e instanceof EfectoTurno.ProponerReserva || e instanceof EfectoTurno.ProponerReprogramacion
                || e instanceof EfectoTurno.ProponerCancelacion || e instanceof EfectoTurno.SolicitarConsentimientoReserva;
    }

    private EfectoTurno elegir(List<EfectoTurno> efectos) {
        EfectoTurno elegido = null;
        for (var efecto : efectos) if (elegido == null || prioridad(efecto) >= prioridad(elegido)) elegido = efecto;
        return elegido;
    }

    private int prioridad(EfectoTurno e) {
        return switch (e) {
            case EfectoTurno.Derivar ignorado -> 5;
            case EfectoTurno.SolicitarConsentimientoDerivacion ignorado -> 4;
            case EfectoTurno.SolicitarConsentimientoReserva ignorado -> 3;
            case EfectoTurno.MostrarFranjas ignorado -> 1;
            default -> 2;
        };
    }

    private static FalloModelo fallo(MotivoFalloModelo motivo) { return new FalloModelo(motivo, Optional.empty(), null); }
    private record Reclamo(long id, long negocio, long conversacion, long revision, String texto, int ficha,
            OptionalLong cliente, Instant inicio, long nanoInicio) { }
}
