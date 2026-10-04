import { ProblemDetail } from './identidad';

export interface JornadaDto {
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
}
export interface CrearBloqueoDto {
  inicio: string;
  fin: string;
  motivo: string;
}
export interface BloqueoDto extends CrearBloqueoDto {
  id: number;
  barberoId: number;
}
export interface BloqueoLoteDto extends CrearBloqueoDto {
  barberoIds: number[];
}
export interface ConflictoHorarios extends ProblemDetail {
  reservas?: number[] | Record<string, number[]>;
}
