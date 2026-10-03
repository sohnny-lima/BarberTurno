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
});
