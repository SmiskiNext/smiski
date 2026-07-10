package io.github.smiskinext.shared.testcontainers;

import org.testcontainers.containers.MinIOContainer;

/**
 * Factory for a reusable MinIO testcontainer (S3-compatible object store).
 *
 * <p>Per-service {@code @TestConfiguration} classes call these static methods and expose the result
 * as Spring beans.
 */
public final class MinioContainerSupport {

    private MinioContainerSupport() {}

    public static MinIOContainer minio() {
        return new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");
    }
}
