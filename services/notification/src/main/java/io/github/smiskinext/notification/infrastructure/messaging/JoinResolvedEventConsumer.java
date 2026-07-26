package io.github.smiskinext.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.meet.v1.JoinApproved;
import io.github.smiskinext.event.meet.v1.JoinDenied;
import io.github.smiskinext.notification.application.sse.SseConnectionManager;
import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code meet.join.approved} and {@code meet.join.denied} CloudEvents and delivers each
 * host decision to the requester that submitted the join request.
 *
 * <p>The outbox publishes each event as CloudEvents 1.0 structured JSON whose {@code data} is the
 * decision proto rendered as proto-JSON. This consumer parses the {@code data} back into the typed
 * {@link JoinApproved} or {@link JoinDenied} proto message via {@link JsonFormat}, records the
 * decision in {@link JoinDecisionStore} for replay, and pushes it to the requester emitters held
 * locally, keyed by join request id.
 *
 * <p>Each replica consumes under its own group (see {@code KafkaConfig}) so every replica receives
 * every decision and pushes to whatever emitters it holds. A message that cannot be decoded into a
 * valid decision event is logged and skipped so the consumer keeps running.
 */
@Component
public class JoinResolvedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(JoinResolvedEventConsumer.class);

    private static final String TYPE_APPROVED = "io.github.smiskinext.meet.join.approved.v1";
    private static final String TYPE_DENIED = "io.github.smiskinext.meet.join.denied.v1";

    private final SseConnectionManager sseConnectionManager;
    private final JoinDecisionStore joinDecisionStore;

    public JoinResolvedEventConsumer(
            SseConnectionManager sseConnectionManager, JoinDecisionStore joinDecisionStore) {
        this.sseConnectionManager = sseConnectionManager;
        this.joinDecisionStore = joinDecisionStore;
    }

    @KafkaListener(
            topics = {"meet.join.approved", "meet.join.denied"},
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        try {
            JoinDecision decision = decode(event);
            joinDecisionStore.upsert(decision);
            sseConnectionManager.pushJoinResolved(decision.joinRequestId(), decision);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping malformed join decision event id={} type={}: {}",
                    event.getId(),
                    event.getType(),
                    e.getMessage());
        }
    }

    private JoinDecision decode(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data");
        }

        String type = event.getType();
        if (TYPE_APPROVED.equals(type)) {
            return decodeApproved(data);
        }
        if (TYPE_DENIED.equals(type)) {
            return decodeDenied(data);
        }
        throw new IllegalArgumentException("Unsupported decision event type: " + type);
    }

    private JoinDecision decodeApproved(CloudEventData data) {
        JoinApproved.Builder builder = JoinApproved.newBuilder();
        merge(data, builder);
        JoinApproved proto = builder.build();

        UUID joinRequestId =
                UUID.fromString(requireNonBlank(proto.getJoinRequestId(), "joinRequestId"));
        return JoinDecision.approved(
                joinRequestId,
                requireNonBlank(proto.getLiveKitToken(), "liveKitToken"),
                requireNonBlank(proto.getRoomName(), "roomName"));
    }

    private JoinDecision decodeDenied(CloudEventData data) {
        JoinDenied.Builder builder = JoinDenied.newBuilder();
        merge(data, builder);
        JoinDenied proto = builder.build();

        UUID joinRequestId =
                UUID.fromString(requireNonBlank(proto.getJoinRequestId(), "joinRequestId"));
        return JoinDecision.denied(joinRequestId, null);
    }

    private static void merge(CloudEventData data, Message.Builder builder) {
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Malformed proto-JSON data: " + e.getMessage(), e);
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
