import { NegocioActual } from '../negocio/negocio-actual';
import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, filter, of, switchMap, tap, throwError } from 'rxjs';
import { AuthApi } from '../api/auth-api';
import {
  CambiarPasswordDto,
  LoginDto,
  PerfilDto,
  RegistroDto,
  UsuarioSesionDto,
} from '../modelos/identidad';

@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly api = inject(AuthApi);
  private readonly negocio = inject(NegocioActual);
  private generacion = 0;
  private readonly identidad = signal<UsuarioSesionDto | null>(null);
  readonly usuario = this.identidad.asReadonly();
  readonly autenticado = computed(() => this.usuario() !== null);
  readonly rol = computed(() => this.usuario()?.rol ?? null);
  readonly debeCambiarPassword = computed(() => this.usuario()?.debeCambiarPassword ?? false);

  cargar() {
    const generacion = this.generacion;
    return this.api.sesion().pipe(
      filter(() => generacion === this.generacion),
      tap((usuario) => this.aceptar(usuario)),
      catchError((error: HttpErrorResponse) => {
        if (generacion !== this.generacion) return of(null);
        this.limpiar();
        return error.status === 401 ? of(null) : throwError(() => error);
      }),
    );
  }
  login(datos: LoginDto) {
    return this.api.login(datos).pipe(tap((usuario) => this.aceptar(usuario)));
  }
  registrar(datos: RegistroDto) {
    return this.api.registrar(datos).pipe(tap((usuario) => this.aceptar(usuario)));
  }
  logout() {
    return this.api.logout().pipe(tap(() => this.limpiar()));
  }
  cambiarPassword(datos: CambiarPasswordDto) {
    return this.api.cambiarPassword(datos).pipe(switchMap(() => this.cargar()));
  }
  actualizarPerfil(perfil: PerfilDto) {
    this.identidad.update((usuario) => (usuario ? { ...usuario, nombre: perfil.nombre } : null));
  }
  generacionActual() {
    return this.generacion;
  }
  private aceptar(usuario: UsuarioSesionDto) {
    if (this.usuario()?.id !== usuario.id) {
      if (this.usuario()) this.limpiar();
      else this.generacion++;
    }
    this.identidad.set(usuario);
  }
  limpiar() {
    this.generacion++;
    this.identidad.set(null);
    this.negocio.limpiar();
  }
  inicio() {
    return this.debeCambiarPassword() ? '/cambiar-password' : '/negocios';
  }
}
