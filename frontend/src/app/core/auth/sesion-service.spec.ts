import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UsuarioSesionDto } from '../modelos/identidad';
import { SesionService } from './sesion-service';

const cliente: UsuarioSesionDto = {
  id: 12,
  nombre: 'Cliente ficticio',
  correo: 'sesion@ejemplo.test',
  rol: 'CLIENTE',
  barberoId: null,
  debeCambiarPassword: false,
};
describe('SesionService', () => {
  let sesion: SesionService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    sesion = TestBed.inject(SesionService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function cargar(usuario = cliente) {
    sesion.cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush(usuario);
  }
  it('cargar con 200 publica identidad, autenticado y rol', () => {
    cargar();
    expect(sesion.usuario()).toEqual(cliente);
    expect(sesion.autenticado()).toBe(true);
    expect(sesion.rol()).toBe('CLIENTE');
    expect(sesion.debeCambiarPassword()).toBe(false);
  });
  it('cargar con 401 representa ausencia de sesión sin error', () => {
    cargar();
    const error = vi.fn();
    const next = vi.fn();
    sesion.cargar().subscribe({ next, error });
    http.expectOne('/api/auth/sesion').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(error).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledWith(null);
    expect(sesion.autenticado()).toBe(false);
    expect(sesion.rol()).toBeNull();
  });
  it('cargar propaga fallos de red', () => {
    const error = vi.fn();
    sesion.cargar().subscribe({ error });
    http.expectOne('/api/auth/sesion').error(new ProgressEvent('error'));
    expect(error).toHaveBeenCalled();
  });
  it('login correcto envía el DTO y abre la sesión', () => {
    const datos = { correo: cliente.correo, password: 'Ficticia12345' };
    sesion.login(datos).subscribe();
    const peticion = http.expectOne('/api/auth/login');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
    peticion.flush(cliente);
    expect(sesion.usuario()).toEqual(cliente);
    expect(sesion.inicio()).toBe('/reservar');
  });
  it('login incorrecto propaga el error sin autenticar', () => {
    const error = vi.fn();
    sesion.login({ correo: cliente.correo, password: 'incorrecta' }).subscribe({ error });
    http.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(error).toHaveBeenCalled();
    expect(sesion.autenticado()).toBe(false);
  });
  it.each(['NO_AUTENTICADO', 'CREDENCIALES_INVALIDAS'])(
    'login con 401 %s propaga el error sin logout ni reintento',
    (codigo) => {
      const error = vi.fn();
      sesion.login({ correo: cliente.correo, password: 'Temporal1234' }).subscribe({ error });
      http
        .expectOne('/api/auth/login')
        .flush({ codigo }, { status: 401, statusText: 'Unauthorized' });
      http.expectNone('/api/auth/logout');
      http.expectNone('/api/auth/login');
      expect(error).toHaveBeenCalledTimes(1);
      expect(error.mock.calls[0][0].error.codigo).toBe(codigo);
      expect(sesion.autenticado()).toBe(false);
    },
  );
  it('registrar abre la sesión con el DTO devuelto', () => {
    const datos = {
      nombre: cliente.nombre,
      correo: cliente.correo,
      telefono: '999000000',
      password: 'Ficticia12345',
      aceptaPrivacidad: true,
    };
    sesion.registrar(datos).subscribe();
    const peticion = http.expectOne('/api/auth/registro');
    expect(peticion.request.body).toEqual(datos);
    peticion.flush(cliente, { status: 201, statusText: 'Created' });
    expect(sesion.usuario()).toEqual(cliente);
  });
  it('logout borra las señales al completar el servidor', () => {
    cargar();
    sesion.logout().subscribe();
    const peticion = http.expectOne('/api/auth/logout');
    expect(peticion.request.method).toBe('POST');
    peticion.flush(null, { status: 204, statusText: 'No Content' });
    expect(sesion.usuario()).toBeNull();
    expect(sesion.debeCambiarPassword()).toBe(false);
  });
  it('logout fallido permite reintentar con la sesión conservada', () => {
    cargar();
    sesion.logout().subscribe({ error: () => undefined });
    http.expectOne('/api/auth/logout').flush({}, { status: 500, statusText: 'Error' });
    expect(sesion.usuario()).toEqual(cliente);
  });
  it('cambiarPassword usa PUT y recarga la identidad de la cookie nueva', () => {
    cargar({ ...cliente, rol: 'BARBERO', debeCambiarPassword: true });
    expect(sesion.inicio()).toBe('/cambiar-password');
    const datos = { passwordActual: 'Temporal12345', passwordNueva: 'Nueva123456' };
    sesion.cambiarPassword(datos).subscribe();
    const peticion = http.expectOne('/api/auth/password');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(datos);
    peticion.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/sesion').flush({ ...cliente, rol: 'BARBERO' });
    expect(sesion.debeCambiarPassword()).toBe(false);
    expect(sesion.inicio()).toBe('/agenda');
  });
});
