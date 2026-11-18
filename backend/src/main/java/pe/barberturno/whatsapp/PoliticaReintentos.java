package pe.barberturno.whatsapp;

import java.time.Duration;

/**
 * Espera exponencial pura y saturada, sin desbordar al recuperar reclamos antiguos.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
final class PoliticaReintentos {
    private PoliticaReintentos() { }

    static Duration espera(int intentos, Duration base, Duration maxima) {
        if (intentos < 1 || base.isNegative() || base.isZero() || maxima.compareTo(base) < 0) {
            throw new IllegalArgumentException("La espera requiere intentos y duraciones positivos y un tope válido.");
        }
        Duration espera = base;
        for (int i = 1; i < intentos && espera.compareTo(maxima) < 0; i++) {
            if (espera.compareTo(maxima.dividedBy(2)) > 0) return maxima;
            espera = espera.multipliedBy(2);
        }
        return espera.compareTo(maxima) > 0 ? maxima : espera;
    }
}
