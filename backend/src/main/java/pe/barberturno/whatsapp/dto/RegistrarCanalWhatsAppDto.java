package pe.barberturno.whatsapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Alta o actualización manual del canal de un negocio por la plataforma (V2-DA-56).
 * @param wabaId cuenta de WhatsApp Business, de 1 a 32 dígitos
 * @param phoneNumberId identificador del número en la Cloud API, de 1 a 32 dígitos; único entre negocios (V2-RN-07)
 * @param numeroVisible número que ven los clientes: {@code +} opcional y de 6 a 20 dígitos
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record RegistrarCanalWhatsAppDto(
        @NotNull @Pattern(regexp = "[0-9]{1,32}", message = "La cuenta de WhatsApp Business debe tener solo dígitos.") String wabaId,
        @NotNull @Pattern(regexp = "[0-9]{1,32}", message = "El identificador del número debe tener solo dígitos.") String phoneNumberId,
        @NotNull @Pattern(regexp = "[+]?[0-9]{6,20}", message = "El número visible debe tener de 6 a 20 dígitos.") String numeroVisible) { }
