import { TestBed } from '@angular/core/testing';
import { DiaSemana } from './dia-semana';
import { EditorSemana } from './editor-semana';

describe('Editor semanal', () => {
  const lunes = { diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' };
  let editor: EditorSemana;
  beforeEach(() => {
    editor = new EditorSemana();
  });
  it('añade y quita intervalos sin afectar los de otros días', () => {
    editor.cargar([lunes]);
    editor.agregar(2);
    expect(editor.delDia(2)).toHaveLength(1);
    expect(editor.formulario.invalid).toBe(true);
    editor.quitar(editor.delDia(2)[0]);
    expect(editor.dto()).toEqual([lunes]);
  });
  it.each([
    ['09:00', '09:00'],
    ['13:00', '09:00'],
  ])('rechaza inicio %s y fin %s', (horaInicio, horaFin) => {
    editor.cargar([{ ...lunes, horaInicio, horaFin }]);
    expect(editor.formulario.invalid).toBe(true);
  });
  it('detecta solapes aun desordenados y permite contiguos y días distintos', () => {
    editor.cargar([lunes, { ...lunes, horaInicio: '08:00', horaFin: '10:00' }]);
    expect(editor.intervalos.getError('servidor')).toContain('solapados');
    editor.cargar([
      lunes,
      { ...lunes, horaInicio: '13:00', horaFin: '18:00' },
      { ...lunes, diaSemana: 2 },
    ]);
    expect(editor.formulario.valid).toBe(true);
  });
  it('copia lunes a martes–sábado, reemplaza sus intervalos y conserva domingo', () => {
    editor.cargar([
      lunes,
      { ...lunes, horaInicio: '14:00', horaFin: '18:00' },
      { ...lunes, diaSemana: 2, horaFin: '10:00' },
      { ...lunes, diaSemana: 7 },
    ]);
    editor.copiarLunes();
    for (let dia = 1; dia <= 6; dia++) {
      expect(editor.delDia(dia).map((c) => c.getRawValue())).toEqual([
        { ...lunes, diaSemana: dia },
        { ...lunes, diaSemana: dia, horaInicio: '14:00', horaFin: '18:00' },
      ]);
    }
    editor.delDia(2)[0].controls.horaInicio.setValue('10:00');
    expect(editor.delDia(1)[0].controls.horaInicio.value).toBe('09:00');
    expect(editor.delDia(7)).toHaveLength(1);
  });
  it('copia un lunes libre y permite guardar una semana vacía', () => {
    editor.cargar([{ ...lunes, diaSemana: 3 }]);
    editor.copiarLunes();
    expect(editor.dto()).toEqual([]);
    expect(editor.formulario.valid).toBe(true);
  });
  it('mapea DTO y editor de vuelta conservando índices, horas y días libres', () => {
    const semana = [
      { ...lunes, diaSemana: 7 },
      lunes,
      { ...lunes, horaInicio: '14:00', horaFin: '18:00' },
    ];
    editor.cargar(semana);
    expect(editor.dto()).toEqual(semana);
    editor.cargar(editor.dto());
    expect(editor.dto()).toEqual(semana);
    expect(editor.delDia(3)).toEqual([]);
  });
});

describe('Presentación del día del editor semanal', () => {
  it('mantiene los nombres accesibles exactos de todos los intervalos dentro de mat-label', () => {
    const editor = new EditorSemana();
    for (let diaSemana = 1; diaSemana <= 7; diaSemana++) {
      editor.cargar([
        { diaSemana, horaInicio: '09:00', horaFin: '13:00' },
        { diaSemana, horaInicio: '14:00', horaFin: '18:00' },
      ]);
      const dia = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'][
        diaSemana - 1
      ];
      const f = TestBed.createComponent(DiaSemana);
      f.componentRef.setInput('dia', dia);
      f.componentRef.setInput('filas', editor.delDia(diaSemana));
      f.detectChanges();
      const raiz: HTMLElement = f.nativeElement;
      const campos = Array.from(raiz.querySelectorAll<HTMLInputElement>('input'));
      expect(campos).toHaveLength(4);
      for (const [i, campo] of campos.entries()) {
        expect(campo.hasAttribute('aria-label')).toBe(false);
        const etiqueta =
          campo.labels?.item(0) ??
          raiz.querySelector<HTMLElement>('[id="' + campo.getAttribute('aria-labelledby') + '"]')!;
        expect(etiqueta.querySelector('mat-label')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
          dia + ' · ' + (i % 2 ? 'Fin ' : 'Inicio ') + (Math.floor(i / 2) + 1),
        );
        expect(etiqueta.querySelectorAll('mat-label .sr-only')).toHaveLength(2);
        expect(campo.value).toBe(['09:00', '13:00', '14:00', '18:00'][i]);
      }
      expect(raiz.querySelector('h3')?.textContent).toBe(dia);
      expect(raiz.querySelectorAll('.intervalo')).toHaveLength(2);
      for (let i = 1; i <= 2; i++) {
        const quitar = raiz.querySelector<HTMLButtonElement>(
          '[aria-label="Quitar intervalo ' + i + ' del ' + dia + '"]',
        )!;
        expect(quitar.textContent).toContain('Quitar');
        expect(quitar.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
      }
      f.destroy();
    }
  });
  it('delega añadir y quitar al mismo editor y conserva el vacío de día libre', () => {
    const editor = new EditorSemana();
    const f = TestBed.createComponent(DiaSemana);
    f.componentRef.setInput('dia', 'Lunes');
    f.componentRef.setInput('filas', editor.delDia(1));
    f.componentInstance.agregar.subscribe(() => editor.agregar(1));
    f.componentInstance.quitar.subscribe((fila) => editor.quitar(fila));
    f.detectChanges();
    const raiz: HTMLElement = f.nativeElement;
    expect(raiz.querySelector('.libre')?.textContent).toBe('Sin intervalos.');
    raiz.querySelector<HTMLButtonElement>('[aria-label="Añadir intervalo al Lunes"]')!.click();
    f.componentRef.setInput('filas', editor.delDia(1));
    f.detectChanges();
    expect(editor.delDia(1)).toHaveLength(1);
    expect(raiz.querySelector('.libre')).toBeNull();
    raiz.querySelector<HTMLButtonElement>('[aria-label="Quitar intervalo 1 del Lunes"]')!.click();
    f.componentRef.setInput('filas', editor.delDia(1));
    f.detectChanges();
    expect(editor.dto()).toEqual([]);
    expect(raiz.querySelector('.libre')?.textContent).toBe('Sin intervalos.');
  });
});
