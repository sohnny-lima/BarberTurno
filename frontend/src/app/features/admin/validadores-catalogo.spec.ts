import { FormControl } from '@angular/forms';
import {
  formularioServicio,
  generarPasswordTemporal,
  passwordPersonal,
} from './validadores-catalogo';

describe('Formulario de servicio: validación de experiencia de usuario', () => {
  it.each([10, 30, 180])('admite duración %s', (valor) => {
    const control = formularioServicio().controls.duracionMin;
    control.setValue(valor);
    expect(control.valid).toBe(true);
  });
  it.each([0, 9, 15, 181, 20.5])('rechaza duración %s', (valor) => {
    const control = formularioServicio().controls.duracionMin;
    control.setValue(valor);
    expect(control.invalid).toBe(true);
  });
  it.each([0, 0.1, 25.5, 999999.99])('admite precio %s', (valor) => {
    const control = formularioServicio().controls.precio;
    control.setValue(valor);
    expect(control.valid).toBe(true);
  });
  it.each([-1, 1.001, 1000000, 0.0000001])('rechaza precio %s', (valor) => {
    const control = formularioServicio().controls.precio;
    control.setValue(valor);
    expect(control.invalid).toBe(true);
  });
  it.each(['', 'A', ' Corte', 'Corte ', 'Corte  barba', 'A'.repeat(81)])(
    'rechaza nombre inválido %s',
    (valor) => {
      const control = formularioServicio().controls.nombre;
      control.setValue(valor);
      expect(control.invalid).toBe(true);
    },
  );
  it('admite descripción vacía y rechaza más de 300 caracteres', () => {
    const control = formularioServicio().controls.descripcion;
    expect(control.valid).toBe(true);
    control.setValue('A'.repeat(301));
    expect(control.invalid).toBe(true);
  });
});

describe('Contraseña temporal RN-25', () => {
  it('cumple longitud, letra, dígito y bytes en 100 generaciones', () => {
    const resultados = new Set<string>();
    for (let i = 0; i < 100; i++) {
      const password = generarPasswordTemporal();
      expect(Array.from(password).length).toBeGreaterThanOrEqual(8);
      expect(Array.from(password).length).toBeLessThanOrEqual(72);
      expect(new TextEncoder().encode(password).length).toBeLessThanOrEqual(72);
      expect(password).toMatch(/[a-zA-Z]/);
      expect(password).toMatch(/[0-9]/);
      resultados.add(password);
    }
    expect(resultados.size).toBe(100);
  });
  it.each(['corta1', 'abcdefgh', '12345678', 'á'.repeat(36) + '1'])(
    'rechaza password fuera de política',
    (valor) => {
      expect(passwordPersonal(new FormControl(valor))).toEqual({ passwordPersonal: true });
    },
  );
  it('acepta una letra multibyte dentro del límite', () => {
    expect(passwordPersonal(new FormControl('á'.repeat(35) + '1'))).toBeNull();
  });
});
