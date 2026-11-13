import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { prepararNegocio } from '../negocio/negocio-prueba';
import { VinculoProfesionalApi } from './vinculo-profesional-api';

describe('VinculoProfesionalApi', () => {
  let api: VinculoProfesionalApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), VinculoProfesionalApi],
    });
    http = TestBed.inject(HttpTestingController);
    prepararNegocio();
    api = TestBed.inject(VinculoProfesionalApi);
  });

  afterEach(() => http.verify());

  it('estado consulta la ruta GET bajo el negocio actual', () => {
    let respuestaRecibida: unknown;
    api.estado().subscribe((dato) => (respuestaRecibida = dato));

    const peticion = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
    expect(peticion.request.method).toBe('GET');

    peticion.flush({ vinculado: true, vinculadoEn: '2026-11-12T10:00:00Z' });
    expect(respuestaRecibida).toEqual({ vinculado: true, vinculadoEn: '2026-11-12T10:00:00Z' });
  });

  it('generar emite un POST vacío a la ruta del negocio', () => {
    let respuestaRecibida: unknown;
    api.generar().subscribe((dato) => (respuestaRecibida = dato));

    const peticion = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({});

    peticion.flush({ codigo: 'ABCD-EFGH', caducaEn: '2026-11-12T10:10:00Z' });
    expect(respuestaRecibida).toEqual({ codigo: 'ABCD-EFGH', caducaEn: '2026-11-12T10:10:00Z' });
  });

  it('desvincular emite un DELETE a la ruta del negocio', () => {
    let completado = false;
    api.desvincular().subscribe(() => (completado = true));

    const peticion = http.expectOne('/api/n/prueba/whatsapp/vinculo-profesional');
    expect(peticion.request.method).toBe('DELETE');

    peticion.flush(null, { status: 204, statusText: 'No Content' });
    expect(completado).toBe(true);
  });
});
