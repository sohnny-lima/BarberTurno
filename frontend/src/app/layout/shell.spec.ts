import { MisNegociosApi } from '../core/api/mis-negocios-api';
import { of } from 'rxjs';
import { PapelDialogo } from '../features/negocios/papel-dialogo';
import { NegocioActual } from '../core/negocio/negocio-actual';
import { prepararNegocio } from '../core/negocio/negocio-prueba';
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
        {
          provide: MisNegociosApi,
          useValue: {
            listar: () =>
              of([
                {
                  slug: 'prueba',
                  nombre: 'Barbería ficticia',
                  rol: 'BARBERO',
                  papeles: ['BARBERO'],
                },
              ]),
          },
        },
        { provide: AvisosService, useValue: { noLeidas: signal(3), actualizar: vi.fn() } },
        { provide: BreakpointObserver, useValue: { observe: vi.fn(() => movil) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    prepararNegocio();
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
    TestBed.inject(NegocioActual).actualizarSesion(TestBed.inject(SesionService).usuario());
  }
  it('los menús obedecen la sesión del negocio aunque el rol global sea ADMIN', () => {
    entrar('ADMIN');
    const negocio = TestBed.inject(NegocioActual);
    negocio.actualizarSesion({ ...negocio.usuario()!, rol: 'CLIENTE' });
    const vista = TestBed.createComponent(Shell);
    vista.detectChanges();
    expect(vista.componentInstance.enlaces().map((enlace) => enlace.texto)).toEqual([
      'Reservar',
      'Mis citas',
      'Mi cuenta',
    ]);
    negocio.seleccionar(null);
    vista.detectChanges();
    expect(vista.componentInstance.enlaces().map((enlace) => enlace.texto)).toEqual([
      'Sus barberías',
      'Mi cuenta',
    ]);
  });
  it.each(['PROPIETARIO', 'ENCARGADO', 'BARBERO'] as const)(
    'gestión usa rolNegocio %s de la sesión local',
    (rolNegocio) => {
      entrar(rolNegocio === 'BARBERO' ? 'BARBERO' : 'ADMIN');
      const negocio = TestBed.inject(NegocioActual);
      negocio.actualizarSesion({ ...negocio.usuario()!, rolNegocio });
      const vista = TestBed.createComponent(Shell);
      const textos = vista.componentInstance.enlaces().map((e) => e.texto);
      expect(textos.includes('Equipo')).toBe(rolNegocio !== 'BARBERO');
      expect(textos.includes('Invitaciones')).toBe(rolNegocio !== 'BARBERO');
      expect(textos.includes('Configuración')).toBe(rolNegocio === 'PROPIETARIO');
    },
  );
  it('los enlaces conservan el papel cliente como query y lo excluyen de Mi cuenta', async () => {
    const router = TestBed.inject(Router);
    router.resetConfig([{ path: 'n/prueba/mis-citas', children: [] }]);
    entrar('CLIENTE');
    const negocio = TestBed.inject(NegocioActual);
    negocio.seleccionar('prueba', 'CLIENTE');
    negocio.cargar().subscribe();
    http.expectOne('/api/n/prueba/sesion').flush({
      ...TestBed.inject(SesionService).usuario(),
      rol: 'CLIENTE',
    });
    http.expectOne('/api/n/prueba/negocio').flush({ slug: 'prueba', nombre: 'Ficticia' });
    movil.next({ matches: true });
    const vista = TestBed.createComponent(Shell);
    vista.detectChanges();
    for (const region of ['mat-sidenav nav', '.barra-inferior']) {
      const enlaces = vista.nativeElement.querySelectorAll(
        region + ' a',
      ) as NodeListOf<HTMLAnchorElement>;
      expect(enlaces[0].getAttribute('href')).toBe('/n/prueba/reservar?papel=cliente');
      expect(enlaces[1].getAttribute('href')).toBe('/n/prueba/mis-citas?papel=cliente');
      expect(enlaces[2].getAttribute('href')).toBe('/perfil');
    }
    (
      vista.nativeElement.querySelector('.barra-inferior a:nth-child(2)') as HTMLAnchorElement
    ).click();
    await vista.whenStable();
    vista.detectChanges();
    expect(router.url).toBe('/n/prueba/mis-citas?papel=cliente');
    expect(negocio.papel()).toBe('CLIENTE');
    expect(vista.componentInstance.tituloPagina()).toBe('Mis citas');
    expect(
      vista.nativeElement.querySelector('.barra-inferior [aria-current="page"]').textContent,
    ).toContain('Mis citas');
  });
  it.each(['slug', 'papel'] as const)(
    'cierra también los diálogos con disableClose al cambiar %s',
    async (cambio) => {
      entrar('ADMIN');
      const vista = TestBed.createComponent(Shell);
      vista.detectChanges();
      const dialogos = vista.debugElement.injector.get(MatDialog);
      dialogos.open(PapelDialogo, {
        disableClose: true,
        data: {
          slug: 'prueba',
          nombre: 'Ficticia',
          rol: 'PROPIETARIO',
          papeles: ['PROPIETARIO', 'CLIENTE'],
        },
      });
      expect(dialogos.openDialogs.length).toBe(1);
      TestBed.inject(NegocioActual).seleccionar(
        cambio === 'slug' ? 'sur' : 'prueba',
        cambio === 'papel' ? 'CLIENTE' : 'PERSONAL',
      );
      await vi.waitFor(() => expect(dialogos.openDialogs.length).toBe(0));
    },
  );
  it('conserva el foco programático sin contorno solo en main y el foco visible de controles y salto', () => {
    const reglaGlobal = document.createElement('style');
    // jsdom no reevalúa :focus-visible al cambiar entre controles; aislar la cascada del contorno.
    reglaGlobal.textContent =
      'main, .saltar, button { outline: 3px solid #145c43; outline-offset: 2px; }';
    document.head.prepend(reglaGlobal);
    try {
      entrar('CLIENTE');
      const f = TestBed.createComponent(Shell);
      f.detectChanges();
      const main = f.nativeElement.querySelector('main#contenido') as HTMLElement;
      expect(main.getAttribute('tabindex')).toBe('-1');
      main.focus();
      expect(document.activeElement).toBe(main);
      expect(getComputedStyle(main).outline).toBe('none');
      const boton = document.createElement('button');
      boton.textContent = 'Control de prueba';
      main.append(boton);
      for (const control of [f.nativeElement.querySelector('.saltar') as HTMLElement, boton]) {
        control.focus();
        expect(document.activeElement).toBe(control);
        expect(getComputedStyle(control).outline).toContain('3px');
      }
    } finally {
      reglaGlobal.remove();
    }
  });
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
  it('muestra la actualización de Agenda solo en la barra móvil y respeta su estado desactivado', () => {
    entrar('BARBERO');
    const ejecutar = vi.fn();
    const desactivada = signal(false);
    const titulo = TestBed.inject(TituloPagina);
    titulo.actualizarAgenda.set({ ejecutar, desactivada });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Actualizar agenda"]')).toBeNull();
    movil.next({ matches: true });
    fixture.detectChanges();
    const actualizar = fixture.nativeElement.querySelector(
      '[aria-label="Actualizar agenda"]',
    ) as HTMLButtonElement;
    actualizar.click();
    expect(ejecutar).toHaveBeenCalledOnce();
    desactivada.set(true);
    fixture.detectChanges();
    expect(actualizar.disabled).toBe(true);
    titulo.actualizarAgenda.set(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Actualizar agenda"]')).toBeNull();
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
  it('muestra el nombre completo accesible de la barbería también al visitante móvil', () => {
    movil.next({ matches: true });
    const vista = TestBed.createComponent(Shell);
    vista.detectChanges();
    const marca = vista.nativeElement.querySelector('.cabecera .titulo-marca') as HTMLElement;
    expect(marca.title).toBe('Barbería ficticia');
    expect(marca.querySelector('.nombre-barberia')?.textContent).toBe('Barbería ficticia');
    expect(vista.nativeElement.querySelector('[aria-label="Abrir cuenta"]')).toBeNull();
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
    TestBed.inject(Router).resetConfig([{ path: 'n/prueba/mis-citas', children: [] }]);
    entrar('CLIENTE');
    movil.next({ matches: true });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/n/prueba/mis-citas');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('.barra-inferior [aria-current="page"]').textContent,
    ).toContain('Mis citas');
    expect(fixture.nativeElement.querySelector('[aria-label="Abrir menú"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.titulo-pagina').textContent).toBe(
      'Mis citas Barbería ficticia',
    );
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
    expect(titulo.textContent).toBe('Reprogramar cita Barbería ficticia');
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
      vi.spyOn(router, 'url', 'get').mockReturnValue('/n/prueba/mis-citas?estado=CONFIRMADA');
      eventos.next(
        new NavigationEnd(1, '/n/prueba/mis-citas', '/n/prueba/mis-citas?estado=CONFIRMADA'),
      );
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('[aria-haspopup="dialog"]')).toHaveLength(
        esMovil ? 1 : 0,
      );
      vi.spyOn(router, 'url', 'get').mockReturnValue('/n/prueba/reservar');
      eventos.next(new NavigationEnd(2, '/n/prueba/reservar', '/n/prueba/reservar'));
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
          provide: MisNegociosApi,
          useValue: {
            listar: () =>
              of([
                {
                  slug: 'prueba',
                  nombre: 'Barbería ficticia',
                  rol: 'BARBERO',
                  papeles: ['BARBERO'],
                },
              ]),
          },
        },
        {
          provide: BreakpointObserver,
          useValue: { observe: () => new BehaviorSubject({ matches: false }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    prepararNegocio();
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
    TestBed.inject(NegocioActual).actualizarSesion(TestBed.inject(SesionService).usuario());
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    TestBed.tick();
    vi.advanceTimersByTime(0);
    http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: 3 });
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
      .expectOne((r) => r.url === '/api/n/prueba/notificaciones')
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
    ['uno', '/api/n/prueba/notificaciones/8/lectura', 2],
    ['todos', '/api/n/prueba/notificaciones/lectura', 0],
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
        .expectOne((r) => r.url === '/api/n/prueba/notificaciones')
        .flush({
          contenido: [{ ...aviso, leida: true }],
          totalElementos: 1,
        });
      // El servicio real solicita el conteo inmediatamente; no se avanza el sondeo de 60 s.
      http.expectOne('/api/n/prueba/notificaciones/conteo').flush({ noLeidas: restantes });
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
