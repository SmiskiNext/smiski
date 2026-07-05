package io.github.smiskinext.meetingmanagement.domain.model;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipationLogId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Participation log aggregate — append-only event log of join/leave events.
 *
 * <p>Each row represents one participation session (one device joining once).
 * A participant rejoining creates a new row. {@code user_id} is null for guest participants.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #join} — created when a token is issued ({@code RequestJoinUseCase} for
 *       {@code ALLOW_ALL}, {@code ApproveJoinRequestUseCase} for {@code MANUAL_APPROVAL});
 *       {@code livekitParticipantSid} is null at this point.
 *   <li>{@link #assignSid} — called by the {@code participant_joined} webhook handler
 *       once LiveKit confirms the participant connected.
 *   <li>{@link #leave} — called by the {@code participant_left} webhook handler.
 * </ol>
 */
public class ParticipationLog extends AggregateRoot<ParticipationLogId> {

    private @Nullable ParticipationLogId id;
    private final MeetingId meetingId;
    private final @Nullable UUID userId;
    private final String displayName;
    private final ParticipantRole role;
    private final LiveKitIdentity livekitIdentity;
    private final Instant joinedAt;

    private @Nullable LiveKitParticipantSid livekitParticipantSid;
    private @Nullable Instant leftAt;

    // -------------------------------------------------------------------------
    // Private constructor
    // -------------------------------------------------------------------------

    private ParticipationLog(
            @Nullable ParticipationLogId id,
            MeetingId meetingId,
            @Nullable UUID userId,
            String displayName,
            ParticipantRole role,
            LiveKitIdentity livekitIdentity,
            @Nullable LiveKitParticipantSid livekitParticipantSid,
            Instant joinedAt,
            @Nullable Instant leftAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
        this.livekitIdentity = livekitIdentity;
        this.livekitParticipantSid = livekitParticipantSid;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Records a participant joining a meeting (token issued, not yet connected to LiveKit).
     */
    public static ParticipationLog join(
            MeetingId meetingId,
            @Nullable UUID userId,
            String displayName,
            ParticipantRole role,
            LiveKitIdentity livekitIdentity) {
        return new ParticipationLog(
                null,
                meetingId,
                userId,
                displayName,
                role,
                livekitIdentity,
                null,
                Instant.now(),
                null);
    }

    /**
     * Reconstitutes from persistence.
     */
    public static ParticipationLog reconstitute(
            ParticipationLogId id,
            MeetingId meetingId,
            @Nullable UUID userId,
            String displayName,
            ParticipantRole role,
            LiveKitIdentity livekitIdentity,
            @Nullable LiveKitParticipantSid livekitParticipantSid,
            Instant joinedAt,
            @Nullable Instant leftAt) {
        return new ParticipationLog(
                id,
                meetingId,
                userId,
                displayName,
                role,
                livekitIdentity,
                livekitParticipantSid,
                joinedAt,
                leftAt);
    }

    // -------------------------------------------------------------------------
    // Domain behaviours
    // -------------------------------------------------------------------------

    /**
     * Assigns the LiveKit session ID once the {@code participant_joined} webhook arrives.
     * Can only be called once.
     */
    public void assignSid(LiveKitParticipantSid sid) {
        if (this.livekitParticipantSid != null) {
            throw new IllegalStateException("LiveKit participant SID already assigned");
        }
        this.livekitParticipantSid = sid;
    }

    /**
     * Records the time the participant left. Can only be called once.
     */
    public void leave(Instant leftAt) {
        if (this.leftAt != null) {
            throw new IllegalStateException("Participant already recorded as left");
        }
        this.leftAt = leftAt;
    }

    /**
     * Returns true if this is a guest participant (no registered user account).
     */
    public boolean isGuest() {
        return userId == null;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    @Override
    public ParticipationLogId getId() {
        return id;
    }

    /**
     * Called by the persistence adapter after insert to set the DB-generated id.
     */
    public void assignId(ParticipationLogId id) {
        if (this.id != null) throw new IllegalStateException("Id already assigned");
        this.id = id;
    }

    public MeetingId getMeetingId() {
        return meetingId;
    }

    public Optional<UUID> getUserId() {
        return Optional.ofNullable(userId);
    }

    public String getDisplayName() {
        return displayName;
    }

    public ParticipantRole getRole() {
        return role;
    }

    public LiveKitIdentity getLivekitIdentity() {
        return livekitIdentity;
    }

    public Optional<LiveKitParticipantSid> getLivekitParticipantSid() {
        return Optional.ofNullable(livekitParticipantSid);
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Optional<Instant> getLeftAt() {
        return Optional.ofNullable(leftAt);
    }
}
