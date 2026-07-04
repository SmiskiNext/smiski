package io.github.smiskinext.meetingmanagement.domain.port;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Port for generating time-limited access URLs for objects stored in external storage (S3/RustFS).
 */
public interface StoragePort {

    /**
     * Generates a presigned URL for the given object key in the recordings bucket.
     *
     * @param objectKey the S3 object key (e.g. "meetings/{id}/recording.mp4")
     * @param expiration how long the URL remains valid
     * @return presigned URL string, or {@code null} if generation fails
     */
    @Nullable String generatePresignedUrl(String objectKey, Duration expiration);
}
