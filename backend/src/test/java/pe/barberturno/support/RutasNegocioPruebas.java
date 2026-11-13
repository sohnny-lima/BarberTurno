package pe.barberturno.support;

/** Construye URLs canónicas de los escenarios heredados sin intervenir en la seguridad ni en MockMvc. */
public final class RutasNegocioPruebas {
    private RutasNegocioPruebas() { }

    public static java.util.List<String> legadas() {
        return java.util.List.of("/api/negocio", "/api/sesion", "/api/registro", "/api/auth/registro",
                "/api/servicios", "/api/servicios/1", "/api/barberos", "/api/barberos/1/jornadas",
                "/api/barberos/1/bloqueos", "/api/bloqueos/lote", "/api/bloqueos/1", "/api/disponibilidad",
                "/api/reservas", "/api/reservas/mias", "/api/reservas/1/cancelacion",
                "/api/reservas/1/reprogramacion", "/api/reservas/1/transiciones", "/api/reservas/1/auditoria",
                "/api/notificaciones", "/api/notificaciones/conteo", "/api/notificaciones/lectura",
                "/api/reportes/resumen", "/api/reportes/resumen.xlsx", "/api/usuarios", "/api/usuarios/1/estado",
                "/api/usuarios/1/restablecer-password", "/api/clientes/1", "/api/clientes/alta",
                "/api/equipo", "/api/equipo/1/rol", "/api/equipo/1/revocacion", "/api/equipo/salida",
                "/api/invitaciones", "/api/configuracion");
    }

    public static String negocio(String ruta) {
        return negocio(ruta, "barberturno");
    }

    public static String negocio(String ruta, String slug) {
        if (!ruta.startsWith("/api/") || ruta.startsWith("/api/n/")) {
            throw new IllegalArgumentException("Se requiere una ruta local sin prefijo en el fixture.");
        }
        return "/api/n/" + slug + "/" + ruta.substring("/api/".length());
    }
}
