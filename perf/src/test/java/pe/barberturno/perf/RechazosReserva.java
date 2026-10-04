package pe.barberturno.perf;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Acumula diagnósticos de POST inesperados sin conservar cuerpos, cuentas ni sesiones.
 * Los pares aceptados siguen definidos exclusivamente por ResultadoReserva.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
final class RechazosReserva {
    private record Rechazo(int estado, String codigo) { }
    private final ConcurrentHashMap<Rechazo, LongAdder> conteos = new ConcurrentHashMap<>();

    void registrar(int estado, String codigo) {
        if (estado == 201 || ResultadoReserva.esperado(estado, codigo)) return;
        String diagnostico = codigo == null || codigo.isBlank() ? "sin cuerpo" : codigo;
        conteos.computeIfAbsent(new Rechazo(estado, diagnostico), ignorado -> new LongAdder()).increment();
    }

    long total() {
        return conteos.values().stream().mapToLong(LongAdder::sum).sum();
    }

    // Solo se serializa al finalizar Gatling; orden estable y código escapado para una fila por par.
    String resumen() {
        StringBuilder salida = new StringBuilder("estado\tcodigo\tcantidad\n");
        conteos.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(
                        Comparator.comparingInt(Rechazo::estado).thenComparing(Rechazo::codigo)))
                .forEach(entrada -> salida.append(entrada.getKey().estado()).append('\t')
                        .append(URLEncoder.encode(entrada.getKey().codigo(), StandardCharsets.UTF_8).replace("+", "%20"))
                        .append('\t').append(entrada.getValue().sum()).append('\n'));
        return salida.toString();
    }
}
