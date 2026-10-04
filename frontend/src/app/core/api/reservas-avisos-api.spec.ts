import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificacionesApi } from './notificaciones-api';
import { ReservasApi } from './reservas-api';

describe('Contratos de consultas propias y avisos', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('omite filtros opcionales vacíos y conserva la página', () => {
    TestBed.inject(ReservasApi)
      .mias({ pagina: 2, tamano: 20, desde: '', hasta: undefined })
      .subscribe();
    const solicitud = http.expectOne('/api/reservas/mias?pagina=2&tamano=20');
    expect(solicitud.request.method).toBe('GET');
    solicitud.flush({ contenido: [], totalElementos: 0 });
  });
  it('consulta avisos no leídos con paginación exacta', () => {
    TestBed.inject(NotificacionesApi).listar(3, 50, true).subscribe();
    const solicitud = http.expectOne('/api/notificaciones?pagina=3&tamano=50&soloNoLeidas=true');
    expect(solicitud.request.method).toBe('GET');
    solicitud.flush({ contenido: [], totalElementos: 0 });
  });
});
