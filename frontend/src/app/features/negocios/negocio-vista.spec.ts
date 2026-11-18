import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { usuarioNegocio } from '../../core/negocio/negocio-prueba';
import { NegocioVista } from './negocio-vista';
import { BarberiaActual } from '../../layout/barberia-actual';

describe('Estados del contenedor de barbería', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it.each([
    [404, 404, null, 'No encontramos esa barbería. Revise la dirección.', 'Barbería no encontrada'],
    [401, 404, null, 'Esta barbería no está disponible por ahora.', 'Barbería no disponible'],
    [200, 404, 'CLIENTE', 'Esta barbería no está disponible por ahora.', 'Barbería no disponible'],
    [
      200,
      404,
      'BARBERO',
      'Barbería suspendida: puede consultar, pero no hacer cambios. Las acciones de cambio quedan desactivadas hasta que la plataforma la reactive.',
      null,
    ],
    [
      200,
      404,
      'ADMIN',
      'Barbería suspendida: puede consultar, pero no hacer cambios. Las acciones de cambio quedan desactivadas hasta que la plataforma la reactive.',
      null,
    ],
  ] as const)(
    'sesión %s, público %s, rol %s: estado explícito sin catálogo ajeno',
    (sesion, publico, rol, texto, titulo) => {
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
      // Las pantallas de estado tienen su h1 (y reservan el sitio de la campana como las demás).
      expect(vista.nativeElement.querySelector('h1')?.textContent ?? null).toBe(titulo);
      expect(!!vista.nativeElement.querySelector('router-outlet')).toBe(
        rol === 'ADMIN' || rol === 'BARBERO',
      );
      http.expectNone((r) => r.url.includes('/servicios') || r.url.includes('/reservas'));
    },
  );
  it('una barbería inexistente no aparece como barbería actual en el menú', () => {
    const negocio = TestBed.inject(NegocioActual);
    const http = TestBed.inject(HttpTestingController);
    negocio.seleccionar('no-existe');
    const bloque = TestBed.createComponent(BarberiaActual);
    bloque.detectChanges();
    expect(bloque.nativeElement.querySelector('section')).not.toBeNull();
    negocio.cargar().subscribe();
    http.expectOne('/api/n/no-existe/negocio').flush({}, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/n/no-existe/sesion').flush({}, { status: 404, statusText: 'Not Found' });
    bloque.detectChanges();
    expect(negocio.estado()?.tipo).toBe('inexistente');
    expect(bloque.nativeElement.querySelector('section')).toBeNull();
  });
});
