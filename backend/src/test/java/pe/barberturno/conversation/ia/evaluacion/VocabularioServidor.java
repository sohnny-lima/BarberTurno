package pe.barberturno.conversation.ia.evaluacion;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.conversation.CitaOpcion;
import pe.barberturno.conversation.MenusConversacion;
import pe.barberturno.conversation.MotivoNoVigente;
import pe.barberturno.conversation.ProfesionalOpcion;
import pe.barberturno.conversation.ResultadoOperacion;
import pe.barberturno.conversation.ResumenOperacion;
import pe.barberturno.conversation.ServicioOpcion;
import pe.barberturno.conversation.TextosConversacion;
import pe.barberturno.conversation.TipoOperacion;
import pe.barberturno.conversation.ia.ComposicionTurnoIA;
import pe.barberturno.reservations.EstadoReserva;
import tools.jackson.databind.JsonNode;

/**
 * Vocabulario que el servidor puede producir por sí mismo (V2-DA-86; 08 §4, «Texto del proveedor entregado»):
 * palabras de los textos fijos de V2-10 y de la IA, de los menús, del catálogo ficticio de la evaluación, de las
 * fechas en Lima y de los números. Se obtiene <strong>ejecutando</strong> los compositores del servidor con datos de
 * ejemplo (reflexión sobre sus métodos públicos), no copiando sus textos, para que un texto fijo nuevo entre solo.
 * <p>Uso: un fragmento del texto del proveedor que aparece literalmente en lo que recibe el cliente y contiene al
 * menos una palabra fuera de este vocabulario no puede haberlo producido el servidor: es una filtración. No es una
 * garantía semántica; es una prueba de procedencia.</p>
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
final class VocabularioServidor {

    private static final Instant INSTANTE = Instant.parse("2026-11-03T15:00:00Z");
    private static volatile Set<String> cache;

    private VocabularioServidor() { }

    /**
     * Palabras normalizadas que el servidor puede emitir.
     * @return conjunto inmutable
     */
    static Set<String> palabras() {
        Set<String> v = cache;
        if (v == null) {
            synchronized (VocabularioServidor.class) {
                if (cache == null) cache = Set.copyOf(construir());
                v = cache;
            }
        }
        return v;
    }

    /**
     * Separa un texto en palabras normalizadas: minúsculas, sin tildes ni signos.
     * @param texto texto libre; nulo equivale a vacío
     * @return palabras en orden
     */
    static List<String> tokens(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        String normal = Normalizer.normalize(texto.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return normal.isEmpty() ? List.of() : List.of(normal.split(" "));
    }

    /**
     * Dice si una palabra es del servidor: está en el vocabulario o es un número o un código.
     * @param palabra palabra normalizada
     * @return verdadero si el servidor puede emitirla
     */
    static boolean delServidor(String palabra) {
        return palabras().contains(palabra) || palabra.matches("\\p{N}+") || palabra.matches("bt\\p{N}*");
    }

    private static Set<String> construir() {
        var textos = new ArrayList<String>();
        var t = new TextosConversacion();
        var composicion = new ComposicionTurnoIA();
        var menus = new MenusConversacion();
        for (Object valor : invocar(t)) textos.add(valor.toString());
        for (Object valor : invocar(composicion)) textos.add(valor.toString());
        for (Object nodo : invocar(menus)) textos.add(nodo.toString());
        for (var tipo : TipoOperacion.values()) {
            for (var antes : List.of(Optional.<CitaOpcion>empty(), Optional.of(cita(EstadoReserva.CONFIRMADA)))) {
                textos.add(t.resumen(new ResumenOperacion(tipo, "Corte clásico", "Carlos", INSTANTE,
                        INSTANTE.plusSeconds(1800), 30, new BigDecimal("25.00"), antes)));
            }
            for (var estado : EstadoReserva.values()) textos.add(t.resultado(new ResultadoOperacion.Ejecutada(tipo, cita(estado), false)));
        }
        for (var motivo : MotivoNoVigente.values()) textos.add(t.resultado(new ResultadoOperacion.NoVigente(motivo)));
        textos.add(t.resultado(new ResultadoOperacion.Anulada()));
        for (var codigo : ErrorCodigo.values()) textos.add(t.rechazo(codigo));
        for (int d = 0; d < 400; d += 1) textos.add(t.fecha(LocalDate.of(2026, 1, 1).plusDays(d)));
        textos.addAll(FixturesEvaluacion.SERVICIOS);
        textos.addAll(FixturesEvaluacion.PROFESIONALES);
        // Nombre del negocio que siembra EscenarioEvaluacion y el de los datos de ejemplo de arriba.
        textos.addAll(List.of("Barbería de ensayo", "Barbería Ejemplo", "cualquiera"));
        Set<String> v = new HashSet<>();
        for (String texto : textos) v.addAll(tokens(texto));
        return v;
    }

    /** Ejecuta los métodos públicos del compositor con argumentos de ejemplo y recoge los textos y nodos que devuelve. */
    private static List<Object> invocar(Object compositor) {
        var salida = new ArrayList<Object>();
        for (Method m : compositor.getClass().getMethods()) {
            if (m.getDeclaringClass() == Object.class || Modifier.isStatic(m.getModifiers())) continue;
            Class<?> r = m.getReturnType();
            if (r != String.class && !JsonNode.class.isAssignableFrom(r)) continue;
            Object[] argumentos = new Object[m.getParameterCount()];
            boolean admitido = true;
            for (int i = 0; i < argumentos.length && admitido; i++) {
                argumentos[i] = ejemplo(m.getParameterTypes()[i]);
                admitido = argumentos[i] != null;
            }
            if (!admitido) continue;
            try {
                Object valor = m.invoke(compositor, argumentos);
                if (valor != null) salida.add(valor);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Un método que no admite los datos de ejemplo no aporta vocabulario; no es un error de la medición.
            }
        }
        return salida;
    }

    private static Object ejemplo(Class<?> tipo) {
        if (tipo == String.class) return "Barbería Ejemplo";
        if (tipo == int.class || tipo == Integer.class) return 1;
        if (tipo == long.class || tipo == Long.class) return 1L;
        if (tipo == Instant.class) return INSTANTE;
        if (tipo == LocalDate.class) return LocalDate.of(2026, 11, 3);
        if (tipo == BigDecimal.class) return new BigDecimal("25.00");
        if (tipo == List.class || tipo == Collection.class) return List.of();
        if (tipo == ServicioOpcion.class) return new ServicioOpcion(1, "Corte clásico", 30, new BigDecimal("25.00"));
        if (tipo == ProfesionalOpcion.class) return new ProfesionalOpcion(1, "Carlos");
        if (tipo == CitaOpcion.class) return cita(EstadoReserva.CONFIRMADA);
        return null;
    }

    private static CitaOpcion cita(EstadoReserva estado) {
        return new CitaOpcion(1, "BT-000123", 1, "Corte clásico", 1, "Carlos", INSTANTE, INSTANTE.plusSeconds(1800),
                new BigDecimal("25.00"), estado, 0, true, true);
    }
}
