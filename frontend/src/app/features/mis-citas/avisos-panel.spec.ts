import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AvisosService } from '../../core/notificaciones/avisos-service';
import { AvisosPanel } from './avisos-panel';

describe('Panel de avisos', () => {
  let http: HttpTestingController;
  const actualizar = vi.fn();
  const avisos = [
    {
      id: 8,
      reservaId: 101,
      tipo: 'CANCELAR',
      mensaje: 'BT-101 cancelada',
      leida: false,
      creadoEn: '2026-10-04T10:30:00-05:00',
    },
    {
      id: 7,
      reservaId: 101,
      tipo: 'CREAR',
      mensaje: 'BT-101 creada',
      leida: false,
      creadoEn: '2026-10-04T10:00:00-05:00',
    },
  ];
  beforeEach(() => {
    actualizar.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AvisosService, useValue: { noLeidas: signal(2), actualizar } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function crear() {
    const fixture = TestBed.createComponent(AvisosPanel);
    fixture.detectChanges();
    const solicitud = http.expectOne((r) => r.url === '/api/notificaciones');
    expect(solicitud.request.params.get('soloNoLeidas')).toBe('false');
    solicitud.flush({
      contenido: avisos,
      totalElementos: 2,
      pagina: 0,
      tamano: 10,
      totalPaginas: 1,
    });
    fixture.detectChanges();
    return fixture;
  }
  it('mantiene el orden reciente del servidor, muestra hora de Lima y estado textual', () => {
    const fixture = crear();
    const filas = [...fixture.nativeElement.querySelectorAll('li')] as HTMLElement[];
    expect(filas[0].textContent).toContain('BT-101 cancelada');
    expect(filas[1].textContent).toContain('BT-101 creada');
    expect(filas[0].textContent).toContain('10:30');
    expect(filas[0].textContent).toContain('Nuevo');
  });
  it.each([undefined, 8])('marca lectura %s, recarga lista y contador sin duplicar', (id) => {
    const fixture = crear();
    fixture.componentInstance.marcar(id);
    fixture.componentInstance.marcar(id);
    const lectura = http.expectOne(
      id === undefined ? '/api/notificaciones/lectura' : '/api/notificaciones/8/lectura',
    );
    expect(lectura.request.method).toBe('POST');
    lectura.flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne((r) => r.url === '/api/notificaciones')
      .flush({ contenido: avisos.map((a) => ({ ...a, leida: true })), totalElementos: 2 });
    fixture.detectChanges();
    expect(actualizar).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Leído');
    expect(fixture.nativeElement.querySelectorAll('li button').length).toBe(0);
  });
  it('pagina una sola vez y actualiza al cambiar revision', () => {
    const fixture = crear();
    fixture.componentInstance.paginar({ pageIndex: 1, pageSize: 10, length: 20 });
    fixture.detectChanges();
    const pagina = http.expectOne((r) => r.url === '/api/notificaciones');
    expect(pagina.request.params.get('pagina')).toBe('1');
    pagina.flush({ contenido: [], totalElementos: 20 });
    fixture.componentRef.setInput('revision', 1);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/notificaciones')
      .flush({ contenido: avisos, totalElementos: 20 });
  });
  it('conserva el aviso y permite reintentar un error de lectura', () => {
    const fixture = crear();
    fixture.componentInstance.marcar(8);
    http
      .expectOne('/api/notificaciones/8/lectura')
      .flush({ detail: 'El aviso no existe.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('El aviso no existe.');
    expect(fixture.componentInstance.guardando()).toBe(false);
    expect(actualizar).not.toHaveBeenCalled();
  });
  it('incrusta el panel sin tarjeta y oculta el paginador cuando cabe en una página', () => {
    const fixture = crear();
    fixture.componentRef.setInput('incrustado', true);
    fixture.componentRef.setInput('mostrarEncabezado', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('section').classList.contains('incrustado')).toBe(
      true,
    );
    expect(fixture.nativeElement.querySelector('h2')).toBeNull();
    expect(fixture.nativeElement.querySelector('mat-paginator')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Actualizar');
    fixture.componentInstance.total.set(11);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-paginator')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('mat-select')).toBeNull();
  });
  it('conserva la tarjeta y el selector de tamaño en el uso de Mis citas', () => {
    const fixture = crear();
    expect(fixture.nativeElement.querySelector('section').classList.contains('incrustado')).toBe(
      false,
    );
    expect(fixture.nativeElement.querySelector('h2').textContent).toBe('Avisos');
    expect(fixture.nativeElement.querySelector('mat-select')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Actualizar');
    expect(fixture.nativeElement.querySelector('.insignia').textContent).toBe('2 sin leer');
  });
  it('el panel de escritorio incrustado conserva título, insignia y lectura alineada', () => {
    const fixture = crear();
    fixture.componentRef.setInput('incrustado', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h2').textContent).toBe('Avisos');
    expect(fixture.nativeElement.querySelector('.insignia').textContent).toBe('2 sin leer');
    expect(fixture.nativeElement.querySelector('section').getAttribute('aria-labelledby')).toBe(
      'titulo-avisos',
    );
    const lectura = fixture.nativeElement.querySelector('li button');
    expect(lectura.getAttribute('aria-label')).toBe('Marcar como leído el aviso de BT-101');
    expect(lectura.textContent).toContain('Marcar como leído');
    expect(fixture.nativeElement.querySelector('mat-paginator')).toBeNull();
  });
});
