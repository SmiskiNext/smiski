package io.github.smiskinext.shared.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * Factory for a reusable Valkey (Redis-compatible) testcontainer.
 *
 * <p>Per-service {@code @TestConfiguration} classes call these static methods and expose the result
 * as Spring beans. The {@link #redisProperties(GenericContainer)} helper registers the Spring Data
 * Redis connection properties.
 */
public final class ValkeyContainerSupport {

    private ValkeyContainerSupport() {}

    @SuppressWarnings("resource")
    public static GenericContainer<?> valkey() {
        return new GenericContainer<>("valkey/valkey:8-alpine").withExposedPorts(6379);
    }

    /**
     * Creates a {@link DynamicPropertyRegistrar} that wires {@code spring.data.redis.host} and
     * {@code spring.data.redis.port} from the given container.
     */
    public static DynamicPropertyRegistrar redisProperties(GenericContainer<?> container) {
        return registry -> {
            registry.add("spring.data.redis.host", container::getHost);
            registry.add(
                    "spring.data.redis.port",
                    () -> container.getMappedPort(6379).toString());
        };
    }
}
