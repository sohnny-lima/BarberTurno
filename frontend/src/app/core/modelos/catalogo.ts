export interface ServicioDto {
  id: number;
  nombre: string;
  descripcion: string;
  duracionMin: number;
  precio: number;
  activo: boolean;
}
export interface GuardarServicioDto {
  nombre: string;
  descripcion: string;
  duracionMin: number;
  precio: number;
}
export interface BarberoDto {
  id: number;
  nombre: string;
  especialidad: string;
  activo: boolean;
  correo?: string | null;
  telefono?: string | null;
}
export type CrearBarberoDto =
  | {
      nombre: string;
      correo: string;
      telefono?: string | null;
      especialidad: string;
      passwordTemporal: string;
    }
  | { usuarioId: number; especialidad: string };
export interface EditarBarberoDto {
  nombre: string;
  telefono?: string | null;
  especialidad: string;
}
export interface CambiarEstadoBarberoDto {
  barbero: BarberoDto;
  reservasFuturasVigentes: number;
}
