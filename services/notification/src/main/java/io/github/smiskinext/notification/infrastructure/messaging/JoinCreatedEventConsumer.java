package io.github.smiskinext.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.meet.v1.JoinCreated;
import io.github.smiskinext.notification.application.command.RelayJoinCreatedCommand;
import io.github.smiskinext.notification.application.usecase.RelayJoinCreatedUseCase;
import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code meet.join.created} CloudEvents and relays them to meeting hosts.
 *
 * <p>The outbox publishes each event as CloudEvents 1.0 structured JSON whose {@code data} is the
 * {@code JoinCreated} proto rendered as proto-JSON. This consumer parses the {@code data} back into
 * the typed {@link JoinCreated} proto message via {@link JsonFormat}, constructs a
 * {@link RelayJoinCreatedCommand}, and delegates to {@link RelayJoinCreatedUseCase} which
 * persists the pending request and pushes it to all locally-held host emitters.
 *
 * <p>Each replica consumes under its own group (see {@code KafkaConfig}) so every replica receives
 * every event and pushes to whatever emitters it holds. A message that cannot be decoded into a
 * valid join event is logged and skipped so the consumer keeps running.
 */
@Component
public class JoinCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(JoinCreatedEventConsumer.class);

    private static final Duration JOIN_REQUEST_TTL = Duration.ofMinutes(5);

    private final RelayJoinCreatedUseCase relayJoinCreatedUseCase;

    public JoinCreatedEventConsumer(RelayJoinCreatedUseCase relayJoinCreatedUseCase) {
        this.relayJoinCreatedUseCase = relayJoinCreatedUseCase;
    }

    @KafkaListener(
            topics = "meet.join.created",
            containerFactory = "joinCreatedKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        try {
            PendingJoinRequest request = decode(event);
            relayJoinCreatedUseCase.execute(
                    new RelayJoinCreatedCommand(request.meetingId(), request));
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping malformed meet.join.created event id={}: {}",
                    event.getId(),
                    e.getMessage());
        }
    }

    private PendingJoinRequest decode(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data");
        }
        JoinCreated proto = parse(data);

        UUID joinRequestId =
                UUID.fromString(requireNonBlank(proto.getJoinRequestId(), "joinRequestId"));
        UUID meetingId = UUID.fromString(requireNonBlank(proto.getMeetingId(), "meetingId"));
        String accountId = requireNonBlank(proto.getAccountId(), "accountId");
        String displayName = requireNonBlank(proto.getDisplayName(), "displayName");
        String deviceId = requireNonBlank(proto.getDeviceId(), "deviceId");
        String avatarUrl = blankToNull(proto.getAvatarUrl());
        Instant occurredAt = Instant.parse(requireNonBlank(proto.getOccurredAt(), "occurredAt"));

        return new PendingJoinRequest(
                joinRequestId,
                meetingId,
                accountId,
                displayName,
                deviceId,
                avatarUrl,
                occurredAt.plus(JOIN_REQUEST_TTL));
    }

    private static JoinCreated parse(CloudEventData data) {
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        JoinCreated.Builder builder = JoinCreated.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Malformed proto-JSON data: " + e.getMessage(), e);
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
}
