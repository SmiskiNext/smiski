package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.application.sse.SseConnectionManager;
import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JoinResolvedEventConsumerTest {

    private static final String TYPE_APPROVED = "io.github.smiskinext.meet.join.approved.v1";
    private static final String TYPE_DENIED = "io.github.smiskinext.meet.join.denied.v1";
    private static final String OCCURRED_AT = "2026-01-01T00:00:00Z";

    private final SseConnectionManager sseConnectionManager = mock(SseConnectionManager.class);
    private final JoinDecisionStore joinDecisionStore = mock(JoinDecisionStore.class);

    private final JoinResolvedEventConsumer consumer =
            new JoinResolvedEventConsumer(sseConnectionManager, joinDecisionStore);

    @Test
    void approvedEventIsStoredAndPushed() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(
                event(TYPE_APPROVED, """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","liveKitToken":"the-token",\
                "roomName":"meeting-%s","approvedBy":"host","occurredAt":"%s"}""".formatted(meetingId, requestId, meetingId, OCCURRED_AT)));

        ArgumentCaptor<JoinDecision> decisionCaptor = ArgumentCaptor.forClass(JoinDecision.class);
        verify(joinDecisionStore).upsert(decisionCaptor.capture());
        JoinDecision stored = decisionCaptor.getValue();
        assertThat(stored.joinRequestId()).isEqualTo(requestId);
        assertThat(stored.status()).isEqualTo(JoinDecision.Status.APPROVED);
        assertThat(stored.token()).isEqualTo("the-token");
        assertThat(stored.roomName()).isEqualTo("meeting-" + meetingId);

        verify(sseConnectionManager).pushJoinResolved(eq(requestId), decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().status()).isEqualTo(JoinDecision.Status.APPROVED);
    }

    @Test
    void deniedEventIsStoredAndPushed() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event(TYPE_DENIED, """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","deniedBy":"host",\
                "occurredAt":"%s"}""".formatted(meetingId, requestId, OCCURRED_AT)));

        ArgumentCaptor<JoinDecision> decisionCaptor = ArgumentCaptor.forClass(JoinDecision.class);
        verify(joinDecisionStore).upsert(decisionCaptor.capture());
        JoinDecision stored = decisionCaptor.getValue();
        assertThat(stored.joinRequestId()).isEqualTo(requestId);
        assertThat(stored.status()).isEqualTo(JoinDecision.Status.DENIED);
        assertThat(stored.token()).isNull();
        assertThat(stored.roomName()).isNull();

        verify(sseConnectionManager).pushJoinResolved(eq(requestId), decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().status()).isEqualTo(JoinDecision.Status.DENIED);
    }

    @Test
    void unknownEventTypeIsSkipped() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event("io.github.smiskinext.meet.join.unknown.v1", """
                {"meetingId":"%s","joinRequestId":"%s","liveKitToken":"the-token",\
                "roomName":"room","occurredAt":"%s"}""".formatted(
                        meetingId, requestId, OCCURRED_AT)));

        verifyNoInteractions(joinDecisionStore);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void approvedMissingLiveKitTokenIsSkipped() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(
                event(TYPE_APPROVED, """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","roomName":"meeting-%s",\
                "approvedBy":"host","occurredAt":"%s"}""".formatted(meetingId, requestId, meetingId, OCCURRED_AT)));

        verifyNoInteractions(joinDecisionStore);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void approvedMissingRoomNameIsSkipped() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event(TYPE_APPROVED, """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","liveKitToken":"the-token",\
                "approvedBy":"host","occurredAt":"%s"}""".formatted(meetingId, requestId, OCCURRED_AT)));

        verifyNoInteractions(joinDecisionStore);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void approvedMissingJoinRequestIdIsSkipped() {
        UUID meetingId = UUID.randomUUID();

        consumer.onMessage(event(TYPE_APPROVED, """
                {"meetingId":"%s","tenantId":"tenant-1","accountId":"account-1",\
                "deviceId":"device-1","liveKitToken":"the-token","roomName":"meeting-%s",\
                "approvedBy":"host","occurredAt":"%s"}""".formatted(meetingId, meetingId, OCCURRED_AT)));

        verifyNoInteractions(joinDecisionStore);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void deniedMissingJoinRequestIdIsSkipped() {
        UUID meetingId = UUID.randomUUID();

        consumer.onMessage(event(TYPE_DENIED, """
                {"meetingId":"%s","tenantId":"tenant-1","accountId":"account-1",\
                "deviceId":"device-1","deniedBy":"host","occurredAt":"%s"}""".formatted(meetingId, OCCURRED_AT)));

        verifyNoInteractions(joinDecisionStore);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void unknownExtraFieldStillSucceeds() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(
                event(TYPE_APPROVED, """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","liveKitToken":"the-token",\
                "roomName":"meeting-%s","approvedBy":"host","occurredAt":"%s",\
                "somethingNew":"x"}""".formatted(meetingId, requestId, meetingId, OCCURRED_AT)));

        ArgumentCaptor<JoinDecision> decisionCaptor = ArgumentCaptor.forClass(JoinDecision.class);
        verify(joinDecisionStore).upsert(decisionCaptor.capture());
        JoinDecision stored = decisionCaptor.getValue();
        assertThat(stored.joinRequestId()).isEqualTo(requestId);
        assertThat(stored.status()).isEqualTo(JoinDecision.Status.APPROVED);
        assertThat(stored.token()).isEqualTo("the-token");
        assertThat(stored.roomName()).isEqualTo("meeting-" + meetingId);

        verify(sseConnectionManager).pushJoinResolved(eq(requestId), decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().status()).isEqualTo(JoinDecision.Status.APPROVED);
    }

    private static CloudEvent event(String type, String json) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(type)
                .withSource(URI.create("/meet"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
