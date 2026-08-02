package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

/**
 * Command to apply an email-driven invitee response (from an iMIP METHOD:REPLY).
 * Authorization is by email match only — no account identity is required.
 */
public record ApplyEmailInviteeResponseCommand(
        String calendarUid, String inviteeEmail, String status) implements Command {}
