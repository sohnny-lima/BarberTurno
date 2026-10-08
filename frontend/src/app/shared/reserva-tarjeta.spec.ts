import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RESERVA_PRUEBA } from './reserva-prueba';
import { ReservaTarjeta } from './reserva-tarjeta';

registerLocaleData(localeEsPe);

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
    expect(texto).toMatch(/S[/]\s25[.]00/);
    expect(texto).not.toMatch(/S[/]\s{2,}/);
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
  it('presenta fecha compacta en minúsculas y horario con profesional', () => {
    const fixture = TestBed.createComponent(ReservaTarjeta);
    fixture.componentRef.setInput('reserva', {
      ...RESERVA_PRUEBA,
      inicio: '2026-10-01T15:00:00Z',
      fin: '2026-10-01T15:30:00Z',
    });
    fixture.detectChanges();
    const fecha = fixture.nativeElement.querySelector('.bloque-fecha');
    expect(fecha.textContent).toMatch(/jue\s*1\s*oct/);
    expect(fixture.nativeElement.querySelector('.horario').textContent).toContain(
      '10:00 a 10:30 con Profesional ficticio',
    );
    expect(fixture.nativeElement.querySelector('article').getAttribute('aria-label')).toBe(
      'Cita BT-101',
    );
  });
  it('el tique conserva estado, referencias y fin con una hora destacada', () => {
    const fixture = TestBed.createComponent(ReservaTarjeta);
    fixture.componentRef.setInput('reserva', RESERVA_PRUEBA);
    fixture.componentRef.setInput('tique', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tique-hora').textContent).toBe('10:00');
    expect(fixture.nativeElement.querySelector('.tique-banda').textContent).toContain(
      'hasta las 10:30',
    );
    expect(fixture.nativeElement.querySelector('h2').textContent).toMatch(
      /Corte clásico\s+con Profesional ficticio/,
    );
    expect(fixture.nativeElement.textContent).toContain('Confirmada');
    expect(fixture.nativeElement.querySelector('.referencias').textContent).toMatch(
      /25\.00,\s*30 min,\s*BT-101/,
    );
  });
});
