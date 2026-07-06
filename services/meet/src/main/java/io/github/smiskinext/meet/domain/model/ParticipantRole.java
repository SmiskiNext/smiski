package io.github.smiskinext.meet.domain.model;

public enum ParticipantRole {
    /**
     * Meeting host — full publish, room admin, can record.
     */
    HOST,
    /**
     * Authenticated user — can publish audio/video, can chat.
     */
    PARTICIPANT,
    /**
     * Unauthenticated guest — subscribe-only, can chat, cannot publish media.
     */
    GUEST
}
