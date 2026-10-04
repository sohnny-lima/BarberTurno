import { semanaLima, sumarDias } from './semana-lima';
import { fechaHoyLima } from './instante-lima';

describe('Calendario de la agenda en días de Lima', () => {
  it.each([
    ['2026-10-05', '2026-10-05', '2026-10-11'],
    ['2026-10-11', '2026-10-05', '2026-10-11'],
    ['2026-10-01', '2026-09-28', '2026-10-04'],
    ['2027-01-01', '2026-12-28', '2027-01-03'],
    ['2024-02-29', '2024-02-26', '2024-03-03'],
  ])('semana de %s: lunes %s a domingo %s', (fecha, desde, hasta) => {
    expect(semanaLima(fecha)).toEqual({ desde, hasta });
  });
  it('toma el día de Lima aunque en Madrid y UTC ya sea enero', () => {
    const dia = fechaHoyLima(0, new Date('2027-01-01T02:30:00Z'));
    expect(dia).toBe('2026-12-31');
    expect(semanaLima(dia)).toEqual({ desde: '2026-12-28', hasta: '2027-01-03' });
  });
  it('suma días en ambos sentidos al cruzar año y mes', () => {
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(sumarDias('2026-03-01', -1)).toBe('2026-02-28');
  });
});
