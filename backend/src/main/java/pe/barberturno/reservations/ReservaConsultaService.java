package pe.barberturno.reservations;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.reservations.dto.ReservaDto;
import pe.barberturno.users.Rol;

/**
 * Consultas RF-11/13 con autorización en servidor, fechas de Lima y proyección mínima RNF-12.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(readOnly = true)
public class ReservaConsultaService {
    private final ReservaRepository reservas;
    private final ReservaAutorizacion autorizacion;
    private final Clock reloj;
    private final ParametrosReserva parametros;

    /**
     * Mantiene accesibles las relaciones durante autorización y proyección, con reloj inyectado.
     * @param reservas persistencia paginada con carga de relaciones y conteo separados
     * @param autorizacion política única para recursos concretos
     * @param reloj fuente del instante de evaluación de permisos
     * @param parametros ventanas RN-07/12 utilizadas por el DTO
     */
    public ReservaConsultaService(ReservaRepository reservas, ReservaAutorizacion autorizacion,
            Clock reloj, ParametrosReserva parametros) {
        this.reservas = reservas;
        this.autorizacion = autorizacion;
        this.reloj = reloj;
        this.parametros = parametros;
    }

    /**
     * Limita la búsqueda al propietario CLIENTE; los días opcionales son inclusivos en Lima.
     * @param actor identidad vigente del propietario
     * @param estado estado opcional, incluidos los terminales
     * @param desde primer día opcional
     * @param hasta último día opcional
     * @param pagina índice desde cero
     * @param tamano cantidad entre 1 y 100
     * @return reservas propias por inicio ascendente e identidad para desempatar
     * @throws NegocioException si el rol no es CLIENTE, el rango está invertido o la página es inválida
     */
    public PaginaDto<ReservaDto> mias(UsuarioAutenticado actor, EstadoReserva estado,
            LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (actor.rol() != Rol.CLIENTE) throw prohibido();
        validarRango(desde, hasta, false);
        return consultar(actor, actor.id(), null, null, estado, desde, hasta, pagina, tamano);
    }

    /**
     * Consulta agenda e historial operativo; BARBERO solo ve su perfil y ADMIN filtra globalmente.
     * Solicitar otro perfil o clienteId como BARBERO se rechaza con PROHIBIDO, sin revelar existencia.
     * @param actor identidad vigente del personal
     * @param desde primer día obligatorio
     * @param hasta último día obligatorio; máximo 366 días inclusivos
     * @param barberoId perfil opcional para ADMIN o el propio para BARBERO
     * @param servicioId servicio opcional
     * @param estado estado opcional
     * @param clienteId propietario opcional exclusivo de ADMIN
     * @param pagina índice desde cero
     * @param tamano cantidad entre 1 y 100
     * @return página autorizada ordenada por inicio e identidad
     * @throws NegocioException si el rol, perfil o filtro no está autorizado, el rango o la página es inválido
     */
    public PaginaDto<ReservaDto> listar(UsuarioAutenticado actor, LocalDate desde, LocalDate hasta,
            Long barberoId, Long servicioId, EstadoReserva estado, Long clienteId, int pagina, int tamano) {
        if (actor.rol() == Rol.BARBERO) {
            long propio = actor.barberoId().orElseThrow(ReservaConsultaService::prohibido);
            if (clienteId != null || barberoId != null && barberoId != propio) throw prohibido();
            barberoId = propio;
        } else if (actor.rol() != Rol.ADMIN) {
            throw prohibido();
        }
        validarRango(desde, hasta, true);
        return consultar(actor, clienteId, barberoId, servicioId, estado, desde, hasta, pagina, tamano);
    }

    /**
     * Oculta uniformemente reservas inexistentes y ajenas según CP-02.
     * @param actor identidad autenticada que recibirá los permisos y datos mínimos
     * @param id identidad solicitada
     * @return detalle autorizado con instantes de Lima
     * @throws NegocioException NO_ENCONTRADO si no existe o el actor no puede verla
     */
    public ReservaDto detalle(UsuarioAutenticado actor, long id) {
        var reserva = reservas.buscarDetalle(id).orElseThrow(ReservaConsultaService::noEncontrado);
        if (!autorizacion.puedeVer(actor, reserva)) throw noEncontrado();
        return ReservaDto.desde(reserva, actor, reloj.instant(), parametros);
    }

    private PaginaDto<ReservaDto> consultar(UsuarioAutenticado actor, Long clienteId, Long barberoId,
            Long servicioId, EstadoReserva estado, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new NegocioException(ErrorCodigo.VALIDACION,
                    "La página debe ser al menos 0 y el tamaño debe estar entre 1 y 100.");
        }
        Specification<Reserva> filtros = (raiz, consulta, cb) -> {
            var condiciones = new ArrayList<Predicate>();
            if (clienteId != null) condiciones.add(cb.equal(raiz.get("cliente").get("id"), clienteId));
            if (barberoId != null) condiciones.add(cb.equal(raiz.get("barbero").get("id"), barberoId));
            if (servicioId != null) condiciones.add(cb.equal(raiz.get("servicio").get("id"), servicioId));
            if (estado != null) condiciones.add(cb.equal(raiz.get("estado"), estado));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(raiz.get("inicio"), TiempoNegocio.inicioDelDia(desde)));
            if (hasta != null) condiciones.add(cb.lessThan(raiz.get("inicio"), TiempoNegocio.finDelDia(hasta)));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        var resultado = reservas.findAll(filtros, PageRequest.of(pagina, tamano, Sort.by("inicio", "id")));
        var ahora = reloj.instant();
        return PaginaDto.desde(resultado.map(r -> ReservaDto.desde(r, actor, ahora, parametros)));
    }

    private static void validarRango(LocalDate desde, LocalDate hasta, boolean obligatorio) {
        boolean incompleto = obligatorio && (desde == null || hasta == null);
        boolean invertido = desde != null && hasta != null && desde.isAfter(hasta);
        boolean excesivo = obligatorio && desde != null && hasta != null
                && ChronoUnit.DAYS.between(desde, hasta) + 1 > 366;
        if (incompleto || invertido || excesivo || LocalDate.MAX.equals(hasta)) {
            throw new NegocioException(ErrorCodigo.RANGO_FECHAS_INVALIDO,
                    "Indique un rango ordenado; la agenda admite como máximo 366 días inclusivos.");
        }
    }

    private static NegocioException prohibido() {
        return new NegocioException(ErrorCodigo.PROHIBIDO, "No tiene permiso para consultar estas reservas.");
    }

    private static NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "La reserva no existe.");
    }
}
