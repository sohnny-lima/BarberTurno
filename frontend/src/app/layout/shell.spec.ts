import { signal } from '@angular/core';
import { AvisosService } from '../core/notificaciones/avisos-service';
import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSidenav } from '@angular/material/sidenav';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { SesionService } from '../core/auth/sesion-service';
import { Rol } from '../core/modelos/identidad';
import { Shell } from './shell';

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
    ['ADMIN', ['Agenda', 'Servicios', 'Barberos', 'Horarios', 'Reportes', 'Usuarios', 'Mi cuenta']],
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
  it('con contraseña temporal solo ofrece cambiarla y salir', () => {
    entrar('BARBERO', true);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(fixture.componentInstance.enlaces()).toEqual([
      { ruta: '/cambiar-password', texto: 'Cambiar contraseña' },
    ]);
  });
});
