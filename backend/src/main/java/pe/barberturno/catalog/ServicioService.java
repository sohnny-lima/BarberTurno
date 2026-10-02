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
 * Gestiona RF-04 preservando las referencias históricas RN-13 y la desactivación lógica RN-16.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ServicioService {
    private final ServicioRepository servicios;
    private final Clock clock;

    /**
     * Inyecta catálogo y Clock para fechar cambios RF-04 sin depender del reloj global.
     * @param servicios catálogo persistido
     * @param clock reloj para creación y actualización
     */
    public ServicioService(ServicioRepository servicios, Clock clock) {
        this.servicios = servicios;
        this.clock = clock;
    }

    /**
     * Proyecta el catálogo al DTO público; el controlador autoriza a ADMIN para incluir inactivos.
     * @param incluirInactivos permiso de lectura concedido por el controlador
     * @return catálogo ordenado por nombre
     */
    @Transactional(readOnly = true)
    public List<ServicioDto> listar(boolean incluirInactivos) {
        return servicios.listar(incluirInactivos).stream().map(ServicioDto::desde).toList();
    }

    /**
     * Valida duración y unicidad del nombre y persiste el servicio activo con saveAndFlush; las colisiones
     * concurrentes se traducen por servicio_nombre_uk.
     * @param datos campos validados por MVC
     * @return servicio creado y activo
     * @throws NegocioException si la duración no es múltiplo de diez (VALIDACION) o el nombre está ocupado
     * (NOMBRE_DUPLICADO)
     */
    @Transactional
    public ServicioDto crear(GuardarServicioDto datos) {
        validarDuracion(datos);
        comprobarNombre(datos.nombre(), null);
        return ServicioDto.desde(servicios.saveAndFlush(new Servicio(datos.nombre(), datos.descripcion(),
                datos.duracionMin().shortValue(), datos.precio(), clock.instant())));
    }

    /**
     * Serializa con PESSIMISTIC_WRITE y conserva estado y referencias RN-13 de reservas existentes; saveAndFlush
     * expone las colisiones de unicidad.
     * @param id servicio que se edita
     * @param datos campos validados por MVC
     * @return servicio actualizado, conservando su estado
     * @throws NegocioException si el servicio no existe (NO_ENCONTRADO), la duración no es múltiplo de diez
     * (VALIDACION) o el nombre está ocupado (NOMBRE_DUPLICADO)
     */
    @Transactional
    public ServicioDto editar(long id, GuardarServicioDto datos) {
        Servicio servicio = obtenerBloqueado(id);
        validarDuracion(datos);
        comprobarNombre(datos.nombre(), id);
        servicio.editar(datos.nombre(), datos.descripcion(), datos.duracionMin().shortValue(),
                datos.precio(), clock.instant());
        return ServicioDto.desde(servicios.saveAndFlush(servicio));
    }

    /**
     * Serializa con PESSIMISTIC_WRITE la activación o desactivación; conserva fila y reservas históricas
     * (RN-16).
     * @param id servicio que se activa o desactiva
     * @param activo estado solicitado
     * @return servicio con su nuevo estado
     * @throws NegocioException si el servicio no existe (NO_ENCONTRADO)
     */
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
