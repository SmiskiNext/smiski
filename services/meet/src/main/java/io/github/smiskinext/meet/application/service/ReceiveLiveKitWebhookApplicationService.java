package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.usecase.ReceiveLiveKitWebhookUseCase;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookPublisher;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookVerifier;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookVerifier.Verification;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Verifies a raw LiveKit webhook and, on a valid signature, enqueues the decoded event for
 * asynchronous processing. No database work is performed inline so the endpoint acknowledges fast.
 */
@Service
public class ReceiveLiveKitWebhookApplicationService implements ReceiveLiveKitWebhookUseCase {

    private final LiveKitWebhookVerifier verifier;
    private final LiveKitWebhookPublisher publisher;

    public ReceiveLiveKitWebhookApplicationService(
            LiveKitWebhookVerifier verifier, LiveKitWebhookPublisher publisher) {
        this.verifier = verifier;
        this.publisher = publisher;
    }

    @Override
    public Acknowledgement receive(String rawBody, @Nullable String authHeader) {
        Verification verification = verifier.verify(rawBody, authHeader);
        return switch (verification.status()) {
            case VALID -> {
                publisher.publish(verification.event());
                yield Acknowledgement.ACCEPTED;
            }
            case INVALID_SIGNATURE -> Acknowledgement.INVALID_SIGNATURE;
            case MALFORMED -> Acknowledgement.MALFORMED;
        };
    }
}
