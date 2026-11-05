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
 * La terminal estructurada se degrada contra datos del servidor; el texto del proveedor solo
 * llega al observador y a la continuación efímera. La salida siempre la compone el servidor.
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
    private final BandejaPropiedades bandeja;
    private final IaPropiedades propiedades;
    private final ConstructorContextoModelo constructor;
    private final HerramientasReserva herramientas;
    private final LimitesIAService limites;
    private final ModeloConversacional modelo;
    private final RespuestasAsistente respuestas;
    private final InstruccionesAsistente instrucciones;
    private final ObservadorEvaluacion observador;
    private final Clock reloj;

    TurnosIAService(EntityManager em, PlatformTransactionManager gestor, EjecutorEnNegocio ejecutor,
            CanalWhatsAppRepository canales, ConversacionesWhatsApp conversaciones, RegistroEntradaWhatsApp entradas,
            OperacionesConversacion operaciones, ConsultasConversacion consultas, DerivacionConversacion derivacion,
            SalidaWhatsApp salida, MenusConversacion menus, TextosConversacion textos,
            ConversacionPropiedades conversacionPropiedades, BandejaPropiedades bandeja, IaPropiedades propiedades,
            ConstructorContextoModelo constructor,
            HerramientasReserva herramientas, LimitesIAService limites, ModeloConversacional modelo,
            RespuestasAsistente respuestas, InstruccionesAsistente instrucciones,
            ObservadorEvaluacion observador, Clock reloj) {
        this.em = em; this.tx = new TransactionTemplate(gestor); this.ejecutor = ejecutor; this.canales = canales;
        this.conversaciones = conversaciones; this.entradas = entradas; this.operaciones = operaciones;
        this.consultas = consultas; this.derivacion = derivacion; this.salida = salida; this.menus = menus;
        this.textos = textos; this.conversacionPropiedades = conversacionPropiedades; this.bandeja = bandeja;
        this.propiedades = propiedades;
        this.constructor = constructor; this.herramientas = herramientas; this.limites = limites; this.modelo = modelo;
        this.respuestas = respuestas; this.instrucciones = instrucciones;
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
                    var reservada = limites.reservar(r.id(), n, peticion);
                    // Los bloqueos pueden haber consumido el margen: revierte también el presupuesto.
                    if (agotado(r, propiedades.tiempoLlamada())) throw fallo(MotivoFalloModelo.TIEMPO_AGOTADO);
                    return reservada;
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
                // El observador puede conservar transcripciones: el pensamiento solo vive en este bucle.
                observador.llamada(r.id(), new RespuestaModelo(respuesta.texto(), respuesta.llamadas(),
                        ContinuacionProveedor.VACIA, respuesta.uso(), respuesta.parada()));
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
                var terminales = respuesta.llamadas().stream()
                        .filter(l -> l.herramienta() == HerramientaIA.RESPONDER_AL_CLIENTE).toList();
                for (var llamada : respuesta.llamadas()) {
                    if (llamada.herramienta() == HerramientaIA.RESPONDER_AL_CLIENTE) continue;
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
                }
                if (!terminales.isEmpty()) {
                    if (terminales.size() == 1 && ++herramientasRespuesta <= Math.min(4, propiedades.maxHerramientas()))
                        c.decision = herramientas.respuesta(c, terminales.getFirst()).orElse(null);
                    finalizar(r, c, texto, efectos, null);
                    return;
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
        if (renovar) return em.createNativeQuery("""
                UPDATE turno_ia SET reclamado_hasta=:hasta, actualizado_en=:ahora
                WHERE id=:id AND estado='PENDIENTE' AND intentos=:ficha AND reclamado_hasta > :ahora
                """).setParameter("id", r.id()).setParameter("ficha", r.ficha()).setParameter("hasta", reloj.instant().plusSeconds(120))
                .setParameter("ahora", reloj.instant()).executeUpdate() == 1;
        return true;
    }

    private void finalizar(Reclamo r, ContextoTurno c, String texto, List<EfectoTurno> efectos, String motivo) {
        tx.executeWithoutResult(estado -> {
            if (!validar(r, false)) return;
            if (motivo != null || agotado(r, Duration.ZERO)) {
                menu(r, MotivoMenu.FALLO);
                cerrar(r, "FALLIDO", motivo == null ? "TIEMPO_AGOTADO" : motivo);
                observarDecision(r.id(), Optional.empty(), texto);
                return;
            }
            EfectoTurno efecto = elegir(efectos);
            if (efecto instanceof EfectoTurno.MostrarFranjas mostrar)
                efecto = new EfectoTurno.MostrarFranjas(mostrar.franjas().stream().limit(10).toList());
            DatosRespuesta datos = datosRespuesta(r, c);
            DecisionRespuesta decision = degradar(c, datos);
            String ultima = "NEUTRO";
            boolean conservarSelecciones = false;
            var distintos = efectos.stream().filter(this::esPropuesta).distinct().toList();
            EfectoTurno aplicado = efecto;
            if (efecto instanceof EfectoTurno.Derivar d) {
                // V2-DA-83: con SOLO_HUMANO el texto que provoca la derivación no se guardó al entrar; se guarda
                // aquí (conversación ya bloqueada por validar) antes del acuse. Con TODOS ya está en el historial.
                if (bandeja.entrantes() == BandejaPropiedades.Entrantes.SOLO_HUMANO && r.texto() != null && !r.texto().isBlank())
                    entradas.guardarTextoDerivado(r.conversacion(), r.texto());
                derivacion.derivar(r.conversacion(), d.motivo());
                ultima = "DERIVAR";
            }
            else if (efecto instanceof EfectoTurno.SolicitarConsentimientoDerivacion d) {
                consentimiento(r, new CodigoSeleccion.Menu(OpcionMenu.PERSONA).codificar(), d.motivo());
                ultima = "CONSENTIMIENTO";
            }
            else if (distintos.size() > 1) {
                menu(r, MotivoMenu.VARIAS_PROPUESTAS);
                aplicado = null;
            }
            else if (efecto instanceof EfectoTurno.SolicitarConsentimientoReserva s) {
                consentimiento(r, new CodigoSeleccion.Franja(s.servicioId(), s.barberoId(), s.inicio().getEpochSecond()).codificar(), null);
                ultima = "CONSENTIMIENTO";
            }
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
                    ultima = "PROPUESTA";
                    conservarSelecciones = true;
                } else {
                    menu(r, MotivoMenu.NO_DISPONIBLE);
                    aplicado = null;
                }
            } else if (efecto instanceof EfectoTurno.MostrarFranjas mostrar && !mostrar.franjas().isEmpty()) {
                enviar(r, respuestas.franjas(mostrar.franjas(), c.hoy));
                ultima = "MOSTRAR_FRANJAS";
                conservarSelecciones = true;
            } else if (decision != null) {
                enviar(r, respuestas.responder(decision, datos));
                ultima = decision.intencion().name() + decision.falta().map(d -> ":" + d.name()).orElse("");
                conservarSelecciones = true;
            } else {
                menu(r, MotivoMenu.NEUTRO);
                aplicado = null;
            }
            guardarBorrador(r, c, ultima, conservarSelecciones);
            cerrar(r, "HECHO", null);
            observarDecision(r.id(), Optional.ofNullable(decision), texto);
            observarFin(r.id(), "HECHO", Optional.empty(), Optional.ofNullable(aplicado));
        });
    }

    private DatosRespuesta datosRespuesta(Reclamo r, ContextoTurno c) {
        var servicios = consultas.servicios();
        var servicio = servicios.stream().filter(s -> Objects.equals(c.servicioBorrador, s.id())).findFirst();
        var activos = servicio.isPresent() ? consultas.profesionales(servicio.get().id())
                : servicios.isEmpty() ? List.<ProfesionalOpcion>of() : consultas.profesionales(servicios.getFirst().id());
        var profesionales = servicio.isPresent() ? activos : List.<ProfesionalOpcion>of();
        SeleccionProfesional profesional = new SeleccionProfesional.SinElegir();
        if (Objects.equals(c.profesionalBorrador, 0L)) profesional = new SeleccionProfesional.Cualquiera();
        else if (c.profesionalBorrador != null) profesional = profesionales.stream()
                .filter(p -> p.id() == c.profesionalBorrador).findFirst()
                .<SeleccionProfesional>map(SeleccionProfesional.Concreto::new).orElse(new SeleccionProfesional.SinElegir());
        // Una selección que desapareció entre las consultas y el cierre nunca origina una lista obsoleta.
        boolean desactivada = c.servicioBorrador != null && servicio.isEmpty()
                || c.profesionalBorrador != null && c.profesionalBorrador != 0
                    && activos.stream().noneMatch(p -> p.id() == c.profesionalBorrador);
        boolean requiereProfesionales = c.decision != null && (c.decision.intencion() == IntencionRespuesta.MOSTRAR_PROFESIONALES
                || c.decision.falta().filter(d -> d == DatoFaltante.DIA || d == DatoFaltante.PROFESIONAL).isPresent());
        if (c.decision != null && (desactivada || requiereProfesionales && servicio.isPresent() && profesionales.isEmpty())) {
            c.decision = new DecisionRespuesta(IntencionRespuesta.MOSTRAR_SERVICIOS, Optional.empty());
            servicios = List.of();
        }
        if (servicio.isEmpty()) c.servicioBorrador = null;
        if (desactivada) c.profesionalBorrador = null;
        var citas = r.cliente().isPresent() ? consultas.citasFuturas(r.cliente().getAsLong()) : List.<CitaOpcion>of();
        return new DatosRespuesta(consultas.nombreNegocio(), c.hoy, servicio, profesional, servicios, profesionales, citas);
    }

    private DecisionRespuesta degradar(ContextoTurno c, DatosRespuesta datos) {
        var d = c.decision;
        if (d == null) return null;
        if (d.intencion() == IntencionRespuesta.SIN_DISPONIBILIDAD && !Boolean.TRUE.equals(c.ultimaBusquedaVacia)) return null;
        boolean pedirDia = d.falta().filter(DatoFaltante.DIA::equals).isPresent();
        boolean pedirProfesional = d.intencion() == IntencionRespuesta.MOSTRAR_PROFESIONALES
                || d.falta().filter(DatoFaltante.PROFESIONAL::equals).isPresent();
        if ((pedirDia || pedirProfesional) && datos.servicio().isEmpty())
            return new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.SERVICIO));
        if (pedirDia && datos.profesional() instanceof SeleccionProfesional.SinElegir)
            return new DecisionRespuesta(IntencionRespuesta.PEDIR_DATO, Optional.of(DatoFaltante.PROFESIONAL));
        return d;
    }

    private void guardarBorrador(Reclamo r, ContextoTurno c, String ultima, boolean conservarSelecciones) {
        ObjectNode estado = conversaciones.bloquear(r.conversacion()).estado();
        estado.remove("ia_corte_mensaje");
        var ia = estado.putObject("ia").put("ultima", ultima);
        if (conservarSelecciones) {
            if (c.servicioBorrador != null) ia.put("servicio", c.servicioBorrador);
            if (c.profesionalBorrador != null) ia.put("profesional", c.profesionalBorrador);
            if (c.diaBorrador != null) ia.put("dia", c.diaBorrador.toString());
        }
        conversaciones.guardar(r.conversacion(), estado);
    }

    private void observarDecision(long turnoId, Optional<DecisionRespuesta> decision, String texto) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** Comunica solo la decisión cuyo cierre quedó confirmado. */
            @Override public void afterCommit() { observador.decision(turnoId, decision,
                    texto.isEmpty() ? Optional.empty() : Optional.of(texto)); }
        });
    }

    private void enviar(Reclamo r, MensajeAsistente mensaje) {
        switch (mensaje) {
            case MensajeAsistente.Texto(var texto) -> salida.encolarTexto(r.conversacion(), texto,
                    OrigenMensaje.ASISTENTE, OptionalLong.empty());
            case MensajeAsistente.Interactivo(var contenido) -> salida.encolarInteractivo(r.conversacion(), contenido,
                    OrigenMensaje.ASISTENTE, OptionalLong.empty());
        }
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

    private void menu(Reclamo r, MotivoMenu motivo) {
        operaciones.anularVigente(r.conversacion());
        conversaciones.vaciar(r.conversacion());
        enviar(r, respuestas.menu(motivo));
    }

    private void cerrar(Reclamo r, String estado, String motivo) {
        if ("HECHO".equals(estado) && agotado(r, Duration.ZERO)) throw fallo(MotivoFalloModelo.TIEMPO_AGOTADO);
        int cerrados = em.createNativeQuery("""
                UPDATE turno_ia SET estado=:estado, texto=NULL, motivo=:motivo, actualizado_en=:ahora
                WHERE id=:id AND estado='PENDIENTE' AND intentos=:ficha AND reclamado_hasta > :ahora
                """).setParameter("id", r.id()).setParameter("ficha", r.ficha()).setParameter("estado", estado)
                .setParameter("motivo", motivo).setParameter("ahora", reloj.instant()).executeUpdate();
        if (cerrados != 1) throw new IllegalStateException("El turno perdió su concesión durante el cierre.");
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
