import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Router, RouterLink } from '@angular/router';
import { finalize, map } from 'rxjs';
import { EquipoApi } from '../../core/api/equipo-api';
import { EquipoDto } from '../../core/modelos/gestion';
import { NOMBRES_PAPEL, RolPersonal } from '../../core/modelos/negocios';
import { NegocioActual } from '../../core/negocio/negocio-actual';
import { CabeceraPagina } from '../../shared/cabecera-pagina';
import { ConfirmarEstadoDialogo } from '../../shared/confirmar-estado-dialogo';
import { EstadoContenido } from '../../shared/estado-contenido';
import { mostrarErrores } from '../../shared/formulario';
import { ListaAdaptable } from '../../shared/lista-adaptable';

@Component({
  selector: 'app-equipo',
  imports: [
    NgTemplateOutlet,
    MatButtonModule,
    MatMenuModule,
    MatSlideToggleModule,
    RouterLink,
    CabeceraPagina,
    EstadoContenido,
    ListaAdaptable,
  ],
  providers: [EquipoApi],
  templateUrl: './equipo.html',
  styleUrls: ['../admin/catalogo-lista.scss', './gestion.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Equipo {
  readonly negocio = inject(NegocioActual);
  private readonly api = inject(EquipoApi);
  private readonly dialogos = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly filas = signal<EquipoDto[]>([]);
  readonly revocadas = signal(false);
  readonly cargando = signal(false);
  readonly mensaje = signal('');
  readonly nombres = NOMBRES_PAPEL;
  readonly roles: RolPersonal[] = ['PROPIETARIO', 'ENCARGADO', 'BARBERO'];
  readonly propietario = computed(() => this.negocio.usuario()?.rolNegocio === 'PROPIETARIO');
  readonly visibles = computed(() =>
    this.filas()
      .filter((f) => this.revocadas() || f.estado === 'ACTIVA')
      .sort((a, b) => Number(a.estado === 'REVOCADA') - Number(b.estado === 'REVOCADA')),
  );
  readonly propia = (fila: EquipoDto) => fila.usuarioId === this.negocio.usuario()?.id;
  readonly puedeRevocar = (fila: EquipoDto) =>
    this.propietario() ||
    (this.negocio.usuario()?.rolNegocio === 'ENCARGADO' && fila.rol === 'BARBERO');
  readonly motivo = (fila: EquipoDto) =>
    fila.motivoRevocacion
      ? {
          SALIDA: 'Salió del negocio',
          DECISION_GESTOR: 'Retirado por la gestión',
          PERFIL_DESACTIVADO: 'Perfil desactivado',
          PLATAFORMA: 'Retirado por la plataforma',
        }[fila.motivoRevocacion]
      : '—';
  constructor() {
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.mensaje.set('');
    this.api
      .listar()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (filas) => this.filas.set(filas),
        error: (error) => this.mensaje.set(mostrarErrores(new FormGroup({}), error)),
      });
  }
  cambiarRol(fila: EquipoDto, rol: RolPersonal) {
    this.confirmar(
      fila,
      'Cambiar rol',
      'Cambiar el rol de ' + fila.nombre + ' a ' + this.nombres[rol] + '.',
      () => this.api.cambiarRol(fila.id, rol),
    );
  }
  revocar(fila: EquipoDto) {
    this.confirmar(
      fila,
      'Revocar acceso',
      'Se retirará el acceso de ' +
        fila.nombre +
        ' a esta barbería y se desactivará su perfil profesional, si lo tiene.',
      () => this.api.revocar(fila.id),
    );
  }
  salir(fila: EquipoDto) {
    this.confirmar(
      fila,
      'Salir de la barbería',
      'Perderá el acceso a esta barbería. Para volver necesitará una nueva invitación.',
      () => this.api.salir(),
      true,
    );
  }
  private confirmar(
    fila: EquipoDto,
    titulo: string,
    texto: string,
    cambiar: () => import('rxjs').Observable<void>,
    salida = false,
  ) {
    const propia = this.propia(fila);
    const nombre = this.negocio.nombre();
    this.dialogos
      .open(ConfirmarEstadoDialogo, {
        width: '480px',
        maxWidth: 'calc(100vw - 32px)',
        maxHeight: 'calc(100dvh - 32px)',
        data: {
          nombre: fila.nombre,
          titulo,
          texto,
          boton: titulo,
          activar: true,
          riesgo: true,
          cambiar: () => cambiar().pipe(map(() => 0)),
        },
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((hecho) => {
        if (!hecho) return;
        if (propia && (salida || titulo === 'Revocar acceso')) {
          this.negocio.avisoSelector.set('Ya no tiene acceso a ' + nombre + '.');
          void this.router.navigate(['/negocios']);
        } else if (propia) {
          // La guarda recarga la sesión antes de crear Agenda. Cargar desde esta
          // vista la destruiría al mostrar el estado de carga del contenedor.
          void this.router.navigateByUrl(this.negocio.enlace('agenda'));
        } else this.cargar();
      });
  }
}
