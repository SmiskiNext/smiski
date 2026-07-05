package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a {@link MeetingInvitee}.
 */
public record InviteeId(UUID value) implements ValueObject {

    public InviteeId {
        Objects.requireNonNull(value, "InviteeId value must not be null");
    }

    public static InviteeId of(UUID value) {
        return new InviteeId(value);
    }
}
