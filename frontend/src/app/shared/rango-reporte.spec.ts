import { FormControl, FormGroup } from '@angular/forms';
import { rangoReporte } from './rango-reporte';

describe('Periodo inclusivo del formulario de reportes', () => {
  it.each([
    ['2026-10-04', '2026-10-04', null],
    ['2024-01-01', '2024-12-31', null],
    ['2026-01-01', '2027-01-01', null],
    ['2024-01-01', '2025-01-01', { extension: true }],
    ['2026-01-01', '2027-01-02', { extension: true }],
    ['2026-10-05', '2026-10-04', { orden: true }],
    ['2026-02-31', '2026-03-01', { fecha: true }],
    ['', '2026-10-04', { fecha: true }],
    ['2026-10-04', '', { fecha: true }],
    ['04/10/2026', '2026-10-05', { fecha: true }],
    ['2024-02-29', '2024-03-01', null],
    ['2026-02-29', '2026-03-01', { fecha: true }],
  ])('%s a %s produce %j', (desde, hasta, esperado) => {
    const grupo = new FormGroup(
      { desde: new FormControl(desde), hasta: new FormControl(hasta) },
      { validators: rangoReporte },
    );
    expect(grupo.errors).toEqual(esperado);
  });
});
