import { Rol } from './identidad';
export interface UsuarioAdminDto {
  id: number;
  nombre: string;
  correo: string;
  telefono: string | null;
  rol: Rol;
  activo: boolean;
  debeCambiarPassword: boolean;
  barberoId?: number | null;
}
export interface ConsultaUsuarios {
  q?: string;
  rol?: Rol;
  pagina: number;
  tamano: number;
}
