import { signal } from '@angular/core';
import { AvisosService } from './core/notificaciones/avisos-service';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouteReuseStrategy } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { routes } from './app.routes';
import { SesionService } from './core/auth/sesion-service';
import { MisNegociosApi } from './core/api/mis-negocios-api';
import { ReservasApi } from './core/api/reservas-api';
import { RutasNegocio } from './core/negocio/rutas-negocio';
import { ReutilizacionNegocio } from './core/negocio/reutilizacion-negocio';
import { NegocioActual } from './core/negocio/negocio-actual';
import { negocioInterceptor } from './core/negocio/negocio-interceptor';
import { Reservar } from './features/reservar/reservar';

describe('Rutas reales', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([negocioInterceptor])),
        provideHttpClientTesting(),
        { provide: RouteReuseStrategy, useClass: ReutilizacionNegocio },
        { provide: AvisosService, useValue: { noLeidas: signal(3), actualizar: vi.fn() } },
        { provide: MisNegociosApi, useValue: { listar: () => of([]) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(RutasNegocio);
  });
  afterEach(() => http.verify());
  const identidad = {
    id: 1,
    nombre: 'Ficticio',
    correo: 'ruta@ejemplo.test',
    rol: 'CLIENTE',
    debeCambiarPassword: false,
  };
  function entrar(temporal = false) {
    TestBed.inject(SesionService).cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush({ ...identidad, debeCambiarPassword: temporal });
  }
  async function contexto(slug: string, rol: string | null = 'CLIENTE', estado = 200) {
    await vi.waitFor(() => {
      const peticion = http.expectOne('/api/n/' + slug + '/sesion');
      if (estado === 401 || estado === 404)
        peticion.flush({}, { status: estado, statusText: 'Estado de prueba' });
      else peticion.flush({ ...identidad, rol });
    });
    http.expectOne('/api/n/' + slug + '/negocio').flush({
      slug,
      nombre: 'Barbería ' + slug,
      horizonteDias: slug === 'b' ? 14 : 30,
      anticipacionCambioClienteMin: 120,
      limiteReservasActivas: 3,
      confirmacionManual: false,
    });
  }
  async function catalogos(slug: string) {
    await vi.waitFor(() =>
      http.expectOne('/api/n/' + slug + '/servicios?incluirInactivos=false').flush([]),
    );
    http.expectOne('/api/n/' + slug + '/barberos?incluirInactivos=false').flush([]);
  }
  it('protege perfil y conserva la ruta de retorno', async () => {
    await RouterTestingHarness.create('/perfil');
    expect(TestBed.inject(Router).url).toBe('/ingresar?returnUrl=%2Fperfil');
  });
  it('protege un reporte con slug y conserva la URL completa', async () => {
    const harness = await RouterTestingHarness.create('/ingresar');
    const navegacion = harness.navigateByUrl('/n/a/admin/reportes?papel=cliente');
    await contexto('a', null, 401);
    await navegacion;
    expect(TestBed.inject(Router).url).toBe(
      '/ingresar?returnUrl=%2Fn%2Fa%2Fadmin%2Freportes%3Fpapel%3Dcliente',
    );
  });
  it('la contraseña temporal bloquea páginas públicas y deja salir', async () => {
    entrar(true);
    const harness = await RouterTestingHarness.create('/registro');
    expect(TestBed.inject(Router).url).toBe('/cambiar-password');
    await harness.navigateByUrl('/privacidad');
    expect(TestBed.inject(Router).url).toBe('/cambiar-password');
    TestBed.inject(SesionService).logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await harness.navigateByUrl('/ingresar');
    expect(TestBed.inject(Router).url).toBe('/ingresar');
  });
  it.each(['servicios', 'barberos', 'reportes'])(
    'un cliente no puede abrir admin/%s con slug',
    async (seccion) => {
      entrar();
      const harness = await RouterTestingHarness.create('/ingresar');
      const navegacion = harness.navigateByUrl('/n/a/admin/' + seccion);
      await contexto('a');
      await contexto('a');
      await navegacion;
      expect(TestBed.inject(Router).url).toBe('/n/a/sin-acceso');
      expect(harness.routeNativeElement?.textContent).toContain('No tiene acceso a esta sección.');
      http.expectNone((r) => r.url.includes('/admin/') || r.url.endsWith('/reportes/resumen'));
    },
  );
  it.each([
    '/reservar',
    '/mis-citas',
    '/agenda',
    '/admin/servicios',
    '/admin/barberos',
    '/admin/horarios',
    '/admin/reportes',
    '/admin/usuarios',
  ])('la ruta antigua %s nunca elige una barbería', async (ruta) => {
    const harness = await RouterTestingHarness.create(ruta);
    expect(TestBed.inject(Router).url).toBe('/ingresar');
    entrar();
    await harness.navigateByUrl(ruta);
    expect(TestBed.inject(Router).url).toBe('/negocios');
    http.expectNone((r) => r.url.startsWith('/api/n/'));
  });
  it('raíz con sesión lleva al selector, sin una barbería implícita', async () => {
    entrar();
    await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/negocios');
    expect(TestBed.inject(NegocioActual).slug()).toBeNull();
  });
  it('reservar es público y destruye vista, providers y selecciones al cambiar slug o papel', async () => {
    const harness = await RouterTestingHarness.create('/ingresar');
    let navegacion = harness.navigateByUrl('/n/a/reservar');
    await contexto('a', null, 401);
    await catalogos('a');
    await navegacion;
    const primera = harness.routeDebugElement!.query(By.directive(Reservar));
    const store = primera.componentInstance.store;
    const apiAntigua = primera.injector.get(ReservasApi);
    store.servicioId.set(73);
    store.cliente.set({ id: 99 });
    store.inicializar();
    const pendiente = http.expectOne('/api/n/a/servicios?incluirInactivos=false');
    const barberos = http.expectOne('/api/n/a/barberos?incluirInactivos=false');
    navegacion = harness.navigateByUrl('/n/b/reservar');
    await contexto('b', null, 401);
    expect(pendiente.cancelled).toBe(true);
    expect(barberos.cancelled).toBe(true);
    expect(store.servicioId()).toBeNull();
    expect(store.cliente()).toBeNull();
    apiAntigua
      .crear({ servicioId: 73, barberoId: 99, inicio: '2026-11-01T10:00:00-05:00' })
      .subscribe();
    http.expectNone((r) => r.method === 'POST');
    await catalogos('b');
    await navegacion;
    const segunda = harness.routeDebugElement!.query(By.directive(Reservar)).componentInstance;
    expect(segunda.store).not.toBe(store);
    expect(segunda.limites.max.getTime()).toBeLessThan(
      primera.componentInstance.limites.max.getTime(),
    );
    navegacion = harness.navigateByUrl('/n/b/reservar?papel=cliente');
    await contexto('b');
    await catalogos('b');
    await navegacion;
    expect(harness.routeDebugElement!.query(By.directive(Reservar)).componentInstance).not.toBe(
      segunda,
    );
    expect(TestBed.inject(NegocioActual).papel()).toBe('CLIENTE');
  });
});
