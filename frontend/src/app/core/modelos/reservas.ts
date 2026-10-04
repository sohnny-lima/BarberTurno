export interface FranjaDto {
  inicio: string;
  fin: string;
  barberoIds: number[];
}
export interface DisponibilidadDto {
  fecha: string;
  servicioId: number;
  duracionMin: number;
  franjas: FranjaDto[];
}
export type EstadoReserva =
  'PENDIENTE' | 'CONFIRMADA' | 'EN_ATENCION' | 'COMPLETADA' | 'NO_ASISTIO' | 'CANCELADA';
export interface ReservaDto {
  id: number;
  codigo: string;
  cliente: { id: number; nombre: string; telefono?: string | null };
  barbero: { id: number; nombre: string };
  servicio: { id: number; nombre: string };
  inicio: string;
  fin: string;
  duracionMin: number;
  precioRef: number;
  estado: EstadoReserva;
  version: number;
  permisos: { reprogramar: boolean; cancelar: boolean; transiciones: EstadoReserva[] };
}
export interface CrearReservaDto {
  servicioId: number;
  barberoId: number;
  inicio: string;
  clienteId?: number;
}
export interface ReprogramarReservaDto {
  inicio: string;
  barberoId?: number;
  version: number;
  motivo?: string;
}
export interface ConsultaDisponibilidad {
  servicioId: number;
  fecha: string;
  barberoId?: number;
  excluirReservaId?: number;
}
export interface CancelarReservaDto {
  version: number;
  motivo?: string;
}
export interface ConsultaMisReservas {
  estado?: EstadoReserva;
  desde?: string;
  hasta?: string;
  pagina: number;
  tamano: number;
}

export interface ConsultaAgenda {
  desde: string;
  hasta: string;
  barberoId?: number;
  servicioId?: number;
  pagina: number;
  tamano: number;
}
export type EstadoTransicion = 'CONFIRMADA' | 'EN_ATENCION' | 'COMPLETADA' | 'NO_ASISTIO';
export interface TransicionReservaDto {
  estado: EstadoTransicion;
  version: number;
}
export interface DatosAuditoria {
  inicio?: string;
  fin?: string;
  barberoId?: number;
  estado?: EstadoReserva;
}
export interface AuditoriaDto {
  accion:
    'CREAR' | 'REPROGRAMAR' | 'CANCELAR' | 'CONFIRMAR' | 'INICIAR' | 'COMPLETAR' | 'NO_ASISTIO';
  actorNombre: string;
  creadoEn: string;
  estadoAnterior: EstadoReserva | null;
  estadoNuevo: EstadoReserva;
  datosAnteriores: DatosAuditoria | null;
  datosNuevos: DatosAuditoria;
  motivo: string | null;
  excepcional: boolean;
}
