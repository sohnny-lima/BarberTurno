import { NegocioActual } from '../negocio/negocio-actual';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  BarberoDto,
  CambiarEstadoBarberoDto,
  CrearBarberoDto,
  EditarBarberoDto,
} from '../modelos/catalogo';

@Injectable({ providedIn: 'root' })
export class BarberosApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  listar(incluirInactivos = false) {
    return this.http.get<BarberoDto[]>(this.negocio.url('/barberos'), {
      context: this.negocio.contexto(),
      params: { incluirInactivos },
    });
  }
  crear(datos: CrearBarberoDto) {
    return this.http.post<BarberoDto>(this.negocio.url('/barberos'), datos, {
      context: this.negocio.contexto(),
    });
  }
  editar(id: number, datos: EditarBarberoDto) {
    return this.http.put<BarberoDto>(this.negocio.url('/barberos/') + id, datos, {
      context: this.negocio.contexto(),
    });
  }
  cambiarEstado(id: number, activo: boolean) {
    return this.http.patch<CambiarEstadoBarberoDto>(
      this.negocio.url('/barberos/') + id + '/estado',
      { activo },
      { context: this.negocio.contexto() },
    );
  }
}
