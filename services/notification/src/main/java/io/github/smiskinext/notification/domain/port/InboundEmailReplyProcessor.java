package io.github.smiskinext.notification.domain.port;

/**
 * Port for processing inbound email replies (iMIP METHOD:REPLY).
 *
 * <p>Implementations fetch the full email, parse the calendar attachment, and publish the
 * email-reply event when extraction succeeds.
 */
public interface InboundEmailReplyProcessor {

    void processInboundEmail(String webhookPayload);
}
