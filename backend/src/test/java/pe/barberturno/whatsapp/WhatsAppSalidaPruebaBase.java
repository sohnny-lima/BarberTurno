package pe.barberturno.whatsapp;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import pe.barberturno.common.tenancy.ContextoNegocio;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.customers.ClienteRepository;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.MembresiaRepository;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.support.JdbcNegocioPruebas;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import pe.barberturno.users.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base común de las pruebas de integración de la salida de WhatsApp (12-v2-09-whatsapp-salida.md §5 y §6).
 * Reutiliza el mismo contexto de Spring Boot y el mismo grupo de propiedades para no duplicar el
 * <em>pool</em> de conexiones Hikari hacia {@code barberturno_v2_test}, y comparte la instancia de
 * {@link ServidorGraphFalso} y el {@link RelojAjustable}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=",
        "barberturno.admin.password=",
        "barberturno.admin.nombre=",
        "barberturno.whatsapp.salida.graph.token=token-ficticio-prueba"
})
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class WhatsAppSalidaPruebaBase {
    protected static final String NUMERO_A = "10001";
    protected static final String NUMERO_B = "10002";
    protected static final String WA_CLIENTE = "51999000001";

    protected static final ServidorGraphFalso servidorGraph = new ServidorGraphFalso();

    @DynamicPropertySource
    static void registrarPropiedades(DynamicPropertyRegistry registry) {
        if (!servidorGraph.estaIniciado()) {
            servidorGraph.iniciar();
        }
        registry.add("barberturno.whatsapp.salida.graph.url-base", servidorGraph::urlBase);
    }

    @AfterAll
    static void cerrarServidorGraph() {
        servidorGraph.close();
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JdbcNegocioPruebas sql;
    @Autowired protected Environment entorno;
    @Autowired protected RelojAjustable reloj;
    @Autowired protected JsonMapper mapper;
    @Autowired protected DirectorioNegocios directorio;
    @Autowired protected ContextoNegocio contexto;
    @Autowired protected EjecutorEnNegocio ejecutor;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected MembresiaRepository membresias;
    @Autowired protected CanalWhatsAppRepository canales;
    @Autowired protected BarberoRepository barberos;
    @Autowired protected ServicioRepository servicios;
    @Autowired protected ClienteRepository clientes;
    @Autowired protected PlatformTransactionManager gestor;

    protected long a;
    protected long b;
    protected Usuario titular;
    protected Usuario encargado;
    protected Usuario barberoCuenta;
    protected long barberoA;
    protected long servicioA;

    @BeforeEach
    void prepararBase() throws Exception {
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
        servidorGraph.limpiar();

        // El propietario solo prepara las filas de negocio, igual que PeticionNegocioPruebaBase.
        try (var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"));
                var sentencia = conexion.prepareStatement("""
                        INSERT INTO negocio(slug,nombre,creado_en,actualizado_en) VALUES (?, ?, ?, ?)
                        """)) {
            assertThat(conexion.getCatalog()).isEqualTo("barberturno_v2_test");
            for (String slug : List.of("a", "b")) {
                sentencia.setString(1, slug);
                sentencia.setString(2, "Negocio ficticio " + slug);
                sentencia.setTimestamp(3, Timestamp.from(reloj.instant()));
                sentencia.setTimestamp(4, Timestamp.from(reloj.instant()));
                sentencia.executeUpdate();
            }
        }
        a = directorio.buscar("a").orElseThrow();
        b = directorio.buscar("b").orElseThrow();

        titular = usuarios.saveAndFlush(new Usuario("Titular ficticio", "titular@ejemplo.test", "999000001",
                "hash-ficticio-no-utilizable", Rol.CLIENTE, reloj.instant(), reloj.instant()));
        encargado = usuarios.saveAndFlush(new Usuario("Encargado ficticio", "encargado@ejemplo.test", "999000002",
                "hash-ficticio-no-utilizable", Rol.CLIENTE, reloj.instant(), reloj.instant()));
        barberoCuenta = usuarios.saveAndFlush(new Usuario("Profesional ficticio", "barbero@ejemplo.test", "999000003",
                "hash-ficticio-no-utilizable", Rol.BARBERO, reloj.instant(), reloj.instant()));

        membresias.save(new Membresia(a, titular.getId(), RolNegocio.PROPIETARIO, reloj.instant()));
        membresias.save(new Membresia(a, encargado.getId(), RolNegocio.ENCARGADO, reloj.instant()));
        membresias.save(new Membresia(a, barberoCuenta.getId(), RolNegocio.BARBERO, reloj.instant()));

        membresias.save(new Membresia(b, titular.getId(), RolNegocio.PROPIETARIO, reloj.instant()));

        canales.save(new CanalWhatsApp(a, "20001", NUMERO_A, "+51999000001", reloj.instant()));
        canales.save(new CanalWhatsApp(b, "20002", NUMERO_B, "+51999000002", reloj.instant()));

        barberoA = ejecutor.ejecutar(a, () -> barberos.saveAndFlush(
                new Barbero(barberoCuenta, "Barbero A", reloj.instant())).getId());
        servicioA = ejecutor.ejecutar(a, () -> servicios.saveAndFlush(
                new Servicio("Corte clásico", "Descripción ficticia", (short) 30, java.math.BigDecimal.valueOf(25.0), reloj.instant())).getId());
    }

    @AfterEach
    void limpiarBase() {
        assertThat(contexto.delimitado()).isFalse();
        LimpiezaBaseDatos.limpiar(sql);
    }

    /**
     * Crea una conversación en el negocio especificado con la ventana de 24 h abierta.
     * @param negocioId negocio destinatario
     * @param waId identificador de WhatsApp del cliente
     * @return id de la conversación creada
     */
    protected long crearConversacion(long negocioId, String waId) {
        return crearConversacionConVentana(negocioId, waId, reloj.instant());
    }

    /**
     * Crea una conversación en el negocio especificado con el instante de última entrada indicado.
     * @param negocioId negocio destinatario
     * @param waId identificador de WhatsApp del cliente
     * @param ultimaEntradaEn instante de la última entrada recibida, o {@code null} si no hay
     * @return id de la conversación creada
     */
    protected long crearConversacionConVentana(long negocioId, String waId, Instant ultimaEntradaEn) {
        return ejecutor.ejecutar(negocioId, () -> {
            var existentes = sql.queryForList("SELECT id FROM conversacion WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            if (!existentes.isEmpty()) {
                long id = existentes.getFirst();
                sql.update("UPDATE conversacion SET ultima_entrada_en = ?, actualizado_en = ? WHERE id = ?",
                        ultimaEntradaEn == null ? null : Timestamp.from(ultimaEntradaEn),
                        Timestamp.from(reloj.instant()), id);
                return id;
            }
            return sql.queryForObject("""
                    INSERT INTO conversacion(negocio_id, wa_id, ultima_entrada_en, creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, negocioId, waId,
                    ultimaEntradaEn == null ? null : Timestamp.from(ultimaEntradaEn),
                    Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
        });
    }

    /**
     * Crea una ficha de cliente y una reserva confirmada en el negocio especificado.
     * @param negocioId negocio de la reserva
     * @param waId número de WhatsApp del cliente
     * @return id de la reserva creada
     */
    protected long crearReserva(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var existentes = sql.queryForList("SELECT id FROM cliente WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            long clienteId = existentes.isEmpty()
                    ? sql.queryForObject("""
                            INSERT INTO cliente(negocio_id, nombre, wa_id, telefono, activo, creado_en, actualizado_en)
                            VALUES (?, 'Cliente Ficticio', ?, '999000001', true, ?, ?) RETURNING id
                            """, Long.class, negocioId, waId,
                            Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()))
                    : existentes.getFirst();

            var inicio = Timestamp.from(OffsetDateTime.parse("2026-11-01T10:00:00-05:00").toInstant());
            var fin = Timestamp.from(OffsetDateTime.parse("2026-11-01T10:30:00-05:00").toInstant());

            return sql.queryForObject("""
                    INSERT INTO reserva(negocio_id, cliente_id, barbero_id, servicio_id, inicio, fin,
                        duracion_ref_min, precio_ref, estado, creada_por, creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, ?, 30, 25.00, 'CONFIRMADA', ?, ?, ?) RETURNING id
                    """, Long.class, negocioId, clienteId, barberoA, servicioA, inicio, fin,
                    titular.getId(), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
        });
    }

    /**
     * Consulta el contenido de un mensaje en la tabla {@code mensaje_whatsapp}.
     * @param mensajeId identificador del mensaje
     * @return mapa con las columnas de la fila
     */
    protected Map<String, Object> filaMensaje(long mensajeId) {
        return sql.queryForMap("SELECT * FROM mensaje_whatsapp WHERE id = ?", mensajeId);
    }

    /**
     * Consulta todos los mensajes de un negocio.
     * @param negocioId negocio a consultar
     * @return lista de mapas con las columnas de los mensajes
     */
    protected List<Map<String, Object>> filasMensajes(long negocioId) {
        return sql.queryForList("SELECT * FROM mensaje_whatsapp WHERE negocio_id = ? ORDER BY id", negocioId);
    }

    /**
     * Cuenta cuántos mensajes hay en un estado dado para un negocio.
     * @param negocioId negocio a consultar
     * @param estado estado del mensaje
     * @return número de mensajes
     */
    protected int contarMensajes(long negocioId, EstadoMensaje estado) {
        return sql.queryForObject("SELECT count(*) FROM mensaje_whatsapp WHERE negocio_id = ? AND estado = ?",
                Integer.class, negocioId, estado.name());
    }

    /**
     * Cuenta cuántas notificaciones internas hay para un usuario en un negocio.
     * @param negocioId negocio a consultar
     * @param usuarioId usuario destinatario
     * @return número de notificaciones
     */
    protected int contarNotificaciones(long negocioId, long usuarioId) {
        return sql.queryForObject("SELECT count(*) FROM notificacion WHERE negocio_id = ? AND usuario_id = ?",
                Integer.class, negocioId, usuarioId);
    }
}
