import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { InvitacionEmitidaDto } from '../modelos/gestion';
import { CrearNegocioDto, NegocioCreadoDto, NegocioPlataformaDto } from '../modelos/plataforma';

@Injectable({ providedIn: 'root' })
export class PlataformaApi {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/plataforma/negocios';
  listar() {
    return this.http.get<NegocioPlataformaDto[]>(this.url);
  }
  crear(datos: CrearNegocioDto) {
    return this.http.post<NegocioCreadoDto>(this.url, datos);
  }
  cambiarEstado(id: number, estado: NegocioPlataformaDto['estado']) {
    return this.http.patch<void>(this.url + '/' + id + '/estado', { estado });
  }
  reenviar(id: number) {
    return this.http.post<InvitacionEmitidaDto>(
      this.url + '/' + id + '/invitacion-propietario',
      null,
    );
  }
  revocar(id: number) {
    return this.http.delete<void>(this.url + '/' + id + '/invitacion-propietario');
  }
}
