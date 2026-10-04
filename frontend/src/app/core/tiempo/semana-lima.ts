/** Suma días civiles; UTC solo sirve de calendario y evita depender de la zona del equipo. */
export function sumarDias(fecha: string, dias: number): string {
  const calendario = new Date(fecha + 'T12:00:00Z');
  calendario.setUTCDate(calendario.getUTCDate() + dias);
  return calendario.toISOString().slice(0, 10);
}

/** Semana completa del día elegido en Lima, de lunes a domingo, incluidos ambos extremos. */
export function semanaLima(fecha: string): { desde: string; hasta: string } {
  const dia = new Date(fecha + 'T12:00:00Z').getUTCDay();
  const desde = sumarDias(fecha, -((dia + 6) % 7));
  return { desde, hasta: sumarDias(desde, 6) };
}
