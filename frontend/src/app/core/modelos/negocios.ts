import { UsuarioSesionDto } from './identidad';

export type Papel = 'PERSONAL' | 'CLIENTE';
export type RolPersonal = 'PROPIETARIO' | 'ENCARGADO' | 'BARBERO';
export interface NegocioPropioDto {
  slug: string;
  nombre: string;
  rol: RolPersonal | 'CLIENTE';
  papeles: (RolPersonal | 'CLIENTE')[];
}
export interface NegocioPublicoDto {
  slug: string;
  nombre: string;
  horizonteDias: number;
  anticipacionCambioClienteMin: number;
  limiteReservasActivas: number;
  confirmacionManual: boolean;
}
export interface AmbitoNegocio {
  slug: string;
  papel: Papel;
  generacion: number;
}
export interface EstadoNegocio {
  tipo: 'cargando' | 'disponible' | 'suspendido' | 'inexistente' | 'error';
  sesion: UsuarioSesionDto | null;
  parametros: NegocioPublicoDto | null;
}

export const NOMBRES_PAPEL = {
  PROPIETARIO: 'Propietario',
  ENCARGADO: 'Encargado',
  BARBERO: 'Barbero',
  CLIENTE: 'Cliente',
};
