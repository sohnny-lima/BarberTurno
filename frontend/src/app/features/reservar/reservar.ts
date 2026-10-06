import { BreakpointObserver } from '@angular/cdk/layout';
import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
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
import { SelectorCliente } from '../../shared/selector-cliente';
import { ReservaStore } from './reserva.store';
import { sumarDias } from '../../core/tiempo/semana-lima';

@Component({
  selector: 'app-reservar',
  imports: [
    ReactiveFormsModule,
    SelectorCliente,
    MatButtonModule,
    MatCardModule,
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

  readonly numeroPaso = computed(() => this.store.paso() + (this.store.asistida() ? 2 : 1));
  readonly pasos = computed(() =>
    Array.from({ length: this.store.asistida() ? 4 : 3 }, (_, i) => i + 1),
  );
  readonly dias = Array.from({ length: 7 }, (_, i) =>
    sumarDias(fechaCivil(this.limites.min), i),
  ).filter((dia) => dia <= fechaCivil(this.limites.max));
  readonly gruposHoras = computed(() => {
    const hora = new Intl.DateTimeFormat('es-PE', {
      timeZone: 'America/Lima',
      hour: '2-digit',
      hourCycle: 'h23',
    });
    return [
      {
        nombre: 'Mañana',
        franjas: this.store.franjas().filter((f) => Number(hora.format(new Date(f.inicio))) < 12),
      },
      {
        nombre: 'Tarde',
        franjas: this.store.franjas().filter((f) => Number(hora.format(new Date(f.inicio))) >= 12),
      },
    ];
  });
  fechaLegible(fecha: string, formato: 'larga' | 'dia' | 'numero' = 'larga') {
    const opciones: Intl.DateTimeFormatOptions =
      formato === 'larga'
        ? { weekday: 'long', day: 'numeric', month: 'long' }
        : formato === 'dia'
          ? { weekday: 'short' }
          : { day: 'numeric' };
    return new Intl.DateTimeFormat('es-PE', { ...opciones, timeZone: 'America/Lima' }).format(
      new Date(fecha + 'T12:00:00-05:00'),
    );
  }
  elegirDia(fecha: string, evento: Event) {
    this.store.elegirFecha(fecha);
    (evento.currentTarget as HTMLElement).scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }
  iniciales(nombre: string) {
    return nombre
      .split(' ')
      .map((parte) => parte[0])
      .slice(0, 2)
      .join('');
  }
  constructor() {
    const snackbar = inject(MatSnackBar);
    // Material comprueba el paso anterior: sincroniza después de actualizar completed.
    afterRenderEffect(() => {
      const stepper = this.stepper();
      const paso = Math.max(0, this.store.paso() + (this.store.asistida() ? 1 : 0));
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
