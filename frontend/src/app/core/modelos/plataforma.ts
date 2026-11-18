export interface NegocioPlataformaDto {
  id: number;
  slug: string;
  nombre: string;
  estado: 'ACTIVO' | 'SUSPENDIDO';
  miembrosActivos: number;
}
export interface CrearNegocioDto {
  nombre: string;
  slug: string;
  correoPropietario: string;
}
export interface NegocioCreadoDto extends NegocioPlataformaDto {
  enlaceInvitacion: string;
}
