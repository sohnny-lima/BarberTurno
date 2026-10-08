import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BreakpointObserver } from '@angular/cdk/layout';
import { BehaviorSubject, map, Subject } from 'rxjs';
import { TituloPagina } from '../../shared/titulo-pagina';
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
  let ancho: BehaviorSubject<number>;
  beforeEach(() => {
    ancho = new BehaviorSubject(360);
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
        {
          provide: BreakpointObserver,
          useValue: {
            observe: vi.fn(() => ancho.pipe(map((valor) => ({ matches: valor >= 1200 })))),
          },
        },
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
    async (estado, nombre) => {
      const fixture = crear();
      fixture.componentInstance.periodo.set({ desde: '2026-10-05', hasta: '2026-10-05' });
      fixture.componentInstance.filas.set([
        {
          ...RESERVA_PRUEBA,
          permisos: { cancelar: false, reprogramar: false, transiciones: [estado] },
        },
      ]);
      fixture.detectChanges();
      const textos = Array.from(
        fixture.nativeElement.querySelectorAll('app-agenda-cita button'),
      ).map((b) => (b as HTMLElement).textContent?.trim());
      expect(textos).toEqual(estado === 'NO_ASISTIO' ? [''] : [nombre, '']);
      fixture.nativeElement.querySelector('.mas').click();
      await fixture.whenStable();
      const menu = Array.from(document.querySelectorAll('[role="menuitem"]')).map((b) =>
        b.textContent?.trim(),
      );
      expect(menu).toEqual(estado === 'NO_ASISTIO' ? [nombre, 'Ver cambios'] : ['Ver cambios']);
    },
  );
  it('BARBERO no ve ni abre cancelar/reprogramar; Ver cambios sigue disponible', async () => {
    rol.set('BARBERO');
    const fixture = crear();
    fixture.componentInstance.periodo.set({ desde: '2026-10-05', hasta: '2026-10-05' });
    fixture.componentInstance.filas.set([RESERVA_PRUEBA]);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.mas').click();
    await fixture.whenStable();
    const textos = fixture.nativeElement.textContent;
    expect(textos).not.toContain('Reprogramar');
    expect(textos).not.toContain('Cancelar');
    expect(document.querySelector('[role="menu"]')?.textContent).toContain('Ver cambios');
    expect(document.querySelector('[role="menu"]')?.textContent).not.toMatch(
      /Reprogramar|Cancelar/,
    );
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
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Rango inválido',
    );
    const reintentar = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === 'Reintentar',
    ) as HTMLButtonElement;
    reintentar.click();
    http
      .expectOne((r) => r.url === '/api/reservas' && r.params.get('desde') === fechaHoyLima())
      .flush({ contenido: [], pagina: 0, totalPaginas: 1 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });
  it('resume estados en minúsculas con plurales y conjunción', () => {
    const agenda = crear().componentInstance;
    expect(agenda.resumen()).toBe('0 citas.');
    agenda.filas.set([
      { ...RESERVA_PRUEBA, estado: 'EN_ATENCION' },
      { ...RESERVA_PRUEBA, id: 2, estado: 'PENDIENTE' },
      ...[3, 4, 5].map((id) => ({ ...RESERVA_PRUEBA, id })),
    ]);
    expect(agenda.resumen()).toBe('5 citas: 1 en atención, 1 pendiente y 3 confirmadas.');
    agenda.filas.set([{ ...RESERVA_PRUEBA, estado: 'NO_ASISTIO' }]);
    expect(agenda.resumen()).toBe('1 cita: 1 no asistió.');
    agenda.filas.update((filas) => [...filas, { ...filas[0], id: 2 }]);
    expect(agenda.resumen()).toBe('2 citas: 2 no asistieron.');
  });
  it('agrupa solo días vacíos consecutivos y conserva el nombre accesible del rango', () => {
    const fixture = crear();
    fixture.componentInstance.formulario.controls.vista.setValue('semana');
    fixture.componentInstance.periodo.set({ desde: '2026-09-28', hasta: '2026-10-04' });
    fixture.componentInstance.filas.set([
      { ...RESERVA_PRUEBA, inicio: '2026-09-28T09:30:00-05:00' },
      { ...RESERVA_PRUEBA, id: 2, inicio: '2026-10-01T10:00:00-05:00' },
    ]);
    fixture.detectChanges();
    expect(fixture.componentInstance.grupos()).toHaveLength(7);
    expect(fixture.componentInstance.bloques().map((b) => b.dias.length)).toEqual([1, 2, 1, 3]);
    const grupos = fixture.nativeElement.querySelectorAll('.dias-vacios');
    expect(grupos[0].textContent).toContain('mar 29 y mié 30');
    expect(grupos[0].textContent).toContain('· sin citas');
    expect(grupos[0].getAttribute('aria-label')).toBe('Agenda del 2026-09-29 al 2026-09-30');
    expect(grupos[1].textContent).toContain('vie 2 al dom 4');
  });
  it.each([360, 768, 1199, 1200, 1440])('presenta tarjetas o tabla a %i px', (valor) => {
    ancho.next(valor);
    const fixture = crear();
    fixture.componentInstance.periodo.set({ desde: '2026-10-05', hasta: '2026-10-05' });
    fixture.componentInstance.filas.set([RESERVA_PRUEBA]);
    fixture.detectChanges();
    expect(TestBed.inject(BreakpointObserver).observe).toHaveBeenCalledWith('(min-width: 1200px)');
    expect(fixture.nativeElement.querySelector('[role="table"]') !== null).toBe(valor >= 1200);
    expect(fixture.nativeElement.querySelector('app-agenda-cita').getAttribute('role')).toBe(
      valor >= 1200 ? 'row' : 'article',
    );
    if (valor >= 1200)
      expect(
        Array.from(fixture.nativeElement.querySelectorAll('[role="columnheader"]')).map((n) =>
          (n as HTMLElement).textContent?.trim(),
        ),
      ).toEqual(['Hora', 'Cliente', 'Servicio', 'Barbero', 'Estado', 'Acciones']);
    fixture.componentInstance.formulario.controls.vista.setValue('semana');
    fixture.detectChanges();
    const citaSemanal = fixture.nativeElement.querySelector('app-agenda-cita');
    expect(citaSemanal.classList.contains('semana')).toBe(valor < 1200);
    expect(citaSemanal.querySelectorAll('[role="cell"]').length).toBe(valor >= 1200 ? 6 : 0);
  });
  it.each([false, true])(
    'ADMIN distribuye reprogramar y otras acciones sin duplicarlas: escritorio=%s',
    async (escritorio) => {
      ancho.next(escritorio ? 1440 : 360);
      const fixture = crear();
      fixture.componentInstance.periodo.set({ desde: '2026-10-05', hasta: '2026-10-05' });
      fixture.componentInstance.filas.set([
        {
          ...RESERVA_PRUEBA,
          permisos: { ...RESERVA_PRUEBA.permisos, transiciones: ['EN_ATENCION', 'NO_ASISTIO'] },
        },
      ]);
      fixture.detectChanges();
      const botones = Array.from(
        fixture.nativeElement.querySelectorAll('app-agenda-cita button'),
      ).map((b) => (b as HTMLElement).textContent?.trim());
      expect(botones).toEqual(
        escritorio ? ['Iniciar atención', 'Reprogramar', ''] : ['Iniciar atención', ''],
      );
      const mas = fixture.nativeElement.querySelector('.mas');
      expect(mas.getAttribute('aria-label')).toBe('Más acciones para BT-101');
      mas.click();
      await fixture.whenStable();
      const items = Array.from(document.querySelectorAll('[role="menuitem"]'));
      expect(items.map((n) => n.textContent?.trim())).toEqual(
        escritorio
          ? ['No asistió', 'Cancelar', 'Ver cambios']
          : ['No asistió', 'Reprogramar', 'Cancelar', 'Ver cambios'],
      );
      (items.at(-1) as HTMLButtonElement).click();
      expect(open).toHaveBeenCalledWith(
        AuditoriaDialogo,
        expect.objectContaining({ data: expect.objectContaining({ codigo: 'BT-101' }) }),
      );
    },
  );
  it('Semana en tarjetas lleva la principal al menú y mantiene teléfono con destino original', async () => {
    const fixture = crear();
    fixture.componentInstance.formulario.controls.vista.setValue('semana');
    fixture.componentInstance.periodo.set({ desde: '2026-10-05', hasta: '2026-10-11' });
    fixture.componentInstance.filas.set([
      {
        ...RESERVA_PRUEBA,
        cliente: { ...RESERVA_PRUEBA.cliente, telefono: '987412036' },
        permisos: { cancelar: false, reprogramar: false, transiciones: ['CONFIRMADA'] },
      },
    ]);
    fixture.detectChanges();
    const enlace = fixture.nativeElement.querySelector('app-agenda-cita a');
    expect(enlace.textContent.trim()).toBe('987 412 036');
    expect(enlace.getAttribute('href')).toBe('tel:987412036');
    expect(fixture.nativeElement.querySelector('app-agenda-cita').textContent).not.toContain(
      'Confirmar',
    );
    fixture.nativeElement.querySelector('.mas').click();
    await fixture.whenStable();
    const confirmar = document.querySelector('[role="menuitem"]') as HTMLButtonElement;
    expect(confirmar.textContent?.trim()).toBe('Confirmar');
    confirmar.click();
    expect(open).toHaveBeenCalledWith(
      TransicionDialogo,
      expect.objectContaining({ data: expect.objectContaining({ estado: 'CONFIRMADA' }) }),
    );
  });
  it('la línea Ahora conserva la estructura de fila y celda de la tabla accesible', () => {
    ancho.next(1440);
    const fixture = crear();
    fixture.componentInstance.filas.set([
      { ...RESERVA_PRUEBA, inicio: fechaHoyLima() + 'T23:50:00-05:00' },
    ]);
    fixture.detectChanges();
    const ahora = fixture.nativeElement.querySelector('.ahora');
    expect(ahora.textContent).toBe('Ahora');
    expect(ahora.parentElement.getAttribute('role')).toBe('cell');
    expect(ahora.parentElement.getAttribute('aria-colspan')).toBe('6');
    expect(ahora.parentElement.parentElement.getAttribute('role')).toBe('row');
  });
  it('carga con esqueletos, anuncio oculto y título único; libera la acción de la barra al destruir', () => {
    const fixture = crear();
    fixture.componentInstance.cargar();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('app-reserva-esqueleto [role="status"]').textContent,
    ).toContain('Cargando agenda…');
    expect(
      fixture.nativeElement.querySelector('app-reserva-esqueleto [aria-hidden="true"]'),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    const titulo = TestBed.inject(TituloPagina);
    expect(titulo.texto()).toBe('Agenda');
    http
      .expectOne((r) => r.url === '/api/reservas')
      .flush({ contenido: [], pagina: 0, totalPaginas: 1 });
    titulo.actualizarAgenda()?.ejecutar();
    http
      .expectOne((r) => r.url === '/api/reservas')
      .flush({ contenido: [], pagina: 0, totalPaginas: 1 });
    fixture.destroy();
    expect(titulo.texto()).toBeNull();
    expect(titulo.actualizarAgenda()).toBeNull();
  });
});
