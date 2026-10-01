import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { erroresInterceptor } from './errores-interceptor';
import { SesionService } from './sesion-service';

describe('Interceptor de errores', () => {
  let http: HttpClient;
  let peticiones: HttpTestingController;
  let router: Router;
  const abrir = vi.fn();
  beforeEach(() => {
    abrir.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([erroresInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: abrir } },
      ],
    });
    http = TestBed.inject(HttpClient);
    peticiones = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });
  afterEach(() => peticiones.verify());
  it('401 protegida limpia la sesión y navega a ingresar', () => {
    const sesion = TestBed.inject(SesionService);
    sesion.cargar().subscribe();
    peticiones
      .expectOne('/api/auth/sesion')
      .flush({ id: 1, rol: 'CLIENTE', debeCambiarPassword: false });
    http.get('/api/perfil').subscribe({ error: () => undefined });
    peticiones.expectOne('/api/perfil').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(sesion.autenticado()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/ingresar'], {
      queryParams: { returnUrl: '/' },
    });
  });
  it('401 de sesión inicial no navega ni muestra error', () => {
    http.get('/api/auth/sesion').subscribe({ error: () => undefined });
    peticiones.expectOne('/api/auth/sesion').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(router.navigate).not.toHaveBeenCalled();
    expect(abrir).not.toHaveBeenCalled();
  });
  it('401 del login muestra detail sin redirigir', () => {
    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    peticiones
      .expectOne('/api/auth/login')
      .flush({ detail: 'Credenciales inválidas.' }, { status: 401, statusText: 'Unauthorized' });
    expect(router.navigate).not.toHaveBeenCalled();
    expect(abrir).toHaveBeenCalledWith('Credenciales inválidas.', 'Cerrar', { duration: 6000 });
  });
  it('403 CAMBIO_PASSWORD_REQUERIDO lleva al cambio', () => {
    http.put('/api/perfil', {}).subscribe({ error: () => undefined });
    peticiones
      .expectOne('/api/perfil')
      .flush({ codigo: 'CAMBIO_PASSWORD_REQUERIDO' }, { status: 403, statusText: 'Forbidden' });
    expect(router.navigate).toHaveBeenCalledWith(['/cambiar-password']);
  });
  it('400 conserva errores por campo para el formulario y muestra detail', () => {
    const error = vi.fn();
    const problema = {
      detail: 'Revise sus datos.',
      errores: [{ campo: 'nombre', mensaje: 'Obligatorio.' }],
    };
    http.post('/api/auth/registro', {}).subscribe({ error });
    peticiones
      .expectOne('/api/auth/registro')
      .flush(problema, { status: 400, statusText: 'Bad Request' });
    expect(error.mock.calls[0][0].error).toEqual(problema);
    expect(abrir).toHaveBeenCalledWith(problema.detail, 'Cerrar', { duration: 6000 });
  });
  it.each([403, 409, 422])('muestra detail en snackbar con estado %s', (status) => {
    http.get('/api/perfil').subscribe({ error: () => undefined });
    peticiones
      .expectOne('/api/perfil')
      .flush({ detail: 'Respuesta del servidor.' }, { status, statusText: 'Error' });
    expect(abrir).toHaveBeenCalledWith('Respuesta del servidor.', 'Cerrar', { duration: 6000 });
  });
  it.each([500, 503])('oculta detalles técnicos del estado %s', (status) => {
    http.get('/api/perfil').subscribe({ error: () => undefined });
    peticiones
      .expectOne('/api/perfil')
      .flush({ detail: 'Detalle técnico que no debe mostrarse.' }, { status, statusText: 'Error' });
    expect(abrir).toHaveBeenCalledWith(
      'No pudimos completar la solicitud. Intente nuevamente.',
      'Cerrar',
      { duration: 6000 },
    );
  });
  it('fallo de red muestra el mensaje genérico', () => {
    http.get('/api/perfil').subscribe({ error: () => undefined });
    peticiones.expectOne('/api/perfil').error(new ProgressEvent('error'));
    expect(abrir).toHaveBeenCalledWith(
      'No pudimos completar la solicitud. Intente nuevamente.',
      'Cerrar',
      { duration: 6000 },
    );
  });
});
