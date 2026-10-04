import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, Subject } from 'rxjs';
import { ConfirmarEstadoDatos } from '../../shared/confirmar-estado-dialogo';
import { PasswordTemporalDialogo } from './password-temporal-dialogo';
import { Usuarios } from './usuarios';
const cliente = {
  id: 9,
  nombre: 'Cliente ficticio',
  correo: 'cliente@ejemplo.test',
  telefono: '999000001',
  rol: 'CLIENTE' as const,
  activo: true,
  debeCambiarPassword: false,
};
describe('Gestión de usuarios', () => {
  let http: HttpTestingController;
  let cierre: Subject<boolean>;
  let temporalCierre: Subject<void>;
  const dialogos = { open: vi.fn() };
  beforeEach(() => {
    cierre = new Subject();
    temporalCierre = new Subject();
    dialogos.open.mockReset();
    TestBed.configureTestingModule({
      imports: [Usuarios],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: dialogos },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function preparar() {
    const f = TestBed.createComponent(Usuarios);
    http
      .expectOne((r) => r.url === '/api/usuarios')
      .flush({ contenido: [cliente], totalElementos: 1 });
    f.detectChanges();
    return f;
  }
  it('muestra tabla, búsqueda por nombre o correo, estado y rol', () => {
    const f = preparar();
    expect(f.nativeElement.textContent).toContain(cliente.correo);
    expect(f.nativeElement.textContent).toContain('Activo');
    f.componentInstance.busqueda.setValue(' Cli ');
    f.componentInstance.rol.setValue('CLIENTE');
    f.componentInstance.buscar();
    const req = http.expectOne((r) => r.url === '/api/usuarios');
    expect(req.request.params.get('q')).toBe('Cli');
    expect(req.request.params.get('rol')).toBe('CLIENTE');
    req.flush({ contenido: [], totalElementos: 0 });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('No se encontraron');
  });
  it('pagina con filtros aplicados aunque el formulario cambie', () => {
    const s = preparar().componentInstance;
    s.busqueda.setValue('cli');
    s.buscar();
    http
      .expectOne((r) => r.url === '/api/usuarios')
      .flush({ contenido: [cliente], totalElementos: 30 });
    s.busqueda.setValue('nuevo');
    s.paginar({ pageIndex: 1, pageSize: 20, length: 30 });
    const req = http.expectOne((r) => r.url === '/api/usuarios');
    expect(req.request.params.get('q')).toBe('cli');
    expect(req.request.params.get('pagina')).toBe('1');
    req.flush({ contenido: [], totalElementos: 30 });
  });
  it('restablece una sola vez y reutiliza el diálogo temporal protegido con Copiar', () => {
    const s = preparar().componentInstance;
    dialogos.open
      .mockReturnValueOnce({ afterClosed: () => cierre })
      .mockReturnValueOnce({ afterClosed: () => temporalCierre });
    s.restablecer(cliente);
    s.restablecer(cliente);
    expect(dialogos.open).toHaveBeenCalledTimes(1);
    const datos = dialogos.open.mock.calls[0][1].data as ConfirmarEstadoDatos;
    datos.cambiar().subscribe();
    const req = http.expectOne('/api/usuarios/9/restablecer-password');
    expect(req.request.method).toBe('POST');
    req.flush({ passwordTemporal: 'Temporal1234' });
    expect(dialogos.open).toHaveBeenCalledTimes(1);
    cierre.next(true);
    expect(dialogos.open.mock.calls[1][0]).toBe(PasswordTemporalDialogo);
    expect(dialogos.open.mock.calls[1][1].disableClose).toBe(true);
    expect(dialogos.open.mock.calls[1][1].data).toBe('Temporal1234');
    expect(JSON.stringify(s.filas())).not.toContain('Temporal1234');
    temporalCierre.next();
    http
      .expectOne((r) => r.url === '/api/usuarios')
      .flush({ contenido: [{ ...cliente, debeCambiarPassword: true }], totalElementos: 1 });
    expect(s.operando()).toBe(false);
    expect(dialogos.open).toHaveBeenCalledTimes(2);
  });
  it('cancelar el restablecimiento no envía ni abre la temporal', () => {
    const s = preparar().componentInstance;
    dialogos.open.mockReturnValue({ afterClosed: () => of(false) });
    s.restablecer(cliente);
    http.expectNone('/api/usuarios/9/restablecer-password');
    expect(s.operando()).toBe(false);
    expect(dialogos.open).toHaveBeenCalledTimes(1);
  });
  it('cambia estado con confirmación y recarga tras el 200 sin cuerpo', () => {
    const s = preparar().componentInstance;
    dialogos.open.mockReturnValue({ afterClosed: () => cierre });
    s.cambiarEstado(cliente);
    const datos = dialogos.open.mock.calls[0][1].data as ConfirmarEstadoDatos;
    expect(datos.activar).toBe(false);
    datos.cambiar().subscribe();
    const req = http.expectOne('/api/usuarios/9/estado');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ activo: false });
    req.flush(null);
    cierre.next(true);
    http
      .expectOne((r) => r.url === '/api/usuarios')
      .flush({ contenido: [{ ...cliente, activo: false }], totalElementos: 1 });
    expect(s.filas()[0].activo).toBe(false);
  });
  it('consulta nueva cancela anterior y presenta error recuperable', () => {
    const s = preparar().componentInstance;
    s.buscar();
    const anterior = http.expectOne((r) => r.url === '/api/usuarios');
    s.buscar();
    expect(anterior.cancelled).toBe(true);
    http
      .expectOne((r) => r.url === '/api/usuarios')
      .flush({ detail: 'No disponible' }, { status: 503, statusText: 'Service Unavailable' });
    expect(s.mensaje()).toBe('No pudimos completar la solicitud. Intente nuevamente.');
    expect(s.filas()).toEqual([]);
  });
});
