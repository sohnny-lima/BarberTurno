package pe.barberturno.conversation.ia.evaluacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.springframework.core.env.Environment;
import pe.barberturno.conversation.ia.*;

/** Controles previos al contexto y saldo conservador compartido por todos los modelos del ensayo. */
final class SeguridadEvaluacion {
    static final List<String> CANARIOS = List.of("Negocio Zafiro B", "Profesional Zafiro B", "Servicio Zafiro B");

    private SeguridadEvaluacion() { }

    static BigDecimal validarTope(String valor) {
        try {
            BigDecimal tope = new BigDecimal(valor);
            if (tope.scale() > 2) throw new IllegalArgumentException();
            tope = tope.setScale(2, RoundingMode.UNNECESSARY);
            if (tope.signum() <= 0 || tope.compareTo(new BigDecimal("10.00")) > 0)
                throw new IllegalArgumentException();
            return tope;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Se exige un tope entre 0.01 y 10.00 USD, con precisión de céntimos.");
        }
    }

    static void validarUrl(String url) {
        try {
            if (url == null || !url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI uri = URI.create(url.substring(5));
            if (!List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
                    || uri.getPort() != 5433 || !"/barberturno_v2_test".equals(uri.getRawPath())
                    || uri.getRawQuery() != null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            // No incluir la URL: puede contener credenciales.
            throw new IllegalStateException("Destino rechazado: solo localhost:5433/barberturno_v2_test sin parámetros.");
        }
    }

    static void validarDestino(Environment env) {
        String url = env.getRequiredProperty("spring.datasource.url");
        validarUrl(url);
        validarUrl(env.getProperty("spring.flyway.url", url));
        if (!"barberturno_v2_app".equals(env.getProperty("spring.datasource.username")))
            throw new IllegalStateException("El arnés exige el rol real barberturno_v2_app.");
        for (String propiedad : List.of("spring.datasource.jndi-name", "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.data-source-class-name", "spring.datasource.hikari.data-source-j-n-d-i",
                "spring.datasource.hikari.username", "spring.datasource.hikari.password", "spring.datasource.hikari.data-source")) {
            if (env.containsProperty(propiedad)) throw new IllegalStateException("Destino alternativo no permitido.");
        }
        for (String prefijo : List.of("spring.datasource.hikari.data-source-properties", "spring.flyway.jdbc-properties")) {
            if (org.springframework.boot.context.properties.bind.Binder.get(env)
                    .bind(prefijo, org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, String.class)).isBound())
                throw new IllegalStateException("Propiedades JDBC alternativas no permitidas.");
        }
    }

    static void comprobarConexion(Environment env) {
        // Ocurre en el inicializador, antes del refresh, Flyway y cualquier trabajador.
        try (var c = DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"),
                env.getRequiredProperty("spring.datasource.username"), env.getRequiredProperty("spring.datasource.password"));
                var s = c.createStatement();
                var r = s.executeQuery("SELECT current_database(), current_user, rolsuper, rolbypassrls FROM pg_roles WHERE rolname=current_user")) {
            if (!r.next() || !"barberturno_v2_test".equals(r.getString(1))
                    || !"barberturno_v2_app".equals(r.getString(2)) || r.getBoolean(3) || r.getBoolean(4))
                throw new IllegalStateException("Identidad de conexión incompatible con el ensayo.");
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo comprobar el destino seguro antes de las migraciones.");
        }
    }

    static void validarModelo(IaPropiedades p, String modelo, String esfuerzo) {
        if (!p.configurada() || !java.util.Objects.equals(modelo, p.modelo()) || !java.util.Objects.equals(esfuerzo, p.esfuerzo())
                || p.trabajador().habilitado() || !p.precios().containsKey(modelo))
            throw new IllegalStateException("IaPropiedades efectivo no coincide con el ensayo solicitado.");
    }

    static boolean contieneCanario(String texto) {
        String normal = texto.toLowerCase(java.util.Locale.ROOT);
        return CANARIOS.stream().anyMatch(c -> normal.contains(c.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Se retiene el máximo incluso con error; no se reutiliza saldo entre casos ni modelos. */
    static final class Saldo {
        private final long topeMicro;
        private long reservado;

        Saldo(BigDecimal tope) { topeMicro = validarTope(tope.toPlainString()).movePointRight(6).longValueExact(); }

        synchronized void reservar(PeticionModelo peticion, IaPropiedades p) {
            long coste = costeMaximo(peticion, p);
            if (coste > topeMicro - reservado) throw new IllegalStateException("Tope global del ensayo agotado antes de la llamada.");
            reservado = Math.addExact(reservado, coste);
        }

        synchronized long reservado() { return reservado; }
        BigDecimal topeUsd() { return BigDecimal.valueOf(topeMicro, 6).setScale(2, RoundingMode.UNNECESSARY); }
    }

    // Misma cota que LimitesIAService: bytes + formatos + continuación opaca, sin presumir caché.
    static long costeMaximo(PeticionModelo peticion, IaPropiedades p) {
        long bytes = bytes(peticion.instrucciones()) + bytes(peticion.contexto()) + 8192;
        for (var h : peticion.herramientas()) bytes += bytes(h.esquemaJson()) + bytes(h.descripcion());
        for (var mensaje : peticion.mensajes()) bytes += switch (mensaje) {
            case MensajeModelo.DelCliente(var texto) -> bytes(texto);
            case MensajeModelo.DelAsistente a -> bytes(a.texto().orElse(""))
                    + a.llamadas().stream().mapToLong(l -> bytes(l.argumentosJson()) + bytes(l.id()) + 256).sum()
                    + p.maxTokens() * 32L;
            case MensajeModelo.ResultadosHerramientas(var resultados) -> resultados.stream()
                    .mapToLong(r -> bytes(r.json()) + bytes(r.llamadaId()) + 256).sum();
        };
        var precio = p.precios().get(p.modelo());
        if (precio == null || List.of(precio.entrada(), precio.salida(), precio.cacheLectura(), precio.cacheEscritura())
                .stream().anyMatch(v -> v.signum() < 0)) throw new IllegalStateException("Falta una tarifa válida.");
        return precio.entrada().max(precio.cacheLectura()).max(precio.cacheEscritura()).multiply(BigDecimal.valueOf(bytes))
                .add(precio.salida().multiply(BigDecimal.valueOf(p.maxTokens())))
                .setScale(0, RoundingMode.CEILING).longValueExact();
    }

    private static long bytes(String valor) { return valor.getBytes(StandardCharsets.UTF_8).length; }
}
