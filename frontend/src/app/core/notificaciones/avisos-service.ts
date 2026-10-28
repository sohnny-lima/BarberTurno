import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import {
  catchError,
  defer,
  distinctUntilChanged,
  EMPTY,
  filter,
  fromEvent,
  map,
  merge,
  startWith,
  Subject,
  switchMap,
  tap,
  timer,
} from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { NegocioActual } from '../negocio/negocio-actual';
import { SesionService } from '../auth/sesion-service';

@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly http = inject(HttpClient);
  private readonly negocio = inject(NegocioActual);
  private readonly sesion = inject(SesionService);
  private readonly documento = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly refrescar = new Subject<void>();
  private readonly contador = signal(0);
  readonly noLeidas = this.contador.asReadonly();

  constructor() {
    this.negocio.cambios$.pipe(takeUntilDestroyed()).subscribe(() => this.contador.set(0));
    toObservable(
      computed(() => ({
        usuario: this.sesion.usuario(),
        ambito: this.negocio.ambito(),
        rol: this.negocio.rol(),
      })),
    )
      .pipe(
        map(({ usuario, ambito, rol }) =>
          usuario && !usuario.debeCambiarPassword && ambito && rol
            ? usuario.id + '/' + ambito.generacion
            : null,
        ),
        distinctUntilChanged(),
        tap(() => this.contador.set(0)),
        // El origen se fija al activarse la clave: al salir de la barbería, el NavigationEnd llega
        // antes de que la clave pase a null y negocioInterceptor descarta esa consulta sin enviarla.
        // Ningún fallo síncrono puede terminar el sondeo durante el resto de la vida de la página.
        switchMap((id) =>
          id === null
            ? EMPTY
            : defer(() => {
                const api = this.negocio.cliente();
                return fromEvent(this.documento, 'visibilitychange').pipe(
                  startWith(null),
                  map(() => this.documento.visibilityState === 'visible'),
                  distinctUntilChanged(),
                  switchMap((visible) =>
                    visible
                      ? merge(
                          timer(0, 60_000),
                          this.router.events.pipe(
                            filter((evento) => evento instanceof NavigationEnd),
                          ),
                          this.refrescar,
                        ).pipe(
                          switchMap(() =>
                            defer(() =>
                              this.http.get<{ noLeidas: number }>(
                                api.url('/notificaciones/conteo'),
                                { context: api.contexto() },
                              ),
                            ).pipe(
                              tap((dato) => this.contador.set(dato.noLeidas)),
                              catchError(() => EMPTY),
                            ),
                          ),
                        )
                      : EMPTY,
                  ),
                );
              }).pipe(catchError(() => EMPTY)),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }
  actualizar() {
    this.refrescar.next();
  }
}
