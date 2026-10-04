import { ValidatorFn } from '@angular/forms';

/** Validación de experiencia de usuario; la API vuelve a validar el periodo inclusivo. */
export const rangoReporte: ValidatorFn = (grupo) => {
  const desde = String(grupo.get('desde')?.value ?? '');
  const hasta = String(grupo.get('hasta')?.value ?? '');
  const fechas = [desde, hasta].map((valor) => new Date(valor + 'T12:00:00Z'));
  if (
    [desde, hasta].some(
      (valor, indice) =>
        !/^\d{4}-\d{2}-\d{2}$/.test(valor) ||
        Number.isNaN(fechas[indice].getTime()) ||
        fechas[indice].toISOString().slice(0, 10) !== valor,
    )
  )
    return { fecha: true };
  if (desde > hasta) return { orden: true };
  const dias = (fechas[1].getTime() - fechas[0].getTime()) / 86400000 + 1;
  return dias > 366 ? { extension: true } : null;
};
