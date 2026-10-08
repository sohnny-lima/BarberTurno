import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { catchError, EMPTY, finalize, switchMap, timer } from 'rxjs';
import { UsuariosApi } from '../core/api/usuarios-api';
import { UsuarioAdminDto } from '../core/modelos/usuarios';
import { mostrarErrores } from './formulario';
/** Búsqueda de presentación; el servidor valida rol, estado y disponibilidad al reservar. */
@Component({
  selector: 'app-selector-cliente',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  templateUrl: './selector-cliente.html',
  styleUrl: './selector-cliente.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectorCliente {
  private readonly api = inject(UsuariosApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly deshabilitado = input(false);
  readonly elegido = output<UsuarioAdminDto | null>();
  readonly control = new FormControl<string | UsuarioAdminDto>('');
  readonly resultados = signal<UsuarioAdminDto[]>([]);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly total = signal(0);
  readonly seleccionVista = signal<UsuarioAdminDto | null>(null);
  readonly nombre = (usuario: UsuarioAdminDto | string | null) =>
    typeof usuario === 'string' ? usuario : (usuario?.nombre ?? '');
  constructor() {
    this.control.valueChanges
      .pipe(
        switchMap((valor) => {
          this.resultados.set([]);
          this.total.set(0);
          this.mensaje.set('');
          if (typeof valor !== 'string') return EMPTY;
          this.seleccionVista.set(null);
          this.elegido.emit(null);
          if (!valor.trim()) return EMPTY;
          return timer(250).pipe(
            switchMap(() => {
              this.cargando.set(true);
              return this.api
                .listar({ q: valor.trim(), rol: 'CLIENTE', pagina: 0, tamano: 20 })
                .pipe(
                  catchError((error) => {
                    this.mensaje.set(mostrarErrores(new FormGroup({}), error));
                    return EMPTY;
                  }),
                  finalize(() => this.cargando.set(false)),
                );
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina) => {
        this.resultados.set(pagina.contenido);
        this.total.set(pagina.totalElementos);
        if (!pagina.totalElementos) this.mensaje.set('No se encontraron clientes.');
      });
  }
  iniciales(nombre: string) {
    return nombre
      .split(' ')
      .map((parte) => parte[0])
      .slice(0, 2)
      .join('');
  }
  seleccionar(usuario: UsuarioAdminDto) {
    if (!this.deshabilitado()) {
      this.seleccionVista.set(usuario);
      this.elegido.emit(usuario);
    }
  }
}
