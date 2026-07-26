package io.github.smiskinext.meet.presentation;

import io.github.smiskinext.meet.application.usecase.ReceiveLiveKitWebhookUseCase;
import io.github.smiskinext.meet.application.usecase.ReceiveLiveKitWebhookUseCase.Acknowledgement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives LiveKit server webhooks. The endpoint resolves to {@code /api/1/webhooks/livekit} under
 * the shared integer-versioned API prefix and does not depend on the tenant header, since LiveKit
 * sends none.
 *
 * <p>The raw request body is consumed as a string with no JSON binding before verification, because
 * the LiveKit signature is a hash over the exact body bytes. Authentication is the signed-JWT
 * payload check; an invalid or missing signature yields {@code 401} with no side effects.
 */
@RestController
@Tag(name = "livekit-webhook-controller", description = "Receives LiveKit server webhooks")
public class LiveKitWebhookController {

    private final ReceiveLiveKitWebhookUseCase receiveLiveKitWebhookUseCase;

    public LiveKitWebhookController(ReceiveLiveKitWebhookUseCase receiveLiveKitWebhookUseCase) {
        this.receiveLiveKitWebhookUseCase = receiveLiveKitWebhookUseCase;
    }

    @Operation(
            summary = "Receive a LiveKit webhook",
            description =
                    "Verifies the LiveKit signed-JWT payload against the raw body and enqueues"
                            + " the decoded event for asynchronous processing.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Signature valid; event accepted"),
        @ApiResponse(responseCode = "400", description = "Body could not be decoded"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid signature")
    })
    @PostMapping(value = "/webhooks/livekit", consumes = "application/webhook+json")
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) @Nullable String authHeader) {
        Acknowledgement acknowledgement = receiveLiveKitWebhookUseCase.receive(rawBody, authHeader);
        return switch (acknowledgement) {
            case ACCEPTED -> ResponseEntity.ok().build();
            case INVALID_SIGNATURE ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case MALFORMED -> ResponseEntity.badRequest().build();
        };
    }
}
