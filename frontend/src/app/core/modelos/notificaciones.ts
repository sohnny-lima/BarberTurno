export interface NotificacionDto {
  id: number;
  /** Reserva a la que se refiere el aviso, o nulo si es de una conversación (V2-11). */
  reservaId: number | null;
  /** Conversación derivada (aviso DERIVACION), o nulo si es de una reserva. */
  conversacionId: number | null;
  tipo:
    | 'CREAR'
    | 'CONFIRMAR'
    | 'REPROGRAMAR'
    | 'CANCELAR'
    | 'INICIAR'
    | 'COMPLETAR'
    | 'NO_ASISTIO'
    | 'WHATSAPP'
    | 'DERIVACION';
  mensaje: string;
  leida: boolean;
  creadoEn: string;
}
