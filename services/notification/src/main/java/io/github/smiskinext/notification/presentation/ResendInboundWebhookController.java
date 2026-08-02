package io.github.smiskinext.notification.presentation;

import io.github.smiskinext.notification.application.command.ProcessInboundEmailReplyCommand;
import io.github.smiskinext.notification.application.usecase.ProcessInboundEmailReplyUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Resend inbound email webhooks (Svix-signed).
 *
 * <p>Delegates to {@link ProcessInboundEmailReplyUseCase} which performs Svix signature
 * verification and, if valid, processes the iMIP reply. An {@link NotificationError.InvalidSignature}
 * result maps to HTTP 400; success maps to HTTP 200.
 */
@RestController
@Tag(
        name = "resend-inbound-webhook-controller",
        description = "Receives Resend inbound email webhooks signed by Svix")
public class ResendInboundWebhookController {

    private final ProcessInboundEmailReplyUseCase processInboundEmailReplyUseCase;

    public ResendInboundWebhookController(
            ProcessInboundEmailReplyUseCase processInboundEmailReplyUseCase) {
        this.processInboundEmailReplyUseCase = processInboundEmailReplyUseCase;
    }

    @Operation(
            summary = "Receive a Resend inbound email webhook",
            description =
                    "Verifies the Svix signature from the request headers against the raw body and,"
                            + " if valid, processes the iMIP calendar reply.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Signature valid; email reply processed"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid Svix signature"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/webhooks/resend/inbound")
    public ResponseEntity<Void> handleInboundEmail(
            @RequestBody String payload, @RequestHeader Map<String, String> headers) {
        Result<?, NotificationError> result = processInboundEmailReplyUseCase.execute(
                new ProcessInboundEmailReplyCommand(payload, headers));
        return result.fold(success -> ResponseEntity.ok().<Void>build(), error -> switch (error) {
            case NotificationError.InvalidSignature ignored ->
                ResponseEntity.badRequest().<Void>build();
            default -> ResponseEntity.internalServerError().<Void>build();
        });
    }
}
