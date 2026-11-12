package pe.barberturno.whatsapp.twilio;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link CorrespondenciaDireccionesTwilio} (CT-10, 20-v2-twilio-sandbox.md §10).
 * Valida la derivación determinista de identidades sintéticas de veinte dígitos con prefijo 9
 * para remitentes BSUID sin WaId, la prioridad del WaId E.164, el rechazo de direcciones From inválidas,
 * la resolución de destinos REST para E.164 y sintéticos conocidos, y la política de desalojo
 * de la dirección menos recientemente vista al superar el límite de 10 000 entradas.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class CorrespondenciaDireccionesTwilioTest {

    private static final String BSUID_VALIDO_1 = "whatsapp:PE.0123456789ABCDEF";
    private static final String BSUID_VALIDO_2 = "whatsapp:PE.FEDCBA9876543210";
    private static final String WA_ID_E164 = "51999887766";

    private CorrespondenciaDireccionesTwilio correspondencia;

    @BeforeEach
    void preparar() {
        correspondencia = new CorrespondenciaDireccionesTwilio();
    }

    /**
     * Calcula de forma independiente la identidad sintética esperada según la especificación
     * del contrato V2-DA-103: SHA-256 de From sin prefijo "whatsapp:", primeros 8 bytes como
     * entero sin signo en big-endian, módulo 10^19, rellenado a 19 dígitos con ceros a la izquierda
     * y prefijado con el dígito "9".
     * @param bsuidSinPrefijo parte del BSUID tras el prefijo "whatsapp:"
     * @return identificador sintético de 20 dígitos empezando por 9
     */
    private static String calcularSinteticoIndependiente(String bsuidSinPrefijo) {
        try {
            byte[] sha256 = MessageDigest.getInstance("SHA-256")
                    .digest(bsuidSinPrefijo.getBytes(StandardCharsets.UTF_8));
            byte[] primeros8Bytes = Arrays.copyOf(sha256, 8);
            BigInteger valorSinSigno = new BigInteger(1, primeros8Bytes);
            BigInteger modulo = BigInteger.TEN.pow(19);
            BigInteger residuo = valorSinSigno.mod(modulo);
            String residuoStr = residuo.toString();
            return "9" + "0".repeat(19 - residuoStr.length()) + residuoStr;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error al calcular SHA-256 en la prueba.", e);
        }
    }

    @Test
    void identificar_bsuidValidoSinWaId_devuelveSinteticoDe20DigitosConPrefijo9CoincidenteConFormula() {
        String esperado = calcularSinteticoIndependiente("PE.0123456789ABCDEF");

        Optional<String> resultado = correspondencia.identificar(null, BSUID_VALIDO_1);

        assertThat(resultado).isPresent();
        String sintetico = resultado.get();
        assertThat(sintetico)
                .as("debe tener exactamente 20 dígitos y empezar por 9")
                .hasSize(20)
                .startsWith("9")
                .matches("9[0-9]{19}")
                .isEqualTo(esperado);
    }

    @Test
    void identificar_mismoBsuidRepetido_devuelveMismoValorDeterminista() {
        Optional<String> primera = correspondencia.identificar(null, BSUID_VALIDO_1);
        Optional<String> segunda = correspondencia.identificar(null, BSUID_VALIDO_1);

        assertThat(primera).isPresent();
        assertThat(segunda).isEqualTo(primera);
    }

    @Test
    void identificar_dosBsuidDistintos_devuelvenValoresDistintos() {
        Optional<String> id1 = correspondencia.identificar(null, BSUID_VALIDO_1);
        Optional<String> id2 = correspondencia.identificar(null, BSUID_VALIDO_2);

        assertThat(id1).isPresent();
        assertThat(id2).isPresent();
        assertThat(id1.get()).isNotEqualTo(id2.get());
    }

    @Test
    void identificar_conWaIdE164Valido_priorizaWaIdSobreDireccionBsuid() {
        Optional<String> resultado = correspondencia.identificar(WA_ID_E164, BSUID_VALIDO_1);

        assertThat(resultado).isPresent().contains(WA_ID_E164);
    }

    @Test
    void identificar_conWaIdInvalido_usaBsuidValido() {
        // Un WaId no numérico o fuera de rango [1-9][0-9]{1,14} recurre al BSUID
        Optional<String> resultado = correspondencia.identificar("invalido", BSUID_VALIDO_1);

        assertThat(resultado).isPresent().contains(calcularSinteticoIndependiente("PE.0123456789ABCDEF"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "whatsapp:pe.0123456789ABCDEF",           // minúsculas en el código de país
            "whatsapp:PE.simbolo#1234567",            // símbolos no alfanuméricos
            "whatsapp:PE.con-guiones_123",            // guiones y guiones bajos
            "PE.0123456789ABCDEF",                    // sin prefijo "whatsapp:"
            "whatsapp:P.0123456789ABCDEF",            // un solo caracter de país
            "whatsapp:PER.0123456789ABCDEF",          // tres caracteres de país
            "whatsapp:",                              // sin contenido tras el prefijo
            "whatsapp:PE.",                           // sin identificador tras el punto
            "whatsapp:PE.123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" // más de 128 caracteres
    })
    void identificar_fromInvalidoSinWaId_devuelveVacio(String fromInvalido) {
        Optional<String> resultado = correspondencia.identificar(null, fromInvalido);

        assertThat(resultado).isEmpty();
    }

    @Test
    void destino_telefonoE164_devuelveDireccionWhatsappMasNumero() {
        Optional<String> destino = correspondencia.destino(WA_ID_E164);

        assertThat(destino).isPresent().contains("whatsapp:+" + WA_ID_E164);
    }

    @Test
    void destino_sinteticoConocido_devuelveDireccionBsuidRegistrada() {
        String sintetico = correspondencia.identificar(null, BSUID_VALIDO_1).orElseThrow();

        Optional<String> destino = correspondencia.destino(sintetico);

        assertThat(destino).isPresent().contains(BSUID_VALIDO_1);
    }

    @Test
    void destino_sinteticoDesconocido_devuelveVacio() {
        // Sintético con formato válido de 20 dígitos pero nunca registrado
        Optional<String> destino = correspondencia.destino("90000000000000000001");

        assertThat(destino).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalido", "0", "+51999887766", "abc"})
    void destino_waIdInvalido_devuelveVacio(String waIdInvalido) {
        Optional<String> destino = correspondencia.destino(waIdInvalido);

        assertThat(destino).isEmpty();
    }

    @Test
    void identificar_limiteDe10000Entradas_retiraLaMenosRecientementeVista() {
        // 1. Insertar 10 000 entradas distintas
        String primerBsuid = "whatsapp:PE." + "A".repeat(16);
        String primerSintetico = correspondencia.identificar(null, primerBsuid).orElseThrow();

        for (int i = 1; i < 10_000; i++) {
            String bsuid = "whatsapp:PE.B%015d".formatted(i);
            correspondencia.identificar(null, bsuid);
        }

        // 2. Comprobar que la primera entrada aún está presente
        assertThat(correspondencia.destino(primerSintetico)).isPresent().contains(primerBsuid);

        // 3. Volver a identificar la primera entrada para que pase a ser la más recientemente vista
        correspondencia.identificar(null, primerBsuid);

        // La segunda entrada ("whatsapp:PE.B000000000000001") pasa a ser la menos recientemente vista
        String segundoBsuid = "whatsapp:PE.B%015d".formatted(1);
        String segundoSintetico = calcularSinteticoIndependiente("PE.B%015d".formatted(1));
        assertThat(correspondencia.destino(segundoSintetico)).isPresent().contains(segundoBsuid);

        // 4. Insertar una entrada 10 001
        String entradaExtraBsuid = "whatsapp:PE.EXTRA1234567890";
        String entradaExtraSintetico = correspondencia.identificar(null, entradaExtraBsuid).orElseThrow();

        // 5. La segunda entrada (la menos recientemente vista) debe haber sido desalojada
        assertThat(correspondencia.destino(segundoSintetico))
                .as("la entrada menos recientemente vista debe haber sido retirada al superar 10 000")
                .isEmpty();

        // 6. La primera entrada sigue presente porque fue refrescada antes del desalojo
        assertThat(correspondencia.destino(primerSintetico)).isPresent().contains(primerBsuid);

        // 7. La nueva entrada debe estar registrada
        assertThat(correspondencia.destino(entradaExtraSintetico)).isPresent().contains(entradaExtraBsuid);
    }
}
