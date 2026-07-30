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
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meet.domain.projection.InviteeSummary;
import io.github.smiskinext.meet.domain.projection.MeetingDetail;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;
import io.github.smiskinext.shared.domain.Result;
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
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findDetailById(meetingId))
                .thenReturn(Optional.of(meetingDetail(meetingId)));
        Instant invitedAt = Instant.parse("2025-01-15T10:35:00Z");
        Instant joinedAt = Instant.parse("2025-01-15T11:00:00Z");
        UUID inviteeId = UUID.randomUUID();
        when(meetingInviteeRepository.findSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new InviteeSummary(
                        inviteeId,
                        "member-1",
                        "alice@example.com",
                        "Alice",
                        "ACCEPTED",
                        invitedAt,
                        null)));
        when(participationLogRepository.findDistinctParticipantSummariesByMeetingId(meetingId))
                .thenReturn(List.of(new ParticipantSummary(
                        UUID.randomUUID(),
                        meetingId,
                        "member-1",
                        "PARTICIPANT",
                        joinedAt,
                        null,
                        false)));

        Result<GetMeetingResult, MeetingError> result =
                service.execute(new GetMeetingQuery(meetingId, TENANT, ACCOUNT));

        assertThat(result.isSuccess()).isTrue();
        GetMeetingResult value = ((Result.Success<GetMeetingResult, MeetingError>) result).value();
        assertThat(value.meeting().id()).isEqualTo(meetingId);
        assertThat(value.meeting().hostId()).isEqualTo("host-1");
        assertThat(value.invitees()).singleElement().satisfies(invitee -> {
            assertThat(invitee.id()).isEqualTo(inviteeId);
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
        when(meetingRepository.findDetailById(meetingId)).thenReturn(Optional.empty());

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
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findDetailById(meetingId)).thenReturn(Optional.empty());

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
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findDetailById(meetingId))
                .thenReturn(Optional.of(meetingDetail(meetingId)));
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

    private static MeetingDetail meetingDetail(UUID meetingId) {
        return new MeetingDetail(
                meetingId,
                "host-1",
                "abc-defg-hij",
                MeetingType.INSTANT,
                MeetingStatus.RUNNING,
                "Sprint planning",
                "Plan the next sprint",
                "10001",
                "PROJ-1",
                "PROJ",
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, true, true, true, true),
                null,
                null,
                "UTC",
                "host@example.com",
                "Host User",
                "calendar-uid",
                0,
                Instant.parse("2025-01-15T10:30:00Z"));
    }
}
