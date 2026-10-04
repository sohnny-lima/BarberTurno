import { fechaHoyLima, instanteLima } from './instante-lima';

describe('Fechas civiles de Lima', () => {
  it.each([
    ['2026-10-01', '09:00', '2026-10-01T14:00:00.000Z'],
    ['2026-03-29', '02:30', '2026-03-29T07:30:00.000Z'],
    ['2026-10-25', '02:30', '2026-10-25T07:30:00.000Z'],
    ['2026-01-01', '00:00', '2026-01-01T05:00:00.000Z'],
  ])('interpreta %s %s en Lima con cualquier TZ del proceso', (fecha, hora, utc) => {
    expect(instanteLima(fecha, hora)).toBe(fecha + 'T' + hora + ':00-05:00');
    expect(new Date(instanteLima(fecha, hora)).toISOString()).toBe(utc);
  });
  it('el rango inicial usa el día de Lima aun cuando UTC ya cambió de fecha', () => {
    const ahora = new Date('2026-12-31T02:00:00Z');
    expect(fechaHoyLima(0, ahora)).toBe('2026-12-30');
    expect(fechaHoyLima(30, ahora)).toBe('2027-01-29');
  });
});
