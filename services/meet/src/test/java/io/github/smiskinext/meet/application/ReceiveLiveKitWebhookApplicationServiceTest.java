package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.service.ReceiveLiveKitWebhookApplicationService;
import io.github.smiskinext.meet.application.usecase.ReceiveLiveKitWebhookUseCase.Acknowledgement;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookPublisher;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookVerifier;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookVerifier.Verification;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiveLiveKitWebhookApplicationServiceTest {

    private LiveKitWebhookVerifier verifier;
    private LiveKitWebhookPublisher publisher;
    private ReceiveLiveKitWebhookApplicationService service;

    @BeforeEach
    void setUp() {
        verifier = mock(LiveKitWebhookVerifier.class);
        publisher = mock(LiveKitWebhookPublisher.class);
        service = new ReceiveLiveKitWebhookApplicationService(verifier, publisher);
    }

    @Test
    void validSignatureEnqueuesEventAndAcceptsFast() {
        LiveKitWebhookEvent event = new LiveKitWebhookEvent(
                "room_started",
                "meeting-" + UUID.randomUUID(),
                "tenant-1",
                null,
                null,
                Map.of(),
                null,
                null,
                UUID.randomUUID().toString(),
                Instant.now());
        when(verifier.verify("body", "auth")).thenReturn(Verification.valid(event));

        Acknowledgement result = service.receive("body", "auth");

        assertThat(result).isEqualTo(Acknowledgement.ACCEPTED);
        verify(publisher).publish(event);
    }

    @Test
    void invalidSignatureIsRejectedWithoutSideEffects() {
        when(verifier.verify(any(), any())).thenReturn(Verification.invalidSignature());

        Acknowledgement result = service.receive("body", null);

        assertThat(result).isEqualTo(Acknowledgement.INVALID_SIGNATURE);
        verify(publisher, never()).publish(any());
    }

    @Test
    void malformedBodyIsRejectedWithoutSideEffects() {
        when(verifier.verify(any(), any())).thenReturn(Verification.malformed());

        Acknowledgement result = service.receive("body", "auth");

        assertThat(result).isEqualTo(Acknowledgement.MALFORMED);
        verify(publisher, never()).publish(any());
    }
}
