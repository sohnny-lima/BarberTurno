import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { NegocioActual } from '../negocio/negocio-actual';
import {
  CodigoVinculoProfesionalDto,
  EstadoVinculoProfesionalDto,
} from '../modelos/vinculo-profesional';

@Injectable({ providedIn: 'root' })
export class VinculoProfesionalApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  private readonly url = this.negocio.url('/whatsapp/vinculo-profesional');

  estado() {
    return this.http.get<EstadoVinculoProfesionalDto>(this.url, {
      context: this.negocio.contexto(),
    });
  }
  generar() {
    return this.http.post<CodigoVinculoProfesionalDto>(
      this.url,
      {},
      {
        context: this.negocio.contexto(),
      },
    );
  }
  desvincular() {
    return this.http.delete<void>(this.url, { context: this.negocio.contexto() });
  }
}
