import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { RESERVA_PRUEBA } from '../../shared/reserva-prueba';
import { AuditoriaDialogo } from './auditoria-dialogo';

describe('Historial de cambios', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: RESERVA_PRUEBA },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('muestra actor, acción, anterior → nuevo, motivo y excepción con fecha de Lima', () => {
    const fixture = TestBed.createComponent(AuditoriaDialogo);
    http.expectOne('/api/reservas/101/auditoria').flush([
      {
        accion: 'REPROGRAMAR',
        actorNombre: 'Administrador ficticio',
        creadoEn: '2027-01-01T02:30:00Z',
        estadoAnterior: 'CONFIRMADA',
        estadoNuevo: 'CONFIRMADA',
        datosAnteriores: {
          inicio: '2026-12-31T22:00:00Z',
          fin: '2026-12-31T22:30:00Z',
          barberoId: 3,
        },
        datosNuevos: { inicio: '2027-01-01T23:00:00Z', fin: '2027-01-01T23:30:00Z', barberoId: 4 },
        motivo: 'Cambio excepcional ficticio',
        excepcional: true,
      },
    ]);
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent;
    for (const valor of [
      'Reprogramar',
      'Administrador ficticio',
      '31/12/2026',
      '21:30',
      '17:00',
      '18:00',
      'Confirmada → Confirmada',
      'Barbero #3',
      'Barbero #4',
      'Cambio excepcional ficticio',
      'Excepción administrativa',
    ])
      expect(texto).toContain(valor);
  });
  it('creación sin datos previos y motivo no muestra valores indefinidos', () => {
    const fixture = TestBed.createComponent(AuditoriaDialogo);
    http.expectOne('/api/reservas/101/auditoria').flush([
      {
        accion: 'CREAR',
        actorNombre: 'Cliente ficticio',
        creadoEn: RESERVA_PRUEBA.inicio,
        estadoAnterior: null,
        estadoNuevo: 'CONFIRMADA',
        datosAnteriores: null,
        datosNuevos: { inicio: RESERVA_PRUEBA.inicio, fin: RESERVA_PRUEBA.fin, barberoId: 3 },
        motivo: null,
        excepcional: false,
      },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sin reserva → Confirmada');
    expect(fixture.nativeElement.textContent).toContain('Sin motivo');
    expect(fixture.nativeElement.textContent).not.toContain('Excepción administrativa');
  });
  it('transiciones con instantáneas de solo estado no inventan horas ni barbero', () => {
    const fixture = TestBed.createComponent(AuditoriaDialogo);
    http.expectOne('/api/reservas/101/auditoria').flush([
      {
        accion: 'INICIAR',
        actorNombre: 'Barbero ficticio',
        creadoEn: RESERVA_PRUEBA.inicio,
        estadoAnterior: 'CONFIRMADA',
        estadoNuevo: 'EN_ATENCION',
        datosAnteriores: { estado: 'CONFIRMADA' },
        datosNuevos: { estado: 'EN_ATENCION' },
        motivo: null,
        excepcional: false,
      },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Confirmada → En atención');
    expect(fixture.nativeElement.querySelector('.valores').textContent).not.toContain('Barbero #');
  });
  it('maneja vacío y un fallo recuperable', () => {
    const fixture = TestBed.createComponent(AuditoriaDialogo);
    http
      .expectOne('/api/reservas/101/auditoria')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
    fixture.componentInstance.cargar();
    http.expectOne('/api/reservas/101/auditoria').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay cambios registrados');
  });
});
