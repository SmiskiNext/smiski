package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.query.GetParticipantsQuery;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.application.response.ParticipantListItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetParticipantsUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    ParticipationLogRepository participationLogRepository;

    @Mock
    ParticipantAvatarResolver avatarResolver;

    private GetParticipantsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetParticipantsUseCase(
                meetingRepository, participationLogRepository, avatarResolver);
    }

    private Optional<Meeting> existingMeeting() {
        return Optional.of(mock(Meeting.class));
    }

    @Test
    void execute_meetingWithParticipants_returnsMappedList() {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant joinedAt = Instant.parse("2026-04-01T10:15:30Z");
        Instant leftAt = Instant.parse("2026-04-01T10:45:30Z");
        when(meetingRepository.findById(meetingId)).thenReturn(existingMeeting());
        when(participationLogRepository.findParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new ParticipantSummary(
                        11L,
                        meetingId,
                        userId,
                        "Alice",
                        ParticipantRole.PARTICIPANT.name(),
                        joinedAt,
                        leftAt)));

        var result = useCase.execute(new GetParticipantsQuery(meetingId));

        assertThat(result).isInstanceOf(Result.Success.class);
        var participant = ((Result.Success<
                                List<
                                        ParticipantListItemResponse>,
                                MeetingError>)
                        result)
                .value()
                .getFirst();
        assertThat(participant.id()).isEqualTo(11L);
        assertThat(participant.meetingId()).isEqualTo(meetingId);
        assertThat(participant.userId()).isEqualTo(userId);
        assertThat(participant.displayName()).isEqualTo("Alice");
        assertThat(participant.role()).isEqualTo(ParticipantRole.PARTICIPANT);
        assertThat(participant.joinedAt()).isEqualTo(joinedAt);
        assertThat(participant.leftAt()).isEqualTo(leftAt);
    }

    @Test
    void execute_emptyMeeting_returnsEmptyList() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(existingMeeting());
        when(participationLogRepository.findParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of());

        var result = useCase.execute(new GetParticipantsQuery(meetingId));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<
                                        List<
                                                ParticipantListItemResponse>,
                                        MeetingError>)
                                result)
                        .value())
                .isEmpty();
    }

    @Test
    void execute_guestParticipant_preservesNullableFields() {
        UUID meetingId = UUID.randomUUID();
        Instant joinedAt = Instant.parse("2026-04-01T11:00:00Z");
        when(meetingRepository.findById(meetingId)).thenReturn(existingMeeting());
        when(participationLogRepository.findParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new ParticipantSummary(
                        12L,
                        meetingId,
                        null,
                        "Guest User",
                        ParticipantRole.GUEST.name(),
                        joinedAt,
                        null)));

        var result = useCase.execute(new GetParticipantsQuery(meetingId));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<
                                        List<
                                                ParticipantListItemResponse>,
                                        MeetingError>)
                                result)
                        .value())
                .singleElement()
                .satisfies(participant -> {
                    assertThat(participant.userId()).isNull();
                    assertThat(participant.role()).isEqualTo(ParticipantRole.GUEST);
                    assertThat(participant.leftAt()).isNull();
                });
    }

    @Test
    void execute_hostParticipant_mapsRoleCorrectly() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(existingMeeting());
        when(participationLogRepository.findParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new ParticipantSummary(
                        20L,
                        meetingId,
                        UUID.randomUUID(),
                        "Meeting Host",
                        ParticipantRole.HOST.name(),
                        Instant.parse("2026-04-01T09:00:00Z"),
                        null)));

        var result = useCase.execute(new GetParticipantsQuery(meetingId));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<
                                        List<
                                                ParticipantListItemResponse>,
                                        MeetingError>)
                                result)
                        .value())
                .singleElement()
                .satisfies(participant -> {
                    assertThat(participant.role()).isEqualTo(ParticipantRole.HOST);
                    assertThat(participant.displayName()).isEqualTo("Meeting Host");
                });
    }

    @Test
    void execute_multipleParticipants_returnsAllInOrder() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(existingMeeting());
        when(participationLogRepository.findParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of(
                        new ParticipantSummary(
                                1L,
                                meetingId,
                                UUID.randomUUID(),
                                "Host",
                                ParticipantRole.HOST.name(),
                                Instant.parse("2026-04-01T10:10:00Z"),
                                null),
                        new ParticipantSummary(
                                2L,
                                meetingId,
                                UUID.randomUUID(),
                                "Participant",
                                ParticipantRole.PARTICIPANT.name(),
                                Instant.parse("2026-04-01T10:05:00Z"),
                                Instant.parse("2026-04-01T10:30:00Z")),
                        new ParticipantSummary(
                                3L,
                                meetingId,
                                null,
                                "Guest",
                                ParticipantRole.GUEST.name(),
                                Instant.parse("2026-04-01T10:00:00Z"),
                                null)));

        var result = useCase.execute(new GetParticipantsQuery(meetingId));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<
                                        List<
                                                ParticipantListItemResponse>,
                                        MeetingError>)
                                result)
                        .value())
                .hasSize(3)
                .extracting(
                        ParticipantListItemResponse::displayName,
                        ParticipantListItemResponse::role)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Host", ParticipantRole.HOST),
                        org.assertj.core.groups.Tuple.tuple(
                                "Participant", ParticipantRole.PARTICIPANT),
                        org.assertj.core.groups.Tuple.tuple("Guest", ParticipantRole.GUEST));
    }

    @Test
    void execute_meetingNotFound_returnsFailure() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        var result = useCase.execute(new GetParticipantsQuery(meetingId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
    }
}
