package pe.barberturno.platform.dto;

/**
 * Negocio al que la cuenta puede acceder, con prioridad del personal sobre el papel de cliente.
 * @param slug identificador público del negocio
 * @param nombre nombre público del negocio
 * @param rol PROPIETARIO, ENCARGADO, BARBERO o CLIENTE
 * @param papeles membresía activa primero, si existe, y CLIENTE solo con ficha activa
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record NegocioPropioDto(String slug, String nombre, String rol, java.util.List<String> papeles) { }
