import { HttpErrorResponse } from '@angular/common/http';

export const ERROR_ESCRITURA_RED =
  'No se pudo confirmar el cambio por falta de conexión. Compruebe el resultado y vuelva a intentarlo cuando tenga conexión. No se reintentará automáticamente.';

/**
 * Fallo por falta de red: sin respuesta (status 0) o el 504 que devuelve el service worker de Angular
 * cuando no hay conexión. Un 504 del servidor con la red disponible sigue siendo un error del servidor.
 */
export function esFalloDeRed(
  error: HttpErrorResponse,
  enLinea = globalThis.navigator?.onLine ?? true,
) {
  return error.status === 0 || (error.status === 504 && !enLinea);
}
