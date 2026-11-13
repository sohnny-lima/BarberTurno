import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Rol, UsuarioSesionDto } from '../modelos/identidad';
import { NegocioActual } from './negocio-actual';

/** Fixture explícito de barbería para las pruebas heredadas; la identidad global no autoriza. */
export function prepararNegocio(rol: Rol | null = null) {
  const negocio = TestBed.inject(NegocioActual);
  negocio.seleccionar('prueba');
  negocio.cargar().subscribe();
  const http = TestBed.inject(HttpTestingController);
  http.expectOne('/api/n/prueba/sesion').flush(usuarioNegocio(rol));
  http.expectOne('/api/n/prueba/negocio').flush({
    slug: 'prueba',
    nombre: 'Barbería ficticia',
    horizonteDias: 30,
    anticipacionCambioClienteMin: 120,
    limiteReservasActivas: 3,
    confirmacionManual: false,
  });
  return negocio;
}
export function usuarioNegocio(rol: Rol | null): UsuarioSesionDto {
  return {
    id: 9,
    nombre: 'Ficticio',
    correo: 'prueba@ejemplo.test',
    rol,
    barberoId: rol === 'BARBERO' ? 3 : null,
    debeCambiarPassword: false,
  };
}
