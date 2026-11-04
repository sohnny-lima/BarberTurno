import { RolPersonal } from './negocios';

export interface EquipoDto {
  id: number;
  usuarioId: number;
  nombre: string;
  correo: string | null;
  rol: RolPersonal;
  estado: 'ACTIVA' | 'REVOCADA';
  motivoRevocacion: 'SALIDA' | 'DECISION_GESTOR' | 'PERFIL_DESACTIVADO' | 'PLATAFORMA' | null;
}
export interface InvitacionDto {
  id: number;
  correo: string;
  rol: RolPersonal;
  expiraEn: string;
}
export interface InvitacionEmitidaDto {
  invitacion: InvitacionDto;
  enlaceInvitacion: string;
}
export interface InvitacionPublicaDto {
  negocio: string;
  rol: RolPersonal;
  correoEnmascarado: string;
  estado: 'PENDIENTE' | 'CADUCADA' | 'REVOCADA' | 'USADA';
}
export interface RegistroInvitadoDto {
  nombre: string;
  telefono: string;
  password: string;
  aceptaPrivacidad: boolean;
}
export interface ConfiguracionNegocioDto {
  nombre: string;
  anticipacionCambioClienteMin: number;
  horizonteDias: number;
  limiteReservasActivas: number;
  limiteBarberosActivos: number;
  confirmacionManual: boolean;
  toleranciaInicioMin: number;
}
