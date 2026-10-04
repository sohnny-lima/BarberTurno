package pe.barberturno.scheduling;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.scheduling.CalculadoraFranjas.Franja;
import pe.barberturno.scheduling.CalculadoraFranjas.IntervaloJornada;
import pe.barberturno.scheduling.dto.*;
import pe.barberturno.users.Rol;

/**
 * Disponibilidad RF-07/RF-21 por día de Lima sin consultas por perfil ni por candidata.
 * Comparte el predicado puro RN-05 con la validación que T-20 invocará después de los bloqueos.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class DisponibilidadService {
    private final ServicioRepository servicios;
    private final BarberoRepository barberos;
    private final JornadaRepository jornadas;
    private final BloqueoRepository bloqueos;
    private final ReservaRepository reservas;
    private final Clock clock;
    private final ParametrosReserva parametros;
    private final UsuarioActual actual;
    private final CalculadoraFranjas calculadora = new CalculadoraFranjas();

    /**
     * Recibe persistencia, parámetros y reloj sin modificar la agenda.
     * @param servicios catálogo de duración y estado RN-06
     * @param barberos perfiles y estado RN-06
     * @param jornadas intervalos del día ISO
     * @param bloqueos indisponibilidades que se cruzan con el día
     * @param reservas ocupaciones y propiedad de la exclusión
     * @param clock reloj del servidor inyectado
     * @param parametros horizonte y rejilla RN-05
     * @param actual identidad revalidada; solo se exige al excluir una reserva
     */
    public DisponibilidadService(ServicioRepository servicios, BarberoRepository barberos,
            JornadaRepository jornadas, BloqueoRepository bloqueos, ReservaRepository reservas,
            Clock clock, ParametrosReserva parametros, UsuarioActual actual) {
        this.servicios = servicios;
        this.barberos = barberos;
        this.jornadas = jornadas;
        this.bloqueos = bloqueos;
        this.reservas = reservas;
        this.clock = clock;
        this.parametros = parametros;
        this.actual = actual;
    }

    /**
     * Calcula candidatas completas y fusiona por inicio los perfiles habilitados RF-21.
     * Comprueba la exclusión antes de cualquier resultado, incluso fuera del horizonte.
     * @param servicioId identidad del servicio activo cuya duración se consulta
     * @param barberoId perfil solicitado o vacío para todos los activos
     * @param fecha día de Lima; fuera del horizonte devuelve franjas vacías
     * @param excluirReservaId reserva del propietario o ADMIN que no ocupará franja
     * @return resultado público ordenado con intervalos semiabiertos en Lima
     * @throws NegocioException si falta un recurso o la reserva es ajena (404), o un recurso está inactivo (422)
     */
    @Transactional(readOnly = true)
    public DisponibilidadDto consultarFranjas(long servicioId, Optional<Long> barberoId,
            LocalDate fecha, Optional<Long> excluirReservaId) {
        excluirReservaId.ifPresent(this::autorizarExclusion);
        var servicio = servicios.findById(servicioId).orElseThrow(this::noEncontrado);
        if (!servicio.isActivo()) throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO,
                "El servicio está inactivo.");
        List<Barbero> perfiles = barberoId.map(id -> List.of(barberoActivo(id)))
                .orElseGet(barberos::findByActivoTrueOrderByIdAsc);
        Instant ahora = clock.instant();
        Instant limite = limiteHorizonte(ahora);
        if (fueraDeHorizonte(fecha, ahora) || perfiles.isEmpty()) {
            return new DisponibilidadDto(fecha, servicioId, servicio.getDuracionMin(), List.of());
        }
        var datos = cargarDia(perfiles.stream().map(Barbero::getId).toList(), fecha,
                excluirReservaId.orElse(0L));
        Map<Instant, List<Long>> fusion = new TreeMap<>();
        for (Barbero perfil : perfiles) {
            long id = perfil.getId();
            var intervalos = datos.jornadas().getOrDefault(id, List.of());
            var ocupaciones = datos.ocupaciones().getOrDefault(id, List.of());
            for (IntervaloJornada intervalo : intervalos) {
                Instant finJornada = fecha.atTime(intervalo.fin()).atZone(TiempoNegocio.ZONA).toInstant();
                for (Instant inicio = fecha.atTime(intervalo.inicio()).atZone(TiempoNegocio.ZONA).toInstant();
                        !inicio.plus(servicio.getDuracionMin(), ChronoUnit.MINUTES).isAfter(finJornada);
                        inicio = inicio.plus(parametros.rejillaMin(), ChronoUnit.MINUTES)) {
                    var candidata = new Franja(inicio, inicio.plus(servicio.getDuracionMin(), ChronoUnit.MINUTES));
                    if (evaluar(fecha, intervalos, ocupaciones, candidata, servicio.getDuracionMin(),
                            ahora, limite).isEmpty()) {
                        fusion.computeIfAbsent(inicio, clave -> new ArrayList<>()).add(id);
                    }
                }
            }
        }
        var franjas = fusion.entrySet().stream().map(e -> new FranjaDisponibleDto(
                TiempoNegocio.aLima(e.getKey()),
                TiempoNegocio.aLima(e.getKey().plus(servicio.getDuracionMin(), ChronoUnit.MINUTES)),
                e.getValue().stream().distinct().sorted().toList())).toList();
        return new DisponibilidadDto(fecha, servicioId, servicio.getDuracionMin(), franjas);
    }

    /**
     * Valida RN-05 sin escribir ni tomar bloqueos; el servicio de escritura debe adquirirlos antes.
     * La duración procede del intervalo, permitiendo conservar la referencia RN-13 al reprogramar.
     * Prioridad temporal: horizonte, inicio pasado, jornada/rejilla, ocupaciones.
     * @param barberoId perfil de destino; debe estar activo
     * @param inicio inicio absoluto inclusivo solicitado
     * @param fin fin absoluto exclusivo, con duración positiva en minutos enteros
     * @param excluirReservaId reserva ya autorizada por el llamador; cero no excluye ninguna
     * @return primer código incumplido, o vacío si la franja pertenece a la disponibilidad
     * @throws NegocioException si el perfil no existe (404)
     */
    @Transactional(readOnly = true)
    public Optional<ErrorCodigo> validarFranja(long barberoId, Instant inicio, Instant fin, long excluirReservaId) {
        var perfil = barberos.findById(barberoId).orElseThrow(this::noEncontrado);
        if (!perfil.isActivo()) return Optional.of(ErrorCodigo.RECURSO_INACTIVO);
        Instant ahora = clock.instant();
        LocalDate fecha = TiempoNegocio.fechaLima(inicio);
        if (fueraDeHorizonte(fecha, ahora)) return Optional.of(ErrorCodigo.FUERA_DE_HORIZONTE);
        if (!inicio.isAfter(ahora)) return Optional.of(ErrorCodigo.INICIO_EN_PASADO);
        Duration duracion = Duration.between(inicio, fin);
        if (duracion.isNegative() || duracion.isZero() || duracion.getNano() != 0
                || duracion.getSeconds() % 60 != 0 || duracion.toMinutes() > Integer.MAX_VALUE) {
            return Optional.of(ErrorCodigo.FUERA_DE_HORARIO);
        }
        var datos = cargarDia(List.of(barberoId), fecha, excluirReservaId);
        return evaluar(fecha, datos.jornadas().getOrDefault(barberoId, List.of()),
                datos.ocupaciones().getOrDefault(barberoId, List.of()), new Franja(inicio, fin),
                (int) duracion.toMinutes(), ahora, limiteHorizonte(ahora));
    }

    private Optional<ErrorCodigo> evaluar(LocalDate fecha, List<IntervaloJornada> intervalos,
            List<Franja> ocupaciones, Franja candidata, int duracion, Instant ahora, Instant limite) {
        if (!candidata.inicio().isAfter(ahora)) return Optional.of(ErrorCodigo.INICIO_EN_PASADO);
        if (!calculadora.esFranjaValida(fecha, intervalos, List.of(), candidata, duracion,
                ahora, limite, parametros.rejillaMin())) return Optional.of(ErrorCodigo.FUERA_DE_HORARIO);
        if (!calculadora.esFranjaValida(fecha, intervalos, ocupaciones, candidata, duracion,
                ahora, limite, parametros.rejillaMin())) return Optional.of(ErrorCodigo.FRANJA_NO_DISPONIBLE);
        return Optional.empty();
    }

    private DatosDia cargarDia(List<Long> ids, LocalDate fecha, long excluirId) {
        Map<Long, List<IntervaloJornada>> intervalos = new HashMap<>();
        Map<Long, List<Franja>> ocupaciones = new HashMap<>();
        jornadas.buscarDia(ids, fecha.getDayOfWeek().getValue()).forEach(j ->
                intervalos.computeIfAbsent(j.getBarbero().getId(), clave -> new ArrayList<>())
                        .add(new IntervaloJornada(j.getHoraInicio(), j.getHoraFin())));
        Instant inicio = TiempoNegocio.inicioDelDia(fecha);
        Instant fin = TiempoNegocio.inicioDelDia(fecha.plusDays(1));
        bloqueos.buscarDia(ids, inicio, fin).forEach(b ->
                ocupaciones.computeIfAbsent(b.getBarbero().getId(), clave -> new ArrayList<>())
                        .add(new Franja(b.getInicio(), b.getFin())));
        reservas.buscarDia(ids, inicio, fin, excluirId).forEach(r ->
                ocupaciones.computeIfAbsent(r.getBarbero().getId(), clave -> new ArrayList<>())
                        .add(new Franja(r.getInicio(), r.getFin())));
        return new DatosDia(intervalos, ocupaciones);
    }

    private void autorizarExclusion(long id) {
        Rol rol;
        long actorId;
        try {
            rol = actual.rol();
            actorId = actual.id();
        } catch (NegocioException error) {
            if (error.codigo() == ErrorCodigo.NO_AUTENTICADO) throw noEncontrado();
            throw error;
        }
        if (rol != Rol.ADMIN && rol != Rol.CLIENTE) throw noEncontrado();
        long propietario = reservas.buscarClienteId(id).orElseThrow(this::noEncontrado);
        if (rol != Rol.ADMIN && propietario != actorId) throw noEncontrado();
    }

    private Barbero barberoActivo(long id) {
        var perfil = barberos.findById(id).orElseThrow(this::noEncontrado);
        if (!perfil.isActivo()) throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO,
                "El barbero está inactivo.");
        return perfil;
    }

    private boolean fueraDeHorizonte(LocalDate fecha, Instant ahora) {
        LocalDate hoy = TiempoNegocio.fechaLima(ahora);
        return fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(parametros.horizonteDias()));
    }

    private Instant limiteHorizonte(Instant ahora) {
        return TiempoNegocio.inicioDelDia(TiempoNegocio.fechaLima(ahora)
                .plusDays(parametros.horizonteDias() + 1L)).minusNanos(1);
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "Recurso no encontrado.");
    }

    private record DatosDia(Map<Long, List<IntervaloJornada>> jornadas, Map<Long, List<Franja>> ocupaciones) { }
}
