import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { PasswordTemporalDialogo } from './password-temporal-dialogo';

describe('Entrega de contraseña temporal', () => {
  const copiar = vi.fn();
  beforeEach(() => {
    copiar.mockReset();
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: copiar },
    });
    TestBed.configureTestingModule({
      imports: [PasswordTemporalDialogo],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: 'Ficticia123' }],
    });
  });
  afterEach(() => Reflect.deleteProperty(navigator, 'clipboard'));
  it('muestra el aviso presencial y copia solo al pulsar el botón', async () => {
    copiar.mockResolvedValue(undefined);
    const fixture = TestBed.createComponent(PasswordTemporalDialogo);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Comuníquela en persona');
    expect(copiar).not.toHaveBeenCalled();
    await fixture.componentInstance.copiar();
    fixture.detectChanges();
    expect(copiar).toHaveBeenCalledWith('Ficticia123');
    expect(fixture.nativeElement.textContent).toContain('Contraseña copiada.');
  });
  it('informa un fallo del portapapeles y permite comunicarla desde el diálogo', async () => {
    copiar.mockRejectedValue(new Error('Portapapeles no disponible'));
    const fixture = TestBed.createComponent(PasswordTemporalDialogo);
    await fixture.componentInstance.copiar();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No pudimos copiarla');
  });
  it('agrupa contraseña y Copiar, conserva el aviso y solo ofrece Cerrar como salida', () => {
    const f = TestBed.createComponent(PasswordTemporalDialogo);
    f.detectChanges();
    const raiz: HTMLElement = f.nativeElement;
    expect(raiz.querySelector('.credencial .password')?.textContent).toBe('Ficticia123');
    expect(raiz.querySelector('.credencial button')?.textContent?.trim()).toBe('Copiar');
    expect(raiz.querySelector('.aviso')?.textContent).toContain('Deberá cambiarla al ingresar.');
    const salidas = raiz.querySelectorAll('[mat-dialog-close]');
    expect(salidas).toHaveLength(1);
    expect(salidas[0].textContent?.trim()).toBe('Cerrar');
    expect(raiz.querySelectorAll('button')).toHaveLength(2);
  });
});
