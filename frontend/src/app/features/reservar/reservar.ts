import { BreakpointObserver } from '@angular/cdk/layout';
import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
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
import { fechaPresentacion } from '../../shared/fecha-presentacion';
import { SelectorCliente } from '../../shared/selector-cliente';
import { TituloPagina } from '../../shared/titulo-pagina';
import { ReservaOpciones } from './reserva-opciones';
import { ReservaResumen } from './reserva-resumen';
import { ReservaEsqueleto } from './reserva-esqueleto';
import { ReservaStore } from './reserva.store';
import { sumarDias } from '../../core/tiempo/semana-lima';

@Component({
  selector: 'app-reservar',
  imports: [
    ReactiveFormsModule,
    ReservaOpciones,
    ReservaResumen,
    ReservaEsqueleto,
    SelectorCliente,
    MatButtonModule,
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

  readonly titulo = computed(() =>
    this.store.reserva()
      ? 'Reprogramar cita'
      : this.store.asistida()
        ? 'Reserva asistida'
        : this.store.paso() === 2
          ? 'Revise su turno'
          : 'Reservar un turno',
  );
  readonly subtitulo = computed(() =>
    this.store.asistida()
      ? 'Reserve en nombre de un cliente registrado.'
      : this.store.paso() === 2
        ? 'Compruebe los datos antes de confirmar.'
        : this.store.paso() === 1
          ? 'Elija el día y la hora.'
          : 'Elija servicio, día y hora. Pago presencial en la barbería.',
  );
  readonly fechaCompleta = (fecha: string) =>
    new Intl.DateTimeFormat('es-PE', {
      timeZone: 'America/Lima',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date(fecha + 'T12:00:00-05:00'));
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
  readonly fechaLegible = fechaPresentacion;
  elegirDia(fecha: string, evento: Event) {
    this.store.elegirFecha(fecha);
    (evento.currentTarget as HTMLElement).scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }
  constructor() {
    const tituloPagina = inject(TituloPagina);
    effect(() => tituloPagina.texto.set(this.titulo()));
    this.destroyRef.onDestroy(() => tituloPagina.texto.set(null));
    const snackbar = inject(MatSnackBar);
    const elemento = inject<ElementRef<HTMLElement>>(ElementRef);
    // La primera presentación conserva el foco; una selección restaurada cambia este índice.
    let pasoEnfocado = 0;
    let fotograma: number | undefined;
    this.destroyRef.onDestroy(() => {
      if (fotograma !== undefined) cancelAnimationFrame(fotograma);
    });
    // Material comprueba el paso anterior: sincroniza después de actualizar completed.
    afterRenderEffect(() => {
      const stepper = this.stepper();
      const paso = Math.max(0, this.store.paso() + (this.store.asistida() ? 1 : 0));
      if (!stepper) return;
      if (stepper.selectedIndex !== paso) stepper.selectedIndex = paso;
      if (pasoEnfocado === paso) return;
      pasoEnfocado = paso;
      if (fotograma !== undefined) cancelAnimationFrame(fotograma);
      // Espera el marcado del nuevo paso, también al restaurar o elegir una hora desde el store.
      fotograma = requestAnimationFrame(() => {
        elemento.nativeElement.querySelector<HTMLElement>('[data-paso="' + paso + '"]')?.focus();
      });
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
  reintentar() {
    this.store.mensaje.set('');
    this.store.cargarFranjas();
  }
  avanzar() {
    this.store.irPaso(1);
    if (!this.store.franjas().length && !this.store.consultando()) this.store.cargarFranjas();
  }
}
