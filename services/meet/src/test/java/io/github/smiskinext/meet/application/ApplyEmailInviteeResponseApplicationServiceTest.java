package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.ApplyEmailInviteeResponseCommand;
import io.github.smiskinext.meet.application.service.ApplyEmailInviteeResponseApplicationService;
import io.github.smiskinext.meet.domain.model.*;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplyEmailInviteeResponseApplicationServiceTest {

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository meetingInviteeRepository;
    private EventPublisher eventPublisher;
    private ApplyEmailInviteeResponseApplicationService service;

    private static final String CALENDAR_UID = UUID.randomUUID().toString();
    private static final String TENANT = "test-tenant";
    private static final String INVITEE_EMAIL = "alice@example.com";

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        meetingInviteeRepository = mock(MeetingInviteeRepository.class);
        eventPublisher = mock(EventPublisher.class);

        service = new ApplyEmailInviteeResponseApplicationService(
                meetingRepository, meetingInviteeRepository, eventPublisher);
    }

    @Test
    void emailReplyUpdatesMatchingInviteeAndPublishesEvent() {
        Meeting meeting = buildMeeting();
        MeetingInvitee invitee = buildInvitee(meeting, InviteeStatus.NEEDS_ACTION);

        when(meetingRepository.findByCalendarUid(CALENDAR_UID)).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndEmail(
                        meeting.getId().value(), Email.of(INVITEE_EMAIL)))
                .thenReturn(Optional.of(invitee));
        when(meetingInviteeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.execute(
                new ApplyEmailInviteeResponseCommand(CALENDAR_UID, INVITEE_EMAIL, "ACCEPTED"));

        assertThat(invitee.getStatus()).isEqualTo(InviteeStatus.ACCEPTED);
        assertThat(invitee.getRespondedAt()).isPresent();
        verify(meetingInviteeRepository).save(invitee);
        verify(eventPublisher).publishEventsOf(invitee);
    }

    @Test
    void unknownMeetingIsIgnored() {
        when(meetingRepository.findByCalendarUid("unknown-uid")).thenReturn(Optional.empty());

        service.execute(
                new ApplyEmailInviteeResponseCommand("unknown-uid", INVITEE_EMAIL, "ACCEPTED"));

        verify(meetingInviteeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any(AggregateRoot.class));
    }

    @Test
    void nonInviteeEmailIsIgnored() {
        Meeting meeting = buildMeeting();
        when(meetingRepository.findByCalendarUid(CALENDAR_UID)).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndEmail(any(), any()))
                .thenReturn(Optional.empty());

        service.execute(new ApplyEmailInviteeResponseCommand(
                CALENDAR_UID, "stranger@example.com", "ACCEPTED"));

        verify(meetingInviteeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any(AggregateRoot.class));
    }

    @Test
    void duplicateReplyIsIdempotent() {
        Meeting meeting = buildMeeting();
        MeetingInvitee invitee = buildInvitee(meeting, InviteeStatus.ACCEPTED);

        when(meetingRepository.findByCalendarUid(CALENDAR_UID)).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndEmail(
                        meeting.getId().value(), Email.of(INVITEE_EMAIL)))
                .thenReturn(Optional.of(invitee));

        service.execute(
                new ApplyEmailInviteeResponseCommand(CALENDAR_UID, INVITEE_EMAIL, "ACCEPTED"));

        verify(meetingInviteeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any(AggregateRoot.class));
    }

    @Test
    void replyToRemovedInvitationIsNotApplied() {
        Meeting meeting = buildMeeting();
        MeetingInvitee invitee = buildInvitee(meeting, InviteeStatus.NEEDS_ACTION);
        invitee.remove();

        when(meetingRepository.findByCalendarUid(CALENDAR_UID)).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndEmail(
                        meeting.getId().value(), Email.of(INVITEE_EMAIL)))
                .thenReturn(Optional.of(invitee));

        service.execute(
                new ApplyEmailInviteeResponseCommand(CALENDAR_UID, INVITEE_EMAIL, "ACCEPTED"));

        verify(meetingInviteeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any(AggregateRoot.class));
    }

    private Meeting buildMeeting() {
        Instant now = Instant.now();
        MeetingTimeRange timeRange = MeetingTimeRange.of(now, now.plus(Duration.ofHours(1)));
        return Meeting.reconstitute(
                TenantId.of(TENANT),
                MeetingId.of(UUID.randomUUID()),
                AccountId.of("host-account"),
                ShortCode.of("abc1234567"),
                MeetingTitle.of("Test Meeting"),
                "description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                timeRange,
                null,
                MeetingType.SCHEDULED,
                MeetingStatus.SCHEDULED,
                new MeetingSettings(AdmissionPolicy.ALLOW_ALL, 50, true, true, true, true),
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host"),
                CALENDAR_UID,
                0,
                now,
                now,
                null,
                null,
                null,
                null);
    }

    private MeetingInvitee buildInvitee(Meeting meeting, InviteeStatus status) {
        return MeetingInvitee.reconstitute(
                TenantId.of(TENANT),
                InviteeId.of(UUID.randomUUID()),
                meeting.getId(),
                InviterId.of("host-account"),
                AccountId.of("alice-account"),
                Email.of(INVITEE_EMAIL),
                InviteeDisplayName.of("Alice"),
                InviteeRole.REQ_PARTICIPANT,
                true,
                status,
                Instant.now(),
                status == InviteeStatus.NEEDS_ACTION ? null : Instant.now(),
                null);
    }
}
