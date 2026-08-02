package io.github.smiskinext.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.tenant.v1.TenantInstalled;
import io.github.smiskinext.notification.application.command.HandleTenantInstalledCommand;
import io.github.smiskinext.notification.application.usecase.HandleTenantInstalledUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code tenant.tenant.installed} CloudEvents and upserts the local tenant projection
 * with {@code status = ACTIVE}.
 *
 * <p>The event's {@code data} is a {@code TenantInstalled} proto rendered as proto-JSON.
 * A blank {@code site_url} is stored as {@code NULL}. Messages that cannot be decoded are
 * thrown so the configured error handler retries and routes to the dead-letter topic.
 */
@Component
public class TenantInstalledEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TenantInstalledEventConsumer.class);

    private final HandleTenantInstalledUseCase handleTenantInstalledUseCase;

    public TenantInstalledEventConsumer(HandleTenantInstalledUseCase handleTenantInstalledUseCase) {
        this.handleTenantInstalledUseCase = handleTenantInstalledUseCase;
    }

    @KafkaListener(
            topics = "tenant.tenant.installed",
            groupId =
                    "${app.notification.kafka.tenant-installed-consumer-group:notification-tenant-installed}",
            containerFactory = "tenantKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        log.debug("Received tenant.tenant.installed event id={}", event.getId());
        TenantInstalled proto = decode(event);
        handleTenantInstalledUseCase.handle(new HandleTenantInstalledCommand(
                requireNonBlank(proto.getCloudId(), "cloudId"),
                proto.getCloudId(),
                blankToNull(proto.getSiteUrl()),
                parseInstant(proto.getUpdatedAt(), Instant.now())));
    }

    private TenantInstalled decode(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data: id=" + event.getId());
        }
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        TenantInstalled.Builder builder = TenantInstalled.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(
                    "Malformed proto-JSON in tenant.tenant.installed: " + e.getMessage(), e);
        }
        return builder.build();
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static @Nullable String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
