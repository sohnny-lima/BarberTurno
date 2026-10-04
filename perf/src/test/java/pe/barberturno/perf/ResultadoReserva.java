package pe.barberturno.perf;

/**
 * Clasifica solamente los rechazos esperados autorizados para T-35.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
final class ResultadoReserva {
    private ResultadoReserva() { }

    static boolean esperado(int estado, String codigo) {
        return estado == 409 && "FRANJA_NO_DISPONIBLE".equals(codigo)
                || estado == 422 && "LIMITE_RESERVAS_ACTIVAS".equals(codigo);
    }
}
