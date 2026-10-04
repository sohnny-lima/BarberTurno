import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { JornadaDto } from '../../core/modelos/horarios';

export const ordenHoras: ValidatorFn = (grupo: AbstractControl) => {
  const inicio = grupo.get('horaInicio')?.value;
  const fin = grupo.get('horaFin')?.value;
  return inicio && fin && inicio >= fin
    ? { servidor: 'El fin debe ser posterior al inicio.' }
    : null;
};
const sinSolapes: ValidatorFn = (lista: AbstractControl) => {
  const intervalos = lista.value as JornadaDto[];
  return intervalos.some((a, i) =>
    intervalos.some(
      (b, j) =>
        i < j &&
        a.diaSemana === b.diaSemana &&
        a.horaInicio < b.horaFin &&
        b.horaInicio < a.horaFin,
    ),
  )
    ? { servidor: 'Hay intervalos solapados en el mismo día. Revise la semana.' }
    : null;
};
export function intervalo(datos: JornadaDto) {
  return new FormGroup(
    {
      diaSemana: new FormControl(datos.diaSemana, { nonNullable: true }),
      horaInicio: new FormControl(datos.horaInicio, {
        nonNullable: true,
        validators: Validators.required,
      }),
      horaFin: new FormControl(datos.horaFin, {
        nonNullable: true,
        validators: Validators.required,
      }),
    },
    { validators: ordenHoras },
  );
}
export class EditorSemana {
  readonly intervalos = new FormArray<ReturnType<typeof intervalo>>([], { validators: sinSolapes });
  readonly formulario = new FormGroup({ intervalos: this.intervalos });
  cargar(semana: JornadaDto[]) {
    this.intervalos.clear();
    for (const datos of semana) this.intervalos.push(intervalo(datos));
    this.formulario.markAsPristine();
    this.formulario.markAsUntouched();
  }
  dto(): JornadaDto[] {
    return this.intervalos.getRawValue();
  }
  delDia(dia: number) {
    return this.intervalos.controls.filter((control) => control.controls.diaSemana.value === dia);
  }
  agregar(dia: number) {
    this.intervalos.push(intervalo({ diaSemana: dia, horaInicio: '', horaFin: '' }));
  }
  quitar(control: ReturnType<typeof intervalo>) {
    this.intervalos.removeAt(this.intervalos.controls.indexOf(control));
  }
  copiarLunes() {
    const lunes = this.dto().filter((fila) => fila.diaSemana === 1);
    const domingo = this.dto().filter((fila) => fila.diaSemana === 7);
    this.cargar([
      ...Array.from({ length: 6 }, (_, i) =>
        lunes.map((fila) => ({ ...fila, diaSemana: i + 1 })),
      ).flat(),
      ...domingo,
    ]);
  }
}
