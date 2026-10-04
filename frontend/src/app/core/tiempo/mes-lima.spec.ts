import { mesLima } from './mes-lima';

describe('Mes civil de Lima', () => {
  it.each([
    ['2026-11-01T04:59:59Z', '2026-10-01', '2026-10-31'],
    ['2026-11-01T05:00:00Z', '2026-11-01', '2026-11-30'],
    ['2024-02-20T10:00:00Z', '2024-02-01', '2024-02-29'],
    ['2026-02-20T10:00:00Z', '2026-02-01', '2026-02-28'],
    ['2026-12-31T22:00:00Z', '2026-12-01', '2026-12-31'],
  ])('usa %s y devuelve %s a %s', (ahora, desde, hasta) => {
    expect(mesLima(new Date(ahora))).toEqual({ desde, hasta });
  });
});
