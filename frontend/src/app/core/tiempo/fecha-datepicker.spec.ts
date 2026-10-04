import { fechaCivil, fechaDatepicker, limitesDatepicker } from './fecha-datepicker';
import { fechaHoyLima } from './instante-lima';

describe('Días civiles del datepicker en Lima, incluso con TZ=Europe/Madrid', () => {
  it('a medianoche europea aún usa el día anterior de Lima', () => {
    const ahora = new Date('2026-10-05T02:00:00Z');
    const limites = limitesDatepicker(ahora);
    expect(fechaCivil(limites.min)).toBe('2026-10-04');
    expect(fechaCivil(limites.max)).toBe('2026-11-03');
  });
  it('cambia de día a las 05:00 UTC, sin depender de DST de Madrid', () => {
    expect(fechaHoyLima(0, new Date('2026-10-25T04:59:59Z'))).toBe('2026-10-24');
    expect(fechaHoyLima(0, new Date('2026-10-25T05:00:00Z'))).toBe('2026-10-25');
  });
  it('la fecha elegida conserva sus campos civiles al cruzar el horario de verano', () => {
    expect(fechaCivil(fechaDatepicker('2026-10-25'))).toBe('2026-10-25');
    expect(fechaCivil(fechaDatepicker('2026-11-03'))).toBe('2026-11-03');
  });
  it('el horizonte incluye hoy y hoy más 30, pasando de año', () => {
    const limites = limitesDatepicker(new Date('2026-12-20T15:00:00Z'));
    expect(fechaCivil(limites.min)).toBe('2026-12-20');
    expect(fechaCivil(limites.max)).toBe('2027-01-19');
  });
});
