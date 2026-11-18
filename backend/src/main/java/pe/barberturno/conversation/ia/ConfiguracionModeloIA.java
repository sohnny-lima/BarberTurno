package pe.barberturno.conversation.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.LogLevel;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente condicional: sin configuración no se construye transporte alguno. Los reintentos del SDK
 * se desactivan para que cada HTTP tenga su propia reserva de presupuesto y límite diario.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionModeloIA {
    @Bean(destroyMethod = "close")
    ClienteOpcionalIA clienteOpcionalIA(IaPropiedades propiedades) {
        return new ClienteOpcionalIA(propiedades.configurada() ? AnthropicOkHttpClient.builder()
                .apiKey(propiedades.clave()).baseUrl(propiedades.urlBase()).timeout(propiedades.tiempoLlamada())
                .maxRetries(0).logLevel(LogLevel.OFF).openTelemetry(false).build() : null);
    }

    @Bean
    @ConditionalOnMissingBean(ModeloConversacional.class)
    ModeloConversacional modeloConversacional(ClienteOpcionalIA cliente, IaPropiedades propiedades) {
        return cliente.valor == null ? peticion -> {
            throw new FalloModelo(MotivoFalloModelo.NO_CONFIGURADO, Optional.empty(), null);
        } : new ModeloAnthropic(cliente.valor, propiedades);
    }

    static final class ClienteOpcionalIA implements AutoCloseable {
        private final AnthropicClient valor;
        private ClienteOpcionalIA(AnthropicClient valor) { this.valor = valor; }
        /** Cierra únicamente el transporte que se construyó con configuración explícita. */
        @Override public void close() { if (valor != null) valor.close(); }
    }
}
