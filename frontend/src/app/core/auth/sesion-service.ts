import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of, switchMap, tap, throwError } from 'rxjs';
import { AuthApi } from '../api/auth-api';
import {
  CambiarPasswordDto,
  LoginDto,
  PerfilDto,
  ProblemDetail,
  RegistroDto,
  UsuarioSesionDto,
} from '../modelos/identidad';

@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly api = inject(AuthApi);
  private readonly identidad = signal<UsuarioSesionDto | null>(null);
  readonly usuario = this.identidad.asReadonly();
  readonly autenticado = computed(() => this.usuario() !== null);
  readonly rol = computed(() => this.usuario()?.rol ?? null);
  readonly debeCambiarPassword = computed(() => this.usuario()?.debeCambiarPassword ?? false);

  cargar() {
    return this.api.sesion().pipe(
      tap((usuario) => this.identidad.set(usuario)),
      catchError((error: HttpErrorResponse) => {
        this.limpiar();
        return error.status === 401 ? of(null) : throwError(() => error);
      }),
    );
  }
  login(datos: LoginDto) {
    return this.api.login(datos).pipe(
      catchError((error: HttpErrorResponse) => {
        // Una cookie revocada impide llegar al login. Logout público la elimina con CSRF.
        if (
          error.status === 401 &&
          (error.error as Partial<ProblemDetail> | null)?.codigo === 'NO_AUTENTICADO'
        ) {
          return this.logout().pipe(switchMap(() => this.api.login(datos)));
        }
        return throwError(() => error);
      }),
      tap((usuario) => this.identidad.set(usuario)),
    );
  }
  registrar(datos: RegistroDto) {
    return this.api.registrar(datos).pipe(tap((usuario) => this.identidad.set(usuario)));
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
  limpiar() {
    this.identidad.set(null);
  }
  inicio() {
    return this.debeCambiarPassword()
      ? '/cambiar-password'
      : this.rol() === 'CLIENTE'
        ? '/reservar'
        : '/agenda';
  }
}
