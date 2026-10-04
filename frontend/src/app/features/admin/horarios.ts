import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  FormGroupDirective,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, EMPTY, finalize, forkJoin, map, Observable, switchMap, tap } from 'rxjs';
import { BarberosApi } from '../../core/api/barberos-api';
import { HorariosApi } from '../../core/api/horarios-api';
import { BarberoDto } from '../../core/modelos/catalogo';
import { BloqueoDto, ConflictoHorarios } from '../../core/modelos/horarios';
import { FechaLimaPipe } from '../../core/tiempo/fecha-lima-pipe';
import { fechaHoyLima, instanteLima } from '../../core/tiempo/instante-lima';
import { ConfirmarEstadoDialogo } from '../../shared/confirmar-estado-dialogo';
import { errorCampo, mostrarErrores } from '../../shared/formulario';
import { EditorSemana, ordenHoras } from './editor-semana';

@Component({
  selector: 'app-horarios',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    FechaLimaPipe,
  ],
  templateUrl: './horarios.html',
  styleUrls: ['./catalogo.scss', './horarios.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Horarios {
  private readonly api = inject(HorariosApi);
  private readonly barberosApi = inject(BarberosApi);
  private readonly dialogos = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly barberos = signal<BarberoDto[]>([]);
  readonly bloqueos = signal<BloqueoDto[]>([]);
  readonly ocupado = signal(false);
  readonly listo = signal(false);
  readonly mensaje = signal('');
  readonly conflictos = signal<{ barbero: string; reservas: number[] }[]>([]);
  readonly seleccion = new FormControl<number | null>(null);
  readonly editor = new EditorSemana();
  readonly dias = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
  readonly errorCampo = errorCampo;
  readonly rango = new FormGroup({
    desde: new FormControl(fechaHoyLima(), { nonNullable: true, validators: Validators.required }),
    hasta: new FormControl(fechaHoyLima(30), {
      nonNullable: true,
      validators: Validators.required,
    }),
  });
  readonly alta = new FormGroup(
    {
      fecha: new FormControl(fechaHoyLima(), {
        nonNullable: true,
        validators: Validators.required,
      }),
      horaInicio: new FormControl('', { nonNullable: true, validators: Validators.required }),
      horaFin: new FormControl('', { nonNullable: true, validators: Validators.required }),
      motivo: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(3), Validators.maxLength(200)],
      }),
      todos: new FormControl(false, { nonNullable: true }),
    },
    { validators: ordenHoras },
  );
  constructor() {
    effect(() => {
      for (const control of [this.seleccion, this.alta.controls.todos]) {
        if (this.ocupado()) control.disable({ emitEvent: false });
        else control.enable({ emitEvent: false });
      }
    });
    this.ocupado.set(true);
    this.barberosApi
      .listar(true)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (filas) => this.barberos.set(filas),
        error: (error) => this.fallo(new FormGroup({}), error),
      });
  }
  private ejecutar<T>(
    peticion: Observable<T>,
    siguiente: (valor: T) => void,
    formulario: FormGroup = new FormGroup({}),
  ) {
    this.ocupado.set(true);
    this.mensaje.set('');
    this.conflictos.set([]);
    peticion
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({ next: siguiente, error: (error) => this.fallo(formulario, error) });
  }
  cargarBarbero() {
    if (this.ocupado()) return;
    this.listo.set(false);
    this.editor.cargar([]);
    this.bloqueos.set([]);
    const id = this.seleccion.value;
    if (id === null) return;
    const { desde, hasta } = this.rango.getRawValue();
    this.ejecutar(
      forkJoin({ semana: this.api.jornadas(id), bloqueos: this.api.bloqueos(id, desde, hasta) }),
      (datos) => {
        this.editor.cargar(datos.semana);
        this.bloqueos.set(datos.bloqueos);
        this.alta.reset({
          fecha: fechaHoyLima(),
          horaInicio: '',
          horaFin: '',
          motivo: '',
          todos: false,
        });
        this.listo.set(true);
      },
      this.rango,
    );
  }
  guardarSemana() {
    if (this.ocupado() || !this.listo()) return;
    this.editor.formulario.markAllAsTouched();
    if (this.editor.formulario.invalid) return;
    this.ejecutar(
      this.api.guardarSemana(this.seleccion.value!, this.editor.dto()),
      (semana) => {
        this.editor.cargar(semana);
        this.mensaje.set('Semana guardada.');
      },
      this.editor.formulario,
    );
  }
  cargarBloqueos() {
    if (this.ocupado() || !this.listo()) return;
    this.rango.markAllAsTouched();
    if (this.rango.invalid) return;
    const { desde, hasta } = this.rango.getRawValue();
    this.bloqueos.set([]);
    this.ejecutar(
      this.api.bloqueos(this.seleccion.value!, desde, hasta),
      (filas) => this.bloqueos.set(filas),
      this.rango,
    );
  }
  crearBloqueo(directiva?: FormGroupDirective) {
    if (this.ocupado() || !this.listo()) return;
    this.alta.markAllAsTouched();
    if (this.alta.invalid) return;
    const datos = this.alta.getRawValue();
    const cuerpo = {
      inicio: instanteLima(datos.fecha, datos.horaInicio),
      fin: instanteLima(datos.fecha, datos.horaFin),
      motivo: datos.motivo,
    };
    const ids = this.barberos()
      .filter((b) => b.activo)
      .map((b) => b.id);
    if (datos.todos && !ids.length) {
      this.mensaje.set('No hay barberos activos.');
      return;
    }
    const peticion: Observable<unknown> = datos.todos
      ? this.api.crearLote({ ...cuerpo, barberoIds: ids })
      : this.api.crearBloqueo(this.seleccion.value!, cuerpo);
    const { desde, hasta } = this.rango.getRawValue();
    this.ejecutar(
      peticion.pipe(
        tap(() => {
          this.alta.reset({
            fecha: datos.fecha,
            horaInicio: '',
            horaFin: '',
            motivo: '',
            todos: false,
          });
          directiva?.resetForm(this.alta.getRawValue());
          this.bloqueos.set([]);
        }),
        switchMap(() =>
          this.api.bloqueos(this.seleccion.value!, desde, hasta).pipe(
            catchError((error) => {
              this.mensaje.set(
                'Bloqueo creado. No se pudo actualizar la lista; consulte nuevamente. ' +
                  mostrarErrores(this.rango, error),
              );
              return EMPTY;
            }),
          ),
        ),
      ),
      (filas) => {
        this.bloqueos.set(filas);
        this.mensaje.set('Bloqueo creado. La lista muestra únicamente el rango consultado.');
      },
      this.alta,
    );
  }
  eliminar(bloqueo: BloqueoDto) {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        data: {
          nombre: bloqueo.motivo,
          activar: false,
          titulo: 'Eliminar bloqueo',
          texto:
            '¿Desea eliminar este bloqueo? El intervalo volverá a estar disponible según la jornada y las reservas.',
          boton: 'Eliminar',
          cambiar: () => this.api.eliminarBloqueo(bloqueo.id).pipe(map(() => 0)),
        },
      })
      .afterClosed()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe((eliminado) => {
        if (eliminado) this.bloqueos.update((filas) => filas.filter((b) => b.id !== bloqueo.id));
      });
  }
  private fallo(formulario: FormGroup, error: HttpErrorResponse) {
    const problema = error.error as Partial<ConflictoHorarios> | null;
    const errores = problema?.errores?.map((detalle) => ({
      ...detalle,
      campo:
        formulario === this.editor.formulario
          ? detalle.campo.replace(/^\[(\d+)\]/, 'intervalos.$1')
          : formulario === this.alta
            ? ({ inicio: 'horaInicio', fin: 'horaFin' }[detalle.campo] ?? detalle.campo)
            : detalle.campo,
    }));
    this.mensaje.set(
      mostrarErrores(
        formulario,
        new HttpErrorResponse({ status: error.status, error: { ...problema, errores } }),
      ),
    );
    if (
      error.status === 409 &&
      problema?.codigo === 'CONFLICTO_CON_RESERVAS' &&
      problema.reservas
    ) {
      const grupos = Array.isArray(problema.reservas)
        ? { [this.seleccion.value!]: problema.reservas }
        : problema.reservas;
      this.conflictos.set(
        Object.entries(grupos).map(([id, reservas]) => ({
          barbero: this.barberos().find((b) => b.id === Number(id))?.nombre ?? 'Barbero ' + id,
          reservas,
        })),
      );
    }
  }
}
