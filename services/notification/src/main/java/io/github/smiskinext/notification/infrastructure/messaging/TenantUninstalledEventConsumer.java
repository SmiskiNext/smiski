package io.github.smiskinext.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.tenant.v1.TenantUninstalled;
import io.github.smiskinext.notification.application.command.HandleTenantUninstalledCommand;
import io.github.smiskinext.notification.application.usecase.HandleTenantUninstalledUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code tenant.tenant.uninstalled} CloudEvents and upserts the local tenant projection
 * with {@code status = UNINSTALLED}, recording {@code uninstalledAt} and {@code purgeAfter}.
 *
 * <p>A malformed event is logged and skipped so the consumer keeps running without blocking
 * subsequent messages.
 */
@Component
public class TenantUninstalledEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TenantUninstalledEventConsumer.class);

    private final HandleTenantUninstalledUseCase handleTenantUninstalledUseCase;

    public TenantUninstalledEventConsumer(
            HandleTenantUninstalledUseCase handleTenantUninstalledUseCase) {
        this.handleTenantUninstalledUseCase = handleTenantUninstalledUseCase;
    }

    @KafkaListener(
            topics = "tenant.tenant.uninstalled",
            groupId =
                    "${app.notification.kafka.tenant-uninstalled-consumer-group:notification-tenant-uninstalled}",
            containerFactory = "tenantKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        TenantUninstalled proto;
        try {
            proto = decode(event);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping malformed tenant.tenant.uninstalled event id={}: {}",
                    event.getId(),
                    e.getMessage());
            return;
        }

        handleTenantUninstalledUseCase.handle(new HandleTenantUninstalledCommand(
                requireNonBlank(proto.getCloudId(), "cloudId"),
                proto.getCloudId(),
                parseInstant(proto.getUpdatedAt(), Instant.now()),
                parseInstantOrNull(proto.getUninstalledAt()),
                parseInstantOrNull(proto.getPurgeAfter())));
    }

    private TenantUninstalled decode(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data: id=" + event.getId());
        }
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        TenantUninstalled.Builder builder = TenantUninstalled.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(
                    "Malformed proto-JSON in tenant.tenant.uninstalled: " + e.getMessage(), e);
        }
        return builder.build();
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
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

    private static @Nullable Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
