import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { NegocioPropioDto } from '../modelos/negocios';

@Injectable({ providedIn: 'root' })
export class MisNegociosApi {
  private readonly http = inject(HttpClient);
  listar() {
    return this.http.get<NegocioPropioDto[]>('/api/mis-negocios');
  }
}
