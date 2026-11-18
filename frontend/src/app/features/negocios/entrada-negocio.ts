import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { map, of } from 'rxjs';
import { NegocioPropioDto, Papel } from '../../core/modelos/negocios';
import { PapelDialogo } from './papel-dialogo';

@Injectable({ providedIn: 'root' })
export class EntradaNegocio {
  private readonly dialogos = inject(MatDialog);
  private readonly router = inject(Router);
  elegir(negocio: NegocioPropioDto) {
    return negocio.papeles.includes('CLIENTE') &&
      negocio.papeles.some((papel) => papel !== 'CLIENTE')
      ? this.dialogos
          .open<PapelDialogo, NegocioPropioDto, Papel>(PapelDialogo, {
            data: negocio,
            width: '480px',
            maxWidth: 'calc(100vw - 32px)',
            maxHeight: 'calc(100dvh - 32px)',
          })
          .afterClosed()
      : of<Papel>(negocio.papeles.includes('CLIENTE') ? 'CLIENTE' : 'PERSONAL');
  }
  entrar(negocio: NegocioPropioDto) {
    return this.elegir(negocio).pipe(
      map((papel) => {
        if (!papel) return;
        try {
          localStorage.setItem('barberturno.ultima-barberia', negocio.slug);
        } catch {
          /* Comodidad opcional. */
        }
        void this.router.navigate(
          ['/n', negocio.slug, papel === 'CLIENTE' ? 'reservar' : 'agenda'],
          {
            queryParams: papel === 'CLIENTE' ? { papel: 'cliente' } : {},
          },
        );
      }),
    );
  }
}
