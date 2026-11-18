import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ConfirmarEstadoDatos } from '../../shared/confirmar-estado-dialogo';
import { AltaNegocio } from './alta-negocio';
import { Plataforma } from './plataforma';

const norte = {
  id: 1,
  slug: 'norte',
  nombre: 'Barbería Norte',
  estado: 'ACTIVO' as const,
  miembrosActivos: 3,
};
const sur = {
  id: 2,
  slug: 'sur',
  nombre: 'Barbería Sur',
  estado: 'SUSPENDIDO' as const,
  miembrosActivos: 1,
};
describe('Plataforma', () => {
  let http: HttpTestingController;
  let cierre: Subject<unknown>;
  const dialogos = { open: vi.fn() };
  beforeEach(() => {
    cierre = new Subject();
    dialogos.open.mockReset().mockReturnValue({ afterClosed: () => cierre });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatDialog, useValue: dialogos },
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches: true }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function preparar() {
    const f = TestBed.createComponent(Plataforma);
    http.expectOne('/api/plataforma/negocios').flush([norte, sur]);
    f.detectChanges();
    return f;
  }
  it('lista estado, slug y miembros, sin menús operativos ni recuperación de claves', () => {
    const f = preparar();
    expect(f.nativeElement.textContent).toContain('Barbería Norte');
    expect(f.nativeElement.textContent).toContain('/n/sur');
    expect(f.nativeElement.textContent).toContain('Miembros activos');
    expect(f.nativeElement.textContent).toContain('Suspendido');
    expect(f.nativeElement.textContent).not.toContain('Restablecer');
  });
  it.each([norte, sur])(
    'confirma el cambio de estado de $nombre y reconsulta el inventario',
    (fila) => {
      const f = preparar();
      f.componentInstance.cambiarEstado(fila);
      f.componentInstance.cambiarEstado(fila);
      expect(dialogos.open).toHaveBeenCalledTimes(1);
      const datos = dialogos.open.mock.calls[0][1].data as ConfirmarEstadoDatos;
      expect(datos.riesgo).toBe(fila.estado === 'ACTIVO');
      datos.cambiar().subscribe();
      const req = http.expectOne(`/api/plataforma/negocios/${fila.id}/estado`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body.estado).toBe(fila.estado === 'ACTIVO' ? 'SUSPENDIDO' : 'ACTIVO');
      req.flush(null);
      cierre.next(true);
      cierre.complete();
      http.expectOne('/api/plataforma/negocios').flush([norte, sur]);
      expect(f.componentInstance.operando()).toBe(false);
    },
  );
  it('alta muestra una vez el enlace, descartado al destruir y recrear la vista', () => {
    const f = preparar();
    f.componentInstance.crear();
    expect(dialogos.open.mock.calls[0][0]).toBe(AltaNegocio);
    cierre.next({ ...norte, enlaceInvitacion: '/api/invitaciones/capacidad-ficticia' });
    cierre.complete();
    http.expectOne('/api/plataforma/negocios').flush([norte, sur]);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('/invitacion/capacidad-ficticia');
    expect(f.nativeElement.textContent).toContain('una sola vez');
    f.destroy();
    const otra = preparar();
    expect(otra.nativeElement.querySelector('app-enlace-invitacion')).toBeNull();
  });
  it('reenviar entrega la caducidad y revocar invalida el enlace local', () => {
    const f = preparar();
    f.componentInstance.reenviar(norte);
    let datos = dialogos.open.mock.calls[0][1].data as ConfirmarEstadoDatos;
    expect(datos.riesgo).toBe(true);
    datos.cambiar().subscribe();
    http.expectOne('/api/plataforma/negocios/1/invitacion-propietario').flush({
      enlaceInvitacion: '/api/invitaciones/nueva-ficticia',
      invitacion: { expiraEn: '2026-10-05T14:00:00Z' },
    });
    cierre.next(true);
    cierre.complete();
    http.expectOne('/api/plataforma/negocios').flush([norte]);
    expect(f.componentInstance.enlace()?.expiraEn).toBe('2026-10-05T14:00:00Z');
    cierre = new Subject();
    dialogos.open.mockReturnValue({ afterClosed: () => cierre });
    f.componentInstance.revocar(norte);
    expect(f.componentInstance.enlace()).toBeNull();
    datos = dialogos.open.mock.calls[1][1].data as ConfirmarEstadoDatos;
    datos.cambiar().subscribe();
    const req = http.expectOne('/api/plataforma/negocios/1/invitacion-propietario');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    cierre.next(true);
    http.expectOne('/api/plataforma/negocios').flush([norte]);
  });
  it.each([403, 404, 409])('presenta el mensaje del servidor %s', (status) => {
    const f = TestBed.createComponent(Plataforma);
    http
      .expectOne('/api/plataforma/negocios')
      .flush({ detail: 'Mensaje de plataforma.' }, { status, statusText: 'Error' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Mensaje de plataforma.');
  });
});

describe('Alta de barbería', () => {
  let http: HttpTestingController;
  const referencia = { close: vi.fn(), disableClose: false };
  beforeEach(() => {
    referencia.close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: referencia },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('valida la dirección técnica y muestra /n/; rechaza campos incompletos sin HTTP', () => {
    const f = TestBed.createComponent(AltaNegocio);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('/n/');
    for (const slug of ['Mayuscula', 'dos--guiones', '-inicio', 'final-', 'a'.repeat(41)]) {
      f.componentInstance.formulario.controls.slug.setValue(slug);
      expect(f.componentInstance.formulario.controls.slug.invalid).toBe(true);
    }
    f.componentInstance.crear();
    http.expectNone('/api/plataforma/negocios');
  });
  it('envía el contrato exacto, evita doble envío y deja el 409 en el diálogo', () => {
    const f = TestBed.createComponent(AltaNegocio);
    const datos = {
      nombre: 'Ficticia',
      slug: 'ficticia',
      correoPropietario: 'propietario@ejemplo.test',
    };
    f.componentInstance.formulario.setValue(datos);
    f.componentInstance.crear();
    f.componentInstance.crear();
    const req = http.expectOne('/api/plataforma/negocios');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(datos);
    req.flush({ detail: 'Esa dirección web ya existe.' }, { status: 409, statusText: 'Conflict' });
    expect(f.componentInstance.mensaje()).toBe('Esa dirección web ya existe.');
    expect(referencia.close).not.toHaveBeenCalled();
    f.componentInstance.crear();
    http
      .expectOne('/api/plataforma/negocios')
      .flush({ ...norte, enlaceInvitacion: '/api/invitaciones/ficticia' });
    expect(referencia.close).toHaveBeenCalledOnce();
  });
});
