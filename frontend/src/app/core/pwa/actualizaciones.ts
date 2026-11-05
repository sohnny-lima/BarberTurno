import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SwUpdate } from '@angular/service-worker';

/** Punto sustituible en pruebas: nunca se invoca sin aceptación del usuario. */
export const RECARGAR_PWA = new InjectionToken<() => void>('Recargar PWA', {
  providedIn: 'root',
  factory: () => () => location.reload(),
});

@Injectable({ providedIn: 'root' })
export class Actualizaciones {
  private readonly sw = inject(SwUpdate, { optional: true });
  private readonly documento = inject(DOCUMENT);
  private readonly recargar = inject(RECARGAR_PWA);
  readonly disponible = signal(false);
  readonly irrecuperable = signal(false);
  readonly activando = signal(false);
  readonly error = signal('');
  private ultimaComprobacion = 0;

  constructor() {
    if (!this.sw?.isEnabled) return;
    this.sw.versionUpdates.pipe(takeUntilDestroyed()).subscribe((evento) => {
      if (evento.type === 'VERSION_READY') this.disponible.set(true);
    });
    this.sw.unrecoverable.pipe(takeUntilDestroyed()).subscribe(() => {
      this.irrecuperable.set(true);
    });
    const comprobar = () => {
      if (this.documento.visibilityState !== 'visible') return;
      const ahora = Date.now();
      if (ahora - this.ultimaComprobacion < 30 * 60_000) return;
      this.ultimaComprobacion = ahora;
      void this.sw!.checkForUpdate().catch(() => {
        // Sin red se conserva la versión instalada; la próxima comprobación respeta el intervalo.
      });
    };
    this.documento.addEventListener('visibilitychange', comprobar);
    inject(DestroyRef).onDestroy(() =>
      this.documento.removeEventListener('visibilitychange', comprobar),
    );
  }

  async actualizar() {
    if (this.activando() || (!this.disponible() && !this.irrecuperable())) return;
    if (
      this.documento.querySelector(
        '#contenido form.ng-dirty, .cdk-overlay-container form.ng-dirty, mat-dialog-container',
      ) &&
      !this.documento.defaultView?.confirm(
        'Hay un formulario modificado o un diálogo abierto. Al actualizar se perderán los cambios sin guardar. ¿Desea continuar?',
      )
    )
      return;
    this.activando.set(true);
    this.error.set('');
    try {
      if (!this.irrecuperable()) await this.sw!.activateUpdate();
      this.recargar();
    } catch {
      this.error.set('No se pudo actualizar. Compruebe la conexión y vuelva a intentarlo.');
    } finally {
      this.activando.set(false);
    }
  }
}
