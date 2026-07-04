package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
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
class ParticipationLogJpaRepositoryTest {

    @Autowired
    MeetingJpaRepository meetingRepository;

    @Autowired
    ParticipationLogJpaRepository repository;

    private MeetingJpaEntity saveMeeting(UUID meetingId) {
        return meetingRepository.save(new MeetingJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "SC-" + meetingId.toString().substring(0, 8),
                "Test meeting",
                null,
                Instant.parse("2026-04-01T09:00:00Z"),
                Instant.parse("2026-04-01T10:00:00Z"),
                "SCHEDULED",
                "LIVE",
                new MeetingSettingsJson("ALLOW_ALL", true, 100, true, true, true, true, null),
                Instant.parse("2026-04-01T08:00:00Z")));
    }

    @Test
    void findParticipantSummariesByMeetingId_returnsNewestFirst() {
        UUID meetingId = UUID.randomUUID();
        UUID otherMeetingId = UUID.randomUUID();
        saveMeeting(meetingId);
        saveMeeting(otherMeetingId);
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "Earlier User",
                ParticipantRole.PARTICIPANT
                        .name(),
                "identity-1",
                null,
                Instant.parse("2026-04-01T10:00:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "Latest User",
                ParticipantRole.HOST.name(),
                "identity-2",
                null,
                Instant.parse("2026-04-01T11:00:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                otherMeetingId,
                UUID.randomUUID(),
                "Other Meeting",
                ParticipantRole.PARTICIPANT
                        .name(),
                "identity-3",
                null,
                Instant.parse("2026-04-01T12:00:00Z"),
                null));

        var results = repository.findParticipantSummariesByMeetingId(meetingId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).displayName()).isEqualTo("Latest User");
        assertThat(results.get(0).role()).isEqualTo("HOST");
        assertThat(results.get(1).displayName()).isEqualTo("Earlier User");
    }

    @Test
    void findParticipantSummariesByMeetingId_preservesNullableFields() {
        UUID meetingId = UUID.randomUUID();
        saveMeeting(meetingId);
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                null,
                "Guest User",
                ParticipantRole.GUEST.name(),
                "identity-guest",
                null,
                Instant.parse("2026-04-01T10:00:00Z"),
                null));

        var results = repository.findParticipantSummariesByMeetingId(meetingId);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.userId()).isNull();
            assertThat(result.leftAt()).isNull();
            assertThat(result.role()).isEqualTo("GUEST");
        });
    }

    @Test
    void findParticipantSummariesByMeetingId_sameJoinedAt_ordersByIdDescending() {
        UUID meetingId = UUID.randomUUID();
        saveMeeting(meetingId);
        Instant joinedAt = Instant.parse("2026-04-01T10:00:00Z");

        var first = repository.save(new ParticipationLogJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "User A",
                ParticipantRole.PARTICIPANT
                        .name(),
                "identity-a",
                null,
                joinedAt,
                null));
        var second = repository.save(new ParticipationLogJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "User B",
                ParticipantRole.PARTICIPANT
                        .name(),
                "identity-b",
                null,
                joinedAt,
                null));

        var results = repository.findParticipantSummariesByMeetingId(meetingId);

        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(second.getId());
        assertThat(results.get(1).id()).isEqualTo(first.getId());
    }

    @Test
    void findParticipantSummariesByMeetingId_emptyMeeting_returnsEmptyList() {
        var results = repository.findParticipantSummariesByMeetingId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    @Test
    void findParticipantSummariesByMeetingId_includesParticipantsWhoLeft() {
        UUID meetingId = UUID.randomUUID();
        saveMeeting(meetingId);
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "Still Here",
                ParticipantRole.PARTICIPANT
                        .name(),
                "identity-still",
                null,
                Instant.parse("2026-04-01T10:00:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "Already Left",
                ParticipantRole.HOST.name(),
                "identity-left",
                null,
                Instant.parse("2026-04-01T09:00:00Z"),
                Instant.parse("2026-04-01T10:30:00Z")));

        var results = repository.findParticipantSummariesByMeetingId(meetingId);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(
                        ParticipantSummary::displayName)
                .containsExactly("Still Here", "Already Left");
    }

    @Test
    void findActiveByMeetingIdAndUserId_excludesGuestsAndLeftSessions() {
        UUID meetingId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        saveMeeting(meetingId);
        UUID otherUserId = UUID.randomUUID();

        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                targetUserId,
                "Target User",
                ParticipantRole.PARTICIPANT
                        .name(),
                "target:device-1",
                null,
                Instant.parse("2026-04-01T10:00:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                targetUserId,
                "Target User",
                ParticipantRole.PARTICIPANT
                        .name(),
                "target:device-2",
                null,
                Instant.parse("2026-04-01T10:05:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                null,
                "Target User",
                ParticipantRole.GUEST.name(),
                "guest:device-xyz",
                null,
                Instant.parse("2026-04-01T10:10:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                targetUserId,
                "Target User",
                ParticipantRole.PARTICIPANT
                        .name(),
                "target:device-left",
                null,
                Instant.parse("2026-04-01T09:00:00Z"),
                Instant.parse("2026-04-01T09:30:00Z")));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                otherUserId,
                "Other User",
                ParticipantRole.PARTICIPANT
                        .name(),
                "other:device-1",
                null,
                Instant.parse("2026-04-01T10:15:00Z"),
                null));

        var results = repository.findActiveByMeetingIdAndUserId(meetingId, targetUserId);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting("livekitIdentity")
                .containsExactlyInAnyOrder("target:device-1", "target:device-2");
    }

    @Test
    void findActiveByMeetingIdAndDisplayName_returnsOnlyGuestsWithMatchingName() {
        UUID meetingId = UUID.randomUUID();
        String guestName = "Wandering Guest";
        saveMeeting(meetingId);
        UUID registeredUserId = UUID.randomUUID();

        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                null,
                guestName,
                ParticipantRole.GUEST.name(),
                "guest:device-A",
                null,
                Instant.parse("2026-04-01T10:00:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                null,
                guestName,
                ParticipantRole.GUEST.name(),
                "guest:device-B",
                null,
                Instant.parse("2026-04-01T10:05:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                registeredUserId,
                guestName,
                ParticipantRole.PARTICIPANT
                        .name(),
                "registered:same-name",
                null,
                Instant.parse("2026-04-01T10:10:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                null,
                "Different Guest",
                ParticipantRole.GUEST.name(),
                "guest:other",
                null,
                Instant.parse("2026-04-01T10:15:00Z"),
                null));
        repository.save(new ParticipationLogJpaEntity(
                meetingId,
                null,
                guestName,
                ParticipantRole.GUEST.name(),
                "guest:device-left",
                null,
                Instant.parse("2026-04-01T09:00:00Z"),
                Instant.parse("2026-04-01T09:30:00Z")));

        var results = repository.findActiveByMeetingIdAndDisplayName(meetingId, guestName);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting("livekitIdentity")
                .containsExactlyInAnyOrder("guest:device-A", "guest:device-B");
    }
}
