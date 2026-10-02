package pe.barberturno.scheduling;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.error.ManejadorErrores.ErrorCampo;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.scheduling.dto.*;
import pe.barberturno.users.Rol;
import pe.barberturno.users.UsuarioRepository;

/**
 * Disponibilidad RF-06/RF-20: RN-18 y altas atómicas después del bloqueo ② ordenado.
 * El borrado físico RN-16 también se serializa con la agenda del perfil.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class BloqueoService {
    private final BloqueoRepository bloqueos;
    private final BarberoRepository barberos;
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    /**
     * Recibe persistencia y reloj; lee conflictos mediante repositorio, sin ciclos entre servicios.
     * @param bloqueos persistencia de indisponibilidad
     * @param barberos perfiles y bloqueo ② ordenado
     * @param reservas lectura de ocupación posterior al bloqueo
     * @param usuarios referencia del actor para creado_por
     * @param clock reloj inyectado para RN-18 y creado_en
     */
    public BloqueoService(BloqueoRepository bloqueos, BarberoRepository barberos,
            ReservaRepository reservas, UsuarioRepository usuarios, Clock clock) {
        this.bloqueos = bloqueos;
        this.barberos = barberos;
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    /**
     * Oculta perfiles ajenos antes de consultar sus bloqueos; las fechas inclusivas son días de Lima.
     * @param id perfil solicitado, activo o inactivo
     * @param desde primer día inclusivo
     * @param hasta último día inclusivo
     * @param actual ADMIN o BARBERO asignado
     * @return bloqueos que se cruzan con el rango, ordenados por inicio e id
     * @throws NegocioException si el perfil es inexistente o ajeno (404) o el rango supera 366 días,
     * está invertido o no puede convertirse en un intervalo de días completos (400)
     */
    @Transactional(readOnly = true)
    public List<BloqueoDto> listar(long id, LocalDate desde, LocalDate hasta, UsuarioAutenticado actual) {
        if (actual.rol() != Rol.ADMIN
                && (actual.rol() != Rol.BARBERO || actual.barberoId().filter(b -> b == id).isEmpty())) {
            throw noEncontrado();
        }
        if (!barberos.existsById(id)) throw noEncontrado();
        if (desde.isAfter(hasta) || ChronoUnit.DAYS.between(desde, hasta) + 1 > 366
                || hasta.equals(LocalDate.MAX)) {
            throw new NegocioException(ErrorCodigo.RANGO_FECHAS_INVALIDO, "Indique un rango de hasta 366 días inclusivos.");
        }
        return bloqueos.buscarQueSeCruzan(id, TiempoNegocio.inicioDelDia(desde), TiempoNegocio.finDelDia(hasta))
                .stream().map(BloqueoDto::desde).toList();
    }

    /**
     * Toma ② y consulta los solapes semiabiertos antes de crear un único bloqueo RN-18.
     * @param id perfil cuya agenda se bloquea
     * @param solicitud cuerpo validado con Bean Validation por el controlador
     * @param actual actor ADMIN autenticado, registrado como creador
     * @return bloqueo persistido con fechas en Lima
     * @throws NegocioException si el intervalo es inválido (400), falta el perfil (404),
     * empieza en el pasado (422) o cruza reservas que ocupan franja (409 con ids)
     */
    @Transactional
    public BloqueoDto crear(long id, CrearBloqueoDto solicitud, UsuarioAutenticado actual) {
        return crearTodos(List.of(id), solicitud.inicio().toInstant(), solicitud.fin().toInstant(),
                solicitud.motivo(), actual, false).getFirst();
    }

    /**
     * Bloquea todos los perfiles con una sola consulta ordenada y valida todos antes de insertar RF-20.
     * @param solicitud cuerpo validado con perfiles distintos e intervalo común
     * @param actual actor ADMIN autenticado que crea cada bloqueo
     * @return bloqueos creados en orden ascendente de perfil
     * @throws NegocioException si hay ids duplicados o intervalo inválido (400), falta algún perfil (404),
     * empieza en el pasado (422) o hay conflictos (409 con reservas agrupadas por barberoId)
     */
    @Transactional
    public List<BloqueoDto> crearLote(CrearBloqueosLoteDto solicitud, UsuarioAutenticado actual) {
        if (new HashSet<>(solicitud.barberoIds()).size() != solicitud.barberoIds().size()) {
            throw new NegocioException("No repita perfiles en el lote.",
                    List.of(new ErrorCampo("barberoIds", "Los identificadores deben ser distintos.")));
        }
        return crearTodos(solicitud.barberoIds().stream().sorted().toList(), solicitud.inicio().toInstant(),
                solicitud.fin().toInstant(), solicitud.motivo(), actual, true);
    }

    /**
     * Obtiene solo el id del perfil, toma ② y vuelve a buscar el bloqueo antes del borrado físico RN-16.
     * @param id bloqueo a eliminar
     * @throws NegocioException si el bloqueo no existe o ya fue eliminado (404)
     */
    @Transactional
    public void eliminar(long id) {
        Long barberoId = bloqueos.buscarBarberoId(id).orElseThrow(this::noEncontrado);
        barberos.bloquearPorIds(List.of(barberoId));
        Bloqueo bloqueo = bloqueos.findById(id).orElseThrow(this::noEncontrado);
        bloqueos.delete(bloqueo);
        bloqueos.flush();
    }

    private List<BloqueoDto> crearTodos(List<Long> ids, Instant inicio, Instant fin, String motivo,
            UsuarioAutenticado actual, boolean lote) {
        if (!inicio.isBefore(fin)) {
            throw new NegocioException(ErrorCodigo.INTERVALO_INVALIDO, "El fin debe ser posterior al inicio.");
        }
        List<Barbero> perfiles = barberos.bloquearPorIds(ids);
        if (perfiles.size() != ids.size()) throw noEncontrado();
        Instant ahora = clock.instant();
        // Reevaluar después de esperar por ② evita crear un inicio que ya pasó durante la espera.
        if (inicio.isBefore(ahora)) {
            throw new NegocioException(ErrorCodigo.INICIO_EN_PASADO, "El bloqueo no puede empezar en el pasado.");
        }
        Map<Long, List<Long>> conflictos = new LinkedHashMap<>();
        for (Barbero perfil : perfiles) {
            List<Long> citas = reservas.buscarSolapamientos(perfil.getId(), inicio, fin, null)
                    .stream().map(Reserva::getId).toList();
            if (!citas.isEmpty()) conflictos.put(perfil.getId(), citas);
        }
        if (!conflictos.isEmpty()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO_CON_RESERVAS, "El bloqueo cruza reservas que ocupan franja.",
                    Map.of("reservas", lote ? conflictos : conflictos.get(ids.getFirst())));
        }
        var actor = usuarios.getReferenceById(actual.id());
        return bloqueos.saveAllAndFlush(perfiles.stream()
                .map(b -> new Bloqueo(b, inicio, fin, motivo, actor, ahora)).toList())
                .stream().map(BloqueoDto::desde).toList();
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el recurso solicitado.");
    }
}
