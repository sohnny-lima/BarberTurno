import { Pipe, PipeTransform } from '@angular/core';

export type FormatoLima = 'fecha' | 'hora' | 'fechaHora';

@Pipe({ name: 'fechaLima' })
export class FechaLimaPipe implements PipeTransform {
  transform(valor: string | Date | null | undefined, formato: FormatoLima = 'fechaHora'): string {
    if (!valor) return '';
    const fecha = new Date(valor);
    if (Number.isNaN(fecha.getTime())) return '';
    const opciones: Intl.DateTimeFormatOptions = { timeZone: 'America/Lima' };
    if (formato !== 'hora')
      Object.assign(opciones, { day: '2-digit', month: '2-digit', year: 'numeric' });
    if (formato !== 'fecha')
      Object.assign(opciones, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    return new Intl.DateTimeFormat('es-PE', opciones).format(fecha);
  }
}
