package pe.barberturno.support;

import java.math.BigDecimal;
import java.time.Instant;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import pe.barberturno.users.UsuarioRepository;

/** Fábrica reutilizable de datos ficticios; no necesita una API ni servicios de negocio. */
public final class DatosPrueba {

    public static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");

    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final ServicioRepository servicios;

    public DatosPrueba(UsuarioRepository usuarios, BarberoRepository barberos, ServicioRepository servicios) {
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.servicios = servicios;
    }

    public Usuario cliente(String identificador) {
        return usuarios.saveAndFlush(new Usuario("Cliente ficticio", identificador + "@ejemplo.test",
                "999000001", "hash-ficticio-no-utilizable", Rol.CLIENTE, AHORA, AHORA));
    }

    public Barbero barbero(String identificador) {
        Usuario usuario = usuarios.saveAndFlush(new Usuario("Barbero ficticio",
                "barbero-" + identificador + "@ejemplo.test", null, "hash-ficticio-no-utilizable",
                Rol.BARBERO, null, AHORA));
        return barberos.saveAndFlush(new Barbero(usuario, "Cortes", AHORA));
    }

    public Servicio servicio() {
        return servicios.saveAndFlush(new Servicio("Corte de prueba", "Descripción ficticia",
                (short) 30, new BigDecimal("20.00"), AHORA));
    }
}