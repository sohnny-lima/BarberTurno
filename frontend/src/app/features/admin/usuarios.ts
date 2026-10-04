import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { finalize, map, Subscription } from 'rxjs';
import { UsuariosApi } from '../../core/api/usuarios-api';
import { Rol } from '../../core/modelos/identidad';
import { UsuarioAdminDto } from '../../core/modelos/usuarios';
import { ConfirmarEstadoDialogo } from '../../shared/confirmar-estado-dialogo';
import { mostrarErrores } from '../../shared/formulario';
import { paginadorEspanol } from '../../shared/paginador-es';
import { PasswordTemporalDialogo } from './password-temporal-dialogo';
@Component({
  selector: 'app-usuarios',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: paginadorEspanol }],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Usuarios {
  private readonly api = inject(UsuariosApi);
  private readonly dialogos = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private peticion?: Subscription;
  private filtros: { q: string; rol?: Rol } = { q: '' };
  readonly busqueda = new FormControl('', { nonNullable: true });
  readonly rol = new FormControl<Rol | null>(null);
  readonly filas = signal<UsuarioAdminDto[]>([]);
  readonly cargando = signal(false);
  readonly operando = signal(false);
  readonly mensaje = signal('');
  readonly pagina = signal(0);
  readonly tamano = signal(10);
  readonly total = signal(0);
  constructor() {
    this.cargar();
  }
  buscar() {
    this.filtros = {
      q: this.busqueda.value.trim(),
      ...(this.rol.value ? { rol: this.rol.value } : {}),
    };
    this.pagina.set(0);
    this.cargar();
  }
  paginar(evento: PageEvent) {
    this.pagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.cargar();
  }
  cargar() {
    this.peticion?.unsubscribe();
    this.cargando.set(true);
    this.mensaje.set('');
    this.peticion = this.api
      .listar({ ...this.filtros, pagina: this.pagina(), tamano: this.tamano() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (p) => {
          this.filas.set(p.contenido);
          this.total.set(p.totalElementos);
        },
        error: (e) => {
          this.filas.set([]);
          this.total.set(0);
          this.mensaje.set(mostrarErrores(new FormGroup({}), e));
        },
      });
  }
  restablecer(usuario: UsuarioAdminDto) {
    if (this.operando()) return;
    this.operando.set(true);
    // Credencial local efímera: no forma parte de filas, señales, almacenamiento ni logs.
    let temporal = '';
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        data: {
          nombre: usuario.nombre,
          activar: true,
          titulo: 'Restablecer contraseña',
          texto:
            'Se invalidarán las sesiones abiertas y deberá cambiar la contraseña temporal al ingresar.',
          boton: 'Restablecer contraseña',
          cambiar: () =>
            this.api.restablecer(usuario.id).pipe(
              map((respuesta) => {
                temporal = respuesta.passwordTemporal;
                return 0;
              }),
            ),
        },
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((guardado) => {
        if (!guardado) {
          this.operando.set(false);
          return;
        }
        const dialogo = this.dialogos.open(PasswordTemporalDialogo, {
          data: temporal,
          disableClose: true,
          width: '480px',
          maxWidth: 'calc(100vw - 32px)',
        });
        temporal = '';
        dialogo
          .afterClosed()
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.operando.set(false);
            this.cargar();
          });
      });
  }
  cambiarEstado(usuario: UsuarioAdminDto) {
    if (this.operando()) return;
    this.operando.set(true);
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        data: {
          nombre: usuario.nombre,
          activar: !usuario.activo,
          texto:
            'Este cambio afecta el acceso de la cuenta. La agenda del profesional se administra en Barberos.',
          cambiar: () => this.api.cambiarEstado(usuario.id, !usuario.activo).pipe(map(() => 0)),
        },
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((guardado) => {
        this.operando.set(false);
        if (guardado) this.cargar();
      });
  }
}
