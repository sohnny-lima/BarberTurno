import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SelectorCliente } from './selector-cliente';
export const clientePrueba = {
  id: 9,
  nombre: 'Cliente ficticio',
  correo: 'cliente@ejemplo.test',
  telefono: '999000001',
  rol: 'CLIENTE' as const,
  activo: true,
  debeCambiarPassword: false,
};
describe('Autocompletado de clientes', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [SelectorCliente],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });
  it('consulta con filtro CLIENTE y fragmento, y selecciona la identidad del resultado', () => {
    const f = TestBed.createComponent(SelectorCliente);
    const s = f.componentInstance;
    const elegido = vi.fn();
    s.elegido.subscribe(elegido);
    f.detectChanges();
    s.control.setValue(' Cli ');
    expect(elegido).toHaveBeenLastCalledWith(null);
    http.expectNone((r) => r.url === '/api/usuarios');
    vi.advanceTimersByTime(250);
    const req = http.expectOne((r) => r.url === '/api/usuarios');
    expect(req.request.params.get('rol')).toBe('CLIENTE');
    expect(req.request.params.get('q')).toBe('Cli');
    expect(req.request.params.get('pagina')).toBe('0');
    req.flush({ contenido: [clientePrueba], totalElementos: 1 });
    expect(s.resultados()).toEqual([clientePrueba]);
    s.control.setValue(clientePrueba);
    s.seleccionar(clientePrueba);
    expect(elegido).toHaveBeenLastCalledWith(clientePrueba);
    expect(s.nombre(clientePrueba)).toBe('Cliente ficticio');
    s.control.setValue('otro');
    expect(elegido).toHaveBeenLastCalledWith(null);
    expect(s.resultados()).toEqual([]);
  });
  it('cancela respuestas obsoletas y búsquedas vacías', () => {
    const s = TestBed.createComponent(SelectorCliente).componentInstance;
    s.control.setValue('primero');
    vi.advanceTimersByTime(250);
    const vieja = http.expectOne((r) => r.url === '/api/usuarios');
    s.control.setValue('segundo');
    expect(vieja.cancelled).toBe(true);
    vi.advanceTimersByTime(250);
    http.expectOne((r) => r.url === '/api/usuarios').flush({ contenido: [], totalElementos: 0 });
    expect(s.mensaje()).toContain('No se encontraron');
    s.control.setValue(' ');
    vi.advanceTimersByTime(250);
    http.expectNone((r) => r.url === '/api/usuarios');
    expect(s.mensaje()).toBe('');
  });
  it('presenta errores y más resultados sin interpretar reglas de acceso', () => {
    const s = TestBed.createComponent(SelectorCliente).componentInstance;
    s.control.setValue('cli');
    vi.advanceTimersByTime(250);
    http
      .expectOne((r) => r.url === '/api/usuarios')
      .flush({ detail: 'Intente otra vez.' }, { status: 503, statusText: 'Service Unavailable' });
    expect(s.mensaje()).toBe('No pudimos completar la solicitud. Intente nuevamente.');
    expect(s.cargando()).toBe(false);
    s.control.setValue('cli2');
    vi.advanceTimersByTime(250);
    http
      .expectOne((r) => r.url === '/api/usuarios')
      .flush({ contenido: [{ ...clientePrueba, activo: false }], totalElementos: 30 });
    expect(s.total()).toBe(30);
    expect(s.resultados()[0].activo).toBe(false);
  });
  it('impide emitir selección durante el envío', () => {
    const f = TestBed.createComponent(SelectorCliente);
    f.componentRef.setInput('deshabilitado', true);
    const emitir = vi.fn();
    f.componentInstance.elegido.subscribe(emitir);
    f.componentInstance.seleccionar(clientePrueba);
    expect(emitir).not.toHaveBeenCalled();
  });
});
