package pe.barberturno.conversation.ia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la conversación con IA ({@code barberturno.ia}, 17-v2-12 §4.4 y §6). Sin clave la IA queda
 * desactivada y la aplicación arranca (CI-9). La clave nunca se registra.
 * @param habilitada interruptor global
 * @param clave clave del proveedor ({@code BT_IA_API_KEY}); vacía = desactivada (P2-03)
 * @param modelo identificador del modelo
 * @param esfuerzo esfuerzo del modelo ({@code low}, {@code medium}, {@code high})
 * @param urlBase URL base del proveedor (en las pruebas, el servidor local)
 * @param tiempoLlamada tiempo máximo de una llamada
 * @param plazoTurno tiempo máximo de un turno
 * @param maxTokens tokens de salida máximos por llamada
 * @param maxLlamadas llamadas al modelo por turno
 * @param maxHerramientas herramientas por respuesta
 * @param limites límites por defecto (las columnas de {@code negocio} los sustituyen)
 * @param precios precios por modelo, en USD por millón de tokens
 * @param trabajador cadencia del trabajador de turnos
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Validated
@ConfigurationProperties("barberturno.ia")
public record IaPropiedades(@DefaultValue("true") boolean habilitada,
        @DefaultValue("") String clave,
        @DefaultValue("claude-opus-5-5") @NotNull String modelo,
        @DefaultValue("low") @NotNull String esfuerzo,
        @DefaultValue("https://api.anthropic.com") @NotNull String urlBase,
        @DefaultValue("15s") @NotNull Duration tiempoLlamada,
        @DefaultValue("90s") @NotNull Duration plazoTurno,
        @DefaultValue("2048") @Positive int maxTokens,
        @DefaultValue("4") @Positive int maxLlamadas,
        @DefaultValue("4") @Positive int maxHerramientas,
        @DefaultValue @NotNull @Valid Limites limites,
        Map<String, Precio> precios,
        @DefaultValue @NotNull @Valid Trabajador trabajador) {

    /**
     * Normaliza los precios (sin precios configurados, mapa vacío: el coste se reserva con el máximo y la IA no
     * se considera disponible para un modelo sin precio).
     * @param habilitada interruptor global
     * @param clave clave del proveedor
     * @param modelo modelo
     * @param esfuerzo esfuerzo
     * @param urlBase URL base
     * @param tiempoLlamada tiempo por llamada
     * @param plazoTurno plazo del turno
     * @param maxTokens tokens de salida por llamada
     * @param maxLlamadas llamadas por turno
     * @param maxHerramientas herramientas por respuesta
     * @param limites límites por defecto
     * @param precios precios por modelo
     * @param trabajador trabajador de turnos
     * @throws IllegalArgumentException si el plazo del turno no supera el tiempo de una llamada: el trabajador
     *     no inicia una llamada sin un {@code tiempoLlamada} completo por delante, así que ningún turno llamaría
     *     al modelo (g095-3)
     */
    public IaPropiedades {
        precios = precios == null ? Map.of() : Map.copyOf(precios);
        if (tiempoLlamada != null && plazoTurno != null && plazoTurno.compareTo(tiempoLlamada) <= 0)
            throw new IllegalArgumentException("barberturno.ia.plazo-turno debe ser mayor que barberturno.ia.tiempo-llamada.");
    }

    /**
     * Si hay clave y modelo (no dice nada de los límites ni del negocio).
     * @return verdadero si la IA está configurada y habilitada
     */
    public boolean configurada() {
        return habilitada && clave != null && !clave.isBlank() && modelo != null && !modelo.isBlank();
    }

    /**
     * Límites por defecto (V2-RN-14).
     * @param entradasMinuto mensajes entrantes por minuto y conversación
     * @param llamadasDia llamadas al modelo por día y conversación
     * @param presupuestoMensualUsd presupuesto mensual por negocio, en USD
     * @param avisoPendienteMax antigüedad tras la que un turno pendiente se da por abandonado
     */
    public record Limites(@DefaultValue("6") @Positive int entradasMinuto,
            @DefaultValue("20") @Positive int llamadasDia,
            @DefaultValue("5") @NotNull BigDecimal presupuestoMensualUsd,
            @DefaultValue("15m") @NotNull Duration avisoPendienteMax) { }

    /**
     * Precio de un modelo, en USD por millón de tokens.
     * @param entrada entrada no cacheada
     * @param salida salida
     * @param cacheLectura lectura de caché
     * @param cacheEscritura escritura de caché
     */
    public record Precio(@NotNull BigDecimal entrada, @NotNull BigDecimal salida, @NotNull BigDecimal cacheLectura,
            @NotNull BigDecimal cacheEscritura) { }

    /**
     * Trabajador de turnos.
     * @param habilitado si se ejecuta el programado
     * @param intervalo periodo entre pasadas
     * @param lote turnos como máximo por negocio y pasada
     */
    public record Trabajador(@DefaultValue("true") boolean habilitado,
            @DefaultValue("1s") @NotNull Duration intervalo,
            @DefaultValue("5") @Positive int lote) { }
}
