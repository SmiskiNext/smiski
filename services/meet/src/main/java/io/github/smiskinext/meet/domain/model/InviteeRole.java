package io.github.smiskinext.meet.domain.model;

/**
 * Role of a meeting invitee, aligned with the iCalendar (RFC 5545) ROLE property.
 *
 * <p>Enum constants use underscores because Java identifiers forbid hyphens; the RFC wire
 * representation (e.g. {@code REQ-PARTICIPANT}) is produced by the calendar-serialization layer.
 */
public enum InviteeRole {
    CHAIR,
    REQ_PARTICIPANT,
    OPT_PARTICIPANT,
    NON_PARTICIPANT
}
