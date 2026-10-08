import { signal } from '@angular/core';
import { AvisosService } from '../core/notificaciones/avisos-service';
import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSidenav } from '@angular/material/sidenav';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { OverlayContainer } from '@angular/cdk/overlay';
import { By } from '@angular/platform-browser';
import { NavigationEnd, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { SesionService } from '../core/auth/sesion-service';
import { Rol } from '../core/modelos/identidad';
import { Shell } from './shell';
import { TituloPagina } from '../shared/titulo-pagina';
import { AvisosDialogo } from './avisos-dialogo';

describe('Shell adaptable', () => {
  let http: HttpTestingController;
  let movil: BehaviorSubject<{ matches: boolean }>;
  beforeEach(() => {
    movil = new BehaviorSubject<{ matches: boolean }>({ matches: false });
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AvisosService, useValue: { noLeidas: signal(3), actualizar: vi.fn() } },
        { provide: BreakpointObserver, useValue: { observe: vi.fn(() => movil) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function entrar(rol: Rol, temporal = false) {
    TestBed.inject(SesionService).cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush({
      id: 1,
      nombre: 'Usuario ficticio',
      correo: 'shell@ejemplo.test',
      rol,
      debeCambiarPassword: temporal,
    });
  }
  it('usa side en escritorio y over en móvil con botón de menú', () => {
    entrar('ADMIN');
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    const menu = fixture.debugElement.query(By.directive(MatSidenav))
      .componentInstance as MatSidenav;
    expect(menu.mode).toBe('side');
    expect(menu.opened).toBe(true);
    movil.next({ matches: true });
    fixture.detectChanges();
    expect(menu.mode).toBe('over');
    expect(menu.opened).toBe(false);
    expect(fixture.nativeElement.querySelector('[aria-controls=menu-principal]')).not.toBeNull();
    expect(TestBed.inject(BreakpointObserver).observe).toHaveBeenCalledWith(
      '(max-width: 767.98px)',
    );
  });
  it.each([true, false])(
    'una navegación cierra el cajón y enfoca el contenido solo en móvil: móvil=%s',
    async (esMovil) => {
      entrar('ADMIN');
      movil.next({ matches: esMovil });
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      const menu = fixture.debugElement.query(By.directive(MatSidenav))
        .componentInstance as MatSidenav;
      if (esMovil) {
        await menu.open();
        fixture.detectChanges();
      }
      expect(menu.opened).toBe(true);
      expect(await TestBed.inject(Router).navigateByUrl('/?redireccion=ingresar')).toBe(true);
      if (esMovil) {
        // jsdom no ejecuta transiciones CSS; entregar el evento que emite el navegador.
        fixture.nativeElement
          .querySelector('mat-sidenav')
          .dispatchEvent(new Event('transitionend'));
      }
      await fixture.whenStable();
      fixture.detectChanges();
      expect(menu.opened).toBe(!esMovil);
      if (esMovil) {
        // El cierre de Material termina fuera de la estabilidad de Angular.
        await vi.waitFor(() =>
          expect(document.activeElement).toBe(fixture.nativeElement.querySelector('main')),
        );
      }
    },
  );
  it.each([
    ['CLIENTE', ['Reservar', 'Mis citas', 'Mi cuenta']],
    ['BARBERO', ['Agenda', 'Mi cuenta']],
    [
      'ADMIN',
      [
        'Agenda',
        'Reserva asistida',
        'Servicios',
        'Barberos',
        'Horarios',
        'Reportes',
        'Usuarios',
        'Mi cuenta',
      ],
    ],
  ] as [Rol, string[]][])('muestra navegación de %s, identidad y Salir', (rol, esperado) => {
    entrar(rol);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(fixture.componentInstance.enlaces().map((enlace) => enlace.texto)).toEqual(esperado);
    expect(fixture.nativeElement.querySelector('.usuario').textContent).toContain(
      'Usuario ficticio',
    );
    expect(fixture.nativeElement.querySelector('.usuario').textContent).toContain('Salir');
    expect(fixture.nativeElement.querySelector('[data-contador-avisos]').textContent).toContain(
      'Avisos sin leer: 3',
    );
  });
  it.each([
    ['CLIENTE', false],
    ['CLIENTE', true],
    ['BARBERO', true],
    ['ADMIN', true],
  ] as [Rol, boolean][])(
    'oculta el distintivo vacío de %s y conserva su estado accesible, móvil=%s',
    (rol, esMovil) => {
      entrar(rol);
      movil.next({ matches: esMovil });
      (TestBed.inject(AvisosService).noLeidas as ReturnType<typeof signal<number>>).set(0);
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('.distintivo')).toHaveLength(0);
      expect(fixture.nativeElement.querySelector('.contador-personal')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-contador-avisos]').textContent).toContain(
        'Avisos sin leer: 0',
      );
    },
  );
  it.each([
    ['CLIENTE', false],
    ['ADMIN', false],
    ['BARBERO', false],
    ['CLIENTE', true],
    ['ADMIN', true],
    ['BARBERO', true],
  ] as [Rol, boolean][])(
    'ofrece diálogo de avisos a CLIENTE y BARBERO: %s, móvil=%s',
    (rol, esMovil) => {
      entrar(rol);
      movil.next({ matches: esMovil });
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      const botones = fixture.nativeElement.querySelectorAll('[aria-haspopup="dialog"]');
      expect(botones).toHaveLength(rol !== 'ADMIN' ? 1 : 0);
      const boton = botones[0];
      if (rol !== 'ADMIN') {
        expect(boton.querySelector('svg')).not.toBeNull();
        expect(boton.getAttribute('aria-label')).toBe(
          `${rol === 'BARBERO' ? 'Ver avisos' : 'Avisos'}: 3 sin leer`,
        );
      } else {
        expect(boton).toBeUndefined();
      }
    },
  );
  it.each(['CLIENTE', 'BARBERO'] as Rol[])(
    'evita diálogos duplicados y permite reabrir para %s',
    async (rol) => {
      const cierre = new Subject<void>();
      entrar(rol);
      const fixture = TestBed.createComponent(Shell);
      const abrirDialogo = vi
        .spyOn(fixture.debugElement.injector.get(MatDialog), 'open')
        .mockReturnValue({ afterClosed: () => cierre } as unknown as MatDialogRef<AvisosDialogo>);

      fixture.detectChanges();
      const boton = fixture.nativeElement.querySelector(
        '[aria-haspopup="dialog"]',
      ) as HTMLButtonElement;
      const apertura = vi.spyOn(fixture.componentInstance, 'abrirAvisos');
      boton.click();
      boton.click();
      // Ambas pulsaciones ocurren antes de que se resuelva la importación diferida.
      expect(abrirDialogo).not.toHaveBeenCalled();
      await Promise.all(apertura.mock.results.map((resultado) => resultado.value));
      expect(abrirDialogo).toHaveBeenCalledExactlyOnceWith(
        AvisosDialogo,
        expect.objectContaining({ autoFocus: '[mat-dialog-close]', restoreFocus: true }),
      );
      await fixture.componentInstance.abrirAvisos();
      expect(abrirDialogo).toHaveBeenCalledOnce();
      cierre.next();
      await fixture.componentInstance.abrirAvisos();
      expect(abrirDialogo).toHaveBeenCalledTimes(2);
      cierre.complete();
    },
  );
  it('no ofrece Ver avisos sin sesión', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-haspopup="dialog"]')).toBeNull();
  });
  it.each([true, false])(
    'con contraseña temporal ofrece cambiarla y Salir siempre visible: móvil=%s',
    (esMovil) => {
      entrar('BARBERO', true);
      movil.next({ matches: esMovil });
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      expect(fixture.componentInstance.enlaces()).toEqual([
        { ruta: '/cambiar-password', texto: 'Cambiar contraseña' },
      ]);
      expect(fixture.nativeElement.querySelector('header button').textContent).toContain('Salir');
      expect(fixture.nativeElement.querySelector('.barra-inferior')).toBeNull();
      expect(fixture.nativeElement.querySelector('[aria-label="Abrir cuenta"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('[aria-haspopup="dialog"]')).toBeNull();
    },
  );
  it.each([
    ['CLIENTE', ['Reservar', 'Mis citas', 'Mi cuenta']],
    ['BARBERO', ['Agenda', 'Mi cuenta']],
    ['ADMIN', []],
  ] as [Rol, string[]][])('barra inferior y cuenta móvil de %s', async (rol, enlaces) => {
    entrar(rol);
    movil.next({ matches: true });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(
      [...fixture.nativeElement.querySelectorAll('.barra-inferior a')].map((a) =>
        (a as HTMLElement).textContent?.trim(),
      ),
    ).toEqual(enlaces);
    (
      fixture.nativeElement.querySelector('[aria-label="Abrir cuenta"]') as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const menu = TestBed.inject(OverlayContainer).getContainerElement();
    expect(menu.textContent).toContain('Mi cuenta');
    expect(menu.textContent).toContain('Salir');
    expect(menu.textContent).toContain('Usuario ficticio');
  });
  it('marca la ruta activa en la barra inferior y no abre cajón para CLIENTE', async () => {
    TestBed.inject(Router).resetConfig([{ path: 'mis-citas', children: [] }]);
    entrar('CLIENTE');
    movil.next({ matches: true });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/mis-citas');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('.barra-inferior [aria-current="page"]').textContent,
    ).toContain('Mis citas');
    expect(fixture.nativeElement.querySelector('[aria-label="Abrir menú"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.titulo-pagina').textContent).toBe('Mis citas');
  });
  it('el ADMIN puede cerrar explícitamente su cajón de ocho secciones', async () => {
    entrar('ADMIN');
    movil.next({ matches: true });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    const menu = fixture.debugElement.query(By.directive(MatSidenav))
      .componentInstance as MatSidenav;
    await menu.open();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('mat-sidenav nav a')).toHaveLength(8);
    (fixture.nativeElement.querySelector('.cerrar-menu') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(menu.opened).toBe(false);
    expect(fixture.nativeElement.querySelector('[aria-haspopup="dialog"]')).toBeNull();
  });
  it('la barra adopta el título de la vista sin añadir otro encabezado', () => {
    entrar('CLIENTE');
    movil.next({ matches: true });
    const f = TestBed.createComponent(Shell);
    TestBed.inject(TituloPagina).texto.set('Reprogramar cita');
    f.detectChanges();
    const titulo = f.nativeElement.querySelector('.titulo-pagina');
    expect(titulo.textContent).toBe('Reprogramar cita');
    expect(titulo.tagName).toBe('SPAN');
    expect(titulo.hasAttribute('role')).toBe(false);
    expect(f.nativeElement.querySelector('h1')).toBeNull();
  });
  it.each([true, false])(
    'en Mis citas conserva la campana solo en móvil y vuelve al salir: móvil=%s',
    (esMovil) => {
      entrar('CLIENTE');
      movil.next({ matches: esMovil });
      const eventos = new Subject<NavigationEnd>();
      const router = TestBed.inject(Router);
      vi.spyOn(router, 'events', 'get').mockReturnValue(eventos);
      const fixture = TestBed.createComponent(Shell);
      vi.spyOn(router, 'url', 'get').mockReturnValue('/mis-citas?estado=CONFIRMADA');
      eventos.next(new NavigationEnd(1, '/mis-citas', '/mis-citas?estado=CONFIRMADA'));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('[aria-haspopup="dialog"]')).toHaveLength(
        esMovil ? 1 : 0,
      );
      vi.spyOn(router, 'url', 'get').mockReturnValue('/reservar');
      eventos.next(new NavigationEnd(2, '/reservar', '/reservar'));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('[aria-haspopup="dialog"]')).toHaveLength(1);
    },
  );
});

describe('Avisos del barbero desde la cabecera', () => {
  let http: HttpTestingController;
  const aviso = {
    id: 8,
    reservaId: 101,
    tipo: 'CREAR',
    mensaje: 'Reserva BT-101 creada',
    leida: false,
    creadoEn: '2026-10-04T10:00:00-05:00',
  };
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: BreakpointObserver,
          useValue: { observe: () => new BehaviorSubject({ matches: false }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    TestBed.inject(MatDialog).closeAll();
    http.verify();
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });
  async function abrir() {
    TestBed.inject(SesionService).cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush({
      id: 2,
      nombre: 'Carlos',
      correo: 'carlos@ejemplo.test',
      rol: 'BARBERO',
      debeCambiarPassword: false,
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    TestBed.tick();
    vi.advanceTimersByTime(0);
    http.expectOne('/api/notificaciones/conteo').flush({ noLeidas: 3 });
    fixture.detectChanges();
    const boton = fixture.nativeElement.querySelector(
      '[aria-haspopup="dialog"]',
    ) as HTMLButtonElement;
    const apertura = vi.spyOn(fixture.componentInstance, 'abrirAvisos');
    boton.focus();
    boton.click();
    await apertura.mock.results[0].value;
    TestBed.tick();
    http
      .expectOne((r) => r.url === '/api/notificaciones')
      .flush({
        contenido: [aviso],
        totalElementos: 1,
        pagina: 0,
        tamano: 10,
        totalPaginas: 1,
      });
    TestBed.tick();
    // Material registra el título en una microtarea tras crear el contenido.
    await Promise.resolve();
    TestBed.tick();
    const contenedor = TestBed.inject(OverlayContainer).getContainerElement();
    return { fixture, boton, contenedor };
  }
  it('abre el panel existente en un diálogo titulado, con cierre y restauración del foco', async () => {
    const { fixture, boton, contenedor } = await abrir();
    const referencia = fixture.debugElement.injector.get(MatDialog).openDialogs[0];
    expect(referencia.componentInstance).toBeInstanceOf(AvisosDialogo);
    expect(contenedor.querySelector('app-avisos-panel')?.textContent).toContain(aviso.mensaje);
    const dialogo = contenedor.querySelector('[role="dialog"]')!;
    const titulo = document.getElementById(dialogo.getAttribute('aria-labelledby')!);
    expect(titulo?.querySelector('span')?.textContent).toBe('Avisos');
    expect(titulo?.querySelector('.insignia')?.textContent).toBe('3 sin leer');
    expect(
      contenedor.querySelector('app-avisos-panel section')?.classList.contains('incrustado'),
    ).toBe(true);
    expect(contenedor.querySelectorAll('h2')).toHaveLength(1);
    expect(
      contenedor.querySelector('app-avisos-panel section')?.hasAttribute('aria-labelledby'),
    ).toBe(false);
    (contenedor.querySelector('[mat-dialog-close]') as HTMLButtonElement).click();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(500);
    TestBed.tick();
    expect(fixture.debugElement.injector.get(MatDialog).openDialogs).toHaveLength(0);
    expect(document.activeElement).toBe(boton);
  });
  it.each([
    ['uno', '/api/notificaciones/8/lectura', 2],
    ['todos', '/api/notificaciones/lectura', 0],
  ] as const)(
    'al marcar %s refresca la cabecera mediante el servicio actual',
    async (_, ruta, restantes) => {
      const { fixture, boton, contenedor } = await abrir();
      const selector = ruta.endsWith('/8/lectura') ? 'li button' : 'app-avisos-panel button';
      (contenedor.querySelector(selector) as HTMLButtonElement).click();
      const lectura = http.expectOne(ruta);
      expect(lectura.request.method).toBe('POST');
      lectura.flush(null, { status: 204, statusText: 'No Content' });
      http
        .expectOne((r) => r.url === '/api/notificaciones')
        .flush({
          contenido: [{ ...aviso, leida: true }],
          totalElementos: 1,
        });
      // El servicio real solicita el conteo inmediatamente; no se avanza el sondeo de 60 s.
      http.expectOne('/api/notificaciones/conteo').flush({ noLeidas: restantes });
      fixture.detectChanges();
      TestBed.tick();
      expect(fixture.nativeElement.querySelector('[data-contador-avisos]').textContent).toContain(
        `Avisos sin leer: ${restantes}`,
      );
      expect(boton.getAttribute('aria-label')).toBe(`Ver avisos: ${restantes} sin leer`);
      expect(contenedor.querySelector('app-avisos-panel')?.textContent).toContain('Leído');
    },
  );
});
