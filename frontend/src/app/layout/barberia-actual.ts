import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { catchError, of, switchMap, map, takeUntil } from 'rxjs';
import { MisNegociosApi } from '../core/api/mis-negocios-api';
import { NegocioPropioDto, NOMBRES_PAPEL } from '../core/modelos/negocios';
import { NegocioActual } from '../core/negocio/negocio-actual';
import { EntradaNegocio } from '../features/negocios/entrada-negocio';

@Component({
  selector: 'app-barberia-actual',
  imports: [RouterLink, MatButtonModule],
  template: `@if (negocio.slug()) {
    <section aria-label="Barbería actual">
      <strong>{{ negocio.nombre() }}</strong
      ><span>{{ nombrePapel() }}</span>
      @if (puedeCambiarPapel()) {
        <button mat-button (click)="cambiarPapel()">Cambiar papel</button>
      }
      <a mat-button routerLink="/negocios">Cambiar de barbería</a>
    </section>
  }`,
  styles: `
    section {
      display: grid;
      border: 1px solid var(--color-linea);
      border-radius: 12px;
      padding: 12px;
      margin-top: 16px;
      background: var(--color-superficie);
    }
    strong {
      font-size: 16px;
      line-height: 24px;
      overflow-wrap: anywhere;
    }
    span {
      color: var(--color-texto-2);
      font-size: 14px;
    }
    a,
    button {
      min-height: 44px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarberiaActual {
  readonly negocio = inject(NegocioActual);
  private readonly api = inject(MisNegociosApi);
  private readonly entrada = inject(EntradaNegocio);
  private readonly destroyRef = inject(DestroyRef);
  readonly relacion = signal<NegocioPropioDto | null>(null);
  readonly puedeCambiarPapel = () =>
    this.relacion()?.papeles.includes('CLIENTE') &&
    this.relacion()?.papeles.some((p) => p !== 'CLIENTE');
  readonly nombrePapel = () =>
    this.negocio.rol() === 'CLIENTE'
      ? 'Cliente'
      : this.negocio.rol()
        ? NOMBRES_PAPEL[this.relacion()?.rol ?? 'BARBERO']
        : 'Visitante';
  constructor() {
    toObservable(
      computed(() => ({ ambito: this.negocio.ambito(), usuario: this.negocio.usuario() })),
    )
      .pipe(
        switchMap(({ ambito: origen, usuario }) => {
          this.relacion.set(null);
          return origen && usuario
            ? this.api.listar().pipe(
                catchError(() => of([])),
                takeUntil(this.negocio.cambios$),
                map((filas) => ({ filas, origen })),
              )
            : of({ filas: [], origen });
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ filas, origen }) => {
        if (!origen || !this.negocio.vigente(origen)) return;
        const relacion = filas.find((fila) => fila.slug === origen.slug) ?? null;
        this.relacion.set(relacion);
        this.negocio.nombreRelacion.set(relacion?.nombre ?? null);
      });
  }
  cambiarPapel() {
    const relacion = this.relacion();
    if (relacion)
      this.entrada.entrar(relacion).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }
}
