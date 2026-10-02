package pe.barberturno.scheduling;

import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.error.ManejadorErrores.ErrorCampo;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.scheduling.dto.JornadaDto;
import pe.barberturno.users.Rol;

/**
 * Semana RN-17 con intervalos múltiples MJ-02 y ocupación MJ-03; serializa cambios con el bloqueo ②.
 * Permite preparar la jornada de perfiles inactivos sin habilitarlos para reservas RN-06.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class JornadaService {
    private final JornadaRepository jornadas;
    private final BarberoRepository barberos;
    private final ReservaRepository reservas;
    private final Clock clock;
    private final Validator validator;

    /**
     * Recibe persistencia, reloj y validación; no depende de servicios de reservas.
     * @param jornadas persistencia de la semana
     * @param barberos perfiles y bloqueo ②
     * @param reservas lectura de ocupación después del bloqueo
     * @param clock reloj inyectado para determinar el futuro estricto
     * @param validator validación por campo de los DTO
     */
    public JornadaService(JornadaRepository jornadas, BarberoRepository barberos,
            ReservaRepository reservas, Clock clock, Validator validator) {
        this.jornadas = jornadas;
        this.barberos = barberos;
        this.reservas = reservas;
        this.clock = clock;
        this.validator = validator;
    }

    /**
     * Comprueba propiedad antes de consultar la semana; ADMIN puede consultar cualquier perfil existente.
     * @param id perfil activo o inactivo solicitado
     * @param actual actor autenticado con rol ADMIN o BARBERO
     * @return intervalos ordenados por día y hora
     * @throws NegocioException si el perfil no existe o es ajeno al barbero (404)
     */
    @Transactional(readOnly = true)
    public List<JornadaDto> listar(long id, UsuarioAutenticado actual) {
        if (actual.rol() != Rol.ADMIN
                && (actual.rol() != Rol.BARBERO || actual.barberoId().filter(b -> b == id).isEmpty())) {
            throw noEncontrado();
        }
        if (!barberos.existsById(id)) throw noEncontrado();
        return jornadas.listarPorBarbero(id).stream().map(JornadaDto::desde).toList();
    }

    /**
     * Valida la semana, bloquea ② y lee después las reservas futuras que ocupan franja.
     * Solo elimina e inserta intervalos cuando todas caben enteras en un intervalo nuevo en Lima.
     * @param id perfil a configurar, incluso inactivo
     * @param semana lista completa; vacía significa sin jornada
     * @return semana persistida ordenada
     * @throws NegocioException si la jornada es inválida (400), falta el perfil (404)
     * o una reserva futura no cabe en los nuevos intervalos (409, con sus ids)
     */
    @Transactional
    public List<JornadaDto> reemplazar(long id, List<JornadaDto> semana) {
        validar(semana);
        Barbero barbero = barberos.bloquearPorIds(List.of(id)).stream().findFirst().orElseThrow(this::noEncontrado);
        List<Long> conflictos = reservas.buscarFuturasQueOcupan(id, clock.instant()).stream()
                .filter(r -> !cabe(r, semana)).map(Reserva::getId).toList();
        if (!conflictos.isEmpty()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO_CON_RESERVAS,
                    "La jornada deja fuera reservas futuras.", Map.of("reservas", conflictos));
        }
        jornadas.borrarPorBarbero(id);
        jornadas.saveAllAndFlush(semana.stream().map(d -> new Jornada(barbero, d.diaSemana().shortValue(),
                LocalTime.parse(d.horaInicio()), LocalTime.parse(d.horaFin()))).toList());
        return jornadas.listarPorBarbero(id).stream().map(JornadaDto::desde).toList();
    }

    private void validar(List<JornadaDto> semana) {
        List<ErrorCampo> errores = new ArrayList<>();
        if (semana == null) {
            errores.add(new ErrorCampo("semana", "Indique la lista completa de intervalos."));
        } else {
            for (int i = 0; i < semana.size(); i++) {
                JornadaDto intervalo = semana.get(i);
                String campo = "[" + i + "]";
                if (intervalo == null) {
                    errores.add(new ErrorCampo(campo, "Indique un intervalo."));
                    continue;
                }
                var violaciones = validator.validate(intervalo);
                violaciones.forEach(v -> errores.add(new ErrorCampo(campo + "." + v.getPropertyPath(), v.getMessage())));
                if (violaciones.isEmpty() && intervalo.horaInicio().compareTo(intervalo.horaFin()) >= 0) {
                    errores.add(new ErrorCampo(campo + ".horaFin", "El fin debe ser posterior al inicio."));
                }
            }
            if (errores.isEmpty()) {
                for (int i = 0; i < semana.size(); i++) {
                    for (int j = i + 1; j < semana.size(); j++) {
                        JornadaDto a = semana.get(i), b = semana.get(j);
                        if (a.diaSemana().equals(b.diaSemana())
                                && a.horaInicio().compareTo(b.horaFin()) < 0
                                && b.horaInicio().compareTo(a.horaFin()) < 0) {
                            errores.add(new ErrorCampo("[" + i + "]", "Se solapa con el intervalo [" + j + "]."));
                            errores.add(new ErrorCampo("[" + j + "]", "Se solapa con el intervalo [" + i + "]."));
                        }
                    }
                }
            }
        }
        if (!errores.isEmpty()) {
            errores.sort(Comparator.comparing(ErrorCampo::campo).thenComparing(ErrorCampo::mensaje));
            throw new NegocioException(ErrorCodigo.JORNADA_INVALIDA, "Revise los intervalos de la jornada.", errores);
        }
    }

    private boolean cabe(Reserva reserva, List<JornadaDto> semana) {
        var inicio = TiempoNegocio.aLima(reserva.getInicio());
        // Comparar instantes del mismo día evita aceptar reservas que terminan al día siguiente.
        return semana.stream().filter(d -> d.diaSemana() == TiempoNegocio.diaIso(reserva.getInicio()))
                .anyMatch(d -> {
                    var desde = inicio.toLocalDate().atTime(LocalTime.parse(d.horaInicio()))
                            .atZone(TiempoNegocio.ZONA).toInstant();
                    var hasta = inicio.toLocalDate().atTime(LocalTime.parse(d.horaFin()))
                            .atZone(TiempoNegocio.ZONA).toInstant();
                    return !reserva.getInicio().isBefore(desde) && !reserva.getFin().isAfter(hasta);
                });
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el barbero.");
    }
}
