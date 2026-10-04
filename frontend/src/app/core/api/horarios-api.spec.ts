import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HorariosApi } from './horarios-api';

describe('API tipada de horarios', () => {
  let http: HttpTestingController;
  let api: HorariosApi;
  const semana = [{ diaSemana: 1, horaInicio: '09:00', horaFin: '18:00' }];
  const bloqueo = {
    inicio: '2026-10-05T09:00:00-05:00',
    fin: '2026-10-05T10:00:00-05:00',
    motivo: 'Trámite ficticio',
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(HorariosApi);
  });
  afterEach(() => http.verify());
  it('consulta jornadas y reemplaza la semana completa', () => {
    api.jornadas(17).subscribe((datos) => expect(datos).toEqual(semana));
    http.expectOne('/api/barberos/17/jornadas').flush(semana);
    api.guardarSemana(17, semana).subscribe();
    const r = http.expectOne('/api/barberos/17/jornadas');
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual(semana);
    r.flush(semana);
  });
  it('consulta bloqueos con fechas civiles y conserva la respuesta', () => {
    api.bloqueos(17, '2026-10-01', '2026-10-31').subscribe((datos) => expect(datos[0].id).toBe(4));
    const r = http.expectOne('/api/barberos/17/bloqueos?desde=2026-10-01&hasta=2026-10-31');
    expect(r.request.method).toBe('GET');
    r.flush([{ ...bloqueo, id: 4, barberoId: 17 }]);
  });
  it('crea un bloqueo individual', () => {
    api.crearBloqueo(17, bloqueo).subscribe();
    const r = http.expectOne('/api/barberos/17/bloqueos');
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual(bloqueo);
    r.flush({ id: 4 });
  });
  it('crea un lote con los ids recibidos', () => {
    api.crearLote({ ...bloqueo, barberoIds: [17, 18] }).subscribe();
    const r = http.expectOne('/api/bloqueos/lote');
    expect(r.request.method).toBe('POST');
    expect(r.request.body.barberoIds).toEqual([17, 18]);
    r.flush([]);
  });
  it('elimina por id y acepta 204', () => {
    api.eliminarBloqueo(4).subscribe();
    const r = http.expectOne('/api/bloqueos/4');
    expect(r.request.method).toBe('DELETE');
    r.flush(null, { status: 204, statusText: 'No Content' });
  });
});
