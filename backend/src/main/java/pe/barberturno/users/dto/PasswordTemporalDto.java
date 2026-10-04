package pe.barberturno.users.dto;

/**
 * Credencial RF-19 entregada una sola vez al ADMIN; nunca se persiste en claro.
 * @param passwordTemporal contraseña de doce caracteres que cumple RN-25
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record PasswordTemporalDto(String passwordTemporal) {
    /** {@return representación segura que evita revelar la credencial al registrar el DTO} */
    @Override public String toString() { return "PasswordTemporalDto[credencial omitida]"; }
}
