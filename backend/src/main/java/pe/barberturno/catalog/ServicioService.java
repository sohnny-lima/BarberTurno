package pe.barberturno.catalog;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.catalog.dto.*;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.ManejadorErrores.ErrorCampo;
import pe.barberturno.common.error.NegocioException;

/**
 * Gestiona el catálogo sin modificar las referencias de las reservas existentes.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ServicioService {
    private final ServicioRepository servicios;
    private final Clock clock;

    /** @param servicios catálogo persistido
     * @param clock reloj para creación y actualización */
    public ServicioService(ServicioRepository servicios, Clock clock) {
        this.servicios = servicios;
        this.clock = clock;
    }

    /** @param incluirInactivos permiso de lectura concedido por el controlador
     * @return catálogo ordenado por nombre */
    @Transactional(readOnly = true)
    public List<ServicioDto> listar(boolean incluirInactivos) {
        return servicios.listar(incluirInactivos).stream().map(ServicioDto::desde).toList();
    }

    /** @param datos campos validados por MVC
     * @return servicio creado y activo
     * @throws NegocioException si la duración no es múltiplo de diez o el nombre está ocupado */
    @Transactional
    public ServicioDto crear(GuardarServicioDto datos) {
        validarDuracion(datos);
        comprobarNombre(datos.nombre(), null);
        return ServicioDto.desde(servicios.saveAndFlush(new Servicio(datos.nombre(), datos.descripcion(),
                datos.duracionMin().shortValue(), datos.precio(), clock.instant())));
    }

    /** @param id servicio que se edita
     * @param datos campos validados por MVC
     * @return servicio actualizado, conservando su estado
     * @throws NegocioException si no existe, la duración es inválida o el nombre está ocupado */
    @Transactional
    public ServicioDto editar(long id, GuardarServicioDto datos) {
        Servicio servicio = obtenerBloqueado(id);
        validarDuracion(datos);
        comprobarNombre(datos.nombre(), id);
        servicio.editar(datos.nombre(), datos.descripcion(), datos.duracionMin().shortValue(),
                datos.precio(), clock.instant());
        return ServicioDto.desde(servicios.saveAndFlush(servicio));
    }

    /** @param id servicio que se activa o desactiva
     * @param activo estado solicitado
     * @return servicio con su nuevo estado
     * @throws NegocioException si el servicio no existe */
    @Transactional
    public ServicioDto cambiarEstado(long id, boolean activo) {
        Servicio servicio = obtenerBloqueado(id);
        if (activo) servicio.activar(clock.instant());
        else servicio.desactivar(clock.instant());
        return ServicioDto.desde(servicios.saveAndFlush(servicio));
    }

    private Servicio obtenerBloqueado(long id) {
        return servicios.bloquearPorId(id).orElseThrow(() ->
                new NegocioException(ErrorCodigo.NO_ENCONTRADO, "El servicio solicitado no existe."));
    }

    private void comprobarNombre(String nombre, Long id) {
        servicios.buscarPorNombre(nombre).filter(s -> !s.getId().equals(id)).ifPresent(s -> {
            throw new NegocioException(ErrorCodigo.NOMBRE_DUPLICADO, "Ya existe un servicio con ese nombre.");
        });
        // saveAndFlush hace visible una colisión concurrente al manejador de servicio_nombre_uk.
    }

    private void validarDuracion(GuardarServicioDto datos) {
        if (datos.duracionMin() % 10 != 0) {
            throw new NegocioException("Revise los datos de la solicitud.",
                    List.of(new ErrorCampo("duracionMin", "La duración debe ser múltiplo de 10 minutos.")));
        }
    }
}
