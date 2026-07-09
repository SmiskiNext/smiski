package io.github.smiskinext.meet.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipationLogId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Participation log aggregate — append-only event log of join/leave events.
 *
 * <p>Each row represents one participation session (one device joining once).
 * A participant rejoining creates a new row.
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

    private final TenantId tenantId;
    private final ParticipationLogId id;
    private final MeetingId meetingId;
    private final AccountId accountId;
    private final @Nullable String displayName;
    private final @Nullable Instant displayNameCachedAt;
    private final ParticipantRole role;
    private final LiveKitIdentity livekitIdentity;
    private final Instant joinedAt;

    private @Nullable LiveKitParticipantSid livekitParticipantSid;
    private @Nullable Instant leftAt;
    private @Nullable CloseReason closeReason;

    // -------------------------------------------------------------------------
    // Private constructor
    // -------------------------------------------------------------------------

    private ParticipationLog(
            TenantId tenantId,
            ParticipationLogId id,
            MeetingId meetingId,
            AccountId accountId,
            @Nullable String displayName,
            @Nullable Instant displayNameCachedAt,
            ParticipantRole role,
            LiveKitIdentity livekitIdentity,
            @Nullable LiveKitParticipantSid livekitParticipantSid,
            Instant joinedAt,
            @Nullable Instant leftAt,
            @Nullable CloseReason closeReason) {
        this.tenantId = tenantId;
        this.id = id;
        this.meetingId = meetingId;
        this.accountId = accountId;
        this.displayName = displayName;
        this.displayNameCachedAt = displayNameCachedAt;
        this.role = role;
        this.livekitIdentity = livekitIdentity;
        this.livekitParticipantSid = livekitParticipantSid;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.closeReason = closeReason;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Records a participant joining a meeting (token issued, not yet connected to LiveKit).
     */
    public static ParticipationLog join(
            TenantId tenantId,
            MeetingId meetingId,
            AccountId accountId,
            String displayName,
            ParticipantRole role,
            LiveKitIdentity livekitIdentity) {
        return new ParticipationLog(
                tenantId,
                ParticipationLogId.of(UuidCreator.getTimeOrderedEpoch()),
                meetingId,
                accountId,
                displayName,
                Instant.now(),
                role,
                livekitIdentity,
                null,
                Instant.now(),
                null,
                null);
    }

    /**
     * Reconstitutes from persistence.
     */
    public static ParticipationLog reconstitute(
            TenantId tenantId,
            ParticipationLogId id,
            MeetingId meetingId,
            AccountId accountId,
            @Nullable String displayName,
            @Nullable Instant displayNameCachedAt,
            ParticipantRole role,
            LiveKitIdentity livekitIdentity,
            @Nullable LiveKitParticipantSid livekitParticipantSid,
            Instant joinedAt,
            @Nullable Instant leftAt,
            @Nullable CloseReason closeReason) {
        return new ParticipationLog(
                tenantId,
                id,
                meetingId,
                accountId,
                displayName,
                displayNameCachedAt,
                role,
                livekitIdentity,
                livekitParticipantSid,
                joinedAt,
                leftAt,
                closeReason);
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
        this.closeReason = CloseReason.LEFT;
    }

    /**
     * Force-closes an orphaned active session when the same identity rejoins.
     * Idempotent: if already closed, does nothing.
     */
    public void supersede(Instant at) {
        if (this.leftAt != null) return;
        this.leftAt = at;
        this.closeReason = CloseReason.SUPERSEDED;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    @Override
    public ParticipationLogId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public MeetingId getMeetingId() {
        return meetingId;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public Optional<Instant> getDisplayNameCachedAt() {
        return Optional.ofNullable(displayNameCachedAt);
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

    public Optional<CloseReason> getCloseReason() {
        return Optional.ofNullable(closeReason);
    }
}
