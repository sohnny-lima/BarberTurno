import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { SesionService } from '../../core/auth/sesion-service';
import { EstadoTransicion } from '../../core/modelos/reservas';
import { AvisosService } from '../../core/notificaciones/avisos-service';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { Agenda } from './agenda';
import { AuditoriaDialogo } from './auditoria-dialogo';
import { CancelarDialogo } from '../mis-citas/cancelar-dialogo';
import { ReprogramarDialogo } from './reprogramar-dialogo';
import { ResultadoAgenda, TransicionDialogo } from './transicion-dialogo';

describe('Agenda operativa', () => {
  let http: HttpTestingController;
  const rol = signal<'ADMIN' | 'BARBERO'>('ADMIN');
  const actualizar = vi.fn();
  const snackbar = { open: vi.fn() };
  let resultado: Subject<ResultadoAgenda | undefined>;
  const open = vi.fn();
  beforeEach(() => {
    rol.set('ADMIN');
    actualizar.mockClear();
    snackbar.open.mockClear();
    resultado = new Subject();
    open.mockReset();
    open.mockReturnValue({ afterClosed: () => resultado });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SesionService, useValue: { rol, usuario: signal({ barberoId: 3 }) } },
        { provide: AvisosService, useValue: { actualizar } },
        { provide: MatDialog, useValue: { open } },
        { provide: MatSnackBar, useValue: snackbar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function crear() {
    const fixture = TestBed.createComponent(Agenda);
    if (rol() === 'ADMIN')
      http
        .expectOne('/api/barberos?incluirInactivos=true')
        .flush([{ id: 3, nombre: 'Profesional ficticio', activo: true }]);
    const inicial = http.expectOne((r) => r.url === '/api/reservas');
    expect(inicial.request.params.get('desde')).toBe(fechaHoyLima());
    expect(inicial.request.params.get('hasta')).toBe(fechaHoyLima());
    expect(inicial.request.params.get('tamano')).toBe('100');
    expect(inicial.request.params.has('barberoId')).toBe(false);
    inicial.flush({ contenido: [], pagina: 0, totalPaginas: 1 });
    return fixture;
  }
  it('destaca la primera transición en el orden existente y cuenta solo las citas cargadas', () => {
    const fixture = crear();
    const agenda = fixture.componentInstance;
    const reserva = {
      ...RESERVA_PRUEBA,
      permisos: {
        reprogramar: false,
        cancelar: false,
        transiciones: ['NO_ASISTIO', 'EN_ATENCION'] as EstadoTransicion[],
      },
    };
    agenda.filas.set([reserva, { ...RESERVA_PRUEBA, id: 102, estado: 'CANCELADA' }]);
    expect(agenda.primeraTransicion(reserva)).toBe('EN_ATENCION');
    expect(
      agenda.primeraTransicion({ ...reserva, permisos: { ...reserva.permisos, transiciones: [] } }),
    ).toBeUndefined();
    expect(agenda.conteos().map((grupo) => [grupo.estado, grupo.total])).toEqual([
      ['CONFIRMADA', 1],
      ['CANCELADA', 1],
    ]);
  });
  it('el BARBERO no tiene selector ni consulta el catálogo, aunque haya un filtro manipulado', () => {
    rol.set('BARBERO');
    const fixture = crear();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-selector-barbero]')).toBeNull();
    http.expectNone((r) => r.url === '/api/barberos');
    fixture.componentInstance.formulario.controls.barberoId.setValue(999);
    fixture.componentInstance.cargar();
    const peticion = http.expectOne((r) => r.url === '/api/reservas');
    expect(peticion.request.params.has('barberoId')).toBe(false);
    peticion.flush({ contenido: [], pagina: 0, totalPaginas: 1 });
  });
  it('ADMIN puede elegir Todos o un barbero', async () => {
    const fixture = crear();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-selector-barbero]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Todos');
    fixture.componentInstance.formulario.controls.barberoId.setValue(3);
    fixture.componentInstance.cargar();
    const peticion = http.expectOne((r) => r.url === '/api/reservas');
    expect(peticion.request.params.get('barberoId')).toBe('3');
    peticion.flush({ contenido: [], pagina: 0, totalPaginas: 1 });
    expect(fixture.componentInstance.todos()).toBe(false);
  });
  it('lee todas las páginas, ordena por instante e id y agrupa en Lima al cruzar el año', () => {
    const fixture = crear();
    fixture.componentInstance.formulario.patchValue({ fecha: '2027-01-01', vista: 'semana' });
    fixture.componentInstance.cargar();
    const pagina0 = http.expectOne((r) => r.url === '/api/reservas');
    expect(pagina0.request.params.get('desde')).toBe('2026-12-28');
    expect(pagina0.request.params.get('hasta')).toBe('2027-01-03');
    const temprana = { ...RESERVA_PRUEBA, id: 1, inicio: '2026-12-31T10:00:00-05:00' };
    const tardia = { ...RESERVA_PRUEBA, id: 2, inicio: '2027-01-01T02:00:00Z' };
    pagina0.flush({ contenido: [tardia], pagina: 0, totalPaginas: 2, totalElementos: 101 });
    const pagina1 = http.expectOne((r) => r.url === '/api/reservas');
    expect(pagina1.request.params.get('pagina')).toBe('1');
    expect(pagina1.request.params.get('tamano')).toBe('100');
    pagina1.flush({ contenido: [temprana], pagina: 1, totalPaginas: 2 });
    expect(fixture.componentInstance.grupos()).toHaveLength(7);
    expect(fixture.componentInstance.grupos()[3].filas.map((r) => r.id)).toEqual([1, 2]);
    expect(fixture.componentInstance.filas().map((r) => r.id)).toEqual([1, 2]);
  });
  it.each([
    ['CONFIRMADA', 'Confirmar'],
    ['EN_ATENCION', 'Iniciar atención'],
    ['COMPLETADA', 'Completar'],
    ['NO_ASISTIO', 'No asistió'],
  ] as [EstadoTransicion, string][])(
    'solo muestra la transición autorizada %s',
    (estado, nombre) => {
      const fixture = crear();
      fixture.componentInstance.periodo.set({ desde: '2026-10-05', hasta: '2026-10-05' });
      fixture.componentInstance.filas.set([
        {
          ...RESERVA_PRUEBA,
          permisos: { cancelar: false, reprogramar: false, transiciones: [estado] },
        },
      ]);
      fixture.detectChanges();
      const textos = Array.from(fixture.nativeElement.querySelectorAll('article button')).map((b) =>
        (b as HTMLElement).textContent?.trim(),
      );
      expect(textos).toEqual([nombre, 'Ver cambios']);
    },
  );
  it('BARBERO no ve ni abre cancelar/reprogramar; Ver cambios sigue disponible', () => {
    rol.set('BARBERO');
    const fixture = crear();
    fixture.componentInstance.periodo.set({ desde: '2026-10-05', hasta: '2026-10-05' });
    fixture.componentInstance.filas.set([RESERVA_PRUEBA]);
    fixture.detectChanges();
    const textos = fixture.nativeElement.textContent;
    expect(textos).not.toContain('Reprogramar');
    expect(textos).not.toContain('Cancelar');
    expect(textos).toContain('Ver cambios');
    fixture.componentInstance.cancelar(RESERVA_PRUEBA);
    fixture.componentInstance.reprogramar(RESERVA_PRUEBA);
    expect(open).not.toHaveBeenCalled();
  });
  it('ADMIN abre cancelación con motivo obligatorio y reprogramación con catálogo', () => {
    const fixture = crear();
    fixture.componentInstance.cancelar(RESERVA_PRUEBA);
    expect(open).toHaveBeenCalledWith(
      CancelarDialogo,
      expect.objectContaining({ data: { ...RESERVA_PRUEBA, motivoObligatorio: true } }),
    );
    fixture.componentInstance.reprogramar(RESERVA_PRUEBA);
    expect(open).toHaveBeenCalledWith(
      ReprogramarDialogo,
      expect.objectContaining({
        data: { reserva: RESERVA_PRUEBA, barberos: fixture.componentInstance.barberos() },
      }),
    );
    fixture.componentInstance.verCambios(RESERVA_PRUEBA);
    expect(open).toHaveBeenCalledWith(
      AuditoriaDialogo,
      expect.objectContaining({ data: RESERVA_PRUEBA }),
    );
  });
  it('confirmación lleva DTO y destino, un 409 recarga la agenda y avisa', () => {
    const fixture = crear();
    const reserva = {
      ...RESERVA_PRUEBA,
      permisos: {
        ...RESERVA_PRUEBA.permisos,
        transiciones: ['EN_ATENCION' as const],
      },
    };
    fixture.componentInstance.transicionar(reserva, 'EN_ATENCION');
    expect(open).toHaveBeenCalledWith(
      TransicionDialogo,
      expect.objectContaining({ data: { reserva, estado: 'EN_ATENCION' } }),
    );
    resultado.next('actualizada');
    http
      .expectOne((r) => r.url === '/api/reservas')
      .flush({
        contenido: [],
        pagina: 0,
        totalPaginas: 1,
      });
    expect(snackbar.open).toHaveBeenCalledWith(expect.stringContaining('Recargamos'), 'Cerrar', {
      duration: 6000,
    });
    expect(actualizar).toHaveBeenCalledOnce();
  });
  it('no abre una transición ausente del permiso ni recarga al descartar', () => {
    const fixture = crear();
    fixture.componentInstance.transicionar(RESERVA_PRUEBA, 'EN_ATENCION');
    expect(open).not.toHaveBeenCalled();
    fixture.componentInstance.cancelar(RESERVA_PRUEBA);
    resultado.next(undefined);
    http.expectNone((r) => r.url === '/api/reservas');
  });
  it('cancela las páginas anteriores al cambiar filtros', () => {
    const fixture = crear();
    fixture.componentInstance.cargar();
    const anterior = http.expectOne((r) => r.url === '/api/reservas');
    fixture.componentInstance.formulario.controls.fecha.setValue('2026-10-05');
    fixture.componentInstance.cargar();
    expect(anterior.cancelled).toBe(true);
    http
      .expectOne((r) => r.url === '/api/reservas')
      .flush({
        contenido: [],
        pagina: 0,
        totalPaginas: 1,
      });
  });
  it('fecha inválida no consulta ni lanza error', () => {
    const fixture = crear();
    fixture.componentInstance.formulario.controls.fecha.setValue('2026-02-31');
    fixture.componentInstance.cargar();
    expect(fixture.componentInstance.formulario.invalid).toBe(true);
    http.expectNone((r) => r.url === '/api/reservas');
  });
  it('un fallo muestra detail y permite reintentar el filtro', () => {
    const fixture = crear();
    fixture.componentInstance.cargar();
    http
      .expectOne((r) => r.url === '/api/reservas')
      .flush({ detail: 'Rango inválido' }, { status: 400, statusText: 'Bad Request' });
    expect(fixture.componentInstance.mensaje()).toBe('Rango inválido');
    expect(fixture.componentInstance.cargando()).toBe(false);
  });
});
