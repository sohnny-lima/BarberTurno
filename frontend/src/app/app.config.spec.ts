import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus, LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';

describe('Configuración de la aplicación', () => {
  let http: HttpClient;
  let peticiones: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    peticiones = TestBed.inject(HttpTestingController);
    const peticion = peticiones.expectOne('/api/auth/sesion');
    expect(peticion.request.method).toBe('GET');
    peticion.flush({}, { status: 401, statusText: 'Unauthorized' });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    document.cookie = 'XSRF-TOKEN=valor-ficticio; path=/';
  });

  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
    peticiones.verify();
  });

  it('el inicializador obtiene sesión y XSRF antes de completar el arranque', async () => {
    const inicializacion = TestBed.inject(ApplicationInitStatus);

    await inicializacion.donePromise;
    expect(inicializacion.done).toBe(true);
  });

  it('usa el locale es-PE', () => {
    expect(TestBed.inject(LOCALE_ID)).toBe('es-PE');
  });

  it('envía la cabecera X-XSRF-TOKEN en un POST relativo', () => {
    http.post('/api/x', {}).subscribe();

    const peticion = peticiones.expectOne('/api/x');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.headers.get('X-XSRF-TOKEN')).toBe('valor-ficticio');
    peticion.flush({});
  });

  it('omite la cabecera X-XSRF-TOKEN en un GET relativo', () => {
    http.get('/api/x').subscribe();

    const peticion = peticiones.expectOne('/api/x');
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    peticion.flush({});
  });
});
