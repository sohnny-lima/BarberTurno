export interface NotificacionDto {
  id: number;
  reservaId: number;
  tipo: 'CREAR' | 'CONFIRMAR' | 'REPROGRAMAR' | 'CANCELAR' | 'INICIAR' | 'COMPLETAR' | 'NO_ASISTIO';
  mensaje: string;
  leida: boolean;
  creadoEn: string;
}
