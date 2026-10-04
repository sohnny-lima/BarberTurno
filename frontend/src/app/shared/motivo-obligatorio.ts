import { ValidatorFn } from '@angular/forms';

/** Ayuda de formulario para el motivo administrativo; la política se valida en el servidor. */
export const motivoObligatorio: ValidatorFn = (control) =>
  String(control.value ?? '').trim().length >= 5 ? null : { motivoObligatorio: true };
