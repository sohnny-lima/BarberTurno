// Espejo de los DTO de la bandeja de WhatsApp (V2-11, docs/v2/16-v2-11-derivacion-bandeja-y-retencion.md §6).
// Ninguna regla de negocio aquí: puedeResponder y motivoNoResponder los calcula el servidor.

export type MotivoDerivacion = 'PETICION' | 'SIN_PROGRESO' | 'LIMITE' | 'HERRAMIENTA';

export type MotivoNoResponder = 'FUERA_DE_VENTANA' | 'NEGOCIO_NO_OPERATIVO';

export type OrigenMensaje = 'CLIENTE' | 'ASISTENTE' | 'SISTEMA' | 'PERSONA';

export type EstadoMensaje =
  'RECIBIDO' | 'PENDIENTE' | 'ENVIADO' | 'ENTREGADO' | 'LEIDO' | 'FALLIDO';

export interface ConversacionBandejaDto {
  id: number;
  cliente: string;
  /** Solo los 3 últimos dígitos; el número completo nunca llega al navegador. */
  telefonoEnmascarado: string;
  motivo: MotivoDerivacion;
  derivadaEn: string;
  ultimaEntradaEn: string | null;
  /** Vencimiento nominal de la ventana de 24 h. */
  ventanaAbiertaHasta: string | null;
  pendienteDeRespuesta: boolean;
  puedeResponder: boolean;
  motivoNoResponder: MotivoNoResponder | null;
}

export interface MensajeBandejaDto {
  id: number;
  entrante: boolean;
  origen: OrigenMensaje;
  /** Nulo si el contenido se retiró por la política de retención. */
  texto: string | null;
  retirado: boolean;
  estado: EstadoMensaje;
  /** Motivo de un envío fallido (p. ej. VENTANA_CERRADA, MODO_HUMANO), o nulo. */
  motivoFallo: string | null;
  creadoEn: string;
  enviadoPor: string | null;
}

export interface DetalleConversacionDto {
  conversacion: ConversacionBandejaDto;
  /** Hasta 50, en orden ascendente de id. */
  mensajes: MensajeBandejaDto[];
  hayAnteriores: boolean;
}

export interface RespuestaPersonalDto {
  texto: string;
}
