package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.query.GetMeetingQuery;
import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.github.smiskinext.meet.application.service.GetMeetingApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeZone;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meet.domain.projection.InviteeSummary;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetMeetingApplicationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String ACCOUNT = "member-1";

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository meetingInviteeRepository;
    private ParticipationLogRepository participationLogRepository;
    private GetMeetingApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        meetingInviteeRepository = mock(MeetingInviteeRepository.class);
        participationLogRepository = mock(ParticipationLogRepository.class);
        service = new GetMeetingApplicationService(
                meetingRepository, meetingInviteeRepository, participationLogRepository);
    }

    @Test
    void existingMeetingReturnsMeetingWithInviteesAndParticipants() {
        Meeting meeting = instantMeeting();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        Instant invitedAt = Instant.parse("2025-01-15T10:35:00Z");
        Instant joinedAt = Instant.parse("2025-01-15T11:00:00Z");
        when(meetingInviteeRepository.findSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new InviteeSummary(
                        "member-1", "alice@example.com", "Alice", "ACCEPTED", invitedAt, null)));
        when(participationLogRepository.findDistinctParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new ParticipantSummary(
                        UUID.randomUUID(),
                        meetingId,
                        "member-1",
                        "Alice",
                        "PARTICIPANT",
                        joinedAt,
                        null)));

        Result<GetMeetingResult, MeetingError> result =
                service.execute(new GetMeetingQuery(meetingId, TENANT, ACCOUNT));

        assertThat(result.isSuccess()).isTrue();
        GetMeetingResult value = ((Result.Success<GetMeetingResult, MeetingError>) result).value();
        assertThat(value.meeting().id()).isEqualTo(meetingId);
        assertThat(value.meeting().hostId()).isEqualTo("host-1");
        assertThat(value.invitees()).singleElement().satisfies(invitee -> {
            assertThat(invitee.accountId()).isEqualTo("member-1");
            assertThat(invitee.status()).isEqualTo("ACCEPTED");
        });
        assertThat(value.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.accountId()).isEqualTo("member-1");
            assertThat(participant.joinedAt()).isEqualTo(joinedAt);
            assertThat(participant.leftAt()).isNull();
        });
    }

    @Test
    void absentMeetingYieldsMeetingNotFound() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        Result<GetMeetingResult, MeetingError> result =
                service.execute(new GetMeetingQuery(meetingId, TENANT, ACCOUNT));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<GetMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verify(meetingInviteeRepository, never()).findSummariesByMeetingId(any());
        verify(participationLogRepository, never())
                .findDistinctParticipantSummariesByMeetingId(any());
    }

    @Test
    void softDeletedMeetingYieldsMeetingNotFound() {
        Meeting meeting = instantMeeting();
        UUID meetingId = meeting.getId().value();
        meeting.delete(AccountId.of("host-1"));
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        Result<GetMeetingResult, MeetingError> result =
                service.execute(new GetMeetingQuery(meetingId, TENANT, ACCOUNT));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<GetMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verify(meetingInviteeRepository, never()).findSummariesByMeetingId(any());
        verify(participationLogRepository, never())
                .findDistinctParticipantSummariesByMeetingId(any());
    }

    @Test
    void emptyInviteeAndParticipantListsStillSucceed() {
        Meeting meeting = instantMeeting();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findSummariesByMeetingId(meetingId)).thenReturn(List.of());
        when(participationLogRepository.findDistinctParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of());

        Result<GetMeetingResult, MeetingError> result =
                service.execute(new GetMeetingQuery(meetingId, TENANT, ACCOUNT));

        assertThat(result.isSuccess()).isTrue();
        GetMeetingResult value = ((Result.Success<GetMeetingResult, MeetingError>) result).value();
        assertThat(value.invitees()).isEmpty();
        assertThat(value.participants()).isEmpty();
    }

    private static Meeting instantMeeting() {
        return Meeting.instant(
                TenantId.of(TENANT),
                AccountId.of("host-1"),
                MeetingTitle.of("Sprint planning"),
                "Plan the next sprint",
                JiraIssueLink.of("10001", "PROJ-1", "PROJ"),
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, true, true, true, true),
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                ShortCode.of("abc-defg-hij"));
    }
}
