package pe.barberturno.perf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Importa una agenda ficticia asistida RN-20 con trazabilidad RN-15 en una base exclusiva.
 * La fecha inicial y la semilla fijan la agenda; no altera migraciones ni reglas del servidor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class DatosCarga {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private DatosCarga() { }

    /**
     * Carga 200 clientes, diez barberos, dos servicios y treinta días; rechaza bases con datos.
     * @param args sin argumentos; configuración y contraseña ficticia por entorno
     * @throws Exception si faltan variables, la base no es exclusiva o falla la transacción
     */
    public static void main(String[] args) throws Exception {
        String url = requerida("BT_DB_URL");
        validarBase(url);
        String fechaConfigurada = System.getenv("BT_PERF_FECHA");
        LocalDate fecha = fechaConfigurada == null || fechaConfigurada.isBlank()
                ? LocalDate.now(Clock.system(LIMA)).plusDays(1) : LocalDate.parse(fechaConfigurada);
        String hash = new BCryptPasswordEncoder(12).encode(requerida("BT_PERF_PASSWORD"));
        try (Connection db = DriverManager.getConnection(url, requerida("BT_DB_USER"), requerida("BT_DB_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                if (escalar(db, "SELECT count(*) FROM usuario") != 0
                        || escalar(db, "SELECT count(*) FROM servicio") != 0) {
                    throw new IllegalStateException("La base de carga debe estar vacía después de Flyway.");
                }
                OffsetDateTime ahora = Clock.systemUTC().instant().atOffset(ZoneOffset.UTC);
                long admin = usuario(db, "Administrador de carga", "admin-perf@ejemplo.test", "ADMIN", hash, ahora);
                List<Long> clientes = new ArrayList<>();
                for (int n = 0; n < 200; n++) {
                    clientes.add(usuario(db, "Cliente ficticio " + n, "cliente-perf-" + n + "@ejemplo.test", "CLIENTE", hash, ahora));
                }
                ejecutar(db, "SELECT pg_advisory_xact_lock(141414)");
                List<Long> barberos = new ArrayList<>();
                for (int n = 0; n < 10; n++) {
                    long u = usuario(db, "Barbero ficticio " + n, "barbero-perf-" + n + "@ejemplo.test", "BARBERO", hash, ahora);
                    long b = insertar(db, "INSERT INTO barbero(usuario_id,especialidad,creado_en,actualizado_en) VALUES (?, 'Carga', ?, ?) RETURNING id", u, ahora, ahora);
                    barberos.add(b);
                    for (int dia = 1; dia <= 6; dia++) {
                        ejecutar(db, "INSERT INTO jornada(barbero_id,dia_semana,hora_inicio,hora_fin) VALUES (?,?,'09:00','13:00'), (?,?,'14:00','18:00')", b, dia, b, dia);
                    }
                }
                long corto = insertar(db, "INSERT INTO servicio(nombre,duracion_min,precio,creado_en,actualizado_en) VALUES ('Corte de carga',20,20,?,?) RETURNING id", ahora, ahora);
                long largo = insertar(db, "INSERT INTO servicio(nombre,duracion_min,precio,creado_en,actualizado_en) VALUES ('Corte y barba de carga',40,35,?,?) RETURNING id", ahora, ahora);
                // Protocolo ① clientes por id → ② barberos por id, antes de ocupar franjas.
                ejecutar(db, "SELECT id FROM usuario WHERE rol = 'CLIENTE' ORDER BY id FOR UPDATE");
                ejecutar(db, "SELECT id FROM barbero ORDER BY id FOR UPDATE");
                Random aleatorio = new Random(35);
                int cantidad = 0;
                int laborables = 0;
                // 4 x 40 + 2 x 20 = 200 minutos / 480 = 41,67 % de ocupación.
                int[] minutos = {540, 600, 660, 840, 900, 960};
                for (int d = 0; d < 30; d++) {
                    LocalDate dia = fecha.plusDays(d);
                    if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                    laborables++;
                    for (int franja = 0; franja < minutos.length; franja++) {
                        int desplazamiento = aleatorio.nextInt(100);
                        for (int b = 0; b < barberos.size(); b++) {
                            long cliente = clientes.get((desplazamiento + b) % 100);
                            int duracion = franja < 4 ? 40 : 20;
                            long servicio = franja < 4 ? largo : corto;
                            OffsetDateTime inicio = dia.atStartOfDay().plusMinutes(minutos[franja]).atZone(LIMA).toOffsetDateTime();
                            OffsetDateTime fin = inicio.plusMinutes(duracion);
                            long reserva = insertar(db, "INSERT INTO reserva(cliente_id,barbero_id,servicio_id,inicio,fin,duracion_ref_min,precio_ref,estado,creada_por,creado_en,actualizado_en) VALUES (?,?,?,?,?,?,?,'CONFIRMADA',?,?,?) RETURNING id",
                                    cliente, barberos.get(b), servicio, inicio, fin, duracion, duracion == 40 ? 35 : 20, admin, ahora, ahora);
                            String datos = "{\"inicio\":\"" + inicio + "\",\"fin\":\"" + fin + "\",\"barberoId\":" + barberos.get(b)
                                    + ",\"servicioId\":" + servicio + ",\"estado\":\"CONFIRMADA\",\"origen\":\"perf-asistida\"}";
                            ejecutar(db, "INSERT INTO auditoria_reserva(reserva_id,actor_id,accion,estado_nuevo,datos_nuevos,creado_en) VALUES (?,?,'CREAR','CONFIRMADA',?::jsonb,?)", reserva, admin, datos, ahora);
                            ejecutar(db, "INSERT INTO notificacion(usuario_id,reserva_id,tipo,mensaje,creado_en) SELECT ?,?,'CREAR','Reserva ficticia de carga',? UNION ALL SELECT usuario_id,?,'CREAR','Reserva ficticia de carga',? FROM barbero WHERE id=?",
                                    cliente, reserva, ahora, reserva, ahora, barberos.get(b));
                            cantidad++;
                        }
                    }
                }
                ejecutar(db, "ANALYZE");
                db.commit();
                Files.createDirectories(Path.of("target"));
                Files.writeString(Path.of("target/datos.properties"), "fecha=" + fecha + "\nbarberos="
                        + String.join(",", barberos.stream().map(Object::toString).toList())
                        + "\nservicios=" + corto + "," + largo + "\nreservas=" + cantidad + "\nlaborables=" + laborables + "\n");
                System.out.printf("Carga: clientes=200, barberos=10, jornadas=120, servicios=2, reservas=%d, auditorías=%d, avisos=%d, días laborables=%d, ocupación=41,67 %.%n", cantidad, cantidad, cantidad * 2, laborables);
            } catch (Exception fallo) {
                db.rollback();
                // No propagar errores SQL que puedan contener hashes o datos de cuentas.
                throw new IllegalStateException("Carga revertida; tipo=" + fallo.getClass().getSimpleName()
                        + (fallo instanceof SQLException sql ? ", SQLState=" + sql.getSQLState() : ""));
            }
        }
    }

    static void validarBase(String url) {
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5433/barberturno_perf")) {
            throw new IllegalArgumentException("Solo se permite barberturno_perf en localhost:5433.");
        }
    }

    static String requerida(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Falta " + nombre);
        return valor;
    }

    private static long usuario(Connection db, String nombre, String correo, String rol, String hash, OffsetDateTime ahora) throws SQLException {
        return insertar(db, "INSERT INTO usuario(nombre,correo,telefono,password_hash,rol,privacidad_aceptada_en,creado_en,actualizado_en) VALUES (?,?,'999000000',?,?,?,?,?) RETURNING id", nombre, correo, hash, rol, ahora, ahora, ahora);
    }

    private static PreparedStatement preparar(Connection db, String sql, Object... valores) throws SQLException {
        PreparedStatement sentencia = db.prepareStatement(sql);
        for (int n = 0; n < valores.length; n++) sentencia.setObject(n + 1, valores[n]);
        return sentencia;
    }

    private static long insertar(Connection db, String sql, Object... valores) throws SQLException {
        try (var sentencia = preparar(db, sql, valores); var resultado = sentencia.executeQuery()) {
            resultado.next();
            return resultado.getLong(1);
        }
    }

    private static long escalar(Connection db, String sql) throws SQLException {
        return insertar(db, sql);
    }

    private static void ejecutar(Connection db, String sql, Object... valores) throws SQLException {
        try (var sentencia = preparar(db, sql, valores)) { sentencia.execute(); }
    }
}
