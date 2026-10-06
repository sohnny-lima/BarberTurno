import { signal } from '@angular/core';
import { AvisosService } from '../core/notificaciones/avisos-service';
import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSidenav } from '@angular/material/sidenav';
import { MatDialog } from '@angular/material/dialog';
import { OverlayContainer } from '@angular/cdk/overlay';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { SesionService } from '../core/auth/sesion-service';
import { Rol } from '../core/modelos/identidad';
import { Shell } from './shell';
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
    expect(fixture.nativeElement.querySelector('mat-toolbar').textContent).toContain(
      'Usuario ficticio',
    );
    expect(fixture.nativeElement.querySelector('mat-toolbar').textContent).toContain('Salir');
    expect(fixture.nativeElement.querySelector('[data-contador-avisos]').textContent).toContain(
      'Avisos sin leer: 3',
    );
  });
  it.each(['CLIENTE', 'ADMIN', 'BARBERO'] as Rol[])(
    'ofrece Ver avisos únicamente al BARBERO: %s',
    (rol) => {
      entrar(rol);
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      const boton = fixture.nativeElement.querySelector('[aria-haspopup="dialog"]');
      if (rol === 'BARBERO') {
        expect(boton.textContent).toContain('Ver avisos');
        expect(boton.getAttribute('aria-label')).toBe('Ver avisos: 3 sin leer');
      } else {
        expect(boton).toBeNull();
      }
    },
  );
  it('no ofrece Ver avisos sin sesión', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-haspopup="dialog"]')).toBeNull();
  });
  it('con contraseña temporal solo ofrece cambiarla y salir', () => {
    entrar('BARBERO', true);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(fixture.componentInstance.enlaces()).toEqual([
      { ruta: '/cambiar-password', texto: 'Cambiar contraseña' },
    ]);
  });
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
    const { boton, contenedor } = await abrir();
    const referencia = TestBed.inject(MatDialog).openDialogs[0];
    expect(referencia.componentInstance).toBeInstanceOf(AvisosDialogo);
    expect(contenedor.querySelector('app-avisos-panel')?.textContent).toContain(aviso.mensaje);
    const dialogo = contenedor.querySelector('[role="dialog"]')!;
    const titulo = document.getElementById(dialogo.getAttribute('aria-labelledby')!);
    expect(titulo?.textContent).toBe('Avisos');
    (contenedor.querySelector('[mat-dialog-close]') as HTMLButtonElement).click();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(500);
    TestBed.tick();
    expect(TestBed.inject(MatDialog).openDialogs).toHaveLength(0);
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
