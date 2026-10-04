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
