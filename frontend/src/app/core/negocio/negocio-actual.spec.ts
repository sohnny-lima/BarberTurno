import {
  HttpClient,
  HttpRequest,
  HttpResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthApi } from '../api/auth-api';
import { MisNegociosApi } from '../api/mis-negocios-api';
import { PerfilApi } from '../api/perfil-api';
import { ReservasApi } from '../api/reservas-api';
import { ServiciosApi } from '../api/servicios-api';
import { of } from 'rxjs';
import { erroresInterceptor } from '../auth/errores-interceptor';
import { SesionService } from '../auth/sesion-service';
import { Papel } from '../modelos/negocios';
import { NegocioActual } from './negocio-actual';
import { negocioInterceptor } from './negocio-interceptor';
import { usuarioNegocio } from './negocio-prueba';

describe('Contexto multinegocio y transporte', () => {
  let negocio: NegocioActual;
  let http: HttpTestingController;
  const abrir = vi.fn();
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([negocioInterceptor, erroresInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: abrir } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    negocio = TestBed.inject(NegocioActual);
    abrir.mockClear();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    sessionStorage.clear();
    localStorage.clear();
  });
  it('sin slug ninguna API de negocio se puede construir', () => {
    expect(() => TestBed.inject(ServiciosApi)).toThrow(
      'La operación requiere una barbería en la ruta.',
    );
    http.expectNone((r) => r.url.includes('/servicios'));
  });
  it.each(['PERSONAL', 'CLIENTE'] as Papel[])(
    'URL de negocio y cabecera dependen de la ruta y de %s, globales nunca la llevan',
    (papel) => {
      negocio.seleccionar('norte', papel);
      TestBed.inject(ServiciosApi).listar().subscribe();
      const catalogo = http.expectOne('/api/n/norte/servicios?incluirInactivos=false');
      expect(catalogo.request.headers.get('X-BT-Papel')).toBe(
        papel === 'CLIENTE' ? 'CLIENTE' : null,
      );
      catalogo.flush([]);
      TestBed.inject(AuthApi).sesion().subscribe();
      TestBed.inject(PerfilApi).obtener().subscribe();
      TestBed.inject(MisNegociosApi).listar().subscribe();
      for (const ruta of ['/api/auth/sesion', '/api/perfil', '/api/mis-negocios']) {
        const global = http.expectOne(ruta);
        expect(global.request.headers.has('X-BT-Papel')).toBe(false);
        global.flush(ruta.endsWith('mis-negocios') ? [] : usuarioNegocio('CLIENTE'));
      }
      TestBed.inject(HttpClient)
        .get('/api/perfil', { headers: { 'X-BT-Papel': 'CLIENTE' } })
        .subscribe();
      const forzada = http.expectOne('/api/perfil');
      expect(forzada.request.headers.has('X-BT-Papel')).toBe(false);
      forzada.flush({});
    },
  );
  it.each(['slug', 'papel', 'vuelta'] as const)(
    'cancela y descarta lecturas, escrituras y suscripciones diferidas al cambiar %s',
    (cambio) => {
      negocio.seleccionar('a');
      const api = TestBed.inject(ReservasApi);
      const recibido = vi.fn();
      api.obtener(17).subscribe(recibido);
      const lectura = http.expectOne('/api/n/a/reservas/17');
      const datos = { servicioId: 5, barberoId: 8, inicio: '2026-11-01T10:00:00-05:00' };
      api.crear(datos).subscribe(recibido);
      const escritura = http.expectOne('/api/n/a/reservas');
      const diferida = api.crear(datos);
      negocio.seleccionar(
        cambio === 'papel' ? 'a' : 'b',
        cambio === 'papel' ? 'CLIENTE' : 'PERSONAL',
      );
      if (cambio === 'vuelta') negocio.seleccionar('a');
      expect(lectura.cancelled).toBe(true);
      expect(escritura.cancelled).toBe(true);
      expect(() => lectura.flush({ id: 17 })).toThrow('Cannot flush a cancelled request');
      diferida.subscribe(recibido);
      // También una nueva llamada a la API conservada por un diálogo anterior queda detenida.
      api.crear(datos).subscribe(recibido);
      http.expectNone((r) => r.method === 'POST');
      expect(recibido).not.toHaveBeenCalled();
    },
  );
  it.each(['slug', 'papel'] as const)(
    'descarta una respuesta cuyo transporte termina mientras cambia %s antes de registrar la cancelación',
    (cambio) => {
      negocio.seleccionar('a');
      const peticion = new HttpRequest('GET', negocio.url('/servicios'), {
        context: negocio.contexto(),
      });
      const recibido = vi.fn();
      TestBed.runInInjectionContext(() =>
        negocioInterceptor(peticion, () => {
          negocio.seleccionar(
            cambio === 'slug' ? 'b' : 'a',
            cambio === 'papel' ? 'CLIENTE' : 'PERSONAL',
          );
          return of(new HttpResponse({ body: [{ nombre: 'Respuesta anterior' }] }));
        }),
      ).subscribe(recibido);
      expect(recibido).not.toHaveBeenCalled();
    },
  );
  it('un 401 de una operación limpia el negocio y conserva la URL completa para reingresar', async () => {
    const sesion = TestBed.inject(SesionService);
    sesion.cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush(usuarioNegocio('CLIENTE'));
    negocio.seleccionar('a', 'CLIENTE');
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/n/a/mis-citas?papel=cliente');
    TestBed.inject(ReservasApi)
      .obtener(8)
      .subscribe({ error: () => undefined });
    http.expectOne('/api/n/a/reservas/8').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(sesion.autenticado()).toBe(false);
    expect(negocio.slug()).toBeNull();
    expect(negocio.estado()).toBeNull();
    await sesion.esperarLimpieza();
    expect(router.navigate).toHaveBeenCalledWith(['/ingresar'], {
      queryParams: { returnUrl: '/n/a/mis-citas?papel=cliente' },
      replaceUrl: true,
    });
  });
  it.each([
    [200, 200, 'disponible'],
    [401, 200, 'disponible'],
    [200, 404, 'suspendido'],
    [401, 404, 'suspendido'],
    [404, 404, 'inexistente'],
    [500, 200, 'error'],
  ] as const)(
    'sesión %s y negocio %s producen %s sin confundir el 401 con inexistencia',
    (sesion, publico, estado) => {
      negocio.seleccionar('a');
      negocio.cargar().subscribe();
      const peticionSesion = http.expectOne('/api/n/a/sesion');
      const peticionPublica = http.expectOne('/api/n/a/negocio');
      // Se resuelve primero lo público para comprobar también los errores de la sesión.
      peticionPublica.flush(
        publico === 200 ? { slug: 'a', nombre: 'Barbería A', horizonteDias: 14 } : {},
        { status: publico, statusText: 'Estado de prueba' },
      );
      peticionSesion.flush(sesion === 200 ? usuarioNegocio('BARBERO') : {}, {
        status: sesion,
        statusText: 'Estado de prueba',
      });
      expect(negocio.estado()?.tipo).toBe(estado);
      if (sesion === 401) expect(negocio.usuario()).toBeNull();
      expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
      expect(abrir).not.toHaveBeenCalled();
    },
  );
  it('un contexto pendiente no actualiza la nueva barbería ni su sesión', () => {
    negocio.seleccionar('a');
    const recibido = vi.fn();
    negocio.cargar().subscribe(recibido);
    const sesion = http.expectOne('/api/n/a/sesion');
    const parametros = http.expectOne('/api/n/a/negocio');
    negocio.seleccionar('b', 'CLIENTE');
    expect(sesion.cancelled).toBe(true);
    expect(parametros.cancelled).toBe(true);
    expect(negocio.estado()).toBeNull();
    expect(recibido).not.toHaveBeenCalled();
  });
  it.each([null, 'CLIENTE'] as const)(
    'un 403 revalida el rol %s antes de anunciar revocación',
    (rol) => {
      negocio.seleccionar('a');
      TestBed.inject(ReservasApi)
        .obtener(8)
        .subscribe({ error: () => undefined });
      http
        .expectOne('/api/n/a/reservas/8')
        .flush({ codigo: 'PROHIBIDO' }, { status: 403, statusText: 'Forbidden' });
      http.expectOne('/api/n/a/sesion').flush(usuarioNegocio(rol));
      if (rol) {
        expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
        expect(abrir).toHaveBeenCalledWith('No tiene acceso a esta sección.', 'Cerrar', {
          duration: 6000,
        });
      } else {
        expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/negocios']);
        expect(negocio.avisoSelector()).toBe('Ya no tiene acceso a a.');
      }
    },
  );
  it('409 NEGOCIO_SUSPENDIDO llega a la acción sin duplicar el mensaje global', () => {
    negocio.seleccionar('a');
    const error = vi.fn();
    TestBed.inject(ReservasApi)
      .crear({ servicioId: 1, barberoId: 2, inicio: '2026-11-01T10:00:00-05:00' })
      .subscribe({ error });
    http.expectOne('/api/n/a/reservas').flush(
      {
        codigo: 'NEGOCIO_SUSPENDIDO',
        detail: 'Barbería suspendida: puede consultar, pero no hacer cambios.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(error.mock.calls[0][0].error.detail).toBe(
      'Barbería suspendida: puede consultar, pero no hacer cambios.',
    );
    expect(error.mock.calls[0][0].error.codigo).toBe('NEGOCIO_SUSPENDIDO');
    expect(abrir).not.toHaveBeenCalled();
  });
  it('una membresía perdida vuelve al selector aunque quede una ficha de cliente', () => {
    negocio.seleccionar('a');
    negocio.cargar().subscribe();
    http
      .expectOne('/api/n/a/sesion')
      .flush({ ...usuarioNegocio('BARBERO'), rolNegocio: 'BARBERO' });
    http.expectOne('/api/n/a/negocio').flush({ nombre: 'Barbería A', slug: 'a' });
    TestBed.inject(ReservasApi)
      .obtener(8)
      .subscribe({ error: () => undefined });
    http
      .expectOne('/api/n/a/reservas/8')
      .flush({ codigo: 'PROHIBIDO' }, { status: 403, statusText: 'Forbidden' });
    http.expectOne('/api/n/a/sesion').flush({ ...usuarioNegocio('CLIENTE'), rolNegocio: null });
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/negocios']);
    expect(negocio.avisoSelector()).toBe(
      'Ya no tiene acceso como personal a Barbería A. Puede seguir entrando como cliente.',
    );
  });
  it('logout limpia identidad, contexto, avisos y almacenamiento opcional', () => {
    const sesion = TestBed.inject(SesionService);
    sesion.cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush(usuarioNegocio('ADMIN'));
    negocio.seleccionar('a', 'CLIENTE');
    sessionStorage.setItem('barberturno.reserva', 'selección ficticia');
    localStorage.setItem('barberturno.ultima-barberia', 'a');
    negocio.avisoSelector.set('Aviso anterior');
    sesion.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    expect(negocio.slug()).toBeNull();
    expect(negocio.estado()).toBeNull();
    expect(negocio.avisoSelector()).toBe('');
    expect(sessionStorage.getItem('barberturno.reserva')).toBeNull();
    expect(localStorage.getItem('barberturno.ultima-barberia')).toBeNull();
    expect(sesion.autenticado()).toBe(false);
  });
});
