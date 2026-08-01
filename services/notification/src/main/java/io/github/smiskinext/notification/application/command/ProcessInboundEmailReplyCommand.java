package io.github.smiskinext.notification.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.Map;

/**
 * Command to process an inbound Resend email webhook, including Svix signature verification.
 *
 * @param payload the raw webhook request body
 * @param headers the HTTP request headers carrying the Svix signature
 */
public record ProcessInboundEmailReplyCommand(String payload, Map<String, String> headers)
        implements Command {}
