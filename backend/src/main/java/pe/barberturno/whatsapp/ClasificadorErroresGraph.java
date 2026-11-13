package pe.barberturno.whatsapp;

import java.util.OptionalInt;

/**
 * Clasifica rechazos por el código de Meta antes de considerar el estado HTTP (contrato §4.2).
 * No conserva mensajes ni detalles que puedan contener datos personales.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
final class ClasificadorErroresGraph {
    private ClasificadorErroresGraph() { }

    static ResultadoEnvio.Rechazado clasificar(int http, OptionalInt codigo) {
        MotivoFallo motivo = null;
        boolean transitorio = false;
        if (codigo.isPresent()) {
            switch (codigo.getAsInt()) {
                case 130429, 131000, 131016, 131056, 1, 2, 4 -> transitorio = true;
                case 131047 -> motivo = MotivoFallo.VENTANA_CERRADA;
                case 131026, 131021, 131051 -> motivo = MotivoFallo.DESTINO_NO_VALIDO;
                case 132000, 132001, 132005, 132007, 132012, 132015, 132016 -> motivo = MotivoFallo.PLANTILLA_RECHAZADA;
                case 0, 10, 190, 200, 131005 -> motivo = MotivoFallo.CREDENCIAL_RECHAZADA;
                case 368, 131031, 131042, 131045, 131048, 131057, 133010 -> motivo = MotivoFallo.CUENTA_RESTRINGIDA;
                default -> { }
            }
        }
        if (motivo == null && !transitorio) {
            transitorio = http == 429 || http >= 500;
            if (!transitorio) motivo = MotivoFallo.SOLICITUD_RECHAZADA;
        }
        return new ResultadoEnvio.Rechazado(http, codigo, transitorio, motivo);
    }
}
