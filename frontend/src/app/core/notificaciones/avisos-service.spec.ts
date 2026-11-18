import { prepararNegocio } from '../negocio/negocio-prueba';
import { NegocioActual } from '../negocio/negocio-actual';
import { negocioInterceptor } from '../negocio/negocio-interceptor';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { SesionService } from '../auth/sesion-service';
import { UsuarioSesionDto } from '../modelos/identidad';
import { AvisosService } from './avisos-service';

describe('Contador y sondeo de avisos', () => {
  let http: HttpTestingController;
  let usuario: ReturnType<typeof signal<UsuarioSesionDto | null>>;
  let eventos: Subject<NavigationEnd>;
  let visibilidad: string;
  const identidad: UsuarioSesionDto = {
    id: 1,
    nombre: 'Ficticio',
    correo: 'avisos@ejemplo.test',
    rol: 'CLIENTE',
    debeCambiarPassword: false,
  };
  beforeEach(() => {
    vi.useFakeTimers();
    usuario = signal<UsuarioSesionDto | null>(null);
    eventos = new Subject();
    visibilidad = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(
      () => visibilidad as DocumentVisibilityState,
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([negocioInterceptor])),
        provideHttpClientTesting(),
        { provide: SesionService, useValue: { usuario } },
        { provide: Router, useValue: { events: eventos } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    prepararNegocio('CLIENTE');
  });
  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });
  function iniciar() {
    const servicio = TestBed.inject(AvisosService);
    usuario.set(identidad);
    TestBed.tick();
    vi.advanceTimersByTime(0);
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 3 });
    return servicio;
  }
  it.each(['slug', 'papel'] as const)(
    'vacía el contador y detiene el sondeo al cambiar %s hasta cargar la nueva sesión',
    (cambio) => {
      const servicio = iniciar();
      servicio.actualizar();
      const pendiente = http.expectOne('/api/n/prueba/notificaciones/conteo');
      TestBed.inject(NegocioActual).seleccionar(
        cambio === 'slug' ? 'sur' : 'prueba',
        cambio === 'papel' ? 'CLIENTE' : 'PERSONAL',
      );
      expect(servicio.noLeidas()).toBe(0);
      TestBed.tick();
      expect(pendiente.cancelled).toBe(true);
      vi.advanceTimersByTime(60_000);
      http.expectNone((r) => r.url.endsWith('/notificaciones/conteo'));
      expect(servicio.noLeidas()).toBe(0);
    },
  );
  // RutasNegocio deja el ámbito a null en NavigationStart y el NavigationEnd de esa misma
  // navegación llega antes de que el efecto propague la clave nula: sin TestBed.tick() intermedio.
  it.each(['salir a /negocios', 'cerrar la sesión'] as const)(
    'al %s no envía conteos caducados y vuelve a contar al entrar de nuevo',
    (caso) => {
      const servicio = iniciar();
      const negocio = TestBed.inject(NegocioActual);
      if (caso === 'cerrar la sesión') {
        usuario.set(null);
        negocio.limpiar();
      } else {
        negocio.seleccionar(null);
      }
      eventos.next(new NavigationEnd(1, '/negocios', '/negocios'));
      servicio.actualizar();
      http.expectNone((r) => r.url.endsWith('/notificaciones/conteo'));
      expect(servicio.noLeidas()).toBe(0);
      TestBed.tick();
      vi.advanceTimersByTime(60_000);
      http.expectNone((r) => r.url.endsWith('/notificaciones/conteo'));
      if (caso === 'cerrar la sesión') usuario.set(identidad);
      prepararNegocio('CLIENTE');
      TestBed.tick();
      vi.advanceTimersByTime(0);
      http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 3 });
      expect(servicio.noLeidas()).toBe(3);
      eventos.next(new NavigationEnd(2, '/n/prueba/mis-citas', '/n/prueba/mis-citas'));
      http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 2 });
      expect(servicio.noLeidas()).toBe(2);
    },
  );
  it('no consulta sin sesión y mantiene cero', () => {
    const servicio = TestBed.inject(AvisosService);
    TestBed.tick();
    vi.advanceTimersByTime(120_000);
    http.expectNone('/api/n/prueba/notificaciones/conteo');
    expect(servicio.noLeidas()).toBe(0);
  });
  it('actualiza cada 60 s y al navegar o solicitar refresco', () => {
    const servicio = iniciar();
    expect(servicio.noLeidas()).toBe(3);
    vi.advanceTimersByTime(59_999);
    http.expectNone('/api/n/prueba/notificaciones/conteo');
    vi.advanceTimersByTime(1);
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 4 });
    eventos.next(new NavigationEnd(1, '/mis-citas', '/mis-citas'));
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 2 });
    servicio.actualizar();
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 0 });
    expect(servicio.noLeidas()).toBe(0);
  });
  it('detiene temporizador y cancela respuestas pendientes al salir', () => {
    const servicio = iniciar();
    servicio.actualizar();
    const pendiente = http.expectOne('/api/n/prueba/notificaciones/conteo');
    usuario.set(null);
    TestBed.tick();
    expect(pendiente.cancelled).toBe(true);
    vi.advanceTimersByTime(120_000);
    eventos.next(new NavigationEnd(1, '/', '/'));
    http.expectNone('/api/n/prueba/notificaciones/conteo');
    expect(servicio.noLeidas()).toBe(0);
  });
  it('pausa en segundo plano y consulta al volver a visible', () => {
    const servicio = iniciar();
    servicio.actualizar();
    const pendiente = http.expectOne('/api/n/prueba/notificaciones/conteo');
    visibilidad = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(pendiente.cancelled).toBe(true);
    vi.advanceTimersByTime(120_000);
    eventos.next(new NavigationEnd(1, '/', '/'));
    servicio.actualizar();
    http.expectNone('/api/n/prueba/notificaciones/conteo');
    visibilidad = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    vi.advanceTimersByTime(0);
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 5 });
    expect(servicio.noLeidas()).toBe(5);
  });
  it('reintenta tras error y un refresco cancela un conteo anterior para evitar datos obsoletos', () => {
    const servicio = iniciar();
    servicio.actualizar();
    const pendiente = http.expectOne('/api/n/prueba/notificaciones/conteo');
    eventos.next(new NavigationEnd(1, '/', '/'));
    expect(pendiente.cancelled).toBe(true);
    const navegacion = http.expectOne('/api/n/prueba/notificaciones/conteo');
    servicio.actualizar();
    expect(navegacion.cancelled).toBe(true);
    http
      .expectOne('/api/n/prueba/notificaciones/conteo')
      .flush(null, { status: 503, statusText: 'Unavailable' });
    vi.advanceTimersByTime(60_000);
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 1 });
    expect(servicio.noLeidas()).toBe(1);
  });
  it('cancela conteos de la identidad anterior y soporta cualquier rol', () => {
    const servicio = iniciar();
    const negocio = TestBed.inject(NegocioActual);
    servicio.actualizar();
    const anterior = http.expectOne('/api/n/prueba/notificaciones/conteo');
    const propietario: UsuarioSesionDto = {
      ...identidad,
      id: 2,
      rol: 'ADMIN',
      rolNegocio: 'PROPIETARIO',
    };
    usuario.set(propietario);
    negocio.actualizarSesion(propietario);
    negocio.marcarSuspendido();
    TestBed.tick();
    vi.advanceTimersByTime(0);
    expect(anterior.cancelled).toBe(true);
    expect(servicio.noLeidas()).toBe(0);
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 6 });
    expect(servicio.noLeidas()).toBe(6);
  });
  it('espera al cambio de contraseña obligatorio para evitar 403 reiterados', () => {
    TestBed.inject(AvisosService);
    usuario.set({ ...identidad, debeCambiarPassword: true });
    TestBed.tick();
    vi.advanceTimersByTime(60_000);
    http.expectNone('/api/n/prueba/notificaciones/conteo');
    usuario.set(identidad);
    TestBed.tick();
    vi.advanceTimersByTime(0);
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 1 });
  });
});
