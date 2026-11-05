package pe.barberturno.conversation.ia;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.ConsultasConversacion;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proyección por lista blanca del contexto mínimo (V2-DA-81), sin serializar entidades,
 * remitentes, selecciones ni contenido del personal. El resultado solo contiene alias del turno.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ConstructorContextoModelo {
    private final EntityManager em;
    private final ConsultasConversacion consultas;
    private final JsonMapper json;
    private final Clock reloj;
    private final TransactionTemplate tx;

    ConstructorContextoModelo(EntityManager em, ConsultasConversacion consultas, JsonMapper json, Clock reloj,
            PlatformTransactionManager gestor) {
        this.em = em; this.consultas = consultas; this.json = json; this.reloj = reloj;
        this.tx = new TransactionTemplate(gestor);
    }

    /**
     * Lee datos bajo RLS y los proyecta sin identidades internas en una transacción corta.
     * @param conversacionId conversación del turno
     * @param c capacidades que se completan con el catálogo publicado
     * @return JSON mínimo para el proveedor
     */
    public String construir(long conversacionId, ContextoTurno c) {
        return tx.execute(estado -> {
            var raiz = json.createObjectNode();
            raiz.put("negocio", HerramientasReserva.limpio(consultas.nombreNegocio()));
            raiz.put("ahora", reloj.instant().atZone(TiempoNegocio.ZONA).format(
                    DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm XXX", Locale.forLanguageTag("es-PE"))));
            var servicios = consultas.servicios();
            var catalogo = raiz.putArray("servicios");
            for (var s : servicios) catalogo.addObject().put("alias", c.servicio(s))
                    .put("nombre", HerramientasReserva.limpio(s.nombre())).put("duracion", s.duracionMin()).put("precio", s.precio());
            var profesionales = raiz.putArray("profesionales");
            if (!servicios.isEmpty()) for (var p : consultas.profesionales(servicios.getFirst().id()))
                profesionales.addObject().put("alias", c.profesional(p)).put("nombre", HerramientasReserva.limpio(p.nombre()));
            raiz.put("tieneFicha", c.ficha.isPresent());
            if (c.ficha.isPresent()) {
                var nombres = em.createNativeQuery("SELECT nombre FROM cliente WHERE id=:id AND activo", String.class)
                        .setParameter("id", c.ficha.getAsLong()).getResultList();
                if (!nombres.isEmpty()) raiz.put("nombre", HerramientasReserva.limpio(nombres.getFirst().toString().strip().split("\\s+")[0]));
            }
            var filas = em.createNativeQuery("SELECT CAST(estado AS text) FROM conversacion WHERE id=:id", String.class)
                    .setParameter("id", conversacionId).getResultList();
            if (!filas.isEmpty()) {
                var estadoConversacion = json.readTree(filas.getFirst().toString());
                // Metadato interno del estado (fuera de ia): se pierde con cualquier vaciado.
                // El corte por identidad también funciona con dos mensajes de igual sello temporal.
                var corte = estadoConversacion.get("ia_corte_mensaje");
                c.corteHistorial = corte != null && corte.isIntegralNumber() && corte.asLong() >= 0 ? corte.asLong()
                        : ((Number) em.createNativeQuery("SELECT COALESCE(max(id),0) FROM mensaje_whatsapp WHERE conversacion_id=:id")
                                .setParameter("id", conversacionId).getSingleResult()).longValue();
                var borrador = estadoConversacion.path("ia");
                var proyectado = raiz.putObject("borrador");
                long servicio = borrador.path("servicio").asLong(0), profesional = borrador.path("profesional").asLong(0);
                c.servicios.forEach((alias, s) -> { if (s.id() == servicio) proyectado.put("servicio", alias); });
                c.profesionales.forEach((alias, p) -> { if (p.id() == profesional) proyectado.put("profesional", alias); });
                String dia = borrador.path("dia").asString("");
                if (dia.matches("\\d{4}-\\d{2}-\\d{2}")) proyectado.put("dia", dia);
            }
            var historial = raiz.putArray("historial");
            var mensajes = em.createNativeQuery("""
                    SELECT CAST(m.contenido AS text) FROM mensaje_whatsapp m JOIN conversacion c
                        ON c.negocio_id=m.negocio_id AND c.id=m.conversacion_id
                    WHERE c.id=:id AND m.origen IN ('ASISTENTE','SISTEMA') AND m.direccion='SALIENTE'
                        AND m.id > :corte AND NOT jsonb_exists(m.contenido, 'retirado')
                    ORDER BY m.id DESC LIMIT 6
                    """, String.class).setParameter("id", conversacionId).setParameter("corte", c.corteHistorial).getResultList();
            for (int i = mensajes.size() - 1; i >= 0; i--) {
                var contenido = json.readTree(mensajes.get(i).toString());
                String texto = contenido.path("interactivo").path("body").path("text").asString(
                        contenido.path("texto").asString(contenido.path("text").path("body").asString("")));
                if (!texto.isBlank()) historial.add(HerramientasReserva.limpio(texto));
            }
            return json.writeValueAsString(raiz);
        });
    }
}
