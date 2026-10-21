import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReportesApi } from './reportes-api';

describe('Exportación Excel del API de reportes', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('envía filtros y Accept Excel y conserva Blob y Content-Disposition', () => {
    const archivo = new Blob(['libro']);
    let nombre = '';
    let cuerpo: Blob | null = null;
    TestBed.inject(ReportesApi)
      .excel({ desde: '2026-10-01', hasta: '2026-10-03', servicioId: 3, barberoId: 5 })
      .subscribe((r) => {
        nombre = r.headers.get('Content-Disposition') ?? '';
        cuerpo = r.body;
      });
    const r = http.expectOne((r) => r.url === '/api/reportes/resumen.xlsx');
    expect(r.request.method).toBe('GET');
    expect(r.request.responseType).toBe('blob');
    expect(r.request.headers.get('Accept')).toBe(
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    );
    expect(r.request.params.keys().sort()).toEqual(['barberoId', 'desde', 'hasta', 'servicioId']);
    expect(r.request.params.get('barberoId')).toBe('5');
    expect(r.request.params.get('servicioId')).toBe('3');
    expect(r.request.params.get('desde')).toBe('2026-10-01');
    expect(r.request.params.get('hasta')).toBe('2026-10-03');
    r.flush(archivo, { headers: { 'Content-Disposition': 'attachment; filename="reporte.xlsx"' } });
    expect(cuerpo).toBe(archivo);
    expect(nombre).toContain('reporte.xlsx');
  });

  it('omite filtros opcionales indefinidos', () => {
    TestBed.inject(ReportesApi)
      .excel({ desde: '2026-10-01', hasta: '2026-10-03', servicioId: undefined })
      .subscribe();
    const r = http.expectOne((r) => r.url === '/api/reportes/resumen.xlsx');
    expect(r.request.params.keys().sort()).toEqual(['desde', 'hasta']);
    r.flush(new Blob());
  });

  it('traduce el problema recibido como Blob para el patrón de errores existente', async () => {
    let error: HttpErrorResponse | undefined;
    TestBed.inject(ReportesApi)
      .excel({ desde: '2026-10-01', hasta: '2026-10-03' })
      .subscribe({
        error: (e) => {
          error = e;
        },
      });
    http
      .expectOne((r) => r.url === '/api/reportes/resumen.xlsx')
      .flush(
        new Blob(
          [JSON.stringify({ codigo: 'VALIDACION', detail: 'Revise el periodo.', errores: [] })],
          { type: 'application/problem+json' },
        ),
        { status: 400, statusText: 'Bad Request' },
      );
    await vi.waitFor(() => expect(error?.error.detail).toBe('Revise el periodo.'));
    expect(error?.status).toBe(400);
  });

  it('conserva el estado HTTP si el cuerpo de error no es JSON', async () => {
    let error: HttpErrorResponse | undefined;
    TestBed.inject(ReportesApi)
      .excel({ desde: '2026-10-01', hasta: '2026-10-03' })
      .subscribe({
        error: (e) => {
          error = e;
        },
      });
    http
      .expectOne((r) => r.url === '/api/reportes/resumen.xlsx')
      .flush(new Blob(['error']), { status: 503, statusText: 'Service Unavailable' });
    await vi.waitFor(() => expect(error?.status).toBe(503));
  });
});
