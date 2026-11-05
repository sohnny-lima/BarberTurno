import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class Teclado {
  readonly abierto = signal(false);

  constructor() {
    const documento = inject(DOCUMENT);
    const ventana = documento.defaultView;
    const viewport = ventana?.visualViewport;
    if (!ventana || !viewport) return;
    let cuadro = 0;
    let altoInicial = viewport.height;
    let anchoInicial = viewport.width;
    const actualizar = () => {
      const campo = documento.activeElement;
      const editable =
        campo instanceof HTMLElement && campo.matches('input, textarea, [contenteditable="true"]');
      if (viewport.scale === 1 && (!editable || Math.abs(viewport.width - anchoInicial) > 50)) {
        altoInicial = viewport.height;
        anchoInicial = viewport.width;
      }
      const abierto = editable && viewport.scale === 1 && altoInicial - viewport.height > 150;
      this.abierto.set(abierto);
      ventana.cancelAnimationFrame(cuadro);
      if (abierto)
        cuadro = ventana.requestAnimationFrame(() =>
          campo.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' }),
        );
    };
    const orientar = () => {
      altoInicial = viewport.height;
      this.abierto.set(false);
    };
    ventana.addEventListener('orientationchange', orientar);
    viewport.addEventListener('resize', actualizar);
    viewport.addEventListener('scroll', actualizar);
    documento.addEventListener('focusin', actualizar);
    documento.addEventListener('focusout', actualizar);
    inject(DestroyRef).onDestroy(() => {
      ventana.cancelAnimationFrame(cuadro);
      ventana.removeEventListener('orientationchange', orientar);
      viewport.removeEventListener('resize', actualizar);
      viewport.removeEventListener('scroll', actualizar);
      documento.removeEventListener('focusin', actualizar);
      documento.removeEventListener('focusout', actualizar);
    });
  }
}
