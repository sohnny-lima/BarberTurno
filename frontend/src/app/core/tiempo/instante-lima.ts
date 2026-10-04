/** Une la fecha y la hora civiles elegidas en Lima sin usar la zona del navegador. */
export function instanteLima(fecha: string, hora: string): string {
  return fecha + 'T' + hora + ':00-05:00';
}

/** Fecha civil de Lima; el desplazamiento sirve para valores iniciales del formulario. */
export function fechaHoyLima(dias = 0, ahora = new Date()): string {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(ahora);
  const parte = (tipo: string) => partes.find((p) => p.type === tipo)!.value;
  const fecha = new Date(parte('year') + '-' + parte('month') + '-' + parte('day') + 'T12:00:00Z');
  fecha.setUTCDate(fecha.getUTCDate() + dias);
  return fecha.toISOString().slice(0, 10);
}
