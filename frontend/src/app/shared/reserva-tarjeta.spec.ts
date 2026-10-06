import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RESERVA_PRUEBA } from './reserva-prueba';
import { ReservaTarjeta } from './reserva-tarjeta';

describe('Tarjeta de reserva', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));
  it.each([
    [true, true],
    [true, false],
    [false, true],
    [false, false],
  ])(
    'respeta permisos reprogramar=%s cancelar=%s, incluso cerca del inicio',
    (reprogramar, cancelar) => {
      const fixture = TestBed.createComponent(ReservaTarjeta);
      fixture.componentRef.setInput('reserva', {
        ...RESERVA_PRUEBA,
        permisos: { reprogramar, cancelar, transiciones: [] },
      });
      fixture.componentRef.setInput('ahora', Date.parse('2026-10-05T09:59:00-05:00'));
      fixture.detectChanges();
      expect(!!fixture.nativeElement.querySelector('a')).toBe(reprogramar);
      expect(!!fixture.nativeElement.querySelector('button')).toBe(cancelar);
      if (reprogramar)
        expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe(
          '/reservar?reprogramar=101',
        );
      expect(fixture.nativeElement.textContent.includes('Faltan menos de 2 horas')).toBe(
        !reprogramar && !cancelar,
      );
    },
  );
  it('presenta referencias, estado legible e intervalo en Lima', () => {
    const fixture = TestBed.createComponent(ReservaTarjeta);
    fixture.componentRef.setInput('reserva', RESERVA_PRUEBA);
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent;
    for (const valor of [
      'BT-101',
      'Corte clásico',
      'Profesional ficticio',
      '10:00',
      '10:30',
      '25.00',
      'Confirmada',
    ])
      expect(texto).toContain(valor);
  });
  it.each(['COMPLETADA', 'CANCELADA', 'NO_ASISTIO', 'EN_ATENCION'])(
    'no explica el plazo para %s',
    (estado) => {
      const fixture = TestBed.createComponent(ReservaTarjeta);
      fixture.componentRef.setInput('reserva', {
        ...RESERVA_PRUEBA,
        estado,
        permisos: { reprogramar: false, cancelar: false, transiciones: [] },
      });
      fixture.componentRef.setInput('ahora', 0);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).not.toContain('Faltan menos de 2 horas');
    },
  );
  it('no muestra la explicación para una cita pasada y emite la reserva al cancelar', () => {
    const fixture = TestBed.createComponent(ReservaTarjeta);
    fixture.componentRef.setInput('reserva', RESERVA_PRUEBA);
    fixture.componentRef.setInput('ahora', Date.parse('2026-10-06T00:00:00-05:00'));
    const emitir = vi.fn();
    fixture.componentInstance.cancelar.subscribe(emitir);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('button').click();
    expect(emitir).toHaveBeenCalledWith(RESERVA_PRUEBA);
    fixture.componentRef.setInput('reserva', {
      ...RESERVA_PRUEBA,
      permisos: { reprogramar: false, cancelar: false, transiciones: [] },
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Faltan menos de 2 horas');
  });
});
