package pe.barberturno.support;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj determinista y adelantable sin esperas reales. */
public final class RelojAjustable extends Clock {
    public static final Instant INICIAL = Instant.parse("2026-09-28T14:00:00Z");
    private final AtomicReference<Instant> ahora;
    private final ZoneId zona;

    public RelojAjustable() { this(new AtomicReference<>(INICIAL), ZoneId.of("America/Lima")); }
    private RelojAjustable(AtomicReference<Instant> ahora, ZoneId zona) {
        this.ahora = ahora;
        this.zona = zona;
    }
    public void reiniciar() { ahora.set(INICIAL); }
    public void adelantar(Duration duracion) { ahora.updateAndGet(instante -> instante.plus(duracion)); }
    @Override public Instant instant() { return ahora.get(); }
    @Override public ZoneId getZone() { return zona; }
    @Override public Clock withZone(ZoneId zone) { return new RelojAjustable(ahora, zone); }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuracion {
        @Bean @Primary
        public RelojAjustable relojAjustable() { return new RelojAjustable(); }
    }
}
