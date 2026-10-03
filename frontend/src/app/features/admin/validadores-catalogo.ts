import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { ServicioDto } from '../../core/modelos/catalogo';

// Espejos para orientar el formulario; el servidor decide la validez de cada operación.
export const nombreSinEspacios = Validators.pattern(/^[^\s\p{Z}]+(?: [^\s\p{Z}]+)*$/u);
export const multiploDeDiez: ValidatorFn = (control) =>
  control.value == null || control.value === '' || Number(control.value) % 10 === 0
    ? null
    : { multiploDeDiez: true };
export const dosDecimales: ValidatorFn = (control) =>
  control.value == null || control.value === '' || /^\d+(?:\.\d{1,2})?$/.test(String(control.value))
    ? null
    : { dosDecimales: true };
export const passwordPersonal: ValidatorFn = (control) => {
  const valor = String(control.value ?? '');
  return Array.from(valor).length >= 8 &&
    Array.from(valor).length <= 72 &&
    new TextEncoder().encode(valor).length <= 72 &&
    /\p{L}/u.test(valor) &&
    /\p{Nd}/u.test(valor)
    ? null
    : { passwordPersonal: true };
};

export function formularioServicio(servicio?: ServicioDto | null) {
  return new FormGroup({
    nombre: new FormControl(servicio?.nombre ?? '', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(2),
        Validators.maxLength(80),
        nombreSinEspacios,
      ],
    }),
    descripcion: new FormControl(servicio?.descripcion ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(300)],
    }),
    duracionMin: new FormControl(servicio?.duracionMin ?? 30, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(10), Validators.max(180), multiploDeDiez],
    }),
    precio: new FormControl(servicio?.precio ?? 0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0), Validators.max(999999.99), dosDecimales],
    }),
  });
}

// Muestreo por rechazo para evitar sesgo; nunca guarda ni registra la contraseña.
export function generarPasswordTemporal(): string {
  const letras = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  const digitos = '23456789';
  const alfabeto = letras + digitos + '-!#';
  function indice(limite: number): number {
    const byte = new Uint8Array(1);
    const techo = Math.floor(256 / limite) * limite;
    do {
      crypto.getRandomValues(byte);
    } while (byte[0] >= techo);
    return byte[0] % limite;
  }
  const caracteres = [letras[indice(letras.length)], digitos[indice(digitos.length)]];
  while (caracteres.length < 16) caracteres.push(alfabeto[indice(alfabeto.length)]);
  for (let i = caracteres.length - 1; i > 0; i--) {
    const j = indice(i + 1);
    [caracteres[i], caracteres[j]] = [caracteres[j], caracteres[i]];
  }
  return caracteres.join('');
}
