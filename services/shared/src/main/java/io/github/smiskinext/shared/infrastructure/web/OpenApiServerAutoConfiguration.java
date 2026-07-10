package io.github.smiskinext.shared.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that pins the OpenAPI {@code servers} list to a single, deployment-stable
 * gateway URL.
 *
 * <p>Without an {@link OpenAPI} bean carrying an explicit server, springdoc computes the server URL
 * from the incoming request. During {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} generation
 * that resolves to {@code http://localhost:<random-port>}, so the emitted spec changes on every run
 * and pollutes version control. Declaring the servers here makes springdoc treat them as present and
 * skip request-based computation, yielding a deterministic document.
 *
 * <p>The URL defaults to {@code http://localhost:8080} and is overridden per environment via the
 * {@code GATEWAY_URL} property. Active only in a SERVLET web application when the OpenAPI model is on
 * the classpath.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI baseOpenApi(@Value("${GATEWAY_URL:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI()
                .servers(List.of(new Server().url(gatewayUrl).description("API Gateway")));
    }
}
