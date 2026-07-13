package io.github.smiskinext.tenant.config;

import io.github.smiskinext.shared.testcontainers.KafkaContainerSupport;
import io.github.smiskinext.shared.testcontainers.PostgresContainerSupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return PostgresContainerSupport.postgres();
    }

    @Bean
    public KafkaContainer kafkaContainer() {
        return KafkaContainerSupport.kafka();
    }

    @Bean
    public DynamicPropertyRegistrar kafkaProperties(KafkaContainer kafkaContainer) {
        return registry -> {
            registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        };
    }
}
