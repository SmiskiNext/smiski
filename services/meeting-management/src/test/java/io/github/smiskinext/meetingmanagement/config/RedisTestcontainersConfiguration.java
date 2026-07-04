package io.github.smiskinext.meetingmanagement.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * Testcontainers configuration that provides a Redis 7 container for integration tests.
 *
 * <p>Automatically registers the Redis host and port as Spring properties via
 * {@link DynamicPropertyRegistrar}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {

    @Bean
    @SuppressWarnings("resource")
    public GenericContainer<?> redisContainer() {
        GenericContainer<?> container =
                new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        container.start();
        return container;
    }

    @Bean
    public DynamicPropertyRegistrar redisProperties(GenericContainer<?> redisContainer) {
        return registry -> {
            registry.add("spring.data.redis.host", redisContainer::getHost);
            registry.add(
                    "spring.data.redis.port",
                    () -> redisContainer.getMappedPort(6379).toString());
        };
    }
}
