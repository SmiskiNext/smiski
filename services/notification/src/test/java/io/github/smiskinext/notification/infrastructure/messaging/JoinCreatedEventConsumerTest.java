package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.application.sse.JoinRequestCreatedData;
import io.github.smiskinext.notification.application.sse.SseConnectionManager;
import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JoinCreatedEventConsumerTest {

    private static final String TYPE = "io.github.smiskinext.meet.join.created.v1";
    private static final String OCCURRED_AT = "2026-01-01T00:00:00Z";

    private final SseConnectionManager sseConnectionManager = mock(SseConnectionManager.class);
    private final PendingJoinRequestStore pendingJoinRequestStore =
            mock(PendingJoinRequestStore.class);

    private final JoinCreatedEventConsumer consumer =
            new JoinCreatedEventConsumer(sseConnectionManager, pendingJoinRequestStore);

    @Test
    void validEventIsStoredAndPushed() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event("""
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","displayName":"Alice","deviceId":"device-1",\
                "occurredAt":"%s","avatarUrl":"https://cdn.example.com/a.png"}""".formatted(meetingId, requestId, OCCURRED_AT)));

        ArgumentCaptor<PendingJoinRequest> requestCaptor =
                ArgumentCaptor.forClass(PendingJoinRequest.class);
        verify(pendingJoinRequestStore).upsert(requestCaptor.capture());
        PendingJoinRequest stored = requestCaptor.getValue();
        assertThat(stored.joinRequestId()).isEqualTo(requestId);
        assertThat(stored.meetingId()).isEqualTo(meetingId);
        assertThat(stored.accountId()).isEqualTo("account-1");
        assertThat(stored.displayName()).isEqualTo("Alice");
        assertThat(stored.deviceId()).isEqualTo("device-1");
        assertThat(stored.avatarUrl()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(stored.expiresAt())
                .isEqualTo(Instant.parse(OCCURRED_AT).plus(Duration.ofMinutes(5)));

        ArgumentCaptor<JoinRequestCreatedData> dataCaptor =
                ArgumentCaptor.forClass(JoinRequestCreatedData.class);
        verify(sseConnectionManager).pushJoinRequestCreated(eq(meetingId), dataCaptor.capture());
        JoinRequestCreatedData pushed = dataCaptor.getValue();
        assertThat(pushed.requestId()).isEqualTo(requestId.toString());
        assertThat(pushed.accountId()).isEqualTo("account-1");
        assertThat(pushed.displayName()).isEqualTo("Alice");
        assertThat(pushed.avatarUrl()).isEqualTo("https://cdn.example.com/a.png");
    }

    @Test
    void missingAccountIdIsSkipped() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event("""
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "displayName":"Alice","deviceId":"device-1","occurredAt":"%s"}""".formatted(meetingId, requestId, OCCURRED_AT)));

        verifyNoInteractions(pendingJoinRequestStore);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void unknownExtraFieldStillSucceeds() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event("""
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","displayName":"Alice","deviceId":"device-1",\
                "occurredAt":"%s","surpriseField":"ignored"}""".formatted(meetingId, requestId, OCCURRED_AT)));

        verify(pendingJoinRequestStore).upsert(any(PendingJoinRequest.class));
        verify(sseConnectionManager)
                .pushJoinRequestCreated(eq(meetingId), any(JoinRequestCreatedData.class));
    }

    @Test
    void blankAvatarUrlIsMappedToNull() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event("""
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","displayName":"Alice","deviceId":"device-1",\
                "occurredAt":"%s","avatarUrl":""}""".formatted(meetingId, requestId, OCCURRED_AT)));

        ArgumentCaptor<PendingJoinRequest> requestCaptor =
                ArgumentCaptor.forClass(PendingJoinRequest.class);
        verify(pendingJoinRequestStore).upsert(requestCaptor.capture());
        assertThat(requestCaptor.getValue().avatarUrl()).isNull();
    }

    private static CloudEvent event(String json) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE)
                .withSource(URI.create("/meet"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
