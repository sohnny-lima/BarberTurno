import { FechaLimaPipe } from './fecha-lima-pipe';

describe('FechaLimaPipe independiente de la zona del proceso', () => {
  const pipe = new FechaLimaPipe();
  const instante = '2026-10-02T02:30:00Z';
  it('muestra el día anterior en Lima', () => {
    expect(pipe.transform(instante, 'fecha')).toBe('01/10/2026');
  });
  it('muestra 21:30 en Lima aunque el proceso use Madrid', () => {
    expect(pipe.transform(instante, 'hora')).toBe('21:30');
    if (new Intl.DateTimeFormat().resolvedOptions().timeZone === 'Europe/Madrid') {
      expect(
        new Intl.DateTimeFormat('es-PE', {
          hour: '2-digit',
          minute: '2-digit',
          hourCycle: 'h23',
        }).format(new Date(instante)),
      ).toBe('04:30');
    }
  });
  it('fechaHora combina fecha y hora en Lima', () => {
    expect(pipe.transform(instante)).toContain('01/10/2026');
    expect(pipe.transform(instante)).toContain('21:30');
  });
  it('acepta Date y desfases equivalentes', () => {
    expect(pipe.transform(new Date(instante), 'hora')).toBe('21:30');
    expect(pipe.transform('2026-10-01T21:30:00-05:00', 'hora')).toBe('21:30');
  });
  it('valor vacío o inválido deja el texto vacío', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('fecha inválida')).toBe('');
  });
});
