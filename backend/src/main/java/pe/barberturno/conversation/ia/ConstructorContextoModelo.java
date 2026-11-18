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
 * remitentes, nombre del cliente ni historial textual. Solo expone alias del turno y ultimaRespuesta
 * de lista cerrada; revalida las selecciones parciales del borrador antes de proyectarlas.
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
            var filas = em.createNativeQuery("SELECT CAST(estado AS text) FROM conversacion WHERE id=:id", String.class)
                    .setParameter("id", conversacionId).getResultList();
            if (!filas.isEmpty()) {
                var borrador = json.readTree(filas.getFirst().toString()).path("ia");
                var proyectado = raiz.putObject("borrador");
                long servicio = borrador.path("servicio").asLong(-1), profesional = borrador.path("profesional").asLong(-1);
                c.servicios.forEach((alias, s) -> {
                    if (s.id() == servicio) {
                        c.servicioBorrador = s.id();
                        proyectado.put("servicio", alias);
                    }
                });
                if (profesional == 0) {
                    c.profesionalBorrador = 0L;
                    proyectado.put("profesional", "cualquiera");
                } else c.profesionales.forEach((alias, p) -> {
                    if (p.id() == profesional) {
                        c.profesionalBorrador = p.id();
                        proyectado.put("profesional", alias);
                    }
                });
                String dia = borrador.path("dia").asString("");
                try {
                    if (dia.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        var fecha = java.time.LocalDate.parse(dia);
                        if (!fecha.isBefore(c.hoy)) {
                            c.diaBorrador = fecha;
                            proyectado.put("dia", dia);
                        }
                    }
                } catch (java.time.DateTimeException invalida) { /* Borrador obsoleto: se vuelve a pedir el día. */ }
                String ultima = borrador.path("ultima").asString("NEUTRO");
                raiz.put("ultimaRespuesta", ContextoTurno.ultimaValida(ultima) ? ultima : "NEUTRO");
            } else raiz.put("ultimaRespuesta", "NEUTRO");
            return json.writeValueAsString(raiz);
        });
    }
}
