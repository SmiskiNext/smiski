package io.github.smiskinext.shared.testcontainers;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Factory for a reusable PostgreSQL testcontainer.
 *
 * <p>Per-service {@code @TestConfiguration} classes call these static methods and expose the result
 * as Spring beans.
 */
public final class PostgresContainerSupport {

    private PostgresContainerSupport() {}

    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }
}
