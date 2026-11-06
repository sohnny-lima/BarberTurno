import { HttpErrorResponse } from '@angular/common/http';
import { effect, inject } from '@angular/core';
import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ProblemDetail } from '../core/modelos/identidad';
import { marcarPresentado } from '../core/notificaciones/errores-presentados';
import { NegocioActual } from '../core/negocio/negocio-actual';
import { ERROR_ESCRITURA_RED } from '../core/pwa/error-red';

export const confirmarPassword: ValidatorFn = (grupo: AbstractControl): ValidationErrors | null =>
  grupo.get('password')?.value === grupo.get('confirmacion')?.value ? null : { confirmacion: true };

/** Devuelve el mensaje que la vista muestra junto a la acción; por eso no se repite en un snackbar. */
export function mostrarErrores(formulario: FormGroup, error: HttpErrorResponse): string {
  marcarPresentado(error);
  const problema = error.error as Partial<ProblemDetail> | null;
  // El interceptor ya tradujo una escritura sin red (V2-DA-89): la vista muestra ese mensaje, no el genérico.
  if (problema?.detail === ERROR_ESCRITURA_RED) return ERROR_ESCRITURA_RED;
  if (error.status === 400) {
    for (const detalle of problema?.errores ?? []) {
      const control = formulario.get(detalle.campo);
      if (control) {
        const anteriores = control.getError('servidor') as string | undefined;
        control.setErrors({
          ...control.errors,
          servidor: anteriores ? anteriores + ' ' + detalle.mensaje : detalle.mensaje,
        });
        control.markAsTouched();
      }
    }
  }
  if (problema?.codigo === 'NOMBRE_DUPLICADO') {
    formulario.get('nombre')?.setErrors({ servidor: problema.detail });
    formulario.get('nombre')?.markAsTouched();
  }
  if (problema?.codigo === 'CORREO_DUPLICADO') {
    formulario.get('correo')?.setErrors({ servidor: problema.detail });
    formulario.get('correo')?.markAsTouched();
  }
  return error.status === 0 || error.status >= 500
    ? 'No pudimos completar la solicitud. Intente nuevamente.'
    : problema?.detail || 'No pudimos completar la solicitud.';
}
/**
 * Con la barbería suspendida el formulario queda en solo lectura (V2-DA-49) y vuelve a editarse
 * si se reactiva. Solo presentación: el servidor sigue rechazando la escritura con 409. Debe
 * llamarse en un contexto de inyección; devuelve la señal para desactivar los botones.
 */
export function soloLecturaSegunNegocio(formulario: AbstractControl) {
  const negocio = inject(NegocioActual);
  effect(() => {
    if (negocio.soloLectura()) formulario.disable({ emitEvent: false });
    else formulario.enable({ emitEvent: false });
  });
  return negocio.soloLectura;
}
export function errorCampo(control: AbstractControl): string {
  return (
    control.getError('servidor') ||
    (control.hasError('required')
      ? 'Este campo es obligatorio.'
      : control.hasError('email')
        ? 'Ingrese un correo válido.'
        : control.hasError('pattern')
          ? 'Ingrese nueve dígitos.'
          : 'Revise el valor ingresado.')
  );
}
