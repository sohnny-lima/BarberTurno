import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { InvitacionDto, InvitacionEmitidaDto } from '../modelos/gestion';
import { RolPersonal } from '../modelos/negocios';
import { NegocioActual } from '../negocio/negocio-actual';

@Injectable()
export class InvitacionesApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual).cliente();
  listar() {
    return this.http.get<InvitacionDto[]>(this.negocio.url('/invitaciones'), {
      context: this.negocio.contexto(),
    });
  }
  crear(datos: { correo: string; rol: RolPersonal }) {
    return this.http.post<InvitacionEmitidaDto>(this.negocio.url('/invitaciones'), datos, {
      context: this.negocio.contexto(),
    });
  }
  revocar(id: number) {
    return this.http.delete<void>(this.negocio.url('/invitaciones/') + id, {
      context: this.negocio.contexto(),
    });
  }
}
