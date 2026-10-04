package pe.barberturno.scheduling;

import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.scheduling.dto.DisponibilidadDto;

/**
 * Consulta pública RF-07/RF-21; la exclusión para reprogramar se autoriza en el servicio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
public class DisponibilidadController {
    private final DisponibilidadService disponibilidad;

    /**
     * Conecta el contrato HTTP con la disponibilidad calculada en Java.
     * @param disponibilidad servicio de cálculo y autorización de exclusiones
     */
    public DisponibilidadController(DisponibilidadService disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    /**
     * Consulta franjas completas del servicio sin exigir sesión salvo para excluir una reserva.
     * @param servicioId identidad positiva del servicio
     * @param fecha día yyyy-MM-dd del negocio
     * @param barberoId perfil positivo opcional; omitido consulta todos los activos
     * @param excluirReservaId reserva positiva opcional del cliente propietario o ADMIN
     * @return disponibilidad con instantes en Lima y perfiles ordenados
     * @throws NegocioException si una identidad opcional es inválida (400), un recurso no existe
     * o es ajeno (404), o está inactivo (422)
     */
    @GetMapping("/api/disponibilidad")
    public DisponibilidadDto consultar(@RequestParam @Positive long servicioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String barberoId,
            @RequestParam(required = false) String excluirReservaId) {
        return disponibilidad.consultarFranjas(servicioId, identidadOpcional(barberoId), fecha,
                identidadOpcional(excluirReservaId));
    }

    private Optional<Long> identidadOpcional(String valor) {
        if (valor == null) return Optional.empty();
        try {
            long id = Long.parseLong(valor);
            if (id > 0) return Optional.of(id);
        } catch (NumberFormatException error) {
            // Un parámetro presente vacío o ilegible conserva VALIDACION, sin mostrar su valor.
        }
        throw new NegocioException(ErrorCodigo.VALIDACION, "Indique una identidad positiva válida.");
    }
}
