package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.config.ReglasNegocioService;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.reservations.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.scheduling.dto.DisponibilidadDto;
import pe.barberturno.users.Rol;

/**
 * Consultas de la ficha con RLS y permisos compartidos con la web, sin identidad web artificial.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NegocioException.class)
public class ConsultasConversacionService implements ConsultasConversacion {
    private final EntityManager entidades;
    private final ServicioRepository servicios;
    private final BarberoRepository barberos;
    private final ReservaRepository reservas;
    private final DisponibilidadService disponibilidad;
    private final ReglasNegocioService reglas;
    private final ProveedorNegocioActual negocio;
    private final Clock reloj;

    /**
     * Comparte las consultas existentes en la transacción de entrega.
     * @param entidades sesión con contexto RLS
     * @param servicios catálogo del negocio
     * @param barberos profesionales del negocio
     * @param reservas citas y propiedad
     * @param disponibilidad cálculo común RN-05
     * @param reglas parámetros del negocio
     * @param negocio ámbito fijado por el receptor
     * @param reloj instante de evaluación
     */
    public ConsultasConversacionService(EntityManager entidades, ServicioRepository servicios,
            BarberoRepository barberos, ReservaRepository reservas, DisponibilidadService disponibilidad,
            ReglasNegocioService reglas, ProveedorNegocioActual negocio, Clock reloj) {
        this.entidades = entidades;
        this.servicios = servicios;
        this.barberos = barberos;
        this.reservas = reservas;
        this.disponibilidad = disponibilidad;
        this.reglas = reglas;
        this.negocio = negocio;
        this.reloj = reloj;
    }

    @Override
    public String nombreNegocio() {
        return entidades.createNativeQuery("select nombre from negocio where id=app_negocio_actual()", String.class)
                .getSingleResult().toString();
    }

    @Override
    public List<ServicioOpcion> servicios() {
        return servicios.listar(negocio.negocioActual().orElseThrow(), false).stream()
                .map(s -> new ServicioOpcion(s.getId(), s.getNombre(), s.getDuracionMin(), s.getPrecio())).toList();
    }

    @Override
    public Optional<ServicioOpcion> servicio(long servicioId) {
        return servicios.findById(servicioId).filter(s -> s.isActivo())
                .map(s -> new ServicioOpcion(s.getId(), s.getNombre(), s.getDuracionMin(), s.getPrecio()));
    }

    @Override
    public List<ProfesionalOpcion> profesionales(long servicioId) {
        if (servicio(servicioId).isEmpty()) return List.of();
        return barberos.listar(negocio.negocioActual().orElseThrow(), false).stream()
                .map(b -> new ProfesionalOpcion(b.getId(), b.getNombreVisible())).toList();
    }

    @Override
    public List<FranjaOpcion> franjas(long servicioId, OptionalLong barberoId, LocalDate dia) {
        try {
            return proyectar(disponibilidad.consultarFranjas(servicioId,
                    barberoId.isPresent() ? Optional.of(barberoId.getAsLong()) : Optional.empty(), dia, Optional.empty()));
        } catch (NegocioException rechazo) {
            return List.of();
        }
    }

    @Override
    public List<FranjaOpcion> franjasCambio(long fichaId, long reservaId, LocalDate dia) {
        if (cita(fichaId, reservaId).filter(CitaOpcion::puedeReprogramar).isEmpty()) return List.of();
        try {
            return proyectar(disponibilidad.consultarCambioFicha(fichaId, reservaId, dia));
        } catch (NegocioException rechazo) {
            return List.of();
        }
    }

    private List<FranjaOpcion> proyectar(DisponibilidadDto resultado) {
        var nombres = new HashMap<Long, String>();
        barberos.listar(negocio.negocioActual().orElseThrow(), false)
                .forEach(b -> nombres.put(b.getId(), b.getNombreVisible()));
        return resultado.franjas().stream().map(f -> {
            long id = f.barberoIds().getFirst();
            return new FranjaOpcion(id, nombres.get(id), f.inicio().toInstant(), f.fin().toInstant());
        }).toList();
    }

    @Override
    public List<CitaOpcion> citasFuturas(long fichaId) {
        return reservas.findAll((raiz, consulta, cb) -> cb.and(cb.equal(raiz.get("cliente").get("id"), fichaId),
                        cb.greaterThan(raiz.get("inicio"), reloj.instant()),
                        cb.notEqual(raiz.get("estado"), EstadoReserva.CANCELADA)),
                PageRequest.of(0, 10, Sort.by("inicio", "id"))).stream().map(r -> proyectarCita(r, fichaId)).toList();
    }

    @Override
    public Optional<CitaOpcion> cita(long fichaId, long reservaId) {
        return reservas.buscarDetalle(reservaId).filter(r -> r.getCliente().getId() == fichaId)
                .map(r -> proyectarCita(r, fichaId));
    }

    // También admite una cancelada recién ejecutada; no vuelve a leer una cita al repetir el resultado.
    CitaOpcion proyectarCita(Reserva reserva, long fichaId) {
        var permisos = PermisosReserva.calcular(reserva, Rol.CLIENTE, reserva.getCliente().getId() == fichaId,
                false, reloj.instant(), reglas.reservas());
        return new CitaOpcion(reserva.getId(), "BT-" + reserva.getId(), reserva.getServicio().getId(),
                reserva.getServicio().getNombre(), reserva.getBarbero().getId(), reserva.getBarbero().getNombreVisible(),
                reserva.getInicio(), reserva.getFin(), reserva.getPrecioRef(), reserva.getEstado(), reserva.getVersion(),
                permisos.cancelar(), permisos.reprogramar());
    }
}
