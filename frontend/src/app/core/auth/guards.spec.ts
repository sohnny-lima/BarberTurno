import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { Rol } from '../modelos/identidad';
import { authGuard, passwordGuard, rolGuard } from './guards';
import { SesionService } from './sesion-service';

describe('Guards de identidad', () => {
  let sesion: SesionService;
  let http: HttpTestingController;
  let router: Router;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    sesion = TestBed.inject(SesionService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });
  afterEach(() => http.verify());
  function entrar(rol: Rol, temporal = false) {
    sesion.cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush({
      id: 1,
      nombre: 'Ficticio',
      correo: 'guard@ejemplo.test',
      rol,
      debeCambiarPassword: temporal,
    });
  }
  function ejecutar(guard: CanActivateFn, url = '/perfil') {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }
  it('authGuard sin sesión conserva returnUrl', () => {
    expect(ejecutar(authGuard)).toEqual(
      router.createUrlTree(['/ingresar'], { queryParams: { returnUrl: '/perfil' } }),
    );
  });
  it('authGuard con sesión permite el acceso', () => {
    entrar('CLIENTE');
    expect(ejecutar(authGuard)).toBe(true);
  });
  it.each(['CLIENTE', 'BARBERO', 'ADMIN'] as Rol[])(
    'rolGuard permite el rol %s autorizado',
    (rol) => {
      entrar(rol);
      expect(ejecutar(rolGuard([rol]))).toBe(true);
    },
  );
  it.each([
    ['CLIENTE', '/reservar'],
    ['BARBERO', '/agenda'],
    ['ADMIN', '/agenda'],
  ] as const)('rolGuard redirige %s a su inicio', (rol, inicio) => {
    entrar(rol);
    expect(ejecutar(rolGuard([]))).toEqual(router.parseUrl(inicio));
  });
  it('rolGuard sin sesión redirige al ingreso', () => {
    expect(ejecutar(rolGuard(['ADMIN']))).toEqual(router.parseUrl('/ingresar'));
  });
  it('passwordGuard temporal impide incluso páginas públicas', () => {
    entrar('BARBERO', true);
    expect(ejecutar(passwordGuard, '/registro')).toEqual(router.parseUrl('/cambiar-password'));
  });
  it('passwordGuard temporal permite cambiar contraseña con parámetros', () => {
    entrar('ADMIN', true);
    expect(ejecutar(passwordGuard, '/cambiar-password?origen=perfil')).toBe(true);
  });
  it('passwordGuard sin temporal permite continuar', () => {
    entrar('CLIENTE');
    expect(ejecutar(passwordGuard)).toBe(true);
  });
  it('rolGuard da prioridad a la contraseña temporal', () => {
    entrar('BARBERO', true);
    expect(ejecutar(rolGuard(['BARBERO']))).toEqual(router.parseUrl('/cambiar-password'));
  });
});
