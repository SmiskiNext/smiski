package io.github.smiskinext.meet.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.*;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Meeting aggregate root.
 *
 * <p>Manages the meeting lifecycle: SCHEDULED → RUNNING → COMPLETED, or SCHEDULED → CANCELED.
 * Domain events are registered on each state transition and published by the infrastructure layer
 * via the Transactional Outbox pattern.
 */
public class Meeting extends AggregateRoot<MeetingId> {

    /**
     * Grace window to tolerate minor clock skew and network latency when validating
     * that a scheduled meeting's start time is not in the past.
     */
    public static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(2);

    private final TenantId tenantId;
    private final MeetingId id;
    private final AccountId hostId;
    private final ShortCode shortCode;
    private final MeetingType type;
    private final Email organizerEmail;
    private final InviteeDisplayName organizerDisplayName;
    private final String calendarUid;
    private MeetingTimeZone timeZone;
    private int calendarSequence;
    private final Instant createdAt;
    private Instant updatedAt;

    private MeetingTitle title;
    private String description;
    private JiraIssueLink issueLink;
    private @Nullable MeetingTimeRange timeRange;
    private @Nullable Instant endTime;
    private MeetingStatus status;
    private MeetingSettings settings;
    private @Nullable CancelReason cancelReason;
    private @Nullable Instant deletedAt;
    private @Nullable AccountId deletedBy;
    private @Nullable Instant purgeAfter;

    // -------------------------------------------------------------------------
    // Private constructor — use factory methods
    // -------------------------------------------------------------------------

    private Meeting(
            TenantId tenantId,
            MeetingId id,
            AccountId hostId,
            ShortCode shortCode,
            MeetingTitle title,
            String description,
            JiraIssueLink issueLink,
            @Nullable MeetingTimeRange timeRange,
            MeetingType type,
            MeetingStatus status,
            MeetingSettings settings,
            MeetingTimeZone timeZone,
            Email organizerEmail,
            InviteeDisplayName organizerDisplayName,
            String calendarUid,
            int calendarSequence,
            Instant createdAt,
            Instant updatedAt) {
        this.tenantId = tenantId;
        this.id = id;
        this.hostId = hostId;
        this.shortCode = shortCode;
        this.title = title;
        this.description = description;
        this.issueLink = issueLink;
        this.timeRange = timeRange;
        this.type = type;
        this.status = status;
        this.settings = settings;
        this.timeZone = timeZone;
        this.organizerEmail = organizerEmail;
        this.organizerDisplayName = organizerDisplayName;
        this.calendarUid = calendarUid;
        this.calendarSequence = calendarSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a new SCHEDULED meeting. Registers {@code MeetingCreatedEvent}.
     *
     * <p>Validates that the scheduled start time is not in the past (allowing for a small
     * {@link #CLOCK_SKEW_TOLERANCE clock-skew grace window}).
     *
     * @return success with the new meeting, or failure with {@link MeetingError.StartTimeInPast}
     */
    public static Result<Meeting, MeetingError> schedule(
            TenantId tenantId,
            AccountId hostId,
            MeetingTitle title,
            String description,
            JiraIssueLink issueLink,
            MeetingTimeRange timeRange,
            MeetingSettings settings,
            MeetingTimeZone timeZone,
            Email organizerEmail,
            InviteeDisplayName organizerDisplayName,
            ShortCode shortCode) {
        Instant now = Instant.now();
        if (timeRange.start().isBefore(now.minus(CLOCK_SKEW_TOLERANCE))) {
            return Result.failure(new MeetingError.StartTimeInPast(timeRange.start()));
        }
        MeetingId id = MeetingId.of(UuidCreator.getTimeOrderedEpoch());
        String calendarUid = UUID.randomUUID().toString();
        Meeting meeting = new Meeting(
                tenantId,
                id,
                hostId,
                shortCode,
                title,
                description,
                issueLink,
                timeRange,
                MeetingType.SCHEDULED,
                MeetingStatus.SCHEDULED,
                settings,
                timeZone,
                organizerEmail,
                organizerDisplayName,
                calendarUid,
                0,
                now,
                now);
        meeting.registerEvent(new MeetingCreatedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                hostId.value(),
                shortCode.value(),
                MeetingType.SCHEDULED.name(),
                MeetingStatus.SCHEDULED.name(),
                title.value(),
                description,
                issueLink.issueId(),
                issueLink.issueKey(),
                issueLink.projectKey(),
                timeRange.start(),
                timeRange.end(),
                settings,
                timeZone.value(),
                organizerEmail.value(),
                organizerDisplayName.value(),
                calendarUid,
                0,
                now));
        return Result.success(meeting);
    }

    /**
     * Creates a new INSTANT meeting (starts immediately, no scheduled time).
     * Registers {@code MeetingCreatedEvent}.
     */
    public static Meeting instant(
            TenantId tenantId,
            AccountId hostId,
            MeetingTitle title,
            String description,
            JiraIssueLink issueLink,
            MeetingSettings settings,
            MeetingTimeZone timeZone,
            Email organizerEmail,
            InviteeDisplayName organizerDisplayName,
            ShortCode shortCode) {
        MeetingId id = MeetingId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();
        Meeting meeting = new Meeting(
                tenantId,
                id,
                hostId,
                shortCode,
                title,
                description,
                issueLink,
                null,
                MeetingType.INSTANT,
                MeetingStatus.SCHEDULED,
                settings,
                timeZone,
                organizerEmail,
                organizerDisplayName,
                UUID.randomUUID().toString(),
                0,
                now,
                now);
        meeting.registerEvent(new MeetingCreatedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                hostId.value(),
                shortCode.value(),
                MeetingType.INSTANT.name(),
                MeetingStatus.SCHEDULED.name(),
                title.value(),
                description,
                issueLink.issueId(),
                issueLink.issueKey(),
                issueLink.projectKey(),
                null,
                null,
                settings,
                timeZone.value(),
                organizerEmail.value(),
                organizerDisplayName.value(),
                meeting.calendarUid,
                0,
                now));
        return meeting;
    }

    /**
     * Reconstitutes a Meeting from persistence. No domain events are registered.
     */
    public static Meeting reconstitute(
            TenantId tenantId,
            MeetingId id,
            AccountId hostId,
            ShortCode shortCode,
            MeetingTitle title,
            String description,
            JiraIssueLink issueLink,
            @Nullable MeetingTimeRange timeRange,
            @Nullable Instant endTime,
            MeetingType type,
            MeetingStatus status,
            MeetingSettings settings,
            MeetingTimeZone timeZone,
            Email organizerEmail,
            InviteeDisplayName organizerDisplayName,
            String calendarUid,
            int calendarSequence,
            Instant createdAt,
            Instant updatedAt,
            @Nullable CancelReason cancelReason,
            @Nullable Instant deletedAt,
            @Nullable AccountId deletedBy,
            @Nullable Instant purgeAfter) {
        Meeting meeting = new Meeting(
                tenantId,
                id,
                hostId,
                shortCode,
                title,
                description,
                issueLink,
                timeRange,
                type,
                status,
                settings,
                timeZone,
                organizerEmail,
                organizerDisplayName,
                calendarUid,
                calendarSequence,
                createdAt,
                updatedAt);
        meeting.endTime = endTime;
        meeting.cancelReason = cancelReason;
        meeting.deletedAt = deletedAt;
        meeting.deletedBy = deletedBy;
        meeting.purgeAfter = purgeAfter;
        return meeting;
    }

    // -------------------------------------------------------------------------
    // Domain behaviours
    // -------------------------------------------------------------------------

    /**
     * Transitions SCHEDULED -> RUNNING. Registers {@code MeetingStartedEvent}.
     */
    public Result<Void, MeetingError> start() {
        if (!status.canTransitionTo(MeetingStatus.RUNNING)) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.RUNNING));
        }
        status = MeetingStatus.RUNNING;
        Instant now = Instant.now();
        this.updatedAt = now;
        registerEvent(new MeetingStartedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                hostId.value(),
                shortCode.value(),
                type.name(),
                MeetingStatus.RUNNING.name(),
                title.value(),
                description,
                issueLink.issueId(),
                issueLink.issueKey(),
                issueLink.projectKey(),
                timeRange != null ? timeRange.start() : null,
                null,
                settings,
                timeZone.value(),
                organizerEmail.value(),
                organizerDisplayName.value(),
                calendarUid,
                calendarSequence,
                createdAt,
                LiveKitRoomName.fromMeetingId(id).value(),
                now));
        return Result.success();
    }

    /**
     * Transitions RUNNING → COMPLETED. Registers {@code MeetingCompletedEvent}.
     */
    public Result<Void, MeetingError> complete() {
        if (!status.canTransitionTo(MeetingStatus.COMPLETED)) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.COMPLETED));
        }
        status = MeetingStatus.COMPLETED;
        Instant now = Instant.now();
        this.endTime = now;
        this.updatedAt = now;
        registerEvent(new MeetingCompletedEvent(
                UUID.randomUUID(), tenantId.value(), id.value(), hostId.value(), now));
        return Result.success();
    }

    /**
     * Records that invitations have been sent for this meeting.
     * Registers {@code MeetingInvitationsCreatedEvent} carrying the invitee details with embedded tokens.
     *
     * <p>Does nothing when the invitee list is empty.
     *
     * @param invitees list of invitee info snapshots (each carrying its own token)
     */
    public void recordInvitationsSent(List<MeetingInvitationsCreatedEvent.InviteeInfo> invitees) {
        if (invitees.isEmpty()) {
            return;
        }
        registerEvent(new MeetingInvitationsCreatedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                title.value(),
                shortCode.value(),
                timeRange != null ? timeRange.start() : null,
                timeRange != null ? timeRange.end() : null,
                timeZone.value(),
                organizerEmail.value(),
                organizerDisplayName.value(),
                calendarUid,
                calendarSequence,
                List.copyOf(invitees),
                Instant.now()));
    }

    /**
     * Records that one or more invitees had their display name updated for this meeting.
     * Registers {@code MeetingInvitationsUpdatedEvent} carrying only the affected invitees.
     *
     * <p>Does nothing when the invitee list is empty. Does not change {@code calendarSequence}.
     *
     * @param invitees list of updated invitee info snapshots
     */
    public void recordInviteesUpdated(List<MeetingInvitationsUpdatedEvent.InviteeInfo> invitees) {
        if (invitees.isEmpty()) {
            return;
        }
        registerEvent(new MeetingInvitationsUpdatedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                title.value(),
                shortCode.value(),
                timeRange != null ? timeRange.start() : null,
                timeRange != null ? timeRange.end() : null,
                timeZone.value(),
                organizerEmail.value(),
                organizerDisplayName.value(),
                calendarUid,
                calendarSequence,
                List.copyOf(invitees),
                Instant.now()));
    }

    /**
     * Records that one or more invitees were removed from this meeting.
     * Registers {@code MeetingInvitationsDeletedEvent} carrying only the removed invitees.
     *
     * <p>Does nothing when the invitee list is empty. Does not change {@code calendarSequence}.
     *
     * @param invitees list of removed invitee info snapshots
     */
    public void recordInviteesRemoved(List<MeetingInvitationsDeletedEvent.InviteeInfo> invitees) {
        if (invitees.isEmpty()) {
            return;
        }
        registerEvent(new MeetingInvitationsDeletedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                title.value(),
                shortCode.value(),
                timeRange != null ? timeRange.start() : null,
                timeRange != null ? timeRange.end() : null,
                timeZone.value(),
                organizerEmail.value(),
                organizerDisplayName.value(),
                calendarUid,
                calendarSequence,
                List.copyOf(invitees),
                Instant.now()));
    }

    public Result<Void, MeetingError> update(
            AccountId updatedBy,
            MeetingTitle newTitle,
            String newDescription,
            JiraIssueLink newIssueLink,
            MeetingSettings newSettings,
            MeetingTimeZone newTimeZone,
            MeetingTimeRange newTimeRange) {
        if (!hostId.equals(updatedBy)) {
            return Result.failure(
                    new MeetingError.NotAuthorized(updatedBy.value(), hostId.value()));
        }
        if (status == MeetingStatus.COMPLETED || status == MeetingStatus.CANCELED) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.SCHEDULED));
        }

        boolean scheduledFieldsChanged =
                !timeZone.equals(newTimeZone) || !java.util.Objects.equals(timeRange, newTimeRange);
        if (scheduledFieldsChanged && status != MeetingStatus.SCHEDULED) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.SCHEDULED));
        }
        if (timeRange != null && newTimeRange == null) {
            return Result.failure(
                    new MeetingError.InvalidSettings("Scheduled meetings require a time range"));
        }
        if (scheduledFieldsChanged
                && newTimeRange != null
                && newTimeRange.start().isBefore(Instant.now().minus(CLOCK_SKEW_TOLERANCE))) {
            return Result.failure(new MeetingError.StartTimeInPast(newTimeRange.start()));
        }

        MeetingInfoSnapshot oldInfo = infoSnapshot();
        MeetingSettings oldSettings = settings;
        boolean infoChanged = !title.equals(newTitle)
                || !description.equals(newDescription)
                || !issueLink.equals(newIssueLink)
                || scheduledFieldsChanged;
        boolean settingsChanged = !settings.equals(newSettings);

        if (!infoChanged && !settingsChanged) {
            return Result.success();
        }

        title = newTitle;
        description = newDescription;
        issueLink = newIssueLink;
        settings = newSettings;
        timeZone = newTimeZone;
        timeRange = newTimeRange;

        Instant now = Instant.now();
        this.updatedAt = now;
        if (infoChanged) {
            calendarSequence++;
            registerEvent(new MeetingInfoUpdatedEvent(
                    UUID.randomUUID(),
                    tenantId.value(),
                    id.value(),
                    hostId.value(),
                    updatedBy.value(),
                    status,
                    oldInfo,
                    infoSnapshot(),
                    now));
        }
        if (settingsChanged) {
            registerEvent(new MeetingSettingsUpdatedEvent(
                    UUID.randomUUID(),
                    tenantId.value(),
                    id.value(),
                    hostId.value(),
                    updatedBy.value(),
                    status,
                    oldSettings,
                    newSettings,
                    now));
        }
        return Result.success();
    }

    private MeetingInfoSnapshot infoSnapshot() {
        return new MeetingInfoSnapshot(
                title.value(),
                description,
                issueLink,
                timeZone.value(),
                timeRange,
                organizerEmail.value(),
                organizerDisplayName.value(),
                calendarUid,
                calendarSequence);
    }

    /**
     * Transitions SCHEDULED → CANCELED with the given reason.
     * Registers {@code MeetingCanceledEvent}.
     *
     * @param reason why the meeting is being canceled
     */
    public Result<Void, MeetingError> cancel(CancelReason reason) {
        return cancel(
                reason,
                title.value(),
                shortCode.value(),
                timeRange != null ? timeRange.start() : null,
                List.of());
    }

    /**
     * Transitions SCHEDULED → CANCELED and includes notification payload for invitees.
     *
     * @param reason           why the meeting is being canceled
     * @param meetingTitle     snapshot of the meeting title for notification
     * @param meetingShortCode the meeting short code
     * @param startTime        scheduled start time (nullable for instant meetings)
     * @param invitees         invitee snapshots for cancellation notification
     */
    public Result<Void, MeetingError> cancel(
            CancelReason reason,
            @Nullable String meetingTitle,
            String meetingShortCode,
            @Nullable Instant startTime,
            List<MeetingCanceledEvent.InviteeInfo> invitees) {
        if (!status.canTransitionTo(MeetingStatus.CANCELED)) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.CANCELED));
        }
        status = MeetingStatus.CANCELED;
        this.cancelReason = reason;
        Instant now = Instant.now();
        this.updatedAt = now;
        registerEvent(new MeetingCanceledEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                hostId.value(),
                reason.name(),
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

    public TenantId getTenantId() {
        return tenantId;
    }

    public AccountId getHostId() {
        return hostId;
    }

    public ShortCode getShortCode() {
        return shortCode;
    }

    public MeetingTitle getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
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

    public Optional<CancelReason> getCancelReason() {
        return Optional.ofNullable(cancelReason);
    }

    public MeetingSettings getSettings() {
        return settings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Optional<Instant> getDeletedAt() {
        return Optional.ofNullable(deletedAt);
    }

    public Optional<AccountId> getDeletedBy() {
        return Optional.ofNullable(deletedBy);
    }

    public Optional<Instant> getPurgeAfter() {
        return Optional.ofNullable(purgeAfter);
    }

    public JiraIssueLink getIssueLink() {
        return issueLink;
    }

    public MeetingTimeZone getTimeZone() {
        return timeZone;
    }

    public Email getOrganizerEmail() {
        return organizerEmail;
    }

    public InviteeDisplayName getOrganizerDisplayName() {
        return organizerDisplayName;
    }

    public String getCalendarUid() {
        return calendarUid;
    }

    public int getCalendarSequence() {
        return calendarSequence;
    }
}
