import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { InvitacionPublicaDto, RegistroInvitadoDto } from '../modelos/gestion';
import { UsuarioSesionDto } from '../modelos/identidad';

@Injectable({ providedIn: 'root' })
export class InvitacionApi {
  private readonly http = inject(HttpClient);
  consultar(token: string) {
    return this.http.get<InvitacionPublicaDto>('/api/invitaciones/' + token);
  }
  aceptar(token: string) {
    return this.http.post<void>('/api/invitaciones/' + token + '/aceptacion', null);
  }
  registrar(token: string, datos: RegistroInvitadoDto) {
    return this.http.post<UsuarioSesionDto>('/api/invitaciones/' + token + '/registro', datos);
  }
}
