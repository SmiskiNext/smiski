package io.github.smiskinext.meetingmanagement.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.phunguy65.zms.meetingmanagement.domain.event.*;
import io.github.phunguy65.zms.meetingmanagement.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.domain.event.*;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.*;
import org.jspecify.annotations.Nullable;

/**
 * Meeting aggregate root.
 *
 * <p>Manages the meeting lifecycle: SCHEDULED → LIVE → ENDED, or SCHEDULED → CANCELLED.
 * Domain events are registered on each state transition and published by the infrastructure layer
 * via the Transactional Outbox pattern.
 */
public class Meeting extends AggregateRoot<MeetingId> {

    private final MeetingId id;
    private final UserId hostId;
    private final ShortCode shortCode;
    private final MeetingType type;
    private final Instant createdAt;

    private @Nullable MeetingTitle title;
    private @Nullable String description;
    private @Nullable MeetingTimeRange timeRange;
    private @Nullable Instant endTime;
    private MeetingStatus status;
    private MeetingSettings settings;

    // -------------------------------------------------------------------------
    // Private constructor — use factory methods
    // -------------------------------------------------------------------------

    private Meeting(
            MeetingId id,
            UserId hostId,
            ShortCode shortCode,
            @Nullable MeetingTitle title,
            @Nullable String description,
            @Nullable MeetingTimeRange timeRange,
            MeetingType type,
            MeetingStatus status,
            MeetingSettings settings,
            Instant createdAt) {
        this.id = id;
        this.hostId = hostId;
        this.shortCode = shortCode;
        this.title = title;
        this.description = description;
        this.timeRange = timeRange;
        this.type = type;
        this.status = status;
        this.settings = settings;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a new SCHEDULED meeting. Registers {@code MeetingScheduledEvent}.
     */
    public static Meeting schedule(
            UserId hostId,
            @Nullable MeetingTitle title,
            @Nullable String description,
            MeetingTimeRange timeRange,
            MeetingSettings settings,
            ShortCode shortCode) {
        MeetingId id = MeetingId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();
        Meeting meeting = new Meeting(
                id,
                hostId,
                shortCode,
                title,
                description,
                timeRange,
                MeetingType.SCHEDULED,
                MeetingStatus.SCHEDULED,
                settings,
                now);
        meeting.registerEvent(new MeetingScheduledEvent(
                UUID.randomUUID(),
                id.value(),
                hostId.value(),
                shortCode.value(),
                title != null ? title.value() : null,
                timeRange.start(),
                now));
        return meeting;
    }

    /**
     * Creates a new INSTANT meeting (starts immediately, no scheduled time).
     * Registers {@code MeetingScheduledEvent}.
     */
    public static Meeting instant(
            UserId hostId,
            @Nullable MeetingTitle title,
            @Nullable String description,
            MeetingSettings settings,
            ShortCode shortCode) {
        MeetingId id = MeetingId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();
        Meeting meeting = new Meeting(
                id,
                hostId,
                shortCode,
                title,
                description,
                null,
                MeetingType.INSTANT,
                MeetingStatus.SCHEDULED,
                settings,
                now);
        meeting.registerEvent(new MeetingScheduledEvent(
                UUID.randomUUID(),
                id.value(),
                hostId.value(),
                shortCode.value(),
                title != null ? title.value() : null,
                null,
                now));
        return meeting;
    }

    /**
     * Reconstitutes a Meeting from persistence. No domain events are registered.
     */
    public static Meeting reconstitute(
            MeetingId id,
            UserId hostId,
            ShortCode shortCode,
            @Nullable MeetingTitle title,
            @Nullable String description,
            @Nullable MeetingTimeRange timeRange,
            @Nullable Instant endTime,
            MeetingType type,
            MeetingStatus status,
            MeetingSettings settings,
            Instant createdAt) {
        Meeting meeting = new Meeting(
                id,
                hostId,
                shortCode,
                title,
                description,
                timeRange,
                type,
                status,
                settings,
                createdAt);
        meeting.endTime = endTime;
        return meeting;
    }

    // -------------------------------------------------------------------------
    // Domain behaviours
    // -------------------------------------------------------------------------

    /**
     * Transitions SCHEDULED → LIVE. Registers {@code MeetingStartedEvent}.
     */
    public Result<Void, MeetingError> start() {
        if (!status.canTransitionTo(MeetingStatus.LIVE)) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.LIVE));
        }
        status = MeetingStatus.LIVE;
        Instant now = Instant.now();
        registerEvent(new MeetingStartedEvent(
                UUID.randomUUID(),
                id.value(),
                hostId.value(),
                LiveKitRoomName.fromMeetingId(id).value(),
                now));
        return Result.success();
    }

    /**
     * Transitions LIVE → ENDED. Registers {@code MeetingEndedEvent}.
     */
    public Result<Void, MeetingError> end() {
        if (!status.canTransitionTo(MeetingStatus.ENDED)) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.ENDED));
        }
        status = MeetingStatus.ENDED;
        Instant now = Instant.now();
        this.endTime = now;
        registerEvent(new MeetingEndedEvent(UUID.randomUUID(), id.value(), hostId.value(), now));
        return Result.success();
    }

    /**
     * Updates meeting settings when status is SCHEDULED or LIVE.
     * Registers {@code MeetingSettingsUpdatedEvent} with both old and new settings snapshots.
     *
     * @param newSettings the new settings to apply
     * @param updatedBy   the user ID performing the update
     * @return success, or failure with {@code InvalidStatusTransition} if meeting is ENDED/CANCELLED
     */
    public Result<Void, MeetingError> updateSettings(MeetingSettings newSettings, UUID updatedBy) {
        if (status != MeetingStatus.SCHEDULED && status != MeetingStatus.LIVE) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.SCHEDULED));
        }
        MeetingSettings oldSettings = this.settings;
        this.settings = newSettings;
        Instant now = Instant.now();
        registerEvent(new MeetingSettingsUpdatedEvent(
                UUID.randomUUID(),
                id.value(),
                hostId.value(),
                updatedBy,
                status,
                oldSettings,
                newSettings,
                now));
        return Result.success();
    }

    /**
     * Transitions SCHEDULED → CANCELLED. Registers {@code MeetingCancelledEvent}.
     */
    public Result<Void, MeetingError> cancel() {
        return cancel(
                title != null ? title.value() : null,
                shortCode.value(),
                timeRange != null ? timeRange.start() : null,
                List.of());
    }

    /**
     * Transitions SCHEDULED → CANCELLED and includes notification payload for invitees.
     */
    public Result<Void, MeetingError> cancel(
            @Nullable String meetingTitle,
            String meetingShortCode,
            @Nullable Instant startTime,
            List<MeetingCancelledEvent.InviteeInfo> invitees) {
        if (!status.canTransitionTo(MeetingStatus.CANCELLED)) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.CANCELLED));
        }
        status = MeetingStatus.CANCELLED;
        Instant now = Instant.now();
        registerEvent(new MeetingCancelledEvent(
                UUID.randomUUID(),
                id.value(),
                hostId.value(),
                meetingTitle,
                meetingShortCode,
                startTime,
                List.copyOf(invitees),
                now));
        return Result.success();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    @Override
    public MeetingId getId() {
        return id;
    }

    public UserId getHostId() {
        return hostId;
    }

    public ShortCode getShortCode() {
        return shortCode;
    }

    public Optional<MeetingTitle> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<MeetingTimeRange> getTimeRange() {
        return Optional.ofNullable(timeRange);
    }

    /**
     * Convenience accessor — start time from the time range, if present.
     */
    public Optional<Instant> getStartTime() {
        return Optional.ofNullable(timeRange).map(MeetingTimeRange::start);
    }

    /**
     * End time: uses the actual ended-at timestamp when the meeting has ended,
     * otherwise falls back to the scheduled end from the time range.
     */
    public Optional<Instant> getEndTime() {
        if (endTime != null) return Optional.of(endTime);
        return Optional.ofNullable(timeRange).map(MeetingTimeRange::end);
    }

    public MeetingType getType() {
        return type;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public MeetingSettings getSettings() {
        return settings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
