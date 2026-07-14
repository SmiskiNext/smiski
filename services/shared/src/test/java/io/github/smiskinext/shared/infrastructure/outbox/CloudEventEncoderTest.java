package io.github.smiskinext.shared.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.cloudevents.CloudEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CloudEventEncoderTest {

    private final CloudEventEncoder encoder = new CloudEventEncoder("test-service");

    @Test
    void encode_produces_valid_cloudevent_json_with_proto_data() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        Struct protoMessage = Struct.newBuilder()
                .putFields("key", Value.newBuilder().setStringValue("value").build())
                .build();

        String encoded = encoder.encode(
                eventId, "test.event.v1", "test.schema.v1", now, "subject-1", protoMessage);

        assertThat(encoded).contains("\"specversion\":\"1.0\"");
        assertThat(encoded).contains("\"type\":\"test.event.v1\"");
        assertThat(encoded).contains("\"datacontenttype\":\"application/json\"");
        assertThat(encoded).contains("\"id\":\"" + eventId + "\"");
    }

    @Test
    void encode_and_decode_roundtrip() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        Struct protoMessage = Struct.newBuilder()
                .putFields(
                        "test", Value.newBuilder().setStringValue("roundtrip").build())
                .build();

        String encoded = encoder.encode(
                eventId, "test.event.v1", "test.schema.v1", now, "subject-1", protoMessage);

        CloudEvent decoded = encoder.decode(encoded);
        assertThat(decoded.getId()).isEqualTo(eventId.toString());
        assertThat(decoded.getType()).isEqualTo("test.event.v1");
        assertThat(decoded.getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void decode_malformed_payload_throws() {
        assertThatThrownBy(() -> encoder.decode("not-valid-json")).isInstanceOf(Exception.class);
    }
}
