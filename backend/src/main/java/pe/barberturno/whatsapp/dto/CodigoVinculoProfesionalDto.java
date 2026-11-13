package pe.barberturno.whatsapp.dto;

import java.time.Instant;

/**
 * Capacidad de un uso mostrada solo al generar el código desde la cuenta profesional.
 * @param codigo ocho caracteres presentados en dos grupos de cuatro
 * @param caducaEn límite exclusivo de uso, diez minutos después de la generación
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record CodigoVinculoProfesionalDto(String codigo, Instant caducaEn) {
    /** @return descripción que evita revelar la capacidad en registros accidentales */
    @Override
    public String toString() { return "CodigoVinculoProfesionalDto[oculto]"; }
}
