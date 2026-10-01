import java.time.LocalDateTime;

/** Prueba reproducible de la clase de ejemplo, sin dependencias externas. */
public class ReservaServicePrueba {
    private static void verificar(boolean valor, String caso) {
        if (!valor) throw new AssertionError(caso);
        System.out.println("CORRECTO: " + caso);
    }
    public static void main(String[] args) {
        ReservaService s = new ReservaService();
        LocalDateTime t = LocalDateTime.of(2026, 10, 1, 10, 0);
        verificar(s.calcularFin(t, 30).equals(t.plusMinutes(30)), "Fin según duración");
        verificar(s.seSolapan(t, t.plusMinutes(30), t.plusMinutes(10), t.plusMinutes(40)), "Solapamiento parcial");
        verificar(!s.seSolapan(t, t.plusMinutes(30), t.plusMinutes(30), t.plusMinutes(50)), "Franjas contiguas permitidas");
        verificar(s.puedeModificar(t.minusHours(2), t, false, null), "Límite exacto de dos horas");
        verificar(!s.puedeModificar(t.minusMinutes(119), t, false, null), "Rechazo antes del mínimo");
        verificar(!s.puedeModificar(t, t, true, " ") && s.puedeModificar(t, t, true, "Solicitud del cliente"), "Excepción administrativa requiere motivo");
        boolean rechazo = false;
        try { s.calcularFin(t, 0); } catch (IllegalArgumentException e) { rechazo = true; }
        verificar(rechazo, "Duración inválida rechazada");
        System.out.println("Resultado: 7 comprobaciones correctas. Alcance: clase de ejemplo.");
    }
}
