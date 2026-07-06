package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.InviteToken;
import io.github.smiskinext.meet.domain.model.InviteTokenStatus;
import io.github.smiskinext.meet.domain.model.valueobject.InviteTokenId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying invite tokens.
 */
public interface InviteTokenRepository {

    /**
     * Persists a new or updated invite token.
     */
    InviteToken save(InviteToken token);

    /**
     * Finds a token by its identity.
     */
    Optional<InviteToken> findById(InviteTokenId id);

    /**
     * Returns all invite tokens for the given meeting.
     */
    List<InviteToken> findByMeetingId(UUID meetingId);

    /**
     * Returns all invite tokens for the given meeting filtered by status.
     */
    List<InviteToken> findByMeetingIdAndStatus(UUID meetingId, InviteTokenStatus status);

    /**
     * Marks all PENDING tokens for the given meeting as REVOKED and returns the count.
     */
    int revokeAllPendingByMeetingId(UUID meetingId);

    /**
     * Marks all PENDING tokens for the given invitee as REVOKED and returns the count.
     */
    int revokeAllPendingByInviteeId(UUID inviteeId);

    /**
     * Returns {@code true} if a token with the given hash exists in the repository.
     */
    boolean existsByTokenHash(String tokenHash);

    /**
     * Finds a token by its hash (used during validation).
     */
    Optional<InviteToken> findByTokenHash(String tokenHash);
}
