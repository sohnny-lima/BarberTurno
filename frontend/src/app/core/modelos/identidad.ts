export type Rol = 'CLIENTE' | 'BARBERO' | 'ADMIN';

export interface UsuarioSesionDto {
  id: number;
  nombre: string;
  correo: string;
  rol: Rol;
  barberoId?: number | null;
  debeCambiarPassword: boolean;
}
export interface LoginDto {
  correo: string;
  password: string;
}
export interface RegistroDto extends LoginDto {
  nombre: string;
  telefono: string;
  aceptaPrivacidad: boolean;
}
export interface CambiarPasswordDto {
  passwordActual: string;
  passwordNueva: string;
}
export interface PerfilDto {
  id: number;
  nombre: string;
  correo: string;
  telefono: string | null;
  rol: Rol;
}
export interface ActualizarPerfilDto {
  nombre: string;
  telefono: string | null;
}
export interface ErrorCampo {
  campo: string;
  mensaje: string;
}
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  codigo: string;
  errores?: ErrorCampo[];
}
