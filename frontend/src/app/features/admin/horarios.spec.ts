import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import {
  ConfirmarEstadoDialogo,
  ConfirmarEstadoDatos,
} from '../../shared/confirmar-estado-dialogo';
import { Horarios } from './horarios';

describe('Administración de horarios', () => {
  let http: HttpTestingController;
  let cerrado = new Subject<boolean>();
  const dialogos = { open: vi.fn(() => ({ afterClosed: () => cerrado })) };
  const semana = [
    { diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' },
    { diaSemana: 1, horaInicio: '14:00', horaFin: '18:00' },
  ];
  const bloqueo = {
    id: 4,
    barberoId: 17,
    inicio: '2026-10-05T21:00:00Z',
    fin: '2026-10-05T22:00:00Z',
    motivo: 'Trámite ficticio',
  };
  beforeEach(() => {
    cerrado = new Subject<boolean>();
    dialogos.open.mockClear();
    TestBed.configureTestingModule({
      imports: [Horarios],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: dialogos },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogos });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function preparar() {
    const fixture = TestBed.createComponent(Horarios);
    http.expectOne('/api/barberos?incluirInactivos=true').flush([
      { id: 17, nombre: 'Ficticio A', activo: true },
      { id: 18, nombre: 'Ficticio B', activo: true },
      { id: 19, nombre: 'Ficticio C', activo: false },
    ]);
    const pagina = fixture.componentInstance;
    pagina.seleccion.setValue(17);
    pagina.cargarBarbero();
    http.expectOne('/api/barberos/17/jornadas').flush(semana);
    http.expectOne((r) => r.url === '/api/barberos/17/bloqueos').flush([bloqueo]);
    fixture.detectChanges();
    return fixture;
  }
  function alta(pagina: Horarios, todos: boolean) {
    pagina.alta.setValue({
      fecha: '2026-10-05',
      horaInicio: '16:00',
      horaFin: '17:00',
      motivo: 'Trámite ficticio',
      todos,
    });
  }
  it('muestra los inactivos, ayuda del descanso, siete días y horas de Lima', () => {
    const f = preparar();
    expect(f.componentInstance.barberos().find((b) => b.id === 19)?.activo).toBe(false);
    expect(f.nativeElement.textContent).toContain('El descanso es el hueco');
    expect(f.nativeElement.querySelectorAll('.dia')).toHaveLength(7);
    expect(f.nativeElement.querySelector('.lista-bloqueos').textContent).toContain('16:00');
    expect(f.nativeElement.querySelector('.lista-bloqueos').textContent).toContain('17:00');
    expect(f.nativeElement.querySelectorAll('mat-label').length).toBeGreaterThan(8);
  });
  it('guarda la semana completa una sola vez y utiliza el DTO recibido', () => {
    const f = preparar();
    const p = f.componentInstance;
    p.guardarSemana();
    p.guardarSemana();
    const r = http.expectOne('/api/barberos/17/jornadas');
    expect(r.request.body).toEqual(semana);
    r.flush(semana.slice(0, 1));
    expect(p.editor.dto()).toEqual(semana.slice(0, 1));
  });
  it('no envía intervalos solapados y permite corregirlos', () => {
    const f = preparar();
    const p = f.componentInstance;
    p.editor.delDia(1)[1].controls.horaInicio.setValue('12:00');
    p.guardarSemana();
    http.expectNone('/api/barberos/17/jornadas');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('solapados');
    p.editor.delDia(1)[1].controls.horaInicio.setValue('14:00');
    p.guardarSemana();
    http.expectOne('/api/barberos/17/jornadas').flush(semana);
  });
  it.each(['[1].horaFin', '[1]'])(
    'asocia el 400 JORNADA_INVALIDA %s al intervalo correcto',
    (campo) => {
      const f = preparar();
      const p = f.componentInstance;
      p.guardarSemana();
      http.expectOne('/api/barberos/17/jornadas').flush(
        {
          codigo: 'JORNADA_INVALIDA',
          detail: 'Revise los intervalos.',
          errores: [{ campo, mensaje: 'Intervalo inválido del servidor.' }],
        },
        { status: 400, statusText: 'Bad Request' },
      );
      f.detectChanges();
      expect(f.nativeElement.textContent).toContain('Intervalo inválido del servidor.');
      expect(p.editor.delDia(1)[0].valid).toBe(true);
      expect(p.editor.delDia(1)[1].invalid).toBe(true);
      expect(p.editor.dto()).toEqual(semana);
    },
  );
  it('presenta los BT del 409 de jornada y conserva la edición', () => {
    const f = preparar();
    const p = f.componentInstance;
    p.guardarSemana();
    http.expectOne('/api/barberos/17/jornadas').flush(
      {
        codigo: 'CONFLICTO_CON_RESERVAS',
        detail: 'La jornada deja fuera reservas futuras.',
        reservas: [101, 102],
      },
      { status: 409, statusText: 'Conflict' },
    );
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Reprograme o cancele primero estas reservas');
    expect(f.nativeElement.textContent).toContain('BT-101');
    expect(f.nativeElement.textContent).toContain('BT-102');
    expect(p.editor.dto()).toEqual(semana);
  });
  it.each([false, true])('la casilla todos=%s usa el endpoint y los ids correctos', (todos) => {
    const f = preparar();
    const p = f.componentInstance;
    alta(p, todos);
    const checkbox = f.nativeElement.querySelector('mat-checkbox input') as HTMLInputElement;
    if (todos) {
      p.alta.controls.todos.setValue(false);
      checkbox.click();
      f.detectChanges();
    }
    expect(p.alta.controls.todos.value).toBe(todos);
    f.nativeElement
      .querySelector('[aria-labelledby="titulo-alta"] form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const r = http.expectOne(todos ? '/api/bloqueos/lote' : '/api/barberos/17/bloqueos');
    expect(r.request.body).toEqual({
      inicio: '2026-10-05T16:00:00-05:00',
      fin: '2026-10-05T17:00:00-05:00',
      motivo: 'Trámite ficticio',
      ...(todos ? { barberoIds: [17, 18] } : {}),
    });
    r.flush(todos ? [bloqueo] : bloqueo);
    http.expectOne((r) => r.url === '/api/barberos/17/bloqueos').flush([bloqueo]);
    expect(p.alta.controls.motivo.value).toBe('');
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('mat-form-field.mat-form-field-invalid')).toHaveLength(
      0,
    );
  });
  it('presenta conflictos de lote por barbero, sin crear ni recargar parcialmente', () => {
    const f = preparar();
    const p = f.componentInstance;
    alta(p, true);
    p.crearBloqueo();
    http.expectOne('/api/bloqueos/lote').flush(
      {
        codigo: 'CONFLICTO_CON_RESERVAS',
        reservas: { '17': [101], '18': [103, 104] },
      },
      { status: 409, statusText: 'Conflict' },
    );
    f.detectChanges();
    const texto = f.nativeElement.querySelector('[role="alert"]').textContent;
    for (const dato of ['Ficticio A', 'Ficticio B', 'BT-101', 'BT-103', 'BT-104'])
      expect(texto).toContain(dato);
    http.expectNone((r) => r.url.includes('/bloqueos'));
    expect(p.alta.controls.motivo.value).toBe('Trámite ficticio');
  });
  it('presenta el 409 individual y los errores 400 de fin en su campo', () => {
    const f = preparar();
    const p = f.componentInstance;
    alta(p, false);
    p.crearBloqueo();
    http
      .expectOne('/api/barberos/17/bloqueos')
      .flush(
        { codigo: 'CONFLICTO_CON_RESERVAS', reservas: [101] },
        { status: 409, statusText: 'Conflict' },
      );
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('BT-101');
    p.crearBloqueo();
    http
      .expectOne('/api/barberos/17/bloqueos')
      .flush(
        { codigo: 'VALIDACION', errores: [{ campo: 'fin', mensaje: 'Fin inválido.' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Fin inválido.');
    expect(p.conflictos()).toEqual([]);
  });
  it('consulta el rango elegido y muestra un 400 sin conservar una lista obsoleta', () => {
    const f = preparar();
    const p = f.componentInstance;
    p.rango.setValue({ desde: '2026-10-01', hasta: '2026-10-31' });
    p.cargarBloqueos();
    const r = http.expectOne('/api/barberos/17/bloqueos?desde=2026-10-01&hasta=2026-10-31');
    r.flush(
      { codigo: 'RANGO_FECHAS_INVALIDO', detail: 'Rango inválido.' },
      { status: 400, statusText: 'Bad Request' },
    );
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Rango inválido.');
    expect(p.bloqueos()).toEqual([]);
  });
  it('reutiliza la confirmación de eliminación y solo retira el bloqueo si se confirma', () => {
    const f = preparar();
    const p = f.componentInstance;
    p.eliminar(bloqueo);
    cerrado.next(false);
    cerrado.complete();
    cerrado = new Subject<boolean>();
    expect(p.bloqueos()).toEqual([bloqueo]);
    p.eliminar(bloqueo);
    expect(dialogos.open).toHaveBeenCalledWith(
      ConfirmarEstadoDialogo,
      expect.objectContaining({
        data: expect.objectContaining({ titulo: 'Eliminar bloqueo', boton: 'Eliminar' }),
      }),
    );
    const config = (
      dialogos.open.mock.calls[1] as unknown as [unknown, { data: ConfirmarEstadoDatos }]
    )[1];
    config.data.cambiar().subscribe((n) => expect(n).toBe(0));
    const r = http.expectOne('/api/bloqueos/4');
    expect(r.request.method).toBe('DELETE');
    r.flush(null, { status: 204, statusText: 'No Content' });
    cerrado.next(true);
    expect(p.bloqueos()).toEqual([]);
  });
  it('impide guardar la semana si falla su carga', () => {
    const f = preparar();
    const p = f.componentInstance;
    p.seleccion.setValue(19);
    p.cargarBarbero();
    http
      .expectOne('/api/barberos/19/jornadas')
      .flush({ detail: 'No encontrado.' }, { status: 404, statusText: 'Not Found' });
    http.expectOne((r) => r.url === '/api/barberos/19/bloqueos');
    p.guardarSemana();
    expect(p.listo()).toBe(false);
    expect(p.editor.dto()).toEqual([]);
    http.expectNone((r) => r.method === 'PUT');
  });
  it('si falla la recarga tras crear, informa el alta y evita repetirla', () => {
    const f = preparar();
    const p = f.componentInstance;
    alta(p, false);
    p.crearBloqueo();
    http.expectOne('/api/barberos/17/bloqueos').flush(bloqueo);
    http
      .expectOne((r) => r.url === '/api/barberos/17/bloqueos')
      .flush(null, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Bloqueo creado. No se pudo actualizar la lista');
    expect(p.alta.invalid).toBe(true);
    expect(p.bloqueos()).toEqual([]);
    p.crearBloqueo();
    http.expectNone((r) => r.method === 'POST');
  });
});
