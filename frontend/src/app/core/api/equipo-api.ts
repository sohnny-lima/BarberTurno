import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { EquipoDto } from '../modelos/gestion';
import { RolPersonal } from '../modelos/negocios';
import { NegocioActual } from '../negocio/negocio-actual';

@Injectable()
export class EquipoApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  listar() {
    return this.http.get<EquipoDto[]>(this.negocio.url('/equipo'), {
      context: this.negocio.contexto(),
    });
  }
  cambiarRol(id: number, rol: RolPersonal) {
    return this.http.patch<void>(
      this.negocio.url('/equipo/') + id + '/rol',
      { rol },
      { context: this.negocio.contexto() },
    );
  }
  revocar(id: number) {
    return this.http.post<void>(this.negocio.url('/equipo/') + id + '/revocacion', null, {
      context: this.negocio.contexto(),
    });
  }
  salir() {
    return this.http.post<void>(this.negocio.url('/equipo/salida'), null, {
      context: this.negocio.contexto(),
    });
  }
}
