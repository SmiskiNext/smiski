package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.RecordingResponseMapper;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.query.GetParticipatedMeetingDetailQuery;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.*;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.StoragePort;
import io.github.smiskinext.meetingmanagement.domain.projection.InviteeSummary;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import io.github.smiskinext.meetingmanagement.domain.projection.RecordingSummary;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.application.response.MeetingDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetParticipatedMeetingDetailUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    ParticipationLogRepository participationLogRepository;

    @Mock
    RecordingRepository recordingRepository;

    @Mock
    MeetingInviteeRepository meetingInviteeRepository;

    @Mock
    StoragePort storagePort;

    @Mock
    ParticipantAvatarResolver avatarResolver;

    private GetParticipatedMeetingDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        var mapper = new RecordingResponseMapper(storagePort);
        useCase = new GetParticipatedMeetingDetailUseCase(
                meetingRepository,
                participationLogRepository,
                recordingRepository,
                meetingInviteeRepository,
                mapper,
                avatarResolver);
    }

    @Test
    void execute_rejectsRequesterOutsideUserScope() {
        UUID userId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        var result = useCase.execute(
                new GetParticipatedMeetingDetailQuery(userId, UUID.randomUUID(), requesterId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.NotOwner(requesterId, userId));
        verifyNoInteractions(
                participationLogRepository,
                meetingRepository,
                recordingRepository,
                meetingInviteeRepository);
    }

    @Test
    void execute_rejectsUserWithoutParticipation() {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(participationLogRepository.existsByMeetingIdAndUserId(meetingId, userId))
                .thenReturn(false);

        var result =
                useCase.execute(new GetParticipatedMeetingDetailQuery(userId, meetingId, userId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.NotParticipant(userId, meetingId));
        verify(participationLogRepository).existsByMeetingIdAndUserId(meetingId, userId);
        verifyNoInteractions(meetingRepository, recordingRepository, meetingInviteeRepository);
    }

    @Test
    void execute_returnsMeetingNotFoundWhenRepositoryEmpty() {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(participationLogRepository.existsByMeetingIdAndUserId(meetingId, userId))
                .thenReturn(true);
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        var result =
                useCase.execute(new GetParticipatedMeetingDetailQuery(userId, meetingId, userId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.MeetingNotFound(meetingId));
    }

    @Test
    void execute_aggregatesMeetingParticipantsRecordingsAndInvitees() {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Meeting meeting = Meeting.reconstitute(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                ShortCode.of("ABC123"),
                MeetingTitle.of("Design Review"),
                "Discuss architecture",
                MeetingTimeRange.of(
                        Instant.parse("2026-04-01T10:00:00Z"),
                        Instant.parse("2026-04-01T11:00:00Z")),
                null,
                MeetingType.SCHEDULED,
                MeetingStatus.ENDED,
                new MeetingSettings(
                        AdmissionPolicy.ALLOW_ALL,
                        true, // allowGuest
                        50, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null), // password
                Instant.parse("2026-04-01T08:00:00Z"));
        when(participationLogRepository.existsByMeetingIdAndUserId(meetingId, userId))
                .thenReturn(true);
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findDistinctParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new ParticipantSummary(
                        1L,
                        meetingId,
                        userId,
                        "Alice",
                        ParticipantRole.PARTICIPANT.name(),
                        Instant.parse("2026-04-01T10:00:00Z"),
                        Instant.parse("2026-04-01T11:00:00Z"))));
        when(recordingRepository.findCompletedSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new RecordingSummary(
                        UUID.randomUUID(),
                        meetingId,
                        "https://example.com/recording.mp4",
                        "meetings/abc/recording.mp4",
                        null,
                        RecordingStatus.COMPLETED,
                        Instant.parse("2026-04-01T10:00:00Z"),
                        Instant.parse("2026-04-01T11:00:00Z"),
                        3600,
                        1024L,
                        Instant.parse("2026-04-01T11:01:00Z"))));
        when(storagePort.generatePresignedUrl(
                        eq("meetings/abc/recording.mp4"), any(Duration.class)))
                .thenReturn("https://presigned.example/recording.mp4");
        when(meetingInviteeRepository.findSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new InviteeSummary(
                        userId,
                        "alice@example.com",
                        "Alice",
                        InviteeStatus
                                .ACCEPTED
                                .name(),
                        Instant.parse("2026-03-31T08:00:00Z"),
                        Instant.parse("2026-03-31T09:00:00Z"))));

        var result =
                useCase.execute(new GetParticipatedMeetingDetailQuery(userId, meetingId, userId));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = ((Result.Success<
                MeetingDetailResponse,
                                MeetingError>)
                        result)
                .value();
        assertThat(response.id()).isEqualTo(meetingId);
        assertThat(response.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.displayName()).isEqualTo("Alice");
            assertThat(participant.role()).isEqualTo(ParticipantRole.PARTICIPANT);
        });
        assertThat(response.recordings()).singleElement().satisfies(recording -> {
            assertThat(recording.status()).isEqualTo(RecordingStatus.COMPLETED);
            assertThat(recording.fileUrl()).isEqualTo("https://presigned.example/recording.mp4");
        });
        assertThat(response.invitees()).singleElement().satisfies(invitee -> {
            assertThat(invitee.email()).isEqualTo("alice@example.com");
            assertThat(invitee.displayName()).isEqualTo("Alice");
        });
    }

    @Test
    void execute_returnsEmptyNestedCollectionsWhenNoAggregatesExist() {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Meeting meeting = Meeting.reconstitute(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                ShortCode.of("XYZ789"),
                null,
                null,
                null,
                null,
                MeetingType.INSTANT,
                MeetingStatus.LIVE,
                new MeetingSettings(
                        AdmissionPolicy.ALLOW_ALL,
                        false, // allowGuest
                        10, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null), // password
                Instant.parse("2026-04-01T08:00:00Z"));
        when(participationLogRepository.existsByMeetingIdAndUserId(meetingId, userId))
                .thenReturn(true);
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findDistinctParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of());
        when(recordingRepository.findCompletedSummariesByMeetingId(meetingId))
                .thenReturn(List.of());
        when(meetingInviteeRepository.findSummariesByMeetingId(meetingId)).thenReturn(List.of());

        var result =
                useCase.execute(new GetParticipatedMeetingDetailQuery(userId, meetingId, userId));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = ((Result.Success<
                MeetingDetailResponse,
                                MeetingError>)
                        result)
                .value();
        assertThat(response.title()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.startTime()).isNull();
        assertThat(response.endTime()).isNull();
        assertThat(response.participants()).isEmpty();
        assertThat(response.recordings()).isEmpty();
        assertThat(response.invitees()).isEmpty();
    }
}
