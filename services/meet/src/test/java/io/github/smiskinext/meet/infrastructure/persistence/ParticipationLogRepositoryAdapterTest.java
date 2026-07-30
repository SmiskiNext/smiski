package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.domain.port.ScreenShareStateRepository;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParticipationLogRepositoryAdapterTest {

    private ParticipationLogJpaRepository jpaRepository;
    private ScreenShareStateRepository screenShareStateRepository;
    private ParticipationLogRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(ParticipationLogJpaRepository.class);
        screenShareStateRepository = mock(ScreenShareStateRepository.class);
        adapter = new ParticipationLogRepositoryAdapter(jpaRepository, screenShareStateRepository);
    }

    @Test
    void collapsesMultipleSessionsPerAccountIntoDistinctParticipants() {
        UUID meetingId = UUID.randomUUID();
        Instant firstJoin = Instant.parse("2025-01-15T10:00:00Z");
        Instant firstLeave = Instant.parse("2025-01-15T10:30:00Z");
        Instant secondJoin = Instant.parse("2025-01-15T11:00:00Z");
        UUID recentSessionId = UUID.randomUUID();
        when(jpaRepository.findParticipantProjectionsByMeetingId(meetingId))
                .thenReturn(List.of(
                        new ParticipantSummary(
                                recentSessionId,
                                meetingId,
                                "member-1",
                                "PARTICIPANT",
                                secondJoin,
                                null,
                                false),
                        new ParticipantSummary(
                                UUID.randomUUID(),
                                meetingId,
                                "member-1",
                                "PARTICIPANT",
                                firstJoin,
                                firstLeave,
                                false)));
        when(screenShareStateRepository.findSharingAccountIds(meetingId))
                .thenReturn(Set.of("member-1"));

        List<ParticipantSummary> participants =
                adapter.findDistinctParticipantSummariesByMeetingId(meetingId);

        assertThat(participants).singleElement().satisfies(participant -> {
            assertThat(participant.id()).isEqualTo(recentSessionId);
            assertThat(participant.accountId()).isEqualTo("member-1");
            assertThat(participant.joinedAt()).isEqualTo(firstJoin);
            assertThat(participant.leftAt()).isNull();
            assertThat(participant.screenSharing()).isTrue();
        });
    }

    @Test
    void reportsLatestLeftAtWhenAllSessionsClosed() {
        UUID meetingId = UUID.randomUUID();
        Instant firstJoin = Instant.parse("2025-01-15T10:00:00Z");
        Instant firstLeave = Instant.parse("2025-01-15T10:30:00Z");
        Instant secondJoin = Instant.parse("2025-01-15T11:00:00Z");
        Instant secondLeave = Instant.parse("2025-01-15T11:45:00Z");
        when(jpaRepository.findParticipantProjectionsByMeetingId(meetingId))
                .thenReturn(List.of(
                        new ParticipantSummary(
                                UUID.randomUUID(),
                                meetingId,
                                "member-1",
                                "PARTICIPANT",
                                secondJoin,
                                secondLeave,
                                false),
                        new ParticipantSummary(
                                UUID.randomUUID(),
                                meetingId,
                                "member-1",
                                "PARTICIPANT",
                                firstJoin,
                                firstLeave,
                                false)));
        when(screenShareStateRepository.findSharingAccountIds(meetingId)).thenReturn(Set.of());

        List<ParticipantSummary> participants =
                adapter.findDistinctParticipantSummariesByMeetingId(meetingId);

        assertThat(participants).singleElement().satisfies(participant -> {
            assertThat(participant.joinedAt()).isEqualTo(firstJoin);
            assertThat(participant.leftAt()).isEqualTo(secondLeave);
            assertThat(participant.screenSharing()).isFalse();
        });
    }

    @Test
    void preservesFirstSeenAccountOrder() {
        UUID meetingId = UUID.randomUUID();
        Instant joinedAt = Instant.parse("2025-01-15T10:00:00Z");
        when(jpaRepository.findParticipantProjectionsByMeetingId(meetingId))
                .thenReturn(List.of(
                        new ParticipantSummary(
                                UUID.randomUUID(),
                                meetingId,
                                "member-2",
                                "PARTICIPANT",
                                joinedAt,
                                null,
                                false),
                        new ParticipantSummary(
                                UUID.randomUUID(),
                                meetingId,
                                "member-1",
                                "PARTICIPANT",
                                joinedAt,
                                null,
                                false)));
        when(screenShareStateRepository.findSharingAccountIds(meetingId)).thenReturn(Set.of());

        List<ParticipantSummary> participants =
                adapter.findDistinctParticipantSummariesByMeetingId(meetingId);

        assertThat(participants)
                .extracting(ParticipantSummary::accountId)
                .containsExactly("member-2", "member-1");
    }
}
