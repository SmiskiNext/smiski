package io.github.smiskinext.meetingmanagement.domain.model;

/**
 * Controls how participants are admitted to a meeting.
 *
 * <p>Designed as an extension point for future trust levels (e.g., DOMAIN_TRUSTED,
 * INVITED_ONLY, RESTRICTED). Adding a new policy only requires a new enum value
 * and a corresponding handler — no schema changes needed.
 */
public enum AdmissionPolicy {

    /**
     * All participants can join immediately without host approval.
     */
    ALLOW_ALL,

    /**
     * Participants must submit a join request and wait for host approval.
     */
    MANUAL_APPROVAL
}
