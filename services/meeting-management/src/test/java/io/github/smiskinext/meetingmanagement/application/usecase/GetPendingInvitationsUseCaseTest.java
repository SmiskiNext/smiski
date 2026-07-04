package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.query.GetPendingInvitationsQuery;
import io.github.smiskinext.meetingmanagement.application.response.PendingInvitationResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviterId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPendingInvitationsUseCaseTest {

    @Mock
    MeetingInviteeRepository meetingInviteeRepository;

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    UserGrpcServicePort userGrpcServicePort;

    GetPendingInvitationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPendingInvitationsUseCase(
                meetingInviteeRepository, meetingRepository, userGrpcServicePort);
    }

    @Test
    void execute_returnsPendingInvitationsWithMeetingMetadata() {
        UUID hostId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Meeting meeting = meeting(hostId);
        MeetingInvitee invitee = invitee(meeting, hostId, userId);
        when(meetingInviteeRepository.findPendingByUserId(userId)).thenReturn(List.of(invitee));
        when(userGrpcServicePort.batchGetUsersByIds(List.of(hostId)))
                .thenReturn(Map.of(
                        hostId,
                        new UserGrpcServicePort.ResolvedUser(
                                hostId, "host@example.com", "Host User", null, null, "EMAIL")));
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));

        Result<List<PendingInvitationResponse>, MeetingError> result =
                useCase.execute(new GetPendingInvitationsQuery(userId, userId));

        assertThat(result).isInstanceOf(Result.Success.class);
        List<PendingInvitationResponse> invitations =
                ((Result.Success<List<PendingInvitationResponse>, MeetingError>) result).value();
        assertThat(invitations).hasSize(1);
        PendingInvitationResponse response = invitations.getFirst();
        assertThat(response.inviteeId()).isEqualTo(invitee.getId().value());
        assertThat(response.meetingId()).isEqualTo(meeting.getId().value());
        assertThat(response.meetingTitle()).isEqualTo("Planning");
        assertThat(response.meetingShortCode()).isEqualTo("ABC123");
        assertThat(response.startTime()).isEqualTo(Instant.parse("2026-05-01T10:00:00Z"));
        assertThat(response.hostDisplayName()).isEqualTo("Host User");
        assertThat(response.invitedAt()).isEqualTo(invitee.getInvitedAt());
    }

    @Test
    void execute_returnsEmptyListWhenNoPendingInvitations() {
        UUID userId = UUID.randomUUID();
        when(meetingInviteeRepository.findPendingByUserId(userId)).thenReturn(List.of());

        Result<List<PendingInvitationResponse>, MeetingError> result =
                useCase.execute(new GetPendingInvitationsQuery(userId, userId));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<List<PendingInvitationResponse>, MeetingError>) result).value())
                .isEmpty();
        verifyNoInteractions(meetingRepository, userGrpcServicePort);
    }

    @Test
    void execute_returnsNotOwnerForOwnershipMismatch() {
        UUID userId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Result<List<PendingInvitationResponse>, MeetingError> result =
                useCase.execute(new GetPendingInvitationsQuery(userId, requesterId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<List<PendingInvitationResponse>, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotOwner.class);
        verifyNoInteractions(meetingInviteeRepository, meetingRepository, userGrpcServicePort);
    }

    private Meeting meeting(UUID hostId) {
        return Meeting.schedule(
                UserId.of(hostId),
                MeetingTitle.of("Planning"),
                null,
                MeetingTimeRange.of(
                        Instant.parse("2026-05-01T10:00:00Z"),
                        Instant.parse("2026-05-01T11:00:00Z")),
                MeetingSettings.defaults(),
                ShortCode.of("ABC123"));
    }

    private MeetingInvitee invitee(Meeting meeting, UUID hostId, UUID userId) {
        return MeetingInvitee.create(
                MeetingId.of(meeting.getId().value()),
                InviterId.of(hostId),
                UserId.of(userId),
                Email.of("alice@example.com"),
                null);
    }
}
