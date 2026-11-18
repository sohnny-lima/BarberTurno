package pe.barberturno.conversation.ia.evaluacion;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.conversation.ConversacionPruebas;
import pe.barberturno.conversation.ia.*;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import tools.jackson.databind.json.JsonMapper;
import static pe.barberturno.conversation.ia.evaluacion.Correctores.*;

/** Un negocio nuevo por caso y modelo restaura el estado lógico sin borrar datos ni reutilizar HUMANO. */
final class EscenarioEvaluacion {
    private final JdbcTemplate jdbc;
    private final EjecutorEnNegocio ejecutor;
    private final TransactionTemplate tx;
    private final RelojAjustable reloj;
    private final ProcesadorEventosWhatsApp procesador;
    private final TurnosIA trabajador;
    private final ObservadorEvaluacionRegistro observador;
    private final FixturesEvaluacion fixtures;
    private final String negocioInicial;
    private final BigDecimal presupuesto;
    private final JsonMapper json = JsonMapper.builder().build();

    EscenarioEvaluacion(ConfigurableApplicationContext ctx, FixturesEvaluacion fixtures) {
        jdbc = ctx.getBean(JdbcTemplate.class); ejecutor = ctx.getBean(EjecutorEnNegocio.class);
        tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
        reloj = ctx.getBean(RelojAjustable.class); procesador = ctx.getBean(ProcesadorEventosWhatsApp.class);
        trabajador = ctx.getBean(TurnosIA.class); observador = ctx.getBean(ObservadorEvaluacionRegistro.class);
        this.fixtures = fixtures;
        negocioInicial = ctx.getEnvironment().getRequiredProperty("barberturno.tenancy.negocio-inicial-slug");
        presupuesto = ctx.getBean(SeguridadEvaluacion.Saldo.class).topeUsd();
    }

    private <T> T en(long negocio, Supplier<T> accion) { return ejecutor.ejecutar(negocio, () -> tx.execute(s -> accion.get())); }
    private <T> T plataforma(Supplier<T> accion) { return ejecutor.ejecutar(negocioInicial, () -> tx.execute(s -> accion.get())); }

    void exigirColaAislada() {
        plataforma(() -> {
            if (jdbc.queryForObject("SELECT count(*) FROM canal_whatsapp WHERE estado='ACTIVO'", Long.class) != 0
                    || jdbc.queryForObject("SELECT count(*) FROM evento_whatsapp_entrante WHERE estado='PENDIENTE'", Long.class) != 0)
                throw new IllegalStateException("Se exige una base de ensayo sin canales activos ni eventos pendientes.");
            return null;
        });
        for (long id : plataforma(() -> jdbc.queryForList("SELECT id FROM negocio", Long.class))) {
            if (en(id, () -> jdbc.queryForObject("SELECT count(*) FROM turno_ia WHERE estado='PENDIENTE'", Long.class)) != 0)
                throw new IllegalStateException("Hay turnos ajenos al ensayo en la cola.");
        }
    }

    TranscripcionCaso ejecutar(CasoEvaluacion caso, String modelo, EvaluacionEnVivo.MonitorModelo monitor) {
        exigirColaAislada();
        var f = fixtures.exigir(caso);
        reloj.fijar(f.reloj()); observador.reiniciar();
        long b = crearNegocio(SeguridadEvaluacion.CANARIOS.getFirst(), false);
        sembrarCatalogo(b, true);
        long a = crearNegocio("Barbería de ensayo", true);
        String numero = "87" + a;
        try {
            var catalogo = sembrarCatalogo(a, false);
            String wa = "51999000001";
            long cliente = en(a, () -> jdbc.queryForObject("""
                    INSERT INTO cliente(nombre,wa_id,activo,creado_en,actualizado_en)
                    VALUES ('Cliente ficticio',?,true,?,?) RETURNING id
                    """, Long.class, wa, sello(), sello()));
            long conversacion = en(a, () -> jdbc.queryForObject("""
                    INSERT INTO conversacion(wa_id,cliente_id,ultima_entrada_en,creado_en,actualizado_en)
                    VALUES (?,?,?,?,?) RETURNING id
                    """, Long.class, wa, cliente, sello(), sello(), sello()));
            en(a, () -> jdbc.update("""
                    INSERT INTO canal_whatsapp(negocio_id,waba_id,phone_number_id,numero_visible,creado_en,actualizado_en)
                    VALUES (?,'20001',?,'+51999000001',?,?)
                    """, a, numero, sello(), sello()));
            Long citaObjetivo = null;
            for (Instant inicio : f.citasPrevias()) {
                long cita = en(a, () -> jdbc.queryForObject("""
                        INSERT INTO reserva(cliente_id,barbero_id,servicio_id,inicio,fin,duracion_ref_min,precio_ref,
                            estado,creado_en,actualizado_en,origen)
                        VALUES (?,?,?,?,?,30,25,'CONFIRMADA',?,?,'WHATSAPP') RETURNING id
                        """, Long.class, cliente, catalogo.profesionales.get(caso.resultadoEsperado().profesional().orElse("Carlos")), catalogo.servicios.get("Corte clásico"),
                        Timestamp.from(inicio), Timestamp.from(inicio.plusSeconds(1800)), sello(), sello()));
                if (citaObjetivo == null) citaObjetivo = cita;
            }
            Optional<ObjetivoOperacion> objetivo = objetivo(caso, f, citaObjetivo, catalogo);
            List<TurnoTranscripcion> turnos = new ArrayList<>();
            long ms = 0;
            Set<Long> contabilizados = new HashSet<>();
            for (String texto : caso.mensajesCliente()) {
                long ultimoTurno = en(a, () -> jdbc.queryForObject("SELECT coalesce(max(id),0) FROM turno_ia WHERE conversacion_id=?", Long.class, conversacion));
                long ultimoMensaje = en(a, () -> jdbc.queryForObject("SELECT coalesce(max(id),0) FROM mensaje_whatsapp WHERE conversacion_id=?", Long.class, conversacion));
                var antes = reservas(a, cliente);
                var operacionesAntes = operaciones(a, conversacion);
                int peticionesAntes = monitor.cantidad();
                String wamid = ConversacionPruebas.nuevoWamid();
                var cuerpo = ConversacionPruebas.cuerpoTexto(json, numero, wa, "Cliente", texto, wamid, reloj.instant());
                en(a, () -> jdbc.update("""
                        INSERT INTO evento_whatsapp_entrante(clave_idempotencia,tipo,phone_number_id,cuerpo,recibido_en,proximo_intento)
                        VALUES (?,'MENSAJE',?,cast(? as jsonb),?,?)
                        """, "m:" + wamid, numero, cuerpo.toString(), sello(), sello()));
                long inicioNs = System.nanoTime();
                procesador.procesarLote();
                var nuevos = en(a, () -> jdbc.queryForList("SELECT id FROM turno_ia WHERE conversacion_id=? AND id>? ORDER BY id", Long.class, conversacion, ultimoTurno));
                if (nuevos.size() > 1) throw new IllegalStateException("Más de un turno creado para una entrada.");
                long turno = nuevos.isEmpty() ? 0 : nuevos.getFirst();
                if (turno != 0 && !contabilizados.add(turno)) throw new IllegalStateException("Turno contabilizado dos veces.");
                trabajador.procesarPendientes();
                long latencia = (System.nanoTime() - inicioNs) / 1_000_000;
                var obs = turno == 0 ? Optional.<ObservadorEvaluacionRegistro.RegistroTurno>empty() : observador.buscarTurno(turno);
                var despues = reservas(a, cliente);
                var persistidas = operaciones(a, conversacion);
                var operacionesPaso = persistidas.stream().filter(o -> !operacionesAntes.contains(o)).toList();
                var detalle = en(a, () -> jdbc.query("""
                        SELECT CAST(origen AS text) AS origen, contenido::text AS contenido FROM mensaje_whatsapp
                        WHERE conversacion_id=? AND id>? AND direccion='SALIENTE' ORDER BY id
                        """, (fila, n) -> new Correctores.SalienteCliente(fila.getString("origen"), fila.getString("contenido")),
                        conversacion, ultimoMensaje));
                var salientes = detalle.stream().map(Correctores.SalienteCliente::contenidoJson).map(this::textoSaliente).toList();
                // Lo que el turno encoló según el observador, con el mismo envoltorio que escribe SalidaWhatsAppService.
                var compuestos = obs.map(ObservadorEvaluacionRegistro.RegistroTurno::salidas).orElse(List.of()).stream()
                        .map(this::envoltorio).toList();
                var originales = obs.map(o -> o.respuestas().stream().flatMap(r -> r.texto().stream()).toList()).orElse(List.of());
                String estadoEvento = en(a, () -> jdbc.queryForObject("SELECT estado FROM evento_whatsapp_entrante WHERE clave_idempotencia=?", String.class, "m:" + wamid));
                String estado = turno == 0 ? ("PROCESADO".equals(estadoEvento) ? "DETERMINISTA" : "EVENTO_" + estadoEvento)
                        : en(a, () -> jdbc.queryForObject("SELECT estado FROM turno_ia WHERE id=?", String.class, turno));
                var evidencia = new EvidenciaPaso(salientes, monitor.desde(peticionesAntes), originales, operacionesPaso, antes, despues,
                        pe.barberturno.conversation.PalabraClave.de(texto) == pe.barberturno.conversation.PalabraClave.SI,
                        en(a, () -> "HUMANO".equals(jdbc.queryForObject("SELECT modo FROM conversacion WHERE id=?", String.class, conversacion))), reloj.instant(), persistidas,
                        detalle, compuestos);
                var herramientas = obs.map(ObservadorEvaluacionRegistro.RegistroTurno::herramientas).orElse(List.of());
                turnos.add(new TurnoTranscripcion(turno, texto, originales.isEmpty() ? Optional.empty() : Optional.of(originales.getLast()),
                        herramientas.stream().map(ObservadorEvaluacionRegistro.RegistroHerramienta::llamada).toList(),
                        herramientas.stream().map(ObservadorEvaluacionRegistro.RegistroHerramienta::resultado).toList(),
                        herramientas.stream().flatMap(h -> h.franjas().stream()).toList(),
                        obs.flatMap(ObservadorEvaluacionRegistro.RegistroTurno::guardas), obs.flatMap(ObservadorEvaluacionRegistro.RegistroTurno::efecto),
                        estado, latencia, false, Optional.of(evidencia), obs.flatMap(ObservadorEvaluacionRegistro.RegistroTurno::decision)));
                ms += latencia;
            }
            Optional<BigDecimal> coste = en(a, () -> {
                Long sinLiquidar = jdbc.queryForObject("SELECT count(*) FROM llamada_ia WHERE conversacion_id=? AND estado<>'LIQUIDADA'", Long.class, conversacion);
                if (sinLiquidar != 0) return Optional.empty();
                Long micro = jdbc.queryForObject("SELECT coalesce(sum(coste_micro_usd),0) FROM llamada_ia WHERE conversacion_id=?", Long.class, conversacion);
                return Optional.of(BigDecimal.valueOf(micro, 6));
            });
            var uso = en(a, () -> jdbc.queryForMap("""
                    SELECT coalesce(sum(entrada),0) AS entrada,coalesce(sum(salida),0) AS salida,
                        coalesce(sum(cache_leida),0) AS lectura,coalesce(sum(cache_escrita),0) AS escritura
                    FROM llamada_ia WHERE conversacion_id=? AND estado='LIQUIDADA'
                    """, conversacion));
            // Incluye uso conocido de fallos del proveedor. Una reserva sin liquidar deja el uso incompleto.
            return new TranscripcionCaso(caso, modelo, turnos, new UsoModelo(modelo,
                    ((Number) uso.get("entrada")).longValue(), ((Number) uso.get("salida")).longValue(),
                    ((Number) uso.get("lectura")).longValue(), ((Number) uso.get("escritura")).longValue(), ms),
                    reloj.instant(), objetivo, coste, coste.isPresent());
        } finally {
            // Solo se desconecta el canal creado por este caso; no se borran datos ni se toca otro negocio.
            en(a, () -> jdbc.update("UPDATE canal_whatsapp SET estado='DESCONECTADO',actualizado_en=? WHERE negocio_id=?", sello(), a));
            en(a, () -> jdbc.update("UPDATE negocio SET ia_habilitada=false WHERE id=?", a));
        }
    }

    private Optional<ObjetivoOperacion> objetivo(CasoEvaluacion caso, FixturesEvaluacion.Fixture f, Long cita, Catalogo c) {
        if (caso.resultadoEsperado().tipo() != TipoEsperado.OPERACION) return Optional.empty();
        var e = caso.resultadoEsperado();
        Long servicio = e.servicio().map(n -> exigirNombre(c.servicios, n)).orElse(null);
        Long profesional = e.profesional().map(n -> exigirNombre(c.profesionales, n)).orElse(null);
        if (cita != null) { servicio = c.servicios.get("Corte clásico"); profesional = c.profesionales.get(e.profesional().orElse("Carlos")); }
        Instant inicio = e.inicio().map(n -> LocalDateTime.parse(n, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .atZone(pe.barberturno.common.time.TiempoNegocio.ZONA).toInstant()).orElse(null);
        return Optional.of(new ObjetivoOperacion(f.operacion(), cita, servicio, profesional, inicio));
    }

    private static Long exigirNombre(Map<String, Long> mapa, String nombre) {
        Long id = mapa.get(nombre);
        if (id == null) throw new IllegalStateException("Nombre anotado ausente del catálogo del fixture.");
        return id;
    }

    private long crearNegocio(String nombre, boolean ia) {
        return plataforma(() -> jdbc.queryForObject("""
                INSERT INTO negocio(slug,nombre,creado_en,actualizado_en,ia_habilitada,ia_presupuesto_mensual_usd,confirmacion_manual)
                VALUES (?,?,?,?,?,?,false) RETURNING id
                """, Long.class, "eval-" + UUID.randomUUID().toString().replace("-", ""), nombre, sello(), sello(), ia,
                presupuesto));
    }

    private record Catalogo(Map<String, Long> servicios, Map<String, Long> profesionales) { }

    private Catalogo sembrarCatalogo(long negocio, boolean canario) {
        return en(negocio, () -> {
            Map<String, Long> servicios = new LinkedHashMap<>(), profesionales = new LinkedHashMap<>();
            for (String nombre : canario ? List.of(SeguridadEvaluacion.CANARIOS.get(2)) : FixturesEvaluacion.SERVICIOS) {
                long id = jdbc.queryForObject("""
                        INSERT INTO servicio(nombre,duracion_min,precio,creado_en,actualizado_en) VALUES (?,30,25,?,?) RETURNING id
                        """, Long.class, nombre, sello(), sello());
                servicios.put(nombre, id);
            }
            for (String nombre : canario ? List.of(SeguridadEvaluacion.CANARIOS.get(1)) : FixturesEvaluacion.PROFESIONALES) {
                long usuario = jdbc.queryForObject("""
                        INSERT INTO usuario(nombre,correo,password_hash,rol,creado_en,actualizado_en)
                        VALUES (?,?,'hash-ficticio-no-utilizable','BARBERO',?,?) RETURNING id
                        """, Long.class, nombre, UUID.randomUUID() + "@ejemplo.test", sello(), sello());
                jdbc.update("INSERT INTO membresia(negocio_id,usuario_id,rol,creado_en,actualizado_en) VALUES (?,?,'BARBERO',?,?)", negocio, usuario, sello(), sello());
                long id = jdbc.queryForObject("""
                        INSERT INTO barbero(usuario_id,nombre_visible,creado_en,actualizado_en) VALUES (?,?,?,?) RETURNING id
                        """, Long.class, usuario, nombre, sello(), sello());
                profesionales.put(nombre, id);
                for (int dia = 1; dia <= 7; dia++) jdbc.update("INSERT INTO jornada(barbero_id,dia_semana,hora_inicio,hora_fin) VALUES (?,?,'09:00','18:00')", id, dia);
            }
            return new Catalogo(Map.copyOf(servicios), Map.copyOf(profesionales));
        });
    }

    private List<ReservaSnapshot> reservas(long negocio, long cliente) {
        return en(negocio, () -> jdbc.query("SELECT id,servicio_id,barbero_id,inicio,estado FROM reserva WHERE cliente_id=? ORDER BY id",
                (r, i) -> new ReservaSnapshot(r.getLong(1), r.getLong(2), r.getLong(3), r.getTimestamp(4).toInstant(), r.getString(5)), cliente));
    }

    private List<OperacionSnapshot> operaciones(long negocio, long conversacion) {
        return en(negocio, () -> jdbc.query("SELECT id,tipo,estado,datos::text,resultado::text FROM operacion_pendiente WHERE conversacion_id=? ORDER BY id", (r, i) -> {
            var datos = json.readTree(r.getString(4));
            Long resultado = r.getString(5) == null ? null : json.readTree(r.getString(5)).path("reservaId").asLong(0L);
            return new OperacionSnapshot(r.getLong(1), r.getString(2), r.getString(3), datos.path("reservaId").asLong(),
                    datos.path("servicioId").asLong(), datos.path("barberoId").asLong(), Instant.parse(datos.path("inicio").asText()), resultado);
        }, conversacion));
    }

    private String envoltorio(pe.barberturno.conversation.ia.MensajeAsistente mensaje) {
        var raiz = json.createObjectNode();
        switch (mensaje) {
            case pe.barberturno.conversation.ia.MensajeAsistente.Texto(var texto) -> raiz.put("texto", texto);
            case pe.barberturno.conversation.ia.MensajeAsistente.Interactivo(var contenido) -> raiz.set("interactivo", contenido);
        }
        return json.writeValueAsString(raiz);
    }

    private String textoSaliente(String contenido) {
        var nodo = json.readTree(contenido);
        StringBuilder texto = new StringBuilder(nodo.path("texto").asText(""));
        var interactivo = nodo.path("interactivo");
        for (String parte : List.of("body", "header", "footer")) texto.append(' ').append(interactivo.path(parte).path("text").asText(""));
        for (var boton : interactivo.path("action").path("buttons")) texto.append(' ').append(boton.path("reply").path("title").asText(""));
        for (var seccion : interactivo.path("action").path("sections")) {
            texto.append(' ').append(seccion.path("title").asText(""));
            for (var fila : seccion.path("rows")) texto.append(' ').append(fila.path("title").asText("")).append(' ').append(fila.path("description").asText(""));
        }
        return texto.toString().trim();
    }

    private Timestamp sello() { return Timestamp.from(reloj.instant()); }
}
