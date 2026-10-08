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
import { TituloPagina } from '../../shared/titulo-pagina';

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
  it.each([false, true])(
    'conserva un único h1 accesible y sincroniza la barra, móvil=%s',
    async (esMovil) => {
      movil = esMovil;
      const f = preparar();
      await f.whenStable();
      expect(f.nativeElement.querySelectorAll('h1')).toHaveLength(1);
      expect(f.nativeElement.querySelector('h1').classList.contains('oculto')).toBe(esMovil);
      expect(TestBed.inject(TituloPagina).texto()).toBe('Reservar un turno');
      seleccionar(f);
      await f.whenStable();
      expect(TestBed.inject(TituloPagina).texto()).toBe('Revise su turno');
      expect(f.nativeElement.querySelectorAll('h1')).toHaveLength(1);
      expect(f.nativeElement.querySelector('app-reserva-resumen')).toBeNull();
      f.destroy();
      expect(TestBed.inject(TituloPagina).texto()).toBeNull();
    },
  );
  it('el anónimo móvil conserva visible el h1 porque la barra muestra la marca', () => {
    movil = true;
    const f = preparar('');
    expect(f.nativeElement.querySelector('h1').classList.contains('oculto')).toBe(false);
  });
  it('reintenta disponibilidad conservando servicio, profesional y fecha', async () => {
    const f = preparar();
    const store = f.componentInstance.store;
    store.elegirServicio(1);
    http.expectOne((r) => r.url === '/api/disponibilidad').flush({ franjas: [] });
    store.elegirPreferencia(3);
    http.expectOne((r) => r.url === '/api/disponibilidad').flush({ franjas: [] });
    store.elegirFecha(fechaHoyLima(1));
    http
      .expectOne((r) => r.url === '/api/disponibilidad')
      .flush({ detail: 'Sin conexión.' }, { status: 503, statusText: 'Service Unavailable' });
    f.detectChanges();
    await f.whenStable();
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain(
      'Su servicio y profesional se conservan',
    );
    expect(f.nativeElement.textContent).not.toContain('No hay horas libres este día.');
    const boton = [...f.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.trim() === 'Reintentar',
    ) as HTMLButtonElement;
    boton.click();
    const peticion = http.expectOne((r) => r.url === '/api/disponibilidad');
    expect(peticion.request.params.get('servicioId')).toBe('1');
    expect(peticion.request.params.get('barberoId')).toBe('3');
    expect(peticion.request.params.get('fecha')).toBe(fechaHoyLima(1));
    f.detectChanges();
    expect(f.nativeElement.querySelector('app-reserva-esqueleto')).not.toBeNull();
    peticion.flush({ franjas: [] });
    f.detectChanges();
    expect(store.servicioId()).toBe(1);
    expect(store.preferencia()).toBe(3);
    expect(store.mensaje()).toBe('');
    expect(f.nativeElement.textContent).toContain('No hay horas libres este día.');
  });
  it('marca el horario anterior con del y conserva las referencias al reprogramar', async () => {
    const f = preparar();
    seleccionar(f);
    f.componentInstance.store.reserva.set({
      id: 7,
      codigo: 'BT-7',
      cliente: { id: 1, nombre: 'Ficticio' },
      servicio: { id: 1, nombre: 'Corte original' },
      barbero: { id: 2, nombre: 'Ficticio A' },
      inicio: '2026-10-09T09:00:00-05:00',
      fin: '2026-10-09T09:40:00-05:00',
      duracionMin: 40,
      precioRef: 22,
      estado: 'CONFIRMADA',
      version: 4,
      permisos: { reprogramar: true, cancelar: true, transiciones: [] },
    });
    f.detectChanges();
    await f.whenStable();
    const anterior = f.nativeElement.querySelector('del');
    expect(anterior.textContent).toContain('Horario anterior, se reemplaza:');
    expect(anterior.textContent).toContain('09:00 con Ficticio A');
    expect(anterior.querySelector('.oculto')).not.toBeNull();
    expect(f.nativeElement.querySelector('.resumen').textContent).toContain('40 minutos');
    expect(f.nativeElement.querySelector('.resumen').textContent).toContain('S/ 22.00');
    expect(f.nativeElement.querySelector('h1').textContent).toBe('Reprogramar cita');
    expect(f.nativeElement.textContent).toContain(
      'Se conservan el servicio, el precio y la duración',
    );
  });
  it('los días incluyen el año y las fotos decorativas usan recursos locales', () => {
    const f = preparar();
    expect(f.nativeElement.querySelector('[data-dia]').getAttribute('aria-label')).toContain(
      fechaHoyLima().slice(0, 4),
    );
    const foto = f.nativeElement.querySelector('picture img');
    expect(foto.getAttribute('alt')).toBe('');
    expect(foto.getAttribute('src')).toBe('/fotos/tijeras-mesa-recorte.webp');
    expect(foto.hasAttribute('loading')).toBe(false);
    expect(f.nativeElement.querySelector('picture source').getAttribute('srcset')).toBe(
      '/fotos/interior-vacio.webp',
    );
  });
});
