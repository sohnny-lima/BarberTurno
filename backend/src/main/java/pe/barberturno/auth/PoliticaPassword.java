package pe.barberturno.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Política pura RN-25 con el límite de entrada UTF-8 de BCrypt.
 * Nunca incorpora el texto recibido a los incumplimientos.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class PoliticaPassword {
    /**
     * Crea la política sin estado.
     */
    public PoliticaPassword() { }

    /**
     * Incumplimientos que el servicio puede traducir a mensajes de validación.
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public enum Incumplimiento {
    /**
     * Contraseña ausente, se requiere propuesta válida.
     */
    OBLIGATORIA,
    /**
     * Contraseña fuera de ocho a 72 puntos de código.
     */
    LONGITUD,
        /**
         * Contraseña que supera los 72 bytes UTF-8 admitidos por BCrypt.
         */
        BYTES_UTF8,
    /**
     * Contraseña sin ningún carácter reconocido como letra.
     */
    LETRA,
    /**
     * Contraseña sin ningún carácter reconocido como dígito.
     */
    DIGITO
    }

    /**
     * Comprueba entre ocho y 72 puntos de código, máximo 72 bytes, letra y dígito.
     * @param password texto propuesto; null representa ausencia
     * @return lista inmutable de todos los incumplimientos, vacía si es válido
     */
    public List<Incumplimiento> validar(String password) {
        if (password == null) return List.of(Incumplimiento.OBLIGATORIA);
        List<Incumplimiento> incumplimientos = new ArrayList<>();
        int caracteres = password.codePointCount(0, password.length());
        if (caracteres < 8 || caracteres > 72) incumplimientos.add(Incumplimiento.LONGITUD);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) incumplimientos.add(Incumplimiento.BYTES_UTF8);
        if (password.codePoints().noneMatch(Character::isLetter)) incumplimientos.add(Incumplimiento.LETRA);
        if (password.codePoints().noneMatch(Character::isDigit)) incumplimientos.add(Incumplimiento.DIGITO);
        return List.copyOf(incumplimientos);
    }
}
