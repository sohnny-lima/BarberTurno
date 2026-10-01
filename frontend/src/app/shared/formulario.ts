import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ProblemDetail } from '../core/modelos/identidad';

export const confirmarPassword: ValidatorFn = (grupo: AbstractControl): ValidationErrors | null =>
  grupo.get('password')?.value === grupo.get('confirmacion')?.value ? null : { confirmacion: true };

export function mostrarErrores(formulario: FormGroup, error: HttpErrorResponse): string {
  const problema = error.error as Partial<ProblemDetail> | null;
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
  if (problema?.codigo === 'CORREO_DUPLICADO') {
    formulario.get('correo')?.setErrors({ servidor: problema.detail });
    formulario.get('correo')?.markAsTouched();
  }
  return error.status === 0 || error.status >= 500
    ? 'No pudimos completar la solicitud. Intente nuevamente.'
    : problema?.detail || 'No pudimos completar la solicitud.';
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
