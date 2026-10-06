import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatStepper } from '@angular/material/stepper';
import { MatDatepickerInput } from '@angular/material/datepicker';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SesionService } from '../../core/auth/sesion-service';
import { fechaCivil } from '../../core/tiempo/fecha-datepicker';
import { fechaHoyLima } from '../../core/tiempo/instante-lima';
import { Reservar } from './reservar';

describe('Vista de reserva guiada', () => {
  let http: HttpTestingController;
  let movil = false;
  beforeEach(() => {
    sessionStorage.clear();
    movil = false;
    TestBed.configureTestingModule({
      imports: [Reservar],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches: movil }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });
  function preparar(rol = 'CLIENTE') {
    if (rol) {
      TestBed.inject(SesionService).cargar().subscribe();
      http.expectOne('/api/auth/sesion').flush({
        id: 1,
        nombre: 'Ficticio',
        correo: 't26@ejemplo.test',
        rol,
        debeCambiarPassword: false,
      });
    }
    const fixture = TestBed.createComponent(Reservar);
    http.expectOne('/api/servicios?incluirInactivos=false').flush([
      {
        id: 1,
        nombre: 'Corte',
        descripcion: 'Clásico',
        duracionMin: 30,
        precio: 25,
        activo: true,
      },
    ]);
    http.expectOne('/api/barberos?incluirInactivos=false').flush([
      { id: 2, nombre: 'Ficticio A', especialidad: 'Corte', activo: true },
      { id: 3, nombre: 'Ficticio B', especialidad: 'Corte', activo: true },
    ]);
    fixture.detectChanges();
    return fixture;
  }
  function seleccionar(f: ReturnType<typeof preparar>) {
    const s = f.componentInstance.store;
    s.elegirServicio(1);
    const r = http.expectOne((r) => r.url === '/api/disponibilidad');
    r.flush({
      franjas: [
        {
          inicio: fechaHoyLima() + 'T10:00:00-05:00',
          fin: fechaHoyLima() + 'T10:30:00-05:00',
          barberoIds: [2, 3],
        },
      ],
    });
    s.irPaso(1);
    s.elegirFranja(s.franjas()[0]);
    f.detectChanges();
  }
  it('muestra tarjetas, precios, pago presencial y stepper lineal', () => {
    const f = preparar('');
    expect(f.nativeElement.textContent).toContain('S/ 25.00');
    expect(f.nativeElement.textContent).toContain('Pago presencial');
    expect(f.debugElement.query(By.directive(MatStepper)).componentInstance.orientation).toBe(
      'horizontal',
    );
    expect(f.componentInstance.store.sesion.autenticado()).toBe(false);
  });
  it.each([
    [false, 'CLIENTE'],
    [true, 'CLIENTE'],
    [false, 'ADMIN'],
    [true, 'ADMIN'],
  ])('conserva el foco en la primera presentación, móvil=%s rol=%s', async (esMovil, rol) => {
    movil = esMovil as boolean;
    const focoInicial = document.activeElement;
    const f = preparar(rol as string);
    await f.whenStable();
    await new Promise<void>((resolver) =>
      requestAnimationFrame(() => requestAnimationFrame(() => resolver())),
    );
    expect(document.activeElement).toBe(focoInicial);
  });
  it.each([false, true])('enfoca la selección restaurada, móvil=%s', async (esMovil) => {
    movil = esMovil;
    const fecha = fechaHoyLima();
    sessionStorage.setItem(
      'barberturno.reserva',
      JSON.stringify({
        servicioId: 1,
        preferencia: null,
        fecha,
        inicio: fecha + 'T10:00:00-05:00',
        barberoId: 2,
      }),
    );
    const f = preparar();
    http
      .expectOne((r) => r.url === '/api/disponibilidad')
      .flush({
        franjas: [
          { inicio: fecha + 'T10:00:00-05:00', fin: fecha + 'T10:30:00-05:00', barberoIds: [2] },
        ],
      });
    f.detectChanges();
    await f.whenStable();
    expect(f.componentInstance.store.paso()).toBe(2);
    await vi.waitFor(() =>
      expect(document.activeElement).toBe(f.nativeElement.querySelector('[data-paso="2"]')),
    );
  });
  it('muestra Sin preferencia como selección inicial del profesional', async () => {
    const f = preparar('');
    await f.whenStable();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[data-profesional=sin-preferencia]').checked).toBe(true);
  });
  it('agrupa horas por Lima incluso cuando los instantes tienen otro desfase', () => {
    const f = preparar('');
    f.componentInstance.store.franjas.set([
      { inicio: '2026-10-05T16:59:00Z', fin: '2026-10-05T17:29:00Z', barberoIds: [2] },
      { inicio: '2026-10-05T17:00:00Z', fin: '2026-10-05T17:30:00Z', barberoIds: [2] },
    ]);
    const grupos = f.componentInstance.gruposHoras();
    expect(grupos.map((g) => [g.nombre, g.franjas.map((franja) => franja.inicio)])).toEqual([
      ['Mañana', ['2026-10-05T16:59:00Z']],
      ['Tarde', ['2026-10-05T17:00:00Z']],
    ]);
  });
  it('presenta siete días dentro de los límites civiles sin desplazar hoy', () => {
    const f = preparar('');
    const dias = f.componentInstance.dias;
    expect(dias).toHaveLength(7);
    expect(dias[0]).toBe(fechaHoyLima());
    expect(dias[6]).toBe(fechaHoyLima(6));
    expect(dias.every((dia) => dia <= fechaCivil(f.componentInstance.limites.max))).toBe(true);
    expect(f.componentInstance.fechaLegible('2026-09-28')).toContain('lunes');
    expect(f.componentInstance.fechaLegible('2026-09-28')).toContain('28 de setiembre');
  });
  it.each([false, true])(
    'muestra el paso 3 y permite volver al paso 2, móvil=%s',
    async (esMovil) => {
      movil = esMovil;
      const f = preparar('');
      seleccionar(f);
      await f.whenStable();
      f.detectChanges();
      const stepper = f.debugElement.query(By.directive(MatStepper)).componentInstance;
      expect(stepper.selectedIndex).toBe(2);
      const pasos = f.nativeElement.querySelectorAll('mat-step-header');
      expect(pasos[2].getAttribute('aria-selected')).toBe('true');
      await vi.waitFor(() =>
        expect(document.activeElement).toBe(f.nativeElement.querySelector('[data-paso="2"]')),
      );
      f.componentInstance.store.irPaso(1);
      f.detectChanges();
      await f.whenStable();
      expect(stepper.selectedIndex).toBe(1);
      await vi.waitFor(() =>
        expect(document.activeElement).toBe(f.nativeElement.querySelector('[data-paso="1"]')),
      );
    },
  );
  it('conserva el asistente horizontal por debajo de 768 px', () => {
    movil = true;
    const f = preparar();
    expect(f.debugElement.query(By.directive(MatStepper)).componentInstance.orientation).toBe(
      'horizontal',
    );
  });
  it('usa límites civiles de Lima y rechaza entradas de calendario fuera de rango', () => {
    const f = preparar();
    seleccionar(f);
    const entrada = f.debugElement
      .query(By.directive(MatDatepickerInput))
      .injector.get(MatDatepickerInput);
    expect(fechaCivil(entrada.min as Date)).toBe(fechaHoyLima());
    expect(fechaCivil(entrada.max as Date)).toBe(fechaHoyLima(30));
    f.componentInstance.fechaControl.setValue(new Date(2000, 0, 1));
    f.detectChanges();
    expect(f.componentInstance.fechaControl.hasError('matDatepickerMin')).toBe(true);
    f.componentInstance.cambiarFecha();
    http.expectNone((r) => r.url === '/api/disponibilidad');
  });
  it('deshabilita confirmar y fecha durante envío y evita la segunda petición', () => {
    const f = preparar();
    seleccionar(f);
    const boton = f.nativeElement.querySelector('[data-confirmar]') as HTMLButtonElement;
    expect(boton.disabled).toBe(false);
    boton.click();
    f.detectChanges();
    expect(boton.disabled).toBe(true);
    expect(boton.textContent).toContain('Enviando');
    expect(f.componentInstance.fechaControl.disabled).toBe(true);
    f.componentInstance.store.confirmar();
    http
      .expectOne('/api/reservas')
      .flush(
        { codigo: 'FUERA_DE_HORARIO', detail: 'Elija otra franja.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    f.detectChanges();
    expect(boton.disabled).toBe(false);
  });
  it('BARBERO puede explorar pero no tiene botón confirmar', () => {
    const f = preparar('BARBERO');
    seleccionar(f);
    expect(f.nativeElement.querySelector('[data-confirmar]')).toBeNull();
    expect(f.nativeElement.textContent).toContain('no confirmar reservas');
  });
  it('presenta el enlace de límite activo y el resumen en horas de Lima', () => {
    const f = preparar();
    seleccionar(f);
    expect(f.nativeElement.textContent).toContain('10:00');
    expect(f.nativeElement.textContent).toContain('Ficticio A');
    f.componentInstance.store.confirmar();
    http
      .expectOne('/api/reservas')
      .flush(
        { codigo: 'LIMITE_RESERVAS_ACTIVAS', detail: 'Tiene tres reservas.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/mis-citas"]')).not.toBeNull();
  });
  it.each([false, true])(
    'ADMIN ve Cliente como paso previo y el resumen conserva la identidad, móvil=%s',
    async (esMovil) => {
      movil = esMovil;
      const f = preparar('ADMIN');
      await f.whenStable();
      f.detectChanges();
      const stepper = f.debugElement.query(By.directive(MatStepper))
        .componentInstance as MatStepper;
      expect(stepper.steps.length).toBe(4);
      expect(stepper.selectedIndex).toBe(0);
      expect(f.nativeElement.querySelector('app-selector-cliente')).not.toBeNull();
      f.componentInstance.store.elegirCliente({
        id: 77,
        nombre: 'Asistido ficticio',
        correo: 'asistido@ejemplo.test',
        telefono: null,
        rol: 'CLIENTE',
        activo: true,
        debeCambiarPassword: false,
      });
      f.componentInstance.store.irPaso(0);
      await f.whenStable();
      f.detectChanges();
      expect(stepper.selectedIndex).toBe(1);
      seleccionar(f);
      await f.whenStable();
      f.detectChanges();
      expect(stepper.selectedIndex).toBe(3);
      expect(f.nativeElement.textContent).toContain('Asistido ficticio');
      f.componentInstance.store.irPaso(-1);
      await f.whenStable();
      f.detectChanges();
      expect(stepper.selectedIndex).toBe(0);
    },
  );
});
