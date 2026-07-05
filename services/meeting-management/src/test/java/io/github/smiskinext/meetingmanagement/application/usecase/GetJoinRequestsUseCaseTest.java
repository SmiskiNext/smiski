package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.query.GetJoinRequestsQuery;
import io.github.smiskinext.meetingmanagement.application.response.JoinRequestResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.JoinRequestSummary;
import io.github.smiskinext.shared.domain.OffsetPageResponse;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetJoinRequestsUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    JoinRequestRepository joinRequestRepository;

    @Mock
    ParticipantAvatarResolver avatarResolver;

    private GetJoinRequestsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetJoinRequestsUseCase(
                meetingRepository, joinRequestRepository, avatarResolver);
    }

    @Test
    void execute_returnsPagedResponsesForAuthorizedHost() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = mock(Meeting.class);
        when(meeting.getHostId()).thenReturn(UserId.of(hostId));
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(joinRequestRepository.findPendingSummariesByMeetingId(meetingId, 20, 10))
                .thenReturn(OffsetPageResponse.of(
                        java.util.List.of(new JoinRequestSummary(
                                UUID.randomUUID(),
                                meetingId,
                                null,
                                "Guest One",
                                JoinRequestStatus.PENDING,
                                Instant.parse("2026-04-01T10:00:00Z"),
                                Instant.parse("2026-04-01T10:02:00Z"))),
                        10,
                        20,
                        true));

        var result = useCase.execute(new GetJoinRequestsQuery(meetingId, hostId, 10, 20));

        assertThat(result).isInstanceOf(Result.Success.class);
        var page = ((Result.Success<OffsetPageResponse<JoinRequestResponse>, MeetingError>) result)
                .value();
        assertThat(page.offset()).isEqualTo(20);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).singleElement().satisfies(request -> {
            assertThat(request.meetingId()).isEqualTo(meetingId);
            assertThat(request.displayName()).isEqualTo("Guest One");
            assertThat(request.userId()).isNull();
        });
        verify(joinRequestRepository).findPendingSummariesByMeetingId(meetingId, 20, 10);
    }

    @Test
    void execute_returnsFailureWhenRequesterIsNotHost() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Meeting meeting = mock(Meeting.class);
        when(meeting.getHostId()).thenReturn(UserId.of(hostId));
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(new GetJoinRequestsQuery(meetingId, requesterId, 10, 0));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
    }

    @Test
    void execute_returnsMeetingNotFoundWhenMeetingDoesNotExist() {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        var result = useCase.execute(new GetJoinRequestsQuery(meetingId, requesterId, 10, 0));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
    }

    @Test
    void execute_normalizesPageSizeAndOffsetBoundaries() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = mock(Meeting.class);
        when(meeting.getHostId()).thenReturn(UserId.of(hostId));
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(joinRequestRepository.findPendingSummariesByMeetingId(meetingId, 0, 100))
                .thenReturn(OffsetPageResponse.empty(100, 0));

        var result = useCase.execute(new GetJoinRequestsQuery(meetingId, hostId, 101, -5));

        assertThat(result).isInstanceOf(Result.Success.class);
        var page = ((Result.Success<OffsetPageResponse<JoinRequestResponse>, MeetingError>) result)
                .value();
        assertThat(page.pageSize()).isEqualTo(100);
        assertThat(page.offset()).isEqualTo(0);
        verify(joinRequestRepository).findPendingSummariesByMeetingId(meetingId, 0, 100);
    }

    @Test
    void execute_returnsEmptyPageWhenNoJoinRequestsExist() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = mock(Meeting.class);
        when(meeting.getHostId()).thenReturn(UserId.of(hostId));
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(joinRequestRepository.findPendingSummariesByMeetingId(meetingId, 0, 10))
                .thenReturn(OffsetPageResponse.empty(10, 0));

        var result = useCase.execute(new GetJoinRequestsQuery(meetingId, hostId, 10, 0));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<OffsetPageResponse<JoinRequestResponse>, MeetingError>) result)
                        .value()
                        .items())
                .isEmpty();
    }
}
