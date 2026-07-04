package io.github.smiskinext.meetingmanagement.infrastructure.storage;

import io.github.smiskinext.meetingmanagement.domain.port.StoragePort;
import io.github.smiskinext.meetingmanagement.infrastructure.config.LiveKitProperties;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3StorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageAdapter(LiveKitProperties liveKitProperties) {
        var recording = liveKitProperties.getRecording();
        this.bucket = recording.getBucket();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(recording.getEndpoint()))
                .region(Region.of(recording.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        recording.getAccessKey(), recording.getSecretKey())))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(recording.isForcePathStyle())
                        .build())
                .build();
    }

    @Override
    public @Nullable String generatePresignedUrl(String objectKey, Duration expiration) {
        try {
            var presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build())
                    .build();

            var presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.warn(
                    "Failed to generate presigned URL for key '{}': {}", objectKey, e.getMessage());
            return null;
        }
    }
}
