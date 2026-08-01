package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.application.command.RelayJoinResolvedCommand;
import io.github.smiskinext.notification.application.usecase.RelayJoinResolvedUseCase;
import io.github.smiskinext.notification.domain.model.JoinDecision;
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

    private final RelayJoinResolvedUseCase relayJoinResolvedUseCase =
            mock(RelayJoinResolvedUseCase.class);

    private final JoinResolvedEventConsumer consumer =
            new JoinResolvedEventConsumer(relayJoinResolvedUseCase);

    @Test
    void approvedEventDelegatesToUseCase() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(
                event(TYPE_APPROVED, """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","liveKitToken":"the-token",\
                "roomName":"meeting-%s","approvedBy":"host","occurredAt":"%s"}""".formatted(meetingId, requestId, meetingId, OCCURRED_AT)));

        ArgumentCaptor<RelayJoinResolvedCommand> captor =
                ArgumentCaptor.forClass(RelayJoinResolvedCommand.class);
        verify(relayJoinResolvedUseCase).execute(captor.capture());

        RelayJoinResolvedCommand cmd = captor.getValue();
        assertThat(cmd.requestId()).isEqualTo(requestId);
        assertThat(cmd.decision().status()).isEqualTo(JoinDecision.Status.APPROVED);
        assertThat(cmd.decision().token()).isEqualTo("the-token");
        assertThat(cmd.decision().roomName()).isEqualTo("meeting-" + meetingId);
    }

    @Test
    void deniedEventDelegatesToUseCase() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event(TYPE_DENIED, """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","deniedBy":"host",\
                "occurredAt":"%s"}""".formatted(meetingId, requestId, OCCURRED_AT)));

        ArgumentCaptor<RelayJoinResolvedCommand> captor =
                ArgumentCaptor.forClass(RelayJoinResolvedCommand.class);
        verify(relayJoinResolvedUseCase).execute(captor.capture());

        RelayJoinResolvedCommand cmd = captor.getValue();
        assertThat(cmd.requestId()).isEqualTo(requestId);
        assertThat(cmd.decision().status()).isEqualTo(JoinDecision.Status.DENIED);
        assertThat(cmd.decision().token()).isNull();
    }

    @Test
    void unknownEventTypeIsSkipped() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        consumer.onMessage(event("io.github.smiskinext.meet.join.unknown.v1", """
                {"meetingId":"%s","joinRequestId":"%s","liveKitToken":"the-token",\
                "roomName":"room","occurredAt":"%s"}""".formatted(
                        meetingId, requestId, OCCURRED_AT)));

        verifyNoInteractions(relayJoinResolvedUseCase);
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

        verifyNoInteractions(relayJoinResolvedUseCase);
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

        verify(relayJoinResolvedUseCase).execute(any(RelayJoinResolvedCommand.class));
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
