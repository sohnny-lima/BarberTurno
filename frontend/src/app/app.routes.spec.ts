import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { SesionService } from './core/auth/sesion-service';

describe('Rutas reales', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('protege perfil y conserva la ruta de retorno', async () => {
    await RouterTestingHarness.create('/perfil');
    expect(TestBed.inject(Router).url).toBe('/ingresar?returnUrl=%2Fperfil');
  });
  it('la contraseña temporal bloquea páginas públicas y deja salir', async () => {
    TestBed.inject(SesionService).cargar().subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/sesion').flush({
      id: 1,
      rol: 'BARBERO',
      nombre: 'Ficticio',
      correo: 'ruta@ejemplo.test',
      debeCambiarPassword: true,
    });
    const harness = await RouterTestingHarness.create('/registro');
    expect(TestBed.inject(Router).url).toBe('/cambiar-password');
    await harness.navigateByUrl('/privacidad');
    expect(TestBed.inject(Router).url).toBe('/cambiar-password');
    TestBed.inject(SesionService).logout().subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/logout')
      .flush(null, { status: 204, statusText: 'No Content' });
    await harness.navigateByUrl('/ingresar');
    expect(TestBed.inject(Router).url).toBe('/ingresar');
  });
  it.each(['/admin/servicios', '/admin/barberos'])('un cliente no puede abrir %s', async (ruta) => {
    TestBed.inject(SesionService).cargar().subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/sesion').flush({
      id: 1,
      rol: 'CLIENTE',
      nombre: 'Ficticio',
      correo: 'ruta@ejemplo.test',
      debeCambiarPassword: false,
    });
    const harness = await RouterTestingHarness.create(ruta);
    expect(TestBed.inject(Router).url).toBe('/reservar');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/servicios?incluirInactivos=false').flush([]);
    http.expectOne('/api/barberos?incluirInactivos=false').flush([]);
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Reservar un turno');
  });
});
