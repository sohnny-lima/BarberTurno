import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormGroupDirective } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { SesionService } from '../../core/auth/sesion-service';
import { EquipoDto } from '../../core/modelos/gestion';
import { RolPersonal } from '../../core/modelos/negocios';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { prepararNegocio } from '../../core/negocio/negocio-prueba';
import { ConfirmarEstadoDatos } from '../../shared/confirmar-estado-dialogo';
import { Aceptacion } from './aceptacion';
import { Configuracion } from './configuracion';
import { Equipo } from './equipo';
import { Invitaciones } from './invitaciones';

const miembro: EquipoDto = {
  id: 3,
  usuarioId: 8,
  nombre: 'Persona ficticia',
  correo: 'persona@ejemplo.test',
  rol: 'BARBERO',
  estado: 'ACTIVA',
  motivoRevocacion: null,
};
const configuracion = {
  nombre: 'Barbería ficticia',
  horizonteDias: 18,
  anticipacionCambioClienteMin: 10,
  limiteReservasActivas: 4,
  limiteBarberosActivos: 6,
  toleranciaInicioMin: 0,
  confirmacionManual: true,
};
const pendiente = {
  id: 4,
  correo: 'invitado@ejemplo.test',
  rol: 'BARBERO',
  expiraEn: '2026-10-01T10:00:00-05:00',
};

describe('Gestión del negocio', () => {
  let http: HttpTestingController;
  const dialogos = { open: vi.fn() };
  let cierre: Subject<boolean>;
  beforeEach(() => {
    cierre = new Subject();
    dialogos.open.mockReset().mockReturnValue({ afterClosed: () => cierre });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches: true }) } },
        { provide: MatDialog, useValue: dialogos },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    prepararNegocio('ADMIN');
  });
  afterEach(() => http.verify());
  function rol(rolNegocio: RolPersonal) {
    const negocio = TestBed.inject(NegocioActual);
    negocio.actualizarSesion({ ...negocio.usuario()!, rolNegocio });
  }
  it.each(['PROPIETARIO', 'ENCARGADO'] as const)(
    'equipo presenta las acciones de %s y oculta correo revocado',
    (actor) => {
      rol(actor);
      const f = TestBed.createComponent(Equipo);
      http.expectOne('/api/n/prueba/equipo').flush([
        miembro,
        { ...miembro, id: 5, rol: 'ENCARGADO' },
        {
          ...miembro,
          id: 6,
          estado: 'REVOCADA',
          correo: 'no-mostrar@ejemplo.test',
          motivoRevocacion: 'SALIDA',
        },
      ]);
      f.detectChanges();
      expect(f.nativeElement.textContent).toContain(miembro.correo);
      expect(f.nativeElement.querySelectorAll('button[aria-label^="Cambiar rol"]').length).toBe(
        actor === 'PROPIETARIO' ? 2 : 0,
      );
      expect(f.nativeElement.querySelectorAll('button[aria-label^="Revocar acceso"]').length).toBe(
        actor === 'PROPIETARIO' ? 2 : 1,
      );
      f.componentInstance.revocadas.set(true);
      f.detectChanges();
      expect(f.nativeElement.textContent).toContain('Salió del negocio');
      expect(f.nativeElement.textContent).not.toContain('no-mostrar@ejemplo.test');
    },
  );
  it('salida y revocación usan confirmación de riesgo; salir vuelve al selector', () => {
    rol('PROPIETARIO');
    const f = TestBed.createComponent(Equipo);
    const propia = { ...miembro, usuarioId: 9 };
    http.expectOne('/api/n/prueba/equipo').flush([propia]);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    f.componentInstance.salir(propia);
    const datos = dialogos.open.mock.calls[0][1].data as ConfirmarEstadoDatos;
    expect(datos.riesgo).toBe(true);
    datos.cambiar().subscribe();
    const salida = http.expectOne('/api/n/prueba/equipo/salida');
    expect(salida.request.method).toBe('POST');
    salida.flush(null);
    // El éxito de la confirmación hace visible el aviso incluso si queda ficha cliente.
    cierre.next(true);
    expect(navegar).toHaveBeenCalledWith(['/negocios']);
    expect(TestBed.inject(NegocioActual).avisoSelector()).toBe(
      'Ya no tiene acceso a Barbería ficticia.',
    );
  });
  it.each([403, 404, 409])('equipo conserva mensaje del servidor %s', (status) => {
    const f = TestBed.createComponent(Equipo);
    http
      .expectOne('/api/n/prueba/equipo')
      .flush({ detail: 'Mensaje del servidor.' }, { status, statusText: 'Error' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Mensaje del servidor.');
  });
  it('cambiar el rol propio navega y deja que la guarda recargue sin destruir la petición', () => {
    rol('PROPIETARIO');
    const propia = { ...miembro, usuarioId: 9, rol: 'PROPIETARIO' as const };
    const f = TestBed.createComponent(Equipo);
    http.expectOne('/api/n/prueba/equipo').flush([propia]);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    f.componentInstance.cambiarRol(propia, 'BARBERO');
    const datos = dialogos.open.mock.calls[0][1].data as ConfirmarEstadoDatos;
    datos.cambiar().subscribe();
    const req = http.expectOne('/api/n/prueba/equipo/3/rol');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ rol: 'BARBERO' });
    req.flush(null);
    cierre.next(true);
    expect(navegar).toHaveBeenCalledWith('/n/prueba/agenda');
    http.expectNone('/api/n/prueba/sesion');
    expect(TestBed.inject(NegocioActual).estado()?.tipo).toBe('disponible');
  });
  it.each(['PROPIETARIO', 'ENCARGADO'] as const)(
    'ofrece solo roles invitables por %s; el enlace no se recupera al recrear',
    (actor) => {
      rol(actor);
      const f = TestBed.createComponent(Invitaciones);
      http.expectOne('/api/n/prueba/invitaciones').flush([]);
      f.detectChanges();
      expect(f.componentInstance.roles()).toEqual(
        actor === 'PROPIETARIO' ? ['PROPIETARIO', 'ENCARGADO', 'BARBERO'] : ['BARBERO'],
      );
      f.componentInstance.formulario.setValue({ correo: pendiente.correo, rol: 'BARBERO' });
      f.nativeElement
        .querySelector('form')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      http
        .expectOne('/api/n/prueba/invitaciones')
        .flush({ invitacion: pendiente, enlaceInvitacion: '/invitacion/capacidad-ficticia' });
      http.expectOne('/api/n/prueba/invitaciones').flush([pendiente]);
      f.detectChanges();
      expect(
        f.debugElement.query(By.directive(FormGroupDirective)).injector.get(FormGroupDirective)
          .submitted,
      ).toBe(false);
      expect(f.nativeElement.textContent).toContain('se muestra una sola vez');
      expect(f.nativeElement.textContent).toContain('hora de Lima');
      f.destroy();
      const nueva = TestBed.createComponent(Invitaciones);
      http.expectOne('/api/n/prueba/invitaciones').flush([pendiente]);
      nueva.detectChanges();
      expect(nueva.nativeElement.querySelector('app-enlace-invitacion')).toBeNull();
    },
  );
  it('un rol forzado se envía al servidor y presenta su rechazo sin simular autorización', () => {
    rol('ENCARGADO');
    const f = TestBed.createComponent(Invitaciones);
    http.expectOne('/api/n/prueba/invitaciones').flush([]);
    f.componentInstance.formulario.setValue({ correo: pendiente.correo, rol: 'PROPIETARIO' });
    f.componentInstance.crear();
    http
      .expectOne('/api/n/prueba/invitaciones')
      .flush({ detail: 'No puede invitar ese rol.' }, { status: 403, statusText: 'Forbidden' });
    expect(f.componentInstance.mensaje()).toBe('No puede invitar ese rol.');
  });
  it('configuración usa datos reales, límites técnicos y muestra el 409 del cupo', () => {
    const f = TestBed.createComponent(Configuracion);
    http.expectOne('/api/n/prueba/configuracion').flush(configuracion);
    f.detectChanges();
    expect(f.componentInstance.formulario.getRawValue()).toEqual(configuracion);
    expect(f.nativeElement.textContent).toContain('Límite técnico: Entre 1 y 32767.');
    expect(f.nativeElement.textContent).toContain('Límite técnico: Entre 0 y 32767.');
    expect(f.nativeElement.textContent).toContain('Los cambia la plataforma.');
    for (const valor of [-1, 32768, 1.5]) {
      f.componentInstance.formulario.controls.horizonteDias.setValue(valor);
      expect(f.componentInstance.formulario.invalid).toBe(true);
    }
    f.componentInstance.formulario.reset(configuracion);
    f.componentInstance.guardar();
    const req = http.expectOne('/api/n/prueba/configuracion');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).not.toHaveProperty('slug');
    req.flush(
      { detail: 'El cupo no puede ser menor a los perfiles activos.' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(f.componentInstance.mensaje()).toContain('El cupo');
    f.detectChanges();
    expect(f.nativeElement.querySelector('form [role="alert"]')?.textContent).toContain('El cupo');
    expect(f.nativeElement.querySelectorAll('[role="alert"]')).toHaveLength(1);
  });
  it('con la barbería suspendida, formularios y acciones de escritura quedan en solo lectura', () => {
    rol('PROPIETARIO');
    TestBed.inject(NegocioActual).marcarSuspendido();
    const configuracionVista = TestBed.createComponent(Configuracion);
    http.expectOne('/api/n/prueba/configuracion').flush(configuracion);
    configuracionVista.detectChanges();
    // Los valores se siguen consultando; solo se desactiva la edición.
    expect(configuracionVista.componentInstance.formulario.getRawValue()).toEqual(configuracion);
    expect(configuracionVista.componentInstance.formulario.disabled).toBe(true);
    expect(configuracionVista.nativeElement.querySelector('button[type=submit]').disabled).toBe(
      true,
    );
    const equipo = TestBed.createComponent(Equipo);
    http.expectOne('/api/n/prueba/equipo').flush([miembro]);
    equipo.detectChanges();
    const botones = [...equipo.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    expect(botones.filter((b) => /Cambiar rol|Revocar/.test(b.textContent!))).toHaveLength(2);
    for (const b of botones.filter((b) => /Cambiar rol|Revocar/.test(b.textContent!)))
      expect(b.disabled).toBe(true);
    expect(equipo.nativeElement.querySelector('mat-slide-toggle button').disabled).toBe(false);
    const invitaciones = TestBed.createComponent(Invitaciones);
    http.expectOne('/api/n/prueba/invitaciones').flush([pendiente]);
    invitaciones.detectChanges();
    expect(invitaciones.componentInstance.formulario.disabled).toBe(true);
    expect(invitaciones.nativeElement.querySelector('button[type=submit]').disabled).toBe(true);
    expect(
      invitaciones.nativeElement.querySelector("button[aria-label^='Revocar invitación']").disabled,
    ).toBe(true);
  });
});

describe('Aceptación global de invitaciones', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: new Map([['token', 'capacidad-ficticia']]) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const datos = {
    negocio: 'Barbería ficticia',
    rol: 'BARBERO',
    correoEnmascarado: 'i***@e***.test',
    estado: 'PENDIENTE',
  };
  it.each([
    ['CADUCADA', 'Esta invitación caducó.'],
    ['REVOCADA', 'Esta invitación ya no es válida.'],
    ['USADA', 'Esta invitación ya se usó.'],
  ])('presenta estado %s sin botón de aceptación', (estado, mensaje) => {
    const f = TestBed.createComponent(Aceptacion);
    http.expectOne('/api/invitaciones/capacidad-ficticia').flush({ ...datos, estado });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain(mensaje);
    expect(f.nativeElement.textContent).not.toContain('Aceptar invitación');
  });
  it('registro usa correo fijo enmascarado sin campo editable ni correo en el cuerpo', () => {
    const f = TestBed.createComponent(Aceptacion);
    http.expectOne('/api/invitaciones/capacidad-ficticia').flush(datos);
    f.componentInstance.registro.set(true);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain(datos.correoEnmascarado);
    expect(f.nativeElement.querySelector('input[type="email"]')).toBeNull();
    f.componentInstance.formulario.setValue({
      nombre: 'Invitado ficticio',
      telefono: '999000008',
      password: 'Ficticia123',
      confirmacion: 'Ficticia123',
      aceptaPrivacidad: true,
    });
    f.componentInstance.crearCuenta();
    const req = http.expectOne('/api/invitaciones/capacidad-ficticia/registro');
    expect(Object.keys(req.request.body).sort()).toEqual([
      'aceptaPrivacidad',
      'nombre',
      'password',
      'telefono',
    ]);
    req.flush(
      { detail: 'Ya existe una cuenta con ese correo.', codigo: 'CORREO_DUPLICADO' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(f.componentInstance.mensaje()).toContain('Ya existe');
    expect(f.componentInstance.formulario.controls.password.value).toBe('');
  });
  it('otra cuenta tiene texto propio; aceptación correcta entra en la relación autorizada', () => {
    TestBed.inject(SesionService).cargar().subscribe();
    http.expectOne('/api/auth/sesion').flush({ id: 2, rol: null, debeCambiarPassword: false });
    const f = TestBed.createComponent(Aceptacion);
    http.expectOne('/api/invitaciones/capacidad-ficticia').flush(datos);
    f.componentInstance.aceptar();
    http
      .expectOne('/api/invitaciones/capacidad-ficticia/aceptacion')
      .flush({ codigo: 'INVITACION_OTRA_CUENTA' }, { status: 403, statusText: 'Forbidden' });
    expect(f.componentInstance.aviso()).toBe(
      'Esta invitación es para otra cuenta. Ingrese con el correo invitado.',
    );
    f.componentInstance.aceptar();
    http.expectOne('/api/invitaciones/capacidad-ficticia/aceptacion').flush(null);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    http
      .expectOne('/api/mis-negocios')
      .flush([{ slug: 'prueba', nombre: datos.negocio, rol: 'BARBERO', papeles: ['BARBERO'] }]);
    expect(navegar).toHaveBeenCalledWith('/n/prueba/agenda');
  });
});
