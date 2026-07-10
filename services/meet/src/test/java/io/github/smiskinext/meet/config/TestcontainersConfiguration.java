package io.github.smiskinext.meet.config;

import io.github.smiskinext.shared.testcontainers.PostgresContainerSupport;
import io.github.smiskinext.shared.testcontainers.ValkeyContainerSupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return PostgresContainerSupport.postgres();
    }

    @Bean
    @SuppressWarnings("resource")
    public GenericContainer<?> valkeyContainer() {
        return ValkeyContainerSupport.valkey();
    }

    @Bean
    public DynamicPropertyRegistrar redisProperties(GenericContainer<?> valkeyContainer) {
        return ValkeyContainerSupport.redisProperties(valkeyContainer);
    }
}
