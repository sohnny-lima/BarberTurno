import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Foto decorativa local adaptada al ancho de pantalla, visible en el primer paso. */
@Component({
  selector: 'app-reserva-foto',
  template: `<figure>
    <picture
      ><source media="(min-width: 768px)" srcset="/fotos/interior-vacio.webp" />
      <img src="/fotos/tijeras-mesa-recorte.webp" alt="" width="960" height="430"
    /></picture>
    <figcaption>Elija servicio, día y hora. Paga en la barbería.</figcaption>
  </figure>`,
  styleUrl: './reserva-foto.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaFoto {}
