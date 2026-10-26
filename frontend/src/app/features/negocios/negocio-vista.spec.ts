import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { usuarioNegocio } from '../../core/negocio/negocio-prueba';
import { NegocioVista } from './negocio-vista';

describe('Estados del contenedor de barbería', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it.each([
    [404, 404, null, 'No encontramos esa barbería. Revise la dirección.'],
    [401, 404, null, 'Esta barbería no está disponible por ahora.'],
    [200, 404, 'CLIENTE', 'Esta barbería no está disponible por ahora.'],
    [200, 404, 'BARBERO', 'Barbería suspendida: puede consultar, pero no hacer cambios.'],
    [200, 404, 'ADMIN', 'Barbería suspendida: puede consultar, pero no hacer cambios.'],
  ] as const)(
    'sesión %s, público %s, rol %s: estado explícito sin catálogo ajeno',
    (sesion, publico, rol, texto) => {
      const negocio = TestBed.inject(NegocioActual);
      const http = TestBed.inject(HttpTestingController);
      negocio.seleccionar('norte');
      negocio.cargar().subscribe();
      // Lo público responde primero para no intentar vaciar una petición ya cancelada por un 404.
      http
        .expectOne('/api/n/norte/negocio')
        .flush({}, { status: publico, statusText: 'Not Found' });
      http
        .expectOne('/api/n/norte/sesion')
        .flush(usuarioNegocio(rol), { status: sesion, statusText: 'Estado de prueba' });
      const vista = TestBed.createComponent(NegocioVista);
      vista.detectChanges();
      expect(vista.nativeElement.textContent).toContain(texto);
      expect(!!vista.nativeElement.querySelector('router-outlet')).toBe(
        rol === 'ADMIN' || rol === 'BARBERO',
      );
      http.expectNone((r) => r.url.includes('/servicios') || r.url.includes('/reservas'));
    },
  );
});
