import { fechaHoyLima } from './instante-lima';

/** Primer y último día del mes civil actual de Lima, sin depender de la zona del equipo. */
export function mesLima(ahora = new Date()): { desde: string; hasta: string } {
  const desde = fechaHoyLima(0, ahora).slice(0, 7) + '-01';
  const calendario = new Date(desde + 'T12:00:00Z');
  calendario.setUTCMonth(calendario.getUTCMonth() + 1, 0);
  return { desde, hasta: calendario.toISOString().slice(0, 10) };
}
