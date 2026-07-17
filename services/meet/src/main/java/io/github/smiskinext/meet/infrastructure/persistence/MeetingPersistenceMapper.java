package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.CancelReason;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Maps between {@link MeetingJpaEntity} and the {@link Meeting} domain aggregate.
 */
final class MeetingPersistenceMapper {

    private MeetingPersistenceMapper() {}

    static MeetingJpaEntity toEntity(Meeting meeting) {
        return new MeetingJpaEntity(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getTitle().value(),
                meeting.getDescription(),
                meeting.getIssueLink().issueId(),
                meeting.getIssueLink().issueKey(),
                meeting.getIssueLink().projectKey(),
                meeting.getTimeRange().map(MeetingTimeRange::start).orElse(null),
                meeting.getEndTime().orElse(null),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getCancelReason().map(CancelReason::name).orElse(null),
                meeting.getSettings(),
                meeting.getDeletedAt().orElse(null),
                meeting.getDeletedBy().map(AccountId::value).orElse(null),
                meeting.getPurgeAfter().orElse(null),
                meeting.getCreatedAt());
    }

    static Meeting toDomain(MeetingJpaEntity entity) {
        JiraIssueLink issueLink =
                JiraIssueLink.of(entity.getIssueId(), entity.getIssueKey(), entity.getProjectKey());

        @Nullable MeetingTimeRange timeRange = null;
        @Nullable Instant startTime = entity.getStartTime();
        @Nullable Instant endTime = entity.getEndTime();
        if (startTime != null && endTime != null && startTime.isBefore(endTime)) {
            timeRange = new MeetingTimeRange(startTime, endTime);
        }

        return Meeting.reconstitute(
                TenantId.of(entity.getTenantId()),
                MeetingId.of(entity.getId()),
                AccountId.of(entity.getHostId()),
                ShortCode.of(entity.getShortCode()),
                MeetingTitle.of(entity.getTitle()),
                entity.getDescription(),
                issueLink,
                timeRange,
                endTime,
                MeetingType.valueOf(entity.getType()),
                MeetingStatus.valueOf(entity.getStatus()),
                entity.getSettings(),
                entity.getCreatedAt(),
                entity.getCancelReason() != null
                        ? CancelReason.valueOf(entity.getCancelReason())
                        : null,
                entity.getDeletedAt(),
                entity.getDeletedBy() != null ? AccountId.of(entity.getDeletedBy()) : null,
                entity.getPurgeAfter());
    }
}
