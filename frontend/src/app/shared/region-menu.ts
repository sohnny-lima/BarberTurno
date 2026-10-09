import { DOCUMENT } from '@angular/common';
import { Directive, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatMenuTrigger } from '@angular/material/menu';

/** Identifica el área portalizada sin cambiar el rol ni el teclado del menú Material. */
@Directive({ selector: '[appRegionMenu]' })
export class RegionMenu {
  readonly regionMenu = input.required<string>({ alias: 'appRegionMenu' });

  constructor() {
    const documento = inject(DOCUMENT);
    const disparador = inject(MatMenuTrigger);
    let contenedor: HTMLElement | undefined;
    disparador.menuOpened.pipe(takeUntilDestroyed()).subscribe(() => {
      const id = disparador.menu?.panelId;
      contenedor = (id ? documento.getElementById(id)?.parentElement : undefined) ?? undefined;
      contenedor?.setAttribute('role', 'region');
      contenedor?.setAttribute('aria-label', this.regionMenu());
    });
    disparador.menuClosed.pipe(takeUntilDestroyed()).subscribe(() => {
      contenedor?.removeAttribute('role');
      contenedor?.removeAttribute('aria-label');
      contenedor = undefined;
    });
  }
}
