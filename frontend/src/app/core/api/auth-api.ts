import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { CambiarPasswordDto, LoginDto, RegistroDto, UsuarioSesionDto } from '../modelos/identidad';
import { NegocioActual } from '../negocio/negocio-actual';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual);
  sesion() {
    return this.http.get<UsuarioSesionDto>('/api/auth/sesion');
  }
  login(datos: LoginDto) {
    return this.http.post<UsuarioSesionDto>('/api/auth/login', datos);
  }
  registrar(datos: RegistroDto) {
    return this.http.post<UsuarioSesionDto>(this.negocio.url('/registro'), datos, {
      context: this.negocio.contexto(),
    });
  }
  logout() {
    return this.http.post<void>('/api/auth/logout', null);
  }
  cambiarPassword(datos: CambiarPasswordDto) {
    return this.http.put<void>('/api/auth/password', datos);
  }
}
