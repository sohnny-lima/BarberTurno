import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { VinculoProfesionalApi } from '../../core/api/vinculo-profesional-api';
import {
  CodigoVinculoProfesionalDto,
  EstadoVinculoProfesionalDto,
} from '../../core/modelos/vinculo-profesional';
import { NegocioActual } from '../../core/negocio/negocio-actual';

@Component({
  selector: 'app-whatsapp-profesional',
  imports: [MatButtonModule],
  templateUrl: './whatsapp-profesional.html',
  styleUrl: './whatsapp-profesional.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WhatsAppProfesional {
  private readonly api = inject(VinculoProfesionalApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly negocio = inject(NegocioActual);
  readonly estado = signal<EstadoVinculoProfesionalDto | null>(null);
  readonly codigo = signal<CodigoVinculoProfesionalDto | null>(null);
  readonly ocupado = signal(false);
  readonly error = signal('');
  readonly disponible = signal(true);
  readonly fecha = (instante: string) =>
    new Intl.DateTimeFormat('es-PE', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'America/Lima',
    }).format(new Date(instante));

  constructor() {
    this.cargar();
  }

  cargar() {
    this.ocupado.set(true);
    this.error.set('');
    this.api
      .estado()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (estado) => this.estado.set(estado),
        error: (error: HttpErrorResponse) => this.fallar(error),
      });
  }
  generar() {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.error.set('');
    this.codigo.set(null);
    this.api
      .generar()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (codigo) => this.codigo.set(codigo),
        error: (error: HttpErrorResponse) => this.fallar(error),
      });
  }
  desvincular() {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.error.set('');
    this.api
      .desvincular()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: () => {
          this.codigo.set(null);
          this.estado.set({ vinculado: false, vinculadoEn: null });
        },
        error: (error: HttpErrorResponse) => this.fallar(error),
      });
  }
  private fallar(error: HttpErrorResponse) {
    this.codigo.set(null);
    if (error.status === 404) this.disponible.set(false);
    else this.error.set('No pudimos actualizar el vínculo. Vuelve a intentarlo.');
  }
}
