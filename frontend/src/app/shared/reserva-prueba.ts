// Datos ficticios compartidos exclusivamente por pruebas.
import { ReservaDto } from '../core/modelos/reservas';

export const RESERVA_PRUEBA: ReservaDto = {
  id: 101,
  codigo: 'BT-101',
  cliente: { id: 1, nombre: 'Cliente ficticio' },
  servicio: { id: 2, nombre: 'Corte clásico' },
  barbero: { id: 3, nombre: 'Profesional ficticio' },
  inicio: '2026-10-05T10:00:00-05:00',
  fin: '2026-10-05T10:30:00-05:00',
  duracionMin: 30,
  precioRef: 25,
  estado: 'CONFIRMADA',
  version: 7,
  permisos: { reprogramar: true, cancelar: true, transiciones: [] },
};
