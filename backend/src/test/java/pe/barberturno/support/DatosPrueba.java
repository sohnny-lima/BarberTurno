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
import pe.barberturno.platform.*;
import pe.barberturno.customers.*;

/** Fábrica reutilizable de datos ficticios; no necesita una API ni servicios de negocio. */
public final class DatosPrueba {

    public static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");

    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final ServicioRepository servicios;
    private final MembresiaRepository membresias;
    private final ClienteRepository clientes;

    public DatosPrueba(UsuarioRepository usuarios, BarberoRepository barberos, ServicioRepository servicios,
            MembresiaRepository membresias, ClienteRepository clientes) {
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.servicios = servicios;
        this.membresias = membresias;
        this.clientes = clientes;
    }

    /** Traduce explícitamente los fixtures del negocio único de v1 a las pertenencias de v2. */
    public static Usuario guardarPersonalV1(UsuarioRepository usuarios, MembresiaRepository membresias, ClienteRepository clientes, Usuario usuario) {
        usuarios.saveAndFlush(usuario);
        if (usuario.getRol() != Rol.CLIENTE) {
            membresias.save(new Membresia(1L, usuario.getId(),
                    usuario.getRol() == Rol.ADMIN ? RolNegocio.PROPIETARIO : RolNegocio.BARBERO, usuario.getCreadoEn()));
        }
        else clientes.saveAndFlush(new Cliente(usuario, usuario.getCreadoEn()));
        return usuario;
    }

    public Usuario cliente(String identificador) {
        return pe.barberturno.support.DatosPrueba.guardarPersonalV1(usuarios, membresias, clientes, new Usuario("Cliente ficticio", identificador + "@ejemplo.test",
                "999000001", "hash-ficticio-no-utilizable", Rol.CLIENTE, AHORA, AHORA));
    }

    public Barbero barbero(String identificador) {
        Usuario usuario = pe.barberturno.support.DatosPrueba.guardarPersonalV1(usuarios, membresias, clientes, new Usuario("Barbero ficticio",
                "barbero-" + identificador + "@ejemplo.test", null, "hash-ficticio-no-utilizable",
                Rol.BARBERO, null, AHORA));
        return barberos.saveAndFlush(new Barbero(usuario, "Cortes", AHORA));
    }

    public Servicio servicio() {
        return servicios.saveAndFlush(new Servicio("Corte de prueba", "Descripción ficticia",
                (short) 30, new BigDecimal("20.00"), AHORA));
    }
}
