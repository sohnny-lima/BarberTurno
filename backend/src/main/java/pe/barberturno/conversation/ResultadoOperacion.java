package pe.barberturno.conversation;

import pe.barberturno.common.error.ErrorCodigo;

/**
 * Resultado de confirmar o rechazar una operación pendiente. Los textos de resultado se componen solo a
 * partir de este valor, que refleja lo confirmado en la base de datos (V2-RN-11).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface ResultadoOperacion {

    /**
     * La operación se ejecutó (ahora o en una pulsación anterior).
     * @param tipo operación ejecutada
     * @param cita reserva resultante leída tras ejecutar (o la cancelada)
     * @param repetida verdadero si ya estaba {@code EJECUTADA} y no se volvió a ejecutar
     */
    record Ejecutada(TipoOperacion tipo, CitaOpcion cita, boolean repetida) implements ResultadoOperacion { }

    /**
     * El servicio de reservas la rechazó por una regla del negocio; la operación queda {@code RECHAZADA}
     * y no hay reserva, auditoría ni aviso nuevos.
     * @param codigo regla incumplida
     */
    record Rechazada(ErrorCodigo codigo) implements ResultadoOperacion { }

    /**
     * No se puede ejecutar; nada cambió salvo, si caducó, su paso a {@code CADUCADA}.
     * @param motivo causa, sin revelar operaciones ajenas
     */
    record NoVigente(MotivoNoVigente motivo) implements ResultadoOperacion { }

    /** El cliente rechazó la propuesta vigente («Cambiar» o «no»); queda {@code ANULADA}. */
    record Anulada() implements ResultadoOperacion { }
}
