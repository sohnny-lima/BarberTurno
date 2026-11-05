import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';

export const ERROR_ESCRITURA_RED =
  'No se pudo confirmar el cambio por falta de conexión. Compruebe el resultado y vuelva a intentarlo cuando tenga conexión. No se reintentará automáticamente.';

/** Solo presenta conectividad; no promete que el servidor esté disponible. */
@Injectable({ providedIn: 'root' })
export class Red {
  private readonly ventana = inject(DOCUMENT).defaultView;
  readonly sinConexion = signal(this.ventana?.navigator.onLine === false);
  readonly recuperada = signal(false);
  private temporizador?: ReturnType<typeof setTimeout>;

  constructor() {
    const actualizar = () => {
      const sinConexion = this.ventana?.navigator.onLine === false;
      const volvio = this.sinConexion() && !sinConexion;
      this.sinConexion.set(sinConexion);
      this.recuperada.set(volvio);
      clearTimeout(this.temporizador);
      if (volvio) this.temporizador = setTimeout(() => this.recuperada.set(false), 5000);
    };
    this.ventana?.addEventListener('online', actualizar);
    this.ventana?.addEventListener('offline', actualizar);
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.temporizador);
      this.ventana?.removeEventListener('online', actualizar);
      this.ventana?.removeEventListener('offline', actualizar);
    });
  }
}
