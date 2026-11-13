import { HttpErrorResponse } from '@angular/common/http';

const presentados = new WeakSet<HttpErrorResponse>();

/**
 * La vista muestra este error junto a la acción, en una región anunciada (`role="alert"` o
 * `status`): el interceptor no lo repite en un snackbar. Se llama en el manejador de error de la
 * suscripción, que se ejecuta antes que el aviso diferido del interceptor.
 */
export function marcarPresentado(error: HttpErrorResponse) {
  presentados.add(error);
}

export function estaPresentado(error: HttpErrorResponse) {
  return presentados.has(error);
}
