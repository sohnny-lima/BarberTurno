import { BreakpointObserver } from '@angular/cdk/layout';
import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { fechaCivil, fechaDatepicker, limitesDatepicker } from '../../core/tiempo/fecha-datepicker';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { ReservaStore } from './reserva.store';

@Component({
  selector: 'app-reservar',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatStepperModule,
    FechaLimaPipe,
    RouterLink,
  ],
  providers: [ReservaStore, provideNativeDateAdapter()],
  templateUrl: './reservar.html',
  styleUrl: './reservar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Reservar {
  readonly store = inject(ReservaStore);
  private readonly stepper = viewChild(MatStepper);
  readonly limites = limitesDatepicker();
  readonly fechaControl = new FormControl<Date | null>(fechaDatepicker(this.store.fecha()));
  private readonly destroyRef = inject(DestroyRef);
  readonly movil = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 767.98px)')
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  constructor() {
    const snackbar = inject(MatSnackBar);
    // Material comprueba el paso anterior: sincroniza después de actualizar completed.
    afterRenderEffect(() => {
      const stepper = this.stepper();
      const paso = this.store.paso();
      if (stepper && stepper.selectedIndex !== paso) stepper.selectedIndex = paso;
    });
    inject(ActivatedRoute)
      .queryParamMap.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const texto = params.get('reprogramar');
        const id = texto === null ? undefined : Number(texto);
        if (id !== undefined && (!Number.isSafeInteger(id) || id <= 0)) {
          this.store.mensaje.set('La referencia de la reserva no es válida.');
          return;
        }
        this.store.inicializar(id);
      });
    effect(() => {
      this.fechaControl.setValue(fechaDatepicker(this.store.fecha()), { emitEvent: false });
      if (this.store.enviando()) this.fechaControl.disable({ emitEvent: false });
      else this.fechaControl.enable({ emitEvent: false });
    });
    effect(() => {
      if (this.store.resultado())
        snackbar.open(
          this.store.reserva() ? 'Reserva reprogramada.' : 'Reserva creada.',
          'Cerrar',
          { duration: 6000 },
        );
    });
  }
  nombreBarbero(id: number) {
    return (
      this.store.barberos().find((b) => b.id === id)?.nombre ??
      this.store.reserva()?.barbero.nombre ??
      'Profesional'
    );
  }
  cambiarFecha() {
    const fecha = this.fechaControl.value;
    if (fecha && this.fechaControl.valid && !Number.isNaN(fecha.getTime()))
      this.store.elegirFecha(fechaCivil(fecha));
  }
  avanzar() {
    this.store.irPaso(1);
    if (!this.store.franjas().length && !this.store.consultando()) this.store.cargarFranjas();
  }
}
