package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.command.DeclineMeetingInviteeCommand;
import io.github.smiskinext.meet.application.result.DeclineMeetingInviteeResult;
import io.github.smiskinext.meet.application.service.DeclineMeetingInviteeApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingContext;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeZone;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeclineMeetingInviteeApplicationServiceTest {

    private static final String TENANT = "tenant";
    private static final String OWNER = "owner";

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository inviteeRepository;
    private EventPublisher eventPublisher;
    private DeclineMeetingInviteeApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        inviteeRepository = mock(MeetingInviteeRepository.class);
        eventPublisher = mock(EventPublisher.class);
        when(inviteeRepository.save(any(MeetingInvitee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new DeclineMeetingInviteeApplicationService(
                meetingRepository, inviteeRepository, eventPublisher);
    }

    @Test
    void ownerDeclinesInvitationReturnsSnapshotAndPublishesEvent() {
        Meeting meeting = scheduledMeeting();
        MeetingInvitee invitee = invitee(meeting, OWNER);
        stub(meeting, invitee);

        Result<DeclineMeetingInviteeResult, MeetingError> result =
                service.execute(command(meeting, invitee, OWNER));

        assertThat(result.isSuccess()).isTrue();
        assertThat(success(result).status()).isEqualTo("DECLINED");
        verify(eventPublisher).publishEventsOf(invitee);
    }

    @Test
    void nonOwnerIsRejectedWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        MeetingInvitee invitee = invitee(meeting, OWNER);
        stub(meeting, invitee);

        Result<DeclineMeetingInviteeResult, MeetingError> result =
                service.execute(command(meeting, invitee, "intruder"));

        assertThat(failure(result)).isInstanceOf(MeetingError.NotAuthorized.class);
        verify(inviteeRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void alreadyDeclinedInvitationIsRejectedAsInvalidTransition() {
        Meeting meeting = scheduledMeeting();
        MeetingInvitee invitee = invitee(meeting, OWNER);
        invitee.decline(new MeetingContext(
                "Title", null, null, "UTC", "host@example.com", "Host", "uid", 0));
        invitee.clearDomainEvents();
        stub(meeting, invitee);

        Result<DeclineMeetingInviteeResult, MeetingError> result =
                service.execute(command(meeting, invitee, OWNER));

        assertThat(failure(result)).isInstanceOf(MeetingError.InvalidInviteeTransition.class);
        verify(inviteeRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    private void stub(Meeting meeting, MeetingInvitee invitee) {
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(inviteeRepository.findById(invitee.getId())).thenReturn(Optional.of(invitee));
    }

    private DeclineMeetingInviteeCommand command(
            Meeting meeting, MeetingInvitee invitee, String accountId) {
        return new DeclineMeetingInviteeCommand(
                meeting.getId().value(), invitee.getId().value(), accountId, TENANT);
    }

    private MeetingInvitee invitee(Meeting meeting, String accountId) {
        return MeetingInvitee.create(
                TenantId.of(TENANT),
                MeetingId.of(meeting.getId().value()),
                InviterId.of("host"),
                AccountId.of(accountId),
                Email.of("invitee@example.com"),
                InviteeDisplayName.of("Invitee"),
                InviteeRole.REQ_PARTICIPANT,
                true);
    }

    private Meeting scheduledMeeting() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Result<Meeting, MeetingError> result = Meeting.schedule(
                TenantId.of(TENANT),
                AccountId.of("host"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                MeetingSettings.defaults(),
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                ShortCode.of("ABC123DEF0"));
        Meeting meeting = ((Result.Success<Meeting, MeetingError>) result).value();
        meeting.clearDomainEvents();
        return meeting;
    }

    private DeclineMeetingInviteeResult success(
            Result<DeclineMeetingInviteeResult, MeetingError> result) {
        return ((Result.Success<DeclineMeetingInviteeResult, MeetingError>) result).value();
    }

    private MeetingError failure(Result<DeclineMeetingInviteeResult, MeetingError> result) {
        return ((Result.Failure<DeclineMeetingInviteeResult, MeetingError>) result).error();
    }
}
