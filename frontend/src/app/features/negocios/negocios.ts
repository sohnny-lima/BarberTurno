import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { switchMap, of } from 'rxjs';
import { MisNegociosApi } from '../../core/api/mis-negocios-api';
import { NegocioPropioDto, NOMBRES_PAPEL } from '../../core/modelos/negocios';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { EstadoContenido } from '../../shared/estado-contenido';
import { EntradaNegocio } from './entrada-negocio';
import { RouterLink } from '@angular/router';
import { SesionService } from '../../core/auth/sesion-service';

@Component({
  selector: 'app-negocios',
  imports: [MatButtonModule, CabeceraPagina, EstadoContenido, RouterLink],
  template: `<app-cabecera-pagina
      titulo="Sus barberías"
      subtitulo="Elija dónde quiere trabajar o reservar."
    >
      @if (sesion.usuario()?.rolPlataforma === 'ADMIN_PLATAFORMA') {
        <a mat-stroked-button routerLink="/plataforma/negocios">Plataforma</a>
      }
    </app-cabecera-pagina>
    @if (negocio.avisoSelector()) {
      <app-estado-contenido tipo="aviso" [texto]="negocio.avisoSelector()" />
    }
    @if (cargando()) {
      <app-estado-contenido tipo="carga" texto="Cargando barberías…" />
    } @else if (error()) {
      <app-estado-contenido
        tipo="error"
        [texto]="error()"
        reintento="Reintentar"
        (reintentar)="cargar()"
      />
    } @else if (!filas().length) {
      <app-estado-contenido
        tipo="vacio"
        texto="Todavía no pertenece a ninguna barbería. Si recibió una invitación, ábrala desde su enlace."
      />
    } @else {
      <ul aria-label="Barberías">
        @for (fila of ordenadas(); track fila.slug) {
          <li>
            <div>
              <strong>{{ fila.nombre }}</strong
              ><span>{{ papeles(fila) }}</span>
              @if (fila.slug === ultima) {
                <small>Última usada</small>
              }
            </div>
            <button
              mat-stroked-button
              [attr.aria-label]="'Entrar en ' + fila.nombre"
              (click)="entrar(fila)"
            >
              Entrar
            </button>
          </li>
        }
      </ul>
    }`,
  styles: `
    ul {
      padding: 0;
      list-style: none;
      border: 1px solid var(--color-linea);
      border-radius: 16px;
      background: var(--color-superficie);
    }
    li {
      display: flex;
      align-items: center;
      gap: 16px;
      justify-content: space-between;
      min-height: 64px;
      padding: 16px;
    }
    li + li {
      border-top: 1px solid var(--color-linea);
    }
    li div {
      min-width: 0;
    }
    strong {
      font-size: 16px;
      overflow-wrap: anywhere;
    }
    span {
      display: block;
      color: var(--color-texto-2);
      font-size: 14px;
    }
    small {
      display: inline-block;
      background: var(--color-verde-claro);
      color: var(--color-verde);
      padding: 2px 8px;
      border-radius: 20px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Negocios {
  readonly sesion = inject(SesionService);
  private readonly api = inject(MisNegociosApi);
  private readonly entrada = inject(EntradaNegocio);
  private readonly destroyRef = inject(DestroyRef);
  readonly negocio = inject(NegocioActual);
  readonly filas = signal<NegocioPropioDto[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly ultima = (() => {
    try {
      return localStorage.getItem('barberturno.ultima-barberia');
    } catch {
      return null;
    }
  })();
  readonly ordenadas = computed(() =>
    [...this.filas()].sort(
      (a, b) => Number(b.slug === this.ultima) - Number(a.slug === this.ultima),
    ),
  );
  readonly papeles = (fila: NegocioPropioDto) =>
    fila.papeles.map((papel) => NOMBRES_PAPEL[papel]).join(' y ');
  constructor() {
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.error.set('');
    this.api
      .listar()
      .pipe(
        switchMap((filas) => {
          this.filas.set(filas);
          this.cargando.set(false);
          // Un aviso de revocación debe poder leerse sin volver a entrar automáticamente.
          return filas.length === 1 && !this.negocio.avisoSelector()
            ? this.entrada.entrar(filas[0])
            : of(undefined);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        error: () => {
          this.cargando.set(false);
          this.error.set('No pudimos consultar sus barberías. Intente nuevamente.');
        },
      });
  }
  entrar(fila: NegocioPropioDto) {
    this.negocio.avisoSelector.set('');
    this.entrada.entrar(fila).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }
}
