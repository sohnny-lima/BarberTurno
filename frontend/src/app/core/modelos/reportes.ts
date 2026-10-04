import { EstadoReserva } from './reservas';

export interface ConsultaResumen {
  desde: string;
  hasta: string;
  servicioId?: number;
  barberoId?: number;
}

export interface ConteoReporteDto {
  id: number;
  nombre: string;
  total: number;
}

export interface ResumenReporteDto {
  total: number;
  porEstado: Record<EstadoReserva, number>;
  porServicio: ConteoReporteDto[];
  porBarbero: ConteoReporteDto[];
}
