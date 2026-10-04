import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { finalize, Subscription } from 'rxjs';
import { NotificacionesApi } from '../../core/api/notificaciones-api';
import { NotificacionDto } from '../../core/modelos/notificaciones';
import { AvisosService } from '../../core/notificaciones/avisos-service';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { mostrarErrores } from '../../shared/formulario';
import { paginadorEspanol } from '../../shared/paginador-es';

@Component({
  selector: 'app-avisos-panel',
  imports: [MatButtonModule, MatPaginatorModule, FechaLimaPipe],
  providers: [{ provide: MatPaginatorIntl, useFactory: paginadorEspanol }],
  templateUrl: './avisos-panel.html',
  styles: [
    'li { padding: 16px 0; border-bottom: 1px solid #d8e3e6; } ul { list-style: none; padding: 0; }',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvisosPanel {
  private readonly api = inject(NotificacionesApi);
  readonly avisos = inject(AvisosService);
  private readonly destroyRef = inject(DestroyRef);
  private peticion?: Subscription;
  readonly revision = input(0);
  readonly filas = signal<NotificacionDto[]>([]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  readonly pagina = signal(0);
  readonly tamano = signal(10);
  readonly total = signal(0);
  constructor() {
    effect(() => {
      this.revision();
      untracked(() => this.cargar());
    });
  }
  cargar() {
    this.peticion?.unsubscribe();
    this.cargando.set(true);
    this.mensaje.set('');
    this.peticion = this.api
      .listar(this.pagina(), this.tamano())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (datos) => {
          this.filas.set(datos.contenido);
          this.total.set(datos.totalElementos);
        },
        error: (error) => this.mensaje.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
  paginar(evento: PageEvent) {
    this.pagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.cargar();
  }
  marcar(id?: number) {
    if (this.guardando()) return;
    this.guardando.set(true);
    this.mensaje.set('');
    (id === undefined ? this.api.marcarTodas() : this.api.marcarLeida(id))
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: () => {
          this.cargar();
          this.avisos.actualizar();
        },
        error: (error) => this.mensaje.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
}
