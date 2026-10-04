import { fechaHoyLima } from './instante-lima';

/** El datepicker representa días civiles con Date local, nunca instantes del negocio. */
export function fechaDatepicker(fecha: string): Date {
  const [ano, mes, dia] = fecha.split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

/** Extrae los campos civiles del calendario sin desplazar el día con toISOString(). */
export function fechaCivil(fecha: Date): string {
  return [
    fecha.getFullYear(),
    String(fecha.getMonth() + 1).padStart(2, '0'),
    String(fecha.getDate()).padStart(2, '0'),
  ].join('-');
}

export function limitesDatepicker(ahora = new Date()) {
  return {
    min: fechaDatepicker(fechaHoyLima(0, ahora)),
    max: fechaDatepicker(fechaHoyLima(30, ahora)),
  };
}
