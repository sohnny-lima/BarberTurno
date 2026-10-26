package pe.barberturno.demo;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.auth.PoliticaPassword;
import pe.barberturno.catalog.*;
import pe.barberturno.common.config.ReglasNegocioService;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.customers.*;
import pe.barberturno.platform.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.users.*;

/**
 * Completa la demo académica con identidades ficticias y dos negocios para V2-07.0.
 * Solo crea lo ausente: conserva contraseñas, revocaciones y configuración tras reinicios.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
@Profile("demo & !prod")
public class DatosDemoMultinegocioService {
    private final EntityManager entidades;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final ClienteRepository clientes;
    private final BarberoRepository barberos;
    private final ServicioRepository servicios;
    private final JornadaRepository jornadas;
    private final ProveedorNegocioActual negocio;
    private final ReglasNegocioService reglas;
    private final PasswordEncoder passwords;
    private final Clock clock;

    /**
     * Recibe persistencia del escenario y dependencias compartidas con el dominio de reservas.
     * @param entidades SQL transaccional para dar de alta el negocio ficticio
     * @param usuarios cuentas globales ficticias
     * @param membresias pertenencias locales acotadas
     * @param clientes fichas de cliente por negocio
     * @param barberos perfiles y bloqueo previo del cupo
     * @param servicios catálogo mínimo de cada negocio
     * @param jornadas disponibilidad semanal de los perfiles nuevos
     * @param negocio ámbito explícito fijado antes de la transacción
     * @param reglas cupo vigente del negocio
     * @param passwords BCrypt sin claves predeterminadas
     * @param clock reloj único para las marcas temporales
     */
    public DatosDemoMultinegocioService(EntityManager entidades, UsuarioRepository usuarios,
            MembresiaRepository membresias, ClienteRepository clientes, BarberoRepository barberos,
            ServicioRepository servicios, JornadaRepository jornadas, ProveedorNegocioActual negocio,
            ReglasNegocioService reglas, PasswordEncoder passwords, Clock clock) {
        this.entidades = entidades; this.usuarios = usuarios; this.membresias = membresias;
        this.clientes = clientes; this.barberos = barberos; this.servicios = servicios;
        this.jornadas = jornadas; this.negocio = negocio; this.reglas = reglas;
        this.passwords = passwords; this.clock = clock;
    }

    /**
     * Crea sur con horizonte de 14 días y confirmación manual, además de las cinco cuentas del E2E.
     * El cliente de ambas barberías es la cuenta cliente de la demo académica ya cargada.
     * @param password BT_DEMO_PASSWORD externa; nunca se registra ni sustituye claves existentes
     * @throws IllegalStateException si la contraseña incumple RN-25
     */
    @Transactional
    public void preparar(String password) {
        if (password == null || !new PoliticaPassword().validar(password).isEmpty()) {
            throw new IllegalStateException("BT_DEMO_PASSWORD debe cumplir RN-25.");
        }
        entidades.createNativeQuery("""
                insert into negocio (slug, nombre, horizonte_dias, confirmacion_manual, creado_en, actualizado_en)
                values ('sur', 'Barbería Sur', 14, true, :ahora, :ahora) on conflict (slug) do nothing
                """).setParameter("ahora", clock.instant()).executeUpdate();
        String hash = passwords.encode(password);
        cuenta("plataforma", "Plataforma de demostración", hash, true);
        cuenta("multirol", "Personal de dos barberías", hash, false);
        cuenta("personal-cliente", "Personal y cliente de demostración", hash, false);
        cuenta("encargado", "Encargado de demostración", hash, false);
        cuenta("cliente", "Cliente de demostración", hash, false);
    }

    /**
     * Completa pertenencias y fichas en el ámbito explícito, con catálogo y jornadas para reservar.
     * Respeta ⓪ propietarios → ⓪ cupo → perfiles; nunca reactiva pertenencias o perfiles existentes.
     * @param inicial true para la barbería inicial; false exclusivamente para sur
     * @throws IllegalStateException si no se prepararon las cuentas o falta cupo profesional
     */
    @Transactional
    public void completar(boolean inicial) {
        long id = negocio.negocioActual().orElseThrow();
        membresias.bloquearPropietarios(id);
        barberos.bloquearLimiteActivos(id);
        var multirol = obtener("multirol");
        miembro(id, multirol, inicial ? RolNegocio.PROPIETARIO : RolNegocio.BARBERO);
        ficha(obtener("cliente"));
        if (inicial) {
            miembro(id, obtener("encargado"), RolNegocio.ENCARGADO);
            var personal = obtener("personal-cliente");
            miembro(id, personal, RolNegocio.BARBERO);
            ficha(personal);
            perfil(personal);
        } else {
            // Sur también conserva un propietario activo para ser operativa.
            miembro(id, obtener("encargado"), RolNegocio.PROPIETARIO);
            perfil(multirol);
        }
        if (servicios.buscarPorNombre(id, "Corte clásico").isEmpty()) {
            servicios.saveAndFlush(new Servicio("Corte clásico", "Corte y acabado", (short) 30,
                    new BigDecimal("25.00"), clock.instant()));
        }
    }

    private void cuenta(String alias, String nombre, String hash, boolean plataforma) {
        if (usuarios.findByCorreo(alias + "@ejemplo.test").isPresent()) return;
        var u = new Usuario(nombre, alias + "@ejemplo.test", "999000002", hash, Rol.CLIENTE, clock.instant(), clock.instant());
        if (plataforma) u.habilitarAdministracionPlataforma();
        usuarios.saveAndFlush(u);
    }
    private Usuario obtener(String alias) { return usuarios.findByCorreo(alias + "@ejemplo.test").orElseThrow(); }
    private void miembro(long id, Usuario u, RolNegocio rol) {
        if (membresias.findByNegocioIdAndUsuarioId(id, u.getId()).isEmpty()) {
            membresias.save(new Membresia(id, u.getId(), rol, clock.instant()));
        }
    }
    private void ficha(Usuario u) {
        if (clientes.findByUsuarioId(u.getId()).isEmpty()) clientes.saveAndFlush(new Cliente(u, clock.instant()));
    }
    private void perfil(Usuario u) {
        if (barberos.buscarIdPorUsuario(u.getId()).isPresent()) return;
        if (barberos.countByActivoTrue(negocio.negocioActual().orElseThrow()) >= reglas.limiteBarberosActivos()) {
            throw new IllegalStateException("La demo multinegocio necesita cupo para un barbero adicional.");
        }
        // Una revocación previa se conserva: no crea un perfil que devuelva el acceso.
        if (!membresias.findByNegocioIdAndUsuarioId(negocio.negocioActual().orElseThrow(), u.getId())
                .filter(Membresia::isActiva).isPresent()) return;
        var perfil = barberos.saveAndFlush(new Barbero(u, "Corte y barba", clock.instant()));
        barberos.bloquearPorIds(List.of(perfil.getId()));
        for (short dia = 1; dia <= 7; dia++) {
            jornadas.save(new Jornada(perfil, dia, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        }
    }
}
