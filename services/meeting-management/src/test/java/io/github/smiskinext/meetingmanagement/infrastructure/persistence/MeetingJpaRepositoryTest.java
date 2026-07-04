package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MeetingJpaRepositoryTest {

    @Autowired
    MeetingJpaRepository repository;

    @Autowired
    ParticipationLogJpaRepository participationLogRepository;

    @Test
    void findParticipatedMeetingsKeyset_ordersByLastJoinedAtDescAndSupportsCursor() {
        UUID userId = UUID.randomUUID();
        UUID firstMeetingId = UUID.randomUUID();
        UUID secondMeetingId = UUID.randomUUID();
        UUID thirdMeetingId = UUID.randomUUID();
        saveMeeting(firstMeetingId, "LIVE", Instant.parse("2026-04-01T08:00:00Z"));
        saveMeeting(secondMeetingId, "ENDED", Instant.parse("2026-04-01T08:10:00Z"));
        saveMeeting(thirdMeetingId, "CANCELLED", Instant.parse("2026-04-01T08:20:00Z"));

        saveParticipation(firstMeetingId, userId, Instant.parse("2026-04-01T10:00:00Z"));
        saveParticipation(firstMeetingId, userId, Instant.parse("2026-04-01T11:00:00Z"));
        saveParticipation(secondMeetingId, userId, Instant.parse("2026-04-01T12:00:00Z"));
        saveParticipation(thirdMeetingId, userId, Instant.parse("2026-04-01T09:00:00Z"));

        List<ParticipatedMeetingRow> page1 =
                repository.findParticipatedMeetingsKeyset(userId.toString(), null, null, 2);

        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).getId()).isEqualTo(secondMeetingId);
        assertThat(page1.get(0).getLastJoinedAt()).isEqualTo(Instant.parse("2026-04-01T12:00:00Z"));
        assertThat(page1.get(1).getId()).isEqualTo(firstMeetingId);
        assertThat(page1.get(1).getLastJoinedAt()).isEqualTo(Instant.parse("2026-04-01T11:00:00Z"));

        List<ParticipatedMeetingRow> page2 = repository.findParticipatedMeetingsKeyset(
                userId.toString(),
                page1.get(1).getLastJoinedAt(),
                page1.get(1).getId().toString(),
                2);

        assertThat(page2).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(thirdMeetingId);
            assertThat(row.getStatus()).isEqualTo("CANCELLED");
        });
    }

    @Test
    void findParticipatedMeetingsKeysetByStatuses_filtersByMeetingStatus() {
        UUID userId = UUID.randomUUID();
        UUID endedMeetingId = UUID.randomUUID();
        UUID liveMeetingId = UUID.randomUUID();
        saveMeeting(endedMeetingId, "ENDED", Instant.parse("2026-04-01T08:00:00Z"));
        saveMeeting(liveMeetingId, "LIVE", Instant.parse("2026-04-01T08:10:00Z"));
        saveParticipation(endedMeetingId, userId, Instant.parse("2026-04-01T10:00:00Z"));
        saveParticipation(liveMeetingId, userId, Instant.parse("2026-04-01T11:00:00Z"));

        var results = repository.findParticipatedMeetingsKeysetByStatuses(
                userId.toString(), List.of("ENDED"), null, null, 10);

        assertThat(results).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(endedMeetingId);
            assertThat(row.getStatus()).isEqualTo("ENDED");
        });
    }

    private void saveMeeting(UUID meetingId, String status, Instant createdAt) {
        repository.save(new MeetingJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "SC-" + meetingId.toString().substring(0, 8),
                "Test meeting",
                null,
                Instant.parse("2026-04-01T09:00:00Z"),
                Instant.parse("2026-04-01T10:00:00Z"),
                "SCHEDULED",
                status,
                new MeetingSettingsJson("ALLOW_ALL", true, 100, true, true, true, true, null),
                createdAt));
    }

    private void saveParticipation(UUID meetingId, UUID userId, Instant joinedAt) {
        participationLogRepository.save(new ParticipationLogJpaEntity(
                meetingId,
                userId,
                "User",
                ParticipantRole.PARTICIPANT
                        .name(),
                "identity-" + joinedAt.toEpochMilli() + meetingId,
                null,
                joinedAt,
                null));
    }
}
