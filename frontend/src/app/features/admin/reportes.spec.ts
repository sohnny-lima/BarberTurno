import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ResumenReporteDto } from '../../core/modelos/reportes';
import { mesLima } from '../../core/tiempo/mes-lima';
import { ESTADOS_RESERVA } from '../../shared/estado-reserva-chip';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { Reportes, tarjetasResumen } from './reportes';

const RESUMEN: ResumenReporteDto = {
  total: 12,
  porEstado: {
    PENDIENTE: 0,
    CONFIRMADA: 2,
    EN_ATENCION: 0,
    COMPLETADA: 7,
    CANCELADA: 2,
    NO_ASISTIO: 1,
  },
  porServicio: [{ id: 3, nombre: 'Corte', total: 12 }],
  porBarbero: [{ id: 5, nombre: 'Profesional ficticio', total: 12 }],
};
const PAGINA = {
  contenido: [RESERVA_PRUEBA],
  pagina: 0,
  tamano: 10,
  totalElementos: 12,
  totalPaginas: 2,
};

describe('Reportes e historial operativo', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function pendientes() {
    // Las dos peticiones deben existir antes de que responda cualquiera de ellas.
    return {
      resumen: http.expectOne((r) => r.url === '/api/reportes/resumen'),
      historial: http.expectOne((r) => r.url === '/api/reservas'),
    };
  }
  function crear(responder = true) {
    const fixture = TestBed.createComponent(Reportes);
    http.expectOne('/api/servicios?incluirInactivos=true').flush([]);
    http.expectOne('/api/barberos?incluirInactivos=true').flush([]);
    if (responder) {
      const peticiones = pendientes();
      peticiones.resumen.flush(RESUMEN);
      peticiones.historial.flush(PAGINA);
    }
    fixture.detectChanges();
    return fixture;
  }

  it('inicia con el mes de Lima y consulta en paralelo sin filtros opcionales', () => {
    const fixture = crear(false);
    const { resumen, historial } = pendientes();
    for (const [clave, valor] of Object.entries(mesLima())) {
      expect(resumen.request.params.get(clave)).toBe(valor);
      expect(historial.request.params.get(clave)).toBe(valor);
    }
    expect(resumen.request.params.has('servicioId')).toBe(false);
    expect(resumen.request.params.has('barberoId')).toBe(false);
    expect(historial.request.params.get('pagina')).toBe('0');
    resumen.flush(RESUMEN);
    expect(fixture.componentInstance.resumen()).toBeNull();
    expect(fixture.componentInstance.cargando()).toBe(true);
    historial.flush(PAGINA);
    expect(fixture.componentInstance.cargando()).toBe(false);
  });

  it('Aplicar consulta simultáneamente los mismos filtros y reinicia la página', () => {
    const fixture = crear();
    fixture.componentInstance.formulario.patchValue({
      desde: '2026-09-01',
      hasta: '2026-10-31',
      servicioId: 3,
      barberoId: 5,
    });
    fixture.componentInstance.aplicar();
    const { resumen, historial } = pendientes();
    expect(resumen.request.params.keys().sort()).toEqual([
      'barberoId',
      'desde',
      'hasta',
      'servicioId',
    ]);
    for (const clave of resumen.request.params.keys()) {
      expect(historial.request.params.get(clave)).toBe(resumen.request.params.get(clave));
    }
    expect(resumen.request.params.get('servicioId')).toBe('3');
    expect(resumen.request.params.get('barberoId')).toBe('5');
    expect(historial.request.params.get('pagina')).toBe('0');
    historial.flush(PAGINA);
    expect(fixture.componentInstance.resumen()).toBeNull();
    resumen.flush(RESUMEN);
    expect(fixture.componentInstance.total()).toBe(12);
  });

  it.each([
    ['2026-10-05', '2026-10-04', 'Desde debe ser anterior'],
    ['2024-01-01', '2025-01-01', 'hasta 366 días'],
    ['', '2026-10-04', 'Ingrese fechas válidas'],
    ['2026-02-31', '2026-03-01', 'Ingrese fechas válidas'],
  ])('periodo inválido %s a %s muestra error sin llamar a API', (desde, hasta, mensaje) => {
    const fixture = crear();
    fixture.componentInstance.formulario.patchValue({ desde, hasta });
    fixture.componentInstance.aplicar();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(mensaje);
    http.expectNone((r) => r.url === '/api/reportes/resumen' || r.url === '/api/reservas');
  });

  it('mapea todos los estados y conserva los seis ceros', () => {
    const tarjetas = tarjetasResumen({
      ...RESUMEN,
      total: 0,
      porEstado: {
        PENDIENTE: 0,
        CONFIRMADA: 0,
        EN_ATENCION: 0,
        COMPLETADA: 0,
        CANCELADA: 0,
        NO_ASISTIO: 0,
      },
    });
    expect(tarjetas.map((t) => t.estado)).toEqual(Object.keys(ESTADOS_RESERVA));
    expect(tarjetas.map((t) => t.total)).toEqual([0, 0, 0, 0, 0, 0]);
    expect(tarjetasResumen(RESUMEN).map((t) => t.total)).toEqual([0, 2, 0, 7, 1, 2]);
  });

  it('renderiza total, seis chips, conciliación, barras etiquetadas y columnas del historial', () => {
    const fixture = crear();
    const dom = fixture.nativeElement as HTMLElement;
    expect(dom.querySelectorAll('.estadistica').length).toBe(7);
    expect(dom.querySelectorAll('.estadisticas app-estado-reserva-chip').length).toBe(6);
    expect(dom.textContent).toContain('Los estados suman el total.');
    expect(dom.querySelector('label[for="servicio-3"]')?.textContent).toContain('Corte: 12 de 12');
    expect(dom.querySelector('progress')?.value).toBe(12);
    expect(dom.querySelectorAll('thead th').length).toBe(6);
    expect(dom.querySelector('tbody')?.textContent).toContain(RESERVA_PRUEBA.codigo);
  });

  it('un cero total no divide por cero y muestra el estado vacío', () => {
    const fixture = crear(false);
    const { resumen, historial } = pendientes();
    resumen.flush({
      ...RESUMEN,
      total: 0,
      porEstado: Object.fromEntries(Object.keys(ESTADOS_RESERVA).map((e) => [e, 0])),
      porServicio: [],
      porBarbero: [],
    });
    historial.flush({ ...PAGINA, contenido: [], totalElementos: 0, totalPaginas: 0 });
    fixture.detectChanges();
    expect(fixture.componentInstance.estadosCoinciden()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Sin reservas por servicio');
    expect(fixture.nativeElement.textContent).toContain('No hay reservas en esta página');
  });

  it('una suma distinta del total no muestra una comprobación falsa', () => {
    const fixture = crear(false);
    const { resumen, historial } = pendientes();
    resumen.flush({ ...RESUMEN, total: 13 });
    historial.flush(PAGINA);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.conciliacion').getAttribute('role')).toBe('alert');
    expect(fixture.nativeElement.textContent).toContain('Los estados no coinciden');
  });

  it('pagina solo el historial con los filtros aplicados, aunque el formulario se haya editado', () => {
    const fixture = crear();
    const filtros = fixture.componentInstance.filtrosAplicados();
    fixture.componentInstance.formulario.patchValue({ desde: '2027-01-01', servicioId: 999 });
    fixture.componentInstance.paginar({ pageIndex: 1, pageSize: 10, length: 12 });
    const peticion = http.expectOne((r) => r.url === '/api/reservas');
    expect(peticion.request.params.get('pagina')).toBe('1');
    expect(peticion.request.params.get('desde')).toBe(filtros!.desde);
    expect(peticion.request.params.has('servicioId')).toBe(false);
    http.expectNone((r) => r.url === '/api/reportes/resumen');
    peticion.flush({ ...PAGINA, pagina: 1 });
    expect(fixture.componentInstance.pagina()).toBe(1);
    expect(fixture.componentInstance.resumen()).toEqual(RESUMEN);
    fixture.componentInstance.formulario.patchValue({ desde: filtros!.desde });
    fixture.componentInstance.aplicar();
    const nuevas = pendientes();
    expect(nuevas.historial.request.params.get('pagina')).toBe('0');
    nuevas.resumen.flush(RESUMEN);
    nuevas.historial.flush(PAGINA);
  });

  it('cambia el tamaño por API y no repite el resumen', () => {
    const fixture = crear();
    fixture.componentInstance.paginar({ pageIndex: 0, pageSize: 20, length: 12 });
    const peticion = http.expectOne((r) => r.url === '/api/reservas');
    expect(peticion.request.params.get('tamano')).toBe('20');
    peticion.flush({ ...PAGINA, tamano: 20, totalPaginas: 1 });
    expect(fixture.componentInstance.tamano()).toBe(20);
    http.expectNone((r) => r.url === '/api/reportes/resumen');
  });

  it('cancela consultas anteriores al aplicar otro periodo', () => {
    const fixture = crear(false);
    const anteriores = pendientes();
    fixture.componentInstance.formulario.patchValue({ desde: '2026-09-01', hasta: '2026-09-30' });
    fixture.componentInstance.aplicar();
    expect(anteriores.resumen.cancelled).toBe(true);
    expect(anteriores.historial.cancelled).toBe(true);
    const nuevas = pendientes();
    nuevas.resumen.flush(RESUMEN);
    nuevas.historial.flush(PAGINA);
  });

  it('un fallo del historial cancela el resumen y no presenta datos parciales; Aplicar reintenta', () => {
    const fixture = crear(false);
    const peticiones = pendientes();
    peticiones.historial.flush(
      { detail: 'Error de consulta' },
      { status: 500, statusText: 'Server Error' },
    );
    expect(peticiones.resumen.cancelled).toBe(true);
    expect(fixture.componentInstance.resumen()).toBeNull();
    expect(fixture.componentInstance.mensaje()).toContain('Intente nuevamente');
    expect(fixture.componentInstance.cargando()).toBe(false);
    fixture.componentInstance.aplicar();
    const nuevas = pendientes();
    nuevas.resumen.flush(RESUMEN);
    nuevas.historial.flush(PAGINA);
  });

  it('un fallo al paginar permite reintentar con la misma página sin perder el resumen', () => {
    const fixture = crear();
    fixture.componentInstance.paginar({ pageIndex: 1, pageSize: 10, length: 12 });
    http
      .expectOne((r) => r.url === '/api/reservas')
      .flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Reintentar historial');
    fixture.componentInstance.cargarPagina();
    const peticion = http.expectOne((r) => r.url === '/api/reservas');
    expect(peticion.request.params.get('pagina')).toBe('1');
    peticion.flush({ ...PAGINA, pagina: 1 });
    expect(fixture.componentInstance.mensajeHistorial()).toBe('');
    expect(fixture.componentInstance.resumen()).toEqual(RESUMEN);
  });

  it('destruir la pantalla cancela ambos HTTP pendientes', () => {
    const fixture = crear(false);
    const peticiones = pendientes();
    fixture.destroy();
    expect(peticiones.resumen.cancelled).toBe(true);
    expect(peticiones.historial.cancelled).toBe(true);
  });
});
