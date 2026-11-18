package pe.barberturno.common.tenancy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigracionesV1InalteradasTest {
    @Test
    void migracionesPreviasACien_coincidenExactamenteConElManifiesto() throws Exception {
        Path directorio = Path.of("src/main/resources/db/migration");
        var esperados = new TreeMap<String, String>();
        for (String linea : Files.readAllLines(directorio.resolve("v1-manifiesto.sha256"))) {
            String[] partes = linea.split("  ");
            assertThat(partes).hasSize(2);
            esperados.put(partes[1], partes[0]);
        }
        var actuales = new TreeMap<String, String>();
        try (var archivos = Files.list(directorio)) {
            for (Path archivo : archivos.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql")).toList()) {
                String nombre = archivo.getFileName().toString();
                if (Integer.parseInt(nombre.substring(1, nombre.indexOf("__"))) < 100) {
                    actuales.put(nombre, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(archivo))));
                }
            }
        }
        assertThat(actuales).isNotEmpty().isEqualTo(esperados);
    }
}
