package io.github.smiskinext.shared.infrastructure.outbox;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Encodes domain events into CloudEvents 1.0 structured JSON for storage in the outbox TEXT column,
 * and decodes stored payloads back to CloudEvent instances for transport publishing.
 */
public class CloudEventEncoder {

    private final EventFormat eventFormat;
    private final String source;

    public CloudEventEncoder(String source) {
        this.source = source;
        this.eventFormat = EventFormatProvider.getInstance()
                .resolveFormat(io.cloudevents.jackson.JsonFormat.CONTENT_TYPE);
        if (this.eventFormat == null) {
            throw new IllegalStateException(
                    "CloudEvents JSON format not available. Ensure cloudevents-json-jackson is on the classpath.");
        }
    }

    public String encode(
            UUID eventId,
            String eventType,
            String dataSchema,
            Instant time,
            String subject,
            Message protoData) {
        String protoJson = toProtoJson(protoData);
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(eventId.toString())
                .withType(eventType)
                .withSource(URI.create(source))
                .withDataContentType("application/json")
                .withDataSchema(URI.create(dataSchema))
                .withTime(OffsetDateTime.ofInstant(time, ZoneOffset.UTC))
                .withSubject(subject)
                .withData("application/json", protoJson.getBytes(StandardCharsets.UTF_8))
                .build();

        byte[] serialized = eventFormat.serialize(cloudEvent);
        return new String(serialized, StandardCharsets.UTF_8);
    }

    public CloudEvent decode(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        CloudEvent event = eventFormat.deserialize(bytes);
        if (event == null) {
            throw new IllegalStateException("Failed to deserialize CloudEvent from stored payload");
        }
        return event;
    }

    private String toProtoJson(Message message) {
        try {
            return JsonFormat.printer().print(message);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Failed to convert proto message to JSON", e);
        }
    }
}
