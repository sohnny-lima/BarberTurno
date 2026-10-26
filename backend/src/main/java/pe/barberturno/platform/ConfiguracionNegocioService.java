package pe.barberturno.platform;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.platform.dto.ConfiguracionNegocioDto;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.users.UsuarioRepository;

/**
 * Lee y escribe únicamente los parámetros locales autorizados, sin caché ni cambios de identidad.
 * El cupo comparte el bloqueo previo de V2-04 con altas y cambios de estado de perfiles.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class ConfiguracionNegocioService {
    private final EntityManager entidades;
    private final ProveedorNegocioActual negocio;
    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final Clock clock;

    /**
     * Recibe persistencia transaccional, contexto y reloj del servidor.
     * @param entidades sesión con guarda de transacción y SQL acotado por negocio
     * @param negocio ámbito resuelto por la ruta
     * @param membresias pertenencias locales para revalidar la propiedad
     * @param usuarios estado vigente de la cuenta del actor
     * @param barberos bloqueo y recuento local del cupo
     * @param clock fuente única del instante de actualización
     */
    public ConfiguracionNegocioService(EntityManager entidades, ProveedorNegocioActual negocio,
            MembresiaRepository membresias, UsuarioRepository usuarios, BarberoRepository barberos, Clock clock) {
        this.entidades = entidades; this.negocio = negocio; this.membresias = membresias;
        this.usuarios = usuarios; this.barberos = barberos; this.clock = clock;
    }

    /**
     * Consulta los valores vigentes sin exponer atributos reservados a plataforma.
     * @param actor propietario activo del negocio actual
     * @return configuración actual, leída en esta transacción
     * @throws NegocioException si el actor no es propietario activo
     */
    @Transactional(readOnly = true)
    public ConfiguracionNegocioDto consultar(UsuarioAutenticado actor) {
        exigirPropietario(actor);
        Object[] fila = (Object[]) entidades.createNativeQuery("""
                select nombre, anticipacion_cambio_cliente_min, horizonte_dias,
                    limite_reservas_activas, limite_barberos_activos, confirmacion_manual,
                    tolerancia_inicio_min from negocio where id=:negocio
                """, Object[].class).setParameter("negocio", idNegocio()).getSingleResult();
        return new ConfiguracionNegocioDto((String) fila[0], ((Number) fila[1]).intValue(),
                ((Number) fila[2]).intValue(), ((Number) fila[3]).intValue(),
                ((Number) fila[4]).intValue(), (Boolean) fila[5], ((Number) fila[6]).intValue());
    }

    /**
     * Sustituye la configuración editable de forma atómica y sin superar el cupo ya ocupado.
     * El SQL enumera las columnas autorizadas por V112; no escribe estado, slug, zona ni rejilla.
     * @param datos valores completos validados por la API
     * @param actor propietario cuya membresía se revalida después del bloqueo previo
     * @return valores guardados disponibles para la siguiente petición
     * @throws NegocioException si falta propiedad, el negocio está suspendido o el cupo es inferior al ocupado
     */
    @Transactional
    public ConfiguracionNegocioDto actualizar(ConfiguracionNegocioDto datos, UsuarioAutenticado actor) {
        barberos.bloquearLimiteActivos(idNegocio());
        exigirPropietario(actor);
        String estado = (String) entidades.createNativeQuery("select estado from negocio where id=:negocio", String.class)
                .setParameter("negocio", idNegocio()).getSingleResult();
        if ("SUSPENDIDO".equals(estado)) throw new NegocioException(ErrorCodigo.NEGOCIO_SUSPENDIDO,
                "El negocio está suspendido y solo permite consultas.");
        if (barberos.countByActivoTrue(idNegocio()) > datos.limiteBarberosActivos()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO,
                    "El límite no puede ser inferior al número de barberos activos.");
        }
        entidades.createNativeQuery("""
                update negocio set nombre=:nombre, anticipacion_cambio_cliente_min=:anticipacion,
                    horizonte_dias=:horizonte, limite_reservas_activas=:reservas,
                    limite_barberos_activos=:barberos, confirmacion_manual=:manual,
                    tolerancia_inicio_min=:tolerancia, actualizado_en=:ahora where id=:negocio
                """).setParameter("nombre", datos.nombre()).setParameter("anticipacion", datos.anticipacionCambioClienteMin())
                .setParameter("horizonte", datos.horizonteDias()).setParameter("reservas", datos.limiteReservasActivas())
                .setParameter("barberos", datos.limiteBarberosActivos()).setParameter("manual", datos.confirmacionManual())
                .setParameter("tolerancia", datos.toleranciaInicioMin()).setParameter("ahora", clock.instant())
                .setParameter("negocio", idNegocio()).executeUpdate();
        return datos;
    }

    private void exigirPropietario(UsuarioAutenticado actor) {
        if (actor == null || !usuarios.findById(actor.id()).map(u -> u.isActivo()).orElse(false)
                || membresias.findByNegocioIdAndUsuarioId(idNegocio(), actor.id())
                    .filter(Membresia::isActiva).filter(m -> m.getRol() == RolNegocio.PROPIETARIO).isEmpty()) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Solo el propietario puede gestionar la configuración.");
        }
    }
    private long idNegocio() { return negocio.negocioActual().orElseThrow(); }
}
