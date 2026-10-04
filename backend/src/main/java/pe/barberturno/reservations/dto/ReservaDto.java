package pe.barberturno.reservations.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.*;
import pe.barberturno.users.Rol;

/**
 * Proyección §6.3 con referencias RN-13 y permisos calculados únicamente por el servidor (DA-15).
 * @param id identidad persistida
 * @param codigo código público BT seguido de la identidad
 * @param cliente propietario con teléfono visible solo para personal
 * @param barbero perfil de atención
 * @param servicio servicio contratado
 * @param inicio inicio inclusivo con desfase de Lima
 * @param fin fin exclusivo con desfase de Lima
 * @param duracionMin minutos copiados al crear
 * @param precioRef precio referencial copiado, en soles
 * @param estado estado vigente RN-10
 * @param version versión optimista vigente
 * @param permisos acciones disponibles para el actor
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ReservaDto(long id, String codigo, ClienteDto cliente, RecursoDto barbero,
        RecursoDto servicio, OffsetDateTime inicio, OffsetDateTime fin, short duracionMin,
        BigDecimal precioRef, EstadoReserva estado, int version, PermisosDto permisos) {
    /**
     * Datos mínimos del propietario; omite el teléfono cuando el actor es CLIENTE.
     * @param id identidad del propietario
     * @param nombre nombre visible
     * @param telefono contacto para personal o nulo
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record ClienteDto(long id, String nombre,
            @JsonInclude(JsonInclude.Include.NON_NULL) String telefono) { }

    /**
     * Identidad y nombre de un recurso contratado, sin datos internos del catálogo o personal.
     * @param id identidad persistida
     * @param nombre nombre visible
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record RecursoDto(long id, String nombre) { }

    /**
     * Capacidades por propiedad, estado y ventana RN-07/08/11/12.
     * @param reprogramar permite cambiar una CONFIRMADA
     * @param cancelar permite cancelar PENDIENTE o CONFIRMADA
     * @param transiciones destinos operativos permitidos, excluye CANCELADA
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record PermisosDto(boolean reprogramar, boolean cancelar,
            List<EstadoReserva> transiciones) { }

    /**
     * Mapea dentro de la transacción y combina identidad con las políticas puras.
     * El permiso administrativo indica capacidad; la acción posterior exigirá un motivo real.
     * @param reserva entidad con relaciones accesibles
     * @param actor identidad revalidada que recibe el DTO
     * @param ahora instante de evaluación del Clock
     * @param parametros ventanas configuradas de negocio
     * @return proyección sin teléfono para clientes ni permisos sobre reservas ajenas
     */
    public static ReservaDto desde(Reserva reserva, UsuarioAutenticado actor, Instant ahora,
            ParametrosReserva parametros) {
        boolean admin = actor.rol() == Rol.ADMIN;
        boolean propietario = actor.rol() == Rol.CLIENTE && actor.id() == reserva.getCliente().getId();
        boolean asignado = actor.barberoId().filter(reserva.getBarbero().getId()::equals).isPresent();
        var reglas = new ReglasTemporales();
        var politica = new PoliticaTransiciones();
        boolean modificable = (admin || propietario) && reglas.puedeModificar(ahora,
                reserva.getInicio(), admin, admin ? "Motivo requerido" : null,
                parametros.anticipacionCambioCliente());
        boolean cancelar = modificable && politica.evaluar(actor.rol(), asignado,
                reserva.getEstado(), EstadoReserva.CANCELADA, ahora, reserva.getInicio(),
                Duration.ofMinutes(parametros.toleranciaInicioMin()), parametros.anticipacionCambioCliente(),
                admin ? "Motivo requerido" : null).permitida();
        var destinos = Arrays.stream(EstadoReserva.values())
                .filter(e -> e != EstadoReserva.CANCELADA)
                .filter(e -> politica.evaluar(actor.rol(), asignado, reserva.getEstado(), e, ahora,
                        reserva.getInicio(), Duration.ofMinutes(parametros.toleranciaInicioMin()),
                        parametros.anticipacionCambioCliente(), null).permitida()).toList();
        return new ReservaDto(reserva.getId(), "BT-" + reserva.getId(),
                new ClienteDto(reserva.getCliente().getId(), reserva.getCliente().getNombre(),
                        actor.rol() == Rol.CLIENTE ? null : reserva.getCliente().getTelefono()),
                new RecursoDto(reserva.getBarbero().getId(), reserva.getBarbero().getUsuario().getNombre()),
                new RecursoDto(reserva.getServicio().getId(), reserva.getServicio().getNombre()),
                TiempoNegocio.aLima(reserva.getInicio()), TiempoNegocio.aLima(reserva.getFin()),
                reserva.getDuracionRefMin(), reserva.getPrecioRef().setScale(2), reserva.getEstado(),
                reserva.getVersion(), new PermisosDto(modificable && reserva.getEstado() == EstadoReserva.CONFIRMADA,
                        cancelar, destinos));
    }
}
