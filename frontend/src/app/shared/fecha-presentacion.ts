/** Presenta una fecha civil ISO o un instante, siempre en Lima. */
export function fechaPresentacion(
  instante: string,
  formato: 'larga' | 'dia' | 'numero' | 'mes' = 'larga',
) {
  const opciones: Intl.DateTimeFormatOptions =
    formato === 'larga'
      ? { weekday: 'long', day: 'numeric', month: 'long' }
      : formato === 'dia'
        ? { weekday: 'short' }
        : formato === 'numero'
          ? { day: 'numeric' }
          : { month: 'short' };
  return new Intl.DateTimeFormat('es-PE', { ...opciones, timeZone: 'America/Lima' }).format(
    new Date(/^\d{4}-\d{2}-\d{2}$/.test(instante) ? instante + 'T12:00:00-05:00' : instante),
  );
}
