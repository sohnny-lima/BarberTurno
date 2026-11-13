package pe.barberturno.conversation.ia;

import java.time.LocalDate;
import java.util.*;
import pe.barberturno.conversation.*;

/**
 * Tabla efímera de capacidades del turno; nunca se comparte ni persiste. Los mapas por finalidad
 * impiden interpretar un alias como otro tipo de recurso y solo contienen lecturas bajo RLS.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class ContextoTurno {
    final long negocioId;
    final OptionalLong ficha;
    final LocalDate hoy;
    final Map<String, ServicioOpcion> servicios = new LinkedHashMap<>();
    final Map<String, ProfesionalOpcion> profesionales = new LinkedHashMap<>();
    final Map<String, CitaOpcion> citas = new LinkedHashMap<>();
    final Map<String, FranjaValidada> franjas = new LinkedHashMap<>();
    Long servicioBorrador;
    // null = sin elegir; 0 = cualquiera; positivo = profesional concreto validado.
    Long profesionalBorrador;
    LocalDate diaBorrador;
    // null = no hubo búsqueda válida; una búsqueda fallida no altera el último resultado válido.
    Boolean ultimaBusquedaVacia;
    DecisionRespuesta decision;

    static boolean ultimaValida(String ultima) {
        return Set.of("MOSTRAR_FRANJAS", "PROPUESTA", "CONSENTIMIENTO", "DERIVAR", "NEUTRO",
                "MOSTRAR_SERVICIOS", "MOSTRAR_PROFESIONALES", "MOSTRAR_CITAS", "SIN_DISPONIBILIDAD",
                "FUERA_DE_ALCANCE", "SALUDO", "PEDIR_DATO:SERVICIO", "PEDIR_DATO:PROFESIONAL",
                "PEDIR_DATO:DIA", "PEDIR_DATO:CITA").contains(ultima);
    }

    /**
     * Crea un ámbito de alias vacío para una única resolución.
     * @param negocioId negocio fijado por el canal, nunca por argumentos del modelo
     * @param ficha ficha leída de la conversación, o vacía
     * @param hoy fecha del reloj del servidor en Lima
     * @throws NullPointerException si falta la ficha opcional o la fecha del negocio
     */
    public ContextoTurno(long negocioId, OptionalLong ficha, LocalDate hoy) {
        this.negocioId = negocioId;
        this.ficha = Objects.requireNonNull(ficha);
        this.hoy = Objects.requireNonNull(hoy);
    }

    String servicio(ServicioOpcion servicio) {
        return alias(servicios, servicio, "s");
    }

    String profesional(ProfesionalOpcion profesional) {
        return alias(profesionales, profesional, "p");
    }

    String cita(CitaOpcion cita) {
        return alias(citas, cita, "c");
    }

    private static <T> String alias(Map<String, T> mapa, T valor, String prefijo) {
        for (var entrada : mapa.entrySet()) if (entrada.getValue().equals(valor)) return entrada.getKey();
        String nuevo = prefijo + (mapa.size() + 1);
        mapa.put(nuevo, valor);
        return nuevo;
    }

    FranjaOfrecida franja(long servicioId, Long citaId, FranjaOpcion opcion) {
        var seleccion = citaId == null
                ? new CodigoSeleccion.Franja(servicioId, opcion.barberoId(), opcion.inicio().getEpochSecond())
                : new CodigoSeleccion.FranjaCambio(citaId, opcion.inicio().getEpochSecond());
        // La capacidad conserva también la finalidad; una franja de cambio nunca sirve para crear.
        var ofrecida = new FranjaOfrecida("f" + (franjas.size() + 1), opcion.inicio(), opcion.fin(),
                opcion.profesional(), seleccion);
        franjas.put(ofrecida.alias(), new FranjaValidada(servicioId, opcion.barberoId(), citaId, ofrecida));
        return ofrecida;
    }

    record FranjaValidada(long servicioId, long barberoId, Long citaId, FranjaOfrecida ofrecida) { }
}
